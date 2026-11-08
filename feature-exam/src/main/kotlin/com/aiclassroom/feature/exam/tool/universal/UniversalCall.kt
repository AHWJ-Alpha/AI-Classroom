package com.aiclassroom.feature.exam.tool.universal

import com.aiclassroom.feature.exam.tool.exam.JsonValue
import com.aiclassroom.feature.exam.tool.exam.asObjectOrNull
import com.aiclassroom.feature.exam.tool.exam.jsonObject
import com.aiclassroom.feature.exam.tool.image.ImageConfig
import com.aiclassroom.feature.exam.tool.image.ImageFailure
import com.aiclassroom.feature.exam.tool.image.ImageSink
import com.aiclassroom.feature.exam.tool.image.MAX_IMAGE_BYTES
import com.aiclassroom.feature.exam.tool.image.appendQueryAuth
import com.aiclassroom.feature.exam.tool.image.buildAuth
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

/** 轮询间隔：够快看到结果，又不至于把手机上的服务敲爆。 */
private const val POLL_INTERVAL_MILLIS: Long = 1_000L

private val DATA_URI_PREFIX: Regex = Regex("^data:[^,]*,")

/** 一次调用要用到的全部参数（profile 与顶层参数合成后的结果）。 */
internal class UniversalCallPlan(
    val profileName: String,
    val baseUrl: String,
    val auth: UniversalAuth,
    val request: UniversalRequest,
    val response: UniversalResponse,
    val async: UniversalAsync,
    val timeoutSeconds: Int,
    val values: Map<String, String>,
)

/** 拿到手的图片字节 + 它的来源（来源会回填在结果里，便于回查服务端）。 */
internal class UniversalPayload(val bytes: ByteArray, val sourceUrl: String)

/**
 * universal_call 的执行器：一个方法覆盖「提交 → 提取 → 下载/解码 → 保存」全流程。
 *
 * 认证与查询参数拼装直接复用 ImageAuth.kt 的 buildAuth() / appendQueryAuth()：
 * 通用服务与内置 provider 用同一套认证语义，不出现第二份实现。
 */
internal class UniversalCaller(private val sink: ImageSink) {

    suspend fun execute(plan: UniversalCallPlan): String {
        val startedAt = System.nanoTime()
        val config = imageConfig(plan.auth)
        val body = renderBody(plan)
        val headers = LinkedHashMap<String, String>()
        headers.putAll(buildAuth(config))
        headers.putAll(plan.request.headers.filterKeys { it.isNotBlank() })
        if (body != null && headers.keys.none { it.equals("Content-Type", true) }) {
            headers["Content-Type"] = "application/json; charset=utf-8"
        }
        val method = plan.request.method.uppercase()
        val url = appendQueryAuth(resolveUrl(plan.baseUrl, plan.request.path), config)
        val submitted = UniversalHttp.send(
            method,
            url,
            headers,
            body?.toByteArray(Charsets.UTF_8),
            plan.timeoutSeconds,
        )
        if (!submitted.ok) {
            throw ImageFailure("提交请求失败 HTTP " + submitted.status + "（" + method + " " + url + "）：" + snippet(submitted.text()))
        }
        val payload = if (plan.async.enabled) {
            resolveAsync(plan, config, submitted)
        } else {
            payloadFromText(plan.response, submitted.text(), submitted.bytes, plan, config, "提交响应", allowMissing = false)
                ?: throw ImageFailure("提交响应里没有取到图片：" + snippet(submitted.text()))
        }

        val bytes = payload.bytes
        if (bytes.isEmpty()) throw ImageFailure("没有拿到图片字节：服务端返回了空内容")
        if (bytes.size > MAX_IMAGE_BYTES) {
            throw ImageFailure("图片 " + bytes.size + " 字节超过上限 " + (MAX_IMAGE_BYTES / (1024 * 1024)) + "MB")
        }
        val mime = plan.response.mime.trim().ifEmpty { sniffMime(bytes) }
        val suggestedName = "universal_" + timestamp() + "." + extensionFor(mime)
        val saved = sink.save(bytes, suggestedName, mime)
        val path = saved.getOrElse { error ->
            throw ImageFailure("图片已拿到（" + bytes.size + " 字节）但保存失败：" + (error.message ?: error::class.java.simpleName))
        }
        if (path.isBlank()) throw ImageFailure("图片保存后返回了空路径")
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
        val fields = ArrayList<Pair<String, JsonValue>>(8)
        fields.add("ok" to JsonValue.Bool(true))
        fields.add("path" to JsonValue.Str(path))
        fields.add("bytes" to JsonValue.Num(bytes.size.toDouble()))
        fields.add("mime" to JsonValue.Str(mime))
        fields.add("elapsedMs" to JsonValue.Num(elapsedMs.toDouble()))
        fields.add("profile" to JsonValue.Str(plan.profileName))
        if (payload.sourceUrl.isNotBlank()) fields.add("resultUrl" to JsonValue.Str(payload.sourceUrl))
        return JsonValue.write(jsonObject(*fields.toTypedArray()))
    }

    /**
     * 异步：先看提交响应是不是已经给了结果（nightmare 就是这样，直接跳过轮询），
     * 否则按 pollPath 轮询到 doneField 为真（或结果地址出现），超时按 timeoutSeconds 收口。
     */
    private fun resolveAsync(plan: UniversalCallPlan, config: ImageConfig, submitted: UniversalFetch): UniversalPayload {
        val submitText = submitted.text()
        payloadFromText(plan.response, submitText, submitted.bytes, plan, config, "提交响应", allowMissing = true)
            ?.let { return it }
        if (plan.async.pollPath.isBlank()) {
            val wanted = plan.response.extract.ifBlank { plan.async.resultUrlFrom }.ifBlank { "(未配置 extract)" }
            throw ImageFailure(
                "异步模式取不到结果：提交响应里没有 " + wanted + "，也没有配置 async.pollPath。服务端响应：" + snippet(submitText)
            )
        }
        val pollUrl = appendQueryAuth(resolveUrl(plan.baseUrl, plan.async.pollPath), config)
        val deadline = System.nanoTime() + plan.async.timeoutSeconds * 1_000_000_000L
        var lastText = submitText
        var polls = 0
        while (true) {
            if (System.nanoTime() >= deadline) {
                throw ImageFailure(
                    "异步轮询超时（" + plan.async.timeoutSeconds + " 秒，" + polls + " 次轮询）：最后一次响应：" + snippet(lastText)
                )
            }
            Thread.sleep(POLL_INTERVAL_MILLIS)
            polls++
            val remainingSeconds = ((deadline - System.nanoTime()) / 1_000_000_000L).toInt().coerceAtLeast(1)
            val poll = UniversalHttp.send("GET", pollUrl, buildAuth(config), null, minOf(remainingSeconds, plan.timeoutSeconds))
            if (!poll.ok) {
                throw ImageFailure("轮询失败 HTTP " + poll.status + "（" + pollUrl + "）：" + snippet(poll.text()))
            }
            lastText = poll.text()
            val json = parseJsonOrNull(lastText) ?: throw ImageFailure("轮询响应不是合法 JSON：" + snippet(lastText))
            failureOf(json)?.let { throw ImageFailure("服务端报告失败：" + it) }
            val done = when {
                plan.async.doneField.isNotBlank() -> truthy(UniversalJsonPath.select(json, plan.async.doneField))
                plan.async.resultUrlFrom.isNotBlank() -> UniversalJsonPath.selectText(json, plan.async.resultUrlFrom) != null
                else -> true
            }
            if (!done) continue
            return payloadFromText(plan.response, lastText, poll.bytes, plan, config, "轮询响应", allowMissing = false)
                ?: throw ImageFailure("轮询响应里没有取到图片：" + snippet(lastText))
        }
    }

    /**
     * 按 response 描述把一次响应变成图片字节。
     * allowMissing = true 表示「暂时取不到就返回 null」（给异步轮询复用同一套提取逻辑）。
     */
    private fun payloadFromText(
        response: UniversalResponse,
        text: String,
        rawBody: ByteArray,
        plan: UniversalCallPlan,
        config: ImageConfig,
        label: String,
        allowMissing: Boolean,
    ): UniversalPayload? {
        if (response.type == UniversalResponse.TYPE_BINARY) {
            if (rawBody.isEmpty()) throw ImageFailure(label + " 返回了空响应体")
            return UniversalPayload(rawBody, plan.request.path)
        }
        if (response.extract.isBlank()) {
            throw ImageFailure(label + " 需要 response.extract（取值路径，例如 results[0].url 或 data[0].b64_json）")
        }
        val root = parseJsonOrNull(text) ?: run {
            if (allowMissing) return null
            throw ImageFailure(label + " 不是合法 JSON：" + snippet(text))
        }
        val value = UniversalJsonPath.selectText(root, response.extract) ?: run {
            if (allowMissing) return null
            throw ImageFailure(label + " 里没有 " + response.extract + "：" + snippet(text))
        }
        return when (response.type) {
            UniversalResponse.TYPE_JSON_BASE64 -> UniversalPayload(decodeBase64(value, label), "")
            UniversalResponse.TYPE_JSON_URL -> {
                val resultUrl = resolveUrl(plan.baseUrl, value)
                UniversalPayload(download(resultUrl, plan, config), resultUrl)
            }
            else -> throw ImageFailure("不支持的 response.type：" + response.type)
        }
    }

    /** 结果图片同样要带认证：json_url 与异步下载都走这里。 */
    private fun download(targetUrl: String, plan: UniversalCallPlan, config: ImageConfig): ByteArray {
        val signedUrl = appendQueryAuth(targetUrl, config)
        val fetch = UniversalHttp.send("GET", signedUrl, buildAuth(config), null, plan.timeoutSeconds)
        if (!fetch.ok) {
            throw ImageFailure("下载结果失败 HTTP " + fetch.status + "（" + signedUrl + "）：" + snippet(fetch.text()))
        }
        if (fetch.bytes.isEmpty()) throw ImageFailure("下载结果失败：" + signedUrl + " 返回了空响应体")
        if (fetch.bytes.size > MAX_IMAGE_BYTES) {
            throw ImageFailure("结果图片 " + fetch.bytes.size + " 字节超过上限 " + (MAX_IMAGE_BYTES / (1024 * 1024)) + "MB")
        }
        return fetch.bytes
    }

    private fun decodeBase64(raw: String, label: String): ByteArray {
        val body = raw.replace(DATA_URI_PREFIX, "").filterNot { it.isWhitespace() }
        if (body.isEmpty()) throw ImageFailure(label + " 返回的 base64 是空串")
        val bytes = try {
            Base64.getDecoder().decode(body)
        } catch (standard: IllegalArgumentException) {
            try {
                Base64.getUrlDecoder().decode(body)
            } catch (urlSafe: IllegalArgumentException) {
                throw ImageFailure(label + " 返回的 base64 无法解码：" + snippet(body))
            }
        }
        if (bytes.isEmpty()) throw ImageFailure(label + " 的 base64 解码后是空字节")
        if (bytes.size > MAX_IMAGE_BYTES) {
            throw ImageFailure("图片 " + bytes.size + " 字节超过上限 " + (MAX_IMAGE_BYTES / (1024 * 1024)) + "MB")
        }
        return bytes
    }

    /** 请求体模板：先把 {{prompt}} 等占位符替换掉（值做 JSON 转义），GET/HEAD 不带请求体。 */
    private fun renderBody(plan: UniversalCallPlan): String? {
        val template = plan.request.bodyTemplate.trim()
        if (template.isEmpty()) return null
        if (plan.request.method == "GET" || plan.request.method == "HEAD") return null
        val names = UniversalTemplate.placeholders(template)
        if (UniversalTemplate.PROMPT in names && plan.values[UniversalTemplate.PROMPT] == null) {
            throw ImageFailure("universal_call 需要 prompt 参数：请求体模板引用了 {{prompt}}")
        }
        return UniversalTemplate.render(template, plan.values)
    }

    private fun imageConfig(auth: UniversalAuth): ImageConfig = ImageConfig(
        authType = auth.type.trim().ifBlank { UniversalAuth.TYPE_NONE },
        authHeader = auth.header,
        authPrefix = auth.prefix,
        apiKey = auth.token,
    )

    private fun failureOf(root: JsonValue): String? {
        val fields = root.asObjectOrNull() ?: return null
        when (val error = fields["error"]) {
            is JsonValue.Str -> if (error.value.isNotBlank()) return snippet(error.value)
            is JsonValue.Obj -> return snippet(JsonValue.write(error))
            else -> Unit
        }
        val failed = fields["failed"]
        if (failed is JsonValue.Arr && failed.items.isNotEmpty()) return snippet(JsonValue.write(failed))
        return null
    }

    private fun truthy(value: JsonValue?): Boolean = when (value) {
        null -> false
        is JsonValue.Null -> false
        is JsonValue.Bool -> value.value
        is JsonValue.Num -> value.value != 0.0
        is JsonValue.Str -> value.value.trim().lowercase() in DONE_WORDS
        is JsonValue.Arr -> value.items.isNotEmpty()
        is JsonValue.Obj -> value.fields.isNotEmpty()
    }

    private fun sniffMime(bytes: ByteArray): String {
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            return "image/png"
        }
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return "image/jpeg"
        }
        if (bytes.size >= 12 &&
            bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
        ) {
            return "image/webp"
        }
        return "image/png"
    }

    private fun extensionFor(mime: String): String = when (mime.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "png"
    }

    private fun timestamp(): String = LocalDateTime.now().format(NAME_STAMP)

    private companion object {
        val NAME_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")
        val DONE_WORDS: Set<String> = setOf(
            "true", "done", "ok", "success", "succeeded", "finished", "complete", "completed", "ready",
        )
    }
}

/** 拼地址：path 写成完整 URL 时原样使用；否则 baseUrl + path（都能容忍多余 / 与缺失 /）。 */
internal fun resolveUrl(baseUrl: String, path: String): String {
    val target = path.trim()
    if (target.startsWith("http://", true) || target.startsWith("https://", true)) return target
    val base = baseUrl.trim().trimEnd('/')
    if (base.isEmpty()) throw ImageFailure("universal_call 需要 baseUrl（例如 http://127.0.0.1:8820）")
    if (!base.startsWith("http://", true) && !base.startsWith("https://", true)) {
        throw ImageFailure("baseUrl 只支持 http / https，收到：" + baseUrl)
    }
    if (target.isEmpty()) return base
    if (target.startsWith("?")) return base + target
    return base + "/" + target.trimStart('/')
}

/** JSON 解析不做错误外抛：异步轮询里「还没好」也可能是非 JSON 文本。 */
internal fun parseJsonOrNull(text: String): JsonValue? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    return try {
        JsonValue.parse(trimmed)
    } catch (failure: Exception) {
        null
    }
}
