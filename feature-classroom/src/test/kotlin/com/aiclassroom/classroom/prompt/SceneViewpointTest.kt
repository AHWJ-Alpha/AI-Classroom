package com.aiclassroom.classroom.prompt

import com.aiclassroom.classroom.domain.CharacterProfile
import com.aiclassroom.classroom.domain.CharacterId
import com.aiclassroom.classroom.domain.ProjectId
import kotlin.test.Test
import kotlin.test.assertTrue

/** 场景必须用讲师第一人称：读起来是当事人经历，不是旁观报告。 */
class SceneViewpointTest {

    private fun profile(name: String) = CharacterProfile(
        id = CharacterId(name),
        projectId = ProjectId("p"),
        name = name,
        identity = "", corePersonality = "", values = "",
        thinkingStyle = "", speechStyle = "", behaviorRules = emptyList(),
    )

    private fun prompt(characters: List<CharacterProfile>) = SceneContinuationPrompt.build(
        SceneContinuationPrompt.Input(
            rawRequest = "一起住一个月",
            unitGoal = "建立借住边界",
            timeRange = "第 1-3 天",
            characters = characters,
            totalTarget = 3000,
            currentChars = 0,
            perCallTarget = 800,
            isFirst = true,
            isLast = false,
            summary = "",
            events = emptyList(),
            usedScenes = emptyList(),
            tailText = "",
            mustFinish = false,
        ),
    )

    @Test
    fun `两个角色时以第一位为第一人称 第二位称作你`() {
        val p = prompt(listOf(profile("她"), profile("哥哥")))
        assertTrue(p.contains("[VIEWPOINT]"))
        assertTrue(p.contains("以「她」的第一人称叙述"))
        assertTrue(p.contains("把「哥哥」称作「你」"))
    }

    @Test
    fun `要求对白用 dialogue 而不是转述`() {
        val p = prompt(listOf(profile("她"), profile("哥哥")))
        assertTrue(p.contains("必须用 dialogue 块"))
        assertTrue(p.contains("不要把台词写进 narration"))
    }

    @Test
    fun `没有角色时不强加视角`() {
        assertTrue(!prompt(emptyList()).contains("[VIEWPOINT]"))
    }
}
