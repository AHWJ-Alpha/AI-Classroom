package com.aiclassroom.feature.plugin.host

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** 宿主能力调用超时（脚本线程等太久）。 */
class PluginHostTimeoutException(message: String) : Exception(message)

/**
 * 把挂起函数压成阻塞调用，供 JS 侧的同步外观使用（host.generateImage / host.speak ...）。
 *
 * 用 kotlin.coroutines.startCoroutine + CountDownLatch 实现，不引 kotlinx-coroutines 依赖。
 *
 * 限制（必须写清楚，宿主接入时才知道坑在哪）：
 * 1. 必须在非主线程调用。若宿主实现内部要求主线程继续调度（例如切到 Dispatchers.Main），
 *    会与被阻塞的线程互相等待而死锁 —— 因此宿主实现要么直接用 IO/后台线程，要么保证 completion 立即回调；
 * 2. 等待超过 [runBlocking] 的 timeoutMillis 会放弃等待并返回失败，但被放弃的挂起任务仍会在后台跑完；
 * 3. 这是单次「开始即等」的桥接，不是通用 runBlocking，不支持调度器切换后的恢复顺序保证。
 */
object BlockingSuspend {

    fun <T> runBlocking(timeoutMillis: Long, block: suspend () -> T): T {
        val latch = CountDownLatch(1)
        val outcome = AtomicReference<Result<T>?>()
        block.startCoroutine(object : Continuation<T> {
            override val context: CoroutineContext = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                outcome.set(result)
                latch.countDown()
            }
        })
        if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
            throw PluginHostTimeoutException("宿主能力调用超过 ${timeoutMillis}ms 未返回")
        }
        val settled = outcome.get() ?: throw PluginHostTimeoutException("宿主能力调用未返回值")
        return settled.getOrThrow()
    }
}
