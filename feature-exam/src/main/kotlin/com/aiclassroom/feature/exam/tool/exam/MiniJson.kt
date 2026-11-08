package com.aiclassroom.feature.exam.tool.exam

/**
 * 极简 JSON 读写。[feature-exam] 是纯 JVM 模块（不引第三方库），
 * 这里只覆盖 function calling 实际用到的子集：对象 / 数组 / 字符串 / 数字 / 布尔 / null。
 */
internal sealed class JsonValue {
    object Null : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()
    data class Num(val value: Double) : JsonValue()
    data class Str(val value: String) : JsonValue()
    data class Arr(val items: List<JsonValue>) : JsonValue()
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue()

    companion object {
        /** 解析失败抛 [JsonFormatException]，调用方负责转成 ToolResult.Error。 */
        fun parse(text: String): JsonValue = JsonParser(text).parseDocument()

        fun write(value: JsonValue): String = StringBuilder().also { writeTo(it, value) }.toString()

        private fun writeTo(out: StringBuilder, value: JsonValue) {
            when (value) {
                is Null -> out.append("null")
                is Bool -> out.append(if (value.value) "true" else "false")
                is Num -> writeNumber(out, value.value)
                is Str -> writeString(out, value.value)
                is Arr -> {
                    out.append('[')
                    value.items.forEachIndexed { index, item ->
                        if (index > 0) out.append(',')
                        writeTo(out, item)
                    }
                    out.append(']')
                }
                is Obj -> {
                    out.append('{')
                    var first = true
                    value.fields.forEach { (key, item) ->
                        if (!first) out.append(',')
                        first = false
                        writeString(out, key)
                        out.append(':')
                        writeTo(out, item)
                    }
                    out.append('}')
                }
            }
        }

        private fun writeNumber(out: StringBuilder, value: Double) {
            if (!value.isFinite()) {
                out.append("null")
                return
            }
            if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15) {
                out.append(value.toLong().toString())
            } else {
                out.append(value.toString())
            }
        }

        private fun writeString(out: StringBuilder, value: String) {
            out.append('"')
            value.forEach { character ->
                when (character) {
                    '"' -> out.append("\\\"")
                    '\\' -> out.append("\\\\")
                    '\n' -> out.append("\\n")
                    '\r' -> out.append("\\r")
                    '\t' -> out.append("\\t")
                    '\b' -> out.append("\\b")
                    '\u000C' -> out.append("\\f")
                    else -> if (character < ' ') {
                        out.append("\\u").append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        out.append(character)
                    }
                }
            }
            out.append('"')
        }
    }
}

/** 参数不是合法 JSON 时抛出；只在模块内部使用。 */
internal class JsonFormatException(message: String) : Exception(message)

private class JsonParser(private val text: String) {
    private var index = 0

    fun parseDocument(): JsonValue {
        skipWhitespace()
        val value = parseValue(0)
        skipWhitespace()
        if (index < text.length) throw JsonFormatException("位置 $index 之后还有多余内容")
        return value
    }

    private fun parseValue(depth: Int): JsonValue {
        if (depth > 64) throw JsonFormatException("JSON 嵌套层级过深")
        if (index >= text.length) throw JsonFormatException("JSON 意外结束")
        return when (val character = text[index]) {
            '{' -> parseObject(depth)
            '[' -> parseArray(depth)
            '"' -> JsonValue.Str(parseString())
            't' -> parseLiteral("true", JsonValue.Bool(true))
            'f' -> parseLiteral("false", JsonValue.Bool(false))
            'n' -> parseLiteral("null", JsonValue.Null)
            else -> if (character == '-' || character in '0'..'9') {
                parseNumber()
            } else {
                throw JsonFormatException("位置 $index 出现非法字符 '$character'")
            }
        }
    }

    private fun parseObject(depth: Int): JsonValue {
        index++ // '{'
        val fields = LinkedHashMap<String, JsonValue>()
        skipWhitespace()
        if (index < text.length && text[index] == '}') {
            index++
            return JsonValue.Obj(fields)
        }
        while (true) {
            skipWhitespace()
            if (index >= text.length || text[index] != '"') throw JsonFormatException("位置 $index 应为对象键名")
            val key = parseString()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            fields[key] = parseValue(depth + 1)
            skipWhitespace()
            if (index >= text.length) throw JsonFormatException("对象没有闭合")
            when (text[index]) {
                ',' -> index++
                '}' -> {
                    index++
                    return JsonValue.Obj(fields)
                }
                else -> throw JsonFormatException("位置 $index 应为 ',' 或 '}'")
            }
        }
    }

    private fun parseArray(depth: Int): JsonValue {
        index++ // '['
        val items = ArrayList<JsonValue>()
        skipWhitespace()
        if (index < text.length && text[index] == ']') {
            index++
            return JsonValue.Arr(items)
        }
        while (true) {
            skipWhitespace()
            items.add(parseValue(depth + 1))
            skipWhitespace()
            if (index >= text.length) throw JsonFormatException("数组没有闭合")
            when (text[index]) {
                ',' -> index++
                ']' -> {
                    index++
                    return JsonValue.Arr(items)
                }
                else -> throw JsonFormatException("位置 $index 应为 ',' 或 ']'")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val builder = StringBuilder()
        while (true) {
            if (index >= text.length) throw JsonFormatException("字符串没有闭合")
            val character = text[index]
            when {
                character == '"' -> {
                    index++
                    return builder.toString()
                }
                character == '\\' -> {
                    index++
                    builder.append(parseEscape())
                }
                character < ' ' -> throw JsonFormatException("位置 $index 出现未转义的控制字符")
                else -> {
                    builder.append(character)
                    index++
                }
            }
        }
    }

    private fun parseEscape(): Char {
        if (index >= text.length) throw JsonFormatException("转义字符意外结束")
        val character = text[index]
        index++
        return when (character) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> parseUnicodeEscape()
            else -> throw JsonFormatException("未知转义 \\$character")
        }
    }

    private fun parseUnicodeEscape(): Char {
        if (index + 4 > text.length) throw JsonFormatException("\\u 转义不完整")
        val digits = text.substring(index, index + 4)
        val code = digits.toIntOrNull(16) ?: throw JsonFormatException("\\u 转义不是十六进制：$digits")
        index += 4
        return code.toChar()
    }

    private fun parseNumber(): JsonValue {
        val start = index
        if (index < text.length && text[index] == '-') index++
        while (index < text.length && text[index] in '0'..'9') index++
        if (index < text.length && text[index] == '.') {
            index++
            while (index < text.length && text[index] in '0'..'9') index++
        }
        if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
            index++
            if (index < text.length && (text[index] == '+' || text[index] == '-')) index++
            while (index < text.length && text[index] in '0'..'9') index++
        }
        val literal = text.substring(start, index)
        val value = literal.toDoubleOrNull() ?: throw JsonFormatException("非法数字：$literal")
        return JsonValue.Num(value)
    }

    private fun parseLiteral(literal: String, value: JsonValue): JsonValue {
        if (!text.startsWith(literal, index)) throw JsonFormatException("位置 $index 无法识别的字面量")
        index += literal.length
        return value
    }

    private fun expect(character: Char) {
        if (index >= text.length || text[index] != character) throw JsonFormatException("位置 $index 应为 '$character'")
        index++
    }

    private fun skipWhitespace() {
        while (index < text.length && text[index].isWhitespace()) index++
    }
}

internal fun jsonObject(vararg fields: Pair<String, JsonValue>): JsonValue = JsonValue.Obj(linkedMapOf(*fields))

internal fun jsonArray(items: List<JsonValue>): JsonValue = JsonValue.Arr(items)

internal fun JsonValue.asObjectOrNull(): Map<String, JsonValue>? = (this as? JsonValue.Obj)?.fields

internal fun JsonValue.asStringOrNull(): String? = (this as? JsonValue.Str)?.value

/** 布尔值；同时宽容接受 "true" / "false" 字符串（部分模型会把布尔值写成字符串）。 */
internal fun JsonValue.asBooleanOrNull(): Boolean? = when (this) {
    is JsonValue.Bool -> value
    is JsonValue.Str -> when (value.trim().lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
    else -> null
}

/** 整数；同时宽容接受 "3" 这样的数字字符串。小数与越界值返回 null。 */
internal fun JsonValue.asIntOrNull(): Int? = when (this) {
    is JsonValue.Num -> value.takeIf {
        it.isFinite() && it == kotlin.math.floor(it) && kotlin.math.abs(it) <= Int.MAX_VALUE.toDouble()
    }?.toInt()
    is JsonValue.Str -> value.trim().toIntOrNull()
    else -> null
}
