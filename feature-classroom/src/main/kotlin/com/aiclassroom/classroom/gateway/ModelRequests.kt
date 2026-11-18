package com.aiclassroom.classroom.gateway

/**
 * 由冻结的策略构造请求。
 *
 * 存在的理由很具体：之前每个调用点各自只填 model，
 * 结果 policy 里的 temperature / reasoningEffort / 输出上限 / 能力标记
 * 一个都没进最终 body —— 单测测的是策略对象本身，所以一直没被发现。
 *
 * 现在所有阶段（规划、正文、审查、修订、状态提取）都从这一个函数出请求。
 */
object ModelRequests {

    fun fromPolicy(
        policy: ModelPolicy,
        messages: List<ModelMessage>,
        expectJson: Boolean = false,
        stream: Boolean = true,
        modelOverride: String? = null,
    ): ModelRequest {
        val model = modelOverride?.takeIf { it.isNotBlank() } ?: policy.primaryModel
        return ModelRequest(
            model = model,
            messages = messages,
            maxOutputTokens = policy.maxOutputTokens,
            temperature = policy.temperature,
            reasoningEffort = policy.reasoningEffort,
            stream = stream,
            expectJson = expectJson,
            capabilities = policy.capabilities,
            fallbackModels = policy.fallbackModels,
        )
    }
}
