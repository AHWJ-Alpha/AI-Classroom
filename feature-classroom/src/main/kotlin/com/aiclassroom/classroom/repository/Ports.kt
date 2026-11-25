package com.aiclassroom.classroom.repository

import com.aiclassroom.classroom.domain.*

/**
 * 课堂的存储端口。
 *
 * 领域只依赖这些接口，Android 侧用 SQLite（后续可换 Room）实现，
 * 测试用内存实现。所有写操作都要求调用方显式表达「一次事务」，
 * 避免出现正文写进去、检查点没写、恢复时状态错位的情况。
 */
interface ClassroomStore {

    // ---- 项目与规划 ----

    fun createProject(project: ClassroomProject)
    fun project(id: ProjectId): ClassroomProject?
    fun projects(): List<ClassroomProject>

    /**
     * 删除项目及其全部关联数据。
     *
     * 项目不是孤立一行：规格、总纲、单元、版本、草稿、摘要、状态快照、清单、
     * 事实、人物、run、事件都挂在它下面。只删 projects 会留下一堆孤儿记录，
     * 下次按 projectId 查还会查出来。
     */
    fun deleteProject(projectId: ProjectId)
    fun updateProject(project: ClassroomProject)

    fun saveSpec(spec: ProjectSpec)
    fun spec(id: ProjectId, version: Int): ProjectSpec?
    fun latestSpec(id: ProjectId): ProjectSpec?

    /** 上下文快照：一次创建后持续复用，来源变化不会悄悄改写。 */
    fun saveSnapshot(snapshot: ContextSnapshot)
    fun latestSnapshot(projectId: ProjectId): ContextSnapshot?

    fun saveOutline(outline: ProjectOutline)
    fun outline(id: ProjectId, version: Int): ProjectOutline?
    fun latestOutline(id: ProjectId): ProjectOutline?

    fun upsertUnit(unit: ClassroomUnit)
    fun unit(unitId: UnitId): ClassroomUnit?
    fun units(projectId: ProjectId): List<ClassroomUnit>

    // ---- 版本与内容 ----

    fun saveRevision(revision: UnitRevision)
    fun revision(revisionId: RevisionId): UnitRevision?
    fun revisions(unitId: UnitId): List<UnitRevision>

    /** 已提交、可阅读的正式版本。 */
    fun officialRevision(unitId: UnitId): UnitRevision?

    // ---- 状态与事实 ----

    fun saveFact(fact: FactRevision)
    fun facts(projectId: ProjectId): List<FactRevision>
    fun latestFactVersion(factId: FactId, projectId: ProjectId): FactRevision?

    fun saveCharacterState(state: CharacterState)
    fun characterStates(projectId: ProjectId): List<CharacterState>
    fun characterState(projectId: ProjectId, characterId: CharacterId): CharacterState?
    fun saveCharacter(profile: CharacterProfile)
    fun characters(projectId: ProjectId): List<CharacterProfile>

    // ---- 任务与进度 ----

    fun saveRun(run: GenerationRun)
    fun run(runId: RunId): GenerationRun?
    fun activeRun(projectId: ProjectId): GenerationRun?
    /** 某个项目下的全部任务（含已结束的），用于恢复与诊断。 */
    fun runs(projectId: ProjectId): List<GenerationRun>

    fun saveAttempt(attempt: GenerationAttempt)
    fun attempts(runId: RunId): List<GenerationAttempt>

    fun saveCheckpoint(checkpoint: Checkpoint)
    fun checkpoint(runId: RunId): Checkpoint?

    /** 每章提交时的角色状态快照：用于早期重写取前置状态。 */
    fun saveStateSnapshot(snapshot: UnitStateSnapshot)
    fun stateSnapshots(projectId: ProjectId): List<UnitStateSnapshot>

    /** 章节摘要：提交时生成，绑定到 revision。 */
    fun saveSummary(summary: ChapterSummary)
    fun summaries(projectId: ProjectId): List<ChapterSummary>

    /** 分段草稿：仓库内部原语。产品代码必须走 DraftStore 的守卫，不能直接调这些。 */
    fun draft(projectId: ProjectId, unitId: UnitId): UnitDraft?

    fun saveDraft(draft: UnitDraft)

    fun removeDraft(projectId: ProjectId, unitId: UnitId)
    fun summaryForRevision(revisionId: RevisionId): ChapterSummary?

    /** 作品版本清单：一版写一次，之后不可变。 */
    fun saveManifest(manifest: WorkManifest)
    fun manifest(projectId: ProjectId, workVersion: Int): WorkManifest?
    fun latestManifest(projectId: ProjectId): WorkManifest?

    /** 事件 outbox：先落库、再投递，投递失败可重试且不重复。 */
    fun saveEvent(event: ClassroomEvent)
    fun event(eventId: String): ClassroomEvent?
    fun pendingEvents(sourceConversationId: String): List<ClassroomEvent>

    fun saveReaderProgress(progress: ReaderProgress)
    fun readerProgress(projectId: ProjectId): ReaderProgress?

    fun saveLearningProgress(progress: LearningProgress)
    fun learningProgress(projectId: ProjectId, objectiveId: String): LearningProgress?

    fun savePlaythrough(save: PlaythroughSave)
    fun playthroughs(projectId: ProjectId): List<PlaythroughSave>
    fun playthrough(id: SaveId): PlaythroughSave?

    /**
     * 在一个事务里执行 [block]。
     *
     * 实现必须保证：块内任何异常都回滚全部写入。
     * 正文版本、事实版本、角色状态、检查点、单元状态必须一起生效或一起不生效。
     */
    fun <T> transaction(block: () -> T): T
}

/** 提交被拒绝的原因。 */
sealed interface CommitRejection {
    /** 单元当前状态不允许提交（例如已经 READY）。 */
    data class WrongStatus(val actual: UnitStatus) : CommitRejection

    /** 基础状态版本已变化：说明有人改过状态，不能用旧补丁覆盖。 */
    data class StaleBaseVersion(val expected: Int, val actual: Int) : CommitRejection

    // 审查已移除，Blocked 拒绝类型随之删除。

    /** 幂等键已提交过：重复回调不应该再造一版。 */
    data class AlreadyCommitted(val revisionId: RevisionId) : CommitRejection

    /**
     * 状态补丁里出现了未注册的角色。
     *
     * 「没有已注册角色」不等于「接受任意角色 id」：那会让状态表被模型编造的名字污染，
     * 事后也没法判断那条状态属于谁。
     */
    data class UnknownCharacter(val ids: List<String>) : CommitRejection

    /** 状态补丁引用了本单元并不存在的事件：没有证据的变化不能生效。 */
    data class UnknownEvent(val ids: List<String>) : CommitRejection

    /** 生成输入已经过期（规格/总纲被改过）：旧候选不能覆盖新输入。 */
    data class StaleInput(val reason: String) : CommitRejection

    /** 执行者已过期（租约被换人或令牌落后）：旧执行者不能提交。 */
    data class StaleExecutor(val reason: String) : CommitRejection
}

/** 提交结果。 */
sealed interface CommitResult {
    data class Committed(val checkpoint: Checkpoint, val revision: UnitRevision) : CommitResult
    data class Rejected(val rejection: CommitRejection) : CommitResult
}
