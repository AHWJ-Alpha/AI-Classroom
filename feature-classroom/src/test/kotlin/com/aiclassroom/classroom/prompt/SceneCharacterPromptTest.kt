package com.aiclassroom.classroom.prompt

import com.aiclassroom.classroom.domain.CharacterId
import com.aiclassroom.classroom.domain.CharacterProfile
import com.aiclassroom.classroom.domain.ProjectId
import kotlin.test.Test
import kotlin.test.assertTrue

/** 角色人格必须全部进入提示词：只给名字等于让模型自己编性格。 */
class SceneCharacterPromptTest {

    private fun prompt(vararg characters: CharacterProfile) = SceneContinuationPrompt.build(
        SceneContinuationPrompt.Input(
            rawRequest = "一起住一个月",
            unitGoal = "建立边界",
            timeRange = "第 1-3 天",
            characters = characters.toList(),
            totalTarget = 3000, currentChars = 0, perCallTarget = 800,
            isFirst = true, isLast = false, summary = "", events = emptyList(),
            usedScenes = emptyList(), tailText = "", mustFinish = false,
        ),
    )

    private fun mentor() = CharacterProfile(
        id = CharacterId("m"), projectId = ProjectId("p"), name = "讲师",
        identity = "叙述者",
        corePersonality = "克制、不主动表白",
        values = "不越界",
        thinkingStyle = "先观察再行动",
        speechStyle = "短句",
        behaviorRules = listOf("不替对方做决定"),
        authorPrivate = "其实早就注意到她",
    )

    @Test
    fun `人格各字段都进提示词`() {
        val p = prompt(mentor())
        assertTrue(p.contains("身份=叙述者"))
        assertTrue(p.contains("人格=克制、不主动表白"))
        assertTrue(p.contains("看重=不越界"))
        assertTrue(p.contains("思维习惯=先观察再行动"))
        assertTrue(p.contains("语言=短句"))
        assertTrue(p.contains("行为约束=不替对方做决定"))
    }

    @Test
    fun `作者私密标明不能说出来`() {
        val p = prompt(mentor())
        assertTrue(p.contains("作者私密（不要让人物说出来）"))
    }
}
