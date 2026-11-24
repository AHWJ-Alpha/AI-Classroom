package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.commit.CommitRequest
import com.aiclassroom.classroom.commit.UnitCommitter
import com.aiclassroom.classroom.content.ReviewParser
import com.aiclassroom.classroom.content.StatePatchParser
import com.aiclassroom.classroom.content.UnitContentParser
import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.gateway.FinishReason
import com.aiclassroom.classroom.gateway.ModelCallResult
import com.aiclassroom.classroom.gateway.ModelGateway
import com.aiclassroom.classroom.gateway.ModelPolicy
import com.aiclassroom.classroom.gateway.ModelRequests
import com.aiclassroom.classroom.gateway.ModelMessage
import com.aiclassroom.classroom.gateway.ModelRequest
import com.aiclassroom.classroom.prompt.PromptTemplates
import com.aiclassroom.classroom.repository.ClassroomStore
import com.aiclassroom.classroom.repository.CommitResult
import com.aiclassroom.classroom.repository.DraftStore
import com.aiclassroom.classroom.repository.DraftWriteGuard
import com.aiclassroom.classroom.store.ContentCodec
import com.aiclassroom.classroom.json.JsonWriter
import kotlinx.coroutines.*

sealed interface UnitOutcome {
    data class Committed(val revisionId: RevisionId, val checkpoint: Checkpoint) : UnitOutcome
    /** 需要人处理：反复审查不通过、输出结构不对、预算用完。 */
    data class NeedsAttention(val reason: String) : UnitOutcome
    /** 调用失败（网络/认证/截断），可再试。 */
    data class Failed(val reason: String) : UnitOutcome
    /**
     * 网络断了，不是任务出错。
     *
     * 和 Failed 分开：这种情况应当**保留进度、稍后自动继续**，
     * 而不是把整轮标成「需要处理」让用户手动重来。
     */
    data class NetworkInterrupted(val reason: String) : UnitOutcome
    data object AlreadyDone : UnitOutcome
}

/**
 * 单元执行器：一个单元从生成到提交的完整过程。
 *
 * 顺序是固定的，每一步都不能跳过：
 * 生成草稿 → 结构校验 → 内容审查 → 有阻断问题就局部修订（有上限）→
 * 从**最终**候选正文提取状态补丁 → 事务提交 → 检查点。
 *
 * 特别注意：状态补丁只从最终正文提取。用修订前的草稿提补丁，
 * 会把已经删掉的剧情写进角色记忆。
 */
private const val LEASE_TTL_MS = 30 * 60 * 1000L

class UnitExecutor(
    private val gateway: ModelGateway,
    private val store: ClassroomStore,
    private val committer: UnitCommitter,
    private val clock: () -> EpochMillis,
    private val maxReviseAttempts: Int = 2,
    /** 冻结的模型策略：所有阶段的请求都由它构造，避免各调用点漏参数。 */
    private val policy: ModelPolicy = ModelPolicy("", "", "default"),
    /** 执行者与令牌：随提交一起校验，挡住过期执行者。 */
    private val runOwner: String? = null,
    private val fencingToken: Long? = null,
    private val drafts: DraftStore = DraftStore(store, clock),
    /**
     * 来源对话最近的几轮原文。
     *
     * 没有它，课堂只知道一句原始需求，写出来的故事与对话里已经发生的剧情无关。
     */
    private val priorTurns: List<Pair<String, String>> = emptyList(),
    /**
     * 来源对话的完整系统提示词。
     *
     * 课堂不能只搬 mentorPrompt 一句话：对话真正的人格还包括一致性规则、
     * 回复边界、公开背景、作者世界书与角色卡。
     */
    private val sourcePersona: String = "",
) : UnitRunner {

    override suspend fun execute(projectId: ProjectId, unitId: UnitId, runId: RunId): UnitOutcome {
        val unit = store.unit(unitId) ?: return UnitOutcome.Failed("单元不存在")
        if (unit.status == UnitStatus.READY) return UnitOutcome.AlreadyDone

        // 每开一章续一次租约，并且**核对续租结果**：
        // TTL 只有 30 分钟，长任务不续中途就会过期；而执行权被别人接管时，
        // 这一章哪怕生成出来也提交不了，不如立刻停下，别白烧 token。
        if (runOwner != null) {
            val renewed = RunLease.renew(store, runId, runOwner, LEASE_TTL_MS, clock)
            val check = RunLease.validateForProject(store, projectId, runId, runOwner, fencingToken ?: 0, clock)
            if (!renewed || check is RunLease.Validation.Invalid) {
                val reason = (check as? RunLease.Validation.Invalid)?.reason ?: "续租失败"
                return UnitOutcome.NeedsAttention("执行权已被接管：" + reason)
            }
        }

        val project = store.project(projectId) ?: return UnitOutcome.Failed("项目不存在")
        val spec = store.latestSpec(projectId) ?: return UnitOutcome.NeedsAttention("没有规格，无法生成")
        val outline = store.latestOutline(projectId)
            ?: return UnitOutcome.NeedsAttention("没有总纲，无法生成")
        val planned = outline.units.firstOrNull { it.unitId == unitId }
            ?: return UnitOutcome.NeedsAttention("总纲里找不到这个单元")

        // 租约续期与执行权核对见 execute 开头。

        // 来源快照：人格、角色、资料都以它为准，不随来源后来的修改漂移。
        val snapshot = store.latestSnapshot(projectId)
        val characters = store.characters(projectId)
        // 重写早期章节时用「当时的」状态：否则第 2 章会用第 5 章结束时的关系与情绪说话。
        val isRewrite = StateTimeline.isRewrite(store, projectId, unit.index)
        val states = if (isRewrite) {
            StateTimeline.statesBefore(store, projectId, unit.index).values.toList()
        } else {
            store.characterStates(projectId)
        }
        // 总纲声明的角色也在提交器的合法身份集合内。
        val knownCharacters = (characters.map { it.id } + unit.requiredCharacters).toSet()
        // 角色视角只给可见事实；作者视角给全量。
        val visibleFacts = FactsForView.factsFor(snapshot, store.facts(projectId))

        if (unit.type == ProjectType.SCENE) {
            return executeScene(projectId, unit, runId, spec, outline, planned, snapshot, characters, states, visibleFacts)
        }

        var revisionId = RevisionId.new()
        var attempt = 0
        var lastReason = ""
        var text = ""
        var content: UnitContent? = null
        // 只有审查明确说过「没有阻断问题」才允许提交；循环跑完但没通过，仍是未通过。
        var approved = false
        // 审查报告必须随正式版本保存：否则用户看不到「这章为什么被判有问题」。
        var approvedReview: ReviewReport? = null

        while (attempt <= maxReviseAttempts) {
            val prompt = if (attempt == 0) {
                PromptTemplates.generator(
                    PromptTemplates.GeneratorInput(
                        spec = spec,
                        outlineSummary = outline.mainLine + "；" + outline.globalGoal,
                        unit = planned,
                        characters = characters,
                        characterStates = states,
                        facts = visibleFacts,
                        previousText = previousText(projectId, unit, outline),
                        directive = unit.directive,
                        projectType = unit.type,
                        unitIndex = outline.units.map { it.index to it.unitId },
                        snapshot = snapshot,
                    ),
                )
            } else {
                // 修订：把阻断问题原样交给模型，并要求只改这些问题。
                PromptTemplates.generator(
                    PromptTemplates.GeneratorInput(
                        spec = spec,
                        outlineSummary = "上一版被审查拦住：" + lastReason,
                        unit = planned,
                        characters = characters,
                        characterStates = states,
                        facts = visibleFacts,
                        // 修订也要拿到同一份跨章上下文：只给候选正文的话，
                        // 改这一节时会把前面几章的摘要与伏笔全丢掉。
                        previousText = previousText(projectId, unit, outline) +
                            "\n\n[待修订的当前稿]\n" + text.take(4000),
                        directive = unit.directive,
                        projectType = unit.type,
                        unitIndex = outline.units.map { it.index to it.unitId },
                        snapshot = snapshot,
                    ),
                )
            }

            var result = gateway.complete(
                ModelRequests.fromPolicy(
                    policy = policy,
                    messages = listOf(ModelMessage(ModelMessage.Role.USER, prompt)),
                    expectJson = true,
                ),
            )
            recordAttempt(runId, unitId, RunPhase.GENERATING, prompt.hashCode().toString(), result)
            if (result.error != null) {
                val err = result.error!!
                // 网络类错误先就地重试一次：多半是瞬时抖动，不值得让整轮停下。
                if (err is com.aiclassroom.classroom.gateway.ModelCallError.Network ||
                    err is com.aiclassroom.classroom.gateway.ModelCallError.Timeout
                ) {
                    val retry = gateway.complete(
                        ModelRequests.fromPolicy(
                            policy = policy,
                            messages = listOf(ModelMessage(ModelMessage.Role.USER, prompt)),
                            expectJson = true,
                        ),
                    )
                    recordAttempt(runId, unitId, RunPhase.GENERATING, prompt.hashCode().toString(), retry)
                    if (retry.error == null && retry.finishReason !is FinishReason.Length) {
                        // 重试成功，继续正常流程。
                        result = retry
                        lastReason = ""
                    } else {
                        return UnitOutcome.NetworkInterrupted(describe(retry))
                    }
                } else {
                    return UnitOutcome.Failed(describe(result))
                }
            }
            // 截断的输出不能当作完整正文：宁可重试，也不要写进正式内容。
            if (result.finishReason is FinishReason.Length) {
                return UnitOutcome.Failed("输出被截断（到达 max_tokens）")
            }
            text = result.content

            val parsed = UnitContentParser.parse(text, unitId, outline.version, knownCharacters, unit.type)
            if (!parsed.ok) {
                lastReason = parsed.problems.joinToString("；")
                attempt++
                continue
            }
            content = parsed.content

            // 审查是独立的一次模型调用：不能拿「生成器自己觉得没问题」当通过。
            val reviewCall = gateway.complete(
                ModelRequests.fromPolicy(
                    policy = policy,
                    messages = listOf(
                        ModelMessage(
                            ModelMessage.Role.USER,
                            PromptTemplates.reviewer(
                                PromptTemplates.ReviewerInput(
                                    spec = spec,
                                    unit = planned,
                                    text = text,
                                    snapshot = snapshot,
                                    directiveNotes = unit.directive?.notes.orEmpty(),
                                    effectiveGoal = unit.directive?.effectiveGoal(planned.goal),
                                    projectType = unit.type,
                                ),
                            ),
                        ),
                    ),
                    expectJson = true,
                ),
            )
            recordAttempt(runId, unitId, RunPhase.REVIEWING, text.hashCode().toString(), reviewCall)
            if (reviewCall.error != null) return UnitOutcome.Failed(describe(reviewCall))
            val review = ReviewParser.parse(reviewCall.content, revisionId, clock())
            // 审查输出读不懂 = 没有通过审查，不能放行。
            if (review.report == null) {
                lastReason = review.problems.joinToString("；")
                attempt++
                continue
            }
            if (!review.report.hasBlocking) {
                approved = true
                approvedReview = review.report
                break
            }

            lastReason = review.report.issues.filter { it.blocking }.joinToString("；") { it.rule + "：" + it.evidence }
            attempt++
        }

        if (content == null) return UnitOutcome.NeedsAttention("生成失败：" + lastReason)
        if (!approved) return UnitOutcome.NeedsAttention("审查未通过：" + lastReason)

        // 草稿置为可提交状态：提交器只接受 DRAFT_READY / REVIEWING / REVISING。
        // 之前漏了这一步，解析成功、审查也通过却永远提交不了。
        store.upsertUnit(unit.copy(status = UnitStatus.DRAFT_READY))

        // 状态补丁只从**最终**候选正文提取。用修订前的草稿提补丁，
        // 会把已经删掉的剧情写进角色记忆。提不出来就当作没有，正文照样提交。
        val patchResult = proposePatch(projectId, unitId, revisionId, content, knownCharacters, planned, visibleFacts.map { it.factId }, unit.type)
        if (patchResult is PatchResult.Failed) {
            // 需要状态变化却提不出来时宁可停下，也不要把正文提交成「已一致」。
            return UnitOutcome.NeedsAttention("状态提取失败：" + patchResult.reason)
        }
        val patch = if (patchResult is PatchResult.Ready) patchResult.patch else null

        val revision = UnitRevision(
            id = revisionId,
            unitId = unitId,
            projectId = projectId,
            version = 0,
            draftText = text,
            content = content,
            review = approvedReview,
            modelName = policy.primaryModel,
            createdAt = clock(),
            sourceHash = text.hashCode().toString(),
        )

        val outcome = committer.commit(
                CommitRequest(
                    projectId = projectId,
                    unitId = unitId,
                    revision = revision,
                    statePatch = patch,
                    runId = runId,
                    idempotencyKey = runId.value + ":" + unitId.value + ":" + revisionId.value,
                    // 生成时冻结的版本：提交前若被改过，说明这次候选已经过期。
                    expectedSpecVersion = spec.version,
                    expectedOutlineVersion = outline.version,
                    owner = runOwner,
                    fencingToken = fencingToken,
                ),
        )
        return when (outcome) {
            is CommitResult.Committed -> UnitOutcome.Committed(outcome.revision.id, outcome.checkpoint)
            is CommitResult.Rejected -> UnitOutcome.NeedsAttention("提交被拒绝：" + outcome.rejection)
        }
    }

    /** 实际产品 SCENE 入口：恢复同库草稿，完整正文经审查后才允许提交。 */
    private suspend fun executeScene(
        projectId: ProjectId, unit: ClassroomUnit, runId: RunId,
        spec: ProjectSpec, outline: ProjectOutline, planned: PlannedUnit,
        snapshot: ContextSnapshot?, characters: List<CharacterProfile>,
        states: List<CharacterState>, facts: List<FactRevision>,
    ): UnitOutcome = coroutineScope {
        val owner = runOwner ?: return@coroutineScope UnitOutcome.NeedsAttention("场景必须通过持久执行入口取得执行权")
        val token = fencingToken ?: return@coroutineScope UnitOutcome.NeedsAttention("缺少执行令牌")
        val plan = LengthPlan.parse(spec.rawRequest + "\n" + spec.expectedLength, ProjectType.SCENE, outline.units.size)
        if (plan !is LengthPlan.Ok) return@coroutineScope UnitOutcome.NeedsAttention((plan as LengthPlan.Unsupported).reason)
        val inputNow = { SceneInputVersion.compute(store, projectId, unit.id, policy) }
        val input = store.transaction { inputNow() }
        val old = drafts.current(projectId, unit.id)
        val resume = old?.takeIf { it.inputVersion == input && it.phase != DraftPhase.INVALIDATED && it.phase != DraftPhase.COMMITTED }
        val candidateId = resume?.candidateId ?: java.util.UUID.randomUUID().toString()
        var candidateVersion = resume?.candidateVersion ?: 0
        var guard = DraftWriteGuard(projectId, unit.id, candidateId, input, candidateVersion, runId, owner, token)
        val revisionId = RevisionId(candidateId)
        val effectivePlan = planned.copy(goal = unit.directive?.effectiveGoal(planned.goal) ?: planned.goal)
        // 复用原生成器的权限过滤与资料检索；删除旧单次输出和篇幅契约。
        val context = PromptTemplates.generator(PromptTemplates.GeneratorInput(
            spec = spec, outlineSummary = outline.mainLine + "；" + outline.globalGoal, unit = effectivePlan,
            characters = characters, characterStates = states, facts = facts,
            previousText = previousText(projectId, unit, outline), directive = unit.directive,
            snapshot = snapshot, projectType = ProjectType.SCENE,
        )).substringBefore("[OUTPUT_SCHEMA]").lineSequence()
            .filterNot { it.contains("本节正文不少于") }.joinToString("\n")
            // 来源对话的最近几轮：课堂要接着对话里已经发生的处境写，
            // 否则人物关系会从零重建，和用户聊过的内容完全脱节。
            .let { base ->
                if (priorTurns.isEmpty() && sourcePersona.isBlank()) base else base + "\n\n" + buildString {
                    if (sourcePersona.isNotBlank()) {
                        // 完整人格优先于任何摘要：规则、边界、世界书都在里面。
                        appendLine("[SOURCE_PERSONA]")
                        appendLine("以下是这个对话本身的设定，必须遵守：")
                        appendLine(sourcePersona)
                        appendLine("[/SOURCE_PERSONA]")
                        appendLine()
                    }
                    if (priorTurns.isEmpty()) return@buildString
                    appendLine("[DATA:PRIOR_CONTEXT]")
                    // 这些是**背景记忆**，不是本节的开头。
                    // 以前写成「接着它写」，模型会把对话原文当成场景开场重写一遍。
                    appendLine("以下是你们之前已经发生过的事。它们属于**背景记忆**，不是本节的起点：")
                    priorTurns.forEach { (role, text) ->
                        val who = if (role == "user") "我" else "对方"
                        appendLine("- " + who + "：" + text)
                    }
                    appendLine("[/DATA:PRIOR_CONTEXT]")
                    appendLine("规则：")
                    appendLine("- 不要复述、不要重演上面的内容，也不要从它们开始写；")
                    appendLine("- 本节的时间从更靠后的地方开始；")
                    appendLine("- 只把它们当作已经成立的关系、称呼与已知事实。")
                }
            }
        val parent = currentCoroutineContext().job
        val heartbeat = launch {
            while (isActive) {
                delay(30_000)
                if (!RunLease.renew(store, runId, owner, LEASE_TTL_MS, clock)) {
                    parent.cancel(CancellationException("执行租约已失效"))
                    return@launch
                }
            }
        }
        try {
            val generated = SceneContinuation(gateway, store, drafts, clock).run(SceneContinuation.Request(
                projectId, unit.copy(goal = effectivePlan.goal), spec, outline.mainLine, planned.title,
                characters, plan, input, candidateId, candidateVersion, runId, owner, token, policy,
                planVersion = outline.version, frozenContext = context, inputVersionNow = inputNow,
                onAttempt = { prompt, result -> recordAttempt(runId, unit.id, RunPhase.GENERATING, prompt.hashCode().toString(), result) },
            ))
            var content = when (generated) {
                is SceneContinuation.Result.Paused -> return@coroutineScope UnitOutcome.NetworkInterrupted(generated.reason)
                is SceneContinuation.Result.NeedsAttention -> return@coroutineScope UnitOutcome.NeedsAttention(generated.reason)
                is SceneContinuation.Result.Assembled -> generated.content
            }
            var text = JsonWriter.write(ContentCodec.encode(content))
            // 不截取正文：每个块和每个事件都进入审查，包括末尾的内容。
            var review = drafts.current(projectId, unit.id)?.review
            var revisionRounds = 0
            while (review == null) {
                val reserved = drafts.reserveCall(guard, inputNow)
                if (!reserved.ok()) return@coroutineScope UnitOutcome.NeedsAttention("审查预算/输入失效：$reserved")
                val prompt = PromptTemplates.reviewer(PromptTemplates.ReviewerInput(
                    spec, effectivePlan, text, snapshot = snapshot, directiveNotes = unit.directive?.notes.orEmpty(),
                    effectiveGoal = effectivePlan.goal, projectType = ProjectType.SCENE,
                ))
                // 审查要读整节正文，请求体大、耗时长；真机上偶发读超时或流被截断。
                // 这种情况重试一次就够，不该让整节卡在待处理。
                var call = gateway.complete(ModelRequests.fromPolicy(policy.copy(
                    maxOutputTokens = policy.maxOutputTokens ?: 8192,
                ),
                    listOf(ModelMessage(ModelMessage.Role.USER, prompt)), expectJson = true))
                recordAttempt(runId, unit.id, RunPhase.REVIEWING, input, call)
                if (call.error is com.aiclassroom.classroom.gateway.ModelCallError.Network ||
                    call.error is com.aiclassroom.classroom.gateway.ModelCallError.Timeout ||
                    call.error is com.aiclassroom.classroom.gateway.ModelCallError.Truncated ||
                    (call.error == null && (!call.transportCompleted || call.finishReason is FinishReason.Length))) {
                    val retryBudget = drafts.reserveCall(guard, inputNow)
                    if (!retryBudget.ok()) return@coroutineScope UnitOutcome.NeedsAttention("审查重试预算不足，正文保留")
                    val retryPrompt = prompt + "\n只输出简短 JSON，issues 最多 4 条，每条证据最多 60 字，无问题直接 {\"issues\":[]}。不要复制原文。"
                    call = gateway.complete(ModelRequests.fromPolicy(policy.copy(
                        maxOutputTokens = policy.maxOutputTokens ?: 8192,
                    ),
                        listOf(ModelMessage(ModelMessage.Role.USER, retryPrompt)), expectJson = true))
                    recordAttempt(runId, unit.id, RunPhase.REVIEWING, input, call)
                }
                if (call.error is com.aiclassroom.classroom.gateway.ModelCallError.Network || call.error is com.aiclassroom.classroom.gateway.ModelCallError.Timeout)
                    return@coroutineScope UnitOutcome.NetworkInterrupted("审查网络中断，已保存正文保留")
                if (call.error != null) return@coroutineScope UnitOutcome.Failed(describe(call))
                if (!call.transportCompleted || call.finishReason is FinishReason.Length)
                    return@coroutineScope UnitOutcome.NeedsAttention("审查输出不完整，候选已保留")
                review = ReviewParser.parse(call.content, revisionId, clock()).report
                    ?: return@coroutineScope UnitOutcome.NeedsAttention("审查格式无效，候选已保留")
                if (review.hasBlocking) {
                    val issues = review.issues.filter { it.blocking }
                    if (revisionRounds++ >= maxReviseAttempts)
                        return@coroutineScope UnitOutcome.NeedsAttention("场景审查未通过，候选保留：" + issues.joinToString { it.rule })
                    val draft = requireNotNull(drafts.current(projectId, unit.id))
                    // 明确定位到问题块的分段；全局问题不猜测改写范围。
                    val affected = draft.segments.filter { s -> issues.any { i -> s.blocks.any { it.id == i.blockId } } }
                    if (affected.isEmpty() || issues.any { it.blockId == null })
                        return@coroutineScope UnitOutcome.NeedsAttention("全局审查需要人工明确修改范围，草稿已保留：" + issues.joinToString { it.rule })
                    for (segment in affected) {
                        val budget = drafts.reserveCall(guard, inputNow)
                        if (!budget.ok()) return@coroutineScope UnitOutcome.NeedsAttention("修订被拒：$budget")
                        val revisionPrompt = context + "\n[LOCAL_REVISION]\n只修订下面这一段，保持其他段落和总目标。" +
                            "保留人物知识边界，不增加标题，输出完整 blocks/events/unresolvedItems/summary/finished JSON。\n" +
                            issues.joinToString("\n") { "${it.blockId}: ${it.rule} ${it.evidence}" } +
                            "\n[PREVIOUS_CONTEXT]\n" + draft.segments.filter { it.index < segment.index }.joinToString("\n") { it.summary } +
                            "\n[SEGMENT]\n" + JsonWriter.write(ContentCodec.encode(content.copy(blocks = segment.blocks, events = segment.events))) +
                            "\n[NEXT_CONTEXT]\n" + draft.segments.filter { it.index > segment.index }.joinToString("\n") { it.summary }
                        val fix = gateway.complete(ModelRequests.fromPolicy(policy,
                            listOf(ModelMessage(ModelMessage.Role.USER, revisionPrompt)), expectJson = true))
                        recordAttempt(runId, unit.id, RunPhase.REVISING, input, fix)
                        // 修订失败不能结束整轮：原段落已经保存，跳过这段继续下一段。
                        // 以前这里直接 NeedsAttention，看起来就像「只生成第一段就不往后」。
                        if (fix.error != null || !fix.transportCompleted || fix.finishReason is FinishReason.Length) {
                            continue
                        }
                        val parsed = UnitContentParser.parse(fix.content, unit.id, outline.version, characters.map { it.id }.toSet(), ProjectType.SCENE)
                        val normalized = parsed.content?.takeIf { parsed.ok }?.let {
                            com.aiclassroom.classroom.content.SegmentNormalizer.normalize(it, candidateId, segment.index,
                                setOf("narration", "dialogue", "heading"), characters.map { c -> c.id }.toSet())
                        }?.segment ?: continue
                        val replaced = drafts.replace(guard, normalized.copy(summary = parsed.content!!.events.joinToString { it.description },
                            finished = segment.finished, unresolvedItems = parsed.content.unresolvedItems), inputNow)
                        if (!replaced.ok()) return@coroutineScope UnitOutcome.NeedsAttention("修订写入被拒：$replaced")
                        candidateVersion++
                        guard = guard.copy(expectedCandidateVersion = candidateVersion)
                    }
                    val changed = requireNotNull(drafts.current(projectId, unit.id))
                    content = content.copy(blocks = changed.segments.flatMap { it.blocks }, events = changed.segments.flatMap { it.events },
                        unresolvedItems = changed.segments.last().unresolvedItems)
                    if (changed.totalChars < com.aiclassroom.classroom.content.TextCount.acceptMin(plan.unitTarget))
                        return@coroutineScope UnitOutcome.NeedsAttention("修订后篇幅不足，草稿已保留，请继续补写")
                    text = JsonWriter.write(ContentCodec.encode(content))
                    review = null
                    continue
                }
                val reviewed = drafts.update(guard, inputNow) { it.copy(review = review, phase = DraftPhase.PATCHING) }
                if (!reviewed.ok()) return@coroutineScope UnitOutcome.NeedsAttention("保存审查被拒：$reviewed")
            }
            if (characters.isNotEmpty() || unit.requiredCharacters.isNotEmpty()) {
                val reserved = drafts.reserveCall(guard, inputNow)
                if (!reserved.ok()) return@coroutineScope UnitOutcome.NeedsAttention("状态提取预算/输入失效：$reserved")
            }
            val patchResult = proposePatch(projectId, unit.id, revisionId, content,
                (characters.map { it.id } + unit.requiredCharacters).toSet(), effectivePlan,
                facts.map { it.factId }, ProjectType.SCENE)
            if (patchResult is PatchResult.Failed) return@coroutineScope UnitOutcome.NeedsAttention(patchResult.reason)
            val patch = (patchResult as? PatchResult.Ready)?.patch
            val prepared = drafts.update(guard, inputNow) { it.copy(phase = DraftPhase.READY_TO_COMMIT) }
            if (!prepared.ok()) return@coroutineScope UnitOutcome.NeedsAttention("候选已失效：$prepared")
            val outcome = store.transaction {
                if (inputNow() != input) return@transaction null
                val current = store.unit(unit.id) ?: return@transaction null
                store.upsertUnit(current.copy(status = UnitStatus.DRAFT_READY))
                committer.commit(CommitRequest(
                    projectId, unit.id,
                    UnitRevision(revisionId, unit.id, projectId, 0, text, content, review = review,
                        statePatch = patch, modelName = policy.primaryModel, createdAt = clock(), sourceHash = input),
                    patch, runId, "$candidateId:$candidateVersion", spec.version, outline.version, owner, token,
                    draftGuard = guard, inputVersionNow = inputNow,
                ))
            }
            when (outcome) {
                is CommitResult.Committed -> UnitOutcome.Committed(outcome.revision.id, outcome.checkpoint)
                is CommitResult.Rejected -> UnitOutcome.NeedsAttention("提交被拒：${outcome.rejection}")
                null -> UnitOutcome.NeedsAttention("输入已更新，旧候选未提交")
            }
        } finally { heartbeat.cancel() }
    }


    /** 让模型从最终正文提出状态变更；失败或没有依据时返回 null，不阻断正文提交。 */
    /**
     * 状态补丁的三种结果。
     *
     * 以前四种情况全返回 null：「本单元没有事件」「模型没提变化」「调用失败」
     * 「输出读不懂」看起来一模一样 —— 于是提取失败的单元照样提交，
     * 正文和状态长期分离却被当成一致。
     */
    private sealed interface PatchResult {
        /** 本单元确实不需要状态变化（例如没有事件、或明确没有变化）。 */
        data object NoChange : PatchResult
        data class Ready(val patch: StatePatch) : PatchResult
        /** 需要状态变化但没能可靠提取；不能当成「没有变化」。 */
        data class Failed(val reason: String) : PatchResult
    }

    private suspend fun proposePatch(
        projectId: ProjectId,
        unitId: UnitId,
        revisionId: RevisionId,
        content: UnitContent,
        knownCharacters: Set<CharacterId>,
        planned: PlannedUnit,
        /** 本次可见的事实 id：模型只能引用这些，否则整份补丁作废。 */
        knownFacts: List<FactId> = emptyList(),
        projectType: ProjectType = ProjectType.TEACHING,
    ): PatchResult {
        // 当前所有状态操作都必须归属一个已注册角色；没有角色时无可提交的
        // 状态，直接结束这个阶段。正文事件和章节摘要仍由正式提交保存。
        if (knownCharacters.isEmpty()) return PatchResult.NoChange
        // 不因为 events 为空就直接判定「没有变化」：模型可能漏写事件，
        // 静默跳过会把真实变化丢掉。仍然问一次，让解析器和校验器说话。
        val prompt = PromptTemplates.stateUpdater(
            PromptTemplates.StateInput(
                unit = planned,
                text = content.blocks.joinToString("\n") { blockText(it) },
                events = content.events,
                characters = knownCharacters.toList(),
                facts = knownFacts,
                projectType = projectType,
            ),
        )
        val call = gateway.complete(
            ModelRequests.fromPolicy(
                policy = policy.copy(maxOutputTokens = policy.maxOutputTokens ?: 8192),
                messages = listOf(ModelMessage(ModelMessage.Role.USER, prompt)),
                expectJson = true,
            ),
        )
        store.runs(projectId).firstOrNull { it.owner == runOwner && it.status == RunStatus.RUNNING }?.let {
            recordAttempt(it.id, unitId, RunPhase.COMMITTING, prompt.hashCode().toString(), call)
        }
        if (!call.transportCompleted || call.finishReason is FinishReason.Length) return PatchResult.Failed("状态提取输出不完整")
        if (call.error != null) {
            return PatchResult.Failed("状态提取调用失败：" + (call.error::class.simpleName ?: "未知"))
        }

        // 模型可能拿名字当 id：能映射就映射，不整份作废。
        val knownNames = store.characters(projectId).associate { it.name to it.id }
        var parsed = StatePatchParser.parse(
            call.content,
            knownCharacters,
            content.events.map { it.id }.toSet(),
            knownFacts.toSet(),
            knownNames = knownNames,
        )
        if (!parsed.usable) {
            // 模型偶尔返回带未转义引号或代码块标记的 JSON。
            // 把具体错误回灌一次，要求只输出 JSON —— 比直接判整节失败划算。
            val retryPrompt = prompt + "\n\n[上一次的输出无法解析：" +
                parsed.dropped.take(2).joinToString("；") +
                "]\n请只输出一个合法的 JSON 对象，不要解释、不要代码块标记。"
            val retry = gateway.complete(
                ModelRequests.fromPolicy(
                    policy = policy.copy(maxOutputTokens = policy.maxOutputTokens ?: 8192),
                    messages = listOf(ModelMessage(ModelMessage.Role.USER, retryPrompt)),
                    expectJson = true,
                ),
            )
            store.runs(projectId).firstOrNull { it.owner == runOwner && it.status == RunStatus.RUNNING }?.let {
                recordAttempt(it.id, unitId, RunPhase.COMMITTING, retryPrompt.hashCode().toString(), retry)
            }
            if (retry.error == null && retry.transportCompleted && retry.finishReason !is FinishReason.Length) {
                parsed = StatePatchParser.parse(
                    retry.content,
                    knownCharacters,
                    content.events.map { it.id }.toSet(),
                    knownFacts.toSet(),
                    knownNames = knownNames,
                )
            }
        }
        if (!parsed.usable) {
            return PatchResult.Failed("状态提取输出无法解析：" + parsed.dropped.take(2).joinToString("；"))
        }
        if (parsed.allDropped) {
            // 模型声明了变化，但没有一条通过校验：这是失败，不是「没有变化」。
            return PatchResult.Failed("状态提取的变化全部未通过校验：" + parsed.dropped.take(2).joinToString("；"))
        }
        if (parsed.dropped.isNotEmpty()) {
            // 部分变化没通过校验时，剩下的那部分提交上去会造成「正文已生效、状态只变一半」，
            // 比整体失败更难排查。宁可停下。
            return PatchResult.Failed(
                "状态提取有变化未通过校验：" + parsed.dropped.take(2).joinToString(";"),
            )
        }
        if (!parsed.hasChanges) {
            // 合法的「本单元确实没有状态变化」，正文与检查点可以正常提交。
            return PatchResult.NoChange
        }

        // 每个涉及角色各自记下当前版本：多人物补丁不能拿第一个人的版本代表全部。
        val isRewrite = StateTimeline.isRewrite(store, projectId, planned.index)
        val historical = if (isRewrite) {
            StateTimeline.statesBefore(store, projectId, planned.index)
        } else {
            emptyMap()
        }
        val baseVersions = parsed.changes.map { it.actorId }.distinct().associateWith { actor ->
            if (isRewrite) historical[actor]?.version ?: 0 else store.characterState(projectId, actor)?.version ?: 0
        }
        val first = parsed.changes.first().actorId
        val base = baseVersions[first] ?: 0
        return PatchResult.Ready(StatePatch(
            id = revisionId.value + ":patch",
            projectId = projectId,
            unitId = unitId,
            revisionId = revisionId,
            baseVersion = base,
            baseVersions = baseVersions,
            changes = parsed.changes,
            reason = parsed.reason,
            evidenceEventIds = parsed.evidenceEventIds,
            idempotencyKey = revisionId.value + ":patch",
        ))
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

    /** 相邻已提交单元的末尾，作为上下文；没有就空。 */
    /**
     * 跨章上下文：所有已提交章节的摘要在预算内，再附上一节尾部原文做衔接。
     *
     * 之前只有「上一节尾部 1500 字」——相隔两章就接不上，伏笔和未解决事项全丢。
     */
    private fun previousText(projectId: ProjectId, unit: ClassroomUnit, outline: ProjectOutline): String {
        val done = store.units(projectId)
            .filter { it.index < unit.index && it.status == UnitStatus.READY }
            .sortedBy { it.index }
        if (done.isEmpty()) return ""

        val summaries = store.summaries(projectId)
            .filter { it.index < unit.index }
        val builder = StringBuilder()
        if (summaries.isNotEmpty()) {
            builder.appendLine("[章节摘要]")
            for (s in summaries) {
                // 预算 1200 字：先给完整摘要，超预算后只留 gist。
                val line = s.render(compact = builder.length > 1200)
                if (builder.length + line.length > 2400) break
                builder.appendLine(line)
            }
            builder.appendLine()
        }

        // 最近一节保留原文尾部，用于文风与直接衔接。
        val last = done.maxByOrNull { it.index }
        val rev = last?.let { store.officialRevision(it.id) }
        if (rev != null) {
            builder.appendLine("[上一节结尾]")
            builder.appendLine(rev.draftText.takeLast(800))
        }
        return builder.toString().trim()
    }

    /**
     * 每次模型调用都留一条尝试记录。
     *
     * 之前只有 GenerationAttempt 这个类型，从来没写进存储 ——
     * 于是「这一版是哪个模型、为什么被截断、重试了几次」都无据可查。
     */
    private fun recordAttempt(
        runId: RunId,
        unitId: UnitId?,
        phase: RunPhase,
        inputHash: String,
        result: ModelCallResult,
    ) {
        store.saveAttempt(
            GenerationAttempt(
                id = AttemptId.new(),
                runId = runId,
                unitId = unitId,
                phase = phase,
                inputHash = inputHash,
                requestedModel = policy.primaryModel,
                actualModel = result.actualModel,
                finishReason = result.finishReason?.let { it::class.simpleName },
                transportCompleted = result.transportCompleted,
                error = result.error?.let { it::class.simpleName },
                promptTokens = result.usage?.prompt,
                completionTokens = result.usage?.completion,
                diagnosticRef = result.diagnosticId,
                createdAt = clock(),
            ),
        )
    }

    private fun describe(result: ModelCallResult): String =
        "调用失败：" + (result.error?.let { it::class.simpleName } ?: "未知")

    companion object {
        /**
         * 依赖都已 READY 的下一个单元；没有则 null。
         *
         * **STALE 也算可执行**：它的语义就是「上游改过，这一节该重写」。
         * 以前只挑 PLANNED，导致依赖链上只要出现一个 STALE，
         * 它和它下游的所有节都永远选不中 —— 表现为「写一节就停，
         * 还报还有 N 个单元无法开始」。
         */
        fun nextReadyUnit(units: List<ClassroomUnit>): ClassroomUnit? {
            val readyIds = units.filter { it.status == UnitStatus.READY }.map { it.id }.toSet()
            return units
                .filter { it.status == UnitStatus.PLANNED || it.status == UnitStatus.STALE }
                .filter { u -> u.dependsOn.all { it in readyIds } }
                .minByOrNull { it.index }
        }
    }
}
