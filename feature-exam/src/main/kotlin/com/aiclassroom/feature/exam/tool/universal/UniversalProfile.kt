package com.aiclassroom.feature.exam.tool.universal

import com.aiclassroom.feature.exam.tool.exam.JsonFormatException
import com.aiclassroom.feature.exam.tool.exam.JsonValue
import com.aiclassroom.feature.exam.tool.exam.asBooleanOrNull
import com.aiclassroom.feature.exam.tool.exam.asIntOrNull
import com.aiclassroom.feature.exam.tool.exam.asObjectOrNull
import com.aiclassroom.feature.exam.tool.exam.asStringOrNull
import com.aiclassroom.feature.exam.tool.exam.jsonObject
import com.aiclassroom.feature.exam.tool.image.ImageFailure
import com.aiclassroom.feature.exam.tool.image.MAX_TIMEOUT_SECONDS

/**
 * 「一份配置 = 一个应用适配」里的那份配置。
 *
 * 字段与 universal_call 的参数一一对应：auth / request / response / async 就是一次调用
 * 手写的那四个对象，[UniversalProfile] 只是把它存成可分享的字符串。于是内置 profile 与
 * 临时手写参数走完全相同的执行链路（见 UniversalCaller）。
 *
 * 认证字段复用 ImageConfig 的语义：type 直接映射到 authType，token 映射到 apiKey，
 * header 映射到 authHeader，prefix 映射到 authPrefix——请求头与 query 拼装全部交给
 * ImageAuth.kt 的 buildAuth() / appendQueryAuth()，这里不重复实现一套。
 */
data class UniversalAuth(
    /** none / bearer / header / basic / query。 */
    val type: String = TYPE_NONE,
    /** type = header 时的请求头名；type = query 时的 query 参数名。 */
    val header: String = "",
    val token: String = "",
    val prefix: String = "",
) {
    companion object {
        const val TYPE_NONE: String = "none"
        const val TYPE_BEARER: String = "bearer"
        const val TYPE_HEADER: String = "header"
        const val TYPE_BASIC: String = "basic"
        const val TYPE_QUERY: String = "query"

        /** 与 ImageConfig 的 authType 完全同名的五种取值。 */
        val SUPPORTED: List<String> = listOf(TYPE_NONE, TYPE_BEARER, TYPE_HEADER, TYPE_BASIC, TYPE_QUERY)

        internal fun fromJson(value: JsonValue?, fallback: UniversalAuth): UniversalAuth {
            if (value == null || value === JsonValue.Null) return fallback
            val fields = value.asObjectOrNull() ?: throw ImageFailure("auth 必须是 JSON 对象")
            val type = text(fields, "type")?.trim()?.lowercase()?.ifEmpty { null } ?: fallback.type
            if (type !in SUPPORTED) {
                throw ImageFailure("auth.type 只支持 " + SUPPORTED.joinToString(" / ") + "，收到「" + type + "」")
            }
            return UniversalAuth(
                type = type,
                header = text(fields, "header")?.trim() ?: fallback.header,
                token = text(fields, "token") ?: fallback.token,
                prefix = text(fields, "prefix") ?: fallback.prefix,
            )
        }
    }
}

/** 请求模板：method + 路径 + 额外请求头 + 请求体模板（支持 {{prompt}} 等占位符）。 */
data class UniversalRequest(
    val method: String = "POST",
    val path: String = "",
    val headers: Map<String, String> = emptyMap(),
    val bodyTemplate: String = "",
) {
    companion object {
        val METHODS: List<String> = listOf("POST", "GET", "PUT", "PATCH", "DELETE", "HEAD")

        internal fun fromJson(value: JsonValue?, fallback: UniversalRequest): UniversalRequest {
            if (value == null || value === JsonValue.Null) return fallback
            val fields = value.asObjectOrNull() ?: throw ImageFailure("request 必须是 JSON 对象")
            var method = text(fields, "method")?.trim()?.uppercase()?.ifEmpty { null } ?: fallback.method
            if (method !in METHODS) {
                throw ImageFailure("request.method 只支持 " + METHODS.joinToString(" / ") + "，收到「" + method + "」")
            }
            return UniversalRequest(
                method = method,
                path = text(fields, "path")?.trim() ?: fallback.path,
                headers = stringMap(fields["headers"], "request.headers") ?: fallback.headers,
                bodyTemplate = text(fields, "bodyTemplate") ?: fallback.bodyTemplate,
            )
        }
    }
}

/** 响应提取：从 JSON 里按路径取值，再决定值是什么形态（base64 / URL / 原始二进制）。 */
data class UniversalResponse(
    /** 极简路径，例如 results[0].url 或 data[0].b64_json；type = binary 时可留空。 */
    val extract: String = "",
    val type: String = TYPE_JSON_URL,
    val mime: String = "",
) {
    companion object {
        const val TYPE_JSON_BASE64: String = "json_base64"
        const val TYPE_JSON_URL: String = "json_url"
        const val TYPE_BINARY: String = "binary"

        val TYPES: List<String> = listOf(TYPE_JSON_BASE64, TYPE_JSON_URL, TYPE_BINARY)

        internal fun fromJson(value: JsonValue?, fallback: UniversalResponse): UniversalResponse {
            if (value == null || value === JsonValue.Null) return fallback
            val fields = value.asObjectOrNull() ?: throw ImageFailure("response 必须是 JSON 对象")
            val type = text(fields, "type")?.trim()?.lowercase()?.ifEmpty { null } ?: fallback.type
            if (type !in TYPES) {
                throw ImageFailure("response.type 只支持 " + TYPES.joinToString(" / ") + "，收到「" + type + "」")
            }
            return UniversalResponse(
                extract = text(fields, "extract")?.trim() ?: fallback.extract,
                type = type,
                mime = text(fields, "mime")?.trim() ?: fallback.mime,
            )
        }
    }
}

/**
 * 异步模式：先提交，再从响应里取结果地址；取不到就轮询 pollPath，直到 doneField 为真。
 * nightmare 属于「提交响应里直接带结果 URL」的那一类，轮询字段留空即可。
 */
data class UniversalAsync(
    val enabled: Boolean = false,
    /** 从 JSON 响应里取「结果地址」的路径；与 response.extract 可以是同一个。 */
    val resultUrlFrom: String = "",
    /** 轮询路径（相对 baseUrl），例如 /runs/id 或 /status；留空表示不需要轮询。 */
    val pollPath: String = "",
    /** 完成标志字段，例如 status 或 done；留空时以「resultUrlFrom 能取到值」当完成。 */
    val doneField: String = "",
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
) {
    companion object {
        const val DEFAULT_TIMEOUT_SECONDS: Int = 120

        internal fun fromJson(value: JsonValue?, fallback: UniversalAsync): UniversalAsync {
            if (value == null || value === JsonValue.Null) return fallback
            val fields = value.asObjectOrNull() ?: throw ImageFailure("async 必须是 JSON 对象")
            val enabled = fields["enabled"]?.asBooleanOrNull() ?: fallback.enabled
            val timeout = fields["timeoutSeconds"]?.asIntOrNull() ?: fallback.timeoutSeconds
            if (timeout < 1 || timeout > MAX_TIMEOUT_SECONDS) {
                throw ImageFailure("async.timeoutSeconds 必须在 1-" + MAX_TIMEOUT_SECONDS + " 之间，收到 " + timeout)
            }
            return UniversalAsync(
                enabled = enabled,
                resultUrlFrom = text(fields, "resultUrlFrom")?.trim() ?: fallback.resultUrlFrom,
                pollPath = text(fields, "pollPath")?.trim() ?: fallback.pollPath,
                doneField = text(fields, "doneField")?.trim() ?: fallback.doneField,
                timeoutSeconds = timeout,
            )
        }
    }
}

/**
 * 一份完整的服务适配。内置三份（nightmare / openai-images / sd-webui）证明同一套配置
 * 能表达三种完全不同的 API；自定义 profile 由主 app 以 JSON 字符串注入。
 */
data class UniversalProfile(
    val name: String,
    val description: String = "",
    val baseUrl: String = "",
    val auth: UniversalAuth = UniversalAuth(),
    val request: UniversalRequest = UniversalRequest(),
    val response: UniversalResponse = UniversalResponse(),
    val async: UniversalAsync = UniversalAsync(),
    /** 调用时没传的模板变量用这里的默认值补，例如 {"width":"512"}；token 不放这里。 */
    val defaults: Map<String, String> = emptyMap(),
) {

    /** 导出成可分享的 JSON（内置 profile 也能直接复制给别人）。 */
    fun toJsonString(): String = JsonValue.write(toJsonValue())

    internal fun toJsonValue(): JsonValue = jsonObject(
        "name" to JsonValue.Str(name),
        "description" to JsonValue.Str(description),
        "baseUrl" to JsonValue.Str(baseUrl),
        "auth" to jsonObject(
            "type" to JsonValue.Str(auth.type),
            "header" to JsonValue.Str(auth.header),
            "token" to JsonValue.Str(auth.token),
            "prefix" to JsonValue.Str(auth.prefix),
        ),
        "request" to jsonObject(
            "method" to JsonValue.Str(request.method),
            "path" to JsonValue.Str(request.path),
            "headers" to mapJson(request.headers),
            "bodyTemplate" to JsonValue.Str(request.bodyTemplate),
        ),
        "response" to jsonObject(
            "extract" to JsonValue.Str(response.extract),
            "type" to JsonValue.Str(response.type),
            "mime" to JsonValue.Str(response.mime),
        ),
        "async" to jsonObject(
            "enabled" to JsonValue.Bool(async.enabled),
            "resultUrlFrom" to JsonValue.Str(async.resultUrlFrom),
            "pollPath" to JsonValue.Str(async.pollPath),
            "doneField" to JsonValue.Str(async.doneField),
            "timeoutSeconds" to JsonValue.Num(async.timeoutSeconds.toDouble()),
        ),
        "defaults" to mapJson(defaults),
    )

    companion object {
        /** 从字符串导入（用户分享来的 profile、或主 app 存的配置）。 */
        fun fromJsonString(text: String): UniversalProfile = fromJsonValue(parseJson(text, "profile"))

        internal fun fromJsonValue(root: JsonValue): UniversalProfile {
            val fields = root.asObjectOrNull() ?: throw ImageFailure("profile 必须是 JSON 对象")
            val name = text(fields, "name")?.trim()?.ifEmpty { null }
                ?: throw ImageFailure("profile 缺少 name")
            if (fields["response"] == null) {
                throw ImageFailure("profile「" + name + "」缺少 response（extract / type），无法确定怎么取图")
            }
            return UniversalProfile(
                name = name,
                description = text(fields, "description")?.trim().orEmpty(),
                baseUrl = text(fields, "baseUrl")?.trim().orEmpty(),
                auth = UniversalAuth.fromJson(fields["auth"], UniversalAuth()),
                request = UniversalRequest.fromJson(fields["request"], UniversalRequest()),
                response = UniversalResponse.fromJson(fields["response"], UniversalResponse()),
                async = UniversalAsync.fromJson(fields["async"], UniversalAsync()),
                defaults = stringMap(fields["defaults"], "defaults").orEmpty(),
            )
        }
    }
}

internal fun parseJson(text: String, label: String): JsonValue {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) throw ImageFailure(label + " 是空字符串")
    return try {
        JsonValue.parse(trimmed)
    } catch (failure: JsonFormatException) {
        throw ImageFailure(label + " 不是合法 JSON：" + failure.message)
    }
}

internal fun mapJson(values: Map<String, String>): JsonValue =
    jsonObject(*values.map { (key, value) -> key to JsonValue.Str(value) }.toTypedArray())

internal fun text(fields: Map<String, JsonValue>, key: String): String? {
    val raw = fields[key] ?: return null
    if (raw === JsonValue.Null) return null
    return raw.asStringOrNull() ?: throw ImageFailure(key + " 必须是字符串")
}

internal fun stringMap(value: JsonValue?, label: String): Map<String, String>? {
    if (value == null || value === JsonValue.Null) return null
    val fields = value.asObjectOrNull() ?: throw ImageFailure(label + " 必须是 JSON 对象（键值都是字符串）")
    val out = LinkedHashMap<String, String>()
    fields.forEach { (rawName, item) ->
        val name = rawName.trim()
        if (name.isEmpty()) return@forEach
        val text = item.asStringOrNull() ?: throw ImageFailure(label + "." + name + " 必须是字符串")
        out[name] = text
    }
    return out
}
