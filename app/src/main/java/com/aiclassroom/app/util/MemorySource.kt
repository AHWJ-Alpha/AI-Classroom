package com.aiclassroom.app.util

import com.aiclassroom.app.model.ChatMessage

internal data class MemorySourceBatch(
    val startIndex: Int,
    val endIndex: Int,
    val audience: List<String>,
    val text: String
)

/** 按消息顺序处理所有新增内容；长消息分片，不截掉最新消息，且不混合不同角色的来源。 */
internal fun memorySourceBatches(messages: List<ChatMessage>, startIndex: Int, budget: Int = 7000): List<MemorySourceBatch> {
    require(budget >= 256)
    val output = mutableListOf<MemorySourceBatch>()
    var content = StringBuilder()
    var start = 0
    var end = 0
    var audience = emptyList<String>()
    fun flush() {
        if (content.isNotEmpty()) output += MemorySourceBatch(start, end, audience, content.toString())
        content = StringBuilder()
    }
    messages.forEachIndexed { index, message ->
        if (index < startIndex || message.role !in setOf("user", "assistant") || message.text.isBlank()) return@forEachIndexed
        message.text.chunked(budget / 2).forEachIndexed { part, text ->
            val item = "[消息 $index；${message.role}；分片 $part]\n$text\n"
            if (content.isNotEmpty() && (message.audience != audience || content.length + item.length > budget)) flush()
            if (content.isEmpty()) { start = index; audience = message.audience.toList() }
            end = index
            content.append(item)
        }
    }
    flush()
    return output
}
