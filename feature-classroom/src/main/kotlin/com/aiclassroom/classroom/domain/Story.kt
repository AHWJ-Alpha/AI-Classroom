package com.aiclassroom.classroom.domain

/**
 * 角色档案。
 *
 * [authorPrivate] 是作者私密段落：进入角色上下文时必须排除，
 * 和旧聊天里「作者世界书不进角色视角」是同一条边界。
 */
data class CharacterProfile(
    val id: CharacterId,
    /**
     * 所属项目。
     *
     * 角色 ID 不是全局唯一的：同一个名字/同一张卡在不同项目里可能是两个不同的人，
     * 只按 characterId 存会让两个项目互相覆盖。null 表示旧数据（迁移前无归属）。
     */
    val projectId: ProjectId? = null,
    val name: String,
    val identity: String,
    /** 固定人格：不能被模型依据上一轮台词悄悄改写。 */
    val corePersonality: String,
    val values: String,
    val thinkingStyle: String,
    val speechStyle: String,
    val behaviorRules: List<String>,
    val positiveExamples: List<String> = emptyList(),
    val negativeExamples: List<String> = emptyList(),
    val authorPrivate: String = "",
    val version: Int = 1,
)

/**
 * 角色的可变状态。
 *
 * 只有通过事件证据驱动的变更才能进来；[version] 用于乐观并发，
 * 状态补丁必须声明它基于哪个版本，避免覆盖别人的新编辑。
 */
data class CharacterState(
    val characterId: CharacterId,
    /** 所属项目；与 [CharacterProfile.projectId] 同义，用于按项目隔离状态。 */
    val projectId: ProjectId? = null,
    val version: Int,
    val emotion: String = "",
    val goal: String = "",
    val stress: Int = 0,
    val location: String = "",
    val relationships: Map<CharacterId, String> = emptyMap(),
    val knownFactIds: Set<FactId> = emptySet(),
)

/** 事实的认知类型：世界真相与人物的相信必须分开。 */
enum class FactKind {
    /** 客观发生的世界事实。 */
    FACT,
    /** 某角色相信但不一定为真的内容。 */
    BELIEF,
    /** 传闻。 */
    RUMOR,
}

/** 谁可见。 */
enum class Visibility {
    /** 所有角色可知。 */
    PUBLIC,
    /** 仅作者/生成器可见。 */
    AUTHOR_ONLY,
    /** 指定角色可知。 */
    RESTRICTED,
}

/**
 * 事实的一个版本。
 *
 * 修订靠追加新版本表达，[supersedes] 指向被替代的版本；
 * 取当前内容时要先选最新版本再按视角过滤，
 * 否则旧版本的公开性会重新泄露已经收窄的信息。
 */
data class FactRevision(
    val factId: FactId,
    val projectId: ProjectId,
    val version: Int,
    val content: String,
    val kind: FactKind,
    /** 来源单元与版本，必须可追溯。 */
    val sourceUnitId: UnitId?,
    val sourceRevisionId: RevisionId?,
    /** 只在某条路线/状态路径下成立（GAL 分支隔离）。 */
    val routeKey: String? = null,
    val visibility: Visibility,
    val knownBy: Set<CharacterId> = emptySet(),
    val unknownBy: Set<CharacterId> = emptySet(),
    val evidence: String = "",
    /** 获知事件：谁在什么时候知道的。 */
    val learnedAtEventId: String? = null,
    val effectiveAt: EpochMillis = 0,
    val supersedes: Int? = null,
)

/** 状态补丁的操作：只允许白名单字段的增量修改。 */
sealed interface StateChange {
    /** 这条变更作用于哪个角色。统一入口，避免调用方各写一套 when。 */
    val actorId: CharacterId

    data class SetEmotion(override val actorId: CharacterId, val value: String) : StateChange
    data class SetGoal(override val actorId: CharacterId, val value: String) : StateChange
    data class SetLocation(override val actorId: CharacterId, val value: String) : StateChange
    data class AdjustStress(override val actorId: CharacterId, val delta: Int) : StateChange
    data class SetRelationship(override val actorId: CharacterId, val target: CharacterId, val value: String) : StateChange
    data class LearnFact(override val actorId: CharacterId, val factId: FactId) : StateChange
}

/**
 * 一次状态变更提案。
 *
 * [baseVersion] 是提交条件：基础版本已变则拒绝，重新排任务，
 * 不用模型输出的「全新完整状态」覆盖旧状态。
 * [idempotencyKey] 让重复提交只生效一次。
 */
data class StatePatch(
    val id: String,
    val projectId: ProjectId,
    val unitId: UnitId,
    val revisionId: RevisionId,
    val baseVersion: Int,
    /**
     * 每个涉及角色各自的基础版本。
     *
     * 只用一个 baseVersion 时，第一个人的版本被当成所有人的版本：
     * 多人物补丁要么误拒、要么放过已经过期的角色。这里按角色逐个记。
     */
    val baseVersions: Map<CharacterId, Int> = emptyMap(),
    val changes: List<StateChange>,
    val reason: String,
    /** 必须引用本单元实际发生的事件。 */
    val evidenceEventIds: List<String>,
    val idempotencyKey: String,
)
