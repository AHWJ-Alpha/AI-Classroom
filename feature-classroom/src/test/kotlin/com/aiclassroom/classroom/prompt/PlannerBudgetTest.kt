package com.aiclassroom.classroom.prompt

import com.aiclassroom.classroom.domain.ProjectType
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 规划提示词必须告诉模型「这次能写多少」。
 *
 * 只卡 max_tokens 是不够的：模型不知道上限，会一直写到自己满意为止，
 * 然后被服务端截断，JSON 一断整次规划就废了。
 */
class PlannerBudgetTest {

    private val prompt = PlannerPrompt.build("在你家过一个月", ProjectType.SCENE, 4)

    @Test
    fun `规划里写明单元数量与字段长度上限`() {
        assertTrue(prompt.contains("[SIZE_BUDGET]"))
        assertTrue(prompt.contains("单元数量不超过 4 个"), "必须把上限说成具体数字")
        assertTrue(prompt.contains("goal 不超过 40 个字"))
        assertTrue(prompt.contains("chapters 最多 6 条"))
    }

    @Test
    fun `写不完时要求减细节而不是截断 JSON`() {
        assertTrue(prompt.contains("减少细节而不是截断 JSON"))
    }
}
