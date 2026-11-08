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

    /** 最大嵌套深度（根节点记为第 1 层）。 */
    const val MAX_DEPTH = PluginLimits.MAX_UI_DEPTH

    /** 单份 ui.json 的最大节点数。 */
    const val MAX_NODES = PluginLimits.MAX_UI_NODES

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
            radius = enumField(node, "radius", path, errors, UiRadius.wireNames, UiRadius::fromWire),
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
            textColor = if (type == UiNodeType.TEXT || type == UiNodeType.ICON || type == UiNodeType.PROGRESS) {
                colorField(node, "color", path, errors, UiColor.FOREGROUNDS)
            } else {
                null
            },
            weight = enumField(node, "weight", path, errors, UiWeight.wireNames, UiWeight::fromWire),
            maxLines = intField(node, "maxLines", path, errors, 1, 1),
            src = imageSource(node, path, errors),
            name = enumField(node, "name", path, errors, UiIcon.wireNames, UiIcon::fromWire),
            iconSize = if (type == UiNodeType.ICON) intField(node, "size", path, errors, null, 1) else null,
            label = stringField(node, "label", path, errors),
            style = enumField(node, "style", path, errors, UiButtonStyle.wireNames, UiButtonStyle::fromWire),
            checkedFrom = expressionField(node, "checkedFrom", path, errors),
            valueFrom = expressionField(node, "valueFrom", path, errors),
            min = intField(node, "min", path, errors, null),
            max = intField(node, "max", path, errors, null),
            onChange = eventField(node, "onChange", path, errors),
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

    private fun imageSource(node: JsonNode.Obj, path: String, errors: MutableList<String>): String? {
        val value = stringField(node, "src", path, errors) ?: return null
        if (!PluginPackage.isSafeRelativePath(value)) {
            errors += path + ".src 必须是包内相对路径（不能以 / 开头、不能含 .. ），实际 '" + value + "'"
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
        return value
    }

    private fun describe(node: JsonNode): String = when (node) {
        is JsonNode.Null -> "null"
        is JsonNode.Bool -> "布尔值"
        is JsonNode.Num -> "数字"
        is JsonNode.Str -> "字符串"
        is JsonNode.Arr -> "数组"
        is JsonNode.Obj -> "对象"
    }
}
