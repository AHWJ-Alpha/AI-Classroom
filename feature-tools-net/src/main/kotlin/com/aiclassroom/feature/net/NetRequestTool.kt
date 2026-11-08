package com.aiclassroom.feature.net

import com.aiclassroom.feature.exam.tool.ToolHandler
import com.aiclassroom.feature.exam.tool.ToolResult
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URI
import java.nio.charset.Charset
import java.util.Locale

/**
 * net_request：让模型直接请求手机本机 / 局域网 / 公网任意 host:port 上的 HTTP 服务。
 *
 * 不做域名白名单（用户已明确授权任意 host:port），护栏改成两道：
 * 1. 总开关：[NetTools.registerNetTools] 的 enabled 回调为 false 时直接拒绝；
 * 2. 调用日志：每次调用都通过 logger 交出 [时间, method, url, 状态码或错误]，请求头脱敏。
 *
 * 只用 java.net.HttpURLConnection；证书校验沿用系统默认，不自定义 TrustManager。
 * execute 是阻塞实现（内部不挂起），调用方应放在 IO 线程 / Dispatchers.IO 上。
 */
class NetRequestTool(private val logger: (String) -> Unit) : ToolHandler {

    override val name: String = "net_request"

    override val description: String =
        "向任意 URL 发起一次 HTTP 请求，返回 JSON：{status, headers, body, truncated}。" +
            "支持 http/https，可访问手机本机、局域网和公网地址；body 超过 256KB 会被截断并置 truncated=true。" +
            "默认不跟随 3xx 重定向，需要时把 followRedirects 设为 true。" +
            "注意：PATCH 在部分运行时会降级为 POST 加 X-HTTP-Method-Override 头（调用日志里会注明）。"

    override val parametersJson: String = """
        {
          "type": "object",
          "properties": {
            "url": {
              "type": "string",
              "description": "完整 URL，必须带协议，例如 http://192.168.1.5:8080/api/status"
            },
            "method": {
              "type": "string",
              "enum": ["GET", "POST", "PUT", "DELETE", "PATCH"],
              "description": "HTTP 方法，默认 GET"
            },
            "headers": {
              "type": "object",
              "additionalProperties": {"type": "string"},
              "description": "请求头键值对，可选"
            },
            "body": {
              "type": "string",
              "description": "请求体，可选；传对象或数组时会自动序列化成 JSON 字符串"
            },
            "timeoutMs": {
              "type": "integer",
              "description": "连接与读取超时毫秒数，默认 15000，上限 60000"
            },
            "followRedirects": {
              "type": "boolean",
              "description": "是否跟随 3xx 重定向，默认 false（避免被重定向到未预期地址）"
            }
          },
          "required": ["url"]
        }
    """.trimIndent()

    override suspend fun execute(argumentsJson: String): ToolResult {
        val startedAt = System.currentTimeMillis()
        val request = try {
            parseRequest(argumentsJson)
        } catch (error: Throwable) {
            logger("[" + timestamp() + "] net_request (unparsed arguments) -> ERROR " + describe(error))
            return ToolResult.Error("net_request 参数不合法：" + describe(error))
        }

        var methodNote = ""
        var connection: HttpURLConnection? = null
        try {
            val url = URI(request.url).toURL()
            if (url.protocol != "http" && url.protocol != "https") {
                val message = "只支持 http / https，收到协议：" + url.protocol
                logger(callLog(startedAt, request.method, request.url, "ERROR " + message, request.headers))
                return ToolResult.Error(message)
            }
            val opened = url.openConnection()
            if (opened !is HttpURLConnection) {
                val message = "该协议不支持 HttpURLConnection：" + url.protocol
                logger(callLog(startedAt, request.method, request.url, "ERROR " + message, request.headers))
                return ToolResult.Error(message)
            }
            connection = opened

            methodNote = if (applyMethod(opened, request.method)) " (sent as POST + X-HTTP-Method-Override)" else ""
            opened.connectTimeout = request.timeoutMs
            opened.readTimeout = request.timeoutMs
            opened.instanceFollowRedirects = request.followRedirects
            opened.useCaches = false
            opened.setRequestProperty("Accept", "*/*")
            for ((headerName, headerValue) in request.headers) {
                try {
                    opened.setRequestProperty(headerName, headerValue)
                } catch (_: IllegalArgumentException) {
                    // Host / Content-Length 等受限头：HttpURLConnection 拒绝，跳过即可。
                }
            }
            if (request.body != null) {
                if (opened.getRequestProperty("Content-Type") == null) {
                    opened.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                val payload = request.body.toByteArray(Charsets.UTF_8)
                opened.doOutput = true
                // 流式模式在跟随重定向时无法重放请求体，跟随开启时退回普通输出流。
                if (!request.followRedirects) opened.setFixedLengthStreamingMode(payload.size)
                opened.outputStream.use { it.write(payload) }
            }

            val status = opened.responseCode
            val responseHeaders = LinkedHashMap<String, List<String>>()
            for ((headerName, headerValues) in opened.headerFields) {
                if (headerName == null) continue
                responseHeaders[headerName] = headerValues?.filterNotNull().orEmpty()
            }
            val charset = charsetOf(opened.contentType)
            val stream = if (status >= 400) opened.errorStream else opened.inputStream
            val read = readCapped(stream, charset)
            val outcome = "HTTP " + status + methodNote + if (read.second) " (body truncated)" else ""
            logger(callLog(startedAt, request.method, request.url, outcome, request.headers))
            return ToolResult.Ok(resultJson(status, responseHeaders, read.first, read.second))
        } catch (error: Throwable) {
            logger(callLog(startedAt, request.method, request.url, "ERROR " + describe(error) + methodNote, request.headers))
            return ToolResult.Error("net_request 失败：" + describe(error))
        } finally {
            connection?.disconnect()
        }
    }

    private data class Request(
        val url: String,
        val method: String,
        val headers: Map<String, String>,
        val body: String?,
        val timeoutMs: Int,
        val followRedirects: Boolean,
    )

    private fun parseRequest(argumentsJson: String): Request {
        val args = Json.parse(argumentsJson) as? Map<*, *>
            ?: throw IllegalArgumentException("arguments 必须是 JSON 对象")
        val url = (args["url"] as? String)?.trim().orEmpty()
        require(url.isNotEmpty()) { "url 是必填参数" }
        val method = (args["method"] as? String)?.trim()?.uppercase(Locale.US).orEmpty().ifEmpty { "GET" }
        require(ALLOWED_METHODS.contains(method)) {
            "method 只支持 " + ALLOWED_METHODS.joinToString("/") + "，收到 " + method
        }
        return Request(
            url = url,
            method = method,
            headers = parseHeaders(args["headers"]),
            body = parseBody(args["body"]),
            timeoutMs = parseTimeout(args["timeoutMs"]),
            followRedirects = args["followRedirects"] as? Boolean ?: false,
        )
    }

    private fun parseHeaders(value: Any?): Map<String, String> {
        if (value == null) return emptyMap()
        val map = value as? Map<*, *> ?: throw IllegalArgumentException("headers 必须是 JSON 对象")
        val result = LinkedHashMap<String, String>()
        for ((rawName, rawValue) in map) {
            val headerName = rawName?.toString()?.trim().orEmpty()
            if (headerName.isEmpty()) continue
            result[headerName] = when (rawValue) {
                null -> ""
                is List<*> -> rawValue.filterNotNull().joinToString(", ")
                is Map<*, *> -> Json.stringify(rawValue)
                else -> rawValue.toString()
            }
        }
        return result
    }

    private fun parseBody(value: Any?): String? = when {
        value == null -> null
        value is String -> value
        value is Map<*, *> || value is List<*> -> Json.stringify(value)
        else -> value.toString()
    }

    private fun parseTimeout(value: Any?): Int {
        val requested = when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull() ?: DEFAULT_TIMEOUT_MS
            else -> DEFAULT_TIMEOUT_MS
        }
        return when {
            requested <= 0 -> DEFAULT_TIMEOUT_MS
            requested > MAX_TIMEOUT_MS -> MAX_TIMEOUT_MS
            else -> requested
        }
    }

    /**
     * HttpURLConnection 的方法白名单里没有 PATCH：先直连，再试反射改写 method 字段；
     * 反射被模块系统挡住时（如 JDK 17+ 未 --add-opens）退到 X-HTTP-Method-Override 约定，真实动词只能是 POST。
     *
     * @return 是否降级成 POST + 覆盖头。
     */
    private fun applyMethod(connection: HttpURLConnection, method: String): Boolean {
        try {
            connection.requestMethod = method
            return false
        } catch (_: ProtocolException) {
            // 落到下面的反射分支。
        }
        try {
            val field = HttpURLConnection::class.java.getDeclaredField("method")
            field.isAccessible = true
            field.set(connection, method)
            return false
        } catch (_: Throwable) {
            // 反射也被挡住，继续走降级分支。
        }
        connection.requestMethod = "POST"
        connection.setRequestProperty("X-HTTP-Method-Override", method)
        return true
    }

    private fun readCapped(stream: InputStream?, charset: Charset): Pair<String, Boolean> {
        if (stream == null) return "" to false
        val collected = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var truncated = false
        stream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                val room = MAX_BODY_BYTES - collected.size()
                if (read > room) {
                    collected.write(buffer, 0, room)
                    truncated = true
                    break
                }
                collected.write(buffer, 0, read)
                if (collected.size() >= MAX_BODY_BYTES) {
                    // 正好读满上限：再探一字节，判断是否真的还有内容。
                    if (input.read() >= 0) truncated = true
                    break
                }
            }
        }
        return String(collected.toByteArray(), charset) to truncated
    }

    private fun charsetOf(contentType: String?): Charset {
        if (contentType == null) return Charsets.UTF_8
        val lower = contentType.lowercase(Locale.US)
        val marker = lower.indexOf("charset=")
        if (marker < 0) return Charsets.UTF_8
        var name = contentType.substring(marker + "charset=".length)
        val end = name.indexOfFirst { it == ';' || it == ' ' }
        if (end >= 0) name = name.substring(0, end)
        return try {
            Charset.forName(name.trim().trim('"', '\''))
        } catch (_: Throwable) {
            Charsets.UTF_8
        }
    }

    private fun resultJson(
        status: Int,
        headers: Map<String, List<String>>,
        body: String,
        truncated: Boolean,
    ): String {
        val out = StringBuilder(body.length + 256)
        val quote = '"'
        out.append('{').append(quote).append("status").append(quote).append(':').append(status)
        out.append(',').append(quote).append("headers").append(quote).append(':').append('{')
        var firstHeader = true
        for ((headerName, headerValues) in headers) {
            if (!firstHeader) out.append(',')
            firstHeader = false
            out.append(Json.quote(headerName)).append(":[")
            for (index in headerValues.indices) {
                if (index > 0) out.append(',')
                out.append(Json.quote(headerValues[index]))
            }
            out.append(']')
        }
        out.append('}').append(',').append(quote).append("body").append(quote).append(':').append(Json.quote(body))
        out.append(',').append(quote).append("truncated").append(quote).append(':').append(truncated)
        out.append('}')
        return out.toString()
    }

    private fun describe(error: Throwable): String {
        if (error is IllegalArgumentException || error is ProtocolException) {
            return error.message ?: error.javaClass.simpleName
        }
        val message = error.message?.takeIf { it.isNotBlank() }
        return if (message == null) error.javaClass.simpleName else error.javaClass.simpleName + ": " + message
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000
        const val MAX_TIMEOUT_MS = 60_000
        const val MAX_BODY_BYTES = 256 * 1024
        val ALLOWED_METHODS = listOf("GET", "POST", "PUT", "DELETE", "PATCH")
    }
}
