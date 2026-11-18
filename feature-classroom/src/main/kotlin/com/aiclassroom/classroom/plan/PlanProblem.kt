package com.aiclassroom.classroom.plan

import com.aiclassroom.classroom.domain.UnitId

/**
 * 规划阶段的问题。
 *
 * [blocking] 为真表示不能进入正文生成：依赖环、重复单元、超出预算这类问题
 * 会让后续生成全部作废，必须退回重规划；只是建议的就不该拦路。
 */
data class PlanProblem(
    val code: String,
    val message: String,
    val blocking: Boolean,
    val unitId: UnitId? = null,
)

/** 规划校验结果。 */
data class PlanValidation(val problems: List<PlanProblem>) {
    val blocking: List<PlanProblem> get() = problems.filter { it.blocking }
    val ok: Boolean get() = blocking.isEmpty()
}