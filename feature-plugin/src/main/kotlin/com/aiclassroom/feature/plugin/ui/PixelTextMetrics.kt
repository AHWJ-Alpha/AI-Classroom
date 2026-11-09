package com.aiclassroom.feature.plugin.ui

/**
 * pixeltext 的自适应字号计算（纯数学，无 Android 依赖，单测直接断言）。
 *
 * 为什么必须自适应：沉浸层的像素字要同时满足「竖屏 1080x2400 撑满宽度」和
 * 「横屏 2400x1080 不被裁掉」，任何写死的字号都只会在其中一种方向崩掉。
 *
 * 公式（[pixelSizeOf]）：
 * ```
 * pixelSize = min(可用宽 / (总宽字符数 * 5 + 字间距), 可用高 / 7 / 2.5)
 * ```
 * 取两者较小值 —— 宽度决定「放得下」，高度除以 2.5 决定「不顶天立地」，
 * 上下自然留白。两条约束任一被满足都不会溢出。
 */
object PixelTextMetrics {

    /** 字模列数（与 [PixelFont.COLS] 同步）。 */
    const val GLYPH_COLS: Int = PixelFont.COLS

    /** 字模行数（与 [PixelFont.ROWS] 同步）。 */
    const val GLYPH_ROWS: Int = PixelFont.ROWS

    /** 字间距，单位是「一个像素点」：放大时跟着一起放大，缩小同样成立。 */
    const val GLYPH_GAP: Int = 1

    /** 文字高度最多占可用高度的 1/2.5，剩下的留给上下留白。 */
    const val VERTICAL_FILL_RATIO: Float = 2.5f

    /** 一个像素点的最小尺寸，避免小屏 + 长文本时算出 0 而画出空白。 */
    const val MIN_PIXEL_SIZE: Float = 1f

    /** 文本按当前 pixelSize 占的总宽，单位是「像素点」。只有一个字时没有字间距。 */
    fun widthInPoints(text: String): Int {
        val count = text.length
        if (count <= 0) return 0
        return count * GLYPH_COLS + (count - 1) * GLYPH_GAP
    }

    /** 文本按当前 pixelSize 占的总高，单位是「像素点」。 */
    fun heightInPoints(): Int = GLYPH_ROWS

    /**
     * 算出能同时放进 [maxWidth] x [maxHeight] 的 pixelSize（单位与入参一致，渲染时传 dp）。
     * 空文本返回 [MIN_PIXEL_SIZE]：没字可画，调用方会直接跳过绘制。
     */
    fun pixelSizeOf(text: String, maxWidth: Float, maxHeight: Float): Float {
        val points = widthInPoints(text)
        if (points <= 0) return MIN_PIXEL_SIZE
        val byWidth = maxWidth / points
        val byHeight = maxHeight / GLYPH_ROWS / VERTICAL_FILL_RATIO
        val size = minOf(byWidth, byHeight)
        if (!size.isFinite() || size.isNaN()) return MIN_PIXEL_SIZE
        return size.coerceAtLeast(MIN_PIXEL_SIZE)
    }

    /** 文本在 [pixelSize] 下占的宽度（dp / px 与入参一致）。 */
    fun widthOf(text: String, pixelSize: Float): Float = widthInPoints(text) * pixelSize

    /** 文本在 [pixelSize] 下占的高度（dp / px 与入参一致）。 */
    fun heightOf(pixelSize: Float): Float = GLYPH_ROWS * pixelSize
}
