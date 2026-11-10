package com.aiclassroom.app.exam

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * 手写答案板：白底 + 深色笔迹，画完由「用这张」导出成 PNG 存进 filesDir/images/。
 *
 * 只存笔迹折线（List<Offset>）而不是 Compose 的 Path：导出时要按同一批点重画成
 * android.graphics.Path，两边共用一份点集才不会出现「屏幕上看是这样、存下来是那样」。
 * 白底是硬要求：深底浅字的图直接喂给模型，很容易被读反。
 */
private val PAD_BACKGROUND = Color(0xFFFFFFFF) // 纯白，与主题无关
private val PAD_INK = Color(0xFF111111)
private const val EXPORT_WIDTH = 1080

@Composable
fun HandwritingPadDialog(onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val strokeWidthPx = with(LocalDensity.current) { 4.dp.toPx() }
    var emptyHint by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF7F7F8)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    // 底部多留一截：全面屏手势条会压在最下面一排按钮上。
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 28.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("手写答案", color = Color(0xFF111111), fontSize = 18.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF555555)) }
                }
                Text(
                    if (emptyHint) "还没写东西，先在上面写点什么" else "在白板上写下答案，可以分多笔；导出后作为图片答案提交",
                    color = if (emptyHint) Color(0xFFC62828) else Color(0xFF666666),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PAD_BACKGROUND)
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> current = listOf(offset); emptyHint = false },
                                onDrag = { change, _ -> current = current + change.position },
                                onDragEnd = {
                                    if (current.isNotEmpty()) strokes.add(current)
                                    current = emptyList()
                                },
                                onDragCancel = {
                                    if (current.isNotEmpty()) strokes.add(current)
                                    current = emptyList()
                                }
                            )
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        // 先画已完成的笔，再画正在画的那一笔：单笔即时跟手。
                        (strokes + listOf(current)).forEach { points ->
                            if (points.isEmpty()) return@forEach
                            drawPath(
                                path = smoothPath(points),
                                color = PAD_INK,
                                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }, enabled = strokes.isNotEmpty()) {
                        Text("撤销")
                    }
                    OutlinedButton(onClick = { strokes.clear(); current = emptyList() }, enabled = strokes.isNotEmpty()) {
                        Text("清空")
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        enabled = !saving,
                        onClick = {
                            if (strokes.isEmpty() || canvasSize == IntSize.Zero) {
                                emptyHint = true
                                return@OutlinedButton
                            }
                            saving = true
                            val snapshot = strokes.toList()
                            val size = canvasSize
                            scope.launch {
                                val path = withContext(Dispatchers.IO) { exportHandwriting(context, snapshot, size, strokeWidthPx) }
                                saving = false
                                if (path != null) onSaved(path) else emptyHint = true
                            }
                        }
                    ) { Text("用这张") }
                }
            }
        }
    }
}

/** 折线过一次中点二次贝塞尔：手写时手指偏移很密，直连会有折角。 */
private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) {
        path.lineTo(points[0].x + 0.1f, points[0].y + 0.1f)
        return path
    }
    var prev = points[0]
    for (i in 1 until points.size) {
        val cur = points[i]
        val midX = (prev.x + cur.x) / 2f
        val midY = (prev.y + cur.y) / 2f
        path.quadraticTo(prev.x, prev.y, midX, midY)
        prev = cur
    }
    path.lineTo(prev.x, prev.y)
    return path
}

/**
 * 导出：白底 + 深色笔迹，宽度固定 [EXPORT_WIDTH]，高度按画布比例；
 * 导出分辨率只求模型看得清，不求好看，避免 PNG 过大拖慢归档。
 */
private fun exportHandwriting(
    context: android.content.Context,
    strokes: List<List<Offset>>,
    canvasSize: IntSize,
    strokeWidthPx: Float
): String? = runCatching {
    // 只导出笔迹的外接框（加一圈留白）：手写通常占画布一小块，
    // 整块导出会让缩略图中心裁切成一片空白，模型也要在大量留白里找字。
    val ink = strokes.flatten()
    if (ink.isEmpty()) return@runCatching null
    val pad = 48f + strokeWidthPx
    val left = (ink.minOf { it.x } - pad).coerceIn(0f, canvasSize.width.toFloat())
    val top = (ink.minOf { it.y } - pad).coerceIn(0f, canvasSize.height.toFloat())
    val right = (ink.maxOf { it.x } + pad).coerceIn(1f, canvasSize.width.toFloat())
    val bottom = (ink.maxOf { it.y } + pad).coerceIn(1f, canvasSize.height.toFloat())
    val cropWidth = (right - left).coerceAtLeast(1f)
    val cropHeight = (bottom - top).coerceAtLeast(1f)
    val scale = EXPORT_WIDTH.toFloat() / cropWidth
    val height = (cropHeight * scale).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(EXPORT_WIDTH, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    canvas.scale(scale, scale)
    canvas.translate(-left, -top)
    val paint = Paint().apply {
        color = android.graphics.Color.rgb(17, 17, 17)
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    strokes.forEach { points ->
        if (points.isEmpty()) return@forEach
        val path = android.graphics.Path()
        path.moveTo(points[0].x, points[0].y)
        if (points.size == 1) {
            canvas.drawCircle(points[0].x, points[0].y, strokeWidthPx / 2f, paint.apply { style = Paint.Style.FILL })
            paint.style = Paint.Style.STROKE
            return@forEach
        }
        var prev = points[0]
        for (i in 1 until points.size) {
            val cur = points[i]
            path.quadTo(prev.x, prev.y, (prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
            prev = cur
        }
        path.lineTo(prev.x, prev.y)
        canvas.drawPath(path, paint)
    }
    val directory = examImagesDir(context)
    val target = File(directory, "handwriting_" + System.currentTimeMillis() + ".png")
    FileOutputStream(target).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    bitmap.recycle()
    if (!target.isFile || target.length() <= 0L) null else target.absolutePath
}.getOrNull()
