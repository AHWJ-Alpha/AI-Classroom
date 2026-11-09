package com.aiclassroom.app.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.http.SslError
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CancellationException

/**
 * 无头（离屏）WebView：不入界面树，手工 measure/layout 之后把 WebView 画到 Bitmap 上。
 *
 * 为什么必须开 JS：现在的登录页、搜索页、SPA 全是运行时渲染出来的，
 * 只抓 HTML 源码（net_request 那条路）拿到的是空壳，视觉模型看了也说不出页面上有什么。
 *
 * 线程约束很硬：WebView 的创建、加载、measure/layout、draw、destroy 全部得在主线程，
 * 换线程会直接抛 "All WebView methods must be called on the same thread"。
 * 所以整段都跑在 [Dispatchers.Main] 上，靠挂起（delay / await）让出主线程，
 * 不做任何阻塞等待；真正耗 CPU 的 PNG 编码由调用方挪回 IO 线程。
 */
class WebCaptureEngine(private val context: Context) {

    /**
     * 打开 [url]，等页面加载完并静置 [waitMillis] 后，把页面句柄交给 [block]。
     *
     * [block] 跑在主线程上，可以连续做「看一眼 → 判断 → 再操作」，页面只加载一次；
     * 整个过程受 [timeoutMs] 总预算约束，超时与异常都收成 Result.failure。
     */
    suspend fun <T> withPage(
        url: String,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
        waitMillis: Long = DEFAULT_WAIT_MILLIS,
        timeoutMs: Long = CAPTURE_TIMEOUT_MS,
        block: suspend (PageHandle) -> T,
    ): Result<T> = withContext(Dispatchers.Main) {
        val target = normalizeUrl(url) ?: return@withContext Result.failure(
            IllegalArgumentException("只支持 http / https 地址，收到：" + url.take(120))
        )
        val safeWidth = width.coerceIn(MIN_SIZE, MAX_SIZE)
        val safeHeight = height.coerceIn(MIN_SIZE, MAX_SIZE)
        val safeWait = waitMillis.coerceIn(0L, MAX_WAIT_MILLIS)

        var webView: WebView? = null
        val outcome = withTimeoutOrNull(timeoutMs) {
            val loaded = CompletableDeferred<String?>()
            try {
                val view = newWebView()
                webView = view
                view.webViewClient = loadClient(target, loaded)
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(safeWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(safeHeight, View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, safeWidth, safeHeight)
                view.loadUrl(target)

                val loadError = withTimeoutOrNull(LOAD_TIMEOUT_MS) { loaded.await() }
                    ?: return@withTimeoutOrNull Result.failure<T>(
                        IllegalStateException("加载超时（" + (LOAD_TIMEOUT_MS / 1000) + " 秒内没有触发页面加载完成）：" + target)
                    )
                if (loadError != null) return@withTimeoutOrNull Result.failure(IllegalStateException(loadError))

                // 页面 load 完成 ≠ 渲染完成：React / Vue 那一挂还要再跑一会儿才把内容挂上去。
                if (safeWait > 0) delay(safeWait)

                Result.success(block(PageHandle(view, safeWidth, safeHeight)))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Result.failure<T>(IllegalStateException("页面操作失败：" + describe(throwable), throwable))
            }
        }
        webView?.let { view ->
            runCatching {
                view.stopLoading()
                view.loadUrl("about:blank")
                view.destroy()
            }.onFailure { Log.w(TAG, "销毁离屏 WebView 失败：" + it.message) }
        }
        outcome ?: Result.failure(IllegalStateException("页面处理超时（" + (timeoutMs / 1000) + " 秒）：" + target))
    }

    /** 只截图不操作的便捷入口。 */
    suspend fun capture(
        url: String,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
        waitMillis: Long = DEFAULT_WAIT_MILLIS,
        scrollY: Int = 0,
        timeoutMs: Long = CAPTURE_TIMEOUT_MS,
    ): Result<Bitmap> = withPage(url, width, height, waitMillis, timeoutMs) { page ->
        if (scrollY > 0) page.scrollTo(scrollY)
        page.snapshot()
    }

    /** 一次 [withPage] 期间持有的活动页面。所有方法都必须在主线程调用，由 [withPage] 保证。 */
    class PageHandle internal constructor(
        private val view: WebView,
        private val width: Int,
        private val height: Int,
    ) {
        /** 跑一段宿主拼好的脚本并等它的返回值。AI 传的裸 JS 不会走到这里。 */
        suspend fun eval(script: String): Result<String> {
            val answer = CompletableDeferred<String>()
            view.evaluateJavascript(script) { value -> answer.complete(value.orEmpty()) }
            val raw = withTimeoutOrNull(JS_TIMEOUT_MS) { answer.await() }
                ?: return Result.failure(IllegalStateException("页面脚本执行超时"))
            return Result.success(raw)
        }

        suspend fun scrollTo(y: Int) {
            // 离屏 WebView 的 scrollTo 没有真实滚动容器可依附，交给页面自己滚。
            eval("window.scrollTo(0," + y.coerceAtLeast(0) + ");").let { }
            delay(SCROLL_SETTLE_MS)
        }

        suspend fun waitFor(ms: Long) {
            if (ms > 0) delay(ms.coerceAtMost(MAX_ACTION_WAIT_MS))
        }

        /** 把当前视口画到一张新 Bitmap 上。 */
        fun snapshot(): Bitmap {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            // 背景透明的页面直接画会得到一张全黑图，先铺白底。
            canvas.drawColor(Color.WHITE)
            view.draw(canvas)
            return bitmap
        }
    }

    /** 每次现建一个 WebView：不复用就不会把上一页的 cookie / JS 状态带进下一次操作。 */
    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(): WebView {
        val view = WebView(context)
        val settings: WebSettings = view.settings
        // 现代站点没有 JS 就是一张白纸，这一条是截图能不能用的前提。
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        // 不把页面内容写进应用数据库，截完就忘。
        settings.saveFormData = false
        settings.setGeolocationEnabled(false)
        view.setBackgroundColor(Color.WHITE)
        return view
    }

    private fun loadClient(target: String, loaded: CompletableDeferred<String?>): WebViewClient =
        object : WebViewClient() {
            override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                loaded.complete(null)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                // 只有主文档失败才算整次操作失败；子资源（图片、统计脚本）挂了不影响。
                if (request?.isForMainFrame != true) return
                val reason = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    error?.description?.toString().orEmpty()
                } else {
                    ""
                }
                loaded.complete(reason.ifBlank { "页面加载失败：" + target })
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?,
            ) {
                // 证书不合法就地取消，绝不"继续"——截一张中间人页面没有意义。
                handler?.cancel()
                loaded.complete("证书校验失败：" + (error?.primaryError ?: -1))
            }
        }

    companion object {
        private const val TAG = "WebCapture"

        const val DEFAULT_WIDTH = 1080
        const val DEFAULT_HEIGHT = 1920
        const val DEFAULT_WAIT_MILLIS = 2500L
        const val CAPTURE_TIMEOUT_MS = 15_000L
        const val ACT_TIMEOUT_MS = 30_000L
        private const val LOAD_TIMEOUT_MS = 12_000L
        private const val JS_TIMEOUT_MS = 5_000L
        private const val SCROLL_SETTLE_MS = 400L
        private const val MAX_ACTION_WAIT_MS = 10_000L
        private const val MIN_SIZE = 240
        private const val MAX_SIZE = 4000
        private const val MAX_WAIT_MILLIS = 15_000L

        /** 只放行 http / https；javascript: / file: / content: 一律在这里挡掉。 */
        fun normalizeUrl(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val lowered = trimmed.lowercase()
            if (!lowered.startsWith("http://") && !lowered.startsWith("https://")) return null
            return trimmed
        }

        private fun describe(throwable: Throwable): String =
            throwable.message?.takeIf { it.isNotBlank() } ?: throwable.javaClass.simpleName
    }
}
