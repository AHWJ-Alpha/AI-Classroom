package com.aiclassroom.feature.exam.tool.image

import com.aiclassroom.feature.exam.tool.exam.JsonFormatException
import com.aiclassroom.feature.exam.tool.exam.JsonValue
import com.aiclassroom.feature.exam.tool.exam.asObjectOrNull
import com.aiclassroom.feature.exam.tool.exam.asStringOrNull
import com.aiclassroom.feature.exam.tool.exam.jsonArray
import com.aiclassroom.feature.exam.tool.exam.jsonObject
import com.aiclassroom.feature.exam.tool.universal.OpenApiSummary
import com.aiclassroom.feature.exam.tool.universal.parseOpenApi

/**
 * probe_endpoint 的实现：只做**匿名**探测，绝不携带任何 token / apiKey。
 *
 * 用途：用户手上有个私有生图服务（例如手机上 8820 端口的 nightmare），不知道它是什么后端、
 * 需要哪种认证。先匿名敲几个标准路径，用状态码 + 响应体判断服务类型与认证需求，
 * 再决定 ImageConfig 的 provider / authType。
 */
internal object ImageProbe {

    /** 依次尝试的路径。顺序即输出顺序。 */
    private val DEFAULT_PATHS: List<String> =
        listOf("/", "/docs", "/openapi.json", "/sdapi/v1/options", "/v1/models", "/health")

    /** extraPaths 的上限：再多也只是拖长总耗时。 */
    private const val MAX_EXTRA_PATHS: Int = 10

    /** 单个路径 5 秒。 */
    private const val PER_PATH_TIMEOUT_MILLIS: Int = 5_000

    /** 整次探测 30 秒；超预算的路径直接跳过并标注。 */
    private const val TOTAL_TIMEOUT_MILLIS: Int = 30_000

    /** 回填给模型的响应体片段长度。 */
    private const val SNIPPET_CHARS: Int = 200

    fun probe(rawUrl: String, extraPaths: List<String>): String {
        val base = normalizeBase(rawUrl)
        if (extraPaths.size > MAX_EXTRA_PATHS) {
            throw ImageFailure("extraPaths 最多 " + MAX_EXTRA_PATHS + " 个，收到 " + extraPaths.size + " 个")
        }
        val targets = LinkedHashSet<String>()
        targets.addAll(DEFAULT_PATHS)
        extraPaths.map { it.trim() }.filterTo(targets) { it.isNotEmpty() }

        val startedAt = System.nanoTime()
        val probes = ArrayList<Probe>()
        var reachable = false
        var lastError = ""
        for (path in targets) {
            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L
            val budget = minOf(PER_PATH_TIMEOUT_MILLIS.toLong(), TOTAL_TIMEOUT_MILLIS - elapsedMillis).toInt()
            if (budget <= 0) {
                probes.add(Probe(path, 0, "", "", "跳过：总超时 " + TOTAL_TIMEOUT_MILLIS + " 毫秒已用完", ""))
                continue
            }
            val target = if (path.startsWith("http://", true) || path.startsWith("https://", true)) {
                path
            } else {
                base + path
            }
            when (val result = ImageHttp.fetchAnonymously(target, budget)) {
                is ProbeFetch.Ok -> {
                    // 只要能拿到任何 HTTP 状态码（含 401/403）就说明服务是活的。
                    reachable = true
                    probes.add(Probe(path, result.status, result.contentType, snippet(result.body), "", result.body))
                }
                is ProbeFetch.Failed -> {
                    lastError = result.reason
                    probes.add(Probe(path, 0, "", "", result.reason, ""))
                }
            }
        }
        return buildReport(base, reachable, lastError, probes)
    }

    /** 一次探测的记录。status = 0 表示这次请求没拿到响应。 */
    private class Probe(
        val path: String,
        val status: Int,
        val contentType: String,
        val snippet: String,
        val error: String,
        /** 完整响应体：只给 OpenAPI 解析用，回填给模型的仍然是 snippet。 */
        val body: String,
    ) {
        val ok: Boolean get() = status in 200..299
    }

    private fun buildReport(base: String, reachable: Boolean, lastError: String, probes: List<Probe>): String {
        // ---- guess 判定规则 ----
        // 1. 任一探测返回 401 / 403 → needsAuth = true（服务活着，只是要凭据）。
        // 2. /sdapi/v1/options 返回 2xx → sdCompatible = true（A1111 / Forge 系 WebUI）。
        // 3. /docs 或 /openapi.json 返回 2xx → openapi = true（FastAPI/OpenAPI 服务，接口可自动推断）。
        // 4. 全部请求失败（连不上）→ reachable = false，并把连接错误原因写进 hint。
        val needsAuth = probes.any { it.status == 401 || it.status == 403 }
        val sdCompatible = probes.any { it.path == SD_OPTIONS_PATH && it.ok }
        val openapi = probes.any { (it.path == "/docs" || it.path == "/openapi.json") && it.ok }
        // 5. 能解析出 OpenAPI 文档时给出摘要：接新服务最省事的入口，比人肉看 /docs 强。
        val openapiSummary: OpenApiSummary? = probes
            .filter { it.ok && (it.path == "/docs" || it.path == "/openapi.json") }
            .firstNotNullOfOrNull { parseOpenApi(it.body) }

        // 服务端原话优先取 401/403 那条的响应体（典型是 {"error":"a token is needed"}）。
        val denyProbe = probes.firstOrNull { it.status == 401 || it.status == 403 }
        val serverMessage = denyProbe?.let { serverMessageOf(it.snippet) }.orEmpty()

        val hint = when {
            !reachable -> "连不上：" + lastError.ifEmpty { "全部探测都没有响应" } +
                "（检查服务是否在跑、端口是否正确、手机端口是否已 adb forward）"
            else -> buildHint(needsAuth, serverMessage, sdCompatible, openapi, openapiSummary)
        }

        return JsonValue.write(
            jsonObject(
                "ok" to JsonValue.Bool(true),
                "base" to JsonValue.Str(base),
                "reachable" to JsonValue.Bool(reachable),
                "probes" to jsonArray(
                    probes.map { probe ->
                        val fields = ArrayList<Pair<String, JsonValue>>(5)
                        fields.add("path" to JsonValue.Str(probe.path))
                        fields.add("status" to JsonValue.Num(probe.status.toDouble()))
                        fields.add("contentType" to JsonValue.Str(probe.contentType))
                        fields.add("snippet" to JsonValue.Str(probe.snippet))
                        if (probe.error.isNotEmpty()) fields.add("error" to JsonValue.Str(probe.error))
                        jsonObject(*fields.toTypedArray())
                    }
                ),
                "guess" to jsonObject(
                    "sdCompatible" to JsonValue.Bool(sdCompatible),
                    "needsAuth" to JsonValue.Bool(needsAuth),
                    "openapi" to JsonValue.Bool(openapi),
                    "openapiSummary" to (openapiSummary?.toGuessJson(OPENAPI_OPERATION_LIMIT) ?: JsonValue.Null),
                    "hint" to JsonValue.Str(hint),
                ),
            )
        )
    }

    private fun buildHint(
        needsAuth: Boolean,
        serverMessage: String,
        sdCompatible: Boolean,
        openapi: Boolean,
        openapiSummary: OpenApiSummary?,
    ): String {
        val parts = ArrayList<String>(3)
        if (needsAuth) {
            val message = if (serverMessage.isEmpty()) "" else "，服务端提示：" + serverMessage
            parts.add("需要认证" + message + "。先按服务端提示填 apiKey，再试 authType=header（如 X-Api-Key）/ bearer / query")
        }
        if (sdCompatible) {
            parts.add("$SD_OPTIONS_PATH 返回 200，可当 Stable Diffusion WebUI 兼容后端用（provider=sdwebui）")
        }
        if (openapi) {
            parts.add("提供 /docs 或 /openapi.json，是 OpenAPI 服务（FastAPI 等），可用 provider=custom 按接口文档填 URL 与请求体模板")
        }
        if (openapiSummary != null) {
            val examples = openapiSummary.operations.take(3).joinToString("、") { it.method + " " + it.path }
            val auth = openapiSummary.securitySchemes.joinToString("、") { it.name + "(" + it.type + ")" }
            parts.add(
                "已解析 OpenAPI：「" + openapiSummary.title.ifEmpty { "未命名" } + " " + openapiSummary.version +
                    "」，共 " + openapiSummary.operations.size + " 个接口" +
                    (if (examples.isEmpty()) "" else "（例如 " + examples + "）") +
                    (if (auth.isEmpty()) "" else "，认证方式：" + auth) +
                    "；可直接用 universal_call 按 guess.openapiSummary 配 profile"
            )
        }
        if (parts.isEmpty()) {
            return "服务可达但没命中已知后端特征，可用 provider=custom 手工填 URL 与请求体模板"
        }
        return parts.joinToString("；")
    }

    /** 从响应体里挖出服务端给的人话提示：{"error": "..."} / {"message": ...} / {"detail": ...}。 */
    private fun serverMessageOf(body: String): String {
        val parsed = try {
            JsonValue.parse(body.trim())
        } catch (failure: JsonFormatException) {
            null
        }
        val fields = parsed?.asObjectOrNull()
        if (fields != null) {
            for (name in listOf("error", "message", "detail", "msg")) {
                val text = fields[name]?.asStringOrNull()?.trim()
                if (!text.isNullOrEmpty()) return collapse(text)
            }
        }
        return collapse(body)
    }

    private fun snippet(text: String): String {
        val collapsed = collapse(text)
        return if (collapsed.length <= SNIPPET_CHARS) collapsed else collapsed.take(SNIPPET_CHARS) + "…"
    }

    /** 压成一行：HTML 错误页 / 多行 JSON 都不要把换行灌回模型上下文。 */
    private fun collapse(text: String): String = text.replace(Regex("\\s+"), " ").trim()

    /** 宽容处理用户输入：没写协议就补 http://，去掉尾部斜杠。 */
    private fun normalizeBase(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) throw ImageFailure("probe_endpoint 需要非空的 url 参数")
        val withScheme = if (trimmed.contains("://")) trimmed else "http://" + trimmed
        val base = withScheme.trimEnd('/')
        if (!base.startsWith("http://", true) && !base.startsWith("https://", true)) {
            throw ImageFailure("只支持 http / https 地址，收到：" + rawUrl)
        }
        return base
    }

    private const val SD_OPTIONS_PATH: String = "/sdapi/v1/options"

    /** guess 里最多回填几条接口：探测结果的用途是「选方向」，不是替代接口文档。 */
    private const val OPENAPI_OPERATION_LIMIT: Int = 5
}
