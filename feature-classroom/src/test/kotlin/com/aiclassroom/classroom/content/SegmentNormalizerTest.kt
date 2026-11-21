package com.aiclassroom.classroom.content

import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 分段规范化：id 由程序分配，事件引用同步改写，类型与标题受限。 */
class SegmentNormalizerTest {

    private val speaker = CharacterId("pc-a")
    private val allowed = setOf("narration", "dialogue", "heading")

    private fun content(vararg blocks: ContentBlock, events: List<UnitEvent> = emptyList()) = UnitContent(
        unitId = UnitId("u1"),
        planVersion = 1,
        title = "",
        blocks = blocks.toList(),
        events = events,
    )

    @Test
    fun `块与事件 id 由程序按候选与段号分配`() {
        val c = content(
            ContentBlock.Narration("b1", "她推门进来"),
            ContentBlock.Dialogue("b2", speaker, "你回来了"),
            events = listOf(UnitEvent("e1", listOf("b1"), "她进门")),
        )
        val seg = assertNotNull(SegmentNormalizer.normalize(c, "cand", 2, allowed, setOf(speaker)).segment)

        assertEquals("cand:s2:b1", seg.blocks[0].id)
        assertEquals("cand:s2:b2", seg.blocks[1].id)
        assertEquals("cand:s2:e1", seg.events[0].id)
    }

    @Test
    fun `事件对块的引用跟着改写`() {
        val c = content(
            ContentBlock.Narration("b7", "她把钥匙放在鞋柜上"),
            events = listOf(UnitEvent("e9", listOf("b7"), "交出钥匙")),
        )
        val seg = assertNotNull(SegmentNormalizer.normalize(c, "c1", 1, allowed, setOf(speaker)).segment)
        assertEquals(listOf("c1:s1:b1"), seg.events[0].sourceBlockIds)
    }

    @Test
    fun `事件引用不存在的块被拒绝`() {
        val c = content(
            ContentBlock.Narration("b1", "正文"),
            events = listOf(UnitEvent("e1", listOf("b9"), "引用了不存在的块")),
        )
        val r = SegmentNormalizer.normalize(c, "c1", 1, allowed, setOf(speaker))
        assertNull(r.segment)
        assertTrue(r.problems.any { it.contains("不存在的块") })
    }

    @Test
    fun `讲解类块被拒绝`() {
        val c = content(ContentBlock.Explanation("b1", "这里是讲解"))
        val r = SegmentNormalizer.normalize(c, "c1", 1, allowed, setOf(speaker))
        assertNull(r.segment)
        assertTrue(r.problems.any { it.contains("explanation") })
    }

    @Test
    fun `第二段起不允许新增标题`() {
        val c = content(ContentBlock.Heading("b1", "第 5 天", 2), ContentBlock.Narration("b2", "正文"))
        assertNotNull(SegmentNormalizer.normalize(c, "c1", 1, allowed, setOf(speaker)).segment, "首段允许一个标题")
        val second = SegmentNormalizer.normalize(c, "c1", 2, allowed, setOf(speaker))
        assertNull(second.segment)
        assertTrue(second.problems.any { it.contains("不允许新增标题") })
    }

    @Test
    fun `不存在的说话人被拒绝`() {
        val c = content(ContentBlock.Dialogue("b1", CharacterId("pc-x"), "台词"))
        val r = SegmentNormalizer.normalize(c, "c1", 1, allowed, setOf(speaker))
        assertNull(r.segment)
        assertTrue(r.problems.any { it.contains("不存在的角色") })
    }

    @Test
    fun `字数按统一口径统计 不含标点`() {
        val c = content(
            ContentBlock.Narration("b1", "她推门进来。"),
            ContentBlock.Dialogue("b2", speaker, "你回来了？"),
        )
        val seg = assertNotNull(SegmentNormalizer.normalize(c, "c1", 1, allowed, setOf(speaker)).segment)
        assertEquals(9, seg.chars)
    }
}
