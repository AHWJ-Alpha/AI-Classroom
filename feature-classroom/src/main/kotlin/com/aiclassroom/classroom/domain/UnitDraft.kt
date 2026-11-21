package com.aiclassroom.classroom.domain

/**
 * 分段草稿：一个单元在「还没写完」时的现场。
 *
 * 为什么要落盘：万字一节需要多次调用，中途断网、暂停、杀进程都发生过；
 * 只放内存的话，恢复时只能从头再写一遍，等于白花额度。
 *
 * 草稿**不锁死在某个执行者**上：新执行者确认输入仍有效并拿到执行权后，
 * 可以接管同一个 candidateId 继续写。
 */
data class UnitDraft(
    val projectId: ProjectId,
    val unitId: UnitId,
    /** 候选版本身份：同一单元重新生成时换新值，旧候选自动作废。 */
    val candidateId: String,
    /** 生成时的输入版本（规格 + 总纲 + 快照）；输入变了旧候选就不能再用。 */
    val inputVersion: String,
    val segments: List<DraftSegment> = emptyList(),
    /** 已累计的正文字数（只算叙述与对白）。 */
    val totalChars: Int = 0,
    /** 供续写使用的累计摘要与已用桥段。 */
    val summary: String = "",
    val usedScenes: List<String> = emptyList(),
    /** 产生最后一段的执行者与令牌，用于拒绝被接管者的迟到写入。 */
    val runId: RunId? = null,
    val fencingToken: Long = 0,
    val updatedAt: EpochMillis = 0,
) {
    val nextIndex: Int get() = (segments.maxOfOrNull { it.index } ?: 0) + 1
}

/** 一段已通过校验的正文。 */
data class DraftSegment(
    val index: Int,
    val text: String,
    /** 本段产生的块（已由程序分配 id）。 */
    val blocks: List<ContentBlock> = emptyList(),
    val events: List<UnitEvent> = emptyList(),
    val chars: Int = 0,
    val createdAt: EpochMillis = 0,
)
