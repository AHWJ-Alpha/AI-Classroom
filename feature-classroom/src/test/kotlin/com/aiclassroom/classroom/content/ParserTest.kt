package com.aiclassroom.classroom.content

import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun raw(s: String): String = s.trimIndent()

class UnitContentParserTest {

    private val unitId = UnitId("u1")
    private val charId = CharacterId("c1")

    private fun parse(text: String, known: Set<CharacterId> = setOf(charId)) =
        UnitContentParser.parse(text, unitId, 1, known)

    @Test
    fun `合法正文解析成块与事件`() {
        val text = raw(
            """
            {"unitId":"u1","title":"开场","blocks":[
              {"id":"b1","type":"narration","text":"雨夜"},
              {"id":"b2","type":"dialogue","speakerId":"c1","text":"你来了"}
            ],"events":[{"id":"e1","sourceBlockIds":["b2"],"description":"两人见面"}]}
            """
        )
        val parsed = parse(text)
        assertTrue(parsed.ok, parsed.problems.toString())
        val content = assertNotNull(parsed.content)
        assertEquals(2, content.blocks.size)
        assertIs<ContentBlock.Dialogue>(content.blocks[1])
        assertEquals(1, content.events.size)
    }

    @Test
    fun `未知块类型判定失败而不是静默丢弃`() {
        val parsed = parse(raw("""{"unitId":"u1","blocks":[{"id":"b1","type":"video","text":"x"}]}"""))
        assertNull(parsed.content)
        assertTrue(parsed.problems.any { it.contains("未知的内容块类型") })
    }

    @Test
    fun `台词引用了不存在的角色判定失败`() {
        val parsed = parse(raw("""{"unitId":"u1","blocks":[{"id":"b1","type":"dialogue","speakerId":"ghost","text":"我是谁"}]}"""))
        assertNull(parsed.content)
        assertTrue(parsed.problems.any { it.contains("不存在的角色") })
    }

    @Test
    fun `事件引用不存在的块判定失败`() {
        val text = raw("""{"unitId":"u1","blocks":[{"id":"b1","type":"narration","text":"x"}],"events":[{"id":"e1","sourceBlockIds":["b9"],"description":"d"}]}""")
        val parsed = parse(text)
        assertNull(parsed.content)
        assertTrue(parsed.problems.any { it.contains("不存在的块") })
    }

    @Test
    fun `模型返回的 unitId 被忽略，身份以程序为准`() {
        // 实测：提示词没给 id 时模型会编一个 unit-1。若当成错误，整条链路永远卡在第一单元。
        val text = raw("""{"unitId":"unit-1","blocks":[{"id":"b1","type":"narration","text":"x"}]}""")
        val content = assertNotNull(parse(text).content)
        assertEquals(unitId, content.unitId)
    }

    @Test
    fun `没有 JSON 判定失败`() {
        val parsed = parse("我不方便写这个。")
        assertNull(parsed.content)
        assertTrue(parsed.problems.isNotEmpty())
    }

    @Test
    fun `选项块能解析出带目标的选项`() {
        val text = raw("""{"unitId":"u1","blocks":[{"id":"b1","type":"choice","prompt":"进去吗","options":[{"id":"o1","text":"进去","targetNodeId":"u2"},{"id":"o2","text":"等着","targetNodeId":"u3"}]}]}""")
        val content = assertNotNull(parse(text).content)
        val choice = assertIs<ContentBlock.Choice>(content.blocks[0])
        assertEquals(2, choice.options.size)
        assertEquals("u2", choice.options[0].targetNodeId)
    }

    @Test
    fun `选项块没有可用选项判定失败`() {
        val text = raw("""{"unitId":"u1","blocks":[{"id":"b1","type":"choice","prompt":"犹豫","options":[]}]}""")
        val parsed = parse(text)
        assertNull(parsed.content)
        assertTrue(parsed.problems.any { it.contains("没有任何可用选项") })
    }

    @Test
    fun `选项缺少目标节点判定失败`() {
        val text = raw("""{"unitId":"u1","blocks":[{"id":"b1","type":"choice","prompt":"进去吗","options":[{"id":"o1","text":"进去"}]}]}""")
        assertNull(parse(text).content)
    }
    @Test
    fun `练习的答案与题干分开保存`() {
        val text = raw("""{"unitId":"u1","blocks":[{"id":"b1","type":"exercise","prompt":"1+1=?","answer":"2"}]}""")
        val content = assertNotNull(parse(text).content)
        val exercise = assertIs<ContentBlock.Exercise>(content.blocks[0])
        assertEquals("1+1=?", exercise.prompt)
        assertEquals("2", exercise.answer)
    }
}


