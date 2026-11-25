package com.aiclassroom.classroom.prompt

import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 规划与审查也要看见来源设定。
 *
 * 之前只有正文生成器拿到快照，规划器只收原始需求，审查器只收规格+正文，
 * 于是总纲和审查意见都跟人格/资料/事实脱节。
 */
class PlannerReviewerContextTest {

    private val now = 1_700_000_000_000L

    private fun spec() = ProjectSpec(
        projectId = ProjectId("p1"), version = 1, rawRequest = "讲面向对象", topic = "Python",
        goals = listOf("讲完"), audience = "学生", language = "zh", style = "平实",
        expectedLength = "8 节", unitCount = 8, runMode = RunMode.CONFIRM_OUTLINE,
        reviewPolicy = ReviewPolicy.STANDARD, hardConstraints = listOf("不要用比喻"),
        authorRules = emptyList(), publicWorld = "", createdAt = now,
    )

    private fun snapshot() = ContextSnapshot(
        id = SnapshotId("s1"), projectId = ProjectId("p1"), version = 1, createdAt = now,
        mentorName = "老周", mentorPrompt = "说话短促，先问再答",
        publicWorldBook = "这门课只讲 Python 3",
        authorWorldBook = "校长是幕后黑手",
        characters = listOf(
            SnapshotCharacter(
                projectCharacterId = CharacterId("c1"), sourceCharacterKey = "林夏.md",
                name = "林夏", persona = "嘴硬心软",
            ),
        ),
        facts = listOf(
            SnapshotFact("学生怕数学", Visibility.RESTRICTED, knownBy = setOf("林夏.md")),
            SnapshotFact("老师是退役军人", Visibility.AUTHOR_ONLY),
        ),
        materials = listOf(SnapshotMaterial(name = "讲义.md", content = "第一章 对象与类")),
    )

    @Test
    fun `规划器拿到人格 世界 角色 事实与资料`() {
        val text = PlannerPrompt.build("讲面向对象", ProjectType.TEACHING, 8, snapshot())
        assertTrue(text.contains("说话短促"))
        assertTrue(text.contains("这门课只讲 Python 3"))
        assertTrue(text.contains("林夏"))
        assertTrue(text.contains("老师是退役军人"), "作者侧事实要给规划器")
        assertTrue(text.contains("第一章 对象与类"))
        assertTrue(text.contains("校长是幕后黑手"), "作者世界书要参与规划")
    }

    @Test
    fun `没有快照时规划器不凭空造设定`() {
        val text = PlannerPrompt.build("讲面向对象", ProjectType.TEACHING, 8, null)
        assertFalse(text.contains("[MENTOR_IDENTITY]"))
        assertFalse(text.contains("[DATA:MATERIALS]"))
    }
}
