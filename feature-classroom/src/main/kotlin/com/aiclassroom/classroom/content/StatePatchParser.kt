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
    ) {
        val usable: Boolean get() = changes.isNotEmpty()
    }

    fun parse(
        raw: String,
        knownCharacters: Set<CharacterId>,
        knownEventIds: Set<String>,
    ): Parsed {
        val jsonText = StructuredPayload.extract(raw)
            ?: return Parsed(emptyList(), emptyList(), "", listOf("输出里没有完整 JSON"))
        val root = runCatching { JsonParser.parse(jsonText) }.getOrNull()
            ?: return Parsed(emptyList(), emptyList(), "", listOf("JSON 无法解析"))

        val o = root.asObject()
        val reason = o["reason"].asString() ?: ""
        val evidence = o["evidenceEventIds"].asArray().mapNotNull { it.asString() }
        val dropped = mutableListOf<String>()

        // 没有事件证据的补丁整体不可信 —— 状态变更必须能指回已发生的剧情。
        if (evidence.isEmpty()) {
            dropped += "补丁没有引用任何事件"
            return Parsed(emptyList(), evidence, reason, dropped)
        }
        evidence.filterNot { it in knownEventIds }.forEach { dropped += "引用了不存在的事件：" + it }

        val changes = o["changes"].asArray().mapNotNull { item ->
            val m = item.asObject()
            val actorRaw = m["characterId"].asString()
            val actor = actorRaw?.let { CharacterId(it) }
            if (actor == null) {
                dropped += "变更缺少 characterId"
                return@mapNotNull null
            }
            if (knownCharacters.isNotEmpty() && actor !in knownCharacters) {
                dropped += "变更引用了不存在的角色：" + actor.value
                return@mapNotNull null
            }
            when (val op = m["op"].asString()) {
                "SetEmotion" -> StateChange.SetEmotion(actor, m["value"].asString() ?: "")
                "SetGoal" -> StateChange.SetGoal(actor, m["value"].asString() ?: "")
                "SetLocation" -> StateChange.SetLocation(actor, m["value"].asString() ?: "")
                "AdjustStress" -> StateChange.AdjustStress(actor, m["delta"].asInt() ?: 0)
                "SetRelationship" -> {
                    val target = m["target"].asString()?.let { CharacterId(it) }
                    if (target == null) {
                        dropped += "关系变更缺少 target"
                        null
                    } else {
                        StateChange.SetRelationship(actor, target, m["value"].asString() ?: "")
                    }
                }
                "LearnFact" -> {
                    val fact = m["factId"].asString()?.let { FactId(it) }
                    if (fact == null) {
                        dropped += "获知变更缺少 factId"
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

        return Parsed(changes, evidence, reason, dropped)
    }
}
