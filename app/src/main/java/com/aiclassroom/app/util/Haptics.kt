package com.aiclassroom.app.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * 触感反馈。
 *
 * 为什么不用 View.performHapticFeedback：它只能挑预置常量（VIRTUAL_KEY / LONG_PRESS ...），
 * 具体强度由系统决定，还会被系统「触感强度」整体缩放 —— vivo 实测把 CLICK(MEDIUM) 压到 0.71，
 * 用户再怎么点都是「几乎没感觉」，而且应用这边没法把它调大。
 *
 * 这里直接走 Vibrator + VibrationEffect，振幅和时长都由 [level] 决定，
 * 设置里的「极大」就是真的 255 满振幅。
 */
object Haptics {

    const val OFF = 0
    const val LIGHT = 1
    const val MEDIUM = 2
    const val STRONG = 3
    const val MAX = 4

    /** 设置页展示用：值 → 名字。 */
    val LABELS = listOf("关闭", "轻", "中", "强", "极大")

    private const val PREFS = "haptic_prefs"
    private const val KEY_LEVEL = "level"

    /** 当前强度。启动时从 prefs 读一次；设置页改动后立即生效。 */
    @Volatile
    var level: Int = MEDIUM
        private set

    fun load(context: Context) {
        level = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_LEVEL, MEDIUM)
            .coerceIn(OFF, MAX)
    }

    fun save(context: Context, value: Int) {
        val v = value.coerceIn(OFF, MAX)
        level = v
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_LEVEL, v).apply()
    }

    /**
     * 触发一次点击反馈。
     * [heavy] 用于长按/吸附这类需要更明确手感的事件，时长翻倍。
     *
     * 时长比振幅更重要：实测 26ms 在手机上几乎摸不出来，60ms 才明显。
     * 振幅再高，时间太短也只是「嗒」一下的空气感。
     */
    fun tap(context: Context, heavy: Boolean = false) {
        val lv = level
        if (lv == OFF) return
        val ms = when (lv) {
            LIGHT -> 10L
            MEDIUM -> 18L
            STRONG -> 30L
            else -> 60L
        }
        val amplitude = when (lv) {
            LIGHT -> 90
            MEDIUM -> 150
            STRONG -> 210
            else -> 255
        }
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (!vibrator.hasVibrator()) { return }
            vibrator.vibrate(
                VibrationEffect.createOneShot(if (heavy) ms * 2 else ms, amplitude)
            )
        }.onFailure { Log.w("Haptics", it.message ?: "vibrate failed") }
    }
}
