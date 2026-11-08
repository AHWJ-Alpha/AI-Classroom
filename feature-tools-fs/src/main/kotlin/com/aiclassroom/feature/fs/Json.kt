package com.aiclassroom.feature.fs

/**
 * 最小 JSON 工具：只服务于工具参数的解析与结果的拼装。
 * 本模块是纯 JVM 且刻意不引第三方依赖，因此这几个函数自己写。
 */
internal object Json {

    /** 把字符串转成 JSON 字面量（含首尾引号）。 */
    fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        for (ch in value) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch < ' ') append("\\u" + ch.code.toString(16).padStart(4, '0')) else append(ch)
            }
        }
        append('"')
    }

    fun bool(value: Boolean): String = if (value) "true" else "false"

    /** 解析一个 JSON 对象；空白字符串按"没有参数"处理。 */
    fun parseObject(text: String): Map<String, Any?> {
        val value = Parser(text).parseDocument() ?: return emptyMap()
        @Suppress("UNCHECKED_CAST")
        return value as? Map<String, Any?> ?: throw IllegalArgumentException("工具参数必须是 JSON 对象")
    }

    private class Parser(private val src: String) {
        private var index = 0

        fun parseDocument(): Any? {
            if (src.isBlank()) return null
            val value = readValue()
            skipWhitespace()
            if (index != src.length) fail("对象结束后还有多余字符")
            return value
        }

        private fun readValue(): Any? {
            skipWhitespace()
            if (index >= src.length) fail("参数意外结束")
            return when (val c = src[index]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> if (c == '-' || c in '0'..'9') readNumber() else fail("非法字符 '" + c + "'")
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                index++
                return result
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                result[key] = readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    '}' -> {
                        index++
                        return result
                    }
                    else -> fail("对象里缺少 ',' 或 '}'")
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                index++
                return result
            }
            while (true) {
                result.add(readValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    ']' -> {
                        index++
                        return result
                    }
                    else -> fail("数组里缺少 ',' 或 ']'")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (index >= src.length) fail("字符串没有结束")
                when (val c = src[index++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (index >= src.length) fail("转义字符不完整")
                        when (val escaped = src[index++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (index + 4 > src.length) fail("\\u 转义不完整")
                                val code = src.substring(index, index + 4).toIntOrNull(16)
                                    ?: fail("\\u 转义不是十六进制")
                                index += 4
                                sb.append(code.toChar())
                            }
                            else -> fail("不支持的转义 \\" + escaped)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): Double {
            val start = index
            if (peek() == '-') index++
            while (index < src.length && (src[index].isDigit() || src[index] in ".eE+-")) index++
            val text = src.substring(start, index)
            return text.toDoubleOrNull() ?: fail("非法数字 '" + text + "'")
        }

        private fun readLiteral(literal: String, value: Any?): Any? {
            if (!src.startsWith(literal, index)) fail("非法的字面量")
            index += literal.length
            return value
        }

        private fun skipWhitespace() {
            while (index < src.length && src[index].isWhitespace()) index++
        }

        private fun peek(): Char = if (index < src.length) src[index] else '\u0000'

        private fun expect(expected: Char) {
            skipWhitespace()
            if (index >= src.length || src[index] != expected) fail("期望 '" + expected + "'")
            index++
        }

        private fun fail(message: String): Nothing =
            throw IllegalArgumentException("JSON 解析失败（位置 " + index + "）：" + message)
    }
}
