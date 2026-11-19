package com.aiclassroom.classroom.prompt

import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 事实必须按视角进入提示词：作者拿全量并标注，角色只拿它真知道的。 */
class FactsInPromptTest {

    private val now = 1_700_000_000_000L
    private val cid = CharacterId("pc1")

    private fun spec() = ProjectSpec(
        projectId = ProjectId("p1"), version = 2, rawRequest = "讲故事", topic = "校园",
        goals = listOf("讲完"), audience = "读者", language = "zh", style = "平实",
        expectedLength = "短", unitCount = 2, runMode = RunMode.CONFIRM_OUTLINE,
        reviewPolicy = ReviewPolicy.STANDARD, hardConstraints = emptyList(),
        authorRules = emptyList(), publicWorld = "", createdAt = now,
    )

    private fun snapshot(viewMode: String) = ContextSnapshot(
        id = SnapshotId("s1"), projectId = ProjectId("p1"), version = 1,
        createdAt = now, viewMode = viewMode, activeCharacterId = "pc1",
        characters = listOf(
            SnapshotCharacter(
                projectCharacterId = cid, sourceCharacterKey = "林夏.md", name = "林夏",
                persona = "嘴硬心软",
            ),
        ),
        facts = listOf(
            SnapshotFact("校长是幕后黑手", Visibility.AUTHOR_ONLY),
            SnapshotFact("林夏是卧底", Visibility.RESTRICTED, knownBy = setOf("林夏.md")),
            SnapshotFact("这所学校在海边", Visibility.PUBLIC),
            SnapshotFact("周老师知道内情", Visibility.RESTRICTED, knownBy = setOf("周老师")),
        ),
    )

    private fun build(viewMode: String) = PromptTemplates.generator(
        PromptTemplates.GeneratorInput(
            spec = spec(), outlineSummary = "主线",
            unit = PlannedUnit(UnitId("u1"), 1, "开场", "见面", completionCriteria = listOf("见面")),
            characters = emptyList(), characterStates = emptyList(), facts = emptyList(),
            previousText = "", snapshot = snapshot(viewMode),
        ),
    )

    @Test
    fun `作者视角能看到全部事实并带可见范围`() {
        val text = build("author")
        assertTrue(text.contains("校长是幕后黑手"))
        assertTrue(text.contains("仅作者"))
        assertTrue(text.contains("林夏是卧底"))
    }

    @Test
    fun `角色视角拿不到作者私密与他人秘密`() {
        val text = build("character")
        assertFalse(text.contains("校长是幕后黑手"), "作者私密不该给角色")
        assertFalse(text.contains("周老师知道内情"), "别人的秘密不该给这个角色")
    }

    @Test
    fun `角色视角能看到自己知道的与公开的`() {
        val text = build("character")
        assertTrue(text.contains("林夏是卧底"), "自己知道的事实要给")
        assertTrue(text.contains("这所学校在海边"), "公开事实要给")
    }

    @Test
    fun `activeCharacterId 是角色卡文件名时也要能对上`() {
        // 宿主存的是 card.name（如「林夏.md」），而项目角色 ID 是 UUID。
        // 直接比较永远不相等 —— 角色视角会拿到空知识集合，等于「谁都不认识」。
        val snap = ContextSnapshot(
            id = SnapshotId("s1"), projectId = ProjectId("p1"), version = 1,
            createdAt = now, viewMode = "character", activeCharacterId = "林夏.md",
            characters = listOf(
                SnapshotCharacter(
                    projectCharacterId = cid, sourceCharacterKey = "林夏.md", name = "林夏",
                    persona = "嘴硬心软",
                ),
            ),
            facts = listOf(
                SnapshotFact("林夏是卧底", Visibility.RESTRICTED, knownBy = setOf("林夏.md")),
                SnapshotFact("校长是幕后黑手", Visibility.AUTHOR_ONLY),
            ),
        )

        val text = PromptTemplates.generator(
            PromptTemplates.GeneratorInput(
                spec = spec(), outlineSummary = "主线",
                unit = PlannedUnit(UnitId("u1"), 1, "开场", "见面", completionCriteria = listOf("见面")),
                characters = emptyList(), characterStates = emptyList(), facts = emptyList(),
                previousText = "", snapshot = snap,
            ),
        )

        assertTrue(text.contains("林夏是卧底"), "自己知道的事实必须给到")
        assertFalse(text.contains("校长是幕后黑手"), "作者私密不能泄漏")
    }
}
