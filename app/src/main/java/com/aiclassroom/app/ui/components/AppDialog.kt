package com.aiclassroom.app.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aiclassroom.app.ui.theme.DesignTokens

/**
 * 7 处 AlertDialog 的统一基座（阶段 1）。
 *
 * 统一两项配色路径（palette 版 / colorScheme 版）为一套：容器 = colorScheme.surface
 * （appColorScheme 将其映射为 palette.surface，取值与原 palette 版完全一致）、
 * 标题/正文 = colorScheme.onSurface（= palette.ink）、圆角 = DesignTokens.Radius.Panel
 * （20dp，与原 AppShapes.panel 取值一致）。
 *
 * 文案、按钮行为与各 Dialog 的函数签名逐字保留；确认/取消按钮由调用方传入
 * [AppPrimaryButton] / [AppSecondaryButton]。
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
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = RoundedCornerShape(DesignTokens.Radius.Panel),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}
