package com.aiclassroom.classroom.gateway

import com.aiclassroom.classroom.json.*

/**
 * SSE 解析器。
 *
 * 设计原则：**如实记录看到的东西**。
 * - 收到 `[DONE]`、看到 finish_reason、连接正常结束，三件事分开记；
 * - 内容还没写完连接就断了 → Truncated，而不是把半截当完整；
 * - 服务端忽略 stream 直接整包 JSON → 也能吃下。
 */
class SseInterpreter {

    private val content = StringBuilder()
    private var finish: FinishReason? = null
    private var done = false
    private var usage: TokenUsage? = null
    private var error: ModelCallError? = null
    private var actualModel: String? = null
    private var eventCount = 0
    /** 是否见过 `data:` 行；没有则说明服务端没走 SSE。 */
    private var sawDataLine = false

    fun feedLine(rawLine: String) {
        val line = rawLine.trimEnd('\r')
        if (line.isEmpty()) return
        if (line.startsWith(":")) return // 注释行

        if (!line.startsWith("data:")) {
            // event: error 之类先记下来，正文里可能没有 data
            if (line.startsWith("event:") && line.substringAfter(':').trim() == "error") {
                error = ModelCallError.BadPayload("服务端返回 error 事件")
            }
            return
        }

        sawDataLine = true
        val payload = line.removePrefix("data:").trim()
        if (payload == "[DONE]") {
            done = true
            return
        }
        if (payload.isEmpty()) return

        val json = runCatching { JsonParser.parse(payload) }.getOrNull()
        if (json == null) {
            // 不是 JSON 的 data 行（少数兼容服务直接吐文本）
            content.append(payload)
            eventCount++
            return
        }
        consumeEvent(json)
    }

    private fun consumeEvent(json: Json) {
        eventCount++
        val o = json.asObject()
        actualModel = o["model"].asString() ?: actualModel

        o["error"].takeIf { it !is Json.Null }?.let { err ->
            val message = err.asObject()["message"].asString() ?: "服务端返回错误"
            error = ModelCallError.BadPayload(message)
            return
        }

        useOf(o).let { if (it != null) usage = it }

        val choices = o["choices"].asArray()
        if (choices.isEmpty()) {
            // 非流式整包：content 在顶层
            o["content"].asString()?.let { content.append(it) }
            return
        }
        for (choice in choices) {
            val c = choice.asObject()
            c["delta"].asObject()["content"].asString()?.let { content.append(it) }
            c["message"].asObject()["content"].asString()?.let { content.append(it) }
            c["text"].asString()?.let { content.append(it) }
            val fr = c["finish_reason"].asString()
            if (fr != null) finish = mapFinish(fr)
        }
    }

    private fun useOf(o: Map<String, Json>): TokenUsage? {
        val u = o["usage"].asObject()
        if (u.isEmpty()) return null
        val prompt = u["prompt_tokens"].asLong() ?: 0L
        val completion = u["completion_tokens"].asLong() ?: 0L
        return TokenUsage(prompt = prompt, completion = completion, fromServer = true)
    }

    private fun mapFinish(raw: String): FinishReason = when (raw) {
        "stop" -> FinishReason.Stop
        "length" -> FinishReason.Length(null)
        "content_filter" -> FinishReason.ContentFilter
        "tool_calls", "function_call" -> FinishReason.ToolCalls
        "" -> FinishReason.Unknown
        else -> FinishReason.Other(raw)
    }

    /**
     * 收尾。[transportCompleted] 表示 HTTP 连接是否正常读完。
     */
    fun finish(transportCompleted: Boolean, structuredPayload: String? = null): ModelCallResult {
        val text = content.toString()
        val resolvedError: ModelCallError? = when {
            error != null -> error
            !transportCompleted -> ModelCallError.Truncated("连接中断，流未正常结束")
            !sawDataLine && text.isEmpty() -> ModelCallError.BadPayload("响应里没有任何内容")
            finish is FinishReason.Length -> null // 截断不是错误，但 finishReason 会告诉调用方
            else -> null
        }
        return ModelCallResult(
            content = text,
            actualModel = actualModel,
            finishReason = finish,
            transportCompleted = transportCompleted,
            structuredPayload = structuredPayload,
            usage = usage,
            error = resolvedError,
            diagnosticId = null,
        )
    }

    /** 是否收到过结束标记，调用方据此判断内容是否可信。 */
    fun sawTerminator(): Boolean = done || finish != null
    fun eventCount(): Int = eventCount
}
