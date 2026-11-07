package com.aiclassroom.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aiclassroom.app.ui.theme.DesignTokens
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeDialog
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * 对话框 haze 状态作用域桥（阶段 4）。
 *
 * 宿主 hazeState 定义在 MainActivity 的 AIClassroomApp 内，AppDialogBase 与它不在同一
 * 作用域，因此在 AIClassroomApp 根部用 CompositionLocalProvider 提供、此处读取。
 * 若某处对话框未被提供（不会发生于本工程），回退到独立 HazeState：无 hazeSource，面板
 * 只显示自身底色，不崩溃。
 */
val LocalAppHazeState = staticCompositionLocalOf { HazeState() }

// 与 material3 AlertDialogImpl/AlertDialogContent 的布局常量逐项对齐（sources核对）。
private const val DialogMinWidthDp = 280
private const val DialogMaxWidthDp = 560
private val DialogPadding = 24.dp
private val TitlePaddingBottom = 16.dp
private val TextPaddingBottom = 24.dp
private val ButtonSpacing = 8.dp

/**
 * 7 处对话框的统一基座（阶段 1 建立；阶段 4 改为 haze 官方 HazeDialog + 毛玻璃面板）。
 *
 * - 窗口：[HazeDialog]（haze 1.5.4），内部是系统 Dialog + ForceInvalidationEffect，
 *   点外部关闭、返回键关闭、输入框焦点/键盘语义全部沿用系统 Dialog，未自行实现；
 * - 面板：圆角 DesignTokens.Radius.Panel（20dp），底色 colorScheme.surface@0.70，
 *   下垫 hazeEffect（blurRadius 18dp、不透明 surface 作 backgroundColor、
 *   surface@0.25 单层 tint、noiseFactor 0.10；blurEnabled()==false 时 fallbackTint
 *   分档 0.28/0.80）；
 * - 内容与顺序（title → text → dismissButton → confirmButton）、排版
 *   （headlineSmall / bodyMedium、onSurface）、间距与原 AlertDialog 逐项一致。
 */
@Composable
fun AppDialogBase(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null
) {
    val hazeState = LocalAppHazeState.current
    // 在 composable 上下文先取值：hazeEffect 的 lambda 不是 composable 作用域。
    val surface = MaterialTheme.colorScheme.surface
    val contentColor = MaterialTheme.colorScheme.onSurface

    HazeDialog(
        hazeState = hazeState,
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = modifier
                .sizeIn(minWidth = DialogMinWidthDp.dp, maxWidth = DialogMaxWidthDp.dp)
                .clip(RoundedCornerShape(DesignTokens.Radius.Panel))
                .hazeEffect(state = hazeState) {
                    blurRadius = 18.dp
                    backgroundColor = surface
                    tints = listOf(HazeTint(surface.copy(alpha = 0.25f)))
                    noiseFactor = 0.10f
                    fallbackTint = HazeTint(
                        surface.copy(alpha = if (HazeDefaults.blurEnabled()) 0.28f else 0.80f)
                    )
                }
                .background(surface.copy(alpha = 0.70f))
                .padding(DialogPadding)
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                title?.let {
                    ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                        Box(Modifier.padding(bottom = TitlePaddingBottom)) { it() }
                    }
                }
                text?.let {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                        Box(
                            Modifier
                                .weight(1f, fill = false)
                                .padding(bottom = TextPaddingBottom)
                        ) { it() }
                    }
                }
                Box(Modifier.align(Alignment.End)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(ButtonSpacing)) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}
