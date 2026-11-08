package com.aiclassroom.feature.plugin.host

import com.aiclassroom.feature.plugin.Json
import com.aiclassroom.feature.plugin.JsonNode
import com.aiclassroom.feature.plugin.JsonParseException
import com.aiclassroom.feature.plugin.PluginStateStore

/**
 * ctx.storage / host.storageGet / host.storagePut 的实现：
 * 一个插件在 [PluginStateStore] 里存一个 JSON 对象（{key: value}），key-value 就建在它上面。
 *
 * 值尽量按 JSON 存（能解析就当 JSON，不能就当字符串），这样插件读到的原始文本
 * 与它写进去的文本一致，人也能直接看 prefs。
 */
class PluginKvStorage(private val store: PluginStateStore, private val pluginId: String) {

    fun get(key: String): String? {
        val node = fields()[key] ?: return null
        return if (node is JsonNode.Str) node.value else Json.write(node)
    }

    fun set(key: String, value: String) {
        val fields = fields().toMutableMap()
        fields[key] = parseOrString(value)
        store.write(pluginId, Json.write(JsonNode.Obj(fields)))
    }

    /** 给宿主 / 测试看的原始快照。 */
    fun snapshot(): String? = store.read(pluginId)

    private fun fields(): Map<String, JsonNode> {
        val raw = store.read(pluginId) ?: return emptyMap()
        val parsed = try {
            Json.parse(raw)
        } catch (e: JsonParseException) {
            return emptyMap()
        }
        return (parsed as? JsonNode.Obj)?.fields ?: emptyMap()
    }

    private fun parseOrString(value: String): JsonNode = try {
        Json.parse(value)
    } catch (e: JsonParseException) {
        JsonNode.Str(value)
    }
}
