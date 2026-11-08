package com.aiclassroom.feature.exam.tool.custom

import com.aiclassroom.feature.exam.tool.exam.JsonFormatException
import com.aiclassroom.feature.exam.tool.exam.JsonValue
import com.aiclassroom.feature.exam.tool.exam.asBooleanOrNull
import com.aiclassroom.feature.exam.tool.exam.asObjectOrNull
import com.aiclassroom.feature.exam.tool.exam.asStringOrNull
import com.aiclassroom.feature.exam.tool.exam.jsonArray
import com.aiclassroom.feature.exam.tool.exam.jsonObject
import com.aiclassroom.feature.exam.tool.image.ImageFailure
import com.aiclassroom.feature.exam.tool.universal.UniversalProfile
import com.aiclassroom.feature.exam.tool.universal.UniversalResponse

/**
 * 一个用户自定义插件 = 一个工具定义（名字 / 用途 / 参数）+ 一份 universal 执行配置。
 *
 * 为什么要合成一个 data class：注册成工具时 name / description / parametersJson 要原样交给模型，
 * 执行时必须拿到 profile 才能发请求，拆成两处存迟早会不同步。
 *
 * [parametersJson] 是 OpenAI function calling 的 parameters JSON Schema 原文；允许 "{}"（无参工具）。
 * [enabled] = false 时工具仍然注册（否则模型看不到、也无从知道被关掉了），但调用会被本地闸门直接拒绝。
 */
data class CustomToolSpec(
    val name: String,
    val description: String,
    val parametersJson: String,
    val profile: UniversalProfile,
    val enabled: Boolean = true,
) {

    /** 存进 prefs 的形态：profile 独立一层，和 universal profile 库的写法保持一致，方便互抄。 */
    internal fun toJsonValue(): JsonValue = jsonObject(
        "name" to JsonValue.Str(name),
        "description" to JsonValue.Str(description),
        "parametersJson" to JsonValue.Str(parametersJson),
        "enabled" to JsonValue.Bool(enabled),
        "profile" to profile.toJsonValue(),
    )

    companion object {

        /** 无参工具的标准写法（function calling 的 parameters 必须是一个对象 schema）。 */
        const val NO_PARAMETERS_JSON: String = """{"type":"object","properties":{}}"""

        /** 工具名规则：小写字母 / 数字 / 下划线，2-32 个字符。 */
        val NAME_PATTERN: Regex = Regex("^[a-z0-9_]{2,32}$")

        /** 内置工具已经占用的名字（与 ToolRegistry 里的注册名一致）。 */
        val BUILTIN_TOOL_NAMES: List<String> = listOf(
            "net_request",
            "generate_image",
            "probe_endpoint",
            "universal_call",
            "universal_get_json",
            "list_profiles",
        )

        /** 带前缀的内置插件命名空间：自定义工具不能借用，否则会撞上插件总开关的闸门。 */
        val RESERVED_TOOL_PREFIXES: List<String> = listOf("exam_", "fs_", "config_")

        /** prefs 里的 JSON 数组 → spec 列表。任何一项坏掉只丢那一项，不让整个设置页打不开。 */
        fun listFromJson(raw: String): List<CustomToolSpec> {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return emptyList()
            val root = try {
                JsonValue.parse(trimmed)
            } catch (failure: JsonFormatException) {
                return emptyList()
            }
            val items = when (root) {
                is JsonValue.Arr -> root.items
                is JsonValue.Obj -> (root.fields["tools"] as? JsonValue.Arr)?.items ?: listOf(root)
                else -> return emptyList()
            }
            val parsed = ArrayList<CustomToolSpec>(items.size)
            items.forEach { item ->
                if (item is JsonValue.Null) return@forEach
                try {
                    parsed.add(fromJsonValue(item))
                } catch (throwable: Throwable) {
                    // 坏的配置在这里静默跳过：设置页仍然能打开，用户可以删掉重配（这里纯解析，没有需要向上传的取消）。
                }
            }
            return parsed
        }

        /** spec 列表 → prefs 里的 JSON 数组。 */
        fun encodeList(specs: List<CustomToolSpec>): String =
            JsonValue.write(jsonArray(specs.map { it.toJsonValue() }))

        internal fun fromJsonValue(value: JsonValue): CustomToolSpec {
            val fields = value.asObjectOrNull() ?: throw ImageFailure("自定义插件必须是 JSON 对象")
            val name = stringField(fields, "name")?.trim().orEmpty()
            if (name.isEmpty()) throw ImageFailure("自定义插件缺少 name")
            val profileJson = fields["profile"] ?: throw ImageFailure("自定义插件「" + name + "」缺少 profile（执行配置）")
            val profile = UniversalProfile.fromJsonValue(profileJson)
            return CustomToolSpec(
                name = name,
                description = stringField(fields, "description")?.trim().orEmpty(),
                parametersJson = stringField(fields, "parametersJson")?.trim()?.ifEmpty { null } ?: NO_PARAMETERS_JSON,
                // profile 的 name 永远跟随工具名，result JSON 里的 profile 字段才和工具对得上。
                profile = profile.copy(name = name),
                enabled = fields["enabled"]?.asBooleanOrNull() ?: true,
            )
        }

        private fun stringField(fields: Map<String, JsonValue>, key: String): String? {
            val raw = fields[key] ?: return null
            if (raw is JsonValue.Null) return null
            return raw.asStringOrNull()
        }
    }
}

/**
 * 保存前的统一校验：返回错误列表，空列表 = 通过。UI 边打字边调它，保存按钮只在空列表时可点。
 *
 * [existingNames] 传「除自己以外」的全部已注册工具名（内置 + 其它自定义插件），
 * 所以编辑一个插件时不会因为它自己在表里而报重名。
 */
fun validateCustomTool(
    name: String,
    description: String,
    parametersJson: String,
    existingNames: Collection<String>,
): List<String> {
    val errors = ArrayList<String>()
    val cleanName = name.trim()

    if (cleanName.isEmpty()) {
        errors.add("工具名不能为空：小写字母 / 数字 / 下划线，2-32 个字符，例如 weather_today")
    } else if (!CustomToolSpec.NAME_PATTERN.matches(cleanName)) {
        errors.add(
            "工具名「" + cleanName + "」不合规：只能用 a-z、0-9 和 _，长度 2-32，不能大写、不能有空格或中文"
        )
    } else {
        val prefix = CustomToolSpec.RESERVED_TOOL_PREFIXES.firstOrNull { cleanName.startsWith(it) }
        if (prefix != null) {
            errors.add("工具名不能以 " + prefix + " 开头：那是内置插件的命名空间（" + CustomToolSpec.RESERVED_TOOL_PREFIXES.joinToString(" / ") + "）")
        }
        val taken = CustomToolSpec.BUILTIN_TOOL_NAMES.any { it.equals(cleanName, ignoreCase = true) } ||
            existingNames.any { it.equals(cleanName, ignoreCase = true) }
        if (taken) {
            errors.add("工具名「" + cleanName + "」已被占用（内置工具或另一个自定义插件），请换一个名字")
        }
    }

    val cleanDescription = description.trim()
    if (cleanDescription.isEmpty()) {
        errors.add("用途描述必填：写清楚「什么时候该用我」，这是 AI 决定要不要调用的唯一依据")
    } else if (cleanDescription.length < 4) {
        errors.add("用途描述太短了（至少 4 个字）：说明什么时候该用这个插件，例如「查询某城市当天的天气」")
    } else if (cleanDescription.length > 1000) {
        errors.add("用途描述太长了（上限 1000 字），一句话说清什么时候该用就够")
    }

    errors.addAll(parameterSchemaErrors(parametersJson))
    return errors
}

/** 参数定义必须是「合法的 JSON Schema 对象」，否则模型拿到的 function 定义会让整次请求失败。 */
private fun parameterSchemaErrors(parametersJson: String): List<String> {
    val raw = parametersJson.trim()
    if (raw.isEmpty()) {
        return listOf("参数定义不能为空：无参工具填 {} 或点「无参数」")
    }
    val root = try {
        JsonValue.parse(raw)
    } catch (failure: JsonFormatException) {
        return listOf("参数定义不是合法 JSON：" + (failure.message ?: "解析失败"))
    }
    val fields = root.asObjectOrNull()
        ?: return listOf("""参数定义必须是 JSON Schema 对象，例如 {} 或 {"type":"object","properties":{...}}""")

    val errors = ArrayList<String>()
    val type = fields["type"]
    if (type != null && type !is JsonValue.Null) {
        val typeText = type.asStringOrNull()
        if (typeText == null) {
            errors.add("""参数定义的 type 必须是字符串 "object"""")
        } else if (typeText.trim() != "object") {
            errors.add("""参数定义的 type 必须是 "object"，收到「""" + typeText + """」：函数的参数永远是一个对象""")
        }
    }

    val propertiesValue = fields["properties"]
    var properties: Map<String, JsonValue>? = null
    if (propertiesValue != null && propertiesValue !is JsonValue.Null) {
        properties = propertiesValue.asObjectOrNull()
        if (properties == null) {
            errors.add("参数定义的 properties 必须是 JSON 对象（参数名 → 子 schema）")
        } else {
            for ((key, value) in properties) {
                if (value is JsonValue.Null || value.asObjectOrNull() == null) {
                    errors.add("properties." + key + " 必须是 JSON Schema 对象，例如 {\"type\":\"string\"}")
                }
            }
        }
    }

    val requiredValue = fields["required"]
    if (requiredValue != null && requiredValue !is JsonValue.Null) {
        val requiredItems = (requiredValue as? JsonValue.Arr)?.items
        if (requiredItems == null) {
            errors.add("参数定义的 required 必须是字符串数组，例如 [\"city\"]")
        } else {
            requiredItems.forEachIndexed { index, item ->
                val requiredName = item.asStringOrNull()
                if (requiredName == null) {
                    errors.add("required[" + index + "] 必须是字符串")
                } else if (properties != null && !properties.containsKey(requiredName)) {
                    errors.add("required 里的「" + requiredName + "」在 properties 里没有定义")
                }
            }
        }
    }
    return errors
}

/** 执行配置的校验：和 [validateCustomTool] 分开，是因为它有自己一层的错误（地址 / 路径 / 取值）。 */
fun validateCustomToolExecution(profile: UniversalProfile): List<String> {
    val errors = ArrayList<String>()
    val base = profile.baseUrl.trim()
    if (base.isEmpty()) {
        errors.add("Base URL 不能为空，例如 http://127.0.0.1:48597")
    } else if (!base.startsWith("http://", ignoreCase = true) && !base.startsWith("https://", ignoreCase = true)) {
        errors.add("Base URL 只支持 http:// 或 https://，收到「" + base + "」")
    }
    if (profile.request.path.trim().isEmpty()) {
        errors.add("路径不能为空，例如 /version（会拼在 Base URL 后面）")
    }
    val responseType = profile.response.type
    if (responseType != UniversalResponse.TYPE_JSON && responseType != UniversalResponse.TYPE_BINARY) {
        if (profile.response.extract.trim().isEmpty()) {
            errors.add("响应类型是「文件」时必须填取值路径，例如 data[0].b64_json 或 results[0].url")
        }
    }
    return errors
}
