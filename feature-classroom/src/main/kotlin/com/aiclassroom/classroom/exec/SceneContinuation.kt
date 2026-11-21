package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.content.SegmentNormalizer
import com.aiclassroom.classroom.content.TextCount
import com.aiclassroom.classroom.content.UnitContentParser
import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.gateway.*
import com.aiclassroom.classroom.prompt.SceneContinuationPrompt
import com.aiclassroom.classroom.repository.ClassroomStore
import com.aiclassroom.classroom.repository.DraftOutcome
import com.aiclassroom.classroom.repository.DraftStore
import com.aiclassroom.classroom.repository.DraftWriteGuard

/**
 * 场景多段续写。
 *
 * 一段一次调用：每段都必须完整合法才落盘，失败只重试当前段；
 * 已保存的段落不会被重写，恢复时从 nextIndex 接着写。
 */
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
    )

    sealed interface Result {
        data class Assembled(val content: UnitContent, val segments: Int, val chars: Int) : Result
        /** 网络中断等可恢复情况：草稿保留，下次从 nextIndex 继续。 */
        data class Paused(val reason: String, val segments: Int, val chars: Int) : Result
        data class NeedsAttention(val reason: String, val segments: Int, val chars: Int) : Result
    }

    private val allowedTypes = setOf("narration", "dialogue", "heading")

    suspend fun run(req: Request): Result {
        val existing = drafts.current(req.projectId, req.unit.id)
        if (existing == null || existing.candidateId != req.candidateId) {
            val begin = drafts.begin(
                projectId = req.projectId, unitId = req.unit.id,
                candidateId = req.candidateId, inputVersion = req.inputVersion,
                expectedOldCandidateId = existing?.candidateId,
            )
            if (begin !is DraftOutcome.Applied && begin !is DraftOutcome.AlreadyApplied) {
                return Result.NeedsAttention("无法开始候选：" + begin, 0, 0)
            }
        }
        val guard = DraftWriteGuard(
            projectId = req.projectId, unitId = req.unit.id,
            candidateId = req.candidateId, expectedInputVersion = req.inputVersion,
            expectedCandidateVersion = req.expectedCandidateVersion,
            runId = req.runId, owner = req.owner, fencingToken = req.fencingToken,
        )
        drafts.claim(guard)

        val minAccept = TextCount.acceptMin(req.plan.unitTarget)
        val maxAccept = TextCount.acceptMax(req.plan.unitTarget)
        var calls = 0
        val callBudget = req.plan.maxSegments + 4

        while (true) {
            val draft = drafts.current(req.projectId, req.unit.id)
                ?: return Result.NeedsAttention("候选丢失", 0, 0)
            if (draft.totalChars >= minAccept && draft.segments.isNotEmpty()) {
                // 达到接受下限：拼装完整单元。
                val content = assemble(req, draft)
                return Result.Assembled(content, draft.segments.size, draft.totalChars)
            }
            if (draft.segments.size >= req.plan.maxSegments || calls >= callBudget) {
                return Result.NeedsAttention(
                    "达到分段/调用上限仍未写够：" + draft.totalChars + " / " + req.plan.unitTarget,
                    draft.segments.size, draft.totalChars,
                )
            }
            val remaining = (req.plan.unitTarget - draft.totalChars).coerceAtLeast(req.plan.perCallTarget)
            val perCall = minOf(req.plan.perCallTarget, remaining)
            val prompt = SceneContinuationPrompt.build(
                SceneContinuationPrompt.Input(
                    rawRequest = req.spec.rawRequest,
                    unitGoal = req.unit.goal,
                    timeRange = req.timeRange,
                    characters = req.characters,
                    totalTarget = req.plan.unitTarget,
                    currentChars = draft.totalChars,
                    perCallTarget = perCall,
                    isFirst = draft.segments.isEmpty(),
                    isLast = false,
                    summary = draft.summary,
                    events = draft.segments.flatMap { it.events },
                    usedScenes = draft.usedScenes,
                    tailText = tailOf(draft),
                    mustFinish = draft.totalChars + perCall >= minAccept,
                ),
            )

            calls++
            val call = gateway.complete(
                ModelRequests.fromPolicy(
                    policy = req.policy,
                    messages = listOf(ModelMessage(ModelMessage.Role.USER, prompt)),
                    expectJson = true,
                ),
            )
            if (call.error != null) {
                // 网络/超时：保留已保存的段落，下次从 nextIndex 继续。
                return Result.Paused(
                    "生成中断：" + call.error,
                    draft.segments.size, draft.totalChars,
                )
            }

            val parsed = UnitContentParser.parse(
                raw = call.content, unitId = req.unit.id,
                planVersion = req.spec.version,
                knownCharacters = req.characters.map { CharacterId(it.id.value) }.toSet(),
                projectType = ProjectType.SCENE,
            )
            if (!parsed.ok || parsed.content == null) {
                // 结构不合法：不落盘，再要一段（受同一预算约束）。
                if (calls >= callBudget) {
                    return Result.NeedsAttention("分段结构反复不合法", draft.segments.size, draft.totalChars)
                }
                continue
            }

            val normalized = SegmentNormalizer.normalize(
                content = parsed.content,
                candidateId = req.candidateId,
                index = draft.nextIndex,
                allowedTypes = allowedTypes,
                knownSpeakers = req.characters.map { CharacterId(it.id.value) }.toSet(),
            )
            if (!normalized.ok || normalized.segment == null) {
                if (calls >= callBudget) {
                    return Result.NeedsAttention("分段格式反复不合格", draft.segments.size, draft.totalChars)
                }
                continue
            }

            val appended = drafts.append(guard, normalized.segment, req.inputVersion) { TextCount.units(it.blocks) }
            when (appended) {
                DraftOutcome.Applied, DraftOutcome.AlreadyApplied -> Unit
                is DraftOutcome.StaleExecutor -> return Result.Paused("执行权已失效", draft.segments.size, draft.totalChars)
                else -> return Result.NeedsAttention("写入草稿被拒：" + appended, draft.segments.size, draft.totalChars)
            }
        }
    }

    /** 最近一段的结尾，最多 1200 个正文计数单位，只用于衔接。 */
    private fun tailOf(draft: UnitDraft): String {
        val text = draft.segments.lastOrNull()?.text ?: return ""
        return if (text.length <= 1200) text else text.takeLast(1200)
    }

    private fun assemble(req: Request, draft: UnitDraft): UnitContent = UnitContent(
        unitId = req.unit.id,
        planVersion = req.spec.version,
        title = req.timeRange,
        blocks = draft.segments.flatMap { it.blocks },
        events = draft.segments.flatMap { it.events },
        unresolvedItems = draft.segments.flatMap { s -> s.blocks.filterIsInstance<ContentBlock.Heading>().map { it.text } },
    )
}
