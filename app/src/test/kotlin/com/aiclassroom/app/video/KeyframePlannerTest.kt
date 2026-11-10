package com.aiclassroom.app.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 选帧算法的验收：给它一条造好的差异曲线，断言它把预算花在了该花的地方。
 * 这些断言就是「自适应比均匀强」的形式化版本——变化点必须被选中，静止段落只留代表。
 */
class KeyframePlannerTest {

    private fun options(maxFrames: Int, minGapMs: Long = 800L, intervalMs: Long = 1000L) =
        KeyframePlanner.Options(maxFrames = maxFrames, minGapMs = minGapMs, intervalMs = intervalMs)

    @Test
    fun emptyAndSinglePointCurvesAreHandled() {
        assertTrue(KeyframePlanner.plan(emptyList(), options(12)).isEmpty())
        assertEquals(listOf(0), KeyframePlanner.plan(listOf(0f), options(12)))
        assertEquals(listOf(0), KeyframePlanner.plan(listOf(0.3f, 0.9f, 0f), options(1)))
    }

    @Test
    fun picksTheLocalPeakWhereTheScreenChanged() {
        // 0..4 静止，第 5 个点画面大变，之后又静止：这一帧必须被选中，否则「点开菜单」就漏了。
        val curve = MutableList(10) { 0f }
        curve[5] = 0.9f
        val picked = KeyframePlanner.plan(curve, options(4))
        assertTrue("变化点 5 必须被选中，实际：" + picked, picked.contains(5))
        assertEquals("首帧锚点必须在", 0, picked.first())
        assertEquals(picked.sorted(), picked)
    }

    @Test
    fun respectsMinGapBetweenSelectedPoints() {
        // 第 3、4 点连着都很大：间隔要求 2 个点（minGap 2000ms / 扫描 1000ms）时只能留一个。
        val curve = MutableList(12) { 0f }
        curve[3] = 0.8f
        curve[4] = 0.95f
        val picked = KeyframePlanner.plan(curve, options(6, minGapMs = 2000L))
        assertTrue("4 比 3 变化大，应留 4", picked.contains(4))
        assertTrue("3 与 4 间隔不足，不能同时选中，实际：" + picked, !picked.contains(3))
    }

    @Test
    fun fillsStaticSegmentsWithRepresentativeFrames() {
        // 整条曲线是平的（30 秒静止画面）：变化点为 0，靠第 3 轮补齐，且必须铺开而不是挤在一起。
        val curve = MutableList(30) { 0f }
        val picked = KeyframePlanner.plan(curve, options(6))
        assertEquals(6, picked.size)
        assertEquals(0, picked.first())
        assertTrue("静止段落也要有靠后的代表帧，实际：" + picked, picked.last() > 20)
        picked.zipWithNext { left, right -> assertTrue("选点不应重复", left < right) }
    }

    @Test
    fun neverExceedsMaxFrames() {
        // 每一帧都在变（快速滚动）：预算必须被守住。
        val curve = MutableList(40) { index -> if (index % 2 == 0) 0f else 0.3f + index * 0.01f }
        assertEquals(5, KeyframePlanner.plan(curve, options(5)).size)
        assertEquals(1, KeyframePlanner.plan(curve, options(1)).size)
    }

    @Test
    fun shortCurveReturnsEveryPointSorted() {
        val curve = listOf(0f, 0.2f, 0.1f, 0.4f, 0.05f)
        assertEquals(listOf(0, 1, 2, 3, 4), KeyframePlanner.plan(curve, options(12)))
    }

    @Test
    fun spendsBudgetOnTheBiggestChangesFirst() {
        // 30 个点里有 5 个变化点，预算 4（含首帧锚点）：最大的 3 个必须入选，最小的两个可以被挤掉。
        val curve = MutableList(30) { 0f }
        mapOf(5 to 0.5f, 10 to 0.9f, 15 to 0.7f, 20 to 0.3f, 25 to 0.1f).forEach { (index, value) ->
            curve[index] = value
        }
        val picked = KeyframePlanner.plan(curve, options(4))
        assertEquals(4, picked.size)
        assertTrue("最大变化点必须入选", picked.contains(10))
        assertTrue("次大变化点必须入选", picked.contains(15))
        assertTrue("第三大变化点必须入选", picked.contains(5))
    }

    @Test
    fun quietNoiseIsNotTreatedAsChange() {
        // 编码噪声级别的抖动（< 0.0015）不构成「画面变了」，否则每一帧都会被当成峰值。
        val curve = MutableList(20) { index -> if (index % 3 == 0) 0.0012f else 0.0004f }
        val picked = KeyframePlanner.plan(curve, options(4))
        assertEquals(4, picked.size)
        assertEquals(0, picked.first())
    }

    @Test
    fun faintButRealChangesStillWinOverStaticSegments() {
        // 实测场景：白底设置页滚动，差异只有 0.03-0.04（深色画面能到 0.3）。
        // 固定阈值会把这种视频整片判成静止，相对阈值才能把预算花在真正滚动的段落上。
        // 差异只有 0.005-0.006（白底界面轻微滚动）：写死 0.01 的绝对阈值会把它们全判成静止，
        // 于是预算又平铺到整条曲线上；相对阈值才能把它们当峰值抓出来。
        val curve = MutableList(40) { 0f }
        listOf(2 to 0.0055f, 3 to 0.006f, 20 to 0.0052f, 21 to 0.0058f).forEach { (index, value) ->
            curve[index] = value
        }
        val picked = KeyframePlanner.plan(curve, options(4))
        // 首帧锚点 + 变化最大的两处，必须落在滚动段落里，而不是平铺到整条曲线。
        assertTrue("应选中 3 号变化点，实际：" + picked, picked.contains(3))
        assertTrue("应选中 21 号变化点，实际：" + picked, picked.contains(21))
    }
}
