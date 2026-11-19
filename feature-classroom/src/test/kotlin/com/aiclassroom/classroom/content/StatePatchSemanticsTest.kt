package com.aiclassroom.classroom.content

import com.aiclassroom.classroom.domain.CharacterId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 状态补丁的四种结果必须彼此可分：
 * 合法变化 / 合法空补丁 / 全部被丢弃 / 输出读不懂。
 */
class StatePatchSemanticsTest {

    private val characters = setOf(CharacterId("c1"))
    private val events = setOf("e1")

    private fun parse(raw: String) = StatePatchParser.parse(raw, characters, events)

    @Test
    fun `合法空补丁是可用结果且没有变化`() {
        val parsed = parse("""{"changes":[],"reason":"本单元没有状态变化","evidenceEventIds":[]}""")
        assertTrue(parsed.usable, "结构可读就应该可用")
        assertFalse(parsed.hasChanges)
        assertFalse(parsed.allDropped, "空补丁不是「全被丢弃」")
    }

    @Test
    fun `声明了变化却没有事件证据时算全部被丢弃`() {
        val parsed = parse("""{"changes":[{"op":"SetEmotion","characterId":"c1","value":"紧张"}],"reason":"x","evidenceEventIds":[]}""")
        assertTrue(parsed.usable)
        assertFalse(parsed.hasChanges)
        assertTrue(parsed.allDropped, "给了变化却没证据 = 不可信，不能当空补丁")
    }

    @Test
    fun `引用不存在的事件也会被丢弃`() {
        val parsed = parse("""{"changes":[{"op":"SetEmotion","characterId":"c1","value":"紧张"}],"reason":"x","evidenceEventIds":["e404"]}""")
        assertTrue(parsed.allDropped)
    }

    @Test
    fun `乱码属于不可用`() {
        assertFalse(parse("我无法判断").usable)
    }

    @Test
    fun `合法变化既可用又有变化`() {
        val parsed = parse("""{"changes":[{"op":"SetEmotion","characterId":"c1","value":"紧张"}],"reason":"x","evidenceEventIds":["e1"]}""")
        assertTrue(parsed.usable)
        assertTrue(parsed.hasChanges)
        assertFalse(parsed.allDropped)
    }
}
