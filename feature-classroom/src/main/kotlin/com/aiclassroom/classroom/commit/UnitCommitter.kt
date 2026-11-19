package com.aiclassroom.classroom.commit

import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.repository.*

/** 一次提交请求。 */
data class CommitRequest(
    val projectId: ProjectId,
    val unitId: UnitId,
    val revision: UnitRevision,
    val statePatch: StatePatch?,
    val runId: RunId,
    val idempotencyKey: String,
    /** 生成时冻结的规格版本；提交时若已变化则说明输入过期。 */
    val expectedSpecVersion: Int? = null,
    val expectedOutlineVersion: Int? = null,
)

/**
 * 单元提交。
 *
 * 提交是整条链上唯一会改变「正式内容」的动作，所以这里只做三件事：
 * 1. 把能拒绝的情况**全部在校验阶段拒绝**，坏数据不进数据库；
 * 2. 在同一个事务里写正文版本、事实/状态、检查点、单元状态与项目版本；
 * 3. 用幂等键挡住重复回调与双 worker。
 *
 * 不做网络请求、不生成内容 —— 那些在任务执行器里，提交失败不影响已提交内容。
 */
class UnitCommitter(
    private val store: ClassroomStore,
    private val clock: () -> EpochMillis,
) {

    fun commit(request: CommitRequest): CommitResult {
        val unit = store.unit(request.unitId)
            ?: return CommitResult.Rejected(
                CommitRejection.WrongStatus(UnitStatus.FAILED),
            )

        // 幂等：同一单元已经用同一把键提交过，直接返回既有结果，不再造新版本。
        val existing = store.checkpoint(request.runId)
        if (existing != null &&
            existing.unitId == request.unitId &&
            existing.idempotencyKey == request.idempotencyKey
        ) {
            val rev = store.revision(existing.revisionId)
                ?: return CommitResult.Rejected(CommitRejection.AlreadyCommitted(existing.revisionId))
            return CommitResult.Committed(existing, rev)
        }

        // 状态机：只有这几种状态可以提交。
        if (unit.status !in COMMITTABLE) {
            return CommitResult.Rejected(CommitRejection.WrongStatus(unit.status))
        }

        // 审查：blocking 问题没清掉之前不能进正式内容。
        val review = request.revision.review
        if (review != null && review.hasBlocking) {
            return CommitResult.Rejected(CommitRejection.Blocked(review.issues.filter { it.blocking }))
        }

        // 并发：状态补丁必须基于当前版本，否则拒绝并让调用方重新排任务。
        val patch = request.statePatch

        // 角色边界：未注册的角色不能产生状态。
        val unknown = if (patch != null) unknownCharacters(request.projectId, unit, patch) else emptyList()
        if (patch != null) {
            // 证据必须真的出现在本单元的正文事件里：没有证据的状态变化不能生效。
            val knownEvents = request.revision.content?.events?.map { it.id }?.toSet().orEmpty()
            val unknownEvents = patch.evidenceEventIds.filterNot { it in knownEvents }
            if (unknownEvents.isNotEmpty()) {
                return CommitResult.Rejected(CommitRejection.UnknownEvent(unknownEvents))
            }
        }
        if (unknown.isNotEmpty()) {
            return CommitResult.Rejected(CommitRejection.UnknownCharacter(unknown))
        }
        if (patch != null) {
            // 逐个人物核对基础版本：多人物补丁不能拿第一个人的版本代表全部。
            for (actor in patch.changes.map { it.actorId }.distinct()) {
                val expected = patch.baseVersions[actor] ?: patch.baseVersion
                val actual = store.characterState(request.projectId, actor)?.version ?: 0
                if (expected != actual) {
                    return CommitResult.Rejected(CommitRejection.StaleBaseVersion(expected, actual))
                }
            }
        }

        val now = clock()
        val nextVersion = unit.officialVersion + 1
        val committedRevision = request.revision.copy(
            version = nextVersion,
            committedAt = now,
        )

        return store.transaction {
            // 输入过期：模型响应回来之前用户可能改过规格或总纲，
            // 旧候选不能覆盖新输入。校验放在事务里，避免检查与写入之间被插队。
            request.expectedSpecVersion?.let { expected ->
                val actual = store.latestSpec(request.projectId)?.version ?: 0
                if (actual != expected) {
                    return@transaction CommitResult.Rejected(
                        CommitRejection.StaleInput("规格已更新（期望 " + expected + "，实际 " + actual + "）"),
                    )
                }
            }
            request.expectedOutlineVersion?.let { expected ->
                val actual = store.latestOutline(request.projectId)?.version ?: 0
                if (actual != expected) {
                    return@transaction CommitResult.Rejected(
                        CommitRejection.StaleInput("总纲已更新（期望 " + expected + "，实际 " + actual + "）"),
                    )
                }
            }
            // 旧版本标记为被替代，历史仍然保留。
            unit.currentRevisionId?.let { oldId ->
                store.revision(oldId)?.let { old ->
                    store.saveRevision(old.copy(supersededBy = committedRevision.id))
                }
            }

            store.saveRevision(committedRevision)
            // 摘要与 revision 绑定：跨章上下文靠它，不再靠「上一节尾部 1500 字」。
            store.saveSummary(
                SummaryBuilder.from(unit, committedRevision, clock),
            )

            if (patch != null) {
                applyPatch(request.projectId, patch, now)
            }

            val checkpoint = Checkpoint(
                runId = request.runId,
                unitId = unit.id,
                revisionId = committedRevision.id,
                workVersion = nextVersion,
                stateVersion = if (patch != null) primaryStateVersion(request.projectId, patch) else 0,
                outlineVersion = store.latestOutline(request.projectId)?.version ?: 0,
                committedAt = now,
                checksum = committedRevision.sourceHash,
                idempotencyKey = request.idempotencyKey,
            )
            store.saveCheckpoint(checkpoint)

            store.upsertUnit(
                unit.copy(
                    status = UnitStatus.READY,
                    currentRevisionId = committedRevision.id,
                    officialVersion = nextVersion,
                ),
            )

            // 重写某一节之后，依赖它的下游章节已经跟新前文对不上了：
            // 只把本节标成待复查，等于默许后面几章继续错着。
            RevisionImpact.markDownstreamStale(store, request.projectId, unit.id)

            store.project(request.projectId)?.let { project ->
                val newVersion = project.workVersion + 1
                store.updateProject(
                    project.copy(
                        workVersion = newVersion,
                        updatedAt = now,
                    ),
                )
                // 每一版写一份**不可变清单**：之后重写只产生新版本，不动这一份。
                // 没有它的话，用户打开「第 2 版」看到的其实是当前正文。
                store.saveManifest(
                    ManifestBuilder.build(
                        project = project,
                        specVersion = store.latestSpec(request.projectId)?.version ?: 0,
                        outlineVersion = store.latestOutline(request.projectId)?.version ?: 0,
                        snapshotId = store.latestSnapshot(request.projectId)?.id?.value,
                        units = store.units(request.projectId),
                        workVersion = newVersion,
                        clock = clock,
                    ),
                )
            }

            CommitResult.Committed(checkpoint, committedRevision)
        }
    }

    /** 补丁涉及的第一个角色的当前状态版本；补丁为空时视为 0。 */
    private fun primaryStateVersion(projectId: ProjectId, patch: StatePatch): Int {
        val first = patch.changes.firstOrNull()?.actorId ?: return 0
        return store.characterState(projectId, first)?.version ?: 0
    }

    /**
     * 补丁里引用的角色必须已经注册。
     *
     * 「没有已注册角色」不等于「接受任意角色 id」——那会让状态表被模型编造的
     * 名字污染，事后也无法判断这条状态属于谁。
     */
    private fun unknownCharacters(
        projectId: ProjectId,
        unit: ClassroomUnit,
        patch: StatePatch,
    ): List<String> {
        // 可接受的角色 = 项目档案里注册过的 ∪ 本单元在总纲里声明的。
        // 后者是作者侧登记，不是模型当场编的；只认前者会让没有角色卡的教学项目寸步难行。
        val registered = store.characters(projectId).map { it.id }.toSet() + unit.requiredCharacters.toSet()
        val referenced = patch.changes.flatMap { change ->
            when (change) {
                is StateChange.SetRelationship -> listOf(change.actorId, change.target)
                else -> listOf(change.actorId)
            }
        }
        return referenced.filterNot { it in registered }.map { it.value }.distinct()
    }

    private fun applyPatch(projectId: ProjectId, patch: StatePatch, now: EpochMillis) {
        // 按角色聚合，避免同一角色在一个补丁里被写成多个互相覆盖的状态。
        val byCharacter = patch.changes.groupBy { it.actorId }
        for ((characterId, changes) in byCharacter) {
            val base = store.characterState(projectId, characterId)
                ?: CharacterState(characterId = characterId, projectId = projectId, version = 0)
            var state = base
            for (change in changes) {
                state = when (change) {
                    is StateChange.SetEmotion -> state.copy(emotion = change.value)
                    is StateChange.SetGoal -> state.copy(goal = change.value)
                    is StateChange.SetLocation -> state.copy(location = change.value)
                    is StateChange.AdjustStress -> state.copy(stress = state.stress + change.delta)
                    is StateChange.SetRelationship ->
                        state.copy(relationships = state.relationships + (change.target to change.value))
                    is StateChange.LearnFact ->
                        state.copy(knownFactIds = state.knownFactIds + change.factId)
                }
            }
            store.saveCharacterState(state.copy(version = base.version + 1))
        }
    }

    private companion object {
        val COMMITTABLE = setOf(
            UnitStatus.DRAFT_READY,
            UnitStatus.REVIEWING,
            UnitStatus.REVISING,
        )
    }
}
