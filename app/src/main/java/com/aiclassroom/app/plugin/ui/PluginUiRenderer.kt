package com.aiclassroom.app.plugin.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiclassroom.app.AppPalette
import com.aiclassroom.app.plugin.PluginUiBinding
import com.aiclassroom.app.ui.theme.DesignTokens
import com.aiclassroom.feature.plugin.ui.UiAnchor
import com.aiclassroom.feature.plugin.ui.UiAnimation
import com.aiclassroom.feature.plugin.ui.UiButtonStyle
import com.aiclassroom.feature.plugin.ui.UiColor
import com.aiclassroom.feature.plugin.ui.UiIcon
import com.aiclassroom.feature.plugin.ui.UiLength
import com.aiclassroom.feature.plugin.ui.UiNode
import com.aiclassroom.feature.plugin.ui.UiNodeType
import com.aiclassroom.feature.plugin.ui.UiRadius
import com.aiclassroom.feature.plugin.ui.UiTextSize
import com.aiclassroom.feature.plugin.ui.UiWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * plugin ui.json → Compose 的渲染器。
 *
 * 三条硬约束（对应协议里的三条要求）：
 * 1. **只吃语义 token**：颜色映射到 [AppPalette]、字号/圆角/间距/动效映射到 [DesignTokens]，
 *    这里没有任何 `Color(0xFF...)` 或魔法数字 —— 插件永远画不出宿主设计语言之外的东西；
 * 2. **无插件零开销**：每个入口都在第一行 `if (bindings.isEmpty()) return`，
 *    没有插件时这三个插槽连一次组合都不会发生；
 * 3. **交互只回跳**：点击 / 长按 / 改值都只是把 `{pluginId, entry, event, value}` 交给主 app，
 *    由主 app 在 IO 线程调插件函数，渲染器自己不执行任何脚本。
 */
data class PluginUiEvent(
    val pluginId: String,
    val entry: String,
    val event: String,
    val value: String? = null,
)

/** 渲染上下文：一个插件此刻的 state 快照 + 本条消息的上下文 + 素材目录。 */
private class Paint(
    val pluginId: String,
    val state: JSONObject?,
    val message: JSONObject?,
    val assets: File?,
    val palette: AppPalette,
    val onEvent: (PluginUiEvent) -> Unit,
)

/** GLOBAL_OVERLAY：铺满父容器但不拦截触摸，每个插件按自己的 anchor / offset 定位。 */
@Composable
internal fun PluginUiOverlay(
    bindings: List<PluginUiBinding>,
    states: Map<String, JSONObject?>,
    palette: AppPalette,
    onEvent: (PluginUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (bindings.isEmpty()) return
    Box(modifier.fillMaxSize()) {
        bindings.forEach { binding ->
            val root = binding.root ?: return@forEach
            Box(Modifier.align(alignmentOf(root.anchor)), contentAlignment = Alignment.TopStart) {
                PaintNode(root, Paint(binding.pluginId, states[binding.pluginId], null, binding.assets, palette, onEvent))
            }
        }
    }
}

/** MESSAGE_BODY_TAIL：画在每条 AI 消息正文下方（[message] 是本条消息的上下文）。 */
@Composable
internal fun PluginUiMessageTail(
    bindings: List<PluginUiBinding>,
    states: Map<String, JSONObject?>,
    message: JSONObject?,
    palette: AppPalette,
    onEvent: (PluginUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (bindings.isEmpty()) return
    Column(modifier.fillMaxWidth().padding(top = DesignTokens.Spacing.Xs)) {
        bindings.forEach { binding ->
            val root = binding.root ?: return@forEach
            PaintNode(root, Paint(binding.pluginId, states[binding.pluginId], message, binding.assets, palette, onEvent))
        }
    }
}

/** TOP_BAR_ACTIONS：插在顶栏右侧动作区（现有图标那一排的左侧）。 */
@Composable
internal fun PluginUiTopBarActions(
    bindings: List<PluginUiBinding>,
    states: Map<String, JSONObject?>,
    palette: AppPalette,
    onEvent: (PluginUiEvent) -> Unit,
) {
    if (bindings.isEmpty()) return
    bindings.forEach { binding ->
        val root = binding.root
        val entry = binding.entry
        when {
            root != null -> PaintNode(
                root,
                Paint(binding.pluginId, states[binding.pluginId], null, binding.assets, palette, onEvent),
            )
            entry != null && !binding.label.isNullOrBlank() -> TopBarFallbackButton(binding, palette, onEvent)
        }
    }
}

/** 没有 ui.json 的 bar_button（旧写法）：用 manifest 的 label 画一个紧凑文字按钮。 */
@Composable
private fun TopBarFallbackButton(binding: PluginUiBinding, palette: AppPalette, onEvent: (PluginUiEvent) -> Unit) {
    val label = binding.label ?: return
    val entry = binding.entry ?: return
    Text(
        text = label,
        color = palette.secondary,
        fontSize = DesignTokens.Type.Small,
        fontWeight = DesignTokens.Weight.Medium,
        modifier = Modifier
            .clip(shapeOf(UiRadius.CONTROL))
            .clickable { onEvent(PluginUiEvent(binding.pluginId, entry, "tap")) }
            .padding(horizontal = DesignTokens.Spacing.Md, vertical = DesignTokens.Spacing.Xs),
    )
}

// ------------------------------------------------------------------ 节点渲染

@Composable
private fun PaintNode(node: UiNode, paint: Paint, modifier: Modifier = Modifier) {
    if (!isVisible(node, paint)) return
    val sized = modifier
        .then(sizeModifier(node))
        .then(offsetModifier(node))
        .then(clickModifier(node, paint))
    val animation = node.animation
    if (animation == null || animation is UiAnimation.Sprite) {
        // sprite 是「一直循环」的帧动画，由 image 节点自己驱动，不走入场动画。
        PaintContent(node, paint, sized)
    } else {
        EnterAnimation(animation) { animated -> PaintContent(node, paint, sized.then(animated)) }
    }
}

@Composable
private fun PaintContent(node: UiNode, paint: Paint, modifier: Modifier) {
    when (node.type) {
        UiNodeType.STACK -> Box(modifier) {
            node.children.forEach { child ->
                val alignment = child.anchor?.let { alignmentOf(it) } ?: Alignment.Center
                Box(Modifier.align(alignment)) { PaintNode(child, paint) }
            }
        }

        UiNodeType.COLUMN -> Column(
            modifier,
            verticalArrangement = node.gap?.let { Arrangement.spacedBy(it.dp) } ?: Arrangement.Top,
        ) {
            node.children.forEach { PaintNode(it, paint) }
        }

        UiNodeType.ROW -> Row(
            modifier,
            horizontalArrangement = node.gap?.let { Arrangement.spacedBy(it.dp) } ?: Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            node.children.forEach { PaintNode(it, paint) }
        }

        UiNodeType.BOX -> Box(modifier) { node.children.forEach { PaintNode(it, paint) } }

        UiNodeType.CARD -> Surface(
            modifier = modifier,
            shape = shapeOf(node.radius ?: UiRadius.CONTROL),
            color = colorOf(node.background ?: UiColor.SURFACE_VARIANT, paint.palette),
            border = BorderStroke(DesignTokens.Border.Hairline, paint.palette.outline),
        ) {
            Column(
                Modifier.padding(horizontal = DesignTokens.Spacing.Lg, vertical = DesignTokens.Spacing.Md),
                verticalArrangement = node.gap?.let { Arrangement.spacedBy(it.dp) } ?: Arrangement.Top,
            ) {
                node.children.forEach { PaintNode(it, paint) }
            }
        }

        UiNodeType.BUBBLE -> Surface(
            modifier = modifier,
            shape = shapeOf(node.radius ?: UiRadius.CARD),
            color = colorOf(node.background ?: UiColor.SURFACE, paint.palette),
            border = BorderStroke(DesignTokens.Border.Hairline, paint.palette.outline),
            shadowElevation = 0.dp,
        ) {
            Column(Modifier.padding(horizontal = DesignTokens.Spacing.CardHorizontal, vertical = DesignTokens.Spacing.CardVertical)) {
                node.children.forEach { PaintNode(it, paint) }
            }
        }

        UiNodeType.SPACER -> Spacer(
            modifier
                .then(if (node.width == null) Modifier.width(1.dp) else Modifier)
                .then(if (node.height == null) Modifier.height(DesignTokens.Spacing.Sm) else Modifier),
        )
        UiNodeType.DIVIDER -> HorizontalDivider(
            modifier = modifier,
            thickness = DesignTokens.Border.Hairline,
            color = paint.palette.outline,
        )

        UiNodeType.TEXT -> {
            val text = textOf(node, paint) ?: return
            Text(
                text = text,
                modifier = modifier,
                color = colorOf(node.textColor ?: UiColor.ON_SURFACE, paint.palette),
                fontSize = fontSizeOf(node.textSize),
                lineHeight = lineHeightOf(node.textSize),
                fontWeight = if (node.weight == UiWeight.BOLD) DesignTokens.Weight.Bold else DesignTokens.Weight.Regular,
                maxLines = node.maxLines ?: Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
        }

        UiNodeType.IMAGE -> {
            val sprite = node.animation as? UiAnimation.Sprite
            val path = if (sprite != null) spriteFrame(sprite) else node.src
            val bitmap = rememberAsset(paint.assets, path)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = modifier.then(if (node.width == null) Modifier.size(48.dp) else Modifier),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        UiNodeType.ICON -> Icon(
            imageVector = iconOf(node.name ?: UiIcon.STAR),
            contentDescription = null,
            modifier = modifier.then(Modifier.size((node.iconSize ?: 20).dp)),
            tint = colorOf(node.textColor ?: UiColor.ON_SURFACE, paint.palette),
        )

        UiNodeType.BUTTON -> PluginButton(node, paint, modifier)

        UiNodeType.SWITCH -> {
            val entry = node.onChange
            Switch(
                checked = booleanOf(node.checkedFrom, paint) ?: false,
                onCheckedChange = { checked ->
                    if (entry != null) {
                        paint.onEvent(PluginUiEvent(paint.pluginId, entry, "change", if (checked) "true" else "false"))
                    }
                },
                modifier = modifier,
            )
        }

        UiNodeType.SLIDER -> {
            val entry = node.onChange
            val minimum = (node.min ?: 0).toFloat()
            val maximum = ((node.max ?: 100).toFloat()).coerceAtLeast(minimum + 1f)
            val current = (numberOf(node.valueFrom, paint)?.toFloat() ?: minimum).coerceIn(minimum, maximum)
            Slider(
                value = current,
                valueRange = minimum..maximum,
                onValueChange = { value ->
                    if (entry != null) {
                        paint.onEvent(PluginUiEvent(paint.pluginId, entry, "change", formatNumber(value.toDouble())))
                    }
                },
                modifier = modifier,
            )
        }

        UiNodeType.PROGRESS -> {
            val raw = (numberOf(node.valueFrom, paint)?.toFloat() ?: 0f).coerceAtLeast(0f)
            // 0..1 视为比例，>1 视为百分比，两种写法都接受。
            val fraction = if (raw <= 1f) raw else (raw / 100f).coerceAtMost(1f)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = modifier,
                color = colorOf(node.textColor ?: UiColor.PRIMARY, paint.palette),
                trackColor = paint.palette.card,
            )
        }
    }
}

@Composable
private fun PluginButton(node: UiNode, paint: Paint, modifier: Modifier) {
    val label = node.label ?: return
    val entry = node.onTap
    val onClick: (() -> Unit)? = if (entry == null) null else {
        { paint.onEvent(PluginUiEvent(paint.pluginId, entry, "tap")) }
    }
    val shape = shapeOf(node.radius ?: UiRadius.CONTROL)
    when (node.style ?: UiButtonStyle.SECONDARY) {
        UiButtonStyle.PRIMARY -> Surface(
            onClick = onClick ?: {},
            modifier = modifier,
            shape = shape,
            color = colorOf(node.background ?: UiColor.PRIMARY, paint.palette),
            contentColor = paint.palette.onButton,
        ) {
            Text(
                text = label,
                color = paint.palette.onButton,
                fontSize = DesignTokens.Type.Small,
                fontWeight = DesignTokens.Weight.Medium,
                modifier = Modifier.padding(horizontal = DesignTokens.Spacing.Lg, vertical = DesignTokens.Spacing.Sm),
            )
        }

        UiButtonStyle.SECONDARY -> Surface(
            onClick = onClick ?: {},
            modifier = modifier,
            shape = shape,
            color = colorOf(node.background ?: UiColor.SURFACE_VARIANT, paint.palette),
            contentColor = paint.palette.ink,
            border = BorderStroke(DesignTokens.Border.Hairline, paint.palette.outline),
        ) {
            Text(
                text = label,
                color = paint.palette.ink,
                fontSize = DesignTokens.Type.Small,
                fontWeight = DesignTokens.Weight.Medium,
                modifier = Modifier.padding(horizontal = DesignTokens.Spacing.Lg, vertical = DesignTokens.Spacing.Sm),
            )
        }

        UiButtonStyle.TEXT -> Text(
            text = label,
            color = paint.palette.secondary,
            fontSize = DesignTokens.Type.Small,
            fontWeight = DesignTokens.Weight.Medium,
            modifier = modifier
                .clip(shape)
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(horizontal = DesignTokens.Spacing.Md, vertical = DesignTokens.Spacing.Xs),
        )
    }
}

// ------------------------------------------------------------------ 动画

@Composable
private fun EnterAnimation(animation: UiAnimation, content: @Composable (Modifier) -> Unit) {
    val duration = animation.durationMs.coerceIn(0, DesignTokens.Motion.SlowMs * 4)
    var started by remember(animation) { mutableStateOf(false) }
    LaunchedEffect(animation) { started = true }
    when (animation) {
        is UiAnimation.Fade -> {
            val value by animateFloatAsState(if (started) 1f else 0f, tween(duration), label = "pluginFade")
            content(Modifier.alpha(value))
        }

        is UiAnimation.Scale -> {
            val value by animateFloatAsState(if (started) 1f else 0.88f, tween(duration), label = "pluginScale")
            content(Modifier.graphicsLayer { scaleX = value; scaleY = value })
        }

        is UiAnimation.Slide -> {
            val value by animateDpAsState(if (started) 0.dp else 16.dp, tween(duration), label = "pluginSlide")
            content(Modifier.offset(y = value))
        }

        is UiAnimation.Sprite -> content(Modifier)
    }
}

/** sprite 循环切帧：按 fps 轮换帧路径。 */
@Composable
private fun spriteFrame(sprite: UiAnimation.Sprite): String? {
    val frames = sprite.frames
    if (frames.isEmpty()) return null
    if (frames.size == 1) return frames[0]
    var index by remember(sprite) { mutableIntStateOf(0) }
    LaunchedEffect(sprite) {
        val interval = (1000 / sprite.fps.coerceIn(1, 30)).toLong()
        while (true) {
            delay(interval)
            index = (index + 1) % frames.size
        }
    }
    return frames[index % frames.size]
}

// ------------------------------------------------------------------ 取值表达式

/** 只支持 `state.xxx` / `msg.xxx` 的单层取值，不做表达式求值。 */
private fun valueOf(expression: String?, paint: Paint): Any? {
    if (expression == null) return null
    val dot = expression.indexOf('.')
    if (dot <= 0 || dot != expression.lastIndexOf('.')) return null
    val source = when (expression.substring(0, dot)) {
        "state" -> paint.state
        "msg" -> paint.message
        else -> null
    } ?: return null
    val key = expression.substring(dot + 1)
    if (!source.has(key)) return null
    val raw = source.opt(key) ?: return null
    return if (raw == JSONObject.NULL) null else raw
}

private fun isVisible(node: UiNode, paint: Paint): Boolean = booleanOf(node.visibleWhen, paint) ?: true

private fun booleanOf(expression: String?, paint: Paint): Boolean? = when (val value = valueOf(expression, paint)) {
    null -> null
    is Boolean -> value
    is Number -> value.toDouble() != 0.0
    is String -> when (value.trim().lowercase(Locale.US)) {
        "true", "1", "yes" -> true
        "false", "0", "no", "" -> false
        else -> null
    }

    else -> null
}

private fun numberOf(expression: String?, paint: Paint): Double? = when (val value = valueOf(expression, paint)) {
    null -> null
    is Number -> value.toDouble()
    is String -> value.trim().toDoubleOrNull()
    is Boolean -> if (value) 1.0 else 0.0
    else -> null
}

private fun textOf(node: UiNode, paint: Paint): String? {
    node.text?.let { return it }
    return when (val value = valueOf(node.textFrom, paint)) {
        null -> null
        is Double -> formatNumber(value)
        is Float -> formatNumber(value.toDouble())
        is Number -> value.toString()
        else -> value.toString()
    }
}

private fun formatNumber(value: Double): String =
    if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString()
    else String.format(Locale.US, "%.1f", value)

// ------------------------------------------------------------------ 语义 token → Compose

private fun colorOf(color: UiColor, palette: AppPalette): Color = when (color) {
    UiColor.SURFACE -> palette.surface
    UiColor.SURFACE_VARIANT -> palette.card
    UiColor.PRIMARY -> palette.primary
    UiColor.TRANSPARENT -> Color.Transparent
    UiColor.MUTED -> palette.muted
    UiColor.DANGER -> DesignTokens.Danger
    UiColor.ON_SURFACE -> palette.ink
}

private fun shapeOf(radius: UiRadius): RoundedCornerShape = when (radius) {
    UiRadius.CARD -> RoundedCornerShape(DesignTokens.Radius.Card)
    UiRadius.CONTROL -> RoundedCornerShape(DesignTokens.Radius.Control)
    UiRadius.PANEL -> RoundedCornerShape(DesignTokens.Radius.Panel)
    UiRadius.FULL -> RoundedCornerShape(DesignTokens.Radius.Pill)
}

private fun fontSizeOf(size: UiTextSize?): androidx.compose.ui.unit.TextUnit = when (size) {
    UiTextSize.TITLE -> DesignTokens.Type.Title
    UiTextSize.SUBTITLE -> DesignTokens.Type.Subtitle
    null, UiTextSize.BODY -> DesignTokens.Type.Body
    UiTextSize.CAPTION -> DesignTokens.Type.Caption
}

private fun lineHeightOf(size: UiTextSize?): androidx.compose.ui.unit.TextUnit = when (size) {
    UiTextSize.CAPTION -> DesignTokens.Type.LineSnug
    UiTextSize.TITLE, UiTextSize.SUBTITLE -> DesignTokens.Type.LineBody
    null, UiTextSize.BODY -> DesignTokens.Type.LineBody
}

private fun iconOf(icon: UiIcon): ImageVector = when (icon) {
    UiIcon.STAR -> Icons.Default.Star
    UiIcon.HEART -> Icons.Default.Favorite
    UiIcon.SETTINGS -> Icons.Default.Settings
    UiIcon.PUBLIC -> Icons.Default.Public
    UiIcon.SEND -> Icons.Default.Send
}

private fun alignmentOf(anchor: UiAnchor?): Alignment = when (anchor) {
    UiAnchor.TOP_LEFT -> Alignment.TopStart
    UiAnchor.TOP_CENTER -> Alignment.TopCenter
    UiAnchor.TOP_RIGHT -> Alignment.TopEnd
    UiAnchor.CENTER_LEFT -> Alignment.CenterStart
    null, UiAnchor.CENTER -> Alignment.Center
    UiAnchor.CENTER_RIGHT -> Alignment.CenterEnd
    UiAnchor.BOTTOM_LEFT -> Alignment.BottomStart
    UiAnchor.BOTTOM_CENTER -> Alignment.BottomCenter
    UiAnchor.BOTTOM_RIGHT -> Alignment.BottomEnd
}

// ------------------------------------------------------------------ 尺寸 / 交互 / 素材

private fun sizeModifier(node: UiNode): Modifier {
    var modifier: Modifier = Modifier
    when (val length = node.width) {
        UiLength.Fill -> modifier = modifier.fillMaxWidth()
        UiLength.Wrap, null -> Unit
        is UiLength.Fixed -> modifier = modifier.width(length.dp.dp)
    }
    when (val length = node.height) {
        UiLength.Fill -> modifier = modifier.fillMaxHeight()
        UiLength.Wrap, null -> Unit
        is UiLength.Fixed -> modifier = modifier.height(length.dp.dp)
    }
    node.padding?.let { modifier = modifier.padding(it.dp) }
    return modifier
}

private fun offsetModifier(node: UiNode): Modifier =
    if (node.offsetX == 0 && node.offsetY == 0) {
        Modifier
    } else {
        Modifier.offset(x = node.offsetX.dp, y = node.offsetY.dp)
    }

@OptIn(ExperimentalFoundationApi::class)
private fun clickModifier(node: UiNode, paint: Paint): Modifier {
    val tap = node.onTap
    val longPress = node.onLongPress
    if (tap == null && longPress == null) return Modifier
    return Modifier.combinedClickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        onClick = { if (tap != null) paint.onEvent(PluginUiEvent(paint.pluginId, tap, "tap")) },
        onLongClick = { if (longPress != null) paint.onEvent(PluginUiEvent(paint.pluginId, longPress, "long_press")) },
    )
}

/** 插件包内素材：只允许包内相对路径，越界一律当作不存在。 */
private fun resolveAsset(dir: File?, path: String?): File? {
    if (dir == null || path.isNullOrBlank()) return null
    if (path.startsWith("/") || path.contains("..") || path.contains("\\") || path.contains(":")) return null
    val file = File(dir, path)
    return if (file.isFile) file else null
}

@Composable
private fun rememberAsset(dir: File?, path: String?): ImageBitmap? {
    val file = remember(dir, path) { resolveAsset(dir, path) }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, file) {
        value = if (file == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    return bitmap
}
