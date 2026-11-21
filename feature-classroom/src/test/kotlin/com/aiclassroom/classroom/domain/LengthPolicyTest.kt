package com.aiclassroom.classroom.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 篇幅：用户说多少就是多少，超范围明确拒绝，故事时间不算字数。 */
class LengthPolicyTest {

    private fun ok(raw: String, type: ProjectType = ProjectType.SCENE, units: Int? = null): LengthPlan.Ok =
        assertIs<LengthPlan.Ok>(LengthPlan.parse(raw, type, units))

    @Test
    fun `每节 200 字就是 200 不夹取`() {
        assertEquals(200, ok("每节 200 字").unitTarget)
    }

    @Test
    fun `每节 5000 字就是 5000`() {
        assertEquals(5000, ok("每节 5000 字，两个人住一个月").unitTarget)
    }

    @Test
    fun `一个月是故事时间 不是字数`() {
        val p = ok("接下来在你家过一个月，达到亲密无间")
        assertEquals(4000, p.unitTarget, "场景默认 4000")
        assertFalse(p.fromUser)
    }

    @Test
    fun `全篇十万字二十节 摊到每节`() {
        val p = ok("全篇 10 万字，共 20 节", ProjectType.NOVEL)
        assertEquals(100_000, p.totalTarget)
        assertEquals(5000, p.unitTarget)
    }

    @Test
    fun `没有显式节数时按正式总纲节数分配`() {
        val p = ok("全篇 9 万字", ProjectType.NOVEL, units = 15)
        assertEquals(6000, p.unitTarget, "90000 / 15")
    }

    @Test
    fun `超过单节支持范围明确拒绝 不静默夹取`() {
        val rejected = LengthPlan.parse("每节 90000 字", ProjectType.SCENE)
        val u = assertIs<LengthPlan.Unsupported>(rejected)
        assertTrue(u.reason.contains("90000"))
        assertTrue(u.reason.contains("超出"))
    }

    @Test
    fun `类型默认各不相同`() {
        assertEquals(6000, ok("写一部小说", ProjectType.NOVEL).unitTarget)
        assertEquals(2500, ok("做个 gal", ProjectType.TEXT_GAL).unitTarget)
        assertEquals(2000, ok("讲清楚指针", ProjectType.TEACHING).unitTarget)
    }

    @Test
    fun `单节与全篇冲突时保留两者并给出说明`() {
        val p = ok("每节 5000 字，全篇 8 万字，共 20 节", ProjectType.NOVEL)
        assertEquals(5000, p.unitTarget, "单节以显式值为准")
        assertEquals(80_000, p.totalTarget)
        assertTrue(p.notes.any { it.contains("不一致") }, "冲突要说明，不能偷偷覆盖")
    }

    @Test
    fun `小数万字可解析`() {
        assertEquals(12_000, ok("每章 1.2 万字", ProjectType.NOVEL).unitTarget)
    }

    @Test
    fun `分段数留出余量且不超过硬上限`() {
        val p = ok("每节 5000 字")
        assertEquals(1200, p.perCallTarget, "单次预算按输出上限收缩")
        assertTrue(p.maxSegments >= 3, "基础段数之外要有补写余量")
        val big = ok("每节 20000 字")
        assertTrue(big.maxSegments <= LengthPlan.MAX_SEGMENTS_HARD)
    }
}
