package com.aiclassroom.classroom.store

import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.json.*

/**
 * 正文块结构的编解码。
 *
 * 不存这个的话，阅读器只能读草稿文本 —— 而草稿文本是模型原始 JSON，
 * 读者看到的就是一堆大括号（实测就是这样）。
 */
object ContentCodec {

    fun encode(content: UnitContent): Json = Json.obj(
        "schemaVersion" to Json.of(content.schemaVersion),
        "unitId" to Json.of(content.unitId.value),
        "planVersion" to Json.of(content.planVersion),
        "title" to Json.of(content.title),
        "objectivesCovered" to Json.arr(content.objectivesCovered.map { Json.of(it) }),
        "unresolvedItems" to Json.arr(content.unresolvedItems.map { Json.of(it) }),
        "blocks" to Json.arr(content.blocks.map(::encodeBlock)),
        "events" to Json.arr(content.events.map { e ->
            Json.obj(
                "id" to Json.of(e.id),
                "description" to Json.of(e.description),
                "sourceBlockIds" to Json.arr(e.sourceBlockIds.map { Json.of(it) }),
            )
        }),
    )

    fun decode(j: Json?): UnitContent? {
        if (j == null || j is Json.Null) return null
        val o = j.asObject()
        if (o.isEmpty()) return null
        return UnitContent(
            schemaVersion = o["schemaVersion"].asInt() ?: 1,
            unitId = UnitId(o["unitId"].asString() ?: ""),
            planVersion = o["planVersion"].asInt() ?: 1,
            title = o["title"].asString() ?: "",
            blocks = o["blocks"].asArray().mapNotNull(::decodeBlock),
            objectivesCovered = o["objectivesCovered"].asArray().mapNotNull { it.asString() },
            events = o["events"].asArray().map { item ->
                val m = item.asObject()
                UnitEvent(
                    id = m["id"].asString() ?: "",
                    sourceBlockIds = m["sourceBlockIds"].asArray().mapNotNull { it.asString() },
                    description = m["description"].asString() ?: "",
                )
            },
            unresolvedItems = o["unresolvedItems"].asArray().mapNotNull { it.asString() },
        )
    }

    private fun encodeBlock(block: ContentBlock): Json = when (block) {
        is ContentBlock.Narration -> blockJson("narration", block.id, block.text)
        is ContentBlock.Dialogue -> Json.obj(
            "type" to Json.of("dialogue"),
            "id" to Json.of(block.id),
            "speakerId" to Json.of(block.speakerId.value),
            "text" to Json.of(block.text),
        )
        is ContentBlock.Heading -> Json.obj(
            "type" to Json.of("heading"),
            "id" to Json.of(block.id),
            "text" to Json.of(block.text),
            "level" to Json.of(block.level),
        )
        is ContentBlock.Explanation -> blockJson("explanation", block.id, block.text)
        is ContentBlock.Example -> Json.obj(
            "type" to Json.of("example"),
            "id" to Json.of(block.id),
            "text" to Json.of(block.text),
            "language" to Json.of(block.language),
        )
        is ContentBlock.Exercise -> Json.obj(
            "type" to Json.of("exercise"),
            "id" to Json.of(block.id),
            "prompt" to Json.of(block.prompt),
            "answer" to Json.of(block.answer),
        )
        is ContentBlock.Choice -> Json.obj(
            "type" to Json.of("choice"),
            "id" to Json.of(block.id),
            "prompt" to Json.of(block.prompt),
            // 选项必须落库：只存 prompt 的话阅读器拿到的是一张没有按钮的选项。
            "options" to Json.arr(
                block.options.map { opt ->
                    Json.obj(
                        "id" to Json.of(opt.id),
                        "text" to Json.of(opt.text),
                        "targetNodeId" to Json.of(opt.targetNodeId),
                    )
                },
            ),
        )
    }

    private fun blockJson(type: String, id: String, text: String): Json = Json.obj(
        "type" to Json.of(type),
        "id" to Json.of(id),
        "text" to Json.of(text),
    )

    private fun decodeBlock(j: Json): ContentBlock? {
        val m = j.asObject()
        val id = m["id"].asString() ?: return null
        val text = m["text"].asString() ?: ""
        return when (m["type"].asString()) {
            "narration" -> ContentBlock.Narration(id, text)
            "dialogue" -> ContentBlock.Dialogue(id, CharacterId(m["speakerId"].asString() ?: ""), text)
            "heading" -> ContentBlock.Heading(id, text, m["level"].asInt() ?: 2)
            "explanation" -> ContentBlock.Explanation(id, text)
            "example" -> ContentBlock.Example(id, text, m["language"].asString())
            "exercise" -> ContentBlock.Exercise(id, m["prompt"].asString() ?: text, m["answer"].asString() ?: "")
            "choice" -> ContentBlock.Choice(
                id,
                m["prompt"].asString() ?: text,
                m["options"].asArray().map { item ->
                    val o = item.asObject()
                    ChoiceOption(
                        id = o["id"].asString() ?: "",
                        text = o["text"].asString() ?: "",
                        targetNodeId = o["targetNodeId"].asString() ?: "",
                    )
                },
            )
            else -> null
        }
    }
}
