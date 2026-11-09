package com.aiclassroom.app.web

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 网页操作的「当面确认」通道。
 *
 * 工具跑在 IO 线程，用户看的是 Compose 界面，两边靠这个对象对接：
 * 工具 [ask] 挂起并把请求挂到 [pending]，界面观察到 [pending] 就弹框，
 * 用户点完按钮走 [answer] 唤醒工具。任何一环没有应答（界面不在前台、超时）
 * 都按「拒绝」处理——这是安全默认值，绝不因为没弹出来就默默放行。
 */
class WebConsentBroker {

    class Request(
        val id: Long,
        val title: String,
        val message: String,
        val answer: CompletableDeferred<Boolean>,
    )

    private val state = mutableStateOf<Request?>(null)

    /** 当前等待用户回答的请求；null 表示没有待确认项。界面直接读这个字段。 */
    val pending: Request?
        get() = state.value

    private var nextId = 1L

    /**
     * 弹一次确认并等待用户点击。无人应答或超时返回 false。
     * 必须在非主线程挂起调用：主线程上等待会把自己卡死。
     */
    suspend fun ask(title: String, message: String, timeoutMs: Long = CONFIRM_TIMEOUT_MS): Boolean {
        val request = Request(nextId++, title, message, CompletableDeferred())
        state.value = request
        return try {
            withTimeoutOrNull(timeoutMs) { request.answer.await() } ?: false
        } finally {
            if (state.value?.id == request.id) state.value = null
        }
    }

    /** 界面层回调：把用户的取舍交回挂起的工具调用。 */
    fun answer(id: Long, allowed: Boolean) {
        val request = state.value?.takeIf { it.id == id } ?: return
        state.value = null
        request.answer.complete(allowed)
    }

    companion object {
        /** 用户没在 2 分钟内回应就当作拒绝，避免 AI 一直卡着等。 */
        const val CONFIRM_TIMEOUT_MS: Long = 120_000L
    }
}

/** 待确认项的弹框。没有待确认项时直接不渲染。 */
@Composable
fun WebConsentDialog(broker: WebConsentBroker) {
    val request = broker.pending ?: return
    AlertDialog(
        onDismissRequest = { broker.answer(request.id, false) },
        title = { Text(request.title) },
        text = { Text(request.message) },
        confirmButton = {
            TextButton(onClick = { broker.answer(request.id, true) }) { Text("允许这一次") }
        },
        dismissButton = {
            TextButton(onClick = { broker.answer(request.id, false) }) { Text("不允许") }
        },
    )
}
