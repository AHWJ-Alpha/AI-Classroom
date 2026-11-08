package com.aiclassroom.app.plugin

import com.aiclassroom.feature.plugin.PluginLimits
import org.json.JSONArray
import org.json.JSONObject

/**
 * 把一组条目拼成 {"<key>":[...],"truncated":bool, ...extra} 的紧凑 JSON。
 *
 * 序列化后超过 [PluginLimits.MAX_RESULT_BYTES] 就从头砍掉尾部条目（对半砍，避免逐条重排），
 * 并把 truncated 置 true —— 宁可少给几条，也不能把整段历史灌进模型上下文。
 * HostFacade 那层还有一次按字节的硬截断，两层都在才算兜住。
 */
fun cappedJson(
    key: String,
    items: List<JSONObject>,
    extra: Map<String, Any> = emptyMap(),
): String {
    var keep = items.size
    while (true) {
        val array = JSONArray()
        for (index in 0 until keep) array.put(items[index])
        val root = JSONObject()
        for ((name, value) in extra) root.put(name, value)
        root.put(key, array)
        root.put("truncated", keep < items.size)
        val text = root.toString()
        if (text.toByteArray(Charsets.UTF_8).size <= PluginLimits.MAX_RESULT_BYTES || keep == 0) return text
        keep /= 2
    }
}
