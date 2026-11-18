package com.aiclassroom.classroom.prompt

import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotPromptTest {

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
        createdAt = now, mentorName = "老周", mentorPrompt = "说话短促，先问再答",
        userAlias = "同学", viewMode = viewMode,
        publicWorldBook = "学校里没有超自然现象",
        authorWorldBook = "校长其实是幕后黑手",
        characters = listOf(
            SnapshotCharacter(
                projectCharacterId = cid, sourceCharacterKey = "角色A.card", name = "林夏",
                persona = "嘴硬心软", speechStyle = "爱用反问",
                authorPrivate = "她其实是卧底",
            ),
        ),
        materials = listOf(SnapshotMaterial(name = "讲义.pdf", content = "第一章：基础")),
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
    fun `讲师身份与公共背景进入提示词`() {
        val text = build("author")
        assertTrue(text.contains("[MENTOR_IDENTITY]"))
        assertTrue(text.contains("说话短促，先问再答"))
        assertTrue(text.contains("学校里没有超自然现象"))
    }

    @Test
    fun `作者视角可以看到作者私密段落`() {
        val text = build("author")
        assertTrue(text.contains("她其实是卧底"))
    }

    @Test
    fun `角色视角绝不带作者私密段落`() {
        val text = build("character")
        assertFalse(text.contains("她其实是卧底"))
        // 但角色的公开人格要给
        assertTrue(text.contains("嘴硬心软"))
    }

    @Test
    fun `作者世界书任何视角都不直接注入`() {
        // 作者世界书只作为作者侧的规划资料，不进生成正文
        assertFalse(build("author").contains("校长其实是幕后黑手"))
        assertFalse(build("character").contains("校长其实是幕后黑手"))
    }

    @Test
    fun `资料以内容形式进入提示词并截断`() {
        val text = build("author")
        assertTrue(text.contains("[DATA:MATERIALS]"))
        assertTrue(text.contains("讲义.pdf"))
        assertTrue(text.contains("第一章：基础"))
    }

    @Test
    fun `超长资料只取前段并标注截断`() {
        val long = "字".repeat(5000)
        val excerpt = SnapshotMaterial.excerpt(long, limit = 100)
        assertTrue(excerpt.length < long.length)
        assertTrue(excerpt.contains("已截断"))
    }
}
