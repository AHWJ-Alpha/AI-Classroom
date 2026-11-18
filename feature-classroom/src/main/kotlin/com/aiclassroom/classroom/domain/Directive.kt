package com.aiclassroom.classroom.domain

/**
 * 单元级指令：用户对**单个单元**方向的微调。
 *
 * 边界是刻意划死的：
 * - 它可以改写本单元的目标、补充要求、说明不要写什么；
 * - 它**不能**改规格里的硬约束、公共背景、作者的全局主线；
 * - 它也不改已经提交的正文，只影响之后的生成。
 *
 * 这样「调方向」和「换一套设定」是两件事：前者随时可做，后者要改规格并重规划。
 */
data class UnitDirective(
    /** 改写后的本单元目标；null 表示沿用总纲里的目标。 */
    val goalOverride: String? = null,
    /** 补充说明：重点、口吻、必须覆盖的点、明确不要写的内容。 */
    val notes: String = "",
    val updatedAt: EpochMillis = 0,
) {
    val isEmpty: Boolean get() = goalOverride.isNullOrBlank() && notes.isBlank()

    /** 实际用于生成的目标：有改写用改写，否则用规划里的。 */
    fun effectiveGoal(plannedGoal: String): String =
        goalOverride?.takeIf { it.isNotBlank() } ?: plannedGoal
}
