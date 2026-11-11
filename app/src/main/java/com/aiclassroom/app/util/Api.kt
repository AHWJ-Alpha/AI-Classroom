package com.aiclassroom.app.util

/**
 * 与模型服务通信的请求构造与响应解析。
 *
 * - 请求：buildJson / buildVisionJson / messageJson / toolsJsonOf
 * - 响应：parseStreamDelta（SSE 增量）/ parseNonStreamTurn / parseToolCalls / isApiFailure
 * - 杂项：defaultBaseUrl / readBody / filterNsfw / 版本号比较
 */

import com.aiclassroom.app.model.ChatMessage
import com.aiclassroom.app.model.ChatTurn
import com.aiclassroom.app.model.ToolCall
import com.aiclassroom.feature.exam.tool.ToolRegistry
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import com.aiclassroom.app.util.optNonNullString
import com.aiclassroom.app.util.escapeJson
import java.io.BufferedReader
import java.io.InputStreamReader

internal fun defaultBaseUrl(provider: String) = when (provider) {
    "OpenAI" -> "https://api.openai.com/v1"
    "DeepSeek" -> "https://api.deepseek.com/v1"
    "通义千问" -> "https://dashscope.aliyuncs.com/compatible-mode/v1"
    else -> "https://api.openai.com/v1"
}

internal fun isRemoteVersionNewer(remote: String, local: String): Boolean {
    val remoteParts = versionParts(remote)
    val localParts = versionParts(local)
    val size = maxOf(remoteParts.size, localParts.size, 3)
    repeat(size) { index ->
        val r = remoteParts.getOrElse(index) { 0 }
        val l = localParts.getOrElse(index) { 0 }
        if (r != l) return r > l
    }
    return false
}

internal fun versionParts(value: String): List<Int> =
    Regex("\\d+").findAll(value).map { it.value.toIntOrNull() ?: 0 }.toList()

internal fun isApiFailure(result: String): Boolean {
    val text = result.lowercase()
    return result.startsWith("调用失败") || result.startsWith("请先") || text.contains("http 4") || text.contains("http 5") || text.contains("unauthorized") || text.contains("invalid api")
}

internal fun parseStreamDelta(payload: String): String = runCatching {
    val choice = JSONObject(payload).optJSONArray("choices")?.optJSONObject(0) ?: return@runCatching ""
    choice.optJSONObject("delta")?.optNonNullString("content")
        ?: choice.optJSONObject("message")?.optNonNullString("content")
        ?: ""
}.getOrDefault("")

internal fun parseToolCalls(toolCallsJson: String?): List<ToolCall> {
    if (toolCallsJson.isNullOrBlank()) return emptyList()
    val array = runCatching { JSONArray(toolCallsJson) }.getOrNull() ?: return emptyList()
    return List(array.length()) { position ->
        val item = array.optJSONObject(position) ?: JSONObject()
        val function = item.optJSONObject("function") ?: JSONObject()
        ToolCall(
            id = item.optNonNullString("id") ?: "call_$position",
            name = function.optNonNullString("name").orEmpty(),
            argumentsJson = function.optNonNullString("arguments").orEmpty().ifBlank { "{}" }
        )
    }.filter { it.name.isNotBlank() }
}

internal fun parseNonStreamTurn(body: String): ChatTurn {
    val choice = runCatching { JSONObject(body).optJSONArray("choices")?.optJSONObject(0) }.getOrNull()
    val message = choice?.optJSONObject("message") ?: choice?.optJSONObject("delta")
        ?: return ChatTurn("", null)
    val sink = ToolCallSink()
    message.optJSONArray("tool_calls")?.let { sink.mergeCalls(it) }
    return ChatTurn(message.optNonNullString("content").orEmpty(), sink.resultJson())
}

internal fun toolsJsonOf(registry: ToolRegistry): String? =
    registry.all().takeIf { it.isNotEmpty() }?.joinToString(",") { handler ->
        "{\"type\":\"function\",\"function\":{\"name\":\"${handler.name.escapeJson()}\",\"description\":\"${handler.description.escapeJson()}\",\"parameters\":${handler.parametersJson.ifBlank { "{\"type\":\"object\",\"properties\":{}}" }}}}"
    }

internal fun readBody(connection: HttpURLConnection): String {
    val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
    return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
}

internal fun buildJson(model: String, system: String, messages: List<ChatMessage>, stream: Boolean = false, deepThinkingEnabled: Boolean = false, reasoningEffort: String = "medium", toolsJson: String? = null, temperature: Double = 0.35): String {
    val all = listOf(ChatMessage("system", system)) + messages
    val jsonMessages = all.joinToString(",") { messageJson(it) }
    val normalizedEffort = reasoningEffort.lowercase()
    val safeEffort = when (normalizedEffort) {
        "low", "medium", "high" -> normalizedEffort
        else -> "medium"
    }
    val reasoning = if (deepThinkingEnabled) ",\"reasoning_effort\":\"$safeEffort\"" else ""
    // toolsJson 为空时这个片段是空串，请求体与改造前逐字节一致。
    val tools = toolsJson?.takeIf { it.isNotBlank() }?.let { ",\"tools\":[$it],\"tool_choice\":\"auto\"" }.orEmpty()
    val safeTemperature = temperature.coerceIn(0.0, 2.0)
    return "{\"model\":\"${model.escapeJson()}\",\"messages\":[$jsonMessages],\"temperature\":$safeTemperature,\"stream\":$stream$reasoning$tools}"
}

internal fun messageJson(message: ChatMessage): String = buildString {
    append("{\"role\":\"").append(message.role).append("\",\"content\":\"").append(message.text.escapeJson()).append('"')
    message.toolCallId?.takeIf { it.isNotBlank() }?.let { append(",\"tool_call_id\":\"").append(it.escapeJson()).append('"') }
    message.toolCallsJson?.takeIf { it.isNotBlank() }?.let { append(",\"tool_calls\":").append(it) }
    append('}')
}

internal fun buildVisionJson(model: String, system: String, prompt: String, dataUrls: List<String>, history: List<ChatMessage> = emptyList()): String {
    val imageBlocks = dataUrls.joinToString(",") { url ->
        "{\"type\":\"image_url\",\"image_url\":{\"url\":\"" + url.escapeJson() + "\"}}"
    }
    // 只带 user / assistant 气泡：history 里那条 system 是课堂状态包，已经有了自己的 system 槽位。
    val historyBlocks = history.filter { it.role == "user" || it.role == "assistant" }
        .joinToString("") { message ->
            ",{\"role\":\"" + message.role + "\",\"content\":\"" + message.text.take(2000).escapeJson() + "\"}"
        }
    return "{\"model\":\"" + model.escapeJson() +
        "\",\"messages\":[{\"role\":\"system\",\"content\":\"" + system.escapeJson() + "\"}" +
        historyBlocks +
        ",{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"" + prompt.escapeJson() + "\"}," +
        imageBlocks + "]}],\"temperature\":0.7}"
}

internal fun filterNsfw(text: String, enabled: Boolean): String {
    if (!enabled) return text
    val blocked = listOf("色情", "裸露", "约炮", "血腥", "自残", "自杀", "nsfw", "porn", "nude", "kill myself")
    return if (blocked.any { text.contains(it, ignoreCase = true) }) "高效模式已过滤不适合学习场景的内容。" else text
}

internal class ToolCallSink {
    private val ids = LinkedHashMap<Int, String>()
    private val names = LinkedHashMap<Int, String>()
    private val arguments = LinkedHashMap<Int, StringBuilder>()
    private var implicitSlot = 0
    private var rawJson: String? = null

    fun reset() {
        ids.clear()
        names.clear()
        arguments.clear()
        implicitSlot = 0
        rawJson = null
    }

    /** 非流式响应：整包的 message.tool_calls 原样保留。 */
    fun setRawJson(toolCallsJson: String?) {
        rawJson = toolCallsJson
    }

    /** 合并一条 SSE data 分片里的 choices[0].delta.tool_calls。 */
    fun mergeDelta(payload: String) {
        val calls = runCatching {
            JSONObject(payload).optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("delta")?.optJSONArray("tool_calls")
        }.getOrNull() ?: return
        mergeCalls(calls)
    }

    fun mergeCalls(calls: JSONArray) {
        for (position in 0 until calls.length()) {
            val item = calls.optJSONObject(position) ?: continue
            // 个别兼容实现省略 index：按出现顺序补位。
            val slot = if (item.has("index") && !item.isNull("index")) item.optInt("index") else implicitSlot++
            item.optNonNullString("id")?.takeIf { it.isNotBlank() }?.let { ids[slot] = it }
            val function = item.optJSONObject("function")
            function?.optNonNullString("name")?.takeIf { it.isNotBlank() }?.let { names[slot] = it }
            function?.optNonNullString("arguments")?.let { arguments.getOrPut(slot) { StringBuilder() }.append(it) }
        }
    }

    /** 归一化后的 tool_calls JSON 数组；没有调用时返回 null。 */
    fun resultJson(): String? {
        rawJson?.takeIf { it.isNotBlank() }?.let { return it }
        val slots = (ids.keys + names.keys + arguments.keys).distinct().sorted()
        if (slots.isEmpty()) return null
        val array = JSONArray()
        slots.forEach { slot ->
            array.put(JSONObject().apply {
                put("id", ids[slot] ?: "call_$slot")
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", names[slot].orEmpty())
                    put("arguments", arguments[slot]?.toString().orEmpty())
                })
            })
        }
        return array.toString()
    }
}
