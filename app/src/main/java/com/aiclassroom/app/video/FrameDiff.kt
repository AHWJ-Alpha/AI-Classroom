package com.aiclassroom.app.video

/**
 * 帧间变化强度：只比亮度，够用且便宜。
 *
 * 粗扫要处理上百个 96px 缩略图，逐像素比 RGB 三通道既慢又不必要——
 * 「画面变了多少」这件事亮度已经能说清楚（PPT 翻页、菜单弹出、鼠标移动都会引起亮度差）。
 * 这里刻意只依赖 IntArray，不碰 Bitmap，好在纯 JVM 单测里直接验证。
 */
object FrameDiff {

    /** ARGB_8888 像素 → 亮度（BT.601 整数权重，和肉眼感知接近）。 */
    fun lumaOf(argb: IntArray): IntArray {
        val luma = IntArray(argb.size)
        for (index in argb.indices) {
            val pixel = argb[index]
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            luma[index] = (red * 299 + green * 587 + blue * 114) / 1000
        }
        return luma
    }

    /**
     * 相邻两帧的差异比：逐像素亮度差绝对值的均值再除以 255，落在 0..1。
     * 0 = 完全没变，1 = 全黑对全白。尺寸不一致说明取帧出了问题，按「完全不同」处理。
     */
    fun changeRatio(previous: IntArray, current: IntArray): Float {
        if (previous.size != current.size) {
            return if (previous.isEmpty() && current.isEmpty()) 0f else 1f
        }
        if (previous.isEmpty()) return 0f
        var sum = 0L
        for (index in previous.indices) {
            val delta = current[index] - previous[index]
            sum += if (delta >= 0) delta else -delta
        }
        return (sum.toDouble() / previous.size / 255.0).toFloat()
    }
}
