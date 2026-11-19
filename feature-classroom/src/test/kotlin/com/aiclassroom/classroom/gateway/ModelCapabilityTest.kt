package com.aiclassroom.classroom.gateway

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 能力适配：推理模型不接受 temperature，普通模型不接受 reasoning_effort。
 *
 * 以前宿主把所有模型都当作「两个都支持」，降级时也只改模型名 ——
 * 结果换到推理模型后仍带着 temperature，某些供应商直接 400。
 */
class ModelCapabilityTest {

    private class RecordingTransport(
        private val replies: MutableList<suspend (HttpRequest, (String) -> Unit) -> HttpExchange>,
    ) : HttpTransport {
        val bodies = mutableListOf<String>()
        override suspend fun post(request: HttpRequest, onLine: (String) -> Unit): HttpExchange {
            bodies += request.body
            val step = replies.removeFirstOrNull() ?: error("脚本用完了")
            return step(request, onLine)
        }
    }

    private class NoopSleeper : Sleeper {
        override suspend fun sleep(ms: Long) = Unit
    }

    private fun fail(): suspend (HttpRequest, (String) -> Unit) -> HttpExchange = { _, _ ->
        HttpExchange(status = 500, completed = true, actualModel = null)
    }

    private fun ok(model: String): suspend (HttpRequest, (String) -> Unit) -> HttpExchange = { _, onLine ->
        onLine("data: {\"model\":\"" + model + "\",\"choices\":[{\"delta\":{\"content\":\"hi\"},\"finish_reason\":\"stop\"}]}")
        onLine("data: [DONE]")
        HttpExchange(status = 200, completed = true, actualModel = model)
    }

    @Test
    fun `推理模型不支持温度而支持思考强度`() {
        val caps = ModelCapabilityTable.of("o1-mini")
        assertFalse(caps.supportsTemperature)
        assertTrue(caps.supportsReasoningEffort)
        assertEquals(ModelCapabilities.MaxTokensField.MAX_COMPLETION_TOKENS, caps.maxTokensField)

        val ds = ModelCapabilityTable.of("deepseek-reasoner")
        assertFalse(ds.supportsTemperature)
        assertTrue(ds.supportsReasoningEffort)
    }

    @Test
    fun `普通模型支持温度但不支持思考强度`() {
        val caps = ModelCapabilityTable.of("gpt-4o-mini")
        assertTrue(caps.supportsTemperature)
        assertFalse(caps.supportsReasoningEffort)
        assertEquals(ModelCapabilities.MaxTokensField.MAX_TOKENS, caps.maxTokensField)
    }

    @Test
    fun `降级到推理模型时不再发送温度`() = runBlocking {
        val transport = RecordingTransport(mutableListOf(fail(), ok("o1-mini")))
        val gateway = HttpModelGateway(
            transport = transport,
            sleeper = NoopSleeper(),
            endpointOf = { "https://example.invalid/chat" },
            headersOf = { mapOf("Authorization" to "Bearer fake") },
            bodyBuilder = { req -> ChatRequestJson.build(req) },
            maxAttemptsPerModel = 1,
        )

        gateway.complete(
            ModelRequest(
                model = "gpt-4o-mini",
                messages = listOf(ModelMessage(ModelMessage.Role.USER, "hi")),
                temperature = 0.7,
                maxOutputTokens = 512,
                capabilities = ModelCapabilityTable.of("gpt-4o-mini"),
                fallbackModels = listOf("o1-mini"),
            ),
        )

        assertEquals(2, transport.bodies.size, "应该先失败再降级")
        assertTrue(transport.bodies[0].contains("temperature"), "主模型应带温度")
        assertFalse(transport.bodies[1].contains("temperature"), "推理模型不该带温度: " + transport.bodies[1])
        assertTrue(
            transport.bodies[1].contains("max_completion_tokens"),
            "推理模型的输出上限字段名不同: " + transport.bodies[1],
        )
    }
}
