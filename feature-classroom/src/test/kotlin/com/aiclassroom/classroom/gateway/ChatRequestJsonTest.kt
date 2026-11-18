package com.aiclassroom.classroom.gateway

import com.aiclassroom.classroom.json.JsonParser
import com.aiclassroom.classroom.json.asArray
import com.aiclassroom.classroom.json.asBool
import com.aiclassroom.classroom.json.asObject
import com.aiclassroom.classroom.json.asString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatRequestJsonTest {

    private fun request(
        capabilities: ModelCapabilities = ModelCapabilities(),
        maxTokens: Int? = null,
        expectJson: Boolean = false,
        temperature: Double? = 0.3,
    ) = ModelRequest(
        model = "m1",
        messages = listOf(
            ModelMessage(ModelMessage.Role.SYSTEM, "你是老师"),
            ModelMessage(ModelMessage.Role.USER, "讲一下"),
        ),
        maxOutputTokens = maxTokens,
        temperature = temperature,
        expectJson = expectJson,
        capabilities = capabilities,
    )

    private fun parse(request: ModelRequest) = JsonParser.parse(ChatRequestJson.build(request)).asObject()

    @Test
    fun `基本字段与角色小写`() {
        val o = parse(request())
        assertEquals("m1", o["model"].asString())
        assertTrue(o["stream"].asBool() == true)
        val messages = o["messages"].asArray()
        assertEquals("system", messages[0].asObject()["role"].asString())
        assertEquals("user", messages[1].asObject()["role"].asString())
    }

    @Test
    fun `默认使用 max_tokens`() {
        val o = parse(request(maxTokens = 1000))
        assertEquals(1000, o["max_tokens"].asObject().let { 1000 })
        assertTrue(ChatRequestJson.build(request(maxTokens = 1000)).contains("\"max_tokens\":1000"))
    }

    @Test
    fun `供应商要求 max_completion_tokens 时不发 max_tokens`() {
        val body = ChatRequestJson.build(
            request(capabilities = ModelCapabilities(maxTokensField = ModelCapabilities.MaxTokensField.MAX_COMPLETION_TOKENS), maxTokens = 500),
        )
        assertTrue(body.contains("\"max_completion_tokens\":500"))
        assertTrue(!body.contains("\"max_tokens\""))
    }

    @Test
    fun `不支持 temperature 的供应商不发送该字段`() {
        val o = parse(request(capabilities = ModelCapabilities(supportsTemperature = false)))
        assertNull(o["temperature"].asString())
        assertTrue(!ChatRequestJson.build(request(capabilities = ModelCapabilities(supportsTemperature = false))).contains("temperature"))
    }

    @Test
    fun `支持 json mode 时才加 response_format`() {
        val withMode = ChatRequestJson.build(request(capabilities = ModelCapabilities(supportsJsonMode = true), expectJson = true))
        assertTrue(withMode.contains("response_format"))
        val without = ChatRequestJson.build(request(capabilities = ModelCapabilities(supportsJsonMode = false), expectJson = true))
        assertTrue(!without.contains("response_format"))
    }

    @Test
    fun `endpoint 拼接不会出双斜杠`() {
        assertEquals("https://a.com/v1/chat/completions", ChatRequestJson.endpoint("https://a.com/v1/"))
        assertEquals("https://a.com/v1/chat/completions", ChatRequestJson.endpoint("https://a.com/v1"))
    }
}