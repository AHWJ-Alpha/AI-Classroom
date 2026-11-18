package com.aiclassroom.classroom.gateway

import com.aiclassroom.classroom.domain.EpochMillis

/** 中立的消息类型：网关不依赖聊天气泡，也不依赖 Compose。 */
data class ModelMessage(val role: Role, val content: String) {
    enum class Role { SYSTEM, USER, ASSISTANT }
}

/** 供应商能力：不同 provider 支持的参数不同，不能一律强加。 */
data class ModelCapabilities(
    /** 用 max_completion_tokens 还是 max_tokens。 */
    val maxTokensField: MaxTokensField = MaxTokensField.MAX_TOKENS,
    val supportsStreaming: Boolean = true,
    val supportsJsonMode: Boolean = false,
    val supportsTemperature: Boolean = true,
    val supportsReasoningEffort: Boolean = false,
) {
    enum class MaxTokensField { MAX_TOKENS, MAX_COMPLETION_TOKENS, NONE }
}

data class ModelRequest(
    val model: String,
    val messages: List<ModelMessage>,
    val maxOutputTokens: Int? = null,
    val temperature: Double? = null,
    val reasoningEffort: String? = null,
    val stream: Boolean = true,
    /** 结构化输出：供应商支持就用 json mode，不支持则回退到严格提示 + 本地校验。 */
    val expectJson: Boolean = false,
    val capabilities: ModelCapabilities = ModelCapabilities(),
    /** 允许降级到的模型序列；空表示不降级。 */
    val fallbackModels: List<String> = emptyList(),
)

data class TokenUsage(
    val prompt: Long,
    val completion: Long,
    /** 服务端返回的是真实账单数据，本地估算必须标成 false。 */
    val fromServer: Boolean,
)

/**
 * 结束原因。
 *
 * 缺失就是 [Unknown]，绝不默认成 [Stop] ——
 * 「没有 finish_reason」和「正常写完」是两件不同的事，
 * 混起来就会把被截断的内容当成完稿。
 */
sealed interface FinishReason {
    data object Stop : FinishReason
    data class Length(val limit: Int?) : FinishReason
    data object ContentFilter : FinishReason
    data object ToolCalls : FinishReason
    data object Unknown : FinishReason
    data class Other(val raw: String) : FinishReason
}

/** 分类过的错误：调用方按类型决定行为，不再靠正文里有没有「调用失败」。 */
sealed interface ModelCallError {
    data class Unauthorized(val status: Int) : ModelCallError
    data class RateLimited(val status: Int, val retryAfterMs: Long?) : ModelCallError
    data class ServerError(val status: Int) : ModelCallError
    data class Network(val message: String) : ModelCallError
    data object Timeout : ModelCallError
    /** 连接中断在半截，流没有正常结束。 */
    data class Truncated(val reason: String) : ModelCallError
    data class BadPayload(val message: String) : ModelCallError
    data class Refused(val message: String) : ModelCallError
    data object Cancelled : ModelCallError
}

data class ModelCallResult(
    val content: String,
    val actualModel: String?,
    val finishReason: FinishReason?,
    val transportCompleted: Boolean,
    val structuredPayload: String?,
    val usage: TokenUsage?,
    val error: ModelCallError?,
    val diagnosticId: String?,
)

/**
 * 模型网关。
 *
 * 主对话与课堂共用连接能力，但各自组装上下文与业务：
 * 网关只负责「把请求发出去、把响应如实地讲清楚」。
 */
interface ModelGateway {
    suspend fun complete(request: ModelRequest): ModelCallResult
}
