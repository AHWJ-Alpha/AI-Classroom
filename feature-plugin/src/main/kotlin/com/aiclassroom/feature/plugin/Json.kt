package com.aiclassroom.feature.plugin

/**
 * 模块内极简 JSON：[feature-plugin] 不引第三方 JSON 库，只覆盖 manifest 与插件状态存储用到的子集。
 * 解析失败抛 [JsonParseException]，调用方负责转成校验错误。
 */
internal sealed class JsonNode {
    object Null : JsonNode()
    data class Bool(val value: Boolean) : JsonNode()
    data class Num(val value: Double) : JsonNode()
    data class Str(val value: String) : JsonNode()
    data class Arr(val items: List<JsonNode>) : JsonNode()
    data class Obj(val fields: Map<String, JsonNode>) : JsonNode()

    val asObjectOrNull: Obj? get() = this as? Obj
    val asArrayOrNull: Arr? get() = this as? Arr
    val asStringOrNull: String? get() = (this as? Str)?.value

    /** 任意数字（settings[].default 里的 number 允许小数）。 */
    val asNumberOrNull: Double?
        get() = (this as? Num)?.value?.takeIf { !it.isNaN() && !it.isInfinite() }

    /** 只接受整数值，避免 1.5 悄悄变成 1。 */
    val asIntOrNull: Int?
        get() {
            val value = (this as? Num)?.value ?: return null
            if (value.isNaN() || value.isInfinite() || value != Math.floor(value)) return null
            if (value < Int.MIN_VALUE || value > Int.MAX_VALUE) return null
            return value.toInt()
        }
}

internal class JsonParseException(message: String) : Exception(message)

internal object Json {

    fun parse(text: String): JsonNode = JsonParser(text).parseDocument()

    fun write(node: JsonNode): String = StringBuilder().also { writeInto(it, node) }.toString()

    private fun writeInto(out: StringBuilder, node: JsonNode) {
        when (node) {
            is JsonNode.Null -> out.append("null")
            is JsonNode.Bool -> out.append(if (node.value) "true" else "false")
            is JsonNode.Num -> out.append(formatNumber(node.value))
            is JsonNode.Str -> writeString(out, node.value)
            is JsonNode.Arr -> {
                out.append('[')
                node.items.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    writeInto(out, item)
                }
                out.append(']')
            }
            is JsonNode.Obj -> {
                out.append('{')
                var first = true
                for ((key, value) in node.fields) {
                    if (!first) out.append(',')
                    first = false
                    writeString(out, key)
                    out.append(':')
                    writeInto(out, value)
                }
                out.append('}')
            }
        }
    }

    private fun formatNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        val asLong = value.toLong()
        return if (asLong.toDouble() == value) asLong.toString() else value.toString()
    }

    private fun writeString(out: StringBuilder, value: String) {
        out.append('"')
        for (ch in value) {
            when {
                ch == '"' -> out.append("\\\"")
                ch == '\\' -> out.append("\\\\")
                ch == '\n' -> out.append("\\n")
                ch == '\r' -> out.append("\\r")
                ch == '\t' -> out.append("\\t")
                ch < ' ' -> out.append("\\u").append(String.format("%04x", ch.code))
                else -> out.append(ch)
            }
        }
        out.append('"')
    }
}

private class JsonParser(private val source: String) {

    private var position = 0

    fun parseDocument(): JsonNode {
        skipWhitespace()
        val value = parseValue()
        skipWhitespace()
        if (position != source.length) fail("JSON 结束后还有多余内容")
        return value
    }

    private fun parseValue(): JsonNode {
        if (position >= source.length) fail("内容意外结束")
        return when (val ch = source[position]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonNode.Str(parseString())
            't' -> {
                expectLiteral("true")
                JsonNode.Bool(true)
            }
            'f' -> {
                expectLiteral("false")
                JsonNode.Bool(false)
            }
            'n' -> {
                expectLiteral("null")
                JsonNode.Null
            }
            else -> if (ch == '-' || ch in '0'..'9') parseNumber() else fail("非法的 JSON 起始字符 '$ch'")
        }
    }

    private fun parseObject(): JsonNode.Obj {
        position++
        val fields = LinkedHashMap<String, JsonNode>()
        skipWhitespace()
        if (peek() == '}') {
            position++
            return JsonNode.Obj(fields)
        }
        while (true) {
            skipWhitespace()
            if (peek() != '"') fail("对象的键必须是字符串")
            val key = parseString()
            skipWhitespace()
            if (peek() != ':') fail("对象键后面缺少 ':'")
            position++
            skipWhitespace()
            fields[key] = parseValue()
            skipWhitespace()
            when (peek()) {
                ',' -> position++
                '}' -> {
                    position++
                    return JsonNode.Obj(fields)
                }
                else -> fail("对象里缺少 ',' 或 '}'")
            }
        }
    }

    private fun parseArray(): JsonNode.Arr {
        position++
        val items = mutableListOf<JsonNode>()
        skipWhitespace()
        if (peek() == ']') {
            position++
            return JsonNode.Arr(items)
        }
        while (true) {
            skipWhitespace()
            items += parseValue()
            skipWhitespace()
            when (peek()) {
                ',' -> position++
                ']' -> {
                    position++
                    return JsonNode.Arr(items)
                }
                else -> fail("数组里缺少 ',' 或 ']'")
            }
        }
    }

    private fun parseString(): String {
        if (peek() != '"') fail("这里需要一个字符串")
        position++
        val out = StringBuilder()
        while (true) {
            if (position >= source.length) fail("字符串没有结束")
            val ch = source[position++]
            when {
                ch == '"' -> return out.toString()
                ch == '\\' -> {
                    if (position >= source.length) fail("转义符没有结束")
                    when (val escaped = source[position++]) {
                        '"' -> out.append('"')
                        '\\' -> out.append('\\')
                        '/' -> out.append('/')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (position + 4 > source.length) fail("\\u 转义不完整")
                            val hex = source.substring(position, position + 4)
                            val code = hex.toIntOrNull(16) ?: fail("\\u 转义非法：$hex")
                            out.append(code.toChar())
                            position += 4
                        }
                        else -> fail("非法转义 \\$escaped")
                    }
                }
                ch < ' ' -> fail("字符串里出现未转义的控制字符")
                else -> out.append(ch)
            }
        }
    }

    private fun parseNumber(): JsonNode.Num {
        val start = position
        if (peek() == '-') position++
        while (position < source.length && source[position] in '0'..'9') position++
        if (position < source.length && source[position] == '.') {
            position++
            while (position < source.length && source[position] in '0'..'9') position++
        }
        if (position < source.length && (source[position] == 'e' || source[position] == 'E')) {
            position++
            if (position < source.length && (source[position] == '+' || source[position] == '-')) position++
            while (position < source.length && source[position] in '0'..'9') position++
        }
        val text = source.substring(start, position)
        val value = text.toDoubleOrNull() ?: fail("非法数字：$text")
        return JsonNode.Num(value)
    }

    private fun expectLiteral(literal: String) {
        if (!source.startsWith(literal, position)) fail("这里需要 $literal")
        position += literal.length
    }

    private fun peek(): Char = if (position < source.length) source[position] else '\u0000'

    private fun skipWhitespace() {
        while (position < source.length) {
            val ch = source[position]
            if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') position++ else return
        }
    }

    private fun fail(message: String): Nothing =
        throw JsonParseException("$message（位置 $position）")
}
