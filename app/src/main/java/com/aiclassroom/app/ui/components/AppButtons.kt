package com.aiclassroom.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aiclassroom.app.ui.theme.DesignTokens

/**
 * 阶段 1 基础按钮（Design System 组件清单）。
 *
 * - [AppPrimaryButton]：主行动按钮，唯一强调色 Nothing 红（DesignTokens.Accent），
 *   仅用于对话框确认等主行动点；扁平无阴影，圆角走 DesignTokens.Radius.Button。
 * - [AppSecondaryButton]：次级描边按钮（细线分隔方向），用于对话框取消。
 *
 * 两者当前的实际调用点在 MainActivity 的 7 处 Dialog（确认/取消按钮），
 * 不创建无调用点的组件。
 */
@Composable
fun AppPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(DesignTokens.Radius.Button),
        colors = ButtonDefaults.buttonColors(
            containerColor = DesignTokens.Accent,
            contentColor = DesignTokens.AccentOn
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            hoveredElevation = 0.dp,
            focusedElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        content = content
    )
}

@Composable
fun AppSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(DesignTokens.Radius.Button),
        border = BorderStroke(DesignTokens.Border.Hairline, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            hoveredElevation = 0.dp,
            focusedElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        content = content
    )
}
