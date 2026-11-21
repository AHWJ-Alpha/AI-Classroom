package com.aiclassroom.classroom.repository

import com.aiclassroom.classroom.domain.*

/**
 * 草稿写入守卫。
 *
 * 每次写都要带完整身份：草稿是并发与接管最容易出问题的地方，
 * 只凭 unitId 写入等于没有边界。
 */
data class DraftWriteGuard(
    val projectId: ProjectId,
    val unitId: UnitId,
    val candidateId: String,
    val expectedInputVersion: String,
    val expectedCandidateVersion: Int,
    val runId: RunId,
    val owner: String,
    val fencingToken: Long,
)

/** 写入结果：类型化，不靠字符串驱动恢复逻辑。 */
sealed interface DraftOutcome {
    data object Applied : DraftOutcome
    /** 同一段重复送达：内容一致，已生效，不重复累加。 */
    data object AlreadyApplied : DraftOutcome
    data class StaleCandidate(val current: String) : DraftOutcome
    data class StaleInput(val current: String) : DraftOutcome
    data class StaleExecutor(val reason: String) : DraftOutcome
    data class Conflict(val reason: String) : DraftOutcome
}

/**
 * 分段草稿的受保护操作层。
 *
 * 所有写操作都在 ClassroomStore.transaction 内重新校验身份、输入版本、令牌与序号，
 * 校验通过才落盘；落盘失败由仓库回滚，不会留下「内存改了、磁盘没改」。
 */
class DraftStore(
    private val store: ClassroomStore,
    private val clock: () -> EpochMillis,
) {

    fun current(projectId: ProjectId, unitId: UnitId): UnitDraft? = store.draft(projectId, unitId)

    /** 开始一个新候选。expectedOldCandidateId 做比较交换；重复创建同一候选返回已有结果。 */
    fun begin(
        projectId: ProjectId,
        unitId: UnitId,
        candidateId: String,
        inputVersion: String,
        expectedOldCandidateId: String?,
    ): DraftOutcome = store.transaction {
        val unit = store.unit(unitId)
            ?: return@transaction DraftOutcome.Conflict("单元不存在")
        if (unit.projectId != projectId) {
            return@transaction DraftOutcome.Conflict("单元不属于该项目")
        }
        val existing = store.draft(projectId, unitId)
        if (existing != null && existing.candidateId == candidateId) {
            // 重复创建同一候选：保留已有分段，不清空。
            return@transaction DraftOutcome.AlreadyApplied
        }
        if (existing != null && existing.candidateId != expectedOldCandidateId) {
            return@transaction DraftOutcome.StaleCandidate(existing.candidateId)
        }
        store.saveDraft(
            UnitDraft(
                projectId = projectId,
                unitId = unitId,
                candidateId = candidateId,
                inputVersion = inputVersion,
                updatedAt = clock(),
            ),
        )
        DraftOutcome.Applied
    }

    /**
     * 追加一段。
     *
     * @param currentInputVersion 当前有效输入的指纹：草稿冻结的版本必须与它一致，
     *   否则说明规格/总纲/快照变了，旧候选不得继续。
     */
    fun append(
        guard: DraftWriteGuard,
        segment: DraftSegment,
        currentInputVersion: String,
        charsOf: (DraftSegment) -> Int,
    ): DraftOutcome = store.transaction {
        val d = store.draft(guard.projectId, guard.unitId)
            ?: return@transaction DraftOutcome.StaleCandidate("")
        if (d.candidateId != guard.candidateId) {
            return@transaction DraftOutcome.StaleCandidate(d.candidateId)
        }
        if (d.inputVersion != guard.expectedInputVersion || currentInputVersion != d.inputVersion) {
            return@transaction DraftOutcome.StaleInput(d.inputVersion)
        }
        if (d.candidateVersion != guard.expectedCandidateVersion) {
            return@transaction DraftOutcome.Conflict("候选版本已变化")
        }
        val executor = checkExecutor(guard)
        if (executor != null) return@transaction executor

        val same = d.segments.firstOrNull { it.index == segment.index }
        if (same != null) {
            return@transaction if (same.checksum == segment.checksum) {
                DraftOutcome.AlreadyApplied
            } else {
                DraftOutcome.Conflict("同一序号已有不同内容")
            }
        }
        if (segment.index != d.nextIndex) {
            return@transaction DraftOutcome.Conflict("分段必须连续，期望 " + d.nextIndex)
        }

        val actualChars = charsOf(segment)
        store.saveDraft(
            d.copy(
                segments = d.segments + segment.copy(chars = actualChars),
                totalChars = d.segments.sumOf { it.chars } + actualChars,
                runId = guard.runId,
                owner = guard.owner,
                fencingToken = guard.fencingToken,
                updatedAt = clock(),
            ),
        )
        DraftOutcome.Applied
    }

    /** 把当前执行身份登记到草稿上（接管时调用），旧执行者随即失效。 */
    fun claim(guard: DraftWriteGuard): DraftOutcome = store.transaction {
        val d = store.draft(guard.projectId, guard.unitId)
            ?: return@transaction DraftOutcome.StaleCandidate("")
        if (d.candidateId != guard.candidateId) {
            return@transaction DraftOutcome.StaleCandidate(d.candidateId)
        }
        val executor = checkExecutor(guard)
        if (executor != null) return@transaction executor
        store.saveDraft(
            d.copy(
                runId = guard.runId,
                owner = guard.owner,
                fencingToken = guard.fencingToken,
                updatedAt = clock(),
            ),
        )
        DraftOutcome.Applied
    }

    fun invalidate(guard: DraftWriteGuard, reason: String): DraftOutcome = store.transaction {
        val d = store.draft(guard.projectId, guard.unitId)
            ?: return@transaction DraftOutcome.StaleCandidate("")
        if (d.candidateId != guard.candidateId) {
            return@transaction DraftOutcome.StaleCandidate(d.candidateId)
        }
        store.saveDraft(
            d.copy(phase = DraftPhase.INVALIDATED, lastError = reason, updatedAt = clock()),
        )
        DraftOutcome.Applied
    }

    fun clearForCommit(guard: DraftWriteGuard, revisionId: RevisionId): DraftOutcome = store.transaction {
        val d = store.draft(guard.projectId, guard.unitId)
            ?: return@transaction DraftOutcome.StaleCandidate("")
        if (d.candidateId != guard.candidateId) {
            return@transaction DraftOutcome.StaleCandidate(d.candidateId)
        }
        store.saveDraft(
            d.copy(
                phase = DraftPhase.COMMITTED,
                committedRevisionId = revisionId,
                updatedAt = clock(),
            ),
        )
        DraftOutcome.Applied
    }

    /**
     * 执行身份校验：run 属于本项目、owner 精确相等、令牌一致、租约严格未过期。
     *
     * token=0 / owner 空 / 租约 0 都不放行 —— 这些「看起来像初始值」的状态，
     * 一旦当成有效就等于任何人可写。
     */
    private fun checkExecutor(guard: DraftWriteGuard): DraftOutcome? {
        if (guard.fencingToken <= 0L) return DraftOutcome.StaleExecutor("缺少有效执行令牌")
        if (guard.owner.isBlank()) return DraftOutcome.StaleExecutor("缺少执行者标识")
        val run = store.run(guard.runId)
            ?: return DraftOutcome.StaleExecutor("任务不存在")
        if (run.projectId != guard.projectId) return DraftOutcome.StaleExecutor("任务不属于该项目")
        if (run.owner != guard.owner) return DraftOutcome.StaleExecutor("执行者已变更")
        if (run.fencingToken != guard.fencingToken) return DraftOutcome.StaleExecutor("执行令牌已被接管")
        if (run.leaseUntil <= clock()) return DraftOutcome.StaleExecutor("租约已过期")
        return null
    }
}
