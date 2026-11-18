package com.aiclassroom.classroom.plan

import com.aiclassroom.classroom.domain.PlannedUnit
import com.aiclassroom.classroom.domain.ProjectOutline
import com.aiclassroom.classroom.domain.ProjectSpec
import com.aiclassroom.classroom.domain.UnitId

/**
 * 总纲的结构校验。
 *
 * 这些检查必须由程序做，不能指望审查模型：依赖环一旦进了正文生成，
 * 会在第 N 章要求「等第 N+2 章的事件先发生」，到那时再发现就整部作废。
 */
object OutlineValidator {

    fun validate(outline: ProjectOutline, spec: ProjectSpec? = null): PlanValidation {
        val problems = mutableListOf<PlanProblem>()

        if (outline.units.isEmpty()) {
            problems += PlanProblem("empty", "总纲里没有任何单元", blocking = true)
            return PlanValidation(problems)
        }

        outline.units.groupBy { it.unitId }.filterValues { it.size > 1 }.forEach { (id, dup) ->
            problems += PlanProblem(
                code = "duplicate-id",
                message = "单元 id 重复：" + id.value + "，出现 " + dup.size + " 次",
                blocking = true,
                unitId = id,
            )
        }

        outline.units.groupBy { it.index }.filterValues { it.size > 1 }.forEach { (index, dup) ->
            problems += PlanProblem(
                code = "duplicate-index",
                message = "单元序号重复：" + index + "，出现 " + dup.size + " 次",
                blocking = true,
            )
        }

        outline.units.forEach { u ->
            if (u.goal.isBlank()) {
                problems += PlanProblem("blank-goal", "单元缺少目标", blocking = true, unitId = u.unitId)
            }
            if (u.completionCriteria.isEmpty()) {
                problems += PlanProblem(
                    code = "no-criteria",
                    message = "单元缺少结束条件，无法判断是否完成：" + u.title,
                    blocking = true,
                    unitId = u.unitId,
                )
            }
        }

        val ids = outline.units.map { it.unitId }.toSet()
        outline.units.forEach { u ->
            u.dependsOn.filterNot { it in ids }.forEach { missing ->
                problems += PlanProblem(
                    code = "unknown-dependency",
                    message = "依赖了不存在的单元：" + missing.value,
                    blocking = true,
                    unitId = u.unitId,
                )
            }
        }

        outline.units.filter { it.dependsOn.contains(it.unitId) }.forEach { u ->
            problems += PlanProblem("self-dependency", "单元依赖了自己", blocking = true, unitId = u.unitId)
        }

        cyclesOf(outline.units).forEach { idsInCycle ->
            problems += PlanProblem(
                code = "dependency-cycle",
                message = "依赖成环：" + idsInCycle.joinToString(" -> ") { it.value.take(8) },
                blocking = true,
                unitId = idsInCycle.firstOrNull(),
            )
        }

        if (spec != null && spec.unitCount > 0 && outline.units.size != spec.unitCount) {
            problems += PlanProblem(
                code = "unit-count-mismatch",
                message = "规划了 " + outline.units.size + " 个单元，规格要求 " + spec.unitCount + " 个",
                blocking = true,
            )
        }

        return PlanValidation(problems)
    }

    /** 返回所有成环的单元；没有环返回空。 */
    fun cyclesOf(units: List<PlannedUnit>): List<List<UnitId>> {
        val indegree = units.associate { it.unitId to 0 }.toMutableMap()
        units.forEach { u ->
            u.dependsOn.forEach { dep ->
                if (indegree.containsKey(dep)) indegree[u.unitId] = (indegree[u.unitId] ?: 0) + 1
            }
        }

        val queue = ArrayDeque(indegree.filterValues { it == 0 }.keys)
        val visited = mutableSetOf<UnitId>()
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            visited += current
            units.filter { it.dependsOn.contains(current) }.forEach { next ->
                val left = (indegree[next.unitId] ?: 0) - 1
                indegree[next.unitId] = left
                if (left == 0) queue.addLast(next.unitId)
            }
        }

        val remaining = units.map { it.unitId }.filterNot { it in visited }
        if (remaining.isEmpty()) return emptyList()

        val cycles = mutableListOf<List<UnitId>>()
        val pool = remaining.toMutableList()
        while (pool.isNotEmpty()) {
            val start = pool.first()
            val chain = mutableListOf(start)
            var current = start
            var guard = 0
            while (guard <= remaining.size) {
                val next = units.firstOrNull { it.unitId == current }
                    ?.dependsOn?.firstOrNull { it in remaining && it !in chain }
                if (next == null) break
                chain += next
                current = next
                guard++
            }
            cycles += chain
            pool.removeAll(chain.toSet())
        }
        return cycles
    }

    /** 生成顺序：依赖在前，同层按序号。有环返回 null。 */
    fun executionOrder(units: List<PlannedUnit>): List<UnitId>? {
        val indegree = units.associate { it.unitId to 0 }.toMutableMap()
        units.forEach { u ->
            u.dependsOn.forEach { dep ->
                if (indegree.containsKey(dep)) indegree[u.unitId] = (indegree[u.unitId] ?: 0) + 1
            }
        }
        val ready = units.filter { indegree[it.unitId] == 0 }.sortedBy { it.index }.map { it.unitId }.toMutableList()
        val order = mutableListOf<UnitId>()
        while (ready.isNotEmpty()) {
            val current = ready.removeAt(0)
            order += current
            units.filter { it.dependsOn.contains(current) }.sortedBy { it.index }.forEach { next ->
                val left = (indegree[next.unitId] ?: 0) - 1
                indegree[next.unitId] = left
                if (left == 0) ready += next.unitId
            }
        }
        return if (order.size == units.size) order else null
    }
}
