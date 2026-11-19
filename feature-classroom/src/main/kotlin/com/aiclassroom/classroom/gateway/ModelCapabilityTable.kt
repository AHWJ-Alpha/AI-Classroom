package com.aiclassroom.classroom.gateway

/**
 * 按模型名推断能力，保守优先。
 *
 * 之前宿主把 supportsTemperature / supportsReasoningEffort 一律写成 true：
 * 推理模型（o1/o3/o4、*-reasoner）其实不接受 temperature，
 * 发过去轻则被忽略、重则直接 400；反过来普通模型收到 reasoning_effort 也会报错。
 *
 * 这里是**按名字猜**的保守规则，不是权威表；猜错时宁可少发参数。
 */
object ModelCapabilityTable {

    fun of(model: String): ModelCapabilities {
        val name = model.trim().lowercase()
        if (name.isEmpty()) return ModelCapabilities()

        val reasoning = name.contains("reasoner") ||
            Regex("^o[1-9]").containsMatchIn(name) ||
            name.contains("-o1") || name.contains("-o3")

        return if (reasoning) {
            ModelCapabilities(
                maxTokensField = ModelCapabilities.MaxTokensField.MAX_COMPLETION_TOKENS,
                supportsTemperature = false,
                supportsReasoningEffort = true,
            )
        } else {
            ModelCapabilities(
                maxTokensField = ModelCapabilities.MaxTokensField.MAX_TOKENS,
                supportsTemperature = true,
                supportsReasoningEffort = false,
            )
        }
    }
}
