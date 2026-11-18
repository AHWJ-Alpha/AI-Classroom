package com.aiclassroom.classroom.prompt

import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GalPromptTest {

    private val now = 1_700_000_000_000L

    private fun spec() = ProjectSpec(
        projectId = ProjectId("p1"), version = 2, rawRequest = "做一个文字 GAL",
        topic = "雨夜", goals = listOf("两个结局"), audience = "读者", language = "zh",
        style = "克制", expectedLength = "6 节", unitCount = 6,
        runMode = RunMode.CONFIRM_OUTLINE, reviewPolicy = ReviewPolicy.STANDARD,
        hardConstraints = emptyList(), authorRules = emptyList(), publicWorld = "", createdAt = now,
    )

    private fun build(type: ProjectType) = PromptTemplates.generator(
        PromptTemplates.GeneratorInput(
            spec = spec(), outlineSummary = "主线",
            unit = PlannedUnit(UnitId("u1"), 1, "开场", "在门口停下", completionCriteria = listOf("做出选择")),
            characters = emptyList(), characterStates = emptyList(), facts = emptyList(),
            previousText = "", projectType = type,
        ),
    )

    @Test
    fun `文字 GAL 的提示词包含选项规则`() {
        val text = build(ProjectType.TEXT_GAL)
        assertTrue(text.contains("[GAL_RULES]"))
        assertTrue(text.contains("choice 块"))
        assertTrue(text.contains("targetNodeId"))
    }

    @Test
    fun `GAL 提示词把真实单元 id 交给模型`() {
        // 不给的话模型只能编 targetNodeId，选项一定指向不存在的节点。
        val text = PromptTemplates.generator(
            PromptTemplates.GeneratorInput(
                spec = spec(), outlineSummary = "主线",
                unit = PlannedUnit(UnitId("u1"), 1, "开场", "停下", completionCriteria = listOf("选择")),
                characters = emptyList(), characterStates = emptyList(), facts = emptyList(),
                previousText = "", projectType = ProjectType.TEXT_GAL,
                unitIndex = listOf(1 to UnitId("u1"), 2 to UnitId("u2"), 3 to UnitId("u3")),
            ),
        )
        assertTrue(text.contains("u2"))
        assertTrue(text.contains("u3"))
        assertTrue(text.contains("不要自己编"))
    }
    @Test
    fun `教学与小说的提示词不含 GAL 规则`() {
        assertFalse(build(ProjectType.TEACHING).contains("[GAL_RULES]"))
        assertFalse(build(ProjectType.NOVEL).contains("[GAL_RULES]"))
    }
}
