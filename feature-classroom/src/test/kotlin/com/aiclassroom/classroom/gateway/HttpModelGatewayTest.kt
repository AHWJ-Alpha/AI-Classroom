package com.aiclassroom.classroom.gateway

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpModelGatewayTest {

    /** 按脚本依次返回结果的假传输层；记录每次被调用时用的模型。 */
    private class FakeTransport(
        private val script: MutableList<suspend (HttpRequest, (String) -> Unit) -> HttpExchange>,
    ) : HttpTransport {
        val calledModels = mutableListOf<String>()
        override suspend fun post(request: HttpRequest, onLine: (String) -> Unit): HttpExchange {
            calledModels += request.body
            val step = script.removeFirstOrNull() ?: error("脚本用完了")
            return step(request, onLine)
        }
    }

    private class RecordingSleeper : Sleeper {
        val delays = mutableListOf<Long>()
        override suspend fun sleep(ms: Long) { delays += ms }
    }

    private fun ok(text: String, model: String = "m1"): suspend (HttpRequest, (String) -> Unit) -> HttpExchange =
        { _, onLine ->
            onLine("data: {\"model\":\"" + model + "\",\"choices\":[{\"delta\":{\"content\":\"" + text + "\"},\"finish_reason\":\"stop\"}]}")
            onLine("data: [DONE]")
            HttpExchange(status = 200, completed = true, actualModel = model)
        }

    private fun gateway(
        transport: HttpTransport,
        sleeper: Sleeper = RecordingSleeper(),
        maxAttempts: Int = 3,
    ) = HttpModelGateway(
        transport = transport,
        sleeper = sleeper,
        endpointOf = { "https://example.invalid/chat" },
        headersOf = { mapOf("Authorization" to "Bearer test") },
        bodyBuilder = { req -> req.model },
        maxAttemptsPerModel = maxAttempts,
    )

    private fun request(model: String = "m1", fallbacks: List<String> = emptyList()) = ModelRequest(
        model = model,
        messages = listOf(ModelMessage(ModelMessage.Role.USER, "hi")),
        fallbackModels = fallbacks,
    )

    @Test
    fun `429 之后重试成功并记录等待时间`() = runBlocking {
        val sleeper = RecordingSleeper()
        val transport = FakeTransport(
            mutableListOf(
                { _, _ -> HttpExchange(status = 429, completed = false, retryAfterMs = 1500) },
                ok("你好"),
            ),
        )
        val result = gateway(transport, sleeper).complete(request())

        assertNull(result.error)
        assertEquals("你好", result.content)
        assertEquals(listOf(1500L), sleeper.delays)
        assertEquals(2, transport.calledModels.size)
    }

    @Test
    fun `401 不重试也不降级`() = runBlocking {
        val transport = FakeTransport(mutableListOf({ _, _ -> HttpExchange(status = 401, completed = false) }))
        val result = gateway(transport).complete(request(fallbacks = listOf("m2")))

        assertIs<ModelCallError.Unauthorized>(result.error)
        assertEquals(1, transport.calledModels.size, "401 不应该换模型重试")
    }

    @Test
    fun `连接半截断会重试且不拼接上一次的半截内容`() = runBlocking {
        val transport = FakeTransport(
            mutableListOf(
                { _, onLine ->
                    onLine("data: {\"choices\":[{\"delta\":{\"content\":\"半截\"}}]}")
                    HttpExchange(status = 200, completed = false)
                },
                ok("完整内容"),
            ),
        )
        val result = gateway(transport).complete(request())

        assertNull(result.error)
        assertEquals("完整内容", result.content, "不能把两次尝试的内容粘一起")
    }

    @Test
    fun `重试用尽后降级到备用模型`() = runBlocking {
        val transport = FakeTransport(
            mutableListOf(
                { _, _ -> HttpExchange(status = 500, completed = false) },
                { _, _ -> HttpExchange(status = 500, completed = false) },
                { _, _ -> HttpExchange(status = 500, completed = false) },
                ok("备用模型的结果", model = "m2"),
            ),
        )
        val result = gateway(transport, maxAttempts = 3).complete(request(fallbacks = listOf("m2")))

        assertNull(result.error)
        assertEquals("备用模型的结果", result.content)
        assertEquals("m2", result.actualModel)
        assertEquals(4, transport.calledModels.size)
    }

    @Test
    fun `取消会抛出去而不是当作失败继续`() = runBlocking {
        val transport = FakeTransport(
            mutableListOf({ _, _ -> throw CancellationException("用户取消") }),
        )
        var thrown = false
        try {
            gateway(transport).complete(request(fallbacks = listOf("m2")))
        } catch (c: CancellationException) {
            thrown = true
        }
        assertTrue(thrown, "取消必须原样抛出")
        assertEquals(1, transport.calledModels.size)
    }

    @Test
    fun `传输层抛异常按网络错误重试`() = runBlocking {
        val transport = FakeTransport(
            mutableListOf(
                { _, _ -> throw RuntimeException("连接被重置") },
                ok("补上了"),
            ),
        )
        val result = gateway(transport).complete(request())
        assertNull(result.error)
        assertEquals("补上了", result.content)
    }

    @Test
    fun `4xx 非认证错误不降级也不重试`() = runBlocking {
        val transport = FakeTransport(mutableListOf({ _, _ -> HttpExchange(status = 400, completed = false, errorMessage = "bad request") }))
        val result = gateway(transport).complete(request(fallbacks = listOf("m2")))

        assertIs<ModelCallError.BadPayload>(result.error)
        assertEquals(1, transport.calledModels.size)
    }
}