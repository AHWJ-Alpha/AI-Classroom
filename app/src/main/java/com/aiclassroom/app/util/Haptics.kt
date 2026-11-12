package com.aiclassroom.app.util

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * 触感反馈。
 *
 * ## 为什么不用 createOneShot
 * 线性马达（LRA）是谐振器件：给一个 60ms 的连续脉冲，它只会嗡嗡响，
 * 听起来像廉价转子马达 —— 这正是「震感低级」的来源。
 * LRA 要的是又短又陡的瞬态：十几毫秒、满幅、快速收敛。
 *
 * ## 正确做法
 * 用 `VibrationEffect.Composition` 的预置基元（PRIMITIVE_CLICK / PRIMITIVE_THUD）：
 * 波形由厂商按各自马达调校过，应用只给一个 0..1 的 scale 控制强弱。
 * 既拿得到高级感，强度又仍然可调。
 *
 * 并给 `VibrationAttributes.USAGE_TOUCH`：系统才会把它当触感反馈处理，
 * 跟随用户的系统级触感强度偏好。
 */
object Haptics {

    const val OFF = 0
    const val LIGHT = 1
    const val MEDIUM = 2
    const val STRONG = 3
    const val MAX = 4

    val LABELS = listOf("关闭", "轻", "中", "强", "极大")

    enum class Kind { TAP, TICK, SETTLE }

    private const val PREFS = "haptic_prefs"
    private const val KEY_LEVEL = "level"

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

    /** 档位 → 基元强度。scale 是 0..1，不是振幅。 */
    private fun scaleFor(level: Int): Float = when (level) {
        LIGHT -> 0.35f
        MEDIUM -> 0.60f
        STRONG -> 0.82f
        else -> 1.0f
    }

    fun tap(context: Context, kind: Kind = Kind.TAP) {
        val lv = level
        if (lv == OFF) return
        runCatching {
            val vibrator = vibratorOf(context) ?: return
            if (!vibrator.hasVibrator()) return
            val effect = effectFor(kind, lv) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect)
            }
        }.onFailure { Log.w("Haptics", it.message ?: "vibrate failed") }
    }

    private fun vibratorOf(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /**
     * - TAP → PRIMITIVE_CLICK：干脆的一下
     * - TICK → 同基元压到一半强度：滑动刻度，不能盖过点按
     * - SETTLE → PRIMITIVE_THUD：沉一点的收尾
     */
    private fun effectFor(kind: Kind, level: Int): VibrationEffect? {
        val scale = scaleFor(level)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val composition = VibrationEffect.startComposition()
            when (kind) {
                Kind.TICK -> composition.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_CLICK, (scale * 0.5f).coerceIn(0.05f, 1f)
                )
                Kind.SETTLE -> composition.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_THUD, (scale * 0.8f).coerceIn(0.05f, 1f)
                )
                else -> composition.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_CLICK, scale
                )
            }
            return composition.compose()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val id = when (kind) {
                Kind.TICK -> VibrationEffect.EFFECT_TICK
                Kind.SETTLE -> VibrationEffect.EFFECT_HEAVY_CLICK
                else -> VibrationEffect.EFFECT_CLICK
            }
            return VibrationEffect.createPredefined(id)
        }
        return null
    }
}
