package com.aiclassroom.app.ui

import android.graphics.Rect
import android.os.Build
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * 把某一条边的一小段从系统返回手势里要回来。
 *
 * 不加这个的话，从边缘滑动会被系统当成「返回」，直接退出应用 —— 实测就是这样。
 *
 * 只排除必要的一小段：宽 24dp、高 200dp 居中（Android 对每侧排除区高度有上限），
 * 其余边缘仍然留给系统返回手势，不在全屏禁用系统手势。
 *
 * [left] 为 true 排除左边缘（课堂里往右滑退出），false 排除右边缘（主对话往左滑进入）。
 */
@Composable
fun EdgeGestureExclusion(enabled: Boolean, left: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val view = LocalView.current

    DisposableEffect(view, enabled, left) {
        if (!enabled) return@DisposableEffect onDispose { }

        val density = view.resources.displayMetrics.density
        val stripWidth = (24 * density).toInt()
        val stripHeight = (200 * density).toInt()

        val listener = ViewTreeObserver.OnPreDrawListener {
            val w = view.width
            val h = view.height
            if (w > stripWidth && h > stripHeight) {
                val top = (h - stripHeight) / 2
                val strip = if (left) {
                    Rect(0, top, stripWidth, top + stripHeight)
                } else {
                    Rect(w - stripWidth, top, w, top + stripHeight)
                }
                // 两侧各自维护，别把另一侧刚登记的排除区冲掉。
                val others = view.systemGestureExclusionRects.filter { r ->
                    if (left) r.left > 0 else r.right < w
                }
                view.systemGestureExclusionRects = others + strip
            }
            true
        }
        view.viewTreeObserver.addOnPreDrawListener(listener)

        onDispose {
            view.viewTreeObserver.removeOnPreDrawListener(listener)
            val w = view.width
            view.systemGestureExclusionRects = view.systemGestureExclusionRects.filter { r ->
                if (left) r.left > 0 else r.right < w
            }
        }
    }
}