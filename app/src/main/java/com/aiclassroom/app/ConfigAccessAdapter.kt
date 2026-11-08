package com.aiclassroom.app

import android.util.Log
import com.aiclassroom.feature.exam.tool.config.ConfigAccess
import com.aiclassroom.feature.exam.tool.config.ConfigAuditEvent
import com.aiclassroom.feature.exam.tool.config.ConfigKeys

/**
 * 把 [ClassroomConfig] 接到 config_* 工具上的适配器。
 *
 * 两条约定（工具侧依赖它们）：
 * 1. 写操作必须同步生效：写完立刻能读到新值，否则工具回填给模型的结果与界面不一致。
 * 2. 密钥只读：本适配器照实把 apiKey 等交给 [ConfigAccess.read]，脱敏由工具侧按 whiteList 处理，
 *    且这里没有提供任何写入密钥的分支。
 */
internal class ConfigAccessAdapter(
    private val readConfig: () -> ClassroomConfig,
    private val writeConfig: (ClassroomConfig) -> Unit
) : ConfigAccess {

    override fun read(): Map<String, String> = with(readConfig()) {
        mapOf(
            ConfigKeys.PROVIDER to provider,
            ConfigKeys.BASE_URL to baseUrl,
            ConfigKeys.SELECTED_MODEL to selectedModel,
            ConfigKeys.CUSTOM_MODEL to customModel,
            ConfigKeys.MODEL_CHAIN to modelChain,
            ConfigKeys.TEMPERATURE to temperature.toString(),
            ConfigKeys.DEEP_THINKING_ENABLED to deepThinkingEnabled.toString(),
            ConfigKeys.DEEP_THINKING_MODEL to deepThinkingModel,
            ConfigKeys.REASONING_EFFORT to reasoningEffort,
            ConfigKeys.ACTIVE_CHAT_MODEL to activeChatModel,
            ConfigKeys.VISION_PROVIDER to visionProvider,
            ConfigKeys.VISION_BASE_URL to visionBaseUrl,
            ConfigKeys.VISION_MODEL to visionModel,
            ConfigKeys.TTS_PROVIDER to ttsProvider,
            ConfigKeys.TTS_BASE_URL to ttsBaseUrl,
            ConfigKeys.TTS_MODEL to ttsModel,
            ConfigKeys.TTS_VOICE to ttsVoice,
            // 密钥照实提供，工具侧的 PUBLIC_KEYS 白名单与脱敏逻辑保证它们不会进模型输出。
            ConfigKeys.API_KEY to apiKey,
            ConfigKeys.VISION_API_KEY to visionApiKey,
            ConfigKeys.TTS_API_KEY to ttsApiKey
        )
    }

    override fun write(key: String, value: String): Result<Unit> = runCatching {
        val current = readConfig()
        val updated = when (key) {
            ConfigKeys.PROVIDER -> current.copy(provider = value)
            ConfigKeys.BASE_URL -> current.copy(baseUrl = value)
            // 与 app 既有语义一致：切模型时清空自定义模型名，否则 UI 里旧的自定义名会盖住 selectedModel。
            ConfigKeys.SELECTED_MODEL -> current.copy(
                selectedModel = value,
                customModel = "",
                modelChain = (listOf(value) + current.modelChain.split('\n').map { it.trim() }
                    .filter { it.isNotBlank() && it != value }).joinToString("\n")
            )
            ConfigKeys.CUSTOM_MODEL -> current.copy(customModel = value)
            ConfigKeys.MODEL_CHAIN -> current.copy(modelChain = value)
            ConfigKeys.TEMPERATURE -> current.copy(
                temperature = value.toDoubleOrNull()?.coerceIn(0.0, 2.0)
                    ?: error("温度不是有效数字：$value")
            )
            ConfigKeys.DEEP_THINKING_ENABLED -> current.copy(
                deepThinkingEnabled = value.toBooleanStrictOrNull()
                    ?: error("不是布尔值：$value")
            )
            ConfigKeys.DEEP_THINKING_MODEL -> current.copy(deepThinkingModel = value)
            ConfigKeys.REASONING_EFFORT -> current.copy(reasoningEffort = value)
            else -> error("不支持写入 $key")
        }
        writeConfig(updated)
    }

    override fun audit(event: ConfigAuditEvent) {
        Log.i("ConfigAudit", "${event.tool} ${event.key}: ${event.oldValue} -> ${event.newValue}")
    }
}