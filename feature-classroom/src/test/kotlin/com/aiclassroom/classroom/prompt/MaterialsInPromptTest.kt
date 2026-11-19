package com.aiclassroom.classroom.prompt

import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 生成提示词里出现的应该是「相关片段」，不是每份资料的开头。 */
class MaterialsInPromptTest {

    private val now = 1_700_000_000_000L

    private fun spec() = ProjectSpec(
        projectId = ProjectId("p1"), version = 1, rawRequest = "讲面向对象", topic = "Python 面向对象",
        goals = listOf("讲完"), audience = "学生", language = "zh", style = "平实",
        expectedLength = "8 节", unitCount = 8, runMode = RunMode.CONFIRM_OUTLINE,
        reviewPolicy = ReviewPolicy.STANDARD, hardConstraints = emptyList(),
        authorRules = emptyList(), publicWorld = "", createdAt = now,
    )

    private fun longSnapshot() = ContextSnapshot(
        id = SnapshotId("s1"), projectId = ProjectId("p1"), version = 1, createdAt = now,
        materials = listOf(
            SnapshotMaterial(
                name = "讲义.md",
                content = (1..200).joinToString("\n\n") { "第" + it + "节：封装与继承的基础说明。" } +
                    "\n\n第 201 节：多态让子类对象可以替换父类对象。",
            ),
        ),
    )

    private fun build(goal: String) = PromptTemplates.generator(
        PromptTemplates.GeneratorInput(
            spec = spec(), outlineSummary = "主线",
            unit = PlannedUnit(UnitId("u1"), 1, "多态", goal, completionCriteria = listOf("讲清")),
            characters = emptyList(), characterStates = emptyList(), facts = emptyList(),
            previousText = "", snapshot = longSnapshot(),
        ),
    )

    @Test
    fun `任务指向末尾时提示词里带的是末尾片段`() {
        val text = build("讲清楚多态为什么重要")
        assertTrue(text.contains("多态让子类对象可以替换父类对象"), "相关片段应进入提示词")
        assertTrue(text.contains("讲义.md"), "要标出来源")
    }

    @Test
    fun `整份资料不再原样铺进去`() {
        val text = build("讲清楚多态为什么重要")
        // 两百段的大材料不该整份铺进去：没有命中的中段（第 100 节）不该出现。
        assertFalse(text.contains("第100节"), "中段无关内容被塞进了提示词")
        assertTrue(text.contains("第1节"), "开头片段仍应保留一份以便模型有上下文")
    }
}
