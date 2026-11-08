package com.aiclassroom.feature.exam.tool.image

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL

/** 单张图片的硬上限：20MB。 */
internal const val MAX_IMAGE_BYTES: Int = 20 * 1024 * 1024

/** base64 比原始字节多约 1/3，再给 JSON 包装留余量。 */
private const val MAX_RESPONSE_BYTES: Int = MAX_IMAGE_BYTES * 2

internal const val DEFAULT_TIMEOUT_SECONDS: Int = 120
internal const val MAX_TIMEOUT_SECONDS: Int = 300

/** 错误响应回填给模型的最大片段长度。 */
private const val SNIPPET_LIMIT: Int = 400

/** 生图链路上的可预期失败；统一由 generate_image 收口成 ToolResult.Error。 */
internal class ImageFailure(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 最小的 HTTP 客户端：java.net.HttpURLConnection，不引任何依赖。
 * 所有失败都以 [ImageFailure] 抛出（带状态码与响应片段），不做静默吞掉。
 */
internal object ImageHttp {

    fun postJson(url: String, jsonBody: String, timeoutSeconds: Int, headers: Map<String, String>): String {
        val payload = jsonBody.toByteArray(Charsets.UTF_8)
        val connection = open(url, timeoutSeconds)
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            return readText(url, connection)
        } catch (failure: ImageFailure) {
            throw failure
        } catch (timeout: SocketTimeoutException) {
            throw ImageFailure("请求超时（${timeoutSeconds} 秒）：$url", timeout)
        } catch (io: IOException) {
            throw ImageFailure("请求 $url 失败：${describe(io)}", io)
        } finally {
            connection.disconnect()
        }
    }

    fun getImageBytes(url: String, timeoutSeconds: Int): ByteArray {
        val connection = open(url, timeoutSeconds)
        try {
            connection.requestMethod = "GET"
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.let { readCapped(it, MAX_IMAGE_BYTES, url, "图片") } ?: ByteArray(0)
            if (status !in 200..299) {
                throw ImageFailure("下载图片失败 HTTP $status（$url）：${snippet(String(bytes, Charsets.UTF_8)).ifEmpty { "响应体为空" }}")
            }
            if (bytes.isEmpty()) throw ImageFailure("下载图片失败：$url 返回了空响应体")
            return bytes
        } catch (failure: ImageFailure) {
            throw failure
        } catch (timeout: SocketTimeoutException) {
            throw ImageFailure("下载图片超时（${timeoutSeconds} 秒）：$url", timeout)
        } catch (io: IOException) {
            throw ImageFailure("下载图片失败（$url）：${describe(io)}", io)
        } finally {
            connection.disconnect()
        }
    }

    private fun readText(url: String, connection: HttpURLConnection): String {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.let { String(readCapped(it, MAX_RESPONSE_BYTES, url, "响应体"), Charsets.UTF_8) } ?: ""
        if (status !in 200..299) {
            val detail = snippet(text)
            throw ImageFailure("HTTP $status（$url）：" + if (detail.isEmpty()) "响应体为空" else detail)
        }
        return text
    }

    private fun open(url: String, timeoutSeconds: Int): HttpURLConnection {
        val parsed = try {
            URL(url)
        } catch (malformed: MalformedURLException) {
            throw ImageFailure("生图地址不合法：$url", malformed)
        }
        if (!parsed.protocol.equals("http", true) && !parsed.protocol.equals("https", true)) {
            throw ImageFailure("只支持 http / https 地址，收到协议：${parsed.protocol}")
        }
        val connection = parsed.openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutSeconds * 1000
        connection.readTimeout = timeoutSeconds * 1000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/json, image/*")
        connection.setRequestProperty("User-Agent", "aiclassroom-image-tool")
        return connection
    }

    private fun readCapped(stream: InputStream, limit: Int, url: String, label: String): ByteArray {
        val out = ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var total = 0
        stream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) throw ImageFailure("${label}超过上限 ${limit / (1024 * 1024)}MB（$url）")
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }

    private fun describe(io: IOException): String = io.message?.takeIf { it.isNotBlank() } ?: io::class.java.simpleName

    /** 压成一行并截断，避免把几十 KB 的 HTML 错误页塞回模型上下文。 */
    private fun snippet(text: String): String {
        val collapsed = text.replace(Regex("\\s+"), " ").trim()
        return if (collapsed.length <= SNIPPET_LIMIT) collapsed else collapsed.take(SNIPPET_LIMIT) + "…（已截断）"
    }
}
