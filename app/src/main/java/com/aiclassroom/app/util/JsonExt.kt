package com.aiclassroom.app.util

/** JSONArray 取值的小工具：null 当空列表处理。 */

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONArray?.toStrings(): List<String> = if (this == null) emptyList() else List(length()) { optString(it) }

internal fun JSONObject.optNonNullString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).takeUnless { it == "null" }
}

internal fun String.escapeJson(): String = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
