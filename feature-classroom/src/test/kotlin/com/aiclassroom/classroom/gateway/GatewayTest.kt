package com.aiclassroom.classroom.gateway

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SseInterpreterTest {

    private fun feed(vararg lines: String): SseInterpreter {
        val i = SseInterpreter()
        lines.forEach(i::feedLine)
        return i
    }

    @Test
    fun `增量内容拼接且 DONE 标记被记录`() {
        val i = feed(
            "data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}",
            "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}",
            "data: [DONE]",
        )
        val r = i.finish(transportCompleted = true)
        assertEquals("你好", r.content)
        assertTrue(i.sawTerminator())
        assertNull(r.error)
    }

    @Test
    fun `连接中断且没有结束标记判定为截断`() {
        val i = feed("data: {\"choices\":[{\"delta\":{\"content\":\"半截\"}}]}")
        val r = i.finish(transportCompleted = false)
        assertIs<ModelCallError.Truncated>(r.error)
        assertEquals("半截", r.content)
    }

    @Test
    fun `finish_reason 为 length 时如实报告而不是当作正常完成`() {
        val i = feed("data: {\"choices\":[{\"delta\":{\"content\":\"x\"},\"finish_reason\":\"length\"}]}")
        val r = i.finish(transportCompleted = true)
        assertIs<FinishReason.Length>(r.finishReason)
    }

    @Test
    fun `缺失 finish_reason 保持 null 不伪造成 stop`() {
        val i = feed("data: {\"choices\":[{\"delta\":{\"content\":\"x\"}}]}")
        val r = i.finish(transportCompleted = true)
        assertNull(r.finishReason)
        assertFalse(i.sawTerminator())
    }

    @Test
    fun `服务端忽略 stream 直接整包 JSON 也能解析`() {
        val i = feed("data: {\"choices\":[{\"message\":{\"content\":\"整包\"},\"finish_reason\":\"stop\"}]}")
        val r = i.finish(transportCompleted = true)
        assertEquals("整包", r.content)
        assertEquals(FinishReason.Stop, r.finishReason)
    }

    @Test
    fun `没有 usage 时保持 null 而不是伪造 0`() {
        val i = feed("data: {\"choices\":[{\"delta\":{\"content\":\"x\"}}]}")
        assertNull(i.finish(transportCompleted = true).usage)
    }

    @Test
    fun `服务端返回 usage 时标记为来自服务端`() {
        val i = feed("data: {\"choices\":[{\"delta\":{\"content\":\"x\"}}],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":34}}")
        val u = i.finish(transportCompleted = true).usage!!
        assertEquals(12L, u.prompt)
        assertEquals(34L, u.completion)
        assertTrue(u.fromServer)
    }

    @Test
    fun `空响应判定为坏负载`() {
        val i = SseInterpreter()
        val r = i.finish(transportCompleted = true)
        assertIs<ModelCallError.BadPayload>(r.error)
    }
}

class RetryPolicyTest {

    @Test
    fun `401 不重试`() {
        val d = RetryPolicy.decide(ModelCallError.Unauthorized(401), 1)
        assertFalse(d.retry)
    }

    @Test
    fun `429 尊重服务端 Retry-After`() {
        val d = RetryPolicy.decide(ModelCallError.RateLimited(429, 2000), 1, serverRetryAfterMs = 2000)
        assertTrue(d.retry)
        assertEquals(2000, d.delayMs)
    }

    @Test
    fun `429 没有 Retry-After 时走指数退避`() {
        val d = RetryPolicy.decide(ModelCallError.RateLimited(429, null), 2)
        assertEquals(1000, d.delayMs)
    }

    @Test
    fun `5xx 可重试且退避递增`() {
        assertEquals(500, RetryPolicy.decide(ModelCallError.ServerError(500), 1).delayMs)
        assertEquals(1000, RetryPolicy.decide(ModelCallError.ServerError(503), 2).delayMs)
        assertEquals(2000, RetryPolicy.decide(ModelCallError.ServerError(502), 3, maxAttempts = 5).delayMs)
        assertTrue(RetryPolicy.decide(ModelCallError.ServerError(502), 3, maxAttempts = 5).retry)
    }

    @Test
    fun `达到最大次数后停止`() {
        assertFalse(RetryPolicy.decide(ModelCallError.Network("x"), 3, maxAttempts = 3).retry)
    }

    @Test
    fun `取消永不重试`() {
        assertFalse(RetryPolicy.decide(ModelCallError.Cancelled, 1).retry)
    }

    @Test
    fun `退避有上限`() {
        assertEquals(30_000, RetryPolicy.backoff(20))
    }
}

class StructuredPayloadTest {

    @Test
    fun `从代码块里取出 JSON`() {
        val text = "好的，这是结果：" + Char(96).toString().repeat(3) + "json\n{\"a\":1}\n" + Char(96).toString().repeat(3) + " 希望有帮助"
        assertEquals("{\"a\":1}", StructuredPayload.extract(text))
    }

    @Test
    fun `没有代码块时直接截取平衡括号`() {
        assertEquals("{\"a\":{\"b\":2}}", StructuredPayload.extract("结果如下 {\"a\":{\"b\":2}} 完毕"))
    }

    @Test
    fun `半截 JSON 返回 null`() {
        assertNull(StructuredPayload.extract("{\"a\":[1,2"))
    }

    @Test
    fun `字符串里的括号不影响配对`() {
        assertEquals("{\"t\":\"}\"}", StructuredPayload.extract("{\"t\":\"}\"}"))
    }

    @Test
    fun `数组形式也能取出`() {
        assertEquals("[1,2,3]", StructuredPayload.extract("前缀 [1,2,3] 后缀"))
    }

    @Test
    fun `完全没有 JSON 时返回 null`() {
        assertNull(StructuredPayload.extract("这里没有结构化内容"))
    }
}