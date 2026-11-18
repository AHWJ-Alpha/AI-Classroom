package com.aiclassroom.classroom.domain

/** 生成任务：一次从规划到完成的执行。 */
data class GenerationRun(
    val id: RunId,
    val projectId: ProjectId,
    /** 冻结的规格/总纲版本：任务中途改配置不影响已生成内容的来源。 */
    val specVersion: Int,
    val outlineVersion: Int,
    val phase: RunPhase,
    val status: RunStatus,
    val currentUnitId: UnitId? = null,
    val completedUnits: Int = 0,
    val totalUnits: Int = 0,
    val retryCount: Int = 0,
    val budgetUsed: BudgetUsage = BudgetUsage(),
    val error: String? = null,
    val startedAt: EpochMillis,
    val updatedAt: EpochMillis,
    /**
     * 本 run 冻结的模型策略（非敏感信息）。
     *
     * 任务中途用户切到别的对话、改了模型，都不影响这一轮；
     * 保留下来才能回答「这一版到底是哪个模型跑的」。
     */
    val frozenProvider: String? = null,
    val frozenPrimaryModel: String? = null,
    val frozenFallbackModels: List<String> = emptyList(),
)

data class BudgetUsage(
    val requests: Int = 0,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
)

/**
 * 一次模型调用尝试。
 *
 * [finishReason] 缺失时留 null，不能伪造为 stop ——
 * 「没有 finish_reason」和「正常结束」是两件事，混起来会把截断当成完成。
 */
data class GenerationAttempt(
    val id: AttemptId,
    val runId: RunId,
    val unitId: UnitId?,
    val phase: RunPhase,
    val inputHash: String,
    val requestedModel: String?,
    val actualModel: String?,
    val finishReason: String?,
    val transportCompleted: Boolean,
    val httpStatus: Int? = null,
    val error: String? = null,
    val promptTokens: Long? = null,
    val completionTokens: Long? = null,
    val diagnosticRef: String? = null,
    val createdAt: EpochMillis,
)

/**
 * 检查点：一个单元提交成功后的凭据。
 *
 * 任务恢复时以检查点为准，不靠「界面显示到第几章」推断。
 */
data class Checkpoint(
    val runId: RunId,
    val unitId: UnitId,
    val revisionId: RevisionId,
    val workVersion: Int,
    val stateVersion: Int,
    val outlineVersion: Int,
    val committedAt: EpochMillis,
    val checksum: String,
    /** 幂等键：同一单元重复回调时只允许生效一次。 */
    val idempotencyKey: String,
)

/** 阅读进度：和生成进度、学习掌握分别持久化。 */
data class ReaderProgress(
    val projectId: ProjectId,
    val workVersion: Int,
    val unitId: UnitId?,
    val offset: Int,
    val updatedAt: EpochMillis,
)

/**
 * 学习进度。
 *
 * 「读完一节课」不等于「掌握」—— 掌握需要作答与批改证据。
 */
data class LearningProgress(
    val projectId: ProjectId,
    val objectiveId: String,
    val answer: String?,
    val graded: Boolean,
    val mastered: Boolean,
    val evidence: String,
    val updatedAt: EpochMillis,
)

/** GAL 存档：只影响游玩，不改作品正文与生成状态。 */
data class PlaythroughSave(
    val id: SaveId,
    val projectId: ProjectId,
    val workVersion: Int,
    val currentNodeId: String,
    val variables: Map<String, GalValue>,
    val choiceHistory: List<String>,
    val seenEndingIds: List<String>,
    val updatedAt: EpochMillis,
)
