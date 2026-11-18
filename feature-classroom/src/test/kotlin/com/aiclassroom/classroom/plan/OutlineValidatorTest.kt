package com.aiclassroom.classroom.plan

import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutlineValidatorTest {

    private val projectId = ProjectId("p1")
    private val now = 1_700_000_000_000L

    private fun unit(
        id: String,
        index: Int,
        dependsOn: List<String> = emptyList(),
        goal: String = "目标",
        criteria: List<String> = listOf("完成条件"),
    ) = PlannedUnit(
        unitId = UnitId(id), index = index, title = "第 " + index + " 节",
        goal = goal, dependsOn = dependsOn.map(::UnitId), completionCriteria = criteria,
    )

    private fun outline(vararg units: PlannedUnit) = ProjectOutline(
        projectId = projectId, version = 1, globalGoal = "目标", mainLine = "主线",
        chapters = listOf("第一章"), units = units.toList(), createdAt = now,
    )

    private fun spec(unitCount: Int) = ProjectSpec(
        projectId = projectId, version = 1, rawRequest = "需求", topic = "主题",
        goals = listOf("目标"), audience = "初学者", language = "zh", style = "平实",
        expectedLength = "短", unitCount = unitCount,
        runMode = RunMode.CONFIRM_OUTLINE, reviewPolicy = ReviewPolicy.STANDARD,
        hardConstraints = emptyList(), authorRules = emptyList(), publicWorld = "",
        createdAt = now,
    )

    @Test
    fun `合法总纲通过校验且给出可执行顺序`() {
        val o = outline(unit("u1", 1), unit("u2", 2, listOf("u1")), unit("u3", 3, listOf("u2")))
        val result = OutlineValidator.validate(o, spec(3))
        assertTrue(result.ok, result.problems.toString())

        val order = OutlineValidator.executionOrder(o.units)
        assertNotNull(order)
        assertEquals(listOf(UnitId("u1"), UnitId("u2"), UnitId("u3")), order)
    }

    @Test
    fun `单元 id 重复是阻断问题`() {
        val o = outline(unit("u1", 1), unit("u1", 2))
        val result = OutlineValidator.validate(o)
        assertTrue(!result.ok)
        assertTrue(result.blocking.any { it.code == "duplicate-id" })
    }

    @Test
    fun `序号重复是阻断问题`() {
        val o = outline(unit("u1", 1), unit("u2", 1))
        assertTrue(OutlineValidator.validate(o).blocking.any { it.code == "duplicate-index" })
    }

    @Test
    fun `缺少结束条件的单元不能进入生成`() {
        val o = outline(unit("u1", 1, criteria = emptyList()))
        assertTrue(OutlineValidator.validate(o).blocking.any { it.code == "no-criteria" })
    }

    @Test
    fun `目标为空是阻断问题`() {
        val o = outline(unit("u1", 1, goal = "  "))
        assertTrue(OutlineValidator.validate(o).blocking.any { it.code == "blank-goal" })
    }

    @Test
    fun `依赖不存在的单元是阻断问题`() {
        val o = outline(unit("u1", 1), unit("u2", 2, listOf("ghost")))
        assertTrue(OutlineValidator.validate(o).blocking.any { it.code == "unknown-dependency" })
    }

    @Test
    fun `自依赖是阻断问题`() {
        val o = outline(unit("u1", 1, listOf("u1")))
        assertTrue(OutlineValidator.validate(o).blocking.any { it.code == "self-dependency" })
    }

    @Test
    fun `依赖成环能被发现且没有可执行顺序`() {
        val o = outline(
            unit("u1", 1, listOf("u3")),
            unit("u2", 2, listOf("u1")),
            unit("u3", 3, listOf("u2")),
        )
        val result = OutlineValidator.validate(o, spec(3))
        assertTrue(!result.ok)
        assertTrue(result.blocking.any { it.code == "dependency-cycle" })
        assertNull(OutlineValidator.executionOrder(o.units))
        assertTrue(OutlineValidator.cyclesOf(o.units).isNotEmpty())
    }

    @Test
    fun `单元数与规格不符是阻断问题`() {
        val o = outline(unit("u1", 1))
        val result = OutlineValidator.validate(o, spec(8))
        assertTrue(result.blocking.any { it.code == "unit-count-mismatch" })
    }

    @Test
    fun `空总纲直接判定不可用`() {
        val o = outline()
        val result = OutlineValidator.validate(o)
        assertTrue(result.problems.any { it.code == "empty" })
        assertTrue(!result.ok)
    }
}
