package com.aiclassroom.classroom.gateway

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 这一组测的是**最终 HTTP body 与 header**，不是 ModelPolicy 对象本身。
 *
 * 曾经的缺陷正是：策略对象字段齐全、单测全绿，但执行器构造请求时只填了 model，
 * 于是 temperature / reasoning_effort / 输出上限一个都没发出去。
 */
class PolicyReachesRequestTest {

    private class CapturingTransport(
        private val reply: suspend (HttpRequest, (String) -> Unit) -> HttpExchange,
    ) : HttpTransport {
        var lastRequest: HttpRequest? = null
        override suspend fun post(request: HttpRequest, onLine: (String) -> Unit): HttpExchange {
            lastRequest = request
            return reply(request, onLine)
        }
    }

    private class NoopSleeper : Sleeper {
        override suspend fun sleep(ms: Long) = Unit
    }

    private fun okBody(): suspend (HttpRequest, (String) -> Unit) -> HttpExchange = { _, onLine ->
        onLine("data: {\"model\":\"m1\",\"choices\":[{\"delta\":{\"content\":\"hi\"},\"finish_reason\":\"stop\"}]}")
        onLine("data: [DONE]")
        HttpExchange(status = 200, completed = true, actualModel = "m1")
    }

    private fun gateway(transport: HttpTransport) = HttpModelGateway(
        transport = transport,
        sleeper = NoopSleeper(),
        endpointOf = { "https://example.invalid/chat" },
        headersOf = { mapOf("Authorization" to "Bearer fake-key", "X-Trace" to "t1") },
        bodyBuilder = { req -> ChatRequestJson.build(req) },
        maxAttemptsPerModel = 1,
    )

    private fun policy(
        capabilities: ModelCapabilities = ModelCapabilities(
            supportsTemperature = true,
            supportsReasoningEffort = true,
        ),
        temperature: Double? = 0.31,
        reasoningEffort: String? = "high",
        maxOutputTokens: Int? = null,
    ) = ModelPolicyResolver.resolve(
        provider = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        orderedModels = listOf("m1", "m2"),
        temperature = temperature,
        reasoningEffort = reasoningEffort,
        deepThinkingEnabled = true,
        maxOutputTokens = maxOutputTokens,
        capabilities = capabilities,
    )

    @Test
    fun `温度与思考强度真的进了请求体`() = runBlocking {
        val transport = CapturingTransport(okBody())
        val call = gateway(transport).complete(
            ModelRequests.fromPolicy(
                policy = policy(),
                messages = listOf(ModelMessage(ModelMessage.Role.USER, "hi")),
            ),
        )
        assertEquals(null, call.error)
        val body = transport.lastRequest!!.body
        assertTrue(body.contains("0.31"), "温度没进请求体: " + body)
        assertTrue(body.contains("reasoning_effort"), "思考强度没进请求体: " + body)
        assertTrue(body.contains("high"))
    }

    @Test
    fun `供应商不支持时这两个参数不出现在请求体`() = runBlocking {
        val transport = CapturingTransport(okBody())
        val caps = ModelCapabilities(supportsTemperature = false, supportsReasoningEffort = false)
        gateway(transport).complete(
            ModelRequests.fromPolicy(
                policy = policy(capabilities = caps),
                messages = listOf(ModelMessage(ModelMessage.Role.USER, "hi")),
            ),
        )
        val body = transport.lastRequest!!.body
        assertFalse(body.contains("temperature"), "不该带温度: " + body)
        assertFalse(body.contains("reasoning_effort"), "不该带思考强度: " + body)
    }

    @Test
    fun `输出上限按供应商字段名发出`() = runBlocking {
        val transport = CapturingTransport(okBody())
        gateway(transport).complete(
            ModelRequests.fromPolicy(
                policy = policy(maxOutputTokens = 2048),
                messages = listOf(ModelMessage(ModelMessage.Role.USER, "hi")),
            ),
        )
        val body = transport.lastRequest!!.body
        assertTrue(body.contains("2048"), "输出上限没进去: " + body)
        assertTrue(body.contains("max_tokens") || body.contains("max_completion_tokens"))
    }

    @Test
    fun `凭据在请求头里而不是请求体里`() = runBlocking {
        val transport = CapturingTransport(okBody())
        gateway(transport).complete(
            ModelRequests.fromPolicy(
                policy = policy(),
                messages = listOf(ModelMessage(ModelMessage.Role.USER, "hi")),
            ),
        )
        val req = transport.lastRequest!!
        assertEquals("Bearer fake-key", req.headers["Authorization"])
        assertFalse(req.body.contains("fake-key"), "密钥不该出现在 body 里")
    }
}
