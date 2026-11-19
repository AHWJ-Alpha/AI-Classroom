package com.aiclassroom.classroom.domain

/**
 * 一章的正式摘要。
 *
 * 之前跨章上下文靠「上一节 JSON 尾部 1500 字」：相隔两章就接不上，
 * 伏笔和未解决事项更是全丢。摘要在提交时生成，与 revision 绑定，
 * 源 revision 变了就失效（不会被当成新正文的依据）。
 */
data class ChapterSummary(
    val projectId: ProjectId,
    val unitId: UnitId,
    val revisionId: RevisionId,
    val index: Int,
    val title: String,
    /** 本章实际发生的事件（来自正文事件表）。 */
    val keyEvents: List<String> = emptyList(),
    /** 正文里明确留下的未解决事项/伏笔。 */
    val unresolvedItems: List<String> = emptyList(),
    /** 本章覆盖的教学目标（小说为空）。 */
    val objectivesCovered: List<String> = emptyList(),
    /** 极短摘要，用于预算受限时也能带上。 */
    val gist: String = "",
    val createdAt: EpochMillis,
) {

    /** 拼成给模型看的摘要行；预算紧张时只留 gist。 */
    fun render(compact: Boolean = false): String = buildString {
        append("第 ").append(index).append(" 章 · ").append(title)
        if (gist.isNotBlank()) append("：").append(gist)
        if (!compact) {
            if (keyEvents.isNotEmpty()) append("｜事件：").append(keyEvents.take(4).joinToString("；"))
            if (unresolvedItems.isNotEmpty()) append("｜未解决：").append(unresolvedItems.take(3).joinToString("；"))
            if (objectivesCovered.isNotEmpty()) append("｜目标：").append(objectivesCovered.take(3).joinToString("；"))
        }
    }
}

object SummaryBuilder {

    fun from(
        unit: ClassroomUnit,
        revision: UnitRevision,
        clock: () -> EpochMillis,
    ): ChapterSummary {
        val content = revision.content
        val events = content?.events?.map { it.description }.orEmpty().filter { it.isNotBlank() }
        val gist = (content?.blocks.orEmpty())
            .take(1)
            .joinToString("") { block ->
                when (block) {
                    is ContentBlock.Heading -> block.text
                    is ContentBlock.Narration -> block.text
                    is ContentBlock.Explanation -> block.text
                    is ContentBlock.Dialogue -> block.text
                    is ContentBlock.Example -> block.text
                    is ContentBlock.Exercise -> block.prompt
                    is ContentBlock.Choice -> block.prompt
                }
            }
            .take(60)

        return ChapterSummary(
            projectId = unit.projectId,
            unitId = unit.id,
            revisionId = revision.id,
            index = unit.index,
            title = unit.chapter,
            keyEvents = events.take(6),
            unresolvedItems = content?.unresolvedItems.orEmpty().take(5),
            objectivesCovered = content?.objectivesCovered.orEmpty().take(5),
            gist = gist,
            createdAt = clock(),
        )
    }
}
