package com.aiclassroom.app.plugin.ui

import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.unit.sp
import com.aiclassroom.app.AppPalette
import com.aiclassroom.app.plugin.PluginUiBinding
import com.aiclassroom.app.ui.theme.DesignTokens
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import com.aiclassroom.feature.plugin.ui.PixelFont
import com.aiclassroom.feature.plugin.ui.PixelTextMetrics
import com.aiclassroom.feature.plugin.ui.UI_FLIP_DURATION_MS
import com.aiclassroom.feature.plugin.ui.UI_PIXEL_ROLL_DURATION_MS
import com.aiclassroom.feature.plugin.ui.UiAlign
import com.aiclassroom.feature.plugin.ui.UiAnchor
import com.aiclassroom.feature.plugin.ui.UiAnimation
import com.aiclassroom.feature.plugin.ui.UiBorder
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
    /** 用户在设置里选的图片：ui.json 写 "src": "user:bg" 时按这个表找文件。 */
    val userImages: Map<String, File> = emptyMap(),
    /** user: 图片的裁切方式（剪裁 / 适应 / 填充），由插件的设置项给出。 */
    val userImageFit: Map<String, String> = emptyMap(),
    /**
     * 是否画在息屏底（background: screen）里面。
     *
     * 息屏底是固定的近黑，不随主题翻转；这时 onSurface / muted 也必须是固定的米白 / 灰，
     * 否则浅色主题下会变成「深字压在深底上」，整屏什么都看不见。
     */
    val aod: Boolean = false,
) {
    /** 进入 / 离开一个容器的背景时，把「我在息屏底上」这个事实传给它的子树。 */
    fun withBackground(background: UiColor?): Paint {
        val next = when (background) {
            UiColor.SCREEN -> true
            null, UiColor.TRANSPARENT -> aod
            else -> false
        }
        if (next == aod) return this
        return Paint(
            pluginId = pluginId,
            state = state,
            message = message,
            assets = assets,
            palette = palette,
            onEvent = onEvent,
            userImages = userImages,
            userImageFit = userImageFit,
            aod = next,
        )
    }
}

/** GLOBAL_OVERLAY：铺满父容器但不拦截触摸，每个插件按自己的 anchor / offset 定位。 */
@Composable
internal fun PluginUiOverlay(
    bindings: List<PluginUiBinding>,
    states: Map<String, JSONObject?>,
    palette: AppPalette,
    onEvent: (PluginUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    /** 插件 id -> 该插件这一版 state 的版本号；变了就重组，实现「宿主调一次 provider 界面就更新」。 */
    stateVersions: Map<String, Int> = emptyMap(),
    userImages: Map<String, Map<String, File>> = emptyMap(),
    userImageFits: Map<String, Map<String, String>> = emptyMap(),
) {
    if (bindings.isEmpty()) return
    Box(modifier.fillMaxSize()) {
        bindings.forEach { binding ->
            val root = binding.root ?: return@forEach
            /** 每个插件一个 key：A 插件刷新不会连带把 B 插件的节点重新组合。 */
            key(binding.pluginId, stateVersions[binding.pluginId] ?: 0) {
                Box(Modifier.align(alignmentOf(root.anchor)), contentAlignment = Alignment.TopStart) {
                    PaintNode(
                        root,
                        Paint(
                            pluginId = binding.pluginId,
                            state = states[binding.pluginId],
                            message = null,
                            assets = binding.assets,
                            palette = palette,
                            onEvent = onEvent,
                            userImages = userImages[binding.pluginId].orEmpty(),
                            userImageFit = userImageFits[binding.pluginId].orEmpty(),
                        ),
                    )
                }
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
    stateVersions: Map<String, Int> = emptyMap(),
    userImages: Map<String, Map<String, File>> = emptyMap(),
    userImageFits: Map<String, Map<String, String>> = emptyMap(),
) {
    if (bindings.isEmpty()) return
    Column(modifier.fillMaxWidth().padding(top = DesignTokens.Spacing.Xs)) {
        bindings.forEach { binding ->
            val root = binding.root ?: return@forEach
            key(binding.pluginId, stateVersions[binding.pluginId] ?: 0) {
                PaintNode(
                    root,
                    Paint(
                        pluginId = binding.pluginId,
                        state = states[binding.pluginId],
                        message = message,
                        assets = binding.assets,
                        palette = palette,
                        onEvent = onEvent,
                        userImages = userImages[binding.pluginId].orEmpty(),
                        userImageFit = userImageFits[binding.pluginId].orEmpty(),
                    ),
                )
            }
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
    stateVersions: Map<String, Int> = emptyMap(),
    userImages: Map<String, Map<String, File>> = emptyMap(),
    userImageFits: Map<String, Map<String, String>> = emptyMap(),
) {
    if (bindings.isEmpty()) return
    bindings.forEach { binding ->
        val root = binding.root
        val entry = binding.entry
        when {
            root != null -> key(binding.pluginId, stateVersions[binding.pluginId] ?: 0) {
                PaintNode(
                    root,
                    Paint(
                        pluginId = binding.pluginId,
                        state = states[binding.pluginId],
                        message = null,
                        assets = binding.assets,
                        palette = palette,
                        onEvent = onEvent,
                        userImages = userImages[binding.pluginId].orEmpty(),
                        userImageFit = userImageFits[binding.pluginId].orEmpty(),
                    ),
                )
            }

            entry != null && !binding.label.isNullOrBlank() -> TopBarFallbackButton(binding, palette, onEvent)
        }
    }
}

/**
 * FULLSCREEN：独占整屏的插件层。
 *
 * 三条约定：
 * 1. **覆盖在底栏之上**：全屏层的意义就是"什么都看不见了"，留一条底栏在下面没有意义；
 * 2. **保留安全区**：内容按 `statusBarsPadding + navigationBarsPadding` 内缩，刘海 / 挖孔 / 手势条不压字，
 *    这样"隐藏系统栏"退化成视觉上的沉浸，而不是把内容塞到系统手势区里；
 * 3. **兜底退出**：右上角固定画一个极小退出按钮，**与插件自己的退出入口无关**——
 *    插件没做退出按钮、或者做了但被藏起来，用户也不会被困在别人的界面里。
 */
@Composable
internal fun PluginUiFullscreen(
    binding: PluginUiBinding,
    state: JSONObject?,
    palette: AppPalette,
    onEvent: (PluginUiEvent) -> Unit,
    onExit: () -> Unit,
    stateVersion: Int = 0,
    userImages: Map<String, File> = emptyMap(),
    userImageFit: Map<String, String> = emptyMap(),
) {
    val root = binding.root ?: return
    val paint = Paint(
        pluginId = binding.pluginId,
        state = state,
        message = null,
        assets = binding.assets,
        palette = palette,
        onEvent = onEvent,
        userImages = userImages,
        userImageFit = userImageFit,
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(palette.page)
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                // 全屏层自己吃掉点击：它盖住了整个课堂页，穿透会让用户误触下面的消息卡片。
                onClick = {},
            ),
    ) {
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // 闪烁修复：key 只认 pluginId，**不要带上 stateVersion**。
            // 带上它等于每次 tick 都把整棵渲染树销毁重建（图片重新解码、动画重新开始），
            // 表现为每秒闪一下。状态变化只需触发重组（recomposition），不需要换 identity。
            key(binding.pluginId) { PaintNode(root, paint, Modifier.fillMaxSize()) }
        }
        FullscreenExitButton(palette, onExit)
    }
}

/**
 * 全屏退出的唯一入口：长按退出，单击无效（防误触）。
 *
 * 按下后：按钮轻微放大 + 一圈进度环从 12 点顺时针画满；画满（900ms）才退出，
 * 中途松手环会快速收回并取消。这是商业应用常见的长按销毁交互。
 * 右下角 44dp 触控区，不再是右上角那个突兀的小叉。
 */
@Composable
private fun BoxScope.FullscreenExitButton(palette: AppPalette, onExit: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val pressed = progress.value > 0f
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.12f else 1f,
        animationSpec = tween(if (pressed) 140 else 220),
        label = "exitScale",
    )
    // 与课堂页的「回到顶部」浮动按钮（FloatingIconAction）完全同款：
    // 圆形 Pill 形状 + Size.Fab 尺寸 + 细描边 + 1dp 阴影，图标用主题强调色。
    // 唯一的不同是这里要长按渐变确认，所以在圆形表面上叠了一圈进度弧。
    val circleShape = RoundedCornerShape(DesignTokens.Radius.Pill)
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(DesignTokens.Spacing.Md)
            .size(DesignTokens.Size.Fab)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(onExit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    scope.launch { progress.animateTo(1f, tween(900, easing = LinearEasing)) }
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { !it.pressed }) break
                    }
                    if (progress.value >= 0.999f) {
                        onExit()
                    } else {
                        scope.launch { progress.animateTo(0f, tween(180)) }
                    }
                }
            },
    ) {
        Surface(
            modifier = Modifier.matchParentSize(),
            color = palette.surface,
            shape = circleShape,
            shadowElevation = 1.dp,
            border = BorderStroke(DesignTokens.Border.Hairline, palette.outline),
        ) {}
        // 长按进度：沿圆形边缘画弧（与 FloatingIconAction 的圆形态自然贴合）。
        Canvas(Modifier.matchParentSize()) {
            if (progress.value <= 0f) return@Canvas
            val stroke = 2.dp.toPx()
            drawArc(
                color = DesignTokens.Accent.copy(alpha = 0.9f),
                startAngle = -90f,
                sweepAngle = 360f * progress.value,
                useCenter = false,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "长按退出全屏",
            tint = DesignTokens.Accent,
            modifier = Modifier.align(Alignment.Center).size(DesignTokens.Size.IconLg),
        )
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
    // 顺序很重要：尺寸 → 背景 → 内边距。
    // 背景压在 padding 外面，写了 padding 的整屏容器（息屏钟）才会连边都铺满颜色，
    // 而不是在四周留一圈主题白的边。
    val sized = modifier
        .then(sizeModifier(node, includePadding = false))
        .then(backgroundModifier(node, paint))
        .then(paddingModifier(node))
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
        UiNodeType.STACK -> Box(modifier.then(backgroundModifier(node, paint))) {
            node.children.forEach { child ->
                val alignment = child.anchor?.let { alignmentOf(it) } ?: Alignment.Center
                Box(Modifier.align(alignment)) { PaintNode(child, childPaint(paint, node)) }
            }
        }

        UiNodeType.COLUMN -> Column(
            // 居中由父容器的 anchor 决定；这里只负责子项对齐，不再擅自 fillMaxWidth ——
            // 那会把 Row 里的卡片撑满整行，分钟/秒被挤出可视区。
            modifier.then(backgroundModifier(node, paint)),
            horizontalAlignment = Alignment.CenterHorizontally,
            // 高度撑满的列默认垂直居中：协议里 height:fill 的列，语义就是「这一列占满这块地方」，
            // 内容贴着顶边摆会让上下留白完全失衡（息屏钟要求的是居中偏上的对称留白）。
            verticalArrangement = when {
                node.gap == null && node.height == UiLength.Fill -> Arrangement.Center
                node.height == UiLength.Fill ->
                    Arrangement.spacedBy(node.gap!!.dp, Alignment.CenterVertically)
                node.gap != null -> Arrangement.spacedBy(node.gap!!.dp)
                else -> Arrangement.Top
            },
        ) {
            node.children.forEach { child ->
                // 纵向容器里 height:fill 的语义是「剩下的高度都归你」——用 weight 而不是
                // fillMaxHeight：后者会让这一个子节点吃满整列，把它后面的兄弟顶出屏幕。
                if (child.height == UiLength.Fill) {
                    Box(Modifier.weight(1f)) { PaintNode(child, childPaint(paint, node)) }
                } else {
                    PaintNode(child, childPaint(paint, node))
                }
            }
        }

        UiNodeType.ROW -> Row(
            modifier.then(backgroundModifier(node, paint)),
            horizontalArrangement = node.gap?.let { Arrangement.spacedBy(it.dp) } ?: Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            node.children.forEach { child ->
                // 横向容器里同理：width:fill 是「剩下的宽度都归你」。
                if (child.width == UiLength.Fill) {
                    Box(Modifier.weight(1f)) { PaintNode(child, childPaint(paint, node)) }
                } else {
                    PaintNode(child, childPaint(paint, node))
                }
            }
        }

        UiNodeType.BOX -> Box(modifier.then(backgroundModifier(node, paint))) {
            node.children.forEach { child ->
                // 子节点的 anchor 在 box 里同样有效：铺满背景的 image 没有 anchor，
                // 保持左上角对齐；写了 anchor 的（居中时钟、底部按钮）按锚点摆。
                val alignment = child.anchor?.let { alignmentOf(it) } ?: Alignment.TopStart
                Box(Modifier.align(alignment)) { PaintNode(child, childPaint(paint, node)) }
            }
        }

        UiNodeType.CARD -> Surface(
            modifier = modifier,
            shape = shapeOf(node.radius ?: UiRadius.CONTROL),
            color = colorOf(node.background ?: UiColor.SURFACE_VARIANT, paint.palette, paint.aod),
            border = BorderStroke(DesignTokens.Border.Hairline, paint.palette.outline),
        ) {
            Column(
                Modifier.padding(horizontal = DesignTokens.Spacing.Lg, vertical = DesignTokens.Spacing.Md),
                verticalArrangement = node.gap?.let { Arrangement.spacedBy(it.dp) } ?: Arrangement.Top,
            ) {
                node.children.forEach { PaintNode(it, childPaint(paint, node)) }
            }
        }

        UiNodeType.BUBBLE -> Surface(
            modifier = modifier,
            shape = shapeOf(node.radius ?: UiRadius.CARD),
            color = colorOf(node.background ?: UiColor.SURFACE, paint.palette, paint.aod),
            border = BorderStroke(DesignTokens.Border.Hairline, paint.palette.outline),
            shadowElevation = 0.dp,
        ) {
            Column(Modifier.padding(horizontal = DesignTokens.Spacing.CardHorizontal, vertical = DesignTokens.Spacing.CardVertical)) {
                node.children.forEach { PaintNode(it, childPaint(paint, node)) }
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
                color = colorOf(node.textColor ?: UiColor.ON_SURFACE, paint.palette, paint.aod),
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
            // user:<key> 指向用户在设置页选中的图片（已复制进插件私有目录），
            // 其余一律按包内相对路径解析 —— 插件拿不到这两条以外的任何文件。
            val bitmap = rememberAsset(paint.assets, path, paint.userImages)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = modifier.then(if (node.width == null) Modifier.size(48.dp) else Modifier),
                    contentScale = contentScaleOf(paint, path),
                )
            }
        }

        UiNodeType.ICON -> Icon(
            imageVector = iconOf(node.name ?: UiIcon.STAR),
            contentDescription = null,
            modifier = modifier.then(Modifier.size((node.iconSize ?: 20).dp)),
            tint = colorOf(node.textColor ?: UiColor.ON_SURFACE, paint.palette, paint.aod),
        )

        UiNodeType.FLIP_CARD -> FlipCard(node, paint, modifier)

        UiNodeType.PIXEL_TEXT -> PixelText(node, paint, modifier)

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
                color = colorOf(node.textColor ?: UiColor.PRIMARY, paint.palette, paint.aod),
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
            color = colorOf(node.background ?: UiColor.PRIMARY, paint.palette, paint.aod),
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
            color = colorOf(node.background ?: UiColor.SURFACE_VARIANT, paint.palette, paint.aod),
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

// ------------------------------------------------------------------ 像素点阵文字

/** 不支持字符的日志标签：同一个字符只记一次，避免每秒 tick 刷屏。 */
private const val PIXEL_TEXT_TAG = "PluginPixelText"

/** 有边框时文字最多占容器的这个比例，剩下的给虚线边框留呼吸空间。 */
private const val BORDER_TEXT_FILL = 0.80f

/** 虚线与文字之间留几个像素点的内边距。 */
private const val BORDER_INSET_POINTS = 3f

/** 全局去重表：键是 "插件id:字符"，整个进程只记一次。 */
private val reportedPixelChars = java.util.Collections.synchronizedSet(mutableSetOf<String>())

/**
 * 像素点阵文字：把 5x7 的字模逐格画成方块，不使用任何字体。
 *
 * 三条设计要点：
 * 1. **自适应尺寸**：pixelSize 由 [PixelTextMetrics] 按容器宽高算出，
 *    竖屏 1080x2400 与横屏 2400x1080 都会自己缩放到放得下为止 —— 这是横竖屏不崩的关键；
 * 2. **只滚变化的那一位**：新旧文本逐位对比，相同的位保持不动，
 *    变化的位做「旧字抛出上方、新字从下方进来」的 180ms 滚动（FastOutSlowIn）；
 * 3. **绝不崩**：不支持的字符（字母、汉字）跳过并记一次日志，不是抛异常。
 *
 * 视觉基调是息屏时钟：深底、亮字、可选扫描线，没有中缝也没有任何说明文字。
 */
@Composable
private fun PixelText(node: UiNode, paint: Paint, modifier: Modifier) {
    val current = textOf(node, paint).orEmpty()
    // 上一次真正画上屏的文本。用 remember 而不是从 state 反推：
    // 插件改得太快时，动画永远从"屏幕上那串字"开始，不会跳帧。
    var previous by remember(node.path) { mutableStateOf(current) }
    val roll = remember(node.path) { Animatable(1f) }

    LaunchedEffect(node.path, current) {
        if (current != previous) {
            roll.snapTo(0f)
            roll.animateTo(1f, tween(UI_PIXEL_ROLL_DURATION_MS, easing = FastOutSlowInEasing))
            // 收尾才把起点挪到新值：中途被打断时，起点仍是屏幕上那串旧字。
            previous = current
        }
    }

    if (current.isEmpty()) return

    val unsupported = PixelFont.unsupportedChars(current)
    if (unsupported.isNotEmpty()) {
        // 只记一次：插件每秒 tick 一次，每帧都打日志会把 logcat 刷满。
        for (ch in unsupported) {
            val key = paint.pluginId + ":" + ch
            if (reportedPixelChars.add(key)) {
                Log.w(PIXEL_TEXT_TAG, "插件 " + paint.pluginId + " 的像素字不支持字符 'U+" +
                    Integer.toHexString(ch.code).uppercase(Locale.US) + "'，已跳过（节点 " + node.path + "）")
            }
        }
    }

    // 像素钟天生是「息屏时钟」：深底 + 亮字。
    // 这里强制按 aod=true 取色（AOD_INK 米白 / AOD_OUTLINE 灰），
    // 否则在浅色主题下 onSurface 会取到深色 ink，压在近黑底上等于隐形（实测就是这么翻车的）。
    val ink = colorOf(node.textColor ?: UiColor.ON_SURFACE, paint.palette, aod = true)
    val borderInk = colorOf(node.borderColor ?: UiColor.OUTLINE, paint.palette, aod = true)
    val progress = roll.value
    val from = previous

    // pixeltext 永远 wrap 到「文字本身占多大」：
    // 用 fill 占位会让它在 Column 里吞掉整列高度，把兄弟节点挤到屏幕边缘去（日期那一行就是这么翻车的）。
    // 容器给多大它就在多大范围里算 pixelSize，但自己只占算出来的那块。
    val fixedDp = node.pixelSize
    val densityScope = LocalDensity.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // 有边框时给边框留出位置：字最多占 80%，四条边上的虚线方块永远压不到字。
        val bordered = node.border != null
        val fill = if (bordered) BORDER_TEXT_FILL else 1f
        val pixelSize = fixedDp?.toFloat() ?: PixelTextMetrics.pixelSizeOf(
            text = current,
            maxWidth = constraints.maxWidth.toFloat() * fill,
            maxHeight = constraints.maxHeight.toFloat() * fill,
        )
        // 自适应的节点自己只占这么点地方；固定 pixelSize 时同理，两者都由字模尺寸决定。
        val insetPoints = pixelSize * BORDER_INSET_POINTS * 2
        val wrapWidth = with(densityScope) { (PixelTextMetrics.widthOf(current, pixelSize) + insetPoints).toDp() }
        val wrapHeight = with(densityScope) { (PixelTextMetrics.heightOf(pixelSize) + insetPoints).toDp() }

        // 只按字模尺寸占位：写在 fillMaxSize 后面是无效的（fill 会赢），节点会重新变成整块。
        Canvas(Modifier.size(wrapWidth, wrapHeight)) {
            if (pixelSize <= 0f) return@Canvas
            val textWidth = PixelTextMetrics.widthOf(current, pixelSize)
            val textHeight = PixelTextMetrics.heightOf(pixelSize)
            val left = when (node.align ?: UiAlign.CENTER) {
                UiAlign.START -> 0f
                UiAlign.CENTER -> (size.width - textWidth) / 2f
                UiAlign.END -> size.width - textWidth
            }
            val top = (size.height - textHeight) / 2f

            // 1) 扫描线：每 3 行像素一条更亮的线，CRT 的观感全靠它。
            if (node.scanlines == true) {
                val lineAlpha = 0.055f
                var y = 0f
                while (y < size.height) {
                    drawRect(
                        color = Color.White.copy(alpha = lineAlpha),
                        topLeft = Offset(0f, y),
                        size = Size(size.width, 1f),
                    )
                    y += 3f * density
                }
            }

            // 2) 像素字本体：逐格对比新旧文本，只给变化的那一位做滚动。
            for (index in current.indices) {
                val ch = current[index]
                val glyph = PixelFont.glyphOf(ch) ?: continue
                val x = left + index * (PixelTextMetrics.GLYPH_COLS + PixelTextMetrics.GLYPH_GAP) * pixelSize
                val oldGlyph = from.getOrNull(index)?.let { PixelFont.glyphOf(it) }
                val changed = progress < 1f && oldGlyph != null && oldGlyph != glyph
                if (changed) {
                    drawGlyphRolling(oldGlyph!!, glyph, x, top, pixelSize, progress, textHeight, ink, node.glow == true)
                } else {
                    drawGlyph(glyph, x, top, pixelSize, ink, glow = node.glow == true)
                }
            }

            // 3) 像素虚线边框：贴着文字块外扩一圈（内边距 = 3 个像素点），
            //    像给数字裱了个框，而不是给整块画布描边 —— 后者在竖屏会变成一根瘦长的线。
            if (node.border == UiBorder.DASHED) {
                val inset = pixelSize * BORDER_INSET_POINTS
                drawPixelDashBorder(
                    pixelSize = pixelSize,
                    leftIn = left - inset,
                    topIn = top - inset,
                    rightIn = left + textWidth + inset,
                    bottomIn = top + textHeight + inset,
                    color = borderInk.copy(alpha = 0.55f),
                )
            }
        }
    }
}

/** 画一个字模：按 5x7 逐格判断，有像素才落一个方块；[glow] 时先铺一层放大的低透明光晕。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlyph(
    glyph: List<String>,
    left: Float,
    top: Float,
    pixelSize: Float,
    color: Color,
    yOffset: Float = 0f,
    glow: Boolean = false,
) {
    if (glow) {
        // 光晕就是同一批方块各外扩半格、透明度极低：不用 RenderEffect，
        // 低端机上也不会因为模糊把每一帧的耗时抬起来。
        val spread = pixelSize * 0.55f
        val halo = color.copy(alpha = 0.10f)
        for (row in 0 until PixelFont.ROWS) {
            for (col in 0 until PixelFont.COLS) {
                if (!PixelFont.pixelAt(glyph, row, col)) continue
                drawRect(
                    color = halo,
                    topLeft = Offset(left + col * pixelSize - spread / 2f, top + row * pixelSize + yOffset - spread / 2f),
                    size = Size(pixelSize + spread, pixelSize + spread),
                )
            }
        }
    }
    for (row in 0 until PixelFont.ROWS) {
        for (col in 0 until PixelFont.COLS) {
            if (!PixelFont.pixelAt(glyph, row, col)) continue
            drawRect(
                color = color,
                topLeft = Offset(left + col * pixelSize, top + row * pixelSize + yOffset),
                size = Size(pixelSize, pixelSize),
            )
        }
    }
}

/**
 * 滚动中的一位：旧字向上滚出、新字从下方滚入，两者都用 clip 限制在一个字格的高度内。
 *
 * [travel] 就是动画进度 0→1（180ms，FastOutSlowIn）：
 * travel=0 时画的是旧字、新字还在格线下方；travel=1 时旧字已滚出、新字正好落位。
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlyphRolling(
    oldGlyph: List<String>,
    newGlyph: List<String>,
    left: Float,
    top: Float,
    pixelSize: Float,
    travel: Float,
    glyphHeight: Float,
    color: Color,
    glow: Boolean,
) {
    val right = left + PixelTextMetrics.GLYPH_COLS * pixelSize
    clipRect(top = top, bottom = top + glyphHeight, left = left, right = right) {
        // 旧字整格向上滚出，新字紧跟其后从下方进来 —— 同一时刻只会看到一个完整的字形。
        drawGlyph(oldGlyph, left, top, pixelSize, color, yOffset = -travel * glyphHeight, glow = glow)
        drawGlyph(newGlyph, left, top, pixelSize, color, yOffset = (1f - travel) * glyphHeight, glow = glow)
    }
}

/**
 * 像素虚线边框：四条边上等距摆 1x1 像素点的小方块，隔一个摆一个。
 *
 * 用方块而不是 Stroke 虚线：像素风的关键是所有边的厚度都是整数个像素点，
 * 而 Compose 的虚线 pathEffect 在小尺寸下会画出半像素，放大后就是毛边。
 * 边框位置跟着 [pixelSize] 缩放，因此横竖屏都不会越界。
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPixelDashBorder(
    pixelSize: Float,
    leftIn: Float,
    topIn: Float,
    rightIn: Float,
    bottomIn: Float,
    color: Color,
) {
    // 夹回画布内：框跟着字走，但字已经贴着边时框也不能跑出屏幕。
    val left = leftIn.coerceIn(0f, size.width)
    val top = topIn.coerceIn(0f, size.height)
    val right = rightIn.coerceIn(0f, size.width)
    val bottom = bottomIn.coerceIn(0f, size.height)
    if (right <= left || bottom <= top) return
    val block = pixelSize.coerceAtLeast(1f)
    val step = block * 2f

    var x = left
    while (x + block <= right) {
        drawRect(color, Offset(x, top), Size(block, block))
        drawRect(color, Offset(x, bottom - block), Size(block, block))
        x += step
    }
    var y = top
    while (y + block <= bottom) {
        drawRect(color, Offset(left, y), Size(block, block))
        drawRect(color, Offset(right - block, y), Size(block, block))
        y += step
    }
}

// ------------------------------------------------------------------ 翻页卡片

/**
 * 3D 翻页卡片：一张被中缝切成上下两半的数字牌，值变化时上半片绕中缝向下翻转 180°。
 *
 * 叠放顺序（Compose 后画的在上），完全照搬真实翻页钟的物理层次：
 * 1. 下半片静态层：[current] 的下半截 —— 翻页牌落下时露出的就是它；
 * 2. 上半片静态层：[current] 的上半截 —— 翻页牌翻走的瞬间露出新值；
 * 3. 上半片翻页牌：[previous] 的上半截，rotationX 0 → -180，带 cameraDistance 透视，
 *    边翻边变暗，过 90° 后 alpha 归零（那时它已翻到背面，再画就是镜像）；
 * 4. 中缝与描边压在最上层，翻页过程中卡片轮廓始终清楚。
 *
 * 只有一个 [Animatable] 驱动整张卡片，**不重建渲染树**：
 * 外层 key 只认 pluginId（见 [PluginUiFullscreen]），每秒的 state 变化只是重组，
 * 动画对象靠 remember 活过每一次 tick。
 */
@Composable
private fun FlipCard(node: UiNode, paint: Paint, modifier: Modifier) {
    val current = flipTextOf(node, paint)
    // previous 是这张卡片自己记得的上一次渲染结果，不是从 state 反推的：
    // 插件改值太快也不会错位，动画永远从"屏幕上那张旧牌"开始。
    var previous by remember(node.path) { mutableStateOf(current) }
    val progress = remember(node.path) { Animatable(1f) }

    LaunchedEffect(node.path, current) {
        if (current != previous) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(UI_FLIP_DURATION_MS, easing = FastOutSlowInEasing))
            // 动画收尾才把起点挪到新值：中途被打断时，起点仍是屏幕上那张旧牌。
            previous = current
        }
    }

    val flip = progress.value
    val surface = colorOf(node.background ?: UiColor.SURFACE_VARIANT, paint.palette, paint.aod)
    val ink = colorOf(node.textColor ?: UiColor.ON_SURFACE, paint.palette, paint.aod)
    val shape = shapeOf(node.radius ?: UiRadius.CONTROL)
    val density = LocalDensity.current.density

    BoxWithConstraints(
        modifier.clip(shape).background(surface),
        contentAlignment = Alignment.Center,
    ) {
        val half: Dp = maxHeight / 2
        // 字号按"两张约束取小"定：高度上要够高，中缝才切得出上下两截；
        // 宽度上要放得下所有字符（等宽字约 0.6em），否则两位数会被裁掉一半。
        val chars = current.length.coerceAtLeast(1)
        val digitSize: TextUnit =
            minOf(maxHeight.value * 1.15f, maxWidth.value * 1.43f / chars).sp

        // 1. 下半片静态层：翻转过半才换成新值 —— 真实翻页钟的下半片是被翻页牌"盖上去"的，
        //    牌没落到底之前，下半片仍是旧值。
        FlipHalf(
            if (flip >= 0.5f) current else previous,
            top = false, half = half, digitSize = digitSize, ink = ink,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // 2. 上半片静态层：新值的上半截（旧牌翻走后露出来）。
        FlipHalf(current, top = true, half = half, digitSize = digitSize, ink = ink,
            modifier = Modifier.align(Alignment.TopCenter))

        // 3. 上半片翻页牌：旧值的上半截，绕中缝（transformOrigin 底边中点）向下翻。
        FlipHalf(
            previous,
            top = true,
            half = half,
            digitSize = digitSize,
            // 翻转过程中逐渐压暗，90° 附近最暗 —— 与真实翻页钟牌面背光一致。
            ink = lerp(ink, Color.Black, (flip * 2f).coerceIn(0f, 0.55f)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    rotationX = -180f * flip
                    cameraDistance = 12f * density
                    // 半片盒子是整卡高、只画出上半区，所以中缝在 50% 高度处，转轴也就放在那里。
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    alpha = (1f - flip * 2f).coerceIn(0f, 1f)
                },
        )

        // 4. 中缝：极细一道，Nothing 的机械感全靠它。
        HorizontalDivider(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
            thickness = DesignTokens.Border.Hairline,
            color = paint.palette.outline,
        )

        // 描边压在最上层，翻页时卡片轮廓不会糊掉。
        Box(Modifier.matchParentSize().border(DesignTokens.Border.Hairline, paint.palette.outline, shape))

        node.label?.let { label ->
            Text(
                text = label,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = DesignTokens.Spacing.Xxs),
                color = paint.palette.muted,
                fontSize = DesignTokens.Type.Micro,
                fontWeight = DesignTokens.Weight.Medium,
            )
        }
    }
}

/**
 * 翻页卡片的半片：把完整数字裁到上 50% / 下 50%。
 *
 * 关键是**两半必须严丝合缝**：所以不去拼两段文字，而是各画一次完整数字，
 * 容器永远按**整张卡片**的高度排版（文字因此不会被半片高度压扁），
 * 再用 [drawWithContent] + [clipRect] 只画上/下半区：
 * 上半片露出字形的上半截，下半片露出下半截，拼起来还是同一个字。
 */
@Composable
private fun FlipHalf(
    text: String,
    top: Boolean,
    half: Dp,
    digitSize: TextUnit,
    ink: Color,
    modifier: Modifier = Modifier,
) {
    // 容器高度恒为整张卡片：半片高度会把整行字压进半个框里，那会变成上下两个完整数字。
    val fullHeight = half * 2
    Box(
        Modifier
            .fillMaxWidth()
            .height(fullHeight)
            .drawWithContent {
                clipRect(
                    top = if (top) 0f else size.height / 2f,
                    bottom = if (top) size.height / 2f else size.height,
                ) {
                    this@drawWithContent.drawContent()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = ink,
            fontSize = digitSize,
            fontWeight = DesignTokens.Weight.Bold,
            fontFamily = FontFamily.Monospace,
            lineHeight = digitSize,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
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

/**
 * 翻页卡片的文字：它读的是 valueFrom（"值"）而不是 textFrom（"文本"）。
 * 语义上这是仪表读数，不是标签文字 —— 协议里也就只给它 valueFrom 一个来源。
 */
private fun flipTextOf(node: UiNode, paint: Paint): String = when (val value = valueOf(node.valueFrom, paint)) {
    null -> "--"
    is Double -> formatNumber(value)
    is Float -> formatNumber(value.toDouble())
    is Number -> value.toString()
    else -> value.toString()
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

/**
 * 语义色 token → 实际颜色。
 *
 * [aod] 是「这一层画在息屏底上」：息屏底的近黑不随主题变，压在它上面的字也必须固定成米白 / 灰，
 * 否则浅色主题下会得到「深字 + 深底」，整屏看不见东西。
 */
internal fun colorOf(color: UiColor, palette: AppPalette, aod: Boolean = false): Color = when (color) {
    UiColor.SURFACE -> if (aod) AOD_SCREEN else palette.surface
    UiColor.SURFACE_VARIANT -> if (aod) AOD_CARD else palette.card
    UiColor.PRIMARY -> if (aod) AOD_INK else palette.primary
    UiColor.TRANSPARENT -> Color.Transparent
    UiColor.MUTED -> if (aod) AOD_MUTED else palette.muted
    UiColor.DANGER -> DesignTokens.Danger
    UiColor.ON_SURFACE -> if (aod) AOD_INK else palette.ink
    UiColor.OUTLINE -> if (aod) AOD_OUTLINE else palette.outline
    // 息屏底色固定近黑：它不是「主题里的某个面」，而是屏幕关掉之后剩下的那点光。
    UiColor.SCREEN -> AOD_SCREEN
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

private fun paddingModifier(node: UiNode): Modifier {
    val padding = node.padding ?: return Modifier
    return Modifier.padding(padding.dp)
}

private fun sizeModifier(node: UiNode, includePadding: Boolean = true): Modifier {
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
    if (includePadding) node.padding?.let { modifier = modifier.padding(it.dp) }
    return modifier
}

/**
 * 子树继承容器的背景语义：容器画了息屏底，它里面的字就得按息屏配色画。
 * 只有 screen 会改变配色基线，其它背景标签沿用父级。
 */
private fun childPaint(paint: Paint, parent: UiNode): Paint =
    if (parent.background == UiColor.SCREEN) paint.withBackground(UiColor.SCREEN) else paint

/**
 * 容器的 background 字段：协议里一直是公共字段，但这里以前只给 card / bubble 用，
 * 写在不带 Surface 的容器上等于被静默丢弃。息屏钟要的就是「整屏近黑」，所以补上。
 */
private fun backgroundModifier(node: UiNode, paint: Paint): Modifier {
    val color = node.background ?: return Modifier
    if (color == UiColor.TRANSPARENT) return Modifier
    return Modifier.background(colorOf(color, paint.palette, paint.aod))
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

/** 息屏底色（#0F0F0D）：近黑但微微偏暖，像素字压在上面不会发灰。 */
private val AOD_SCREEN = Color(0xFF0F0F0D)

/** 息屏上的米白数字（#E8E4D8）：纯白会过曝，暖白才像老式荧光屏。 */
private val AOD_INK = Color(0xFFE8E4D8)

/** 息屏上的次要文字（#96948C）。 */
private val AOD_MUTED = Color(0xFF96948C)

/** 息屏上的低对比度描边（#4A4945）：像素虚线边框用它，不抢数字的视觉。 */
private val AOD_OUTLINE = Color(0xFF4A4945)

/** 息屏上比底略高的面（卡片 / 面板）。 */
private val AOD_CARD = Color(0xFF1A1A17)

/** 用户图片的引用前缀：ui.json 写 "src": "user:bg" 时去设置里的 bg 字段取路径。 */
private const val USER_IMAGE_PREFIX = "user:"

/** 插件包内素材：只允许包内相对路径，越界一律当作不存在。 */
private fun resolveAsset(dir: File?, path: String?): File? {
    if (dir == null || path.isNullOrBlank()) return null
    if (path.startsWith("/") || path.contains("..") || path.contains("\\") || path.contains(":")) return null
    val file = File(dir, path)
    return if (file.isFile) file else null
}

/** user: 图片的路径由宿主解析（设置里存的是插件私有目录下的绝对路径），再做一次包内边界检查。 */
private fun resolveUserAsset(dir: File?, userImages: Map<String, File>, path: String): File? {
    val key = path.removePrefix(USER_IMAGE_PREFIX)
    val file = userImages[key] ?: return null
    if (dir == null) return null
    val rootPath = dir.absolutePath
    // 只认插件自己目录里的文件：设置页理论上不会写别处的路径，这里也不允许。
    if (!file.absolutePath.startsWith(rootPath)) return null
    return if (file.isFile) file else null
}

/** 裁切方式：用户在设置里选「剪裁 / 适应 / 填充」，user: 图片默认按剪裁铺满。 */
private fun contentScaleOf(paint: Paint, path: String?): ContentScale {
    if (path == null || !path.startsWith(USER_IMAGE_PREFIX)) return ContentScale.Fit
    val key = path.removePrefix(USER_IMAGE_PREFIX)
    return when (paint.userImageFit[key]) {
        "fit" -> ContentScale.Fit
        "fill" -> ContentScale.FillBounds
        else -> ContentScale.Crop
    }
}

@Composable
private fun rememberAsset(dir: File?, path: String?, userImages: Map<String, File> = emptyMap()): ImageBitmap? {
    val file = remember(dir, path, userImages) {
        if (path != null && path.startsWith(USER_IMAGE_PREFIX)) {
            resolveUserAsset(dir, userImages, path)
        } else {
            resolveAsset(dir, path)
        }
    }
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
