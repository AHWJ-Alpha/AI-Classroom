package com.aiclassroom.classroom.gateway

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelPolicyTest {

    private fun resolve(
        models: List<String> = listOf("m1", "m2", "m3"),
        temperature: Double? = 0.3,
        reasoningEffort: String? = "high",
        deepThinking: Boolean = true,
        capabilities: ModelCapabilities = ModelCapabilities(supportsReasoningEffort = true),
        maxFallbacks: Int = 3,
    ) = ModelPolicyResolver.resolve(
        provider = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        orderedModels = models,
        temperature = temperature,
        reasoningEffort = reasoningEffort,
        deepThinkingEnabled = deepThinking,
        capabilities = capabilities,
        maxFallbacks = maxFallbacks,
    )

    @Test
    fun `首选是第一个 其余按宿主顺序作为降级`() {
        val p = resolve()
        assertEquals("m1", p.primaryModel)
        assertEquals(listOf("m2", "m3"), p.fallbackModels)
        assertEquals(listOf("m1", "m2", "m3"), p.models)
    }

    @Test
    fun `空白与重复会被去掉且保持顺序`() {
        val p = resolve(models = listOf("  m1 ", "", "m2", "m1", "m3"))
        assertEquals(listOf("m1", "m2", "m3"), p.models)
    }

    @Test
    fun `降级数量受上限约束`() {
        val p = resolve(models = listOf("a", "b", "c", "d", "e"), maxFallbacks = 2)
        assertEquals(listOf("b", "c"), p.fallbackModels)
    }

    @Test
    fun `没有可用模型时标记为不可用`() {
        assertFalse(resolve(models = emptyList()).usable)
        assertFalse(ModelPolicyResolver.resolve("p", "", listOf("m1")).usable)
        assertTrue(resolve().usable)
    }

    @Test
    fun `供应商不支持 temperature 时不带该参数`() {
        assertNull(resolve(capabilities = ModelCapabilities(supportsTemperature = false)).temperature)
        assertEquals(0.3, resolve().temperature)
    }

    @Test
    fun `没开深度思考就不带 reasoningEffort`() {
        assertNull(resolve(deepThinking = false).reasoningEffort)
        assertEquals("high", resolve().reasoningEffort)
    }

    @Test
    fun `供应商不支持思考强度时也不带`() {
        assertNull(resolve(capabilities = ModelCapabilities(supportsReasoningEffort = false)).reasoningEffort)
    }
}
