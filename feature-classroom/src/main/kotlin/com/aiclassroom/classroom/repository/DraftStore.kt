package com.aiclassroom.classroom.repository

import com.aiclassroom.classroom.domain.*

/** 分段草稿的读写端口。 */
interface DraftStore {

    data class WriteResult(val ok: Boolean, val reason: String = "")

    /** 读某单元当前的候选草稿；没有就返回 null。 */
    fun draft(unitId: UnitId): UnitDraft?

    /**
     * 追加一段。
     *
     * 事务内校验：
     * - 输入版本一致（输入变了旧候选作废）；
     * - 执行者令牌不落后于草稿记录（被接管者的迟到响应不得写入）；
     * - 分段序号未被占用（重复响应不产生重复段）。
     */
    fun appendSegment(
        unitId: UnitId,
        segment: DraftSegment,
        runId: RunId?,
        fencingToken: Long,
        now: EpochMillis,
    ): WriteResult

    /** 开始一个新候选（旧候选随之作废）。 */
    fun startCandidate(
        projectId: ProjectId,
        unitId: UnitId,
        candidateId: String,
        inputVersion: String,
        now: EpochMillis,
    ): WriteResult

    /** 清理某个单元的草稿（正式提交后调用）。 */
    fun clear(unitId: UnitId)
}

/** 内存实现，供测试与模拟链路使用。 */
class InMemoryDraftStore : DraftStore {

    private val drafts = LinkedHashMap<UnitId, UnitDraft>()

    override fun draft(unitId: UnitId): UnitDraft? = drafts[unitId]

    override fun appendSegment(
        unitId: UnitId,
        segment: DraftSegment,
        runId: RunId?,
        fencingToken: Long,
        now: EpochMillis,
    ): DraftStore.WriteResult {
        val current = drafts[unitId] ?: return DraftStore.WriteResult(false, "没有进行中的候选")
        if (current.segments.any { it.index == segment.index }) {
            return DraftStore.WriteResult(false, "分段序号已存在：" + segment.index)
        }
        if (fencingToken > 0 && fencingToken < current.fencingToken) {
            return DraftStore.WriteResult(false, "执行权已被接管，拒绝写入")
        }
        drafts[unitId] = current.copy(
            segments = current.segments + segment,
            totalChars = current.totalChars + segment.chars,
            runId = runId ?: current.runId,
            fencingToken = maxOf(current.fencingToken, fencingToken),
            updatedAt = now,
        )
        return DraftStore.WriteResult(true)
    }

    override fun startCandidate(
        projectId: ProjectId,
        unitId: UnitId,
        candidateId: String,
        inputVersion: String,
        now: EpochMillis,
    ): DraftStore.WriteResult {
        drafts[unitId] = UnitDraft(
            projectId = projectId,
            unitId = unitId,
            candidateId = candidateId,
            inputVersion = inputVersion,
            updatedAt = now,
        )
        return DraftStore.WriteResult(true)
    }

    override fun clear(unitId: UnitId) {
        drafts.remove(unitId)
    }
}
