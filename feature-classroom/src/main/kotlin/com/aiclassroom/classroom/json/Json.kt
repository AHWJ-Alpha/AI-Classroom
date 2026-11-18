package com.aiclassroom.classroom.json

/**
 * 一个够用就好的 JSON 值模型。
 *
 * 为什么不直接用 org.json：领域模块是纯 Kotlin JVM，
 * 而 org.json 只在 Android 运行时才有（单元测试里是抛异常的桩）。
 * 自己写一份，存储、快照、导出、诊断都能在 JVM 上被测到。
 */
sealed interface Json {
    data object Null : Json
    data class Bool(val value: Boolean) : Json
    data class Num(val value: Double) : Json
    data class Str(val value: String) : Json
    data class Arr(val items: List<Json>) : Json
    data class Obj(val fields: Map<String, Json>) : Json

    companion object {
        fun of(value: String?): Json = if (value == null) Null else Str(value)
        fun of(value: Boolean): Json = Bool(value)
        fun of(value: Int): Json = Num(value.toDouble())
        fun of(value: Long): Json = Num(value.toDouble())
        fun of(value: Double): Json = Num(value)
        fun arr(items: List<Json>): Json = Arr(items)
        fun obj(vararg pairs: Pair<String, Json>): Json = Obj(linkedMapOf(*pairs))
    }
}

/** 取值便捷方法：缺失或类型不符时返回 null，由调用方决定回退策略。 */
fun Json?.asObject(): Map<String, Json> = (this as? Json.Obj)?.fields ?: emptyMap()
fun Json?.asArray(): List<Json> = (this as? Json.Arr)?.items ?: emptyList()
fun Json?.asString(): String? = (this as? Json.Str)?.value
fun Json?.asInt(): Int? = (this as? Json.Num)?.value?.toInt()
fun Json?.asLong(): Long? = (this as? Json.Num)?.value?.toLong()
fun Json?.asBool(): Boolean? = (this as? Json.Bool)?.value

/** 序列化。输出稳定顺序（按插入顺序），方便对比与追踪来源。 */
object JsonWriter {
    fun write(json: Json): String = StringBuilder().also { append(it, json) }.toString()

    private fun append(sb: StringBuilder, json: Json) {
        when (json) {
            is Json.Null -> sb.append("null")
            is Json.Bool -> sb.append(if (json.value) "true" else "false")
            is Json.Num -> sb.append(formatNumber(json.value))
            is Json.Str -> appendString(sb, json.value)
            is Json.Arr -> {
                sb.append('[')
                json.items.forEachIndexed { i, item ->
                    if (i > 0) sb.append(',')
                    append(sb, item)
                }
                sb.append(']')
            }
            is Json.Obj -> {
                sb.append('{')
                var first = true
                for ((k, v) in json.fields) {
                    if (!first) sb.append(',')
                    first = false
                    appendString(sb, k)
                    sb.append(':')
                    append(sb, v)
                }
                sb.append('}')
            }
        }
    }

    /** 整数不带小数点，避免 3 变成 3.0 之后回读成 Double。 */
    private fun formatNumber(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    private fun appendString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }
}

/** 解析失败。携带位置，便于定位是哪份存档/导出坏了。 */
class JsonParseException(message: String, val offset: Int) : Exception(message + " (at " + offset + ")")

/** 递归下降解析器。拒绝尾随内容，避免「解析了一半就算成功」。 */
object JsonParser {
    fun parse(text: String): Json {
        val p = Cursor(text)
        p.skipWhitespace()
        val value = p.parseValue()
        p.skipWhitespace()
        if (!p.eof()) throw JsonParseException("存在多余内容", p.pos)
        return value
    }

    private class Cursor(val s: String) {
        var pos = 0
        fun eof() = pos >= s.length
        fun peek(): Char = s[pos]
        fun next(): Char = s[pos++]

        fun skipWhitespace() {
            while (!eof() && s[pos].let { it == ' ' || it == '\n' || it == '\r' || it == '\t' }) pos++
        }

        fun expect(c: Char) {
            if (eof() || s[pos] != c) throw JsonParseException("期望 '" + c + "'", pos)
            pos++
        }

        fun parseValue(): Json {
            if (eof()) throw JsonParseException("意外结束", pos)
            return when (val c = peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> Json.Str(parseString())
                't' -> { literal("true"); Json.Bool(true) }
                'f' -> { literal("false"); Json.Bool(false) }
                'n' -> { literal("null"); Json.Null }
                else -> if (c == '-' || c.isDigit()) parseNumber() else throw JsonParseException("非法字符 " + c, pos)
            }
        }

        fun literal(word: String) {
            if (pos + word.length > s.length || s.substring(pos, pos + word.length) != word) {
                throw JsonParseException("期望 " + word, pos)
            }
            pos += word.length
        }

        fun parseObject(): Json {
            expect('{')
            val fields = linkedMapOf<String, Json>()
            skipWhitespace()
            if (!eof() && peek() == '}') { pos++; return Json.Obj(fields) }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                fields[key] = parseValue()
                skipWhitespace()
                if (eof()) throw JsonParseException("对象未闭合", pos)
                when (next()) {
                    ',' -> continue
                    '}' -> return Json.Obj(fields)
                    else -> throw JsonParseException("对象内期望 , 或 }", pos - 1)
                }
            }
        }

        fun parseArray(): Json {
            expect('[')
            val items = mutableListOf<Json>()
            skipWhitespace()
            if (!eof() && peek() == ']') { pos++; return Json.Arr(items) }
            while (true) {
                skipWhitespace()
                items += parseValue()
                skipWhitespace()
                if (eof()) throw JsonParseException("数组未闭合", pos)
                when (next()) {
                    ',' -> continue
                    ']' -> return Json.Arr(items)
                    else -> throw JsonParseException("数组内期望 , 或 ]", pos - 1)
                }
            }
        }

        fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (eof()) throw JsonParseException("字符串未闭合", pos)
                when (val c = next()) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (eof()) throw JsonParseException("转义未完成", pos)
                        when (val e = next()) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw JsonParseException("\\u 不完整", pos)
                                val hex = s.substring(pos, pos + 4)
                                pos += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> throw JsonParseException("非法转义 \\" + e, pos - 1)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        fun parseNumber(): Json {
            val start = pos
            if (!eof() && peek() == '-') pos++
            while (!eof() && peek().isDigit()) pos++
            if (!eof() && peek() == '.') {
                pos++
                while (!eof() && peek().isDigit()) pos++
            }
            if (!eof() && (peek() == 'e' || peek() == 'E')) {
                pos++
                if (!eof() && (peek() == '+' || peek() == '-')) pos++
                while (!eof() && peek().isDigit()) pos++
            }
            val text = s.substring(start, pos)
            val d = text.toDoubleOrNull() ?: throw JsonParseException("非法数字 " + text, start)
            return Json.Num(d)
        }
    }
}
