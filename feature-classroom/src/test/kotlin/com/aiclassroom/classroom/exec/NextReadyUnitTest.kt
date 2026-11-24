package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 依赖链上出现 STALE 时不能卡死。
 *
 * 真机症状：一节标了 STALE，它和它下游的所有节都永远选不中，
 * 报「还有 N 个单元无法开始（依赖未完成）」，表现为写一节就停。
 */
class NextReadyUnitTest {

    private val projectId = ProjectId("p1")

    private fun unit(index: Int, status: UnitStatus, dependsOn: List<String> = emptyList()) =
        ClassroomUnit(
            id = UnitId("u" + index), projectId = projectId, index = index, type = ProjectType.SCENE,
            chapter = "第" + index + "节", goal = "g",
            dependsOn = dependsOn.map(::UnitId),
            requiredCharacters = emptyList(), completionCriteria = listOf("done"),
            inputStateVersion = 0, status = status,
        )

    @Test
    fun `STALE 单元自己可以被选中`() {
        val next = UnitExecutor.nextReadyUnit(listOf(unit(1, UnitStatus.STALE)))
        assertEquals(UnitId("u1"), next?.id, "STALE 的语义就是该重写，必须能选中")
    }

    @Test
    fun `依赖是 STALE 时下游不会提前执行`() {
        // STALE 还没重写完，下游不能跳过它先写。
        val next = UnitExecutor.nextReadyUnit(
            listOf(unit(1, UnitStatus.STALE), unit(2, UnitStatus.PLANNED, listOf("u1"))),
        )
        assertEquals(UnitId("u1"), next?.id)
    }

    @Test
    fun `中间有 STALE 时整条链能继续推进`() {
        // 真机的形状：1 PLANNED → 2 STALE → 3 PLANNED
        val units = listOf(
            unit(1, UnitStatus.READY),
            unit(2, UnitStatus.STALE, listOf("u1")),
            unit(3, UnitStatus.PLANNED, listOf("u2")),
        )
        assertEquals(UnitId("u2"), UnitExecutor.nextReadyUnit(units)?.id, "应该先重写 STALE 的 2")

        // 2 重写完成后，3 立刻可以开始。
        val after = listOf(
            unit(1, UnitStatus.READY),
            unit(2, UnitStatus.READY, listOf("u1")),
            unit(3, UnitStatus.PLANNED, listOf("u2")),
        )
        assertEquals(UnitId("u3"), UnitExecutor.nextReadyUnit(after)?.id)
    }

    @Test
    fun `全部完成时返回 null`() {
        assertNull(UnitExecutor.nextReadyUnit(listOf(unit(1, UnitStatus.READY))))
    }
}
