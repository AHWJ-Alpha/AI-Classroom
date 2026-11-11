package com.aiclassroom.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * 开屏动画：像素「3」消散 → 像素「4」重组。
 *
 * ## 为什么是这两个数字和这两种配色
 * 3.0 时代的图标是二次元粉紫（#3A254F 底 / #6C63FF 蓝紫 / #58D7E6 青色对勾），
 * 3.5 之后收敛成 Nothing 极简：唯一强调色 #FF3B30，禁止渐变和玻璃拟态。
 * 所以这个 CG 的叙事就是这条视觉迁移本身 —— 旧的紫蓝碎片飞散，新的红在此处重组。
 *
 * ## 时间轴（总计 [TOTAL_MS]）
 * ```
 * 0.00 - 0.75s  「3」逐列点亮（旧配色，带轻微色散）
 * 0.75 - 1.45s  「3」的像素四散飘走（每块独立延迟、位移、淡出）
 * 1.45 - 1.60s  短暂留白（让「消散」这件事被看见）
 * 1.60 - 2.35s  「4」的像素从屏幕边缘汇聚成型（新配色）
 * 2.35 - 2.75s  红色辉光扫过 + 版本号淡入
 * 2.75 - 3.10s  整体淡出，交给主界面
 * ```
 *
 * ## 像素感从哪来
 * 数字不是用字体画的，而是 5x7 点阵逐格铺方块 —— 每格之间留 1px 缝隙，
 * 放大后能清楚看到「格子」，这是像素风的关键，用字体缩放做不出来。
 */
private const val TOTAL_MS = 3_800

// ---- 调色：旧（3.0 二次元） ----
private val OldMain = Color(0xFF6C63FF)
private val OldGlow = Color(0xFF58D7E6)
private val OldDeep = Color(0xFF3A254F)

// ---- 调色：新（3.5+ Nothing 极简） ----
private val NewMain = Color(0xFFFF3B30)
private val NewGlow = Color(0xFFFF8A80)

private val Backdrop = Color(0xFF0A0A0C)
private val Ink = Color(0xFFE8E8EA)
private val MutedInk = Color(0xFF6A6A72)

/** 5x7 点阵字模：1 = 实心。 */
private val DIGIT_3 = listOf(
    "11111",
    "00001",
    "00001",
    "01110",
    "00001",
    "00001",
    "11111",
)

private val DIGIT_4 = listOf(
    "10001",
    "10001",
    "10001",
    "11111",
    "00001",
    "00001",
    "00001",
)

/** 一个像素格在某个时刻的状态。 */
private data class Pixel(
    /** 网格坐标（列, 行），0 起。 */
    val col: Int,
    val row: Int,
    /** 0..1 的随机数，用于错开每块的动画节奏。 */
    val jitter: Float,
    /** 飘散方向（弧度）。 */
    val angle: Float,
)

/** 把字模展开成像素列表，随机参数用固定种子生成，保证每次开屏一致。 */
private fun glyphPixels(glyph: List<String>, seed: Int): List<Pixel> {
    val rnd = Random(seed)
    val out = mutableListOf<Pixel>()
    glyph.forEachIndexed { row, line ->
        line.forEachIndexed { col, ch ->
            if (ch == '1') {
                out += Pixel(
                    col = col,
                    row = row,
                    jitter = rnd.nextFloat(),
                    angle = rnd.nextFloat() * 6.2832f,
                )
            }
        }
    }
    return out
}

/** 各阶段的归一化时间点（相对 [TOTAL_MS]）。 */
private const val T3_IN_END = 0.18f      // 「3」点亮完成
// 完整停留：早先没有这一段，像素才刚亮齐就开始散，实测截到的都是「已经散了一半」的画面，
// 谁也看不出那是个 3。加这一段之后形状能被认出来，消散才有对比。
private const val T3_HOLD_END = 0.34f    // 「3」完整停留结束，开始消散
private const val T3_OUT_END = 0.56f     // 「3」散尽
private const val GAP_END = 0.60f        // 留白结束
private const val T4_IN_END = 0.84f      // 「4」成型
private const val FLARE_END = 0.94f      // 辉光扫过
private const val FADE_START = 0.94f

/** 缓出：让「到位」这件事有刹车感。 */
private fun easeOut(x: Float): Float = 1f - (1f - x) * (1f - x) * (1f - x)

/** 缓入：消散起步慢、后段快。 */
private fun easeIn(x: Float): Float = x * x * x

@Composable
fun SplashCg(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clock = remember { Animatable(0f) }
    var skipped by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        clock.animateTo(1f, tween(TOTAL_MS, easing = LinearEasing))
        onFinished()
    }
    // 点一下就跳过：3 秒对熟客来说太长，但第一次看到值得看完。
    LaunchedEffect(skipped) {
        if (skipped) onFinished()
    }
    val measurer = rememberTextMeasurer()
    Box(
        modifier
            .fillMaxSize()
            .background(Backdrop)
            .pointerInput(Unit) {
                detectTapGestures { skipped = true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.value
            val cells = 5
            val rows = 7
            // 格子尺寸：数字约占屏宽的 46%
            val gap = size.minDimension * 0.006f
            val cell = (size.minDimension * 0.46f - gap * (cells - 1)) / cells
            val glyphW = cell * cells + gap * (cells - 1)
            val glyphH = cell * rows + gap * (rows - 1)
            val originX = (size.width - glyphW) / 2f
            val originY = (size.height - glyphH) / 2f - size.height * 0.03f

            drawOldThree(t, originX, originY, cell, gap)
            drawNewFour(t, originX, originY, cell, gap)
            drawCaption(t, measurer)
        }
    }
}

/** 「3」：点亮 → 飘散。 */
private fun DrawScope.drawOldThree(
    t: Float,
    originX: Float,
    originY: Float,
    cell: Float,
    gap: Float,
) {
    if (t > T3_OUT_END) return
    val pixels = glyphPixels(DIGIT_3, seed = 303)
    pixels.forEach { p ->
        val appearAt = (p.col / 5f) * T3_IN_END * 0.7f + p.jitter * 0.04f
        // 消散从 HOLD 结束才开始 —— 保证「全部亮齐 → 完整停留 → 才散」。
        val vanishAt = T3_HOLD_END + (T3_OUT_END - T3_HOLD_END) * (0.25f + p.jitter * 0.65f)
        if (t < appearAt || t > vanishAt) return@forEach

        val appear = easeOut(((t - appearAt) / 0.08f).coerceIn(0f, 1f))
        val gone = ((t - vanishAt) / 0.10f).coerceIn(0f, 1f)
        if (gone >= 1f) return@forEach

        // 飘散：沿随机方向飞出去 + 一边飞一边缩 + 淡出
        val fly = easeIn(gone)
        val dx = cosf(p.angle) * fly * cell * 6.5f
        val dy = sinf(p.angle) * fly * cell * 6.5f - fly * cell * 1.4f
        val scale = (1f - fly * 0.85f) * appear
        val alpha = (1f - fly) * appear
        val side = cell * scale
        if (side <= 0.4f) return@forEach

        val cx = originX + p.col * (cell + gap) + cell / 2f + dx
        val cy = originY + p.row * (cell + gap) + cell / 2f + dy
        // 主体
        drawRect(
            color = OldMain.copy(alpha = alpha),
            topLeft = Offset(cx - side / 2f, cy - side / 2f),
            size = Size(side, side),
        )
        // 少量像素带青色高光，呼应 3.0 图标里的对勾色
        if (p.jitter > 0.82f) {
            drawRect(
                color = OldGlow.copy(alpha = alpha * 0.9f),
                topLeft = Offset(cx - side / 2f, cy - side / 2f),
                size = Size(side, side),
            )
        }
    }
}

/** 「4」：从屏幕外汇聚成型 → 红色辉光扫过。 */
private fun DrawScope.drawNewFour(
    t: Float,
    originX: Float,
    originY: Float,
    cell: Float,
    gap: Float,
) {
    if (t < GAP_END) return
    val pixels = glyphPixels(DIGIT_4, seed = 404)
    val span = T4_IN_END - GAP_END
    pixels.forEach { p ->
        // 从外向内：越靠外的格子越晚到，形成「汇聚」而不是「齐刷刷出现」
        val distNorm = (abs(p.col - 2) / 2f) * 0.5f + p.jitter * 0.5f
        val arriveAt = GAP_END + span * (0.25f + distNorm * 0.75f)
        if (t < arriveAt) return@forEach

        val come = easeOut(((t - arriveAt) / (span * 0.55f)).coerceIn(0f, 1f))
        // 从屏幕边缘飞入
        val fromX = if (p.col < 2) -size.width * 0.5f else size.width * 0.5f
        val fromY = if (p.row < 3) -size.height * 0.4f else size.height * 0.4f
        val dx = fromX * (1f - come)
        val dy = fromY * (1f - come)
        val side = cell * (0.5f + 0.5f * come)

        val cx = originX + p.col * (cell + gap) + cell / 2f + dx
        val cy = originY + p.row * (cell + gap) + cell / 2f + dy

        val base = NewMain.copy(alpha = come)
        drawRect(
            color = base,
            topLeft = Offset(cx - side / 2f, cy - side / 2f),
            size = Size(side, side),
        )
        // 辉光扫过：一道亮带从左上往右下走
        if (t in T4_IN_END..FLARE_END) {
            val sweep = ((t - T4_IN_END) / (FLARE_END - T4_IN_END)).coerceIn(0f, 1f)
            val line = (p.col / 5f) * 0.55f + (p.row / 7f) * 0.45f
            val d = abs(line - sweep)
            if (d < 0.16f) {
                val k = (1f - d / 0.16f)
                drawRect(
                    color = NewGlow.copy(alpha = k * 0.85f),
                    topLeft = Offset(cx - side / 2f, cy - side / 2f),
                    size = Size(side, side),
                )
            }
        }
    }
}

/** 底部版本文字：3.0 的余晖 → 4.0 的宣告。 */
private fun DrawScope.drawCaption(t: Float, measurer: TextMeasurer) {
    val label = if (t < GAP_END) "3.0" else "4.0"
    val color = if (t < GAP_END) OldDeep.copy(alpha = 0.9f) else MutedInk
    val alpha = when {
        t < T3_IN_END -> (t / T3_IN_END) * 0.6f
        t < GAP_END -> 0.6f
        t < T4_IN_END -> ((t - GAP_END) / (T4_IN_END - GAP_END)) * 0.8f
        else -> 0.8f
    }
    val fade = if (t > FADE_START) 1f - ((t - FADE_START) / (1f - FADE_START)) else 1f
    val style = TextStyle(
        color = color.copy(alpha = color.alpha * alpha * fade),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 6.sp,
    )
    val laid = measurer.measure(label, style)
    drawText(
        laid,
        topLeft = Offset(
            (size.width - laid.size.width) / 2f,
            size.height * 0.76f,
        ),
    )
}

/** kotlin.math.cos 的 Float 版本（避免到处写 toFloat()）。 */
private fun cosf(x: Float): Float = kotlin.math.cos(x)

private fun sinf(x: Float): Float = kotlin.math.sin(x)