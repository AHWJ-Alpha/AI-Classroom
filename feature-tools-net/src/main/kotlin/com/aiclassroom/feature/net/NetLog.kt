package com.aiclassroom.feature.net

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 头名里出现这些关键字就按敏感处理：日志只留头名，值一律打码。 */
private val SENSITIVE_HEADER_KEYWORDS = listOf(
    "authorization", "auth", "cookie", "token", "key", "secret", "password", "signature", "session", "credential",
)

internal fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

internal fun isSensitiveHeader(name: String): Boolean {
    val lower = name.lowercase(Locale.US)
    return SENSITIVE_HEADER_KEYWORDS.any { lower.contains(it) }
}

/** 日志用的请求头：敏感值打码，其余值裁剪长度，避免日志本身泄漏或膨胀。 */
internal fun redactedHeaders(headers: Map<String, String>): String {
    if (headers.isEmpty()) return "{}"
    return headers.entries.joinToString(", ", "{", "}") { (name, value) ->
        name + "=" + if (isSensitiveHeader(name) || looksLikeSecret(value)) "***" else value.take(60)
    }
}

/** 头名不敏感、但值长得像凭据（Bearer / Basic / 一长串无空格字符）时也打码。 */
private fun looksLikeSecret(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.startsWith("Bearer ", ignoreCase = true) ||
        trimmed.startsWith("Basic ", ignoreCase = true) ||
        (trimmed.length >= 32 && !trimmed.contains(' '))
}

/** 一次调用的日志行：[时间, method, url, 状态码或错误, 耗时, 脱敏后的请求头]。 */
internal fun callLog(
    startedAt: Long,
    method: String,
    url: String,
    outcome: String,
    headers: Map<String, String> = emptyMap(),
): String {
    val elapsed = System.currentTimeMillis() - startedAt
    val head = "[" + timestamp() + "] net_request " + method + " " + url + " -> " + outcome + " (" + elapsed + "ms)"
    return if (headers.isEmpty()) head else head + " headers" + redactedHeaders(headers)
}
