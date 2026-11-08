package com.aiclassroom.feature.exam.tool.config

import com.aiclassroom.feature.exam.tool.ToolHandler
import com.aiclassroom.feature.exam.tool.ToolRegistry
import com.aiclassroom.feature.exam.tool.ToolResult
import com.aiclassroom.feature.exam.tool.exam.JsonFormatException
import com.aiclassroom.feature.exam.tool.exam.JsonValue
import com.aiclassroom.feature.exam.tool.exam.asObjectOrNull
import com.aiclassroom.feature.exam.tool.exam.asStringOrNull
import com.aiclassroom.feature.exam.tool.exam.jsonArray
import com.aiclassroom.feature.exam.tool.exam.jsonObject
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

private const val MAX_MODEL_CHAIN = 20
private const val MAX_BASE_URL_LENGTH = 2048

/**
 * 课堂配置工具集（config_get / config_set_model / config_set_base_url /
 * config_set_temperature / config_set_provider）。
 *
 * 安全边界（刻意设计，不要放宽）：
 * - API Key 没有写入入口：允许模型改写自己的 key，等于把提示词注入交给攻击者去换服务器地址。
 * - config_get 只按 [ConfigKeys.PUBLIC_KEYS] 白名单取值；apiKey / visionApiKey / ttsApiKey
 *   一律输出 [ConfigKeys.SECRET_MASK]，宿主在 read() 里多放的键也不会外泄。
 * - BaseURL 改动会通过 [ConfigAccess.audit] 回调交给宿主落审计日志。
 *
 * 不持有全局单例：所有读写都走构造注入的 [access]。execute 永不抛异常。
 */
class ConfigTools(private val access: ConfigAccess) {

    /** 按注册顺序暴露给模型的工具。 */
    val handlers: List<ToolHandler> = listOf(
        GetTool(),
        SetModelTool(),
        SetBaseUrlTool(),
        SetTemperatureTool(),
        SetProviderTool(),
    )

    /** 阶段集成的接入方式：[registry] 里已有同名工具会被覆盖。 */
    fun register(registry: ToolRegistry): ConfigTools {
        handlers.forEach(registry::register)
        return this
    }

    // ---------- 工具实现 ----------

    private inner class GetTool : ToolHandler {
        override val name: String = "config_get"
        override val description: String =
            "读取当前课堂的模型与 API 配置：provider、baseUrl、selectedModel、modelChain（含拆好的 modelChainList）、temperature、深度思考相关字段等。" +
                "API Key 一律脱敏成 *** 只用来告诉你「配没配」，取值永远不返回。"
        override val parametersJson: String = ConfigToolSchemas.GET

        override suspend fun execute(argumentsJson: String): ToolResult = guarded {
            parseArguments(argumentsJson)
            val config = readConfig()
            success("已读取当前课堂配置；API Key 一律脱敏为 " + ConfigKeys.SECRET_MASK + "。", config)
        }
    }

    private inner class SetModelTool : ToolHandler {
        override val name: String = "config_set_model"
        override val description: String =
            "切换当前课堂的对话模型，可选地同时重设模型优先级链。写入后 selectedModel 与 modelChain 首位都是新模型（customModel 清空，" +
                "避免旧的自定义模型名继续覆盖）。只能改模型名，改不了 API Key。"
        override val parametersJson: String = ConfigToolSchemas.SET_MODEL

        override suspend fun execute(argumentsJson: String): ToolResult = guarded {
            val arguments = parseArguments(argumentsJson)
            val model = modelName(arguments.string("model"))
            val requestedChain = arguments.stringListOrNull("modelChain")
            val existingChain = chainOf(value(readConfig(), ConfigKeys.MODEL_CHAIN))
            val chain = (listOf(model) + (requestedChain ?: existingChain).filterNot { it == model }).distinct()
            if (chain.size > MAX_MODEL_CHAIN) {
                throw ToolFailure("模型链最多 " + MAX_MODEL_CHAIN + " 个模型，收到 " + chain.size + " 个")
            }
            writeKey(ConfigKeys.SELECTED_MODEL, model)
            writeKey(ConfigKeys.CUSTOM_MODEL, "")
            writeKey(ConfigKeys.MODEL_CHAIN, chain.joinToString("\n"))
            val config = readConfig()
            val message = "已把当前对话模型切换为「" + model + "」；模型优先级：" +
                chain.joinToString(" → ") + deepThinkingNote(config)
            success(message, config)
        }
    }

    private inner class SetBaseUrlTool : ToolHandler {
        override val name: String = "config_set_base_url"
        override val description: String =
            "修改当前课堂的 API Base URL。只接受带主机名的 http / https 地址，其它输入一律拒绝；" +
                "每次成功修改都会记一条审计日志（含新旧值）。改地址不会顺带改 API Key。"
        override val parametersJson: String = ConfigToolSchemas.SET_BASE_URL

        override suspend fun execute(argumentsJson: String): ToolResult = guarded {
            val arguments = parseArguments(argumentsJson)
            val baseUrl = validateBaseUrl(arguments.string("baseUrl"))
            val previous = value(readConfig(), ConfigKeys.BASE_URL)
            writeKey(ConfigKeys.BASE_URL, baseUrl)
            val auditNote = recordAudit(previous, baseUrl)
            val plaintextNote = if (baseUrl.startsWith("http://", ignoreCase = true)) {
                "；注意：http:// 是明文传输，请求内容与密钥可能被中间人看到"
            } else {
                ""
            }
            val message = "Base URL 已改为「" + baseUrl + "」（原值「" +
                previous.ifBlank { "空" } + "」）" + plaintextNote + auditNote
            success(message, readConfig())
        }
    }

    private inner class SetTemperatureTool : ToolHandler {
        override val name: String = "config_set_temperature"
        override val description: String =
            "设置采样温度，允许范围 0.0–2.0：越小回答越稳定，越大越发散。超出范围的数值会被拒绝。"
        override val parametersJson: String = ConfigToolSchemas.SET_TEMPERATURE

        override suspend fun execute(argumentsJson: String): ToolResult = guarded {
            val arguments = parseArguments(argumentsJson)
            val temperature = arguments.double("temperature")
            if (temperature < 0.0 || temperature > 2.0) {
                throw ToolFailure(
                    "temperature 必须在 0.0–2.0 之间，收到 " + formatTemperature(temperature)
                )
            }
            val stored = formatTemperature(temperature)
            writeKey(ConfigKeys.TEMPERATURE, stored)
            success("温度已设为 " + stored + "（允许范围 0.0–2.0）", readConfig())
        }
    }

    private inner class SetProviderTool : ToolHandler {
        override val name: String = "config_set_provider"
        override val description: String =
            "切换当前课堂的 provider 名称（如 OpenAI / DeepSeek / 通义千问 / 自定义）。只改名称标签，" +
                "不会动 BaseURL 与 API Key；要换服务地址请用 config_set_base_url。"
        override val parametersJson: String = ConfigToolSchemas.SET_PROVIDER

        override suspend fun execute(argumentsJson: String): ToolResult = guarded {
            val arguments = parseArguments(argumentsJson)
            val provider = providerName(arguments.string("provider"))
            val previous = value(readConfig(), ConfigKeys.PROVIDER)
            writeKey(ConfigKeys.PROVIDER, provider)
            success(
                "provider 已从「" + previous.ifBlank { "空" } + "」切换为「" + provider + "」",
                readConfig(),
            )
        }
    }

    // ---------- 配置读写 ----------

    private fun readConfig(): Map<String, String> = try {
        access.read()
    } catch (throwable: Throwable) {
        throw ToolFailure("读取课堂配置失败：" + describe(throwable))
    }

    private fun value(config: Map<String, String>, key: String): String = config[key]?.trim().orEmpty()

    private fun writeKey(key: String, updated: String) {
        if (ConfigKeys.SECRET_KEYS.contains(key)) {
            throw ToolFailure("不允许通过工具写入 " + key)
        }
        val result = try {
            access.write(key, updated)
        } catch (throwable: Throwable) {
            throw ToolFailure("写入 " + key + " 失败：" + describe(throwable))
        }
        result.exceptionOrNull()?.let { failure ->
            throw ToolFailure("写入 " + key + " 失败：" + describe(failure))
        }
    }

    private fun recordAudit(previous: String, updated: String): String = try {
        access.audit(
            ConfigAuditEvent(
                tool = "config_set_base_url",
                key = ConfigKeys.BASE_URL,
                oldValue = previous,
                newValue = updated,
                timestampMillis = System.currentTimeMillis(),
            )
        )
        ""
    } catch (throwable: Throwable) {
        "；审计回调失败（改动已生效）：" + describe(throwable)
    }

    // ---------- 校验 ----------

    private fun modelName(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) throw ToolFailure("参数 model 不能为空")
        if (text.length > 200) throw ToolFailure("参数 model 太长（上限 200 字符）")
        if (text.any { it.isISOControl() }) throw ToolFailure("参数 model 不能包含换行或控制字符")
        return text
    }

    private fun providerName(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) throw ToolFailure("参数 provider 不能为空")
        if (text.length > 64) throw ToolFailure("参数 provider 太长（上限 64 字符）")
        if (text.any { it.isISOControl() }) throw ToolFailure("参数 provider 不能包含换行或控制字符")
        return text
    }

    private fun validateBaseUrl(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) throw ToolFailure("参数 baseUrl 不能为空")
        if (text.length > MAX_BASE_URL_LENGTH) {
            throw ToolFailure("baseUrl 太长（上限 " + MAX_BASE_URL_LENGTH + " 字符）")
        }
        if (text.any { it.isWhitespace() || it.isISOControl() }) {
            throw ToolFailure("baseUrl 不能包含空白或控制字符")
        }
        val uri = try {
            URI(text)
        } catch (failure: URISyntaxException) {
            throw ToolFailure("baseUrl 不是合法 URL：" + (failure.reason ?: failure.message ?: "格式错误"))
        }
        val scheme = uri.scheme?.lowercase(Locale.US)
            ?: throw ToolFailure("baseUrl 必须带协议头，例如 https://api.openai.com/v1")
        if (scheme != "http" && scheme != "https") {
            throw ToolFailure("baseUrl 只支持 http / https，收到「" + scheme + "」")
        }
        val host = uri.host
        if (host.isNullOrBlank()) {
            throw ToolFailure("baseUrl 缺少主机名，例如 https://api.openai.com/v1")
        }
        if (host.contains('/') || host.contains(' ') || !host.contains('.')) {
            throw ToolFailure("baseUrl 主机名非法：" + host)
        }
        return text
    }

    /** 模型链：一行一个，同时宽容接受逗号 / 分号分隔，按出现顺序去重。 */
    private fun chainOf(raw: String): List<String> =
        raw.split('\n', ',', '，', ';', '；')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    private fun formatTemperature(value: Double): String {
        val text = String.format(Locale.US, "%.4f", value).trimEnd('0')
        return if (text.endsWith(".")) text + "0" else text
    }

    private fun deepThinkingNote(config: Map<String, String>): String {
        val enabled = value(config, ConfigKeys.DEEP_THINKING_ENABLED).equals("true", ignoreCase = true)
        val deepModel = value(config, ConfigKeys.DEEP_THINKING_MODEL)
        return if (enabled && deepModel.isNotEmpty()) {
            "；注意：当前已开启深度思考，实际优先使用「" + deepModel + "」，关闭深度思考后新模型才会生效"
        } else {
            ""
        }
    }

    // ---------- 参数解析 ----------

    private fun parseArguments(argumentsJson: String): Map<String, JsonValue> {
        val trimmed = argumentsJson.trim()
        if (trimmed.isEmpty()) return emptyMap()
        val parsed = try {
            JsonValue.parse(trimmed)
        } catch (failure: JsonFormatException) {
            throw ToolFailure("参数不是合法 JSON：" + failure.message)
        } catch (throwable: Throwable) {
            throw ToolFailure("参数不是合法 JSON")
        }
        return parsed.asObjectOrNull() ?: throw ToolFailure("参数必须是 JSON 对象")
    }

    /** 键存在且不是 null。用于区分「没传」和「传了空字符串」。 */
    private fun Map<String, JsonValue>.provided(key: String): Boolean =
        containsKey(key) && this[key] !is JsonValue.Null

    private fun Map<String, JsonValue>.string(key: String): String? {
        if (!provided(key)) return null
        return this[key]?.asStringOrNull() ?: throw ToolFailure("参数 " + key + " 必须是字符串")
    }

    private fun Map<String, JsonValue>.stringListOrNull(key: String): List<String>? {
        if (!provided(key)) return null
        val raw = this[key]
        if (raw is JsonValue.Arr) {
            val items = raw.items.mapNotNull { item ->
                val text = item.asStringOrNull()
                    ?: throw ToolFailure("参数 " + key + " 必须是字符串数组")
                text.trim().takeIf { it.isNotEmpty() }
            }.distinct()
            if (items.isEmpty()) throw ToolFailure("参数 " + key + " 不能是空数组")
            return items
        }
        val text = raw?.asStringOrNull()
            ?: throw ToolFailure("参数 " + key + " 必须是字符串数组")
        return chainOf(text).ifEmpty { throw ToolFailure("参数 " + key + " 不能是空数组") }
    }

    private fun Map<String, JsonValue>.double(key: String): Double {
        val raw = this[key] ?: throw ToolFailure("缺少参数 " + key)
        val number = when (raw) {
            is JsonValue.Num -> raw.value
            is JsonValue.Str -> raw.value.trim().toDoubleOrNull()
                ?: throw ToolFailure("参数 " + key + " 必须是数字")
            else -> throw ToolFailure("参数 " + key + " 必须是数字")
        }
        if (!number.isFinite()) throw ToolFailure("参数 " + key + " 必须是有限数字")
        return number
    }

    // ---------- 结果 ----------

    private fun success(message: String, config: Map<String, String>): ToolResult = ToolResult.Ok(
        JsonValue.write(
            jsonObject(
                "ok" to JsonValue.Bool(true),
                "message" to JsonValue.Str(message),
                "config" to publicConfigJson(config),
                "secrets" to secretsJson(config),
            )
        )
    )

    /** 只按白名单取值：宿主多返回的键（哪怕叫 proxyToken）不会出现在结果里。 */
    private fun publicConfigJson(config: Map<String, String>): JsonValue {
        val fields = ConfigKeys.PUBLIC_KEYS.map { key ->
            key to JsonValue.Str(value(config, key))
        }.toMutableList<Pair<String, JsonValue>>()
        fields += "modelChainList" to jsonArray(
            chainOf(value(config, ConfigKeys.MODEL_CHAIN)).map { JsonValue.Str(it) }
        )
        return jsonObject(*fields.toTypedArray())
    }

    private fun secretsJson(config: Map<String, String>): JsonValue = jsonObject(
        ConfigKeys.API_KEY to JsonValue.Str(ConfigKeys.SECRET_MASK),
        ConfigKeys.VISION_API_KEY to JsonValue.Str(ConfigKeys.SECRET_MASK),
        ConfigKeys.TTS_API_KEY to JsonValue.Str(ConfigKeys.SECRET_MASK),
        "configured" to jsonObject(
            ConfigKeys.API_KEY to JsonValue.Bool(value(config, ConfigKeys.API_KEY).isNotEmpty()),
            ConfigKeys.VISION_API_KEY to JsonValue.Bool(value(config, ConfigKeys.VISION_API_KEY).isNotEmpty()),
            ConfigKeys.TTS_API_KEY to JsonValue.Bool(value(config, ConfigKeys.TTS_API_KEY).isNotEmpty()),
        ),
    )

    private fun describe(throwable: Throwable): String =
        throwable.message ?: throwable::class.java.simpleName

    private fun guarded(block: () -> ToolResult): ToolResult = try {
        block()
    } catch (failure: ToolFailure) {
        ToolResult.Error(failure.message ?: "配置工具调用失败")
    } catch (throwable: Throwable) {
        ToolResult.Error("配置工具内部错误：" + describe(throwable))
    }
}

private class ToolFailure(message: String) : Exception(message)

/**
 * 阶段集成一行接入：
 * ```
 * registerConfigTools(registry, classroomConfigAccess)
 * ```
 * [access] 由主 app 用 `ClassroomConfig` 的适配器实现（见 ConfigAccess 的说明）。
 */
fun registerConfigTools(registry: ToolRegistry, access: ConfigAccess): ConfigTools =
    ConfigTools(access).register(registry)
