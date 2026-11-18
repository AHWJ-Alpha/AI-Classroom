package com.aiclassroom.app.classroom

import com.aiclassroom.classroom.gateway.ChatRequestJson
import com.aiclassroom.classroom.gateway.ModelPolicy
import com.aiclassroom.classroom.gateway.ModelPolicyResolver
import com.aiclassroom.app.model.ClassroomConfig
import com.aiclassroom.classroom.gateway.HttpModelGateway
import com.aiclassroom.classroom.gateway.ModelGateway
import com.aiclassroom.classroom.gateway.Sleeper
import kotlinx.coroutines.delay

/**
 * 把宿主的连接配置接到公共网关上。
 *
 * 密钥只在执行时从这里解析，不写进项目、快照、导出或诊断 ——
 * 课堂数据里永远只有「模型策略引用」。
 */
object ClassroomGatewayFactory {

    /**
     * 把来源对话的**真实**模型策略转成冻结策略。
     *
     * 模型顺序直接用主对话已有的 chatModels()，不在这里重写一遍优先级 ——
     * 两套规则迟早会不一致，那时用户看到的降级顺序就不是实际发生的顺序。
     */
    internal fun policyOf(config: ClassroomConfig): ModelPolicy = ModelPolicyResolver.resolve(
        provider = config.provider,
        baseUrl = config.baseUrl,
        orderedModels = config.chatModels(),
        temperature = config.temperature,
        reasoningEffort = config.reasoningEffort,
        deepThinkingEnabled = config.deepThinkingEnabled,
        capabilities = com.aiclassroom.classroom.gateway.ModelCapabilities(
            supportsTemperature = true,
            supportsReasoningEffort = true,
        ),
    )

    fun create(
        policy: ModelPolicy,
        apiKey: String,
        maxAttemptsPerModel: Int = 3,
    ): ModelGateway {
        val headers = buildMap {
            if (apiKey.isNotBlank()) put("Authorization", "Bearer " + apiKey)
            putAll(policy.extraHeaders)
        }
        return HttpModelGateway(
            transport = AndroidHttpTransport(),
            sleeper = Sleeper { ms -> delay(ms) },
            endpointOf = { ChatRequestJson.endpoint(policy.baseUrl) },
            headersOf = { headers },
            bodyBuilder = { request -> ChatRequestJson.build(request) },
            maxAttemptsPerModel = maxAttemptsPerModel,
        )
    }
}