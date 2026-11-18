package com.aiclassroom.classroom.gateway

/**
 * 冻结下来的模型策略。
 *
 * 只放**非敏感**信息：连接地址、模型与参数。密钥永远不在这里，
 * 快照、事件、导出和诊断都不会带上它。
 *
 * 模型顺序由宿主计算（主对话已有的 chatModels()/orderedModels()），
 * 这里只做中立整理 —— 不重新实现一套规则，否则两边迟早不一致。
 */
data class ModelPolicy(
    val provider: String,
    val baseUrl: String,
    /** 首选模型；空表示没有可用模型。 */
    val primaryModel: String,
    /** 降级顺序（不含首选）。 */
    val fallbackModels: List<String> = emptyList(),
    val temperature: Double? = null,
    val reasoningEffort: String? = null,
    val maxOutputTokens: Int? = null,
    val extraHeaders: Map<String, String> = emptyMap(),
    val capabilities: ModelCapabilities = ModelCapabilities(),
) {
    val models: List<String> get() = listOf(primaryModel) + fallbackModels
    val usable: Boolean get() = primaryModel.isNotBlank() && baseUrl.isNotBlank()
}

object ModelPolicyResolver {

    /**
     * 从宿主给出的**有序模型列表**整理成策略。
     *
     * @param orderedModels 宿主已经算好的顺序（含降级链、自定义模型、当前选择）。
     */
    fun resolve(
        provider: String,
        baseUrl: String,
        orderedModels: List<String>,
        temperature: Double? = null,
        reasoningEffort: String? = null,
        deepThinkingEnabled: Boolean = false,
        maxOutputTokens: Int? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        capabilities: ModelCapabilities = ModelCapabilities(),
        maxFallbacks: Int = 3,
    ): ModelPolicy {
        // 去空、去重，保持宿主给的顺序 —— 顺序本身就是策略。
        val models = orderedModels.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val primary = models.firstOrNull().orEmpty()
        val fallbacks = models.drop(1).take(maxFallbacks.coerceAtLeast(0))

        return ModelPolicy(
            provider = provider,
            baseUrl = baseUrl,
            primaryModel = primary,
            fallbackModels = fallbacks,
            // 不支持 temperature 的供应商就别塞这个参数，否则会直接 400。
            temperature = temperature.takeIf { capabilities.supportsTemperature },
            // 只有开了深度思考，reasoningEffort 才有意义。
            reasoningEffort = reasoningEffort?.takeIf { deepThinkingEnabled && capabilities.supportsReasoningEffort },
            maxOutputTokens = maxOutputTokens,
            extraHeaders = extraHeaders,
            capabilities = capabilities,
        )
    }
}
