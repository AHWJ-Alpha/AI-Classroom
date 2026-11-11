package com.aiclassroom.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiclassroom.app.R
import com.aiclassroom.app.model.AppPalette
import com.aiclassroom.app.model.Classroom

/**
 * AI Classroom Design Tokens（阶段 1：设计系统建立）
 *
 * 颜色取值与 design-preview/index.html 中已获用户确认的 Nothing 风格双主题 token 一一对应：
 * 深色 page #181B20 / surface #22262D / card #2B3038 / ink #E8EBEF / ink-2 #AAB2BD / line #48515D
 * 浅色 page #F5F6F8 / surface #FFFFFF / card #F0F2F5 / ink #20242A / ink-2 #697382 / line #D7DCE3
 *
 * 唯一强调色：Nothing 红 #FF3B30，仅用于主行动按钮、选中态、关键状态与少量视觉锚点；
 * 禁止满屏染红、禁止大面积渐变与玻璃拟态。
 *
 * 本文件只增不改：MainActivity.kt 中既有字面色值改为引用此处，取值保持完全一致，
 * 因此 AppPalette / paletteFor / appColorScheme 的字段语义未发生任何变化。
 */
object DesignTokens {

    /** 唯一强调色（深浅两套主题恒为同一值）。 */
    val Accent = Color(0xFFFF3B30)

    /** 强调色上的前景色。 */
    val AccentOn = Color(0xFFFFFFFF)

    /** 表单校验等关键错误状态色（与强调色区分的深红，取值不变）。 */
    val Danger = Color(0xFFB42318)

    // ---------------------------------------------------------------- 字体

    /**
     * 衬线字体：思源宋体 Noto Serif SC（SIL OFL 1.1 开源授权，可商用）。
     * 两个字重取自 Google Fonts 的 legacy TTF 端点（现代 UA 只给 woff2，安卓不认）：
     * res/font/noto_serif_sc_400.ttf 与 noto_serif_sc_700.ttf，各约 14.8MB。
     * 代码与公式仍走 FontFamily.Monospace（冻结区，不随此处变化）。
     */
    val Serif = FontFamily(
        Font(R.font.noto_serif_sc_400, FontWeight.Normal),
        Font(R.font.noto_serif_sc_700, FontWeight.Bold),
    )

    /**
     * 把整套 M3 排版的字族换成 Serif，字号/行高/字重保持 base 原值。
     * 必须设在 typography 层而不是包一层 LocalTextStyle：MainActivity 的 Markdown 渲染
     * 会用 MaterialTheme.typography.bodyLarge 再 copy 覆盖 LocalTextStyle，
     * 只有 typography 本身带字族，那处才不会把衬线丢掉。
     */
    fun serifTypography(base: androidx.compose.material3.Typography): androidx.compose.material3.Typography =
        androidx.compose.material3.Typography(
            displayLarge = base.displayLarge.copy(fontFamily = Serif),
            displayMedium = base.displayMedium.copy(fontFamily = Serif),
            displaySmall = base.displaySmall.copy(fontFamily = Serif),
            headlineLarge = base.headlineLarge.copy(fontFamily = Serif),
            headlineMedium = base.headlineMedium.copy(fontFamily = Serif),
            headlineSmall = base.headlineSmall.copy(fontFamily = Serif),
            titleLarge = base.titleLarge.copy(fontFamily = Serif),
            titleMedium = base.titleMedium.copy(fontFamily = Serif),
            titleSmall = base.titleSmall.copy(fontFamily = Serif),
            bodyLarge = base.bodyLarge.copy(fontFamily = Serif),
            bodyMedium = base.bodyMedium.copy(fontFamily = Serif),
            bodySmall = base.bodySmall.copy(fontFamily = Serif),
            labelLarge = base.labelLarge.copy(fontFamily = Serif),
            labelMedium = base.labelMedium.copy(fontFamily = Serif),
            labelSmall = base.labelSmall.copy(fontFamily = Serif),
        )

    // ---------------------------------------------------------------- 深色主题

    object Dark {
        val Page = Color(0xFF181B20)
        val Surface = Color(0xFF22262D)
        val Card = Color(0xFF2B3038)
        val Ink = Color(0xFFE8EBEF)
        val Ink2 = Color(0xFFAAB2BD)
        val Line = Color(0xFF48515D)
        val BubbleUser = Color(0xFF242931)
        val CodeBg = Color(0xFF1D2126)
        val ChipActiveInk = Color(0xFF181B20)
        val Dot = Color(0x0DE8EBEF)
    }

    // ---------------------------------------------------------------- 浅色主题

    object Light {
        val Page = Color(0xFFF5F6F8)
        val Surface = Color(0xFFFFFFFF)
        val Card = Color(0xFFF0F2F5)
        val Ink = Color(0xFF20242A)
        val Ink2 = Color(0xFF697382)
        val Line = Color(0xFFD7DCE3)
        val BubbleUser = Color(0xFFEDF0F4)
        val CodeBg = Color(0xFFEDEFF3)
        val ChipActiveInk = Color(0xFFFFFFFF)
        val Dot = Color(0x0F20242A)
    }

    // ------------------------------------------------ 既有全局裸色（L3719-3724）

    /**
     * 阶段 0 审计 L3719-3724 的全局裸色：只增不删，改为引用本文件，取值一字不变。
     * 命名与实际不符的历史事实（Blue/Green/Purple 实为灰系）原样保留，避免任何语义漂移。
     */
    object Legacy {
        val Page = Light.Page
        val Ink = Light.Ink
        /** 即历史 Muted（#697382，对应 MainActivity L3721 的 private val Muted）。 */
        val Muted = Light.Ink2
        val Blue = Color(0xFF596579)
        val Green = Color(0xFF65758A)
        val Purple = Color(0xFF667085)
        val ButtonDefault = Color(0xFF667085)
        val OnButtonOnLight = Color(0xFF062526)
    }

    // ------------------------------------------------------------------ 排版

    /** 字号层级（与阶段 0 实测的 46 处硬编码字号取值完全一致，收敛后零视觉差异）。 */
    object Type {
        val Micro: TextUnit = 10.sp
        val Caption: TextUnit = 11.sp
        val Small: TextUnit = 12.sp
        val Body: TextUnit = 13.sp
        val Subtitle: TextUnit = 17.sp
        val Title: TextUnit = 18.sp
        val Headline: TextUnit = 20.sp
        val Display: TextUnit = 22.sp

        val LineTight: TextUnit = 14.sp
        val LineSnug: TextUnit = 18.sp
        val LineRelaxed: TextUnit = 19.sp
        val LineBody: TextUnit = 21.sp
    }

    /** 字重层级。 */
    object Weight {
        val Regular: FontWeight = FontWeight.Normal
        val Medium: FontWeight = FontWeight.Medium
        val SemiBold: FontWeight = FontWeight.SemiBold
        val Bold: FontWeight = FontWeight.Bold
    }

    /** 技术编辑式排版的常用角色样式（模型名 / Token / 参数 / 文件名等可走等宽）。 */
    object TextStyles {
        val Kicker = TextStyle(
            fontSize = Type.Micro,
            fontWeight = Weight.Bold,
            letterSpacing = 1.4.sp,
            lineHeight = Type.LineSnug
        )
        val SectionTitle = TextStyle(
            fontSize = Type.Small,
            fontWeight = Weight.Bold,
            letterSpacing = 0.6.sp,
            lineHeight = Type.LineSnug
        )
        val Body = TextStyle(
            fontSize = Type.Body,
            fontWeight = Weight.Regular,
            lineHeight = Type.LineBody
        )
        val BodyStrong = TextStyle(
            fontSize = Type.Body,
            fontWeight = Weight.SemiBold,
            lineHeight = Type.LineBody
        )
        val Caption = TextStyle(
            fontSize = Type.Small,
            fontWeight = Weight.Regular,
            lineHeight = Type.LineSnug
        )
        val Technical = TextStyle(
            fontSize = Type.Small,
            fontWeight = Weight.Regular,
            fontFamily = FontFamily.Monospace,
            lineHeight = Type.LineBody
        )
    }

    // ------------------------------------------------------------- 间距网格

    /** 4dp 基准网格；页面水平边距统一走 Spacing.Page。 */
    object Spacing {
        val Xxs = 2.dp
        val Xs = 4.dp
        val Sm = 6.dp
        val Md = 8.dp
        val Lg = 12.dp
        val Xl = 16.dp
        val Xxl = 20.dp
        val Xxxl = 24.dp
        val Huge = 32.dp
        val Page = 16.dp
        val CardHorizontal = 14.dp
        val CardVertical = 13.dp
    }

    // ------------------------------------------------------------------ 圆角

    /** 圆角体系；既有 5 档 AppShapes 取值不变，其余档位用于收敛裸 RoundedCornerShape。 */
    object Radius {
        val None = 0.dp
        val Xs = 4.dp
        val Sm = 6.dp
        val Tile = 8.dp
        val Chip = 9.dp
        val Note = 10.dp
        val Button = 12.dp
        val Control = 14.dp
        val Card = 16.dp
        val Panel = 20.dp
        val Menu = 28.dp
        val Pill = 999.dp
    }

    // ------------------------------------------------------------------ 边框

    /** 细线分隔优先：卡片与分组一律 1dp hairline，不使用厚重阴影。 */
    object Border {
        val Hairline = 1.dp
        val Focus = 1.5.dp
        val Selected = 2.dp
    }

    // ------------------------------------------------------------------ 尺寸

    /** 列表行、按钮、输入框、图标按钮的统一高度与图标尺寸。 */
    object Size {
        val IconSm = 16.dp
        val Icon = 20.dp
        val IconLg = 24.dp
        val IconButton = 36.dp
        val ButtonHeight = 40.dp
        val ButtonHeightLarge = 44.dp
        val FieldMinHeight = 44.dp
        val ListRowMin = 56.dp
        val Avatar = 32.dp
        val Tile = 38.dp
        val Fab = 46.dp
    }

    // ------------------------------------------------------------------ 阴影

    /** 浮层阴影：只给"浮在内容之上"的面板用（底栏）。取克制档，压出层次即可，不喧宾夺主。 */
    object Shadow {
        val Bar = 8.dp
    }

    // ------------------------------------------------------------------ 动效

    /** 克制动效时长（毫秒）；可中断、轻量，不得影响流式对话响应。 */
    object Motion {
        const val FastMs = 120
        const val BaseMs = 180
        const val SlowMs = 260

        /** 阶段 0 现有课堂菜单滑入/滑出时长，取值不变。 */
        const val DrawerEnterMs = 220
        const val DrawerExitMs = 180
    }
}
