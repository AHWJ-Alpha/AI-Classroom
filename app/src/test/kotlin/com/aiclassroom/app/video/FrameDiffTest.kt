package com.aiclassroom.app.video

import org.junit.Assert.assertEquals
import org.junit.Test

/** 差异计算是自适应选帧的地基：这里钉死单位、边界和「尺寸不一致」的兜底行为。 */
class FrameDiffTest {

    private fun pixelsOf(vararg values: Int): IntArray = values

    @Test
    fun lumaOfUsesBt601Weighting() {
        // 纯红：0.299*255 ≈ 76；纯绿：0.587*255 ≈ 149；纯蓝：0.114*255 ≈ 29
        assertEquals(76, FrameDiff.lumaOf(pixelsOf(0xFFFF0000.toInt()))[0])
        assertEquals(149, FrameDiff.lumaOf(pixelsOf(0xFF00FF00.toInt()))[0])
        assertEquals(29, FrameDiff.lumaOf(pixelsOf(0xFF0000FF.toInt()))[0])
        assertEquals(0, FrameDiff.lumaOf(pixelsOf(0xFF000000.toInt()))[0])
        assertEquals(255, FrameDiff.lumaOf(pixelsOf(0xFFFFFFFF.toInt()))[0])
    }

    @Test
    fun identicalFramesHaveNoChange() {
        val frame = FrameDiff.lumaOf(pixelsOf(0xFF102030.toInt(), 0xFF445566.toInt(), 0xFF778899.toInt()))
        assertEquals(0f, FrameDiff.changeRatio(frame, frame.copyOf()), 0.0001f)
    }

    @Test
    fun blackAgainstWhiteIsMaximumChange() {
        val black = IntArray(16) { 0 }
        val white = IntArray(16) { 255 }
        assertEquals(1f, FrameDiff.changeRatio(black, white), 0.0001f)
    }

    @Test
    fun changeIsAveragedOverAllPixels() {
        // 4 个像素里有 1 个差 255，其余不动 -> 255/4/255 = 0.25
        val before = intArrayOf(0, 0, 0, 0)
        val after = intArrayOf(0, 0, 0, 255)
        assertEquals(0.25f, FrameDiff.changeRatio(before, after), 0.0001f)
    }

    @Test
    fun sizeMismatchIsTreatedAsTotalChange() {
        // 取帧尺寸变化说明解码那头出了问题，宁可当成「整帧变了」也不当成「没变」。
        assertEquals(1f, FrameDiff.changeRatio(intArrayOf(1, 2, 3), intArrayOf(1, 2)), 0.0001f)
        assertEquals(0f, FrameDiff.changeRatio(IntArray(0), IntArray(0)), 0.0001f)
    }
}
