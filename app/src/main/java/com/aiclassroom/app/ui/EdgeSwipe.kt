package com.aiclassroom.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 边缘滑动。
 *
 * 三条自我约束（不这么做就会跟系统返回手势和正文横向拖动打架）：
 * 1. 只有从边缘 28dp 内按下才开始跟踪；
 * 2. 超过 touch slop 且横向明显大于纵向才接管，绝不一下按就消费；
 * 3. 要有足够位移（约 56dp）才真的触发，避免误触。
 *
 * [fromRight] 为 true 表示「从右边缘往左滑」，false 表示「从左边缘往右滑」。
 */
fun Modifier.edgeSwipe(
    enabled: Boolean,
    fromRight: Boolean,
    onTrigger: () -> Unit,
): Modifier = pointerInput(enabled, fromRight) {
    if (!enabled) return@pointerInput
    val edge = 28.dp.toPx()
    val slop = viewConfiguration.touchSlop
    val threshold = 56.dp.toPx()

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val startsAtEdge = if (fromRight) {
            down.position.x > size.width - edge
        } else {
            down.position.x < edge
        }
        if (!startsAtEdge) return@awaitEachGesture

        var dx = 0f
        var claimed = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            dx = change.position.x - down.position.x
            val dy = change.position.y - down.position.y
            val horizontalSign = if (fromRight) dx < 0 else dx > 0
            if (!claimed && horizontalSign && abs(dx) > slop && abs(dx) > abs(dy) * 1.5f) {
                claimed = true
            }
            if (claimed) change.consume()
        }

        val farEnough = if (fromRight) dx < -threshold else dx > threshold
        if (claimed && farEnough) onTrigger()
    }
}