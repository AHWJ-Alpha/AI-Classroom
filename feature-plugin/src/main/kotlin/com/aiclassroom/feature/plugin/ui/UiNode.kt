package com.aiclassroom.feature.plugin.ui

/**
 * ui.json 的数据模型（纯数据，无 Android 依赖，可在 JVM 单测里直接断言）。
 *
 * 设计原则：**一切视觉取值都只能是语义 token**。
 * 颜色、字号、字重、圆角、图标名全部是枚举，协议里根本没有接受任意色值 / 任意像素字号的字段，
 * 这样插件作者拿不到「把界面染成荧光绿」的能力，成品必然落在宿主既有设计语言内。
 */

/** 颜色 token。背景允许 surface|surfaceVariant|primary|transparent，前景允许 primary|muted|danger|onSurface。 */
enum class UiColor(val wire: String) {
    SURFACE("surface"),
    SURFACE_VARIANT("surfaceVariant"),
    PRIMARY("primary"),
    TRANSPARENT("transparent"),
    MUTED("muted"),
    DANGER("danger"),
    ON_SURFACE("onSurface"),
    ;

    companion object {
        fun fromWire(wire: String): UiColor? = entries.firstOrNull { it.wire == wire }

        /** background 允许的取值。 */
        val BACKGROUNDS: List<UiColor> = listOf(SURFACE, SURFACE_VARIANT, PRIMARY, TRANSPARENT)

        /** color（前景）允许的取值。 */
        val FOREGROUNDS: List<UiColor> = listOf(PRIMARY, MUTED, DANGER, ON_SURFACE)

        val backgroundWires: List<String> = BACKGROUNDS.map { it.wire }
        val foregroundWires: List<String> = FOREGROUNDS.map { it.wire }
    }
}

/** 字号 token。 */
enum class UiTextSize(val wire: String) {
    TITLE("title"), SUBTITLE("subtitle"), BODY("body"), CAPTION("caption");

    companion object {
        fun fromWire(wire: String): UiTextSize? = entries.firstOrNull { it.wire == wire }
        val wireNames: List<String> = entries.map { it.wire }
    }
}

/** 字重 token。 */
enum class UiWeight(val wire: String) {
    NORMAL("normal"), BOLD("bold");

    companion object {
        fun fromWire(wire: String): UiWeight? = entries.firstOrNull { it.wire == wire }
        val wireNames: List<String> = entries.map { it.wire }
    }
}

/** 圆角 token。 */
enum class UiRadius(val wire: String) {
    CARD("card"), CONTROL("control"), PANEL("panel"), FULL("full");

    companion object {
        fun fromWire(wire: String): UiRadius? = entries.firstOrNull { it.wire == wire }
        val wireNames: List<String> = entries.map { it.wire }
    }
}

/** 图标 token：只给宿主已确认的一组图标，插件不能自造。 */
enum class UiIcon(val wire: String) {
    STAR("star"), HEART("heart"), SETTINGS("settings"), PUBLIC("public"), SEND("send");

    companion object {
        fun fromWire(wire: String): UiIcon? = entries.firstOrNull { it.wire == wire }
        val wireNames: List<String> = entries.map { it.wire }
    }
}

/** 按钮样式 token。 */
enum class UiButtonStyle(val wire: String) {
    PRIMARY("primary"), SECONDARY("secondary"), TEXT("text");

    companion object {
        fun fromWire(wire: String): UiButtonStyle? = entries.firstOrNull { it.wire == wire }
        val wireNames: List<String> = entries.map { it.wire }
    }
}

/** 锚点（仅对挂在 overlay 上的根节点有意义）。 */
enum class UiAnchor(val wire: String) {
    TOP_LEFT("top_left"),
    TOP_CENTER("top_center"),
    TOP_RIGHT("top_right"),
    CENTER_LEFT("center_left"),
    CENTER("center"),
    CENTER_RIGHT("center_right"),
    BOTTOM_LEFT("bottom_left"),
    BOTTOM_CENTER("bottom_center"),
    BOTTOM_RIGHT("bottom_right"),
    ;

    companion object {
        fun fromWire(wire: String): UiAnchor? = entries.firstOrNull { it.wire == wire }
        val wireNames: List<String> = entries.map { it.wire }
    }
}

/** 组件类型。 */
enum class UiNodeType(val wire: String) {
    STACK("stack"),
    COLUMN("column"),
    ROW("row"),
    BOX("box"),
    CARD("card"),
    SPACER("spacer"),
    DIVIDER("divider"),
    TEXT("text"),
    IMAGE("image"),
    ICON("icon"),
    BUTTON("button"),
    SWITCH("switch"),
    SLIDER("slider"),
    PROGRESS("progress"),
    BUBBLE("bubble"),
    ;

    /** 能装子节点的容器。 */
    val isContainer: Boolean get() = this in CONTAINERS

    companion object {
        fun fromWire(wire: String): UiNodeType? = entries.firstOrNull { it.wire == wire }

        val CONTAINERS: Set<UiNodeType> = setOf(STACK, ROW, COLUMN, BOX, CARD, BUBBLE)
        val wireNames: List<String> = entries.map { it.wire }
    }
}

/** 宽高：fill / wrap / 固定 dp。 */
sealed class UiLength {
    object Fill : UiLength()

    object Wrap : UiLength()

    data class Fixed(val dp: Int) : UiLength()
}

/** 动画描述。fade / scale / slide 是入场动画，sprite 是循环切帧。 */
sealed class UiAnimation {
    abstract val wire: String

    abstract val durationMs: Int

    data class Fade(override val durationMs: Int = 300) : UiAnimation() {
        override val wire: String get() = "fade"
    }

    data class Scale(override val durationMs: Int = 300) : UiAnimation() {
        override val wire: String get() = "scale"
    }

    data class Slide(override val durationMs: Int = 300) : UiAnimation() {
        override val wire: String get() = "slide"
    }

    data class Sprite(val frames: List<String>, val fps: Int) : UiAnimation() {
        override val wire: String get() = "sprite"
        override val durationMs: Int get() = if (fps <= 0) 0 else 1000 / fps
    }
}

/**
 * 一个已解析的界面节点。[path] 记录它在 ui.json 里的位置（如 `root.children[1].children[0]`），
 * 既是报错定位的依据，也让单测能直接断言「错在哪一个节点」。
 */
data class UiNode(
    val type: UiNodeType,
    val path: String,
    val anchor: UiAnchor? = null,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val padding: Int? = null,
    val gap: Int? = null,
    val width: UiLength? = null,
    val height: UiLength? = null,
    val visibleWhen: String? = null,
    val background: UiColor? = null,
    val radius: UiRadius? = null,
    val animation: UiAnimation? = null,
    val onTap: String? = null,
    val onLongPress: String? = null,
    val children: List<UiNode> = emptyList(),
    // text
    val text: String? = null,
    val textFrom: String? = null,
    val textSize: UiTextSize? = null,
    val textColor: UiColor? = null,
    val weight: UiWeight? = null,
    val maxLines: Int? = null,
    // image
    val src: String? = null,
    // icon
    val name: UiIcon? = null,
    val iconSize: Int? = null,
    // button
    val label: String? = null,
    val style: UiButtonStyle? = null,
    // switch / slider / progress
    val checkedFrom: String? = null,
    val valueFrom: String? = null,
    val min: Int? = null,
    val max: Int? = null,
    val onChange: String? = null,
)

/** 解析结果：成功时 [root] 非空且 [errors] 为空；失败时 [root] 为 null，[errors] 逐条可读。 */
data class UiParseResult(val root: UiNode?, val errors: List<String>) {
    val ok: Boolean get() = root != null && errors.isEmpty()
}
