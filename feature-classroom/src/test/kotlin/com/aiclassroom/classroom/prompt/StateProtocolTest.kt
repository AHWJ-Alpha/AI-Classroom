package com.aiclassroom.classroom.prompt

import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 状态提取协议必须把字段和合法 ID 写清楚。
 *
 * 之前示例只有 value，没写 target / factId；模型照着示例输出，
 * 然后被校验器整份拒掉 —— 正文正常、状态却永远进不去。
 */
class StateProtocolTest {

    private val unit = PlannedUnit(UnitId("u1"), 1, "第 1-3 天", "建立借住边界")

    private fun prompt() = PromptTemplates.stateUpdater(
        PromptTemplates.StateInput(
            unit = unit,
            text = "她把钥匙放在鞋柜上。",
            events = listOf(UnitEvent("event-1", listOf("b1"), "她交出钥匙")),
            characters = listOf(CharacterId("pc-a"), CharacterId("pc-b")),
            facts = listOf(FactId("f-1")),
            projectType = ProjectType.SCENE,
        ),
    )

    @Test
    fun `关系变化必须写明 target`() {
        assertTrue(prompt().contains("SetRelationship：characterId, target, value"), "字段表要写全")
    }

    @Test
    fun `事实变化必须写明 factId`() {
        assertTrue(prompt().contains("LearnFact：characterId, factId"))
    }

    @Test
    fun `提示词里列出真实可用的 ID`() {
        val p = prompt()
        assertTrue(p.contains("pc-a"), "必须给出合法 characterId")
        assertTrue(p.contains("f-1"), "必须给出合法 factId")
        assertTrue(p.contains("event-1"), "必须给出事件 ID")
    }

    @Test
    fun `允许合法空补丁`() {
        val p = prompt()
        assertTrue(p.contains("空补丁"), "场景允许一节没有永久变化")
        assertTrue(p.contains("不要为了让 changes 非空而编造变化"))
    }
}
