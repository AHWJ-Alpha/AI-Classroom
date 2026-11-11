package com.aiclassroom.app.util

/**
 * 调色板与主题模式。
 *
 * 从 MainActivity.kt 搬出来的一整组纯函数：给定 ClassroomConfig 和系统深浅色，
 * 算出 AppPalette / Material3 ColorScheme。没有任何 UI 或状态依赖，所以可以独立测试。
 *
 * 其中 [luminanceValue] / [compositeOnWhite] / [compositeOn] 是 Color 的扩展：
 * 「半透明色叠在某种底色上之后到底长什么样」—— 判断该用深字还是浅字要用它。
 */

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.aiclassroom.app.model.AppPalette
import com.aiclassroom.app.model.ClassroomConfig
import com.aiclassroom.app.ui.theme.DesignTokens

internal fun isDarkInterface(config: ClassroomConfig, systemDark: Boolean): Boolean = when (normalizeInterfaceMode(config.interfaceMode)) {
    "dark" -> true
    "light" -> false
    else -> systemDark
}

internal fun appColorScheme(palette: AppPalette, dark: Boolean) = if (dark) {
    androidx.compose.material3.darkColorScheme(
        primary = palette.button,
        onPrimary = palette.onButton,
        primaryContainer = palette.button.copy(alpha = 0.18f).compositeOn(palette.surface),
        onPrimaryContainer = palette.ink,
        secondary = palette.secondary,
        onSecondary = palette.ink,
        secondaryContainer = palette.secondary.copy(alpha = 0.18f).compositeOn(palette.surface),
        onSecondaryContainer = palette.ink,
        tertiary = palette.accent,
        onTertiary = palette.ink,
        background = palette.page,
        onBackground = palette.ink,
        surface = palette.surface,
        onSurface = palette.ink,
        surfaceVariant = palette.card,
        onSurfaceVariant = palette.muted,
        surfaceTint = Color.Transparent,
        surfaceBright = palette.card,
        surfaceDim = palette.page,
        surfaceContainer = palette.card,
        surfaceContainerHigh = palette.card,
        surfaceContainerHighest = palette.card,
        surfaceContainerLow = palette.surface,
        surfaceContainerLowest = palette.page,
        inverseSurface = palette.ink,
        inverseOnSurface = palette.page,
        outline = palette.outline,
        outlineVariant = palette.outline,
        scrim = Color.Black
    )
} else {
    androidx.compose.material3.lightColorScheme(
        primary = palette.button,
        onPrimary = palette.onButton,
        primaryContainer = palette.button.copy(alpha = 0.14f).compositeOn(palette.surface),
        onPrimaryContainer = palette.ink,
        secondary = palette.secondary,
        onSecondary = palette.ink,
        secondaryContainer = palette.secondary.copy(alpha = 0.12f).compositeOn(palette.surface),
        onSecondaryContainer = palette.ink,
        tertiary = palette.accent,
        onTertiary = palette.ink,
        background = palette.page,
        onBackground = palette.ink,
        surface = palette.surface,
        onSurface = palette.ink,
        surfaceVariant = palette.card,
        onSurfaceVariant = palette.muted,
        surfaceTint = Color.Transparent,
        surfaceBright = palette.surface,
        surfaceDim = palette.card,
        surfaceContainer = palette.card,
        surfaceContainerHigh = palette.card,
        surfaceContainerHighest = palette.card,
        surfaceContainerLow = palette.surface,
        surfaceContainerLowest = palette.page,
        inverseSurface = palette.ink,
        inverseOnSurface = palette.page,
        outline = palette.outline,
        outlineVariant = palette.outline
    )
}

internal fun paletteFor(config: ClassroomConfig, systemDark: Boolean): AppPalette {
    val dark = isDarkInterface(config, systemDark)
    val custom = normalizeThemeMode(config.themeMode) == "custom"
    val button = when (normalizeThemeMode(config.themeMode)) {
        "custom" -> colorFromLong(config.primaryColor)
        else -> DesignTokens.Legacy.ButtonDefault
    }
    val onButton = if (button.luminanceValue() > 0.58f) DesignTokens.Legacy.OnButtonOnLight else Color.White
    return if (dark) {
        AppPalette(
            page = DesignTokens.Dark.Page,
            surface = DesignTokens.Dark.Surface,
            card = DesignTokens.Dark.Card,
            ink = DesignTokens.Dark.Ink,
            muted = DesignTokens.Dark.Ink2,
            button = button,
            onButton = onButton,
            outline = DesignTokens.Dark.Line,
            primary = button,
            secondary = if (custom) colorFromLong(config.secondaryColor) else button,
            accent = button
        )
    } else {
        AppPalette(
            page = DesignTokens.Light.Page,
            surface = DesignTokens.Light.Surface,
            card = DesignTokens.Light.Card,
            ink = DesignTokens.Light.Ink,
            muted = DesignTokens.Light.Ink2,
            button = button,
            onButton = onButton,
            outline = DesignTokens.Light.Line,
            primary = button,
            secondary = if (custom) colorFromLong(config.secondaryColor) else button,
            accent = button
        )
    }
}

internal fun blendOnWhite(primary: Color, secondary: Color, alpha: Float): Color = Color(
    red = ((primary.red + secondary.red) / 2f) * alpha + (1f - alpha),
    green = ((primary.green + secondary.green) / 2f) * alpha + (1f - alpha),
    blue = ((primary.blue + secondary.blue) / 2f) * alpha + (1f - alpha),
    alpha = 1f
)

internal fun colorFromLong(value: Long): Color = Color(value.toInt())
internal fun argbToHex(value: Long): String = "#" + (value and 0xFFFFFFFFL).toString(16).padStart(8, '0').uppercase()
internal fun parseHexColor(raw: String): Long? {
    val clean = raw.trim().removePrefix("#")
    val argb = when (clean.length) {
        6 -> "FF$clean"
        8 -> clean
        else -> return null
    }
    return argb.toLongOrNull(16)?.takeIf { it in 0..0xFFFFFFFFL }
}
// 以前这里会把未知值归到 "ocean"（二次元粉紫主题）。
// 现在只保留一种主题模型：用户自己选颜色（custom），默认就是橙色。
internal fun normalizeThemeMode(raw: String): String = when (raw) {
    "custom", "single", "自定义", "单主色", "鍗曚富鑹?" -> "custom"
    else -> "custom"
}

internal fun normalizeInterfaceMode(raw: String): String = when (raw) {
    "dark", "black", "黑色", "黑" -> "dark"
    "light", "white", "白色", "白" -> "light"
    else -> "system"
}

internal fun legacyInterfaceMode(themeMode: String): String = when (themeMode) {
    "mono" -> "light"
    "system" -> "system"
    else -> "system"
}

internal fun Color.luminanceValue(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
internal fun Color.compositeOnWhite(): Color = Color(
    red = red * alpha + (1f - alpha),
    green = green * alpha + (1f - alpha),
    blue = blue * alpha + (1f - alpha),
    alpha = 1f
)
internal fun Color.compositeOn(base: Color): Color = Color(
    red = red * alpha + base.red * (1f - alpha),
    green = green * alpha + base.green * (1f - alpha),
    blue = blue * alpha + base.blue * (1f - alpha),
    alpha = 1f
)
