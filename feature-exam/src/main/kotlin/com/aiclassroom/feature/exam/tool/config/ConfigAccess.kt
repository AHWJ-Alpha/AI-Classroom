package com.aiclassroom.feature.exam.tool.config

/**
 * 课堂配置的读写通道。
 *
 * feature-exam 是纯 JVM 模块，看不到 app 模块里的 `ClassroomConfig`，所以配置由宿主实现本接口后注入：
 * 工具只通过它读写，不依赖任何 Android API。
 *
 * 约定：
 * - [read] 返回「键 -> 值」的扁平快照，键名见 [ConfigKeys]；没设置的键可以不返回。
 * - [write] 返回 failure 表示这次写入没有生效，工具会把它转成 ToolResult.Error 回填给模型。
 * - [audit] 是审计回调，敏感改动（BaseURL）会在这里通知宿主落日志；默认空实现。
 */
interface ConfigAccess {
    fun read(): Map<String, String>

    fun write(key: String, value: String): Result<Unit>

    fun audit(event: ConfigAuditEvent) {}
}

/** 一次配置改动的审计记录，由 [ConfigAccess.audit] 交给宿主写日志。 */
data class ConfigAuditEvent(
    val tool: String,
    val key: String,
    val oldValue: String,
    val newValue: String,
    val timestampMillis: Long,
)

/**
 * 工具与宿主之间的配置键名，取值与 app 的 `ClassroomConfig` 字段同名，
 * 宿主适配器可以直接按同名属性映射。
 *
 * [SECRET_KEYS] 里的键只读（并且一律脱敏），工具没有任何写入它们的入口：
 * 允许模型改写 API Key 等于把「提示词注入 -> 请求打到攻击者服务器」变成可能。
 */
object ConfigKeys {
    const val PROVIDER = "provider"
    const val BASE_URL = "baseUrl"
    const val SELECTED_MODEL = "selectedModel"
    const val CUSTOM_MODEL = "customModel"
    const val MODEL_CHAIN = "modelChain"
    const val TEMPERATURE = "temperature"
    const val DEEP_THINKING_ENABLED = "deepThinkingEnabled"
    const val DEEP_THINKING_MODEL = "deepThinkingModel"
    const val REASONING_EFFORT = "reasoningEffort"
    const val ACTIVE_CHAT_MODEL = "activeChatModel"
    const val VISION_PROVIDER = "visionProvider"
    const val VISION_BASE_URL = "visionBaseUrl"
    const val VISION_MODEL = "visionModel"
    const val TTS_PROVIDER = "ttsProvider"
    const val TTS_BASE_URL = "ttsBaseUrl"
    const val TTS_MODEL = "ttsModel"
    const val TTS_VOICE = "ttsVoice"

    const val API_KEY = "apiKey"
    const val VISION_API_KEY = "visionApiKey"
    const val TTS_API_KEY = "ttsApiKey"

    /** 脱敏后统一显示的占位符。 */
    const val SECRET_MASK = "***"

    /** 只读密钥键：config_get 只输出掩码，任何工具都不写。 */
    val SECRET_KEYS: List<String> = listOf(API_KEY, VISION_API_KEY, TTS_API_KEY)

    /** config_get 允许暴露的键（白名单之外的键一律不输出，避免宿主多放的敏感字段外泄）。 */
    val PUBLIC_KEYS: List<String> = listOf(
        PROVIDER,
        BASE_URL,
        SELECTED_MODEL,
        CUSTOM_MODEL,
        MODEL_CHAIN,
        TEMPERATURE,
        DEEP_THINKING_ENABLED,
        DEEP_THINKING_MODEL,
        REASONING_EFFORT,
        ACTIVE_CHAT_MODEL,
        VISION_PROVIDER,
        VISION_BASE_URL,
        VISION_MODEL,
        TTS_PROVIDER,
        TTS_BASE_URL,
        TTS_MODEL,
        TTS_VOICE,
    )
}
