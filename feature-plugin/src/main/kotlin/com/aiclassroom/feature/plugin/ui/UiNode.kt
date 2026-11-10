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

    /** 分隔线 / 描边色：像素虚线边框这种「低对比度装饰」用它。 */
    OUTLINE("outline"),

    /**
     * 「弱化装饰色」：waveform / disk / glow 这三个装饰组件的默认色。
     *
     * 它和 OUTLINE 在浅色主题下是同一个值，但语义不同 —— outline 是「线」，decorative 是
     * 「不抢视线的音律装饰」。放在这里而不是让组件各自默认 primary：
     * 用户要的是「微微有一点」的节奏感，满饱和度的强调色满屏画 24 根竖线会变成视觉噪音。
     */
    DECORATIVE("decorative"),

    /**
     * 息屏底色（近黑）。息屏时钟要的是「关掉屏幕还剩一点光」的那种黑，
     * 面板色 surface 在这种场景下偏亮，会把像素字衬成灰蒙蒙一片。
     */
    SCREEN("screen"),

    /**
     * 强调色：主题里的「点缀色」（palette.accent）。
     *
     * 与 PRIMARY 的分工：primary 是主操作色（按钮底、进度填充），
     * accent 是视觉锚点 —— 一屏只用几处，把视线钉在关键位置。
     * 用多了就没有强调的意义，所以协议层不设任何默认值，必须显式引用。
     */
    ACCENT("accent"),
    ;

    companion object {
        fun fromWire(wire: String): UiColor? = entries.firstOrNull { it.wire == wire }

        /** background 允许的取值。 */
        val BACKGROUNDS: List<UiColor> = listOf(SURFACE, SURFACE_VARIANT, PRIMARY, TRANSPARENT, SCREEN)

        /** color（前景）允许的取值。 */
        val FOREGROUNDS: List<UiColor> = listOf(PRIMARY, MUTED, DANGER, ON_SURFACE, OUTLINE, DECORATIVE, ACCENT)

        /**
         * 唱片底色的允许取值：surfaceVariant / primary 这些「面」和「装饰色」它都要能取 ——
         * 没有封面时黑胶底是 surfaceVariant，有封面时中心孔是 primary，两者都得说得出话。
         */
        val DECORATION_SURFACES: List<UiColor> = (FOREGROUNDS + BACKGROUNDS).distinct()

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
    STAR("star"), HEART("heart"), SETTINGS("settings"), PUBLIC("public"), SEND("send"),

    // 音乐插件要的五个：没有它们，播放器就只能退化成一排汉字按钮。
    MUSIC("music"), PLAY("play"), PAUSE("pause"), PREV("prev"), NEXT("next");

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

/** 水平对齐 token：pixeltext 的文字块在自己盒子里的对齐方式。 */
enum class UiAlign(val wire: String) {
    START("start"), CENTER("center"), END("end");

    companion object {
        fun fromWire(wire: String): UiAlign? = entries.firstOrNull { it.wire == wire }
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

/** 边框样式 token：目前只有「像素虚线」。 */
enum class UiBorder(val wire: String) {
    DASHED("dashed");

    companion object {
        fun fromWire(wire: String): UiBorder? = entries.firstOrNull { it.wire == wire }
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

    /**
     * 3D 翻页卡片：一个被中缝切成上下两半的数字，valueFrom 变化时上半片绕中缝向下翻转。
     * 叶组件（不装子节点）—— 它要画的是"一张被切开又翻过去的牌"，子节点在这一层没有意义。
     */
    FLIP_CARD("flipcard"),

    /**
     * 像素点阵文字：5x7 点阵逐像素画方块，不用字体。
     * 尺寸按容器宽高自适应（见 PixelTextMetrics），横竖屏都不会把字挤出屏幕。
     */
    PIXEL_TEXT("pixeltext"),

    /**
     * 节奏竖线：一组圆头竖线按正弦相位起伏，模拟播放器的音频律动。
     *
     * 纯正弦模拟，不接 Visualizer / 不申请录音权限 —— 那会多一个运行时权限弹窗，
     * 而这里要的只是「和歌一起轻轻呼吸」的观感。
     */
    WAVEFORM("waveform"),

    /** 唱片：圆形黑胶 + 居中封面裁切，播放时匀速旋转、暂停停在当前角度。 */
    DISK("disk"),

    /** 光波：极低强度的径向渐变光晕，按正弦缓慢呼吸。 */
    GLOW("glow"),
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

/** 翻页卡片的翻转时长（毫秒）；协议不开放给插件，避免每个插件翻得不一样快。 */
const val UI_FLIP_DURATION_MS: Int = 350

/** 像素滚动动画的时长（毫秒）：变化的字符垂直滚过一格。同样不开放给插件。 */
const val UI_PIXEL_ROLL_DURATION_MS: Int = 180

/**
 * waveform 一个完整起伏周期的时长（毫秒）：正弦相位走完 2π。
 *
 * 取 2.6 秒是照着 60–90 BPM 的慢歌定的 —— 一周期略长于一拍会让竖线「跟着歌走」，
 * 而不是像均衡器那样比歌快。插件只能按倍数缩放（speed），改不了这个基数，
 * 免得每个插件跳出完全不同的节奏感。
 */
const val UI_WAVEFORM_CYCLE_MS: Int = 2600

/** waveform 暂停后的静止基准高度占比：不归零，留一点呼吸感，但完全不跳。 */
const val UI_WAVEFORM_REST_RATIO: Float = 0.18f

/**
 * waveform 播放时竖线的最低高度占比（摆动的地板）。
 *
 * amplitude 是「用掉多少可用摆幅」：amplitude=0 时所有竖线停在这条地板线上，
 * amplitude=1 时从地板一直摆到容器顶。默认 0.35 因此只有约 1/4 容器高的起伏 ——
 * 用户要的是「微微有一点」，而不是均衡器那种满幅跳动。
 */
const val UI_WAVEFORM_FLOOR_RATIO: Float = 0.25f

/** disk 转一圈的时长（毫秒）：33 转黑胶约 1.8 秒，但屏幕上那太快，8 秒是肉眼舒服的匀速。 */
const val UI_DISK_ROTATION_MS: Int = 8000

/** glow 呼吸一个周期的时长（毫秒）：3 秒，比 waveform 更慢，两层动画不会互相打架。 */
const val UI_GLOW_BREATH_MS: Int = 3000

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

    /**
     * 这个节点可以被手指拖动。
     *
     * 拖动结果会**替换** [anchor] / [offsetX] / [offsetY]，并持久化到宿主 prefs
     * （键由 pluginId + 节点路径组成），下次启动仍在原位。
     * 只有 GLOBAL_OVERLAY / FULLSCREEN 这类「自由摆放」的插槽里才有意义。
     */
    val draggable: Boolean = false,

    /**
     * 松手后的吸附方式：目前只认 "grid9" —— 吸附到九宫格里最近的一格。
     *
     * 吸附的是**锚点**，不是像素坐标：拖到左上就变成 TOP_LEFT，拖到正中就变成 CENTER。
     * 于是「位置即对齐」—— 文字的左/中/右对齐不需要额外配置项，落在哪边就按哪边排。
     */
    val snapTo: String? = null,

    /**
     * 允许双指捏合缩放（0.5×..3×，围绕节点中心）。
     *
     * 与 [draggable] 共用同一套手势识别（detectTransformGestures 同时给平移和缩放），
     * 所以只写 scaleable 不写 draggable 时解析会报错。
     */
    val scaleable: Boolean = false,
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
    // image：包内相对路径 / user:<设置项>，或 state.xxx / msg.xxx 运行期取值
    val src: String? = null,
    // icon
    val name: UiIcon? = null,
    val iconSize: Int? = null,
    // button
    val label: String? = null,
    val style: UiButtonStyle? = null,
    // pixeltext
    /** 每个像素点的 dp 大小；不给就按容器宽高自适应（横竖屏不崩的关键）。 */
    val pixelSize: Int? = null,
    val align: UiAlign? = null,
    val scanlines: Boolean? = null,
    val glow: Boolean? = null,
    /** 像素虚线边框；null 表示不画边框。 */
    val border: UiBorder? = null,
    val borderColor: UiColor? = null,
    // switch / slider / progress
    val checkedFrom: String? = null,
    val valueFrom: String? = null,
    val min: Int? = null,
    val max: Int? = null,
    val onChange: String? = null,
    // waveform / disk / glow —— 三个装饰组件共用一组「数值参数」，
    // 一律存成整数（万分比 / 千分比），协议层不出现浮点，解析与单测才好断言。
    /** waveform 竖线条数（1..[PluginUiParser.MAX_WAVEFORM_BARS]）。 */
    val bars: Int? = null,
    /** waveform 起伏幅度，万分比 0..10000（0.35 → 3500）。 */
    val amplitudePermyriad: Int? = null,
    /** waveform 速度倍率，万分比 1000..50000（1.0 → 10000）。 */
    val speedPermyriad: Int? = null,
    /** disk 直径 dp / glow 半径 dp。 */
    val sizeDp: Int? = null,
    /** glow 强度，万分比 0..10000（0.25 → 2500）。 */
    val intensityPermyriad: Int? = null,
    /** disk 的封面来源，与 image 的 src 同一套写法（包内相对路径 / user:<设置项>）。 */
    val coverFrom: String? = null,
    /** disk 何时旋转；waveform 何时起伏。读 state.xxx / msg.xxx 的布尔取值。 */
    val activeFrom: String? = null,

    /**
     * waveform 的强调位置：把少数几根竖线画成 [UiColor.ACCENT]。
     *
     * 目前只开放 "edges" —— 首、正中、末三根。这是「有节奏感但不吵」的最小剂量：
     * 满屏同色时眼睛找不到落点，全换成强调色又会变成噪音，三根正好。
     */
    val accentAt: String? = null,
)

/** 解析结果：成功时 [root] 非空且 [errors] 为空；失败时 [root] 为 null，[errors] 逐条可读。 */
data class UiParseResult(val root: UiNode?, val errors: List<String>) {
    val ok: Boolean get() = root != null && errors.isEmpty()
}
