package com.aiclassroom.classroom.content

import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.gateway.StructuredPayload
import com.aiclassroom.classroom.json.*

/**
 * 解析状态提取器的输出。
 *
 * 策略是**逐条校验、坏条目单独丢弃**，而不是整份作废：
 * 一条没依据的情绪变更不该让整章内容提交不了；
 * 但没依据的条目**绝不能**悄悄生效，所以每条被丢掉的原因都会返回。
 */
object StatePatchParser {

    data class Parsed(
        val changes: List<StateChange>,
        val evidenceEventIds: List<String>,
        val reason: String,
        val dropped: List<String>,
        /** 输出结构是否可读（能拿到 JSON、字段类型正确）。 */
        val wellFormed: Boolean = false,
        /** 模型原本给了几条变化（含被丢弃的）。 */
        val rawChangeCount: Int = 0,
    ) {
        /**
         * 能不能当成「合法的结果」继续走。
         *
         * 以前是 changes.isNotEmpty()：合法空补丁（本单元确实没变化）被当成失败，
         * 而「全部变化被校验器丢弃」又可能被当成空补丁放行 —— 两头都错。
         */
        val usable: Boolean get() = wellFormed
        val hasChanges: Boolean get() = changes.isNotEmpty()
        /** 模型给了变化但没有一条通过校验：这是失败，不是「没有变化」。 */
        val allDropped: Boolean get() = rawChangeCount > 0 && changes.isEmpty()
    }

    /**
     * 把模型给的 characterId 解析成真实角色。
     *
     * 模型在生成阶段看到的是「id=…；姓名=…」，经常直接拿名字填。
     * 只认 UUID 会让整份补丁作废；先按 id 认，再按名字认。
     */
    private fun resolveCharacter(
        raw: String?,
        known: Set<CharacterId>,
        names: Map<String, CharacterId>,
    ): CharacterId? {
        if (raw == null) return null
        val byId = CharacterId(raw)
        if (byId in known) return byId
        return names[raw.trim()]?.takeIf { it in known }
    }

    fun parse(
        raw: String,
        knownCharacters: Set<CharacterId>,
        knownEventIds: Set<String>,
        knownFacts: Set<FactId> = emptySet(),
        /** 角色名 → id：模型常把名字当 characterId 填，能映射就映射，不因此整份作废。 */
        knownNames: Map<String, CharacterId> = emptyMap(),
    ): Parsed {
        val jsonText = StructuredPayload.extract(raw)
            ?: return Parsed(emptyList(), emptyList(), "", listOf("输出里没有完整 JSON"))
        val root = runCatching { JsonParser.parse(jsonText) }.getOrNull()
            ?: return Parsed(emptyList(), emptyList(), "", listOf("JSON 无法解析"))

        val o = root.asObject()
        if (o["changes"] !is Json.Arr) {
            return Parsed(emptyList(), emptyList(), "", listOf("补丁缺少 changes 数组"))
        }
        val reason = o["reason"].asString() ?: ""
        val evidence = o["evidenceEventIds"].asArray().mapNotNull { it.asString() }
        val dropped = mutableListOf<String>()

        // 先看模型到底给了几条变化：
        // - 0 条 = 合法的「本单元没有状态变化」，不需要证据；
        // - >0 条却不引用事件 = 违反协议，整份不可用（不能伪装成空补丁）。
        val rawChangeCount = o["changes"].asArray().size
        if (rawChangeCount == 0) {
            return Parsed(
                emptyList(), evidence, reason, dropped,
                wellFormed = true, rawChangeCount = 0,
            )
        }
        if (evidence.isEmpty()) {
            dropped += "补丁声明了变化却没有引用任何事件"
            return Parsed(
                emptyList(), evidence, reason, dropped,
                wellFormed = true, rawChangeCount = rawChangeCount,
            )
        }
        evidence.filterNot { it in knownEventIds }.forEach { dropped += "引用了不存在的事件：" + it }
        // 一条真实事件都引不到时，变化没有依据：整份丢弃，不能当作空补丁放行。
        if (evidence.none { it in knownEventIds }) {
            dropped += "证据里没有一条是本单元真实发生的事件"
            return Parsed(
                emptyList(), evidence, reason, dropped,
                wellFormed = true, rawChangeCount = rawChangeCount,
            )
        }

        val changes = o["changes"].asArray().mapNotNull { item ->
            val m = item.asObject()
            val actorRaw = m["characterId"].asString()?.takeIf { it.isNotBlank() }
            val actor = resolveCharacter(actorRaw, knownCharacters, knownNames)
            if (actor == null) {
                dropped += "变更缺少 characterId"
                return@mapNotNull null
            }
            // 空集合表示“当前项目没有注册角色”，不是“允许模型自由发明角色”。
            if (actor !in knownCharacters) {
                dropped += "变更引用了不存在的角色：" + actor.value
                return@mapNotNull null
            }
            when (val op = m["op"].asString()) {
                "SetEmotion" -> StateChange.SetEmotion(actor, m["value"].asString() ?: "")
                "SetGoal" -> StateChange.SetGoal(actor, m["value"].asString() ?: "")
                "SetLocation" -> StateChange.SetLocation(actor, m["value"].asString() ?: "")
                "AdjustStress" -> StateChange.AdjustStress(actor, m["delta"].asInt() ?: 0)
                "SetRelationship" -> {
                    val targetRaw = m["target"].asString()?.takeIf { it.isNotBlank() }
                    val target = resolveCharacter(targetRaw, knownCharacters, knownNames)
                    if (target == null) {
                        dropped += if (targetRaw == null) "关系变更缺少 target"
                            else "关系变更引用了不存在的角色：" + targetRaw
                        null
                    } else {
                        StateChange.SetRelationship(actor, target, m["value"].asString() ?: "")
                    }
                }
                "LearnFact" -> {
                    val fact = m["factId"].asString()?.takeIf { it.isNotBlank() }?.let { FactId(it) }
                    if (fact == null) {
                        dropped += "获知变更缺少 factId"
                        null
                    } else if (fact !in knownFacts) {
                        dropped += "获知变更引用了不存在的事实：" + fact.value
                        null
                    } else {
                        StateChange.LearnFact(actor, fact)
                    }
                }
                else -> {
                    dropped += "未知的状态操作：" + op
                    null
                }
            }
        }

        return Parsed(
            changes, evidence, reason, dropped,
            wellFormed = true, rawChangeCount = rawChangeCount,
        )
    }
}
