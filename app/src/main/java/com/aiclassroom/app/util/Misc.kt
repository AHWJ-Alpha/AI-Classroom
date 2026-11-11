package com.aiclassroom.app.util

/**
 * 零散的小工具：模式标签、日期文案、身份推断、插件折叠状态。
 */

import com.aiclassroom.feature.exam.tool.universal.UniversalResponse
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun responseModeLabel(type: String): String = when (type) {
    UniversalResponse.TYPE_JSON -> "数据回给 AI"
    UniversalResponse.TYPE_JSON_URL -> "文件（URL 下载）"
    UniversalResponse.TYPE_JSON_BASE64 -> "文件（base64 解码）"
    UniversalResponse.TYPE_BINARY -> "文件（原始二进制）"
    else -> type
}

internal fun togglePluginConfig(current: Set<String>, pluginId: String): Set<String> =
    if (pluginId in current) current - pluginId else current + pluginId

internal fun todayDateText(): Pair<String, String> {
    val today = LocalDate.now()
    val week = when (today.dayOfWeek.value) {
        1 -> "星期一"
        2 -> "星期二"
        3 -> "星期三"
        4 -> "星期四"
        5 -> "星期五"
        6 -> "星期六"
        else -> "星期日"
    }
    return today.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)) to week
}

internal fun localStudyQuote(): String {
    val quotes = listOf(
        "把今天学透一点，明天就轻一点。",
        "慢慢来，深一点，稳一点。",
        "清晰的问题，会带来清晰的进步。",
        "先进入状态，再交给时间。",
        "每一轮理解，都会留下痕迹。"
    )
    return quotes[LocalDate.now().dayOfYear % quotes.size]
}
