package com.aiclassroom.classroom.domain

/** 统一的时间戳（毫秒），由宿主时钟提供，领域不直接读系统时间。 */
typealias EpochMillis = Long

/**
 * 课堂项目：一个可以持续完成的独立工作区。
 *
 * 注意不要和旧聊天的 `com.aiclassroom.app.model.Classroom` 混淆 ——
 * 那个表示旧的「聊天课堂」，这里是结构化的长内容项目。
 */
data class ClassroomProject(
    val id: ProjectId,
    val title: String,
    val type: ProjectType,
    val lifecycle: ProjectLifecycle,
    /** 当前任务（没有则为 null）。 */
    val currentRunId: RunId? = null,
    /** 当前生效的规格版本号，从 1 开始。 */
    val specVersion: Int = 0,
    /** 当前生效的总纲版本号。 */
    val outlineVersion: Int = 0,
    /** 正式作品版本：每次提交单元递增。 */
    val workVersion: Int = 0,
    val createdAt: EpochMillis,
    val updatedAt: EpochMillis,
    /** 来源对话的稳定 id；null 表示还没有关联（旧项目）。 */
    val sourceConversationId: String? = null,
    /** 创建时的对话显示名，仅用于展示，绝不作为归属键。 */
    val sourceDisplayNameSnapshot: String? = null,
    val binding: BindingStatus = BindingStatus.UNBOUND,
)

/** 项目与来源对话的关联状态。 */
enum class BindingStatus {
    /** 已绑定，来源存在。 */
    BOUND,
    /** 旧项目，还没关联任何对话。 */
    UNBOUND,
    /** 曾经绑定过，但来源对话已被删除。 */
    SOURCE_MISSING,
}

/**
 * 结构化规格：把用户的原始需求整理成可执行、可验证的东西。
 *
 * [rawRequest] 永远保留用户原话，之后任何阶段都能回看，
 * 不能让模型整理的规格悄悄取代用户真实意图。
 */
data class ProjectSpec(
    val projectId: ProjectId,
    val version: Int,
    val rawRequest: String,
    val topic: String,
    val goals: List<String>,
    /** 教学是学习者画像，小说/GAL 是目标读者。 */
    val audience: String,
    val language: String,
    val style: String,
    val expectedLength: String,
    val unitCount: Int,
    val runMode: RunMode,
    val reviewPolicy: ReviewPolicy,
    /** 硬约束：不允许在生成过程中被模型改写的规则。 */
    val hardConstraints: List<String>,
    /** 作者规则（作者视角私密，不进角色上下文）。 */
    val authorRules: List<String>,
    /** 所有角色都可知的公共背景。 */
    val publicWorld: String,
    val characterIds: List<CharacterId> = emptyList(),
    /** 模型策略引用，绝不存 API Key。 */
    val modelPolicyRef: String? = null,
    val budget: Budget? = null,
    val createdAt: EpochMillis,
)

/** 预算：只记请求数/Token，金额在宿主配置价格后才换算，不伪造。 */
data class Budget(
    val maxRequests: Int? = null,
    val maxTokens: Long? = null,
)

/** 规划阶段的单元索引项：还没有正文，只有意图。 */
data class PlannedUnit(
    val unitId: UnitId,
    val index: Int,
    val title: String,
    val goal: String,
    /** 依赖的上游单元；为空表示可立即生成。 */
    val dependsOn: List<UnitId> = emptyList(),
    val requiredCharacters: List<CharacterId> = emptyList(),
    /** 结束条件：满足即可认为本单元完成，不能只凭模型说「写完了」。 */
    val completionCriteria: List<String> = emptyList(),
)

/**
 * 作品级总纲。
 *
 * 规划完整不等于一次生成巨型 JSON：先出全局索引（本类的 units），
 * 再分批细化，全部通过结构校验后才进入正文生成。
 */
data class ProjectOutline(
    val projectId: ProjectId,
    val version: Int,
    val globalGoal: String,
    /** 教学是知识依赖主线，小说是剧情主线。 */
    val mainLine: String,
    val chapters: List<String>,
    val units: List<PlannedUnit>,
    val milestones: List<String> = emptyList(),
    /** 人物弧线：角色 -> 变化描述。 */
    val arcs: Map<CharacterId, String> = emptyMap(),
    val foreshadows: List<String> = emptyList(),
    val ending: String = "",
    val createdAt: EpochMillis,
)

/** 单元的运行时记录：指向当前正式版本与任务状态。 */
data class ClassroomUnit(
    val id: UnitId,
    val projectId: ProjectId,
    val index: Int,
    val type: ProjectType,
    val chapter: String,
    val goal: String,
    val dependsOn: List<UnitId>,
    val requiredCharacters: List<CharacterId>,
    val completionCriteria: List<String>,
    /** 输入状态版本：生成时冻结，提交时校验。 */
    val inputStateVersion: Int,
    val status: UnitStatus,
    /** 当前正式版本（未提交过则为 null）。 */
    val currentRevisionId: RevisionId? = null,
    val officialVersion: Int = 0,
    /** 用户对本单元方向的微调；不改变规格与全局主线。 */
    val directive: UnitDirective? = null,
)

/**
 * 单元的一个版本。
 *
 * 未提交的草稿与正式版本用同一个类型承载，靠 [committedAt] 区分：
 * 只有 committedAt 非空且 status 通过审查的版本才能进阅读器。
 */
data class UnitRevision(
    val id: RevisionId,
    val unitId: UnitId,
    val projectId: ProjectId,
    val version: Int,
    val draftText: String,
    val content: UnitContent?,
    /** 生成后由 StateUpdater 提出、程序校验通过的状态补丁。 */
    val statePatch: StatePatch? = null,
    val attemptId: AttemptId? = null,
    val modelName: String? = null,
    val promptTemplateVersion: Int = 1,
    val createdAt: EpochMillis,
    /** 正式提交时间；为 null 表示仍是草稿。 */
    val committedAt: EpochMillis? = null,
    /** 内容 hash：用于来源追踪与幂等判断。 */
    val sourceHash: String = "",
    /** 被哪个版本替代（保持历史可追溯，不覆盖旧版本）。 */
    val supersededBy: RevisionId? = null,
)
