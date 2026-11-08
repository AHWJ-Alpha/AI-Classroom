package com.aiclassroom.feature.exam.tool.universal

import com.aiclassroom.feature.exam.tool.exam.JsonValue
import com.aiclassroom.feature.exam.tool.exam.asObjectOrNull
import com.aiclassroom.feature.exam.tool.image.ImageFailure

/**
 * 极简路径解析器：把 a.b[0].c / results[0].url / data.0.url 这种写法落到 JSON 上。
 *
 * 只做取值，不做求值、不做函数、不支持通配——接未知服务时够用且不会骗人：
 * 取不到就返回 null，由调用方带着响应片段报错。
 */
internal object UniversalJsonPath {

    /** 解析路径并取值；路径为空、语法不合法或中途缺失都返回 null。 */
    fun select(root: JsonValue, path: String): JsonValue? {
        val tokens = tokenize(path) ?: return null
        if (tokens.isEmpty()) return null
        var current = root
        for (token in tokens) {
            current = when (token) {
                is Token.Field -> (current.asObjectOrNull()?.get(token.name)) ?: return null
                is Token.Index -> {
                    val items = (current as? JsonValue.Arr)?.items ?: return null
                    items.getOrNull(token.position) ?: return null
                }
            }
        }
        return current
    }

    /** 取字符串值：数字 / 布尔也宽容转成文本（部分服务把 url 之外的字段写成数字）。 */
    fun selectText(root: JsonValue, path: String): String? {
        return when (val value = select(root, path)) {
            null, is JsonValue.Null -> null
            is JsonValue.Str -> value.value.trim().ifEmpty { null }
            is JsonValue.Num -> formatNumber(value.value)
            is JsonValue.Bool -> value.value.toString()
            else -> null
        }
    }

    private sealed class Token {
        data class Field(val name: String) : Token()
        data class Index(val position: Int) : Token()
    }

    /** a.b[0].c / [0].url / a.0.b 都能拆；空段、非数字下标视为语法错误（返回 null）。 */
    private fun tokenize(path: String): List<Token>? {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return emptyList()
        val tokens = ArrayList<Token>()
        var index = 0
        while (index < trimmed.length) {
            val character = trimmed[index]
            when {
                character == '.' -> {
                    if (index + 1 >= trimmed.length) return null // 结尾多一个点
                    index++
                }
                character == '[' -> {
                    val close = trimmed.indexOf(']', index + 1)
                    if (close < 0) return null
                    val literal = trimmed.substring(index + 1, close).trim()
                    val position = literal.toIntOrNull() ?: return null
                    if (position < 0) return null
                    tokens.add(Token.Index(position))
                    index = close + 1
                }
                else -> {
                    val start = index
                    while (index < trimmed.length && trimmed[index] != '.' && trimmed[index] != '[') index++
                    val name = trimmed.substring(start, index).trim()
                    if (name.isEmpty()) return null
                    // a.0.b：纯数字段按数组下标处理，容错用户把下标写成点号。
                    val position = name.toIntOrNull()
                    if (position != null && position >= 0) tokens.add(Token.Index(position)) else tokens.add(Token.Field(name))
                }
            }
        }
        return tokens
    }
}

/** 请求体模板：占位符替换，值一律做 JSON 字符串转义（转义后不含外层引号）。 */
internal object UniversalTemplate {

    /** 支持的占位符名（别名同时生效，方便和既有 custom provider 的模板互抄）。 */
    const val PROMPT: String = "prompt"
    const val NEGATIVE: String = "negative"
    const val WIDTH: String = "width"
    const val HEIGHT: String = "height"
    const val STEPS: String = "steps"
    const val CFG: String = "cfg"
    const val MODEL: String = "model"

    private val PLACEHOLDER: Regex = Regex("\\{\\{\\s*([A-Za-z0-9_]+)\\s*\\}\\}")

    /** 模板里出现过的占位符名（去重，保持出现顺序）。 */
    fun placeholders(template: String): Set<String> {
        val names = LinkedHashSet<String>()
        PLACEHOLDER.findAll(template).forEach { names.add(it.groupValues[1]) }
        return names
    }

    /** 逐个替换；没给值的占位符替换成空串（不是原样留下 {{x}}，否则会发出非法 JSON）。 */
    fun render(template: String, values: Map<String, String>): String =
        PLACEHOLDER.replace(template) { match -> escape(values[match.groupValues[1]].orEmpty()) }

    /** JSON 字符串转义：借 MiniJson 的写入器，去掉外层引号即得转义后的内容。 */
    fun escape(value: String): String {
        val quoted = JsonValue.write(JsonValue.Str(value))
        return quoted.substring(1, quoted.length - 1)
    }
}

/** 数字统一按「整数不带 .0」输出，避免模板里出现 width:1024.0 这种值。 */
internal fun formatNumber(value: Double): String {
    if (!value.isFinite()) throw ImageFailure("模板参数不是有限数字：" + value)
    if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15) return value.toLong().toString()
    return value.toString()
}

/** 布尔 / 字符串 / 数字都收，统一成模板变量文本；对象与数组直接报错。 */
internal fun scalarText(raw: JsonValue?, label: String): String? = when (raw) {
    null, is JsonValue.Null -> null
    is JsonValue.Str -> raw.value.trim().ifEmpty { null }
    is JsonValue.Num -> formatNumber(raw.value)
    is JsonValue.Bool -> raw.value.toString()
    else -> throw ImageFailure(label + " 必须是字符串或数字")
}
