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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.aiclassroom.feature.plugin.ui.PluginUiParser
import com.aiclassroom.feature.plugin.ui.UI_DISK_ROTATION_MS
import com.aiclassroom.feature.plugin.ui.UI_FLIP_DURATION_MS
import com.aiclassroom.feature.plugin.ui.UI_GLOW_BREATH_MS
import com.aiclassroom.feature.plugin.ui.UI_PIXEL_ROLL_DURATION_MS
import com.aiclassroom.feature.plugin.ui.UI_WAVEFORM_CYCLE_MS
import com.aiclassroom.feature.plugin.ui.UI_WAVEFORM_FLOOR_RATIO
import com.aiclassroom.feature.plugin.ui.UI_WAVEFORM_REST_RATIO
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
    /**
     * 从祖先继承来的水平对齐。
     *
     * 拖动把节点吸附到九宫格的哪一格，就按那里的对齐排文字 ——「位置即对齐」。
     * 节点自己写了 align 时以自己为准，没写才用这个继承值。
     */
    val inheritedAlign: UiAlign? = null,
) {
    /** 进入 / 离开一个容器的背景时，把「我在息屏底上」这个事实传给它的子树。 */
    fun withBackground(background: UiColor?): Paint {
        val next = when (background) {
            UiColor.SCREEN -> true
            null, UiColor.TRANSPARENT -> aod
            else -> false
        }
        if (next == aod) return this
        return copy(aod = next)
    }

    /** 把「这一层按哪种水平对齐排」传给子树（由可拖动节点的吸附结果决定）。 */
    fun withAlign(align: UiAlign?): Paint = if (align == inheritedAlign) this else copy(inheritedAlign = align)

    private fun copy(aod: Boolean = this.aod, inheritedAlign: UiAlign? = this.inheritedAlign): Paint = Paint(
        pluginId = pluginId,
        state = state,
        message = message,
        assets = assets,
        palette = palette,
        onEvent = onEvent,
        userImages = userImages,
        userImageFit = userImageFit,
        aod = aod,
        inheritedAlign = inheritedAlign,
    )
}

/**
 * 松手后吸附到九宫格的哪一格。
 *
 * 以「拖了多远」判断方向：超过容器 1/6 就算跨了一格，否则留在中间。
 * 用相对位移而不是绝对坐标，是因为锚点本身就承载了基准位置 ——
 * 从 TOP_LEFT 往右拖过 1/6 宽就是 TOP_CENTER，再拖就是 TOP_RIGHT。
 */
private fun snapAnchorTo(base: UiAnchor?, offset: IntOffset, width: Int, height: Int): UiAnchor {
    val startRow = when (base) {
        UiAnchor.TOP_LEFT, UiAnchor.TOP_CENTER, UiAnchor.TOP_RIGHT -> 0
        UiAnchor.CENTER_LEFT, UiAnchor.CENTER, UiAnchor.CENTER_RIGHT -> 1
        else -> 2
    }
    val startCol = when (base) {
        UiAnchor.TOP_LEFT, UiAnchor.CENTER_LEFT, UiAnchor.BOTTOM_LEFT -> 0
        UiAnchor.TOP_CENTER, UiAnchor.CENTER, UiAnchor.BOTTOM_CENTER -> 1
        else -> 2
    }
    val thresholdX = (width / 6).coerceAtLeast(1)
    val thresholdY = (height / 6).coerceAtLeast(1)
    val col = (startCol + offset.x / thresholdX).coerceIn(0, 2)
    val row = (startRow + offset.y / thresholdY).coerceIn(0, 2)
    return GRID9[row * 3 + col]
}

/**
 * 可拖动的子节点。
 *
 * 拖动状态只活在这一个节点上（remember 以节点路径为 key），不影响兄弟节点。
 * 松手吸附到九宫格后，位置改由 anchor 表达、offset 归零 —— 于是「位置即对齐」：
 * 落在左边，它内部的文字就左对齐。
 */
@Composable
private fun BoxScope.DraggableChild(node: UiNode, paint: Paint) {
    var anchor by remember(node.path) { mutableStateOf(node.anchor) }
    var offset by remember(node.path) { mutableStateOf(IntOffset(node.offsetX, node.offsetY)) }
    var scale by remember(node.path) { mutableFloatStateOf(1f) }
    val boxSize = remember(node.path) { mutableStateOf(IntSize.Zero) }
    val snapToGrid = node.snapTo == "grid9"

    // grid9 的吸附：detectTransformGestures 没有「松手」回调（它是个无限循环），
    // 所以用位移后的静默期判断手已离开。free 模式不做这一步（offset 本身就是位置）。
    LaunchedEffect(offset, snapToGrid) {
        if (!snapToGrid || offset == IntOffset.Zero) return@LaunchedEffect
        delay(300)
        val size = boxSize.value
        if (size.width > 0) {
            anchor = snapAnchorTo(anchor, offset, size.width, size.height)
            offset = IntOffset.Zero
        }
    }

    // 对齐：grid9 由吸附锚点决定；free 按横向偏移量判断自己靠哪边（超过 1/6 才换边）。
    val w = boxSize.value.width
    val align = when {
        snapToGrid || w <= 0 -> anchor.horizontalAlign()
        offset.x < -w / 6 -> UiAlign.START
        offset.x > w / 6 -> UiAlign.END
        else -> UiAlign.CENTER
    }

    // **定位与手势必须在同一个 Box 上**。
    // 之前的写法是 BoxWithConstraints(align) 里再套一个 Box(offset + 手势)，
    // 结果视觉位置和命中区域对不上：胶囊画在 y880-1290，实际能点到的却是 y1394-1905，
    // 点它没反应、反而穿透到下面的页面（实测点一下切到了「分支」页）。
    // 现在 align → offset → 尺寸 → 缩放 → 手势 全在一条链上，点哪儿就是哪儿。
    // **为什么用 `offset(x.dp, y.dp)` 而不是 `offset { IntOffset }`**：
    //
    // 后者（lambda 版）在测量**之后**才应用位移，于是「画在哪」和「点得到哪」是两个位置 ——
    // 实测红色边框画出浮窗在 y1200-1660，而 boundsInRoot 报 y1394-1905，差 245px；
    // 点浮窗没反应、反而穿透到下面的页面（点一下切到了「分支」页）。
    // 非 lambda 版在测量阶段就位移，命中区域跟着一起走，两者始终一致。
    val density = LocalDensity.current
    val dx = with(density) { offset.x.toDp() }
    val dy = with(density) { offset.y.toDp() }
    Box(
        Modifier
            .align(alignmentOf(anchor))
            .offset(x = dx, y = dy)
            .onSizeChanged { boxSize.value = it }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // 点击 / 长按：独立识别器，手指一移动它自己取消。
            .pointerInput(node.path, node.onDoubleTap) {
                detectTapGestures(
                    // 同时挂单击与双击时，单击会被延迟到双击超时之后才触发（Compose 内建行为）——
                    // 所以插件里「双击展开」的节点不要再挂 onTap，否则点击会有 300ms 延迟。
                    onDoubleTap = node.onDoubleTap?.let { entry ->
                        { paint.onEvent(PluginUiEvent(paint.pluginId, entry, "doubleTap")) }
                    },
                    onTap = node.onTap?.let { entry ->
                        { paint.onEvent(PluginUiEvent(paint.pluginId, entry, "tap")) }
                    },
                    onLongPress = node.onLongPress?.let { entry ->
                        { paint.onEvent(PluginUiEvent(paint.pluginId, entry, "longPress")) }
                    },
                )
            }
            // 拖动 + 双指缩放：一个识别器同时给平移和 zoom。
            .pointerInput(node.path, node.snapTo, node.scaleable) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (node.scaleable && zoom != 1f) {
                        scale = (scale * zoom).coerceIn(0.5f, 3f)
                    } else if (pan != Offset.Zero) {
                        offset = IntOffset(
                            (offset.x + pan.x).roundToInt(),
                            (offset.y + pan.y).roundToInt(),
                        )
                    }
                }
            },
    ) {
        PaintNode(node, paint.withAlign(align), interactive = false)
    }
}

/** 九宫格顺序：行优先。 */
private val GRID9 = listOf(
    UiAnchor.TOP_LEFT, UiAnchor.TOP_CENTER, UiAnchor.TOP_RIGHT,
    UiAnchor.CENTER_LEFT, UiAnchor.CENTER, UiAnchor.CENTER_RIGHT,
    UiAnchor.BOTTOM_LEFT, UiAnchor.BOTTOM_CENTER, UiAnchor.BOTTOM_RIGHT,
)

/** 九宫格锚点 → 水平对齐（位置即对齐）。 */
private fun UiAnchor?.horizontalAlign(): UiAlign? = when (this) {
    UiAnchor.TOP_LEFT, UiAnchor.CENTER_LEFT, UiAnchor.BOTTOM_LEFT -> UiAlign.START
    UiAnchor.TOP_CENTER, UiAnchor.CENTER, UiAnchor.BOTTOM_CENTER -> UiAlign.CENTER
    UiAnchor.TOP_RIGHT, UiAnchor.CENTER_RIGHT, UiAnchor.BOTTOM_RIGHT -> UiAlign.END
    null -> null
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
            // 每个插件一个 key：A 插件刷新不会连带把 B 插件的节点重新组合。
            //
            // key 里**不带 stateVersion**：带上它等于每次刷新都把整棵子树销毁重建，
            // 插件的 image 每秒重新解码（封面闪一下）、waveform / disk 的动画相位每秒归零
            // （ui_ticker 一秒一次，看上去就是「每秒抖一下」）。state 变更是快照读，
            // PaintNode 的 paint 参数变了自然会重组 —— 和全屏层那条注释是同一个道理。
            key(binding.pluginId) {
                // 这里**只负责按根节点的 anchor 摆位置**。
                // 拖动/缩放/吸附全部交给 PaintNode 的 BOX 分支（DraggableChild），
                // 因为 draggable 是标在子节点上的；这里再写一遍会和它叠成双重定位
                // （实测音乐浮窗因此跑到了屏幕中间而不是右下角）。
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
            key(binding.pluginId) {
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
            root != null -> key(binding.pluginId) {
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
    // 保险：插件的根节点自己不可见（所有形态的 visibleWhen 都为 false）时，
    // 绝不能继续拿一层不透明背景盖住整屏、还吃掉所有点击 —— 那时用户会觉得「界面死了」。
    // 自动让出全屏槽位，把控制权还给课堂页。
    if (!isVisible(root, paint)) {
        LaunchedEffect(binding.pluginId) { onExit() }
        return
    }
    Box(Modifier.fillMaxSize().background(palette.page)) {
        // 背景层单独吃点击：全屏层盖住整个课堂页，不挡就会误触下面的消息卡片。
        //
        // 这里**不能用 Modifier.clickable**：clickable 会把按下事件整段消费掉，
        // 于是子节点里的 detectDragGestures 永远收不到拖动手势（实测拖不动就是这个原因）。
        // 换成 detectTapGestures —— 它只在「点一下」时消费，拖动时把事件让给子节点。
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures { /* 吃掉点击，不影响拖动 */ }
                },
        )
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // 闪烁修复：key 只认 pluginId，**不要带上 stateVersion**。
            // 带上它等于每次 tick 都把整棵渲染树销毁重建（图片重新解码、动画重新开始），
            // 表现为每秒闪一下。状态变化只需触发重组（recomposition），不需要换 identity。
            key(binding.pluginId) { PaintNode(root, paint, Modifier.fillMaxSize()) }
        }
        // 首次进入全屏时给一句引导：退出按钮只有图标，不说一句没人知道要长按。
        // 4 秒后自动淡出，不常驻、不挡内容。
        FullscreenExitHint(palette)
        FullscreenExitButton(palette, onExit)
    }
}

/**
 * 进全屏后显示一次的引导文字（4 秒淡出）。
 *
 * 退出按钮按用户要求做成了「和回到底部差不多的圆形图标 + 长按画圈」，
 * 但纯图标没有可发现性 —— 第一次进来的人根本不知道右下角那个圆是干什么的，
 * 于是会觉得「全屏之后点什么都没反应」（实际是背景层在防穿透，设计如此）。
 */
@Composable
private fun BoxScope.FullscreenExitHint(palette: AppPalette) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(4000)
        visible = false
    }
    if (!visible) return
    Text(
        text = "长按右下角按钮退出",
        color = palette.muted,
        fontSize = DesignTokens.Type.Small,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(end = DesignTokens.Spacing.Md, bottom = DesignTokens.Size.Fab + DesignTokens.Spacing.Xl),
    )
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
private fun PaintNode(
    node: UiNode,
    paint: Paint,
    modifier: Modifier = Modifier,
    /**
     * 是否由这里挂点击/长按识别器。
     *
     * [DraggableChild] 要传 false —— 它自己用一套手写手势同时处理点击、长按、拖动、缩放；
     * 两套识别器叠在同一个节点上会互相抢事件：外层的 pointerInput 先消费掉 down，
     * 内层的 combinedClickable 就永远收不到点击（实测小圆点了没反应）。
     */
    interactive: Boolean = true,
) {
    if (!isVisible(node, paint)) return
    // 顺序很重要：尺寸 → 背景 → 内边距。
    // 背景压在 padding 外面，写了 padding 的整屏容器（息屏钟）才会连边都铺满颜色，
    // 而不是在四周留一圈主题白的边。
    val sized = modifier
        .then(sizeModifier(node, paint, includePadding = false))
        .then(backgroundModifier(node, paint))
        .then(paddingModifier(node))
        .then(offsetModifier(node))
        .then(if (interactive) clickModifier(node, paint) else Modifier)
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
            // 子项水平对齐跟随「位置即对齐」的继承值：拖到左边就左对齐、拖到右边就右对齐。
            // 没继承值时退回居中（这是 app 里纵向容器一贯的默认）。
            horizontalAlignment = when (paint.inheritedAlign) {
                UiAlign.START -> Alignment.Start
                UiAlign.END -> Alignment.End
                else -> Alignment.CenterHorizontally
            },
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
                val childPaint = childPaint(paint, node)
                android.util.Log.i("PluginDrag", "BOX child type=${child.type} draggable=${child.draggable} anchor=${child.anchor} path=${child.path}")
                if (child.draggable) {
                    // 可拖动子节点：位置由吸附出来的 anchor 决定，文字对齐跟着它走。
                    DraggableChild(child, childPaint)
                } else {
                    val alignment = child.anchor?.let { alignmentOf(it) } ?: Alignment.TopStart
                    Box(Modifier.align(alignment)) { PaintNode(child, childPaint) }
                }
            }
        }

        UiNodeType.CARD -> Surface(
            modifier = modifier,
            shape = shapeOf(node.radius ?: UiRadius.CONTROL),
            color = colorOf(node.background ?: UiColor.SURFACE_VARIANT, paint.palette, paint.aod),
            border = BorderStroke(DesignTokens.Border.Hairline, paint.palette.outline),
        ) {
            Column(
                // fillMaxWidth 必须写：card 的 width 是给 Surface 的，内容层不加这条就会 wrap content，
                // 于是「里层有 fill 的子项 + 外层 wrap 的容器」互相等待，宽度塌到最小 ——
                // 实测把浮窗拖到左边（对齐从 Center 变 Start）时宽度从 495px 缩到 390px。
                Modifier.fillMaxWidth().padding(horizontal = DesignTokens.Spacing.Lg, vertical = DesignTokens.Spacing.Md),
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
            // 对齐：节点自己写了就用自己；没写就跟随拖动吸附出来的继承值。
            // 这一行就是「位置即对齐」的落点 —— 时钟拖到左边自动左对齐。
            val align = when (paint.inheritedAlign) {
                UiAlign.START -> TextAlign.Start
                UiAlign.END -> TextAlign.End
                else -> TextAlign.Center
            }
            Text(
                text = text,
                modifier = if (paint.inheritedAlign != null) modifier.fillMaxWidth() else modifier,
                color = colorOf(node.textColor ?: UiColor.ON_SURFACE, paint.palette, paint.aod),
                fontSize = fontSizeOf(node.textSize),
                lineHeight = lineHeightOf(node.textSize),
                fontWeight = if (node.weight == UiWeight.BOLD) DesignTokens.Weight.Bold else DesignTokens.Weight.Regular,
                maxLines = node.maxLines ?: Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
                textAlign = align,
            )
        }

        UiNodeType.IMAGE -> {
            val sprite = node.animation as? UiAnimation.Sprite
            // user:<key> 指向用户在设置页选中的图片（已复制进插件私有目录），state.xxx 是运行期取值
            // （播放器封面），其余一律按包内相对路径解析 —— 插件拿不到这三条以外的任何文件。
            val path = if (sprite != null) spriteFrame(sprite) else imagePathOf(node, paint)
            val bitmap = rememberAsset(paint.assets, path, paint.userImages)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = modifier
                        .then(if (node.width == null) Modifier.size(48.dp) else Modifier)
                        // 写了 radius 就按圆角裁：radius 在协议里对所有组件都是同一个意思，
                        // 以前只有 card / bubble 有形状，image 上写了等于静默失效（封面只能是直角方块）。
                        .then(node.radius?.let { Modifier.clip(shapeOf(it)) } ?: Modifier),
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

        UiNodeType.WAVEFORM -> Waveform(node, paint, modifier)

        UiNodeType.DISK -> Disk(node, paint, modifier)

        UiNodeType.GLOW -> Glow(node, paint, modifier)

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

// ------------------------------------------------------- 装饰组件：waveform / disk / glow

/**
 * 节奏竖线。
 *
 * 三条实现约束（都对应协议里的要求）：
 * 1. **相位不进重组**：正弦只在 Canvas 的 draw 里算，用一个 [Animatable] 的相位值驱动，
 *    整棵树一帧一帧地「只重绘不重组」，24~48 根竖线不会每帧重建 48 个 Composable；
 * 2. **相位固定种子**：每条线的相位偏移由节点路径哈希出来，同一插件实例重建后完全一致，
 *    否则每秒 tick 一次重组就会换一批相位，看上去像在乱跳；
 * 3. **可见才跑**：用 produceState 跟着 Canvas 所在的 composition 走，节点不可见时
 *    PaintNode 直接 return，动画自然停 —— 不做常驻的后台动画循环。
 *
 * 暂停时竖线降到 [UI_WAVEFORM_REST_RATIO] 的静止高度：不归零（留一点呼吸感），也不跳。
 */
@Composable
private fun Waveform(node: UiNode, paint: Paint, modifier: Modifier) {
    val bars = (node.bars ?: PluginUiParser.DEFAULT_WAVEFORM_BARS).coerceIn(1, PluginUiParser.MAX_WAVEFORM_BARS)
    // 幅度按万分比存，渲染时折成 0..1；默认 0.35 是「微微有一点」的档位。
    val amplitude = (node.amplitudePermyriad ?: PluginUiParser.DEFAULT_WAVEFORM_AMPLITUDE_PERMYRIAD) / 10000f
    val speed = (node.speedPermyriad ?: PluginUiParser.DEFAULT_SPEED_PERMYRIAD) / 10000f
    val active = booleanOf(node.activeFrom, paint) ?: true
    val color = colorOf(node.textColor ?: UiColor.DECORATIVE, paint.palette, paint.aod)
    // 强调位置：只把首、正中、末这三根画成 accent。
    // 三根是「有节奏感但不吵」的最小剂量 —— 整排同色时眼睛没有落点，
    // 整排换成强调色又会变成视觉噪音。
    val accentColor = colorOf(UiColor.ACCENT, paint.palette, paint.aod)
    val accentIndexes: Set<Int> =
        if (node.accentAt == "edges" && bars >= 3) setOf(0, bars / 2, bars - 1) else emptySet()
    val phases = remember(node.path, bars) { waveformPhases(node.path, bars) }

    // 相位随「真实时间」推进：暂停时把相位冻在当前值，恢复播放从原处接着走，
    // 不会因为暂停过一次就把整排竖线的节奏打乱。
    val phase = remember(node.path) { Animatable(0f) }
    LaunchedEffect(node.path, active, speed) {
        if (!active) return@LaunchedEffect
        val cycleMillis = UI_WAVEFORM_CYCLE_MS / speed
        val start = phase.value
        var startedAt = 0L
        withFrameNanos { startedAt = it }
        while (true) {
            // withFrameNanos 的 lambda 不是 suspend，只能在里面读时间、在外面写状态。
            var now = startedAt
            withFrameNanos { now = it }
            // 用真实时钟算相位，而不是一帧加一个固定增量：掉帧时节奏不会越走越慢。
            phase.snapTo(start + ((now - startedAt) / 1_000_000f) * TWO_PI / cycleMillis)
        }
    }

    Canvas(modifier) {
        val stroke = WAVEFORM_BAR_WIDTH.toPx()
        val slot = size.width / bars
        val maxHeight = size.height
        val rest = maxHeight * UI_WAVEFORM_REST_RATIO
        // amplitude 是「用掉多少可用摆幅」：0 时所有线停在同一条地板上，1 时摆到容器顶。
        // 暂停时压到 rest（比地板更低），于是「暂停 = 整排降下去并静止」看得出来但不过火。
        val floor = maxHeight * UI_WAVEFORM_FLOOR_RATIO
        val span = maxHeight * (1f - UI_WAVEFORM_FLOOR_RATIO) * amplitude
        val t = phase.value
        for (index in 0 until bars) {
            // h = floor + span * (0.5 + 0.5 * sin(t + phase_i))
            val wave = 0.5f + 0.5f * kotlin.math.sin(t + phases[index])
            val height = if (active) floor + span * wave else rest
            val x = slot * index + slot / 2f
            val highlighted = index in accentIndexes
            drawLine(
                color = if (highlighted) accentColor else color,
                start = Offset(x, size.height),
                end = Offset(x, (size.height - height).coerceAtLeast(0f)),
                // 高亮的三根略粗一点，让「锚点」在不看颜色时也认得出来。
                strokeWidth = if (highlighted) stroke * 1.2f else stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** 每条竖线的相位偏移：由节点路径 + 序号哈希出来，同一实例每次重建都一致。 */
private fun waveformPhases(path: String, bars: Int): FloatArray {
    val seed = path.hashCode().toLong()
    return FloatArray(bars) { index ->
        // 末位雪崩，避免相邻序号的哈希挤在一起导致「整排同步上下」。
        var hash = seed * 31 + index * 2654435761L
        hash = hash xor (hash ushr 33)
        hash *= -0x3b3d5ac6f9e7a1b1L
        hash = hash xor (hash ushr 29)
        (((hash and 0xFFFFFF).toFloat() / 0xFFFFFF.toFloat()) * TWO_PI)
    }
}

/**
 * 唱片：圆底 + 封面裁切填满 + 中心小孔。
 *
 * 播放时匀速转（[UI_DISK_ROTATION_MS] 一圈），暂停时停在当前角度而不是弹回 0 ——
 * 「停在当前角度」正是真实唱机的行为，弹回去会让暂停看起来像重启。
 * 旋转走 graphicsLayer 的 rotationZ，只影响绘制矩阵，不触发重排与重组。
 */
@Composable
private fun Disk(node: UiNode, paint: Paint, modifier: Modifier) {
    // 直径也支持从 state 取：用户在设置里拖滑条改大小，这里每次都重新读。
    val dynSize = node.widthFrom?.let { numberOf(it, paint)?.toFloat()?.takeIf { v -> v > 0f } }
    val diameter = (dynSize ?: (node.sizeDp ?: PluginUiParser.DEFAULT_DISK_SIZE).toFloat()).dp
    val spinning = booleanOf(node.activeFrom, paint) ?: true
    // 封面来源既可以是静态图，也可以是 state.artworkPath 这种运行期取值（宿主刚落盘的封面）。
    val coverPath = node.coverFrom?.let { source ->
        if (source.startsWith("state.") || source.startsWith("msg.")) {
            valueOf(source, paint)?.toString()
        } else {
            source
        }
    }
    val cover = rememberAsset(paint.assets, coverPath, paint.userImages)
    val discColor = colorOf(node.textColor ?: UiColor.SURFACE_VARIANT, paint.palette, paint.aod)
    val holeColor = colorOf(UiColor.PRIMARY, paint.palette, paint.aod)
    val outline = colorOf(UiColor.OUTLINE, paint.palette, paint.aod)

    // 小尺寸封面球（<=80dp）不画外圈留白，也不画中心孔：那个尺度下留白就是「难看的边缘」，
    // 中心孔更是小到看不出是孔、只会让封面中间糊一个点。
    val compact = diameter <= 80.dp
    val coverRatio = if (compact) 1f else DISK_COVER_RATIO

    val angle = remember(node.path) { Animatable(0f) }
    LaunchedEffect(node.path, spinning) {
        if (!spinning) return@LaunchedEffect
        // 从当前角度接着转：暂停再播放不会跳回 0 度。
        val start = angle.value
        var startedAt = 0L
        withFrameNanos { startedAt = it }
        while (true) {
            var now = startedAt
            withFrameNanos { now = it }
            val elapsed = (now - startedAt) / 1_000_000f
            angle.snapTo((start + elapsed * 360f / UI_DISK_ROTATION_MS) % 360f)
        }
    }

    // 封面球铺满后整块就是「一张图贴在浅色底上」，太平了没有层次。
    // 补两样东西，都很轻：
    // 1) 一层很淡的投影（ambient 6% / spot 10%），让它从背景上浮起来；
    // 2) 一圈画在封面**之上**的高光描边，边缘才有「玻璃球」的厚度感。
    // 投影只给 compact 封面球加：200dp 大唱片在深色沉浸底上，加投影反而显脏。
    val bodyModifier = if (compact) {
        Modifier.shadow(
            elevation = 3.dp,
            shape = CircleShape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.06f),
            spotColor = Color.Black.copy(alpha = 0.10f),
        )
    } else {
        Modifier
    }

    Box(
        modifier
            .size(diameter)
            .then(bodyModifier)
            .graphicsLayer { rotationZ = angle.value },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val radius = size.minDimension / 2f
            drawCircle(color = discColor, radius = radius, center = center)
        }
        if (cover != null) {
            // 封面按正圆裁切填满：ContentScale.Crop 已经在 Box 里，再 clip 成圆才是唱片。
            //
            // 比例分两档：**小尺寸（悬浮窗那种几十 dp 的封面球）直接铺满**，
            // 不然 56dp 的圆里封面只占 40dp，四周一圈圆底看起来就是「旁边有很宽的边缘」，很难看；
            // 大唱片（沉浸界面 200dp 那种）才保留 0.72 的留边，那是「唱片外圈」的质感。
            Image(
                bitmap = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(diameter * coverRatio)
                    .clip(CircleShape),
            )
        }
        // 描边画在封面**之上**：铺满之后先画描边会被封面整块盖住。
        // compact 用一点浅色（白 22%）做高光，大唱片保持原来的 outline 细线。
        Canvas(Modifier.matchParentSize()) {
            val radius = size.minDimension / 2f
            val lineColor = if (compact) Color.White.copy(alpha = 0.22f) else outline
            val lineWidth = if (compact) 1.5.dp.toPx() else DesignTokens.Border.Hairline.toPx()
            drawCircle(
                color = lineColor,
                radius = radius - lineWidth / 2f,
                center = center,
                style = Stroke(width = lineWidth),
            )
        }
        // 中心小孔：有封面时它是「转轴」，没有封面时它是唯一还能看出这是张唱片的东西。
        // 小尺寸封面球不画：那个尺度下孔只有一两个像素，只会让封面中间糊一个点。
        if (!compact || cover == null) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(color = holeColor, radius = size.minDimension * DISK_HOLE_RATIO)
            }
        }
    }
}

/**
 * 光波：极低强度的径向渐变，按正弦缓慢呼吸。
 *
 * 强度默认 0.25，上限 0.6 —— 用户要的是「微小光波」，不是发光特效。
 * 渐变从中心向外到边缘渐隐，中心色 alpha = intensity，边缘恒为 0。
 */
@Composable
private fun Glow(node: UiNode, paint: Paint, modifier: Modifier) {
    val radius = (node.sizeDp ?: PluginUiParser.DEFAULT_GLOW_RADIUS).dp
    val intensity = (node.intensityPermyriad ?: PluginUiParser.DEFAULT_GLOW_INTENSITY_PERMYRIAD) / 10000f
    val color = colorOf(node.textColor ?: UiColor.DECORATIVE, paint.palette, paint.aod)

    val breath = remember(node.path) { Animatable(0f) }
    LaunchedEffect(node.path) {
        var startedAt = 0L
        withFrameNanos { startedAt = it }
        while (true) {
            var now = startedAt
            withFrameNanos { now = it }
            breath.snapTo(((now - startedAt) / 1_000_000f) / UI_GLOW_BREATH_MS)
        }
    }

    Canvas(modifier.size(radius * 2)) {
        // 呼吸：半径 ±6%、透明度在 0.75~1.0 倍强度之间来回，幅度刻意很小。
        val wave = 0.5f + 0.5f * kotlin.math.sin(breath.value * TWO_PI)
        val liveRadius = size.minDimension / 2f * (0.94f + 0.06f * wave)
        val alpha = intensity * (0.75f + 0.25f * wave)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                center = center,
                radius = liveRadius,
            ),
            radius = liveRadius,
            center = center,
        )
    }
}

/** 竖线线宽（dp）：3dp 是这个尺寸下「看得见但不成块」的宽度。 */
private val WAVEFORM_BAR_WIDTH = 3.5.dp

/** 封面相对唱片直径的比例：留一圈黑胶边，才看得出这是唱片而不是头像。 */
private const val DISK_COVER_RATIO = 0.72f

/** 中心小孔相对直径的比例。 */
private const val DISK_HOLE_RATIO = 0.06f

private const val TWO_PI = (Math.PI * 2).toFloat()

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
    // 装饰色：比「线」更轻的一档。主色是给行动按钮的，24 根竖线全用主色就成了视觉噪音，
    // 所以 waveform / disk / glow 的默认色都落在这里 —— 它仍然是主题色推导出来的，
    // 换主题时跟着变，只是不抢视线。
    UiColor.DECORATIVE -> if (aod) AOD_OUTLINE else lerp(palette.muted, palette.outline, 0.55f)
    // 强调色：必须与主色拉开差距，否则「强调」是空话。
    // 默认主题里 palette.accent 与 palette.primary 是同一个值（都取 button 色），
    // 直接返回的话插件作者写了 accent 却看不出任何变化。
    // 这里做一次推导：优先用主题的 secondary；它与 primary 相同时按明度往反方向推一档
    // （浅色底加深、深色底提亮），保证 accent 永远是个看得见的锚点。
    // 只影响插件渲染，不动全局 palette（它还挂在 Material3 的 tertiary 上）。
    UiColor.ACCENT -> {
        val base = palette.secondary
        if (base == palette.primary) {
            // 感知亮度（BT.601 权重），够判断「这颜色偏亮还是偏暗」。
            val lum = 0.299f * base.red + 0.587f * base.green + 0.114f * base.blue
            if (lum > 0.5f) lerp(base, Color.Black, 0.32f) else lerp(base, Color.White, 0.32f)
        } else {
            base
        }
    }
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
    UiIcon.MUSIC -> Icons.Default.MusicNote
    UiIcon.PLAY -> Icons.Default.PlayArrow
    UiIcon.PAUSE -> Icons.Default.Pause
    UiIcon.PREV -> Icons.Default.SkipPrevious
    UiIcon.NEXT -> Icons.Default.SkipNext
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

private fun sizeModifier(node: UiNode, paint: Paint, includePadding: Boolean = true): Modifier {
    var modifier: Modifier = Modifier
    // 动态尺寸优先：插件把「用户在设置里调的大小」塞进 state，这里每次重组都重新取。
    val dynWidth = node.widthFrom?.let { numberOf(it, paint)?.toFloat()?.takeIf { v -> v > 0f } }
    val dynHeight = node.heightFrom?.let { numberOf(it, paint)?.toFloat()?.takeIf { v -> v > 0f } }
    if (dynWidth != null) {
        modifier = modifier.width(dynWidth.dp)
    } else {
        when (val length = node.width) {
            UiLength.Fill -> modifier = modifier.fillMaxWidth()
            UiLength.Wrap, null -> Unit
            is UiLength.Fixed -> modifier = modifier.width(length.dp.dp)
        }
    }
    if (dynHeight != null) {
        modifier = modifier.height(dynHeight.dp)
    } else {
        when (val length = node.height) {
            UiLength.Fill -> modifier = modifier.fillMaxHeight()
            UiLength.Wrap, null -> Unit
            is UiLength.Fixed -> modifier = modifier.height(length.dp.dp)
        }
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
    val fill = Modifier.background(colorOf(color, paint.palette, paint.aod))
    // 容器上的 radius 同样要生效：以前只有 card / bubble 自带形状，
    // box 上写 radius 是静默失效的（封面占位块就只能是个直角方块）。
    return node.radius?.let { Modifier.clip(shapeOf(it)).then(fill) } ?: fill
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

/**
 * 宿主媒体封面：插件目录是 <filesDir>/plugins/<id>，所以 <filesDir> 是它的上两级。
 * 只放行 <filesDir>/images 里的文件，防止插件借 artworkPath 读到宿主私有目录里别的东西。
 */
private fun resolveHostMediaAsset(dir: File?, path: String): File? {
    if (dir == null) return null
    val filesDir = dir.parentFile?.parentFile ?: return null
    val mediaDir = File(filesDir, "images")
    val file = File(path)
    if (!file.absolutePath.startsWith(mediaDir.absolutePath + File.separator)) return null
    return if (file.isFile) file else null
}

/**
 * image 的 src 现取一次值：包内相对路径 / user:<设置项> 原样返回，
 * `state.xxx` / `msg.xxx` 取当前 state 里的字符串（封面路径）。
 */
private fun imagePathOf(node: UiNode, paint: Paint): String? {
    val src = node.src ?: return null
    if (!src.startsWith("state.") && !src.startsWith("msg.")) return src
    return valueOf(src, paint)?.toString()?.takeIf { it.isNotBlank() }
}

/** 裁切方式：用户在设置里选「剪裁 / 适应 / 填充」，user: 图片默认按剪裁铺满。 */
private fun contentScaleOf(paint: Paint, path: String?): ContentScale {
    // 宿主刚落盘的封面是绝对路径：正方形封面框里要铺满，Fit 会在两边留黑边。
    if (path != null && path.startsWith("/")) return ContentScale.Crop
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
        when {
            path == null -> null
            path.startsWith(USER_IMAGE_PREFIX) -> resolveUserAsset(dir, userImages, path)
            // 宿主刚落盘的封面（media.artworkPath）是绝对路径：它在插件目录之外，
            // 所以单独放行 —— 但只认 <filesDir>/images 这一个目录，别的绝对路径一律当不存在。
            path.startsWith("/") -> resolveHostMediaAsset(dir, path)
            else -> resolveAsset(dir, path)
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
