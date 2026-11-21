package com.aiclassroom.classroom.content

import com.aiclassroom.classroom.domain.*

/**
 * 分段规范化。
 *
 * 模型给的块 id / 事件 id 只在它自己那一段里有意义，拼成整节后必须唯一；
 * 所以 id 一律由程序分配，并同步改写事件对块的引用 —— 只改 blocks.id 而不改
 * events.sourceBlockIds，等于留下指向空处的引用。
 */
object SegmentNormalizer {

    data class Result(val segment: DraftSegment?, val problems: List<String>) {
        val ok: Boolean get() = segment != null && problems.isEmpty()
    }

    fun normalize(
        content: UnitContent,
        candidateId: String,
        index: Int,
        allowedTypes: Set<String>,
        knownSpeakers: Set<CharacterId>,
    ): Result {
        val problems = mutableListOf<String>()
        val idMap = HashMap<String, String>()

        val blocks = content.blocks.mapIndexed { i, block ->
            val type = typeName(block)
            if (type !in allowedTypes) {
                problems += "不允许的内容块类型：" + type
            }
            // 第 2 段起不再允许标题：整节最多一个可见标题。
            if (type == "heading" && index > 1) {
                problems += "续写段不允许新增标题"
            }
            if (block is ContentBlock.Dialogue && knownSpeakers.isNotEmpty() &&
                block.speakerId !in knownSpeakers
            ) {
                problems += "台词引用了不存在的角色：" + block.speakerId.value
            }
            val newId = candidateId + ":s" + index + ":b" + (i + 1)
            idMap[block.id] = newId
            reid(block, newId)
        }

        val newBlockIds = blocks.map { it.id }.toSet()
        val events = content.events.mapIndexed { i, event ->
            val mapped = event.sourceBlockIds.mapNotNull { idMap[it] }
            if (mapped.size != event.sourceBlockIds.size) {
                problems += "事件引用了不存在的块：" + event.id
            }
            if (mapped.isEmpty()) {
                problems += "事件没有指向任何本段块：" + event.id
            }
            UnitEvent(
                id = candidateId + ":s" + index + ":e" + (i + 1),
                sourceBlockIds = mapped.filter { it in newBlockIds },
                description = event.description,
            )
        }

        // 事件 id 必须唯一（模型偶尔会重复给 e1）。
        if (events.map { it.id }.toSet().size != events.size) {
            problems += "事件 id 重复"
        }

        if (problems.isNotEmpty()) return Result(null, problems)

        val text = blocks.joinToString("\n") { blockText(it) }
        return Result(
            DraftSegment(
                index = index,
                text = text,
                blocks = blocks,
                events = events,
                chars = TextCount.units(blocks),
            ),
            emptyList(),
        )
    }

    fun typeName(block: ContentBlock): String = when (block) {
        is ContentBlock.Narration -> "narration"
        is ContentBlock.Dialogue -> "dialogue"
        is ContentBlock.Heading -> "heading"
        is ContentBlock.Explanation -> "explanation"
        is ContentBlock.Example -> "example"
        is ContentBlock.Exercise -> "exercise"
        is ContentBlock.Choice -> "choice"
    }

    private fun reid(block: ContentBlock, id: String): ContentBlock = when (block) {
        is ContentBlock.Narration -> block.copy(id = id)
        is ContentBlock.Dialogue -> block.copy(id = id)
        is ContentBlock.Heading -> block.copy(id = id)
        is ContentBlock.Explanation -> block.copy(id = id)
        is ContentBlock.Example -> block.copy(id = id)
        is ContentBlock.Exercise -> block.copy(id = id)
        is ContentBlock.Choice -> block.copy(id = id)
    }

    private fun blockText(block: ContentBlock): String = when (block) {
        is ContentBlock.Narration -> block.text
        is ContentBlock.Dialogue -> block.text
        is ContentBlock.Heading -> block.text
        is ContentBlock.Explanation -> block.text
        is ContentBlock.Example -> block.text
        is ContentBlock.Exercise -> block.prompt
        is ContentBlock.Choice -> block.prompt
    }
}
