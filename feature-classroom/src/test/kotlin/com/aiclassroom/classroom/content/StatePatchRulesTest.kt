package com.aiclassroom.classroom.content

import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 状态补丁的合法性判定。
 *
 * 三条边界最容易错：缺 target / 缺 factId 被整份拒绝；
 * 合法空补丁算成功；全部被丢弃算失败（不能当成「没有变化」）。
 */
class StatePatchRulesTest {

    private val a = CharacterId("pc-a")
    private val b = CharacterId("pc-b")
    private val known = setOf(a, b)

    private fun parse(raw: String) = StatePatchParser.parse(raw, known, setOf("event-1"))

    @Test
    fun `合法的关系变化可以解析`() {
        val raw = """{"changes":[{"op":"SetRelationship","characterId":"pc-a","target":"pc-b","value":"开始互相照应"}],"reason":"她替他留了灯","evidenceEventIds":["event-1"]}"""
        val p = parse(raw)
        assertTrue(p.usable, "结构可读")
        assertEquals(1, p.changes.size, "缺字段不该出现在这里：" + p.dropped)
    }

    @Test
    fun `缺少 target 的关系变化被拒绝`() {
        val raw = """{"changes":[{"op":"SetRelationship","characterId":"pc-a","value":"更好"}],"reason":"x","evidenceEventIds":["event-1"]}"""
        val p = parse(raw)
        assertTrue(p.changes.isEmpty())
        assertTrue(p.allDropped, "缺字段必须算失败，不能当成没有变化")
        assertTrue(p.dropped.any { it.contains("target") }, "要指出缺 target")
    }

    @Test
    fun `缺少 factId 的事实变化被拒绝`() {
        val raw = """{"changes":[{"op":"LearnFact","characterId":"pc-a"}],"reason":"x","evidenceEventIds":["event-1"]}"""
        val p = parse(raw)
        assertTrue(p.allDropped)
        assertTrue(p.dropped.any { it.contains("factId") })
    }

    @Test
    fun `合法的空补丁算成功`() {
        val raw = """{"changes":[],"reason":"这一节只是日常","evidenceEventIds":[]}"""
        val p = parse(raw)
        assertTrue(p.usable, "空补丁是合法结果")
        assertFalse(p.hasChanges)
        assertFalse(p.allDropped, "本来就是空的，不算全部被丢弃")
    }

    @Test
    fun `未知角色被拒绝`() {
        val raw = """{"changes":[{"op":"SetEmotion","characterId":"pc-x","value":"紧张"}],"reason":"x","evidenceEventIds":[]}"""
        val p = parse(raw)
        assertTrue(p.allDropped)
    }
}
