package com.aiclassroom.feature.plugin

/**
 * 插件能力的用量上限。核心是 [MAX_RESULT_BYTES]：任何返回给脚本的数据序列化后超过 32KB
 * 就截断并标记 truncated，避免一个插件把整段历史灌进模型上下文。
 */
object PluginLimits {

    /** 单次能力返回值的字节上限（UTF-8）。 */
    const val MAX_RESULT_BYTES = 32 * 1024

    /** readAllDialogs 每个课堂默认取多少条。 */
    const val MAX_DIALOGS_PER_CLASSROOM = 50

    /** readAllDialogs 单条消息正文最多保留多少字符。 */
    const val MAX_DIALOG_CHARS = 300

    /** timer.intervalSeconds 下限（秒）：小于 60 秒视为刷接口，直接拒绝。 */
    const val MIN_TIMER_INTERVAL_SECONDS = 60

    /** timer.intervalSeconds 上限（秒）：一天。 */
    const val MAX_TIMER_INTERVAL_SECONDS = 86_400

    /** 同一插件最多几个 timer 扩展点。 */
    const val MAX_TIMER_EXTENSIONS = 1

    /** 同一插件最多几个 event 扩展点。 */
    const val MAX_EVENT_EXTENSIONS = 8

    /** ui.json 的最大嵌套深度（根节点算第 1 层）。 */
    const val MAX_UI_DEPTH = 8

    /** 单份 ui.json 的最大节点数。 */
    const val MAX_UI_NODES = 200

    /** 按 UTF-8 字节截断，不切断半个字符；返回截断后的文本与被截断的标记。 */
    fun truncate(text: String, maxBytes: Int = MAX_RESULT_BYTES): TruncatedText {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return TruncatedText(text, false)
        var keep = maxBytes.coerceAtLeast(0)
        // 回退到字符边界：续字节（10xxxxxx）不能作为结尾。
        while (keep > 0 && (bytes[keep].toInt() and 0xC0) == 0x80) keep--
        val head = String(bytes, 0, keep, Charsets.UTF_8)
        return TruncatedText(head, true)
    }
}

/** [truncate] 的结果。 */
data class TruncatedText(val text: String, val truncated: Boolean)
