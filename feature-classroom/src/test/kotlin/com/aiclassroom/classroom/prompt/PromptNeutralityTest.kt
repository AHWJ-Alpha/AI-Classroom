package com.aiclassroom.classroom.prompt

import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 提示词不得把课堂写成「教学」。
 *
 * 课堂是应用外层包装；模型看到的应当是「写一段连贯长内容」。
 * 一旦提示词里出现课程/教学/讲课，亲密或剧情向的长篇会被压成讲义。
 */
class PromptNeutralityTest {

    private val now = 1_700_000_000_000L
    private val projectId = ProjectId("p1")

    private fun spec() = ProjectSpec(
        projectId = projectId, version = 1,
        rawRequest = "我想慢慢来，先别急着推进关系，多写她的犹豫",
        topic = "夏夜", goals = listOf("推进关系"), audience = "自己",
        language = "zh", style = "细腻", expectedLength = "若干节", unitCount = 3,
        runMode = RunMode.CONFIRM_OUTLINE, reviewPolicy = ReviewPolicy.STANDARD,
        hardConstraints = emptyList(), authorRules = emptyList(),
        publicWorld = "", createdAt = now,
    )

    private fun unit() = PlannedUnit(
        UnitId("u1"), 1, "雨夜", "写到她第一次松口",
        completionCriteria = listOf("她说了半句真话"),
    )

    private fun generator(): String = PromptTemplates.generator(
        PromptTemplates.GeneratorInput(
            spec = spec(),
            outlineSummary = "主线：两个人在雨天重遇",
            unit = unit(),
            characters = emptyList(),
            characterStates = emptyList(),
            facts = emptyList(),
            previousText = "",
        ),
    )

    @Test
    fun `原始需求原文必须进入生成提示词`() {
        val prompt = generator()
        assertTrue(
            prompt.contains("先别急着推进关系"),
            "规划会把原话压成摘要，摘要留不住语气与尺度",
        )
        assertTrue(prompt.contains("[ORIGINAL_REQUEST]"))
        assertTrue(prompt.contains("[/ORIGINAL_REQUEST]"))
    }


    @Test
    fun `场景类型的提示词要求反流水线`() {
        val prompt = PromptTemplates.typeGuidance(ProjectType.SCENE)
        assertTrue(prompt.contains("共同生活的时间线"), "场景不是小说章节")
        assertTrue(prompt.contains("流水账"), "必须明确禁止流水账")
        assertTrue(prompt.contains("关系状态"), "每节要推进关系状态")
        assertTrue(prompt.contains("安静段落"), "允许没有事件的段落，否则节奏必然被推成事件流水")
    }

    @Test
    fun `场景类型的生成提示词带上指导`() {
        val prompt = PromptTemplates.generator(
            PromptTemplates.GeneratorInput(
                spec = spec(),
                outlineSummary = "主线：一个月同居",
                unit = unit(),
                characters = emptyList(),
                characterStates = emptyList(),
                facts = emptyList(),
                previousText = "",
                projectType = ProjectType.SCENE,
            ),
        )
        assertTrue(prompt.contains("[TYPE_GUIDANCE]"))
        assertTrue(prompt.contains("共同生活的时间线"), "指导内容要真的进请求")
    }



    @Test
    fun `叙事类型不得出现讲解类块`() {
        for (type in listOf(ProjectType.SCENE, ProjectType.NOVEL)) {
            val types = PromptTemplates.blockTypes(type)
            assertTrue(!types.contains("explanation"), "叙事类型不该给讲解块")
            assertTrue(!types.contains("example"))
            assertTrue(!types.contains("exercise"))
            assertTrue(types.contains("narration"))
            assertTrue(types.contains("dialogue"))
        }
    }

    @Test
    fun `场景的生成请求里没有讲解块也没有教学目标措辞`() {
        val prompt = PromptTemplates.generator(
            PromptTemplates.GeneratorInput(
                spec = spec(),
                outlineSummary = "一个月同居",
                unit = unit(),
                characters = emptyList(),
                characterStates = emptyList(),
                facts = emptyList(),
                previousText = "",
                projectType = ProjectType.SCENE,
            ),
        )
        assertTrue(!prompt.contains("explanation / example / exercise"), "块类型词表必须收紧")
        assertTrue(prompt.contains("不要写成知识点清单"), "要明确 objectivesCovered 填什么")
    }

    @Test
    fun `教学类型仍然保留讲解与练习`() {
        val types = PromptTemplates.blockTypes(ProjectType.TEACHING)
        assertTrue(types.contains("explanation"))
        assertTrue(types.contains("exercise"))
    }

}
