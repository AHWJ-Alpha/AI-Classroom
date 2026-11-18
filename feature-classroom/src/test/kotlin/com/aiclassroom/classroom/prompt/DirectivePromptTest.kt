package com.aiclassroom.classroom.prompt

import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DirectivePromptTest {

    private val now = 1_700_000_000_000L
    private val projectId = ProjectId("p1")

    private fun spec() = ProjectSpec(
        projectId = projectId, version = 2, rawRequest = "原始需求", topic = "Python OOP",
        goals = listOf("学会面向对象"), audience = "初学者", language = "zh", style = "平实",
        expectedLength = "8 节", unitCount = 8, runMode = RunMode.CONFIRM_OUTLINE,
        reviewPolicy = ReviewPolicy.STANDARD,
        hardConstraints = listOf("不要使用框架"), authorRules = emptyList(),
        publicWorld = "", createdAt = now,
    )

    private fun unit() = PlannedUnit(
        unitId = UnitId("u1"), index = 1, title = "类与对象",
        goal = "理解类的概念", completionCriteria = listOf("能举例"),
    )

    private fun build(directive: UnitDirective?) = PromptTemplates.generator(
        PromptTemplates.GeneratorInput(
            spec = spec(), outlineSummary = "主线", unit = unit(),
            characters = emptyList(), characterStates = emptyList(), facts = emptyList(),
            previousText = "", directive = directive,
        ),
    )

    @Test
    fun `没有指令时不出现 UNIT_DIRECTIVE 区块`() {
        val text = build(null)
        assertFalse(text.contains("[UNIT_DIRECTIVE]"))
        assertTrue(text.contains("理解类的概念"))
    }

    @Test
    fun `改写目标会替换掉原目标并带上补充要求`() {
        val text = build(UnitDirective(goalOverride = "只讲类的定义与实例化", notes = "不要提前讲继承"))
        assertTrue(text.contains("[UNIT_DIRECTIVE]"))
        assertTrue(text.contains("只讲类的定义与实例化"))
        assertTrue(text.contains("不要提前讲继承"))
        assertFalse(text.contains("目标：理解类的概念"))
    }

    @Test
    fun `指令区块明确写了不得违反硬约束和全局主线`() {
        val text = build(UnitDirective(notes = "补充一点"))
        assertTrue(text.contains("不得违反上面的硬约束"))
        assertTrue(text.contains("不得改变全局主线"))
    }

    @Test
    fun `硬约束仍然保留在提示词里不被指令覆盖`() {
        val text = build(UnitDirective(goalOverride = "换个讲法"))
        assertTrue(text.contains("不要使用框架"))
    }

    @Test
    fun `空指令视为没有指令`() {
        val text = build(UnitDirective(goalOverride = "  ", notes = ""))
        assertFalse(text.contains("[UNIT_DIRECTIVE]"))
    }

    @Test
    fun `effectiveGoal 在改写为空时回退到规划目标`() {
        assertEquals("规划目标", UnitDirective(null, "").effectiveGoal("规划目标"))
        assertEquals("规划目标", UnitDirective("   ", "").effectiveGoal("规划目标"))
        assertEquals("改写的", UnitDirective("改写的", "").effectiveGoal("规划目标"))
    }
}
