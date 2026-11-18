package com.aiclassroom.app.classroom

import com.aiclassroom.classroom.gateway.HttpExchange
import com.aiclassroom.classroom.gateway.HttpRequest
import com.aiclassroom.classroom.gateway.HttpTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * HttpURLConnection 的传输实现。
 *
 * 三个必须做对的点：
 * 1. **取消要解绑连接**：协程被取消时主线程会立刻返回，但底层 socket 还在读，
 *    不解绑就会卡到 readTimeout。这里用 invokeOnCancellation 直接 disconnect。
 * 2. **completed 只认正常读到 EOF**：状态 200 但连接中途断掉，正文是不完整的。
 * 3. **Retry-After 只解析秒数形式**：HTTP-date 形式少见，宁可返回 null 走本地退避，
 *    也不要算错时间让用户干等。
 */
class AndroidHttpTransport(
    private val connectTimeoutMs: Int = 20_000,
    private val readTimeoutMs: Int = 180_000,
) : HttpTransport {

    override suspend fun post(
        request: HttpRequest,
        onLine: (String) -> Unit,
    ): HttpExchange = withContext(Dispatchers.IO) {
        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            request.headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

        // 取消时立刻断开 socket，避免线程继续阻塞在 readLine 上。
        val handle = coroutineContext.job.invokeOnCompletion { cause ->
            if (cause != null) connection.disconnect()
        }

        try {
            connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val retryAfter = parseRetryAfter(connection.getHeaderField("Retry-After"))
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream

            var completed = false
            var errorMessage: String? = null

            if (stream != null) {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    if (status in 200..299) {
                        while (true) {
                            coroutineContext.ensureActive()
                            val line = reader.readLine() ?: break
                            onLine(line)
                        }
                        completed = true
                    } else {
                        // 错误体通常很短，整段读下来当诊断信息。
                        errorMessage = reader.readText().take(600)
                    }
                }
            }

            HttpExchange(
                status = status,
                completed = completed,
                retryAfterMs = retryAfter,
                errorMessage = if (status in 200..299) null else errorMessage,
            )
        } catch (c: CancellationException) {
            connection.disconnect()
            throw c
        } catch (t: Throwable) {
            connection.disconnect()
            throw t
        } finally {
            handle.dispose()
            runCatching { connection.disconnect() }
        }
    }

    /** 只认秒数形式（`Retry-After: 12`）；日期形式返回 null，交给本地指数退避。 */
    private fun parseRetryAfter(raw: String?): Long? {
        val seconds = raw?.trim()?.toLongOrNull() ?: return null
        if (seconds < 0) return null
        return (seconds * 1000L).coerceAtMost(30_000L)
    }
}