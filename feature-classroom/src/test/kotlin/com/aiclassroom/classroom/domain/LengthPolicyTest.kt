package com.aiclassroom.classroom.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 篇幅解析：用户明确优先，类型默认兜底，故事时间不算字数。 */
class LengthPolicyTest {

    @Test
    fun `每节 5000 字直接设置单节目标`() {
        val p = LengthPolicy.parse("每节 5000 字，写一个月同居", ProjectType.SCENE)
        assertEquals(5000, p.unitTarget)
        assertTrue(p.fromUser)
    }

    @Test
    fun `全篇十万字二十节摊到每一节`() {
        val p = LengthPolicy.parse("全篇 10 万字，共 20 节", ProjectType.NOVEL)
        assertEquals(100_000, p.totalTarget)
        assertEquals(5000, p.unitTarget)
    }

    @Test
    fun `一个月是故事时间 不是字数`() {
        val p = LengthPolicy.parse("接下来我要在你家过一个月，达到亲密无间", ProjectType.SCENE)
        assertEquals(4000, p.unitTarget, "场景默认每节 4000 字")
        assertFalse(p.fromUser, "没提字数就不算用户指定")
    }

    @Test
    fun `类型默认各不相同`() {
        assertEquals(6000, LengthPolicy.parse("写一部小说", ProjectType.NOVEL).unitTarget)
        assertEquals(2500, LengthPolicy.parse("做个 gal", ProjectType.TEXT_GAL).unitTarget)
        assertEquals(2000, LengthPolicy.parse("讲清楚指针", ProjectType.TEACHING).unitTarget)
    }

    @Test
    fun `单节目标被夹在合理区间`() {
        assertEquals(1500, LengthPolicy.parse("每节 200 字", ProjectType.SCENE).unitTarget)
        assertEquals(20_000, LengthPolicy.parse("每节 90000 字", ProjectType.SCENE).unitTarget)
    }

    @Test
    fun `续写次数按单次预算折算且有上限`() {
        val p = LengthPolicy.parse("每节 5000 字", ProjectType.SCENE)
        assertEquals(2500, p.perCallTarget)
        assertEquals(2, p.maxSegments, "5000 / 2500 = 2 段")
        val big = LengthPolicy.parse("每节 20000 字", ProjectType.SCENE)
        assertTrue(big.maxSegments <= 12, "不能无限续写")
    }

    @Test
    fun `单节目标低于单次预算时只跑一段`() {
        val p = LengthPolicy.parse("每节 2000 字", ProjectType.SCENE)
        assertEquals(1, p.maxSegments)
    }
}
