package com.aiclassroom.classroom.gateway

import com.aiclassroom.classroom.json.*
import kotlin.test.*

class JsonModeContractTest {
    @Test fun `启用供应商 JSON 模式时最终 HTTP 消息总有小写 json 指令`() {
        val original = ModelMessage(ModelMessage.Role.USER, "只输出结果：{\"issues\":[]}")
        val request = ModelRequest("deepseek-flash", listOf(original), expectJson = true,
            capabilities = ModelCapabilities(supportsJsonMode = true))
        val body = JsonParser.parse(ChatRequestJson.build(request)).asObject()
        val messages = body["messages"].asArray().map { it.asObject() }
        assertEquals("json_object", body["response_format"].asObject()["type"].asString())
        assertTrue(messages.any { "json" in it["content"].asString().orEmpty() })
        assertEquals(original.content, messages.last()["content"].asString())
        assertEquals(listOf(original), request.messages)
        val withoutJson = JsonParser.parse(ChatRequestJson.build(request.copy(expectJson = false))).asObject()
        assertEquals(1, withoutJson["messages"].asArray().size)
    }
}
