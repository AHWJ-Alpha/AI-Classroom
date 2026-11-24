package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.content.TextCount
import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.prompt.SceneContinuationPrompt
import kotlin.test.Test
import kotlin.test.assertTrue

/** 每段预算是「必须写满」而不是「建议约」，否则模型只写三分之一。 */
class ScenePromptBudgetTest {

    private fun prompt(perCall: Int) = SceneContinuationPrompt.build(
        SceneContinuationPrompt.Input(
            rawRequest = "一起住", unitGoal = "建立边界", timeRange = "第 1 天",
            characters = emptyList(),
            totalTarget = 1250, currentChars = 0, perCallTarget = perCall,
            isFirst = true, isLast = false, summary = "", events = emptyList(),
            usedScenes = emptyList(), tailText = "", mustFinish = false,
        ),
    )

    @Test
    fun `预算写成必须写满而不是建议`() {
        val p = prompt(800)
        assertTrue(p.contains("必须写满 800 字以上"), "要明确下限：$p".take(200))
        assertTrue(!p.contains("建议新增"), "「建议」会让模型只写一点点")
    }
}
