package com.aiclassroom.app.util

/** JSONArray 取值的小工具：null 当空列表处理。 */

import org.json.JSONArray

internal fun JSONArray?.toStrings(): List<String> = if (this == null) emptyList() else List(length()) { optString(it) }
