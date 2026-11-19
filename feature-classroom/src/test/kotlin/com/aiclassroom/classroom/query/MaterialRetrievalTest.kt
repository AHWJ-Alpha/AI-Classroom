package com.aiclassroom.classroom.query

import com.aiclassroom.classroom.domain.SnapshotMaterial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 长资料必须能被「翻到后半部分」。
 *
 * 旧实现是每份取前 4000 字，一本讲义的后半段永远进不了上下文。
 */
class MaterialRetrievalTest {

    private fun longMaterial(): SnapshotMaterial {
        // 前半段讲基础，最后一段才讲到「多态」。
        val front = (1..40).joinToString("\n\n") { "第" + it + "节：封装与继承的基础说明，内容与后面的主题无关。" }
        val tail = "第 41 节：多态让子类对象可以替换父类对象，这是本节的核心。"
        return SnapshotMaterial(name = "讲义.md", content = front + "\n\n" + tail)
    }

    @Test
    fun `超长资料被切成多块且不丢内容`() {
        val m = longMaterial()
        val chunks = MaterialRetrieval.chunk(m.content, chunkSize = 200)
        assertTrue(chunks.size >= 3, "应切成多块，实际 " + chunks.size)
        val joined = chunks.joinToString("") { it.replace("\n", "").replace(" ", "") }
        assertTrue(joined.contains("多态让子类对象可以替换父类对象"), "最后一段不能被丢掉")
    }

    @Test
    fun `问题指向末尾时能召回末尾片段`() {
        val picked = MaterialRetrieval.select(
            materials = listOf(longMaterial()),
            query = "解释一下多态",
        )
        assertTrue(picked.isNotEmpty())
        assertTrue(
            picked.any { it.text.contains("多态") },
            "应该命中末尾那段，实际选中：" + picked.map { it.chunkIndex },
        )
    }

    @Test
    fun `没有关键词时退化为开头片段而不是什么都不给`() {
        val picked = MaterialRetrieval.select(
            materials = listOf(longMaterial()),
            query = "",
        )
        assertEquals(1, picked.size)
        assertEquals(0, picked.first().chunkIndex)
    }

    @Test
    fun `总预算受限`() {
        val materials = (1..5).map { SnapshotMaterial(name = "资料" + it + ".md", content = "内容".repeat(500)) }
        val picked = MaterialRetrieval.select(materials, query = "内容", totalBudget = 1200, chunkSize = 300)
        val used = picked.sumOf { it.text.length }
        assertTrue(used <= 1200 + 300, "预算不该被大幅突破，实际 " + used)
    }
}
