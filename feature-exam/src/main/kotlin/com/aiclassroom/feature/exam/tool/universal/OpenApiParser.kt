package com.aiclassroom.feature.exam.tool.universal

import com.aiclassroom.feature.exam.tool.exam.JsonFormatException
import com.aiclassroom.feature.exam.tool.exam.JsonValue
import com.aiclassroom.feature.exam.tool.exam.asObjectOrNull
import com.aiclassroom.feature.exam.tool.exam.asStringOrNull
import com.aiclassroom.feature.exam.tool.exam.jsonArray
import com.aiclassroom.feature.exam.tool.exam.jsonObject

/** 一个接口：method + 路径 + 一句话说明。 */
data class OpenApiOperation(
    val method: String,
    val path: String,
    val summary: String,
)

/** 一种认证方式：v3 的 components.securitySchemes / v2 的 securityDefinitions。 */
data class OpenApiSecurityScheme(
    val name: String,
    val type: String,
    val location: String,
    val scheme: String,
)

/** OpenAPI 精简摘要：够判断「这是什么服务、要哪种认证、有哪些接口」，不试图自动生成可执行配置。 */
data class OpenApiSummary(
    val title: String,
    val version: String,
    val specVersion: String,
    val servers: List<String>,
    val securitySchemes: List<OpenApiSecurityScheme>,
    val operations: List<OpenApiOperation>,
) {

    fun toJsonString(): String = JsonValue.write(toJsonValue())

    internal fun toJsonValue(): JsonValue = jsonObject(
        "title" to JsonValue.Str(title),
        "version" to JsonValue.Str(version),
        "specVersion" to JsonValue.Str(specVersion),
        "servers" to jsonArray(servers.map { JsonValue.Str(it) }),
        "securitySchemes" to jsonArray(
            securitySchemes.map {
                jsonObject(
                    "name" to JsonValue.Str(it.name),
                    "type" to JsonValue.Str(it.type),
                    "in" to JsonValue.Str(it.location),
                    "scheme" to JsonValue.Str(it.scheme),
                )
            }
        ),
        "operations" to jsonArray(
            operations.map {
                jsonObject(
                    "method" to JsonValue.Str(it.method),
                    "path" to JsonValue.Str(it.path),
                    "summary" to JsonValue.Str(it.summary),
                )
            }
        ),
    )

    /** probe_endpoint 的 guess 里只放前 [limit] 条接口，避免把整份文档灌回模型。 */
    internal fun toGuessJson(limit: Int): JsonValue = jsonObject(
        "title" to JsonValue.Str(title),
        "version" to JsonValue.Str(version),
        "specVersion" to JsonValue.Str(specVersion),
        "servers" to jsonArray(servers.map { JsonValue.Str(it) }),
        "securitySchemes" to jsonArray(
            securitySchemes.map {
                jsonObject(
                    "name" to JsonValue.Str(it.name),
                    "type" to JsonValue.Str(it.type),
                    "in" to JsonValue.Str(it.location),
                    "scheme" to JsonValue.Str(it.scheme),
                )
            }
        ),
        "operationCount" to JsonValue.Num(operations.size.toDouble()),
        "operations" to jsonArray(
            operations.take(limit).map {
                jsonObject(
                    "method" to JsonValue.Str(it.method),
                    "path" to JsonValue.Str(it.path),
                    "summary" to JsonValue.Str(it.summary),
                )
            }
        ),
    )

    /**
     * 建议的 profile 骨架：认证按 securitySchemes 猜，路径挑一个像「生成 / 执行」的接口。
     * 只给骨架——请求体与 response.extract 必须人工照文档补全，自动生成的可执行配置不可靠。
     */
    fun suggestProfileJson(profileName: String, baseUrl: String): String {
        val operation = pickOperation()
        val scheme = securitySchemes.firstOrNull { it.type == "http" || it.type == "apiKey" }
            ?: securitySchemes.firstOrNull()
        val auth = authFrom(scheme)
        val fields = ArrayList<Pair<String, JsonValue>>(9)
        fields.add("name" to JsonValue.Str(profileName))
        fields.add("description" to JsonValue.Str(
            "OpenAPI 骨架（" + title.ifBlank { "未命名服务" } + " " + version + "）：请照接口文档补全 bodyTemplate 与 response.extract"
        ))
        fields.add("baseUrl" to JsonValue.Str(baseUrl.ifBlank { servers.firstOrNull().orEmpty() }))
        fields.add(
            "auth" to jsonObject(
                "type" to JsonValue.Str(auth.type),
                "header" to JsonValue.Str(auth.header),
                "token" to JsonValue.Str(""),
                "prefix" to JsonValue.Str(auth.prefix),
            )
        )
        fields.add(
            "request" to jsonObject(
                "method" to JsonValue.Str(operation?.method ?: "POST"),
                "path" to JsonValue.Str(operation?.path ?: ""),
                "headers" to jsonObject("Content-Type" to JsonValue.Str("application/json; charset=utf-8")),
                "bodyTemplate" to JsonValue.Str("""{"prompt":"{{prompt}}"}"""),
            )
        )
        fields.add(
            "response" to jsonObject(
                "extract" to JsonValue.Str(""),
                "type" to JsonValue.Str(UniversalResponse.TYPE_JSON_URL),
                "mime" to JsonValue.Str("image/png"),
            )
        )
        fields.add("async" to jsonObject("enabled" to JsonValue.Bool(false)))
        fields.add(
            "note" to JsonValue.Str(
                "骨架尚未可执行：把 response.extract 填成图片 URL 或 base64 的路径（例如 results[0].url / data[0].b64_json），" +
                    "bodyTemplate 按文档补齐参数，再用 universal_call 试跑。"
            )
        )
        return JsonValue.write(jsonObject(*fields.toTypedArray()))
    }

    private fun pickOperation(): OpenApiOperation? {
        val preferred = operations.firstOrNull { operation ->
            operation.method == "POST" && GENERATE_HINTS.any { hint ->
                operation.path.lowercase().contains(hint) || operation.summary.lowercase().contains(hint)
            }
        }
        return preferred
            ?: operations.firstOrNull { it.method == "POST" }
            ?: operations.firstOrNull()
    }

    private fun authFrom(scheme: OpenApiSecurityScheme?): UniversalAuth = when {
        scheme == null -> UniversalAuth(type = UniversalAuth.TYPE_NONE)
        scheme.type.equals("http", true) && scheme.scheme.equals("basic", true) -> UniversalAuth(type = UniversalAuth.TYPE_BASIC)
        scheme.type.equals("http", true) -> UniversalAuth(type = UniversalAuth.TYPE_BEARER)
        scheme.type.equals("apiKey", true) && scheme.location.equals("query", true) ->
            UniversalAuth(type = UniversalAuth.TYPE_QUERY, header = scheme.name)
        scheme.type.equals("apiKey", true) -> UniversalAuth(type = UniversalAuth.TYPE_HEADER, header = scheme.name)
        scheme.type.equals("oauth2", true) || scheme.type.equals("openIdConnect", true) ->
            UniversalAuth(type = UniversalAuth.TYPE_BEARER)
        else -> UniversalAuth(type = UniversalAuth.TYPE_NONE)
    }

    private companion object {
        val GENERATE_HINTS: List<String> = listOf("run", "generat", "image", "txt2img", "prompt", "draw", "render")
    }
}

private val HTTP_METHODS: Set<String> = setOf("get", "post", "put", "patch", "delete", "head", "options")

/** 文档再大也只留这么多接口，摘要本来就只用来判断方向。 */
private const val MAX_OPERATIONS: Int = 300

/**
 * 解析 OpenAPI / Swagger 文档，返回精简摘要；不是 OpenAPI 文档（或是坏 JSON）返回 null。
 *
 * 刻意不做「自动生成可执行配置」：接口的语义（哪个字段是提示词、结果在哪）无法从 schema 可靠推出，
 * 猜错的配置比没有配置更糟。这里只给摘要 + 一条需人工补齐的骨架（[OpenApiSummary.suggestProfileJson]）。
 */
fun parseOpenApi(json: String): OpenApiSummary? {
    val trimmed = json.trim()
    if (trimmed.isEmpty()) return null
    val root = try {
        JsonValue.parse(trimmed)
    } catch (failure: JsonFormatException) {
        return null
    }
    val fields = root.asObjectOrNull() ?: return null
    val specVersion = fields["openapi"]?.asStringOrNull()?.trim()?.ifEmpty { null }
        ?: fields["swagger"]?.asStringOrNull()?.trim()?.ifEmpty { null }
        ?: return null
    val info = fields["info"]?.asObjectOrNull().orEmpty()
    val title = info["title"]?.asStringOrNull()?.trim().orEmpty()
    val version = info["version"]?.asStringOrNull()?.trim().orEmpty()
    return OpenApiSummary(
        title = title,
        version = version,
        specVersion = specVersion,
        servers = parseServers(fields, specVersion),
        securitySchemes = parseSecuritySchemes(fields),
        operations = parseOperations(fields),
    )
}

private fun parseServers(fields: Map<String, JsonValue>, specVersion: String): List<String> {
    val servers = ArrayList<String>()
    val declared = (fields["servers"] as? JsonValue.Arr)?.items.orEmpty()
    declared.forEach { item ->
        val url = item.asObjectOrNull()?.get("url")?.asStringOrNull()?.trim()
        if (!url.isNullOrEmpty() && url !in servers) servers.add(url)
    }
    if (servers.isNotEmpty()) return servers
    // Swagger 2.0 没有 servers，用 schemes + host + basePath 拼。
    val host = fields["host"]?.asStringOrNull()?.trim().orEmpty()
    if (host.isEmpty()) return servers
    val basePath = fields["basePath"]?.asStringOrNull()?.trim().orEmpty()
    val schemes = (fields["schemes"] as? JsonValue.Arr)?.items.orEmpty()
        .mapNotNull { it.asStringOrNull()?.trim() }
        .filter { it.isNotEmpty() }
        .ifEmpty { listOf("https") }
    schemes.forEach { scheme -> servers.add(scheme + "://" + host + basePath) }
    return servers
}

private fun parseSecuritySchemes(fields: Map<String, JsonValue>): List<OpenApiSecurityScheme> {
    val container = fields["components"]?.asObjectOrNull()?.get("securitySchemes")?.asObjectOrNull()
        ?: fields["securityDefinitions"]?.asObjectOrNull()
        ?: return emptyList()
    return container.map { (name, raw) ->
        val scheme = raw.asObjectOrNull().orEmpty()
        OpenApiSecurityScheme(
            name = name,
            type = scheme["type"]?.asStringOrNull()?.trim().orEmpty(),
            location = scheme["in"]?.asStringOrNull()?.trim().orEmpty(),
            scheme = scheme["scheme"]?.asStringOrNull()?.trim().orEmpty(),
        )
    }
}

private fun parseOperations(fields: Map<String, JsonValue>): List<OpenApiOperation> {
    val paths = fields["paths"]?.asObjectOrNull().orEmpty()
    val operations = ArrayList<OpenApiOperation>()
    for ((path, rawItem) in paths) {
        if (operations.size >= MAX_OPERATIONS) break
        val item = rawItem.asObjectOrNull() ?: continue
        for ((key, rawOperation) in item) {
            val method = key.trim().lowercase()
            if (method !in HTTP_METHODS) continue
            val operation = rawOperation.asObjectOrNull() ?: continue
            val summary = operation["summary"]?.asStringOrNull()?.trim()?.ifEmpty { null }
                ?: operation["operationId"]?.asStringOrNull()?.trim()?.ifEmpty { null }
                ?: operation["description"]?.asStringOrNull()?.trim()?.ifEmpty { null }
                ?: ""
            operations.add(OpenApiOperation(method.uppercase(), path, oneLine(summary)))
            if (operations.size >= MAX_OPERATIONS) break
        }
    }
    return operations
}

/** 说明文字压成一行并截断，避免把多行文档灌回模型上下文。 */
internal fun oneLine(text: String, limit: Int = 160): String {
    val collapsed = text.replace(Regex("\\s+"), " ").trim()
    return if (collapsed.length <= limit) collapsed else collapsed.take(limit) + "…"
}
