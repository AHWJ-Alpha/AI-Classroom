package com.aiclassroom.feature.plugin.ui

/**
 * 一次界面取数的结果。
 *
 * [values] 是**当前可用于渲染**的扁平 state（provider 失败时是上一次成功的值，可能是空表）；
 * [ok] 说明这一次调用是否真的成功，宿主据此决定要不要记日志；
 * [error] 只在 ok=false 时有值，是给日志看的一句中文/异常消息。
 */
data class UiProviderState(
    val values: Map<String, String>?,
    val ok: Boolean,
    val error: String?,
) {
    /** 转成 ui.json 取值表达式（state.xxx）能读的 JSON 文本。 */
    fun stateJson(): String? = values?.let { UiStateJson.encode(it) }
}

/** Map<String,String> -> 紧凑 JSON 对象文本。手写而不是引 org.json，是为了让本模块保持纯 JVM。 */
internal object UiStateJson {
    fun encode(values: Map<String, String>): String {
        val builder = StringBuilder("{")
        var first = true
        for ((key, value) in values) {
            if (!first) builder.append(',')
            first = false
            builder.append(quote(key)).append(':').append(quote(value))
        }
        return builder.append('}').toString()
    }

    private fun quote(text: String): String {
        val builder = StringBuilder(text.length + 2)
        builder.append('"')
        for (ch in text) {
            when (ch) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> if (ch < ' ') builder.append(String.format("\\u%04x", ch.code)) else builder.append(ch)
            }
        }
        return builder.append('"').toString()
    }
}
