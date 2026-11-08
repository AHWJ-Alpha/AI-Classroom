package com.aiclassroom.feature.exam.tool.universal

import com.aiclassroom.feature.exam.tool.image.ImageFailure
import com.aiclassroom.feature.exam.tool.image.MAX_IMAGE_BYTES
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL

/** base64 比原始字节多约 1/3，再给 JSON 包装留余量；超了就别往内存里塞。 */
private const val MAX_RESPONSE_BYTES: Int = MAX_IMAGE_BYTES * 2

/** 错误回填给模型的响应体片段长度：接未知服务时这是最有用的线索。 */
internal const val RESPONSE_SNIPPET_LIMIT: Int = 300

/** 一次请求的原始结果。状态码不做判定，由调用方决定语义。 */
internal class UniversalFetch(
    val status: Int,
    val bytes: ByteArray,
) {
    val ok: Boolean get() = status in 200..299

    fun text(): String = String(bytes, Charsets.UTF_8)
}

/**
 * universal_call 专用的最小 HTTP 客户端：任意 method + 任意请求头 + 可选请求体。
 *
 * 为什么不直接用 ImageHttp：那个只提供「POST JSON / GET 图片」两个固定形状（GET 不带请求头），
 * 而通用适配需要任意方法、任意头、以及同时拿到「状态码 + 原始字节 + 文本」。
 * 失败统一抛 [ImageFailure]，并且**一定**带上服务端响应体片段。
 */
internal object UniversalHttp {

    fun send(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
        timeoutSeconds: Int,
    ): UniversalFetch {
        val connection = open(url, timeoutSeconds)
        try {
            connection.requestMethod = method
            headers.forEach { (name, value) ->
                if (name.isNotBlank() && value.isNotBlank()) connection.setRequestProperty(name, value)
            }
            if (body != null && body.isNotEmpty() && method != "GET" && method != "HEAD") {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.let { readCapped(it, MAX_RESPONSE_BYTES, url) } ?: ByteArray(0)
            return UniversalFetch(status, bytes)
        } catch (failure: ImageFailure) {
            throw failure
        } catch (timeout: SocketTimeoutException) {
            throw ImageFailure("请求超时（" + timeoutSeconds + " 秒）：" + method + " " + url, timeout)
        } catch (io: IOException) {
            throw ImageFailure("请求失败 " + method + " " + url + "：" + describe(io), io)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, timeoutSeconds: Int): HttpURLConnection {
        // 先用 URI（Java 20 起 URL(String) 已废弃），解析不过再退回 URL：
        // 未知服务常把中文文件名 / 空格塞进 URL，URI 严格按 RFC 会拒，URL 则当原样字节发出去。
        @Suppress("DEPRECATION")
        val parsed = try {
            URI(url).toURL()
        } catch (strict: Exception) {
            try {
                URL(url)
            } catch (lenient: Exception) {
                throw ImageFailure("请求地址不合法：" + url, lenient)
            }
        }
        if (!parsed.protocol.equals("http", true) && !parsed.protocol.equals("https", true)) {
            throw ImageFailure("只支持 http / https 地址，收到协议：" + parsed.protocol)
        }
        val connection = parsed.openConnection() as HttpURLConnection
        // 服务端可能用 405/重定向来试探，这里不做任何隐式重试，避免把提交请求发两遍。
        connection.instanceFollowRedirects = true
        connection.connectTimeout = timeoutSeconds * 1000
        connection.readTimeout = timeoutSeconds * 1000
        connection.setRequestProperty("Accept", "application/json, image/*, */*")
        connection.setRequestProperty("User-Agent", "aiclassroom-universal-tool")
        return connection
    }

    private fun readCapped(stream: InputStream, limit: Int, url: String): ByteArray {
        val out = ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var total = 0
        stream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) throw ImageFailure("响应体超过上限 " + (limit / (1024 * 1024)) + "MB（" + url + "）")
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }

    private fun describe(io: IOException): String =
        io.message?.takeIf { it.isNotBlank() } ?: io::class.java.simpleName
}

/** 压成一行并截断：错误信息里带的就是它，不能把几十 KB 的 HTML 错误页灌回模型。 */
internal fun snippet(text: String): String {
    val collapsed = text.replace(Regex("\\s+"), " ").trim()
    if (collapsed.isEmpty()) return "响应体为空"
    return if (collapsed.length <= RESPONSE_SNIPPET_LIMIT) {
        collapsed
    } else {
        collapsed.take(RESPONSE_SNIPPET_LIMIT) + "…（已截断）"
    }
}
