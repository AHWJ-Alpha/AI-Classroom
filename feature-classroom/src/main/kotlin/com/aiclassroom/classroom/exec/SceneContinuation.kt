package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.content.*
import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.gateway.*
import com.aiclassroom.classroom.json.*
import com.aiclassroom.classroom.prompt.SceneContinuationPrompt
import com.aiclassroom.classroom.repository.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 产品与测试共用的分段编排，成功分段在下一次请求前已落盘。 */
class SceneContinuation(
    private val gateway: ModelGateway,
    private val store: ClassroomStore,
    private val drafts: DraftStore,
    private val clock: () -> EpochMillis,
) {
    data class Request(
        val projectId: ProjectId,
        val unit: ClassroomUnit,
        val spec: ProjectSpec,
        val outlineSummary: String,
        val timeRange: String,
        val characters: List<CharacterProfile>,
        val plan: LengthPlan.Ok,
        val inputVersion: String,
        val candidateId: String,
        val expectedCandidateVersion: Int,
        val runId: RunId,
        val owner: String,
        val fencingToken: Long,
        val policy: ModelPolicy,
        val planVersion: Int = spec.version,
        val frozenContext: String = "",
        val inputVersionNow: () -> String = { inputVersion },
        val onAttempt: (String, ModelCallResult) -> Unit = { _, _ -> },
    )

    companion object {
        /**
         * 分段绝对硬上限。
         *
         * 动态放宽也不能无限：模型每段只写几十字时，没有上限会一直烧调用。
         */
        const val MAX_SEGMENTS = 24
    }

    sealed interface Result {
        data class Assembled(val content: UnitContent, val segments: Int, val chars: Int) : Result
        data class Paused(val reason: String, val segments: Int, val chars: Int) : Result
        data class NeedsAttention(val reason: String, val segments: Int, val chars: Int) : Result
    }

    suspend fun run(req: Request): Result {
        val guard = DraftWriteGuard(req.projectId, req.unit.id, req.candidateId, req.inputVersion,
            req.expectedCandidateVersion, req.runId, req.owner, req.fencingToken)
        val existing = drafts.current(req.projectId, req.unit.id)
        if (existing == null || existing.candidateId != req.candidateId) {
            val begin = drafts.begin(req.projectId, req.unit.id, req.candidateId, req.inputVersion,
                existing?.candidateId, guard, req.inputVersionNow)
            if (!begin.ok()) return Result.NeedsAttention("无法开始候选：$begin", 0, 0)
        }
        val claimed = drafts.claim(guard, req.inputVersionNow)
        if (!claimed.ok()) return Result.NeedsAttention("无法接管候选：$claimed", 0, 0)
        val minAccept = TextCount.acceptMin(req.plan.unitTarget,
            if (req.spec.rawRequest.contains("至少") || req.spec.expectedLength.contains("至少")) req.plan.unitTarget else null)
        var correction = existing?.lastError.orEmpty()
        while (true) {
            currentCoroutineContext().ensureActive()
            val draft = requireNotNull(drafts.current(req.projectId, req.unit.id))
            val claimedNow = drafts.claim(guard, req.inputVersionNow)
            if (!claimedNow.ok()) return Result.NeedsAttention("候选已经失效：$claimedNow", draft.segments.size, draft.totalChars)
            if (draft.totalChars >= minAccept && (draft.segments.lastOrNull()?.finished == true || draft.phase != DraftPhase.GENERATING)) {
                val saved = drafts.update(guard, req.inputVersionNow) { it.copy(phase = DraftPhase.ASSEMBLED) }
                if (!saved.ok()) return Result.NeedsAttention("组装失败：$saved", draft.segments.size, draft.totalChars)
                return Result.Assembled(assemble(req, draft), draft.segments.size, draft.totalChars)
            }
            // 分段上限按「实际产出」动态放宽：
            // 计划里的 maxSegments 是按 600 字/段估的，而真机每段常只有 200~300 字，
            // 于是刚写一半就撞上限，报「达到上限」——用户看到的就是「写几段就停」。
            // 只要每段还在有效产出、总字数还没到目标，就继续写，直到硬上限为止。
            val plannedSegments = req.plan.maxSegments
            val dynamicSegments = run {
                val avg = if (draft.segments.isEmpty()) 0 else draft.totalChars / draft.segments.size
                val remain = (req.plan.unitTarget - draft.totalChars).coerceAtLeast(0)
                // 用实测均值估算还需要几段；均值太小（模型不给力）时不无限膨胀，交给硬上限兜底。
                val needed = if (avg <= 0) plannedSegments else draft.segments.size + (remain + avg - 1) / avg
                needed.coerceAtMost(MAX_SEGMENTS)
            }
            val segmentLimit = maxOf(plannedSegments, dynamicSegments)
            if (draft.segments.size >= segmentLimit || draft.callCount >= 64 || draft.failStreak >= 3) {
                return Result.NeedsAttention("达到分段/调用/纠正上限：${draft.totalChars} / ${req.plan.unitTarget}（已写 ${draft.segments.size} 段，上限 $segmentLimit）",
                    draft.segments.size, draft.totalChars)
            }
            if (draft.totalChars > TextCount.acceptMax(req.plan.unitTarget))
                return Result.NeedsAttention("篇幅超出目标且尚未自然收束，草稿已保留", draft.segments.size, draft.totalChars)
            val remaining = (req.plan.unitTarget - draft.totalChars).coerceAtLeast(1)
            val tokenTextLimit = req.policy.maxOutputTokens?.let { ((it - 512) / 3).coerceAtLeast(1) } ?: 1800
            // 已达到下限但模型漏了 finished 时，只补一次有意义的收束，不反复要求“再写 1 字”。
            val closing = draft.totalChars >= minAccept
            val perCall = (minOf(req.plan.perCallTarget, if (closing) 120 else remaining, tokenTextLimit) / (draft.failStreak + 1)).coerceAtLeast(1)
            val prompt = SceneContinuationPrompt.build(SceneContinuationPrompt.Input(
                rawRequest = req.spec.rawRequest, unitGoal = req.unit.goal, timeRange = req.timeRange,
                characters = req.characters, totalTarget = req.plan.unitTarget, currentChars = draft.totalChars,
                perCallTarget = perCall, isFirst = draft.segments.isEmpty(), isLast = draft.totalChars + perCall >= minAccept,
                summary = draft.summary, events = draft.segments.flatMap { it.events }.takeLast(40),
                usedScenes = draft.usedScenes, tailText = draft.segments.lastOrNull()?.text.orEmpty().takeLast(1200),
                mustFinish = closing || draft.totalChars + perCall >= minAccept,
                frozenContext = req.frozenContext.ifBlank { req.outlineSummary }, correction = correction,
            ))
            val reserved = drafts.reserveCall(guard, req.inputVersionNow)
            if (!reserved.ok()) return Result.NeedsAttention("请求被拒：$reserved", draft.segments.size, draft.totalChars)
            val call = gateway.complete(ModelRequests.fromPolicy(req.policy.copy(maxOutputTokens = req.policy.maxOutputTokens ?: 8192),
                listOf(ModelMessage(ModelMessage.Role.USER, prompt)), expectJson = true))
            req.onAttempt(prompt, call)
            currentCoroutineContext().ensureActive()
            if (call.error != null) {
                val message = "生成中断：${call.error}"
                drafts.update(guard, req.inputVersionNow) { it.copy(lastError = message) }
                return if (call.error is ModelCallError.Network || call.error is ModelCallError.Timeout)
                    Result.Paused(message, draft.segments.size, draft.totalChars)
                else Result.NeedsAttention(message, draft.segments.size, draft.totalChars)
            }
            var problem = when {
                call.finishReason is FinishReason.Length -> "当前段到达输出上限，请减少本段篇幅并完整闭合 JSON"
                !call.transportCompleted -> "响应未完整传输"
                else -> ""
            }
            val parsed = if (problem.isBlank()) UnitContentParser.parse(call.content, req.unit.id, req.planVersion,
                req.characters.map { it.id }.toSet(), ProjectType.SCENE) else null
            if (parsed != null && !parsed.ok) problem = parsed.problems.joinToString("；")
            val normalized = parsed?.content?.takeIf { parsed.ok }?.let {
                SegmentNormalizer.normalize(it, req.candidateId, draft.nextIndex,
                    setOf("narration", "dialogue", "heading"), req.characters.map { c -> c.id }.toSet())
            }
            if (normalized != null && !normalized.ok) problem = normalized.problems.joinToString("；")
            val raw = StructuredPayload.extract(call.content)?.let { runCatching { JsonParser.parse(it).asObject() }.getOrNull() }
            val segment = normalized?.segment?.copy(
                createdAt = clock(), summary = raw?.get("summary").asString()?.takeIf { it.isNotBlank() }?.take(1500)
                    ?: parsed?.content?.events?.joinToString { it.description }?.takeIf { it.isNotBlank() }
                    ?: normalized?.segment?.text.orEmpty().take(600),
                finished = raw?.get("finished").asBool() ?: (draft.totalChars + (normalized?.segment?.chars ?: 0) >= minAccept),
                unresolvedItems = parsed?.content?.unresolvedItems.orEmpty(),
                objectivesCovered = parsed?.content?.objectivesCovered.orEmpty(),
            )
            if (segment != null && TextCount.units(segment.blocks) == 0) problem = "当前段没有有效新增正文"
            if (segment != null && draft.segments.any { it.text.filterNot(Char::isWhitespace) == segment.text.filterNot(Char::isWhitespace) })
                problem = "当前段重复已保存正文，请紧接着推进新的内容"
            if (problem.isNotBlank() || segment == null) {
                correction = problem.ifBlank { "没有可读的完整分段" }
                val failed = drafts.update(guard, req.inputVersionNow) { it.copy(failStreak = it.failStreak + 1, lastError = correction) }
                if (!failed.ok()) return Result.NeedsAttention("草稿失效：$failed", draft.segments.size, draft.totalChars)
                continue
            }
            val appended = drafts.append(guard, segment, req.inputVersion, { TextCount.units(it.blocks) }, req.inputVersionNow)
            if (!appended.ok()) return Result.NeedsAttention("写入草稿被拒：$appended", draft.segments.size, draft.totalChars)
            correction = ""
        }
    }

    private fun assemble(req: Request, draft: UnitDraft) = UnitContent(
        unitId = req.unit.id, planVersion = req.planVersion, title = req.timeRange,
        blocks = draft.segments.flatMap { it.blocks }, events = draft.segments.flatMap { it.events },
        objectivesCovered = draft.segments.flatMap { it.objectivesCovered }.distinct(),
        unresolvedItems = draft.segments.lastOrNull()?.unresolvedItems.orEmpty(),
    )
}

internal fun DraftOutcome.ok() = this is DraftOutcome.Applied || this is DraftOutcome.AlreadyApplied
