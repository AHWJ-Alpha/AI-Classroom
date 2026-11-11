package com.aiclassroom.app.ui.chat

/**
 * 对话相关的小组件（从 MainActivity 搬出）。
 */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.aiclassroom.app.model.AppPalette
import com.aiclassroom.app.util.compositeOn
import com.aiclassroom.app.ui.theme.DesignTokens

/**
 * 消息长按操作菜单：同 window 树内毛玻璃浮层（替代 Popup DropdownMenu，Popup 采不到 hazeSource）。
 * 容器布局尺寸记 0 但以屏幕尺寸绘制并平移到窗口原点，故不影响卡片/列表排版；面板锚定长按位置并夹在卡片底与窗口内。
 */
@Composable
internal fun MessageActionMenu(expanded: Boolean, palette: AppPalette, onDismiss: () -> Unit, onCopy: () -> Unit, onBranch: () -> Unit, onDeleteAfter: () -> Unit) {
    if (!expanded) return
    // **不要再用「整屏 Box + 负偏移 + haze 模糊」那套 hack**。
    //
    // 原实现强制 measure 成整屏尺寸、再把内容 place 到 (-anchor.x, -anchor.y)，
    // 外面还叠了一层 18dp 的 `hazeEffect` 全屏模糊。这段代码跑在 LazyColumn 的
    // item 里，只被 clip 到几百 px 的消息区，却要渲染 2400×1080 的 GPU 离屏模糊层 ——
    // 实测长按直接 SIGSEGV in RenderThread（fault addr 0x7278612f10），进程当场死亡，
    // 用户看到的就是「长按没有菜单，App 直接退出」。
    //
    // 用标准 Popup：它自己就是一个独立窗口，不需要伪造全屏、不需要负偏移；
    // 模糊也去掉 —— 弹出菜单底下就一层半透明底，视觉上够了，没必要赌 GPU。
    Popup(
        onDismissRequest = onDismiss,
        // 默认贴着父容器（消息卡片）左上角、向上展开，长卡片会顶到状态栏里；
        // 往下让一点，既避开状态栏也贴近手指。
        offset = IntOffset(0, with(LocalDensity.current) { 28.dp.roundToPx() }),
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            color = palette.surface,
            shape = RoundedCornerShape(DesignTokens.Radius.Control),
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
        ) {
            Column {
                DropdownMenuItem(
                    text = { Text("复制", color = palette.ink) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = palette.button) },
                    onClick = { onDismiss(); onCopy() }
                )
                DropdownMenuItem(
                    text = { Text("从这里开分支", color = palette.ink) },
                    leadingIcon = { Icon(Icons.Default.AccountTree, null, tint = palette.button) },
                    onClick = { onDismiss(); onBranch() }
                )
                DropdownMenuItem(
                    text = { Text("删除此处及之后", color = palette.ink) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = palette.button) },
                    onClick = { onDismiss(); onDeleteAfter() }
                )
            }
        }
    }
}

@Composable
internal fun AiThinkingRow(palette: AppPalette, mentorName: String = "AI 讲师") {
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
        Surface(Modifier.size(DesignTokens.Size.Avatar), shape = RoundedCornerShape(DesignTokens.Radius.Button), color = DesignTokens.Accent) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(DesignTokens.Size.Icon), color = DesignTokens.AccentOn, strokeWidth = 2.dp)
            }
        }
        Text(
            "${mentorName.ifBlank { "AI 讲师" }} 正在生成回复…",
            color = palette.muted,
            fontSize = DesignTokens.Type.Body,
            modifier = Modifier.background(palette.card.copy(alpha = 0.46f).compositeOn(palette.surface), RoundedCornerShape(DesignTokens.Radius.Note)).padding(horizontal = 13.dp, vertical = 10.dp)
        )
    }
}
