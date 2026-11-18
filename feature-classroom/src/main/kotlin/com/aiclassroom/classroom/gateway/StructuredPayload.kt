package com.aiclassroom.classroom.gateway

/**
 * 从模型输出里提取结构化 JSON。
 *
 * 模型经常这样回答：一句客套话 + 代码块 + 一句总结。整体 parse 会失败，
 * 所以这里按「最外层括号配对」截取，并且**只在真正闭合时返回** ——
 * 半截 JSON 必须返回 null，让调用方进入待检查，而不是拿残缺数据当成功。
 */
object StructuredPayload {

    /** 反引号用 Char 拼出来，避免源码里出现三连反引号被当成 Kotlin 原始字符串。 */
    private val FENCE: String = Char(96).toString().repeat(3)

    fun extract(text: String): String? {
        var from = 0
        while (true) {
            val start = text.indexOf(FENCE, from)
            if (start < 0) break
            val end = text.indexOf(FENCE, start + FENCE.length)
            if (end < 0) break
            val inner = text.substring(start + FENCE.length, end).trim()
            val body = if (inner.startsWith("json")) inner.removePrefix("json").trim() else inner
            if (body.startsWith("{") || body.startsWith("[")) {
                extractBalanced(body)?.let { return it }
            }
            from = end + FENCE.length
        }
        return extractBalanced(text)
    }

    /** 扫描第一段括号平衡的子串；不平衡返回 null。字符串里的括号不计入深度。 */
    fun extractBalanced(text: String): String? {
        var start = -1
        var depth = 0
        var inString = false
        var escaped = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (start < 0) {
                if (c == '{' || c == '[') {
                    start = i
                    depth = 1
                }
                i++
                continue
            }
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                i++
                continue
            }
            when (c) {
                '"' -> inString = true
                '{', '[' -> depth++
                '}', ']' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
            i++
        }
        return null
    }
}