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
import androidx.compose.ui.graphics.graphicsLayer

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
private const val TOTAL_MS = 5_000

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

// **必须预计算**：早先是在 DrawScope 里每帧调 glyphPixels()，
// 相当于每秒构造上百次 Random、分配上千个 Pixel 对象 —— 实机上表现为明显掉帧。
// Kotlin 顶层 val 是懒初始化的，只算一次，之后每帧只是读这个 List。
private val PIXELS_3: List<Pixel> = glyphPixels(DIGIT_3, seed = 303)
private val PIXELS_4: List<Pixel> = glyphPixels(DIGIT_4, seed = 404)

/** 各阶段的归一化时间点（相对 [TOTAL_MS]）。 */
// ---- 第一幕：「3」 ----
// 总长从 6.4s 压到 5.0s 时**不能等比缩放**：等比会把原本就紧的段落压没。
// 这里按「各段的最小可辨识时长」重排 —— 停留 0.40s、消散 0.70s、NEW 0.60s。
private const val T3_IN_END = 0.12f      // 点亮完成           (0.60s)
// 完整停留：早先没有这一段，像素才刚亮齐就开始散，实测截到的都是「已经散了一半」的画面，
// 谁也看不出那是个 3。加这一段之后形状能被认出来，消散才有对比。
private const val T3_HOLD_END = 0.20f    // 完整停留结束       (1.00s)
private const val T3_OUT_END = 0.34f     // 散尽               (1.70s)

// ---- 第二幕：「4」 ----
private const val GAP_END = 0.36f        // 留白结束           (1.80s)
private const val T4_IN_END = 0.54f      // 「4」成型          (2.70s)
private const val FLARE_END = 0.58f      // 辉光扫过           (2.90s)
private const val T4_OUT_END = 0.62f     // 「4」隐去          (3.10s)

// ---- 第三幕：方框 + 沉浸图标 ----
private const val FRAME_IN_START = 0.62f // 方框开始描边       (3.10s)
private const val FRAME_IN_END = 0.68f   // 方框画完           (3.40s)
private const val ICON_IN_END = 0.75f    // 地球图标浮现完成   (3.75s)

// ---- 第四幕：收缩成 NEW ----
// 这一段原来只占 0.04（约 0.2 秒），NEW 等于闪一下就没了 —— 用户根本来不及看。
// 现在给「收缩 + 三个字母落位 + 稳定停留」共 0.12（0.60s），最后才淡出。
private const val SHRINK_END = 0.80f     // 方框收缩到最小     (4.00s)
private const val NEW_IN_END = 0.92f     // 「NEW」逐个落位    (4.60s)
private const val FADE_START = 0.97f     // 文字淡出           (4.85s)

// 整个画布的淡出：末段把深黑背景渐隐，露出下面的主界面。
// 没有它的话，CG 一结束就是「黑 → 白」的硬切，浅色主题下尤其刺眼。
// 有了它，无论主界面是浅色还是深色，都是平滑交叉过去的。
private const val CANVAS_FADE_START = 0.92f

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
    // 末段整体渐隐：graphicsLayer 的 alpha 会连同深黑背景一起淡掉，
    // 于是底下的 AIClassroomApp() 自然露出来 —— 这就是「结束过渡」。
    val canvasAlpha = run {
        val t = clock.value
        if (t <= CANVAS_FADE_START) 1f
        else (1f - (t - CANVAS_FADE_START) / (1f - CANVAS_FADE_START)).coerceIn(0f, 1f)
    }
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = canvasAlpha }
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
            drawFrameWithIcon(t, originX, originY, cell, gap)
            drawWordNew(t, originX, originY, cell, gap)
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
    val pixels = PIXELS_3
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
    if (t < GAP_END || t > T4_OUT_END) return
    // 第三幕要在这个位置放方框和地球，所以「4」得先让开。
    // 淡出窗口取整段的 40%，5s 下约 0.2 秒 —— 不留突兀的硬切。
    val fadeWin = 0.05f
    val fadeOut = ((t - (T4_OUT_END - fadeWin)) / fadeWin).coerceIn(0f, 1f)
    val pixels = PIXELS_4
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

        val base = NewMain.copy(alpha = come * (1f - fadeOut))
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
    // 版本号跟着幕走：3.0 → 4.0 → NEW（第三幕后交给「NEW」自己说事）
    if (t >= FRAME_IN_START) return
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


/**
 * 7x7 地球点阵（沉浸模式图标）。
 *
 * 第一版写得太稀疏（只有外圈 + 两三条零散的内点），缩到小尺寸后看着像一道斜线，
 * 完全不像球。改成「实心球 + 挖空一条赤道带」：轮廓是实心的，中间留白两格当纬线，
 * 这样即便只有 7x7 也能读出「球体」。
 */
// 实心圆，只在中间一行挖掉两个点当赤道 —— 挖一整行会变成上下两个汉堡片。
private val GLOBE_7 = listOf(
    "..111..",
    ".11111.",
    "1111111",
    "1101011",
    "1111111",
    ".11111.",
    "..111..",
)

/** NEW 三个字母的 5x7 点阵。 */
private val LETTER_N = listOf("10001","11001","10101","10011","10001","10001","10001")
private val LETTER_E = listOf("11111","10000","10000","11110","10000","10000","11111")
private val LETTER_W = listOf("10001","10001","10001","10101","10101","10101","01010")

/**
 * 第三幕：方框描边浮现 → 沉浸模式的地球图标在框内亮起 → 方框收缩。
 *
 * 方框用像素方块逐边画出（四边同时推进），不是画一个圆角矩形 ——
 * 那会在整体像素风里显得突兀。
 */
private fun DrawScope.drawFrameWithIcon(
    t: Float,
    originX: Float,
    originY: Float,
    cell: Float,
    gap: Float,
) {
    if (t < FRAME_IN_START || t > SHRINK_END) return

    val frameP = ((t - FRAME_IN_START) / (FRAME_IN_END - FRAME_IN_START)).coerceIn(0f, 1f)
    val iconP = ((t - FRAME_IN_END) / (ICON_IN_END - FRAME_IN_END)).coerceIn(0f, 1f)
    val shrinkP = ((t - ICON_IN_END) / (SHRINK_END - ICON_IN_END)).coerceIn(0f, 1f)

    // 方框整体从 100% 收到 58%，给下一幕的 NEW 让出位置
    val boxScale = 1f - easeIn(shrinkP) * 0.42f
    val side = cell * boxScale
    val step = side + gap
    val cols = 9
    val rows = 9
    val boxW = side * cols + gap * (cols - 1)
    val boxH = side * rows + gap * (rows - 1)
    val bx = (size.width - boxW) / 2f
    val by = (originY + cell * 3.5f) - boxH / 2f

    val perim = (cols * 2 + rows * 2 - 4).toFloat()
    var drawn = 0f
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val onEdge = r == 0 || r == rows - 1 || c == 0 || c == cols - 1
            if (!onEdge) continue
            // 沿周长推进：按「离起点的距离」决定这一格何时亮
            val order = perimeterOrder(c, r, cols, rows) / perim
            if (order > frameP) continue
            val cx = bx + c * step + side / 2f
            val cy = by + r * step + side / 2f
            drawRect(
                color = NewMain.copy(alpha = 0.85f),
                topLeft = Offset(cx - side / 2f, cy - side / 2f),
                size = Size(side, side),
            )
        }
    }

    // 地球图标：逐点浮现
    if (iconP > 0f) {
        val gx = (size.width - (side * 7 + gap * 6)) / 2f
        val gy = (originY + cell * 3.5f) - (side * 7 + gap * 6) / 2f
        GLOBE_7.forEachIndexed { r, line ->
            line.forEachIndexed { c, ch ->
                if (ch != '1') return@forEachIndexed
                // 从中心向外浮现
                val d = (abs(c - 3) + abs(r - 3)) / 6f
                if (d > iconP) return@forEachIndexed
                val cx = gx + c * (side + gap) + side / 2f
                val cy = gy + r * (side + gap) + side / 2f
                drawRect(
                    color = NewGlow.copy(alpha = 0.95f),
                    topLeft = Offset(cx - side / 2f, cy - side / 2f),
                    size = Size(side, side),
                )
            }
        }
    }
}

/** 周长上的推进顺序：0 = 左上角起，顺时针。 */
private fun perimeterOrder(c: Int, r: Int, cols: Int, rows: Int): Float = when {
    r == 0 -> c.toFloat()
    c == cols - 1 -> (cols - 1 + r).toFloat()
    r == rows - 1 -> (cols - 1 + rows - 1 + (cols - 1 - c)).toFloat()
    else -> (cols - 1 + rows - 1 + cols - 1 + (rows - 1 - r)).toFloat()
}

/**
 * 第四幕：收缩成「NEW」。
 *
 * 三个字母从左往右依次落位，最后一个落定时正好到 [NEW_IN_END]，
 * 紧接 [FADE_START] 整体淡出，把画面交给 NEW 介绍界面。
 */
private fun DrawScope.drawWordNew(
    t: Float,
    originX: Float,
    originY: Float,
    cell: Float,
    gap: Float,
) {
    if (t < SHRINK_END) return
    val letters = listOf(LETTER_N, LETTER_E, LETTER_W)
    // NEW 必须用**比数字小得多的格子**：数字是 5 格宽、屏幕上只放一个，
    // 而 NEW 要横排三个字母 —— 按数字的 cell（约屏宽 9%）算，
    // 15 格就有 1500px，远超 1080 屏宽，三个字母直接叠成一团。
    // 0.30 让总宽落在屏宽 62% 左右。
    val unit = cell * 0.30f
    val letterW = unit * 5 + gap * 4
    val spacing = unit * 1.6f
    val totalW = letterW * 3 + spacing * 2
    val startX = (size.width - totalW) / 2f
    val startY = (originY + cell * 3.5f) - (unit * 7 + gap * 6) / 2f

    val span = NEW_IN_END - SHRINK_END
    letters.forEachIndexed { li, glyph ->
        // 每个字母的落位窗口错开，形成「逐个落定」
        val letterStart = SHRINK_END + span * (li * 0.28f)
        if (t < letterStart) return@forEachIndexed
        val p = easeOut(((t - letterStart) / (span * 0.44f)).coerceIn(0f, 1f))

        val lx = startX + li * (letterW + spacing)
        glyph.forEachIndexed { r, line ->
            line.forEachIndexed { c, ch ->
                if (ch != '1') return@forEachIndexed
                // 从上往下落
                val drop = (1f - p) * unit * 2.2f * (r / 7f + 0.4f)
                val cx = lx + c * (unit + gap) + unit / 2f
                val cy = startY + r * (unit + gap) + unit / 2f - drop
                drawRect(
                    color = NewMain.copy(alpha = p),
                    topLeft = Offset(cx - unit / 2f, cy - unit / 2f),
                    size = Size(unit, unit),
                )
            }
        }
    }
}

/** kotlin.math.cos 的 Float 版本（避免到处写 toFloat()）。 */
private fun cosf(x: Float): Float = kotlin.math.cos(x)

private fun sinf(x: Float): Float = kotlin.math.sin(x)