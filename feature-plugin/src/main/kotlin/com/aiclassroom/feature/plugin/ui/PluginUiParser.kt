package com.aiclassroom.feature.plugin.ui

import com.aiclassroom.feature.plugin.Json
import com.aiclassroom.feature.plugin.JsonNode
import com.aiclassroom.feature.plugin.JsonParseException
import com.aiclassroom.feature.plugin.PluginLimits
import com.aiclassroom.feature.plugin.PluginPackage

/**
 * ui.json 的严格解析器（纯 JVM，无 Android 依赖）。
 *
 * 三条硬规则：
 * 1. **只认语义 token**：颜色 / 字号 / 字重 / 圆角 / 图标 / 按钮样式全部按枚举校验，
 *    写 `"color": "#FF00FF"` 会被明确拒绝，而不是被渲染器悄悄忽略；
 * 2. **错误必须可读**：每个错误都带节点路径（`root.children[1].children[0].color`）与字段名，
 *    一次收集全部错误，插件作者一轮就能改完；
 * 3. **防爆炸**：嵌套深度 <= [MAX_DEPTH]、节点总数 <= [MAX_NODES]，超限直接判死，不做部分渲染。
 *
 * 未知字段也算错误：拼错一个字段名（`"colr"`）不应该变成静默失效。
 */
object PluginUiParser {

    /** ui.json 里 `"src": "user:<key>"` 的前缀：取用户在设置页选中的图片。 */
    const val USER_IMAGE_PREFIX = "user:"

    /** 最大嵌套深度（根节点记为第 1 层）。 */
    const val MAX_DEPTH = PluginLimits.MAX_UI_DEPTH

    /** 单份 ui.json 的最大节点数。 */
    const val MAX_NODES = PluginLimits.MAX_UI_NODES

    /** pixeltext 单个像素点的上限（dp）：再大就不是像素风，而是一个方块。 */
    const val MAX_PIXEL_SIZE = 64

    /** waveform 竖线条数上限：再多在手机上就细到看不见，纯粹是白耗 GPU。 */
    const val MAX_WAVEFORM_BARS = 48

    /** waveform 竖线条数的默认值。 */
    const val DEFAULT_WAVEFORM_BARS = 24

    /** waveform 容器高的默认值（dp）。 */
    const val DEFAULT_WAVEFORM_HEIGHT = 48

    /** waveform 起伏幅度默认值的万分比：0.35。用户要的是「微微有一点」。 */
    const val DEFAULT_WAVEFORM_AMPLITUDE_PERMYRIAD = 3500

    /** waveform 起伏幅度的上限（万分比）：1.0。超过 1 会让竖线溢出容器。 */
    const val MAX_AMPLITUDE_PERMYRIAD = 10000

    /** waveform 速度倍率默认为 1.0 倍。 */
    const val DEFAULT_SPEED_PERMYRIAD = 10000

    /** waveform 速度倍率下限：0.1 倍（再慢就看不出在动）。 */
    const val MIN_SPEED_PERMYRIAD = 1000

    /** waveform 速度倍率上限（万分比）：5.0 倍。再快就是闪。 */
    const val MAX_SPEED_PERMYRIAD = 50000

    /** disk / glow 尺寸的上限（dp）：再大就不是装饰，是一块盖住内容的色斑。 */
    const val MAX_DECORATION_SIZE = 480

    /** disk / glow 尺寸的下限（dp）：小到这个份上就只剩一个点，参数写错了。 */
    const val MIN_DECORATION_SIZE = 8

    /** disk 直径的默认值（dp）。 */
    const val DEFAULT_DISK_SIZE = 160

    /** glow 半径的默认值（dp）。 */
    const val DEFAULT_GLOW_RADIUS = 120

    /** glow 强度上限（万分比）：0.6 以上就从「光波」变成「发光特效」，不是用户要的。 */
    const val MAX_GLOW_INTENSITY_PERMYRIAD = 6000

    /** glow 强度的默认值（万分比）：0.25。 */
    const val DEFAULT_GLOW_INTENSITY_PERMYRIAD = 2500

    private val EVENT_NAME = Regex("^[A-Za-z_$][A-Za-z0-9_$]*$")

    /** 表达式只支持 `state.xxx` / `msg.xxx` 的单层取值，不做完整表达式求值。 */
    private val VALUE_EXPRESSION = Regex("^(?:state|msg)[.][A-Za-z_$][A-Za-z0-9_$]*$")

    private val COMMON_FIELDS: Set<String> = setOf(
        "type", "anchor", "offsetX", "offsetY", "padding", "gap",
        "width", "height", "visibleWhen", "background", "radius", "animation",
        "onTap", "onLongPress",
    )

    /** 每种组件允许的字段：多写一个未知字段会报错，避免拼错的字段静默失效。 */
    private fun fieldsFor(type: UiNodeType): Set<String> = when (type) {
        UiNodeType.STACK, UiNodeType.ROW, UiNodeType.COLUMN, UiNodeType.BOX,
        UiNodeType.CARD, UiNodeType.BUBBLE,
        -> COMMON_FIELDS + "children"

        UiNodeType.TEXT -> COMMON_FIELDS + setOf("text", "textFrom", "size", "color", "weight", "maxLines")
        UiNodeType.IMAGE -> COMMON_FIELDS + "src"
        UiNodeType.ICON -> COMMON_FIELDS + setOf("name", "size", "color")
        UiNodeType.BUTTON -> COMMON_FIELDS + setOf("label", "style")
        UiNodeType.SWITCH -> COMMON_FIELDS + setOf("checkedFrom", "onChange")
        UiNodeType.SLIDER -> COMMON_FIELDS + setOf("valueFrom", "min", "max", "onChange")
        UiNodeType.PROGRESS -> COMMON_FIELDS + setOf("valueFrom", "color")
        // flipcard 的字体大小由卡片高度决定（数字必须撑满中缝），因此没有 size 字段；
        // textColor 是 color 的同义写法，两种都收，插件作者不用猜该写哪个。
        UiNodeType.FLIP_CARD -> COMMON_FIELDS + setOf("valueFrom", "color", "textColor", "label")
        // pixeltext 的尺寸给不给都能画：给了就当上限，不给按容器自适应。
        UiNodeType.PIXEL_TEXT -> COMMON_FIELDS +
            setOf(
                "text", "textFrom", "pixelSize", "color", "textColor",
                "align", "scanlines", "glow", "border", "borderColor",
            )
        // 三个装饰组件：color 必须走语义色（FOREGROUNDS），数值参数一律有上下限。
        UiNodeType.WAVEFORM -> COMMON_FIELDS + setOf("bars", "color", "amplitude", "speed", "activeFrom", "accentAt")
        // disk 的直径用 size；半径 dp 的用户语义在 disk 上不成立，所以它没有 radius。
        UiNodeType.DISK -> COMMON_FIELDS + setOf("size", "color", "coverFrom", "spinningFrom")
        // glow 的 radius 是「光的半径 dp」，与公共字段里那个圆角 token 同名不同义：
        // 在 glow 上按数字解析（见 radiusField），圆角对它没有落点，因此这里不再接受
        // card|control|panel|full 这套取值。
        UiNodeType.GLOW -> (COMMON_FIELDS - "radius") + setOf("radius", "color", "intensity")
        UiNodeType.SPACER, UiNodeType.DIVIDER -> COMMON_FIELDS
    }

    /**
     * 解析 ui.json 文本。顶层可以是一个节点对象，也可以是 `{"root": {...}}`。
     * 失败时 [UiParseResult.root] 为 null —— 宁可什么都不画，也不画半个坏界面。
     */
    fun parse(text: String?): UiParseResult {
        if (text == null || text.isBlank()) return UiParseResult(null, listOf("ui.json 内容为空"))
        val parsed = try {
            Json.parse(text)
        } catch (e: JsonParseException) {
            return UiParseResult(null, listOf("ui.json 不是合法 JSON：" + e.message))
        }
        val root = parsed as? JsonNode.Obj
            ?: return UiParseResult(null, listOf("ui.json 顶层必须是对象，实际是 " + describe(parsed)))
        val nodeObject = root.fields["root"] as? JsonNode.Obj ?: root
        val state = ParseState()
        val node = parseNode(nodeObject, "root", 1, state)
        if (state.errors.isEmpty() && node == null) state.errors += "root 缺少字符串字段 type"
        return UiParseResult(if (state.errors.isEmpty()) node else null, state.errors)
    }

    private class ParseState {
        val errors = mutableListOf<String>()
        var nodes = 0
        var nodeBudgetReported = false
    }

    private fun parseNode(node: JsonNode.Obj, path: String, depth: Int, state: ParseState): UiNode? {
        if (depth > MAX_DEPTH) {
            state.errors += path + " 超过最大嵌套深度 " + MAX_DEPTH + " 层"
            return null
        }
        state.nodes++
        if (state.nodes > MAX_NODES) {
            if (!state.nodeBudgetReported) {
                state.nodeBudgetReported = true
                state.errors += path + " 起节点数超过上限 " + MAX_NODES + " 个"
            }
            return null
        }
        val errors = state.errors
        val typeName = node.fields["type"]?.asStringOrNull
        if (typeName == null) {
            errors += path + " 缺少字符串字段 type"
            return null
        }
        val type = UiNodeType.fromWire(typeName)
        if (type == null) {
            errors += path + ".type 未知组件 '" + typeName + "'（允许：" + UiNodeType.wireNames.joinToString(", ") + "）"
            return null
        }
        val allowed = fieldsFor(type)
        for (key in node.fields.keys) {
            if (key !in allowed) {
                errors += path + "." + key + " 不是 " + type.wire + " 支持的字段（允许：" +
                    allowed.sorted().joinToString(", ") + "）"
            }
        }

        val children = if (type.isContainer) parseChildren(node, path, depth, state) else emptyList()

        return UiNode(
            type = type,
            path = path,
            anchor = anchorField(node, path, errors),
            offsetX = intField(node, "offsetX", path, errors, 0) ?: 0,
            offsetY = intField(node, "offsetY", path, errors, 0) ?: 0,
            padding = intField(node, "padding", path, errors, null, 0),
            gap = intField(node, "gap", path, errors, null, 0),
            width = lengthField(node, "width", path, errors),
            height = lengthField(node, "height", path, errors),
            visibleWhen = expressionField(node, "visibleWhen", path, errors),
            background = colorField(node, "background", path, errors, UiColor.BACKGROUNDS),
            // glow 的 radius 是「光的半径 dp」，不是圆角 token；其余组件照旧按枚举解析。
            radius = if (type == UiNodeType.GLOW) {
                null
            } else {
                enumField(node, "radius", path, errors, UiRadius.wireNames, UiRadius::fromWire)
            },
            animation = animationField(node, path, errors),
            onTap = eventField(node, "onTap", path, errors),
            onLongPress = eventField(node, "onLongPress", path, errors),
            children = children,
            text = stringField(node, "text", path, errors),
            textFrom = expressionField(node, "textFrom", path, errors),
            textSize = if (type == UiNodeType.TEXT) {
                enumField(node, "size", path, errors, UiTextSize.wireNames, UiTextSize::fromWire)
            } else {
                null
            },
            textColor = when (type) {
                UiNodeType.TEXT, UiNodeType.ICON, UiNodeType.PROGRESS,
                UiNodeType.WAVEFORM, UiNodeType.GLOW,
                ->
                    colorField(node, "color", path, errors, UiColor.FOREGROUNDS)

                // 唱片的 color 是「黑胶底色」：没有封面时它是 surfaceVariant，
                // 所以这里额外放行 BACKGROUNDS 里的面色，但仍然只认语义 token。
                UiNodeType.DISK -> colorField(node, "color", path, errors, UiColor.DECORATION_SURFACES)

                // flipcard / pixeltext 接受 color / textColor 两种写法，同时给且不一致才算错。
                UiNodeType.FLIP_CARD, UiNodeType.PIXEL_TEXT -> {
                    val primary = colorField(node, "color", path, errors, UiColor.FOREGROUNDS)
                    val alias = colorField(node, "textColor", path, errors, UiColor.FOREGROUNDS)
                    if (primary != null && alias != null && primary != alias) {
                        errors += path + " 的 color 与 textColor 不一致（" + primary.wire + " / " + alias.wire + "）"
                    }
                    primary ?: alias
                }

                else -> null
            },
            weight = enumField(node, "weight", path, errors, UiWeight.wireNames, UiWeight::fromWire),
            maxLines = intField(node, "maxLines", path, errors, 1, 1),
            src = imageSource(node, "src", path, errors),
            name = enumField(node, "name", path, errors, UiIcon.wireNames, UiIcon::fromWire),
            iconSize = if (type == UiNodeType.ICON) intField(node, "size", path, errors, null, 1) else null,
            label = stringField(node, "label", path, errors),
            style = enumField(node, "style", path, errors, UiButtonStyle.wireNames, UiButtonStyle::fromWire),
            pixelSize = if (type == UiNodeType.PIXEL_TEXT) {
                intField(node, "pixelSize", path, errors, null, 1)
            } else {
                null
            },
            align = if (type == UiNodeType.PIXEL_TEXT) {
                enumField(node, "align", path, errors, UiAlign.wireNames, UiAlign::fromWire)
            } else {
                null
            },
            scanlines = if (type == UiNodeType.PIXEL_TEXT) booleanField(node, "scanlines", path, errors) else null,
            glow = if (type == UiNodeType.PIXEL_TEXT) booleanField(node, "glow", path, errors) else null,
            border = if (type == UiNodeType.PIXEL_TEXT) {
                enumField(node, "border", path, errors, UiBorder.wireNames, UiBorder::fromWire)
            } else {
                null
            },
            borderColor = if (type == UiNodeType.PIXEL_TEXT) {
                colorField(node, "borderColor", path, errors, UiColor.FOREGROUNDS)
            } else {
                null
            },
            checkedFrom = expressionField(node, "checkedFrom", path, errors),
            valueFrom = expressionField(node, "valueFrom", path, errors),
            min = intField(node, "min", path, errors, null),
            max = intField(node, "max", path, errors, null),
            onChange = eventField(node, "onChange", path, errors),
            bars = if (type == UiNodeType.WAVEFORM) {
                intField(
                    node, "bars", path, errors, DEFAULT_WAVEFORM_BARS, 1,
                    maxValue = MAX_WAVEFORM_BARS,
                    maxMessage = "竖线最多 $MAX_WAVEFORM_BARS 条，再多在手机上就细到看不见",
                )
            } else {
                null
            },
            amplitudePermyriad = if (type == UiNodeType.WAVEFORM) {
                ratioField(
                    node, "amplitude", path, errors,
                    DEFAULT_WAVEFORM_AMPLITUDE_PERMYRIAD, MAX_AMPLITUDE_PERMYRIAD,
                )
            } else {
                null
            },
            speedPermyriad = if (type == UiNodeType.WAVEFORM) {
                ratioField(
                    node, "speed", path, errors,
                    DEFAULT_SPEED_PERMYRIAD, MAX_SPEED_PERMYRIAD, MIN_SPEED_PERMYRIAD,
                )
            } else {
                null
            },
            // disk 的 size 是直径、glow 的 radius 是半径：两者都存进 sizeDp，含义由 type 决定。
            // disk 没写 size 时按 width（上面已校验过 width 与 height 相等）推直径，两边都不写才用默认值。
            sizeDp = when (type) {
                UiNodeType.DISK -> intField(
                    node, "size", path, errors, diameterFallback(node), MIN_DECORATION_SIZE,
                    maxValue = MAX_DECORATION_SIZE,
                    maxMessage = "直径最大 $MAX_DECORATION_SIZE dp",
                )

                UiNodeType.GLOW -> intField(
                    node, "radius", path, errors, DEFAULT_GLOW_RADIUS, MIN_DECORATION_SIZE,
                    maxValue = MAX_DECORATION_SIZE,
                    maxMessage = "半径最大 $MAX_DECORATION_SIZE dp",
                )

                else -> null
            },
            intensityPermyriad = if (type == UiNodeType.GLOW) {
                ratioField(
                    node, "intensity", path, errors,
                    DEFAULT_GLOW_INTENSITY_PERMYRIAD, MAX_GLOW_INTENSITY_PERMYRIAD,
                )
            } else {
                null
            },
            coverFrom = if (type == UiNodeType.DISK) coverSource(node, path, errors) else null,
            activeFrom = expressionField(
                node,
                if (type == UiNodeType.DISK) "spinningFrom" else "activeFrom",
                path,
                errors,
            ),
            // 只认 "edges"（首/中/末三根）；写别的值直接报错，避免插件作者以为随便填都行。
            accentAt = if (type == UiNodeType.WAVEFORM) {
                val raw = node.fields["accentAt"]?.asStringOrNull?.trim().orEmpty()
                when (raw) {
                    "" -> null
                    "edges" -> "edges"
                    else -> {
                        errors += path + ".accentAt 只能是 edges，实际 '" + raw.take(20) + "'"
                        null
                    }
                }
            } else {
                null
            },
        ).also { requireFields(it, node, path, errors) }
    }

    /** 各类型自己的必填项与取值约束（UI 渲染不出来的组合在这里就拦掉）。 */
    private fun requireFields(node: UiNode, source: JsonNode.Obj, path: String, errors: MutableList<String>) {
        when (node.type) {
            UiNodeType.TEXT -> {
                if (node.text == null && node.textFrom == null) errors += path + " 必须提供 text 或 textFrom"
                if (node.text != null && node.textFrom != null) errors += path + " 的 text 与 textFrom 只能二选一"
            }

            UiNodeType.IMAGE -> {
                if (node.src == null && source.fields["src"] == null) errors += path + " 缺少必需字段 src"
            }

            UiNodeType.ICON -> {
                if (node.name == null && source.fields["name"] == null) errors += path + " 缺少必需字段 name"
            }

            UiNodeType.BUTTON -> {
                if (node.label == null && source.fields["label"] == null) errors += path + " 缺少必需字段 label"
            }

            UiNodeType.SWITCH -> {
                if (node.checkedFrom == null && source.fields["checkedFrom"] == null) {
                    errors += path + " 缺少必需字段 checkedFrom"
                }
                if (node.onChange == null && source.fields["onChange"] == null) {
                    errors += path + " 缺少必需字段 onChange（没有回调的开关只会显示成死控件）"
                }
            }

            UiNodeType.SLIDER -> {
                if (node.valueFrom == null && source.fields["valueFrom"] == null) {
                    errors += path + " 缺少必需字段 valueFrom"
                }
                if (node.onChange == null && source.fields["onChange"] == null) {
                    errors += path + " 缺少必需字段 onChange"
                }
                val min = node.min ?: 0
                val max = node.max ?: 100
                if (min >= max) errors += path + " 的 min 必须小于 max（实际 min=" + min + ", max=" + max + "）"
            }

            UiNodeType.PROGRESS -> {
                if (node.valueFrom == null && source.fields["valueFrom"] == null) {
                    errors += path + " 缺少必需字段 valueFrom"
                }
            }

            UiNodeType.PIXEL_TEXT -> {
                if (node.textFrom == null && source.fields["textFrom"] == null) {
                    errors += path + " 缺少必需字段 textFrom（像素字画什么内容由它决定）"
                }
                node.pixelSize?.let {
                    if (it > MAX_PIXEL_SIZE) {
                        errors += path + ".pixelSize 最大 " + MAX_PIXEL_SIZE + "，实际 " + it
                    }
                }
                // borderColor 只在画边框时才有意义，单写一个颜色却不要边框是作者写错了。
                if (node.borderColor != null && node.border == null) {
                    errors += path + ".borderColor 必须和 border 一起用（不画边框时颜色没有落点）"
                }
            }

            UiNodeType.FLIP_CARD -> {
                if (node.valueFrom == null && source.fields["valueFrom"] == null) {
                    errors += path + " 缺少必需字段 valueFrom（没有它，卡片不知道该翻到哪个数字）"
                }
                // 翻页牌必须有明确的两条边：宽度撑满父级就量不出中缝，高度自适应就切不出上下两半。
                if (node.width == null) errors += path + " 缺少必需字段 width（翻页卡片要固定的宽，否则中缝量不出来）"
                if (node.height == null) errors += path + " 缺少必需字段 height（翻页卡片要固定的高，否则上下两半切不出来）"
                if (node.width == UiLength.Fill) errors += path + ".width 不能是 fill（翻页卡片必须是固定宽）"
                if (node.height == UiLength.Fill) errors += path + ".height 不能是 fill（翻页卡片必须是固定高）"
            }

            // 三个装饰组件都不接收 color 之外的视觉自由度，但各自有「画不出来」的组合要先拦掉。
            UiNodeType.WAVEFORM -> {
                if (node.width == null) errors += path + " 缺少必需字段 width（竖线要按容器宽度均分）"
                if (node.width == UiLength.Wrap) {
                    errors += path + ".width 不能是 wrap（wrap 量不出宽度，竖线间距无从计算）"
                }
                if (node.height == null) {
                    errors += path + " 缺少必需字段 height（竖线的最大高度就是容器高）"
                }
                if (node.height == UiLength.Fill) {
                    errors += path + ".height 不能是 fill（起伏幅度要相对一个确定的高度）"
                }
            }

            UiNodeType.DISK -> {
                // 直径要么由 size 决定，要么由宽高一致地撑出来；宽高不一致会把圆画成椭圆。
                if (node.width != null && node.height != null &&
                    node.width != node.height
                ) {
                    errors += path + " 的 width 与 height 必须一致（唱片是圆，宽高不等会画成椭圆）"
                }
                if (node.width == UiLength.Fill || node.height == UiLength.Fill) {
                    errors += path + " 不能用 fill（唱片要有确定的直径，fill 会把圆压成椭圆）"
                }
            }

            UiNodeType.GLOW -> {
                if (node.width != null && node.height != null && node.width != node.height) {
                    errors += path + " 的 width 与 height 必须一致（光晕是圆）"
                }
            }

            else -> Unit
        }
    }

    private fun parseChildren(node: JsonNode.Obj, path: String, depth: Int, state: ParseState): List<UiNode> {
        val nodeChildren = node.fields["children"] ?: return emptyList()
        if (nodeChildren is JsonNode.Null) return emptyList()
        val array = nodeChildren.asArrayOrNull
            ?: run {
                state.errors += path + ".children 必须是数组"
                return emptyList()
            }
        val result = mutableListOf<UiNode>()
        for ((index, item) in array.items.withIndex()) {
            val childPath = path + ".children[" + index + "]"
            val child = item.asObjectOrNull
            if (child == null) {
                state.errors += childPath + " 必须是对象"
                continue
            }
            parseNode(child, childPath, depth + 1, state)?.let { result += it }
        }
        return result
    }

    private fun anchorField(node: JsonNode.Obj, path: String, errors: MutableList<String>): UiAnchor? {
        val value = stringField(node, "anchor", path, errors) ?: return null
        val anchor = UiAnchor.fromWire(value)
        if (anchor == null) {
            errors += path + ".anchor 只能是 " + UiAnchor.wireNames.joinToString("|") + " 之一，实际 '" + value + "'"
        }
        return anchor
    }

    private fun animationField(node: JsonNode.Obj, path: String, errors: MutableList<String>): UiAnimation? {
        val raw = node.fields["animation"] ?: return null
        if (raw is JsonNode.Null) return null
        val animation = raw.asObjectOrNull
            ?: run {
                errors += path + ".animation 必须是对象"
                return null
            }
        val typeName = animation.fields["type"]?.asStringOrNull
        if (typeName == null) {
            errors += path + ".animation 缺少字符串字段 type"
            return null
        }
        val duration = intField(animation, "durationMs", path + ".animation", errors, 300, 0) ?: 300
        return when (typeName) {
            "fade" -> UiAnimation.Fade(duration)
            "scale" -> UiAnimation.Scale(duration)
            "slide" -> UiAnimation.Slide(duration)
            "sprite" -> spriteAnimation(animation, path, errors) ?: UiAnimation.Fade(duration)
            else -> {
                errors += path + ".animation.type 只能是 fade|scale|slide|sprite 之一，实际 '" + typeName + "'"
                null
            }
        }
    }

    private fun spriteAnimation(animation: JsonNode.Obj, path: String, errors: MutableList<String>): UiAnimation? {
        val framesNode = animation.fields["frames"]
        val array = framesNode?.asArrayOrNull
        if (array == null) {
            errors += path + ".animation.frames 必须是字符串数组"
            return null
        }
        val frames = mutableListOf<String>()
        for ((index, item) in array.items.withIndex()) {
            val frame = item.asStringOrNull
            if (frame == null) {
                errors += path + ".animation.frames[" + index + "] 必须是字符串"
                continue
            }
            if (!PluginPackage.isSafeRelativePath(frame)) {
                errors += path + ".animation.frames[" + index + "] 必须是包内相对路径，实际 '" + frame + "'"
                continue
            }
            frames += frame
        }
        if (frames.isEmpty()) {
            errors += path + ".animation.frames 不能为空"
            return null
        }
        val fps = intField(animation, "fps", path + ".animation", errors, 3, 1) ?: 3
        if (fps > 30) errors += path + ".animation.fps 最大 30，实际 " + fps
        return UiAnimation.Sprite(frames, fps.coerceIn(1, 30))
    }

    /**
     * 唱片封面：静态图（包内相对路径 / `user:<设置项>`）或者**运行期表达式**（`state.artworkPath`）。
     *
     * 封面是随歌变的，写死一张显然不够用；又不能让插件随便写一个字面绝对路径
     * 去读宿主目录里的任意文件 —— 所以只有 `state.xxx` / `msg.xxx` 这种「值来自插件自己声明的
     * state」的写法才会被当成路径，渲染器还会再把它夹在宿主封面目录内（见 PluginUiRenderer）。
     */
    private fun coverSource(node: JsonNode.Obj, path: String, errors: MutableList<String>): String? {
        val value = stringField(node, "coverFrom", path, errors) ?: return null
        if (VALUE_EXPRESSION.matches(value)) return value
        if (value.startsWith(USER_IMAGE_PREFIX) || PluginPackage.isSafeRelativePath(value)) {
            return imageSource(node, "coverFrom", path, errors)
        }
        errors += path + ".coverFrom 必须是包内相对路径、user:<设置项>，或 state.xxx / msg.xxx 取值表达式，实际 '" +
            value + "'"
        return null
    }

    /**
     * 图片来源：包内相对路径，或者 `user:<settings key>`。
     * `user:` 指向用户在设置页选的图片 —— 插件只写一个键名，宿主负责把文件找出来，
     * 脚本拿不到、也不需要处理任何二进制。
     */
    /**
     * 图片来源。除了包内相对路径与 `user:<设置项>`，还接受 **运行期取值表达式**（`state.xxx` / `msg.xxx`）：
     * 播放器封面是随歌变的（`state.artworkPath`），写死一张显然不够用。
     *
     * 之所以不放开任意绝对路径：取值表达式只能来自插件自己声明的 state，渲染器还会把结果再夹在
     * 宿主封面目录内（见 PluginUiRenderer.rememberAsset），插件拿不到别的文件。
     */
    private fun imageSource(
        node: JsonNode.Obj,
        field: String,
        path: String,
        errors: MutableList<String>,
    ): String? {
        val value = stringField(node, field, path, errors) ?: return null
        if (VALUE_EXPRESSION.matches(value)) return value
        if (value.startsWith(USER_IMAGE_PREFIX)) {
            val key = value.removePrefix(USER_IMAGE_PREFIX)
            if (key.isBlank()) {
                errors += path + "." + field + " 写成 '" + value + "' 时后面必须跟设置项的 key（例：user:bgImage）"
                return null
            }
            return value
        }
        if (!PluginPackage.isSafeRelativePath(value)) {
            errors += path + "." + field + " 必须是包内相对路径或 user:<设置项>（不能以 / 开头、不能含 .. ），实际 '" + value + "'"
            return null
        }
        return value
    }

    private fun colorField(
        node: JsonNode.Obj,
        field: String,
        path: String,
        errors: MutableList<String>,
        allowed: List<UiColor>,
    ): UiColor? {
        val value = stringField(node, field, path, errors) ?: return null
        val color = UiColor.fromWire(value)
        if (color == null || color !in allowed) {
            errors += path + "." + field + " 只能是 " + allowed.joinToString("|") { it.wire } +
                " 之一，实际 '" + value + "'（颜色必须用语义 token，不接受任意色值）"
            return null
        }
        return color
    }

    private fun <T> enumField(
        node: JsonNode.Obj,
        field: String,
        path: String,
        errors: MutableList<String>,
        wires: List<String>,
        fromWire: (String) -> T?,
    ): T? {
        val value = stringField(node, field, path, errors) ?: return null
        val parsed = fromWire(value)
        if (parsed == null) {
            errors += path + "." + field + " 只能是 " + wires.joinToString("|") + " 之一，实际 '" + value + "'"
        }
        return parsed
    }

    private fun expressionField(node: JsonNode.Obj, field: String, path: String, errors: MutableList<String>): String? {
        val value = stringField(node, field, path, errors) ?: return null
        if (!VALUE_EXPRESSION.matches(value)) {
            errors += path + "." + field + " 只支持 state.xxx / msg.xxx 的单层取值，实际 '" + value + "'"
            return null
        }
        return value
    }

    private fun eventField(node: JsonNode.Obj, field: String, path: String, errors: MutableList<String>): String? {
        val value = stringField(node, field, path, errors) ?: return null
        if (!EVENT_NAME.matches(value)) {
            errors += path + "." + field + " 必须是 logic.js 里的函数名，实际 '" + value + "'"
            return null
        }
        return value
    }

    private fun lengthField(node: JsonNode.Obj, field: String, path: String, errors: MutableList<String>): UiLength? {
        val raw = node.fields[field] ?: return null
        if (raw is JsonNode.Null) return null
        val text = raw.asStringOrNull
        if (text != null) {
            when (text) {
                "fill" -> return UiLength.Fill
                "wrap" -> return UiLength.Wrap
            }
            val fixed = text.toIntOrNull()
            if (fixed == null || fixed < 0) {
                errors += path + "." + field + " 只能是 fill|wrap|非负整数 dp，实际 '" + text + "'"
                return null
            }
            return UiLength.Fixed(fixed)
        }
        val fixed = raw.asIntOrNull
        if (fixed == null || fixed < 0) {
            errors += path + "." + field + " 只能是 fill|wrap|非负整数 dp"
            return null
        }
        return UiLength.Fixed(fixed)
    }

    private fun booleanField(node: JsonNode.Obj, field: String, path: String, errors: MutableList<String>): Boolean? {
        val raw = node.fields[field] ?: return null
        if (raw is JsonNode.Null) return null
        val value = (raw as? JsonNode.Bool)?.value
        if (value == null) errors += path + "." + field + " 必须是布尔值 true / false"
        return value
    }

    private fun stringField(node: JsonNode.Obj, field: String, path: String, errors: MutableList<String>): String? {
        val raw = node.fields[field] ?: return null
        if (raw is JsonNode.Null) return null
        val value = raw.asStringOrNull
        if (value == null) {
            errors += path + "." + field + " 必须是字符串"
            return null
        }
        return value
    }

    private fun intField(
        node: JsonNode.Obj,
        field: String,
        path: String,
        errors: MutableList<String>,
        default: Int? = null,
        min: Int? = null,
        maxValue: Int? = null,
        maxMessage: String? = null,
    ): Int? {
        val raw = node.fields[field] ?: return default
        if (raw is JsonNode.Null) return default
        val value = raw.asIntOrNull
        if (value == null) {
            errors += path + "." + field + " 必须是整数"
            return default
        }
        if (min != null && value < min) {
            errors += path + "." + field + " 不能小于 " + min + "（实际 " + value + "）"
            return default
        }
        if (maxValue != null && value > maxValue) {
            errors += path + "." + field + " " + (maxMessage ?: ("不能大于 " + maxValue)) + "（实际 " + value + "）"
            return default
        }
        return value
    }

    /**
     * 0..1 之间的比例参数（amplitude / speed / intensity），协议侧写小数，模型里存万分比整数。
     *
     * 不收整数写法的「百分比」：这三个参数和 min/max 那种刻度不是一回事，
     * 允许 0.35 和 35 两种写法只会让「35 到底是 35% 还是 3500%」变成猜谜。
     */
    private fun ratioField(
        node: JsonNode.Obj,
        field: String,
        path: String,
        errors: MutableList<String>,
        defaultPermyriad: Int,
        maxPermyriad: Int,
        minPermyriad: Int = 0,
    ): Int? {
        val raw = node.fields[field] ?: return defaultPermyriad
        if (raw is JsonNode.Null) return defaultPermyriad
        val value = raw.asNumberOrNull
        if (value == null) {
            errors += path + "." + field + " 必须是数字（" + trimZero(minPermyriad / 10000.0) +
                "~" + trimZero(maxPermyriad / 10000.0) + " 之间的小数）"
            return defaultPermyriad
        }
        val minimum = minPermyriad / 10000.0
        val maximum = maxPermyriad / 10000.0
        if (value < minimum || value > maximum) {
            errors += path + "." + field + " 必须在 " + trimZero(minimum) + "~" + trimZero(maximum) +
                " 之间，实际 " + value
            return defaultPermyriad
        }
        return Math.round(value * 10000.0).toInt().coerceIn(minPermyriad, maxPermyriad)
    }

    /** disk 没写 size 时，用 width 上的固定 dp 当直径；没有就退回默认值。 */
    private fun diameterFallback(node: JsonNode.Obj): Int =
        (node.fields["width"] as? JsonNode.Num)?.asIntOrNull?.takeIf { it > 0 } ?: DEFAULT_DISK_SIZE

    private fun trimZero(value: Double): String =
        if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()

    private fun describe(node: JsonNode): String = when (node) {
        is JsonNode.Null -> "null"
        is JsonNode.Bool -> "布尔值"
        is JsonNode.Num -> "数字"
        is JsonNode.Str -> "字符串"
        is JsonNode.Arr -> "数组"
        is JsonNode.Obj -> "对象"
    }
}
