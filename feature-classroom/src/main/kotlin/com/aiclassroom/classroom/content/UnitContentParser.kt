package com.aiclassroom.classroom.content

import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.gateway.StructuredPayload
import com.aiclassroom.classroom.json.*

/**
 * 解析生成器返回的单元正文。
 *
 * 原则是宁可判定失败，也不要猜测：未知块类型、台词说了不存在的角色、
 * 事件引用了不存在的块，都让这份草稿进不了审查，而不是悄悄丢掉一块内容当作完整。
 */
object UnitContentParser {

    data class Parsed(val content: UnitContent?, val problems: List<String>) {
        val ok: Boolean get() = content != null && problems.isEmpty()
    }

    fun parse(
        raw: String,
        unitId: UnitId,
        planVersion: Int,
        knownCharacters: Set<CharacterId> = emptySet(),
    ): Parsed {
        val jsonText = StructuredPayload.extract(raw)
            ?: return Parsed(null, listOf("输出里没有完整的 JSON"))
        val root = runCatching { JsonParser.parse(jsonText) }.getOrNull()
            ?: return Parsed(null, listOf("JSON 无法解析"))

        val o = root.asObject()
        val problems = mutableListOf<String>()

        // 单元身份以**程序**为准：模型返回的 unitId 一律忽略。
        // 实测（模拟器 + 真实模型）：提示词里没给 id 时模型会自己编一个 unit-1，
        // 如果这里当成错误，整条链路就卡在第一单元永远生成不了。
        // 我们本来就知道这次要写哪个单元，不需要模型再确认一遍。

        val rawBlocks = o["blocks"].asArray()
        if (rawBlocks.isEmpty()) problems += "没有任何内容块"

        val blocks = rawBlocks.mapIndexed { i, item ->
            val m = item.asObject()
            val id = m["id"].asString() ?: ("b" + (i + 1))
            when (val type = m["type"].asString() ?: "") {
                "narration" -> ContentBlock.Narration(id, m["text"].asString() ?: "")
                "heading" -> ContentBlock.Heading(id, m["text"].asString() ?: "", m["level"].asInt() ?: 2)
                "explanation" -> ContentBlock.Explanation(id, m["text"].asString() ?: "")
                "example" -> ContentBlock.Example(id, m["text"].asString() ?: "", m["language"].asString())
                "exercise" -> ContentBlock.Exercise(
                    id,
                    m["prompt"].asString() ?: m["text"].asString() ?: "",
                    m["answer"].asString() ?: "",
                )
                "choice" -> {
                    val options = m["options"].asArray().mapNotNull { item ->
                        val o = item.asObject()
                        val optId = o["id"].asString()
                        val target = o["targetNodeId"].asString()
                        if (optId == null || target == null) {
                            problems += "选项缺少 id 或 targetNodeId：" + id
                            null
                        } else {
                            ChoiceOption(
                                id = optId,
                                text = o["text"].asString() ?: optId,
                                targetNodeId = target,
                            )
                        }
                    }
                    if (options.isEmpty()) {
                        problems += "选项块没有任何可用选项：" + id
                    }
                    if (options.map { it.id }.toSet().size != options.size) {
                        problems += "同一选项块里选项 id 重复：" + id
                    }
                    ContentBlock.Choice(id, m["prompt"].asString() ?: "", options)
                }
                "dialogue" -> {
                    val speaker = m["speakerId"].asString()?.let { CharacterId(it) }
                    if (speaker == null) {
                        problems += "台词缺少 speakerId：" + id
                        ContentBlock.Narration(id, m["text"].asString() ?: "")
                    } else {
                        if (knownCharacters.isNotEmpty() && speaker !in knownCharacters) {
                            problems += "台词引用了不存在的角色：" + speaker.value
                        }
                        ContentBlock.Dialogue(id, speaker, m["text"].asString() ?: "")
                    }
                }
                else -> {
                    problems += "未知的内容块类型：" + type
                    ContentBlock.Narration(id, m["text"].asString() ?: "")
                }
            }
        }

        val blockIds = blocks.map { it.id }.toSet()
        val events = o["events"].asArray().mapIndexed { i, item ->
            val m = item.asObject()
            val sources = m["sourceBlockIds"].asArray().mapNotNull { it.asString() }
            sources.filterNot { it in blockIds }.forEach { problems += "事件引用了不存在的块：" + it }
            UnitEvent(
                id = m["id"].asString() ?: ("e" + (i + 1)),
                sourceBlockIds = sources,
                description = m["description"].asString() ?: "",
            )
        }

        val content = UnitContent(
            unitId = unitId,
            planVersion = o["planVersion"].asInt() ?: planVersion,
            title = o["title"].asString() ?: "",
            blocks = blocks,
            objectivesCovered = o["objectivesCovered"].asArray().mapNotNull { it.asString() },
            events = events,
            unresolvedItems = o["unresolvedItems"].asArray().mapNotNull { it.asString() },
        )

        return Parsed(if (problems.isEmpty()) content else null, problems)
    }
}
