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
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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
    private companion object {
        val io = Executors.newCachedThreadPool { task -> Thread(task, "classroom-http").apply { isDaemon = true } }
    }

    override suspend fun post(
        request: HttpRequest,
        onLine: (String) -> Unit,
    ): HttpExchange = suspendCancellableCoroutine { continuation ->
        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            request.headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

        // 独立阻塞任务：取消回调在取消开始时触发，不等 readLine 返回后才 disconnect。
        val future = io.submit {
        try {
            continuation.context.ensureActive()
            val exchange = exchange(connection, request, onLine) { continuation.context.ensureActive() }
            if (continuation.isActive) continuation.resume(exchange)
        } catch (t: Throwable) {
            if (continuation.isActive) continuation.resumeWithException(t)
        } finally {
            runCatching { connection.disconnect() }
        }
        }
        continuation.invokeOnCancellation {
            future.cancel(true)
            // 部分 ROM 的 disconnect 自己会等待网络锁，不让取消线程被它阻塞。
            io.execute { runCatching { connection.disconnect() } }
        }
    }

    private fun exchange(connection: HttpURLConnection, request: HttpRequest,
        onLine: (String) -> Unit, ensureActive: () -> Unit): HttpExchange {
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
                            ensureActive()
                            val line = reader.readLine() ?: break
                            ensureActive()
                            onLine(line)
                        }
                        completed = true
                    } else {
                        // 错误体通常很短，整段读下来当诊断信息。
                        errorMessage = reader.readText().take(600)
                    }
                }
            }

            return HttpExchange(
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
