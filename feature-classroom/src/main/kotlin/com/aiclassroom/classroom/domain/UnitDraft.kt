package com.aiclassroom.classroom.domain

/** 草稿阶段：恢复时按阶段接着跑，不从头生成。 */
enum class DraftPhase {
    GENERATING,
    ASSEMBLED,
    REVIEWING,
    PATCHING,
    READY_TO_COMMIT,
    COMMITTED,
    INVALIDATED,
}

/**
 * 分段草稿：一个单元在「还没写完」时的现场。
 *
 * 万字一节需要多次调用，中途断网、暂停、杀进程都发生过；
 * 只放内存的话，恢复时只能从头再写一遍，等于白花额度。
 */
data class UnitDraft(
    val projectId: ProjectId,
    val unitId: UnitId,
    /** 候选版本身份：同一单元重新生成时换新值，旧候选自动作废。 */
    val candidateId: String,
    /** 生成时的输入版本（规格 + 总纲 + 快照）；输入变了旧候选就不能再用。 */
    val inputVersion: String,
    val segments: List<DraftSegment> = emptyList(),
    /** 已累计的正文字数，由程序按 blocks 重算，不采信调用方。 */
    val totalChars: Int = 0,
    val summary: String = "",
    val usedScenes: List<String> = emptyList(),
    val runId: RunId? = null,
    val owner: String? = null,
    val fencingToken: Long = 0,
    /** 内容有修订时递增；审查/补丁结果绑定这个版本。 */
    val candidateVersion: Int = 0,
    val phase: DraftPhase = DraftPhase.GENERATING,
    /** 应用层调用计数与连续失败数：恢复后不清零。 */
    val callCount: Int = 0,
    val failStreak: Int = 0,
    val committedRevisionId: RevisionId? = null,
    val lastError: String = "",
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
) {
    /** 用于识别重复响应：同段内容一致就不再累加。 */
    /**
     * 重复响应判定用正文内容，不用 chars —— chars 是程序算出来的派生物，
     * 把它算进去会让「同一段重复送达」被误判成冲突。
     */
    val checksum: String get() = text.trim()
}
