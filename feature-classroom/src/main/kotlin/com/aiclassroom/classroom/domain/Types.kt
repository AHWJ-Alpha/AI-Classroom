package com.aiclassroom.classroom.domain

import java.util.UUID

/**
 * 课堂的稳定标识。
 *
 * 一律用 UUID 字符串，不用数组下标、标题或角色显示名当业务身份 ——
 * 标题可改、显示名可重名，下标会随插入变化，都不能作为身份。
 */
@JvmInline
value class ProjectId(val value: String) {
    companion object {
        fun new(): ProjectId = ProjectId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class UnitId(val value: String) {
    companion object {
        fun new(): UnitId = UnitId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class RevisionId(val value: String) {
    companion object {
        fun new(): RevisionId = RevisionId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class CharacterId(val value: String) {
    companion object {
        fun new(): CharacterId = CharacterId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class FactId(val value: String) {
    companion object {
        fun new(): FactId = FactId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class RunId(val value: String) {
    companion object {
        fun new(): RunId = RunId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class AttemptId(val value: String) {
    companion object {
        fun new(): AttemptId = AttemptId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class SnapshotId(val value: String) {
    companion object {
        fun new(): SnapshotId = SnapshotId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class SaveId(val value: String) {
    companion object {
        fun new(): SaveId = SaveId(UUID.randomUUID().toString())
    }
}

/** 课堂作品的三种形态。 */
enum class ProjectType(val label: String) {
    // 界面上叫「课堂」，但对模型不宣称教学：这里只说明产出是连贯的长内容，
    // 否则模型会把它写成讲义，亲密/剧情向的长篇直接被压扁。
    TEACHING("连贯长内容"),
    NOVEL("小说"),
    TEXT_GAL("文字 GAL"),
}

/**
 * 项目生命周期。
 *
 * 与「任务状态」和「单元状态」是三个独立维度，不能混成一个字符串 ——
 * 一个 ACTIVE 项目里可以同时有 READY 单元和 FAILED 单元。
 */
enum class ProjectLifecycle {
    /** 刚创建，规格还没成型。 */
    DRAFT,

    /** 规格与总纲已生成，等待用户确认。 */
    PLAN_READY,

    /** 有可运行的任务。 */
    ACTIVE,

    /** 全部必需单元已提交。 */
    READY,

    /** 需要用户处理（审查持续失败、预算耗尽、依赖破裂）。 */
    NEEDS_ATTENTION,

    ARCHIVED,
}

/** 生成任务的运行状态。 */
enum class RunStatus {
    QUEUED,
    RUNNING,
    PAUSE_REQUESTED,
    PAUSED,
    /** 等用户做决定（例如总纲待确认、预算超出）。 */
    WAITING_INPUT,
    RETRY_WAIT,
    FAILED,
    CANCELLED,
    COMPLETED,
}

/** 单元状态。 */
enum class UnitStatus {
    PLANNED,
    GENERATING,
    DRAFT_READY,
    REVIEWING,
    REVISING,
    /** 正式内容已提交，可阅读。 */
    READY,
    /** 上游改动导致需要复查。 */
    STALE,
    FAILED,
}

/**
 * 执行阶段。
 *
 * 与状态正交保存，避免需要的组合状态数量爆炸
 * （RUNNING × SPECIFYING/PLANNING/GENERATING/REVIEWING/REVISING/COMMITTING）。
 */
enum class RunPhase {
    SPECIFYING,
    PLANNING,
    GENERATING,
    REVIEWING,
    REVISING,
    COMMITTING,
}

/** 运行模式：默认停在总纲待确认，全自动必须由用户主动开启。 */
enum class RunMode {
    /** 默认：确认总纲后自动生成。 */
    CONFIRM_OUTLINE,

    /** 用户明确开启：规划阶段自动通过。 */
    FULL_AUTO,
}

/** 审查强度。 */
enum class ReviewPolicy {
    LIGHT,
    STANDARD,
    STRICT,
}
