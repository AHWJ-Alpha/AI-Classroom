package com.aiclassroom.feature.net

import java.util.Locale

/**
 * 极简 JSON 读写：只服务 net_request 的参数解析与结果拼装，不引第三方依赖。
 * 只覆盖工具参数所需的子集（对象 / 数组 / 字符串 / 数字 / 布尔 / null）。
 */
internal object Json {

    fun quote(text: String): String {
        val out = StringBuilder(text.length + 16)
        out.append('"')
        for (ch in text) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (ch < ' ') {
                    out.append("\\u").append(String.format(Locale.US, "%04x", ch.code))
                } else {
                    out.append(ch)
                }
            }
        }
        out.append('"')
        return out.toString()
    }

    /** 把解析出来的任意值再写回 JSON，用于 body 允许直接传对象 / 数组的场景。 */
    fun stringify(value: Any?): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Boolean -> value.toString()
        is Double -> if (value.isFinite() && value == Math.floor(value) && Math.abs(value) < 1e15) value.toLong().toString() else value.toString()
        is Number -> value.toString()
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { (k, v) -> quote(k.toString()) + ":" + stringify(v) }
        is List<*> -> value.joinToString(",", "[", "]") { stringify(it) }
        else -> quote(value.toString())
    }

    fun parse(text: String): Any? = Parser(text).parseDocument()

    private class Parser(private val src: String) {
        private var pos = 0

        fun parseDocument(): Any? {
            val value = parseValue()
            skipWhitespace()
            require(pos == src.length) { "trailing characters at " + pos }
            return value
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            require(pos < src.length) { "unexpected end of input" }
            return when (val c = src[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                else -> if (c == '-' || c.isDigit()) parseNumber() else throw IllegalArgumentException("unexpected char at " + pos)
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return result
            }
            while (true) {
                skipWhitespace()
                // 容忍模型偶尔多打的尾随逗号。
                if (peek() == '}') {
                    pos++
                    return result
                }
                val key = parseString()
                skipWhitespace()
                expect(':')
                result[key] = parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return result
                    }
                    else -> throw IllegalArgumentException("expected ',' or '}' at " + pos)
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return result
            }
            while (true) {
                skipWhitespace()
                if (peek() == ']') {
                    pos++
                    return result
                }
                result.add(parseValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return result
                    }
                    else -> throw IllegalArgumentException("expected ',' or ']' at " + pos)
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                require(pos < src.length) { "unterminated string" }
                when (val c = src[pos++]) {
                    '"' -> return out.toString()
                    '\\' -> {
                        require(pos < src.length) { "unterminated escape" }
                        when (val esc = src[pos++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                require(pos + 4 <= src.length) { "bad unicode escape" }
                                out.append(src.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("bad escape at " + pos)
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun parseNumber(): Double {
            val start = pos
            if (peek() == '-') pos++
            while (pos < src.length && (src[pos].isDigit() || src[pos] == '.' || src[pos] == 'e' || src[pos] == 'E' || src[pos] == '+' || src[pos] == '-')) pos++
            return src.substring(start, pos).toDoubleOrNull()
                ?: throw IllegalArgumentException("bad number at " + start)
        }

        private fun parseLiteral(word: String, value: Any?): Any? {
            require(src.startsWith(word, pos)) { "unexpected token at " + pos }
            pos += word.length
            return value
        }

        private fun expect(c: Char) {
            require(pos < src.length && src[pos] == c) { "expected '" + c + "' at " + pos }
            pos++
        }

        private fun peek(): Char = if (pos < src.length) src[pos] else throw IllegalArgumentException("unexpected end of input")

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }
    }
}
