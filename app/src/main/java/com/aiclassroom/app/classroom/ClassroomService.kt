package com.aiclassroom.app.classroom

import android.content.Context
import com.aiclassroom.classroom.commit.UnitCommitter
import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.exec.RunControl
import com.aiclassroom.classroom.exec.RunEngine
import com.aiclassroom.classroom.exec.RunOutcome
import com.aiclassroom.classroom.exec.Regeneration
import com.aiclassroom.classroom.exec.RunRecovery
import com.aiclassroom.classroom.exec.UnitExecutor
import com.aiclassroom.classroom.gateway.FinishReason
import com.aiclassroom.classroom.gateway.ModelPolicy
import com.aiclassroom.classroom.gateway.ModelRequests
import com.aiclassroom.classroom.query.ProjectContext
import com.aiclassroom.classroom.gateway.ModelMessage
import com.aiclassroom.classroom.gateway.ModelRequest
import com.aiclassroom.classroom.plan.PlannedProjectParser
import com.aiclassroom.classroom.prompt.PlannerBatch
import com.aiclassroom.classroom.prompt.PlannerPrompt
import com.aiclassroom.classroom.repository.ClassroomStore
import com.aiclassroom.classroom.store.FileClassroomStore
import java.io.File

/**
 * 课堂的宿主装配：存储、网关、执行器、编排器都在这里接起来。
 *
 * 刻意不持有 Compose 状态：任务生命周期不能被界面决定。
 * 界面（阶段 G）只调用这里的方法并观察返回值。
 */
private const val LEASE_TTL_MS = 30 * 60 * 1000L

class ClassroomService(
    context: Context,
    /** 对外可见：取消落盘等收尾逻辑需要同一时钟。 */
    val clock: () -> EpochMillis = { System.currentTimeMillis() },
    /**
     * 仓库实例由 [ClassroomServiceProvider] 统一提供。
     *
     * 每个页面各建一份 FileClassroomStore 时，两个实例各持一份内存快照，
     * 后台提交的内容会被页面的旧快照写回覆盖 —— 这是会真正丢数据的。
     */
    val store: ClassroomStore = ClassroomServiceProvider.store(context),
    /** 测试可注入模拟连接，生产仍使用唯一的真实网关装配。 */
    private val gatewayFactory: (ModelPolicy, String) -> com.aiclassroom.classroom.gateway.ModelGateway = ClassroomGatewayFactory::create,
) {

    /** 应用启动时调用一次：把上次遗留的「生成中」状态收拾干净。 */
    fun recover(): RunRecovery.Report = RunRecovery.recover(store, clock())

    fun projects(): List<ClassroomProject> = store.projects().sortedByDescending { it.updatedAt }

    /**
     * 某个对话自己的项目。
     *
     * 未关联的旧项目不在这里出现 —— 它们需要显式关联，不能被当前对话顺手接管。
     */
    fun projectsOf(sourceConversationId: String): List<ClassroomProject> =
        projects().filter { it.sourceConversationId == sourceConversationId }

    /** 全部项目：包含其他对话的、以及未关联的旧项目。 */
    fun allProjects(): List<ClassroomProject> = projects()

    /**
     * 调整单元方向或加注释。
     *
     * 只影响这个单元之后的生成：已提交的正文不动，但会标为 STALE ——
     * 因为约束变了，之前的内容不再能宣称「符合当前设定」。
     */
    fun updateDirective(
        projectId: ProjectId,
        unitId: UnitId,
        goalOverride: String?,
        notes: String,
    ) {
        val unit = store.unit(unitId) ?: return
        val cleanGoal = goalOverride?.takeIf { it.isNotBlank() }
        val directive = UnitDirective(cleanGoal, notes.trim(), clock())
        val next = directive.takeUnless { it.isEmpty }
        val status = if (unit.status == UnitStatus.READY) UnitStatus.STALE else unit.status
        store.upsertUnit(unit.copy(directive = next, status = status))
        store.project(projectId)?.let {
            store.updateProject(it.copy(updatedAt = clock()))
        }
    }

    fun createProject(
        title: String,
        type: ProjectType,
        rawRequest: String,
        /** 来源对话 id：项目归属靠它，不靠「当前打开的是哪个」。 */
        sourceConversationId: String? = null,
        sourceDisplayName: String? = null,
    ): ClassroomProject {
        val now = clock()
        val project = ClassroomProject(
            id = ProjectId.new(),
            title = title,
            type = type,
            lifecycle = ProjectLifecycle.DRAFT,
            createdAt = now,
            updatedAt = now,
            sourceConversationId = sourceConversationId,
            sourceDisplayNameSnapshot = sourceDisplayName,
            binding = if (sourceConversationId != null) BindingStatus.BOUND else BindingStatus.UNBOUND,
        )
        store.createProject(project)
        // 原始需求立刻落库：之后规划再改也回得到用户最初的话。
        store.saveSpec(
            ProjectSpec(
                projectId = project.id,
                version = 1,
                rawRequest = rawRequest,
                topic = "",
                goals = emptyList(),
                audience = "",
                language = "zh",
                style = "",
                expectedLength = "",
                unitCount = 0,
                runMode = RunMode.CONFIRM_OUTLINE,
                reviewPolicy = ReviewPolicy.STANDARD,
                hardConstraints = emptyList(),
                authorRules = emptyList(),
                publicWorld = "",
                createdAt = now,
            ),
        )
        return project
    }

    /** 规划结果。 */
    sealed interface PlanResult {
        data class Success(val unitCount: Int) : PlanResult
        data class Invalid(val problems: List<String>) : PlanResult
        data class Failed(val reason: String) : PlanResult
    }

    /** 阅读进度：重启后回到上次那一节。 */
    fun readerProgress(projectId: ProjectId) = store.readerProgress(projectId)

    fun saveReaderProgress(projectId: ProjectId, unitId: UnitId, workVersion: Int) {
        store.saveReaderProgress(
            ReaderProgress(
                projectId = projectId,
                workVersion = workVersion,
                unitId = unitId,
                offset = 0,
                updatedAt = clock(),
            ),
        )
    }

    /** 已发布版本号（新到旧）。 */
    fun workVersions(projectId: ProjectId): List<Int> =
        com.aiclassroom.classroom.domain.ManifestResolver.versions(store, projectId)

    /**
     * 正在写的这一节：把草稿里已通过校验的分段按序拼起来。
     *
     * 只用于阅读展示，不进入正式作品：它没有经过整节审查与提交，
     * 项目检索、讨论与版本清单都不会读它。
     */
    fun draftContent(projectId: ProjectId, unitId: UnitId): com.aiclassroom.classroom.domain.UnitContent? {
        val draft = store.draft(projectId, unitId) ?: return null
        if (draft.segments.isEmpty()) return null
        val unit = store.unit(unitId)
        return com.aiclassroom.classroom.domain.UnitContent(
            unitId = unitId,
            planVersion = unit?.officialVersion ?: 0,
            title = unit?.chapter.orEmpty(),
            blocks = draft.segments.sortedBy { it.index }.flatMap { it.blocks },
            events = draft.segments.sortedBy { it.index }.flatMap { it.events },
            unresolvedItems = emptyList(),
        )
    }

    /** 按版本读某一节；旧项目没有清单时返回 Legacy。 */
    fun revisionAt(projectId: ProjectId, unitId: UnitId, workVersion: Int?) =
        com.aiclassroom.classroom.domain.ManifestResolver.revisionAt(
            store = store, projectId = projectId, unitId = unitId, workVersion = workVersion,
        )

    /**
     * 从正式作品里取相关片段，供对话讨论使用。
     *
     * 只读已提交的 revision（不读草稿、不调模型）；取不到就返回空。
     */
    fun projectContext(
        projectId: ProjectId,
        question: String,
        maxExcerpts: Int = 3,
        authorView: Boolean = true,
    ): ProjectContext? =
        com.aiclassroom.classroom.query.ProjectRetrieval.forProject(
            store = store,
            projectId = projectId,
            question = question,
            maxExcerpts = maxExcerpts,
            authorView = authorView,
        )

    /** 当前对话下可讨论的项目（用于消歧：多个项目并存时不要偷偷选最后一个）。 */
    fun discussableProjects(sourceConversationId: String): List<ClassroomProject> =
        projectsOf(sourceConversationId)

    /**
     * 拍一张来源上下文快照。
     *
     * 只由调用方传入**已经整理好的**内容（人格、世界书、资料、事实边界），
     * 宿主负责从对话里取；这里不做「猜」的解析，也不碰凭据。
     */
    fun saveSnapshot(
        projectId: ProjectId,
        mentorName: String,
        mentorPrompt: String,
        userAlias: String,
        viewMode: String,
        activeCharacterId: String?,
        publicWorldBook: String,
        authorWorldBook: String,
        characters: List<SnapshotCharacter>,
        materials: List<SnapshotMaterial>,
        facts: List<com.aiclassroom.classroom.domain.SnapshotFact>,
        historyThroughIndex: Int,
        sourceConversationId: String?,
    ): ContextSnapshot {
        val previous = store.latestSnapshot(projectId)
        val snapshot = ContextSnapshot(
            id = SnapshotId.new(),
            projectId = projectId,
            version = (previous?.version ?: 0) + 1,
            sourceConversationId = sourceConversationId,
            createdAt = clock(),
            mentorName = mentorName,
            mentorPrompt = mentorPrompt,
            userAlias = userAlias,
            viewMode = viewMode,
            activeCharacterId = activeCharacterId,
            publicWorldBook = publicWorldBook,
            authorWorldBook = authorWorldBook,
            // 首次创建时由宿主映射来源角色卡；之后的快照沿用同一批项目角色，
            // 不能因为来源改卡就换一套 id，否则人物状态会串。
            characters = characters.ifEmpty { previous?.characters.orEmpty() },
            materials = materials,
            historyThroughIndex = historyThroughIndex,
            facts = facts,
        )
        store.saveSnapshot(snapshot)
        // 人物也要真正注册进项目：否则状态校验那一侧不认识他们，
        // 提示词里写着有这些人，校验却按空集合处理。
        com.aiclassroom.classroom.domain.CharacterRegistration.register(
            store = store,
            projectId = projectId,
            characters = snapshot.characters,
        )
        return snapshot
    }

    /**
     * 同步来源设定：拍一张新快照，并把已完成的单元保守标记为待复查。
     *
     * 只由用户显式触发 —— 来源改了什么不会自动改写正在跑的任务。
     */
    fun syncSource(
        projectId: ProjectId,
        mentorName: String,
        mentorPrompt: String,
        userAlias: String,
        viewMode: String,
        activeCharacterId: String?,
        publicWorldBook: String,
        authorWorldBook: String,
        characters: List<SnapshotCharacter>,
        materials: List<SnapshotMaterial>,
        facts: List<com.aiclassroom.classroom.domain.SnapshotFact>,
        historyThroughIndex: Int,
        sourceConversationId: String?,
    ): Pair<ContextSnapshot, Int> {
        val snapshot = saveSnapshot(
            projectId = projectId,
            mentorName = mentorName,
            mentorPrompt = mentorPrompt,
            userAlias = userAlias,
            viewMode = viewMode,
            activeCharacterId = activeCharacterId,
            publicWorldBook = publicWorldBook,
            authorWorldBook = authorWorldBook,
            characters = characters,
            materials = materials,
            facts = facts,
            historyThroughIndex = historyThroughIndex,
            sourceConversationId = sourceConversationId,
        )
        val stale = com.aiclassroom.classroom.exec.Regeneration.markReadyStale(store, projectId)
        store.project(projectId)?.let {
            store.updateProject(it.copy(lifecycle = ProjectLifecycle.NEEDS_ATTENTION, updatedAt = clock()))
        }
        return snapshot to stale
    }

    /**
     * 生成规格与总纲。成功后写入规格 v2、总纲 v1，并把每个单元建成 PLANNED。
     *
     * 这一步不改正文，也不消耗生成预算 —— 用户在总览里看到单元后才会点「开始生成」。
     */
    suspend fun plan(
        projectId: ProjectId,
        policy: ModelPolicy,
        apiKey: String,
        desiredUnits: Int = 8,
    ): PlanResult {
        val project = store.project(projectId) ?: return PlanResult.Failed("项目不存在")
        val rawRequest = store.latestSpec(projectId)?.rawRequest.orEmpty()
        if (rawRequest.isBlank()) return PlanResult.Failed("没有原始需求")


        val gateway = gatewayFactory(policy, apiKey)
        // 单元多时先要骨架，细节随后分批追问：一次写完所有单元的目标必然被输出上限截断，
        // 正确做法是分几次写、由程序合并，而不是让模型少写细节。
        val skeletonFirst = desiredUnits > PlannerPrompt.BATCH
        val call = gateway.complete(
            ModelRequests.fromPolicy(
                policy = policy,
                messages = listOf(
                    ModelMessage(
                        ModelMessage.Role.USER,
                        PlannerPrompt.build(
                            rawRequest = rawRequest,
                            type = project.type,
                            desiredUnits = desiredUnits,
                            snapshot = store.latestSnapshot(projectId),
                        ),
                    ),
                ),
                expectJson = true,
            ),
        )
        if (call.error != null) return PlanResult.Failed("调用失败：" + call.error)
        if (call.finishReason is FinishReason.Length) return PlanResult.Failed("规划输出被截断")

        val parsed = PlannedProjectParser.parse(
            raw = call.content,
            projectId = projectId,
            rawRequest = rawRequest,
            fallbackUnitCount = desiredUnits,
            now = clock(),
        )
        if (!parsed.ok) {
            return PlanResult.Invalid(parsed.problems.map { it.message })
        }
        val spec = parsed.spec!!
        var outline = parsed.outline!!
        if (skeletonFirst) {
            val titles = outline.units.sortedBy { it.index }.map { it.title }
            val details = mutableMapOf<Int, PlannerBatch.UnitDetail>()
            var from = 1
            while (from <= titles.size) {
                val to = minOf(from + PlannerPrompt.BATCH - 1, titles.size)
                val detailCall = gateway.complete(
                    ModelRequests.fromPolicy(
                        policy = policy,
                        messages = listOf(
                            ModelMessage(
                                ModelMessage.Role.USER,
                                PlannerBatch.request(rawRequest, project.type, titles, from, to),
                            ),
                        ),
                        expectJson = true,
                    ),
                )
                if (detailCall.error == null) {
                    PlannerBatch.parse(detailCall.content, from, to).forEach { details[it.index] = it }
                }
                from = to + 1
            }
            if (details.isEmpty()) return PlanResult.Failed("分批补写单元细节失败")
            outline = outline.copy(
                units = outline.units.map { u ->
                    val d = details[u.index] ?: return@map u
                    u.copy(goal = d.goal, completionCriteria = d.criteria.ifEmpty { u.completionCriteria })
                },
            )
        }

        store.transaction {
            store.saveSpec(spec)
            store.saveOutline(outline)
            outline.units.forEach { planned ->
                store.upsertUnit(
                    ClassroomUnit(
                        id = planned.unitId,
                        projectId = projectId,
                        index = planned.index,
                        type = project.type,
                        chapter = planned.title,
                        goal = planned.goal,
                        dependsOn = planned.dependsOn,
                        requiredCharacters = planned.requiredCharacters,
                        completionCriteria = planned.completionCriteria,
                        inputStateVersion = 0,
                        status = UnitStatus.PLANNED,
                    ),
                )
            }
            store.updateProject(
                project.copy(
                    lifecycle = ProjectLifecycle.PLAN_READY,
                    specVersion = spec.version,
                    outlineVersion = outline.version,
                    updatedAt = clock(),
                ),
            )
        }
        return PlanResult.Success(outline.units.size)
    }

    /**
     * 重写待复查的单元。
     *
     * 只把那几节放回队列再跑一轮，其余已完成的单元不受影响；
     * 旧版本不会被删，提交时会标成「被替代」，历史仍可回溯。
     */
    suspend fun regenerateStale(
        projectId: ProjectId,
        policy: ModelPolicy,
        apiKey: String,
        control: RunControl,
        forceLease: Boolean = false,
    ): RunOutcome? {
        val reset = Regeneration.resetStale(store, projectId)
        if (!reset.any) return null
        return run(projectId, policy, apiKey, control, forceLease = forceLease)
    }

    /** 当前项目里待复查的单元数。 */
    fun staleCount(projectId: ProjectId): Int =
        store.units(projectId).count { it.status == UnitStatus.STALE }

    /**
     * 跑一个任务。密钥在执行时才解析，不写进任何课堂数据。
     */
    suspend fun run(
        projectId: ProjectId,
        policy: ModelPolicy,
        apiKey: String,
        control: RunControl,
        /** Worker 传 true：uniqueWork 已保证唯一执行者，可以接管被杀进程留下的租约。 */
        forceLease: Boolean = false,
    ): RunOutcome {
        // 开跑之前先把上次遗留的「生成中 / 草稿待提交」放回队列，
        // 否则那些单元永远不会被重新选中，后面的依赖链会整体卡死。
        // 只恢复当前项目中没有有效执行者的遗留步骤；不能因为启动项目 B
        // 就把项目 A 的 Worker 和已提交检查点重置掉。
        val now = clock()
        val runId = RunId.new()

        val units = store.units(projectId)
        store.saveRun(
            GenerationRun(
                id = runId,
                projectId = projectId,
                specVersion = store.latestSpec(projectId)?.version ?: 1,
                outlineVersion = store.latestOutline(projectId)?.version ?: 0,
                phase = RunPhase.GENERATING,
                status = RunStatus.QUEUED,
                totalUnits = units.size,
                startedAt = now,
                updatedAt = now,
                // 冻结本轮策略：中途切换对话/改模型都不影响这次运行。
                frozenProvider = policy.provider,
                frozenPrimaryModel = policy.primaryModel,
                frozenFallbackModels = policy.fallbackModels,
            ),
        )

        val gateway = gatewayFactory(policy, apiKey)
        val committer = UnitCommitter(store, clock)
        // 任务已落库，现在抢执行权：同一任务同时只能有一个有效提交者。
        val owner = "owner-" + java.util.UUID.randomUUID()
        // 启动前清掉「占着项目但其实已死」的执行者（owner 还在、租约过期），
        // 否则新任务永远拿到 Busy，界面看起来就是点了没反应。
        RunRecovery.cleanupStale(store, projectId, clock())

        val leaseToken = when (val granted = RunLease.acquireForProject(
            store = store,
            projectId = projectId,
            runId = runId,
            owner = owner,
            ttlMillis = LEASE_TTL_MS,
            clock = clock,
            force = forceLease,
        )) {
            is RunLease.Acquired.Granted -> granted.lease.token
            is RunLease.Acquired.Busy -> {
                // 不能静默返回：把原因写进 run，界面与任务卡都能看到「已有任务在跑」。
                store.run(runId)?.let {
                    store.saveRun(
                        it.copy(
                            status = RunStatus.WAITING_INPUT,
                            error = "已有任务正在运行（" + granted.owner + "），稍后再试",
                            updatedAt = clock(),
                        ),
                    )
                }
                return RunOutcome.NeedsAttention(0, "已有任务正在运行，稍后再试")
            }
            is RunLease.Acquired.NotFound -> return RunOutcome.NeedsAttention(0, granted.reason)
        }

        // 取得项目执行权后才恢复单元；此前不能触碰其他仍有效执行者的现场。
        store.transaction {
            for (u in store.units(projectId)) {
                if (u.status in setOf(UnitStatus.GENERATING, UnitStatus.REVIEWING, UnitStatus.REVISING, UnitStatus.DRAFT_READY))
                    store.upsertUnit(u.copy(status = UnitStatus.PLANNED))
            }
            store.project(projectId)?.let {
                store.updateProject(it.copy(lifecycle = ProjectLifecycle.ACTIVE, currentRunId = runId, updatedAt = now))
            }
        }

        val executor = UnitExecutor(
            gateway = gateway,
            store = store,
            committer = committer,
            clock = clock,
            policy = policy,
            runOwner = owner,
            fencingToken = leaseToken,
            drafts = com.aiclassroom.classroom.repository.DraftStore(store, clock),
        )
        return try {
            RunEngine(store, executor, control, clock, owner = owner, fenceToken = leaseToken)
                .run(projectId, runId)
        } finally {
            // 无论正常结束还是被取消都要释放，否则这个任务再也抢不到执行权。
            RunLease.release(store, runId, owner, clock)
        }
    }
}
