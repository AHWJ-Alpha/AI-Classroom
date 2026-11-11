package com.aiclassroom.app.util

/**
 * 课堂身份（讲师名 / 用户称呼）的来源解析。
 *
 * 优先用模型从对话里推断出来的，取不到再退回本地规则。
 */

import com.aiclassroom.app.model.MEMORY_PROMPT_LIMIT

internal fun parseClassroomIdentity(raw: String, fallbackSource: String): Pair<String, String>? {
    if (raw.isBlank() || isApiFailure(raw)) return localClassroomIdentity(fallbackSource)
    val lines = raw.lines().map { it.trim().trim('"', '\'') }.filter { it.isNotBlank() }
    fun valueFor(label: String): String? = lines.firstOrNull { it.startsWith(label) }
        ?.let { line ->
            when {
                line.contains('：') -> line.substringAfter('：')
                line.contains(':') -> line.substringAfter(':')
                else -> ""
            }
        }?.trim()
        ?.takeIf { it.isNotBlank() }
    val name = valueFor("名称") ?: valueFor("名字")
    val summary = valueFor("简介") ?: valueFor("摘要")
    return if (name != null && summary != null) {
        name.replace(Regex("[。.!！?？]+$"), "").take(24) to summary.replace(Regex("\\s+"), " ").take(80)
    } else localClassroomIdentity(fallbackSource)
}

internal fun localClassroomIdentity(source: String): Pair<String, String>? {
    val clean = source.replace(Regex("\\s+"), " ").trim()
    if (clean.isBlank()) return null
    val phrase = clean.trimEnd('。', '！', '？', '.', '!', '?').take(16)
    val summary = clean.trimEnd('。', '！', '？', '.', '!', '?').let { if (it.length > 38) it.take(38) + "…" else it } + "。"
    return phrase to summary
}
