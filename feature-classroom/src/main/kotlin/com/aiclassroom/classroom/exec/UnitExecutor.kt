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
    private val model: String = "default",
    /** 降级顺序：主模型失败时按这个顺序换。 */
    private val fallbacks: List<String> = emptyList(),
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
        val states = store.characterStates(projectId)
        val knownCharacters = characters.map { it.id }.toSet()

        var revisionId = RevisionId.new()
        var attempt = 0
        var lastReason = ""
        var text = ""
        var content: UnitContent? = null
        // 只有审查明确说过「没有阻断问题」才允许提交；循环跑完但没通过，仍是未通过。
        var approved = false

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
                ModelRequest(
                    model = model,
                    fallbackModels = fallbacks,
                    messages = listOf(ModelMessage(ModelMessage.Role.USER, prompt)),
                    expectJson = true,
                ),
            )
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
                ModelRequest(
                    model = model,
                    fallbackModels = fallbacks,
                    messages = listOf(
                        ModelMessage(
                            ModelMessage.Role.USER,
                            PromptTemplates.reviewer(
                                PromptTemplates.ReviewerInput(spec = spec, unit = planned, text = text),
                            ),
                        ),
                    ),
                    expectJson = true,
                ),
            )
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
        val patch = proposePatch(projectId, unitId, revisionId, content, knownCharacters, planned)

        val revision = UnitRevision(
            id = revisionId,
            unitId = unitId,
            projectId = projectId,
            version = 0,
            draftText = text,
            content = content,
            review = null,
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
            ),
        )
        return when (outcome) {
            is CommitResult.Committed -> UnitOutcome.Committed(outcome.revision.id, outcome.checkpoint)
            is CommitResult.Rejected -> UnitOutcome.NeedsAttention("提交被拒绝：" + outcome.rejection)
        }
    }


    /** 让模型从最终正文提出状态变更；失败或没有依据时返回 null，不阻断正文提交。 */
    private suspend fun proposePatch(
        projectId: ProjectId,
        unitId: UnitId,
        revisionId: RevisionId,
        content: UnitContent,
        knownCharacters: Set<CharacterId>,
        planned: PlannedUnit,
    ): StatePatch? {
        if (content.events.isEmpty()) return null
        val prompt = PromptTemplates.stateUpdater(
            PromptTemplates.StateInput(
                unit = planned,
                text = content.blocks.joinToString("\n") { blockText(it) },
                events = content.events,
            ),
        )
        val call = gateway.complete(
            ModelRequest(
                model = model,
                fallbackModels = fallbacks,
                messages = listOf(ModelMessage(ModelMessage.Role.USER, prompt)),
                expectJson = true,
            ),
        )
        if (call.error != null) return null

        val parsed = StatePatchParser.parse(
            call.content,
            knownCharacters,
            content.events.map { it.id }.toSet(),
        )
        if (!parsed.usable) return null

        val first = parsed.changes.first().actorId
        val base = store.characterState(projectId, first)?.version ?: 0
        return StatePatch(
            id = revisionId.value + ":patch",
            projectId = projectId,
            unitId = unitId,
            revisionId = revisionId,
            baseVersion = base,
            changes = parsed.changes,
            reason = parsed.reason,
            evidenceEventIds = parsed.evidenceEventIds,
            idempotencyKey = revisionId.value + ":patch",
        )
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
    private fun previousText(projectId: ProjectId, unit: ClassroomUnit, outline: ProjectOutline): String {
        val previous = store.units(projectId)
            .filter { it.index < unit.index && it.status == UnitStatus.READY }
            .maxByOrNull { it.index } ?: return ""
        val rev = store.officialRevision(previous.id) ?: return ""
        return rev.draftText.takeLast(1500)
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
