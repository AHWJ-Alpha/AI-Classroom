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

sealed interface UnitOutcome {
    data class Committed(val revisionId: RevisionId, val checkpoint: Checkpoint) : UnitOutcome
    /** 需要人处理：反复审查不通过、输出结构不对、预算用完。 */
    data class NeedsAttention(val reason: String) : UnitOutcome
    /** 调用失败（网络/认证/截断），可再试。 */
    data class Failed(val reason: String) : UnitOutcome
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
) : UnitRunner {

    override suspend fun execute(projectId: ProjectId, unitId: UnitId, runId: RunId): UnitOutcome {
        val unit = store.unit(unitId) ?: return UnitOutcome.Failed("单元不存在")
        if (unit.status == UnitStatus.READY) return UnitOutcome.AlreadyDone

        val project = store.project(projectId) ?: return UnitOutcome.Failed("项目不存在")
        val spec = store.latestSpec(projectId) ?: return UnitOutcome.NeedsAttention("没有规格，无法生成")
        val outline = store.latestOutline(projectId)
            ?: return UnitOutcome.NeedsAttention("没有总纲，无法生成")
        val planned = outline.units.firstOrNull { it.unitId == unitId }
            ?: return UnitOutcome.NeedsAttention("总纲里找不到这个单元")

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
        val knownCharacters = characters.map { it.id }.toSet()

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
                        facts = store.facts(projectId),
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
                        facts = store.facts(projectId),
                        previousText = text.take(4000),
                        directive = unit.directive,
                        projectType = unit.type,
                        unitIndex = outline.units.map { it.index to it.unitId },
                        snapshot = snapshot,
                    ),
                )
            }

            val result = gateway.complete(
                ModelRequests.fromPolicy(
                    policy = policy,
                    messages = listOf(ModelMessage(ModelMessage.Role.USER, prompt)),
                    expectJson = true,
                ),
            )
            recordAttempt(runId, unitId, RunPhase.GENERATING, prompt.hashCode().toString(), result)
            if (result.error != null) return UnitOutcome.Failed(describe(result))
            // 截断的输出不能当作完整正文：宁可重试，也不要写进正式内容。
            if (result.finishReason is FinishReason.Length) {
                return UnitOutcome.Failed("输出被截断（到达 max_tokens）")
            }
            text = result.content

            val parsed = UnitContentParser.parse(text, unitId, outline.version, knownCharacters)
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
        val patchResult = proposePatch(projectId, unitId, revisionId, content, knownCharacters, planned)
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
    ): PatchResult {
        // 不因为 events 为空就直接判定「没有变化」：模型可能漏写事件，
        // 静默跳过会把真实变化丢掉。仍然问一次，让解析器和校验器说话。
        val prompt = PromptTemplates.stateUpdater(
            PromptTemplates.StateInput(
                unit = planned,
                text = content.blocks.joinToString("\n") { blockText(it) },
                events = content.events,
            ),
        )
        val call = gateway.complete(
            ModelRequests.fromPolicy(
                policy = policy,
                messages = listOf(ModelMessage(ModelMessage.Role.USER, prompt)),
                expectJson = true,
            ),
        )
        if (call.error != null) {
            return PatchResult.Failed("状态提取调用失败：" + (call.error::class.simpleName ?: "未知"))
        }

        val parsed = StatePatchParser.parse(
            call.content,
            knownCharacters,
            content.events.map { it.id }.toSet(),
        )
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
        val historical = if (StateTimeline.isRewrite(store, projectId, planned.index)) {
            StateTimeline.statesBefore(store, projectId, planned.index)
        } else {
            emptyMap()
        }
        val baseVersions = parsed.changes.map { it.actorId }.distinct().associateWith { actor ->
            historical[actor]?.version ?: store.characterState(projectId, actor)?.version ?: 0
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
        /** 依赖都已 READY 的下一个单元；没有则 null。 */
        fun nextReadyUnit(units: List<ClassroomUnit>): ClassroomUnit? {
            val readyIds = units.filter { it.status == UnitStatus.READY }.map { it.id }.toSet()
            return units
                .filter { it.status == UnitStatus.PLANNED }
                .filter { u -> u.dependsOn.all { it in readyIds } }
                .minByOrNull { it.index }
        }
    }
}
