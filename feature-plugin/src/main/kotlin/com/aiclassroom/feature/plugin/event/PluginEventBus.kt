package com.aiclassroom.feature.plugin.event

import com.aiclassroom.feature.plugin.Json
import com.aiclassroom.feature.plugin.JsonNode
import com.aiclassroom.feature.plugin.PluginClock
import com.aiclassroom.feature.plugin.PluginRuntime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 一次事件触发。payload 只放短字符串键值（课堂名、条数、事件时间等），
 * 不做大数据搬运——需要内容的回调应当自己去 host.readXxx 取。
 */
data class PluginEvent(val name: String, val payload: Map<String, String> = emptyMap()) {
    /** payload 的 JSON 形式，交给插件时会被解析回对象。 */
    fun payloadJson(): String = Json.write(
        JsonNode.Obj(payload.mapValues { (_, value) -> JsonNode.Str(value) }),
    )
}

/** 一个被跳过的插件：谁、在哪个事件上、为什么。 */
data class DispatchSkipped(val pluginId: String, val event: String, val reason: String)

/** 一次分发的账本：谁收到了、谁被跳过了。 */
data class DispatchReport(
    val event: String,
    val delivered: List<String>,
    val skipped: List<DispatchSkipped>,
) {
    /** 没有任何插件参与（常用于「没装插件时零开销」的判断与日志过滤）。 */
    val isEmpty: Boolean get() = delivered.isEmpty() && skipped.isEmpty()
}

/**
 * 事件分发器：本模块只负责「什么时候调谁」。
 *
 * - 事件：宿主在真实时机调用 [dispatch]，逐个调插件注册的回调；
 * - 定时器：[startTimers] 按 manifest 的 timer 扩展点起后台循环，[stopTimers] 停掉。
 *
 * 隔离原则：单个插件抛异常 / 脚本超时只记进 [DispatchReport.skipped]，不影响其它插件，
 * 也绝不把异常抛给宿主主流程。
 */
class PluginEventBus(
    private val logger: (String) -> Unit,
    /** 秒 -> 延迟毫秒；单测用它把 60 秒压成几毫秒，生产用默认值。 */
    private val delayMillisProvider: (Int) -> Long = { it * 1000L },
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val lock = Any()
    private val timerJobs = mutableListOf<Job>()

    /** 当前活跃的定时器数量（宿主切前后台 / 卸载时用来自检）。 */
    val timerCount: Int get() = synchronized(lock) { timerJobs.size }

    /**
     * 把 [event] 分发给所有声明了该事件的插件。
     *
     * 调用方负责把它放在后台线程（app 侧一律 Dispatchers.IO），这里本身是阻塞的同步循环：
     * 串行调用可以让插件之间互不干扰，也让日志顺序可读。
     */
    fun dispatch(plugins: List<PluginRuntime>, event: PluginEvent, payloadJson: String = event.payloadJson()): DispatchReport {
        if (plugins.isEmpty()) return DispatchReport(event.name, emptyList(), emptyList())
        val delivered = mutableListOf<String>()
        val skipped = mutableListOf<DispatchSkipped>()
        for (plugin in plugins) {
            val entry = plugin.manifest.eventEntry(event.name) ?: continue
            try {
                plugin.runEvent(entry, event.name, payloadJson)
                delivered += plugin.manifest.id
            } catch (t: Throwable) {
                val reason = describe(t)
                skipped += DispatchSkipped(plugin.manifest.id, event.name, reason)
                logger("[plugin:" + plugin.manifest.id + "] 事件 " + event.name + " 回调失败（已跳过）：" + reason)
            }
        }
        if (delivered.isNotEmpty() || skipped.isNotEmpty()) {
            logger(
                "事件 " + event.name + " → 送达 [" + delivered.joinToString(",") + "]" +
                    (if (skipped.isEmpty()) "" else " 跳过 [" + skipped.joinToString(",") { it.pluginId + ":" + it.reason } + "]"),
            )
        }
        return DispatchReport(event.name, delivered, skipped)
    }

    /**
     * 按 manifest 为每个插件起一个定时器协程。重复调用会先 [stopTimers]（幂等），
     * 卸载插件后的正确用法就是：重新装载完再调一次 startTimers。
     */
    fun startTimers(plugins: List<PluginRuntime>, scope: CoroutineScope, clock: PluginClock = PluginClock.SYSTEM) {
        stopTimers()
        if (plugins.isEmpty()) return
        var started = 0
        for (plugin in plugins) {
            val timer = plugin.manifest.timerExtension ?: continue
            val entry = timer.entry ?: continue
            val intervalSeconds = timer.intervalSeconds ?: continue
            val waitMillis = delayMillisProvider(intervalSeconds).coerceAtLeast(1L)
            val job = scope.launch(dispatcher) {
                while (isActive) {
                    delay(waitMillis)
                    fireTimer(plugin, entry, intervalSeconds, clock)
                }
            }
            synchronized(lock) { timerJobs += job }
            started++
        }
        if (started > 0) logger("已启动 " + started + " 个插件定时器")
    }

    /** 停掉所有定时器；插件卸载、app 退到后台都必须调它，否则协程会一直空转。 */
    fun stopTimers() {
        val stopping = synchronized(lock) {
            val snapshot = timerJobs.toList()
            timerJobs.clear()
            snapshot
        }
        stopping.forEach { it.cancel() }
        if (stopping.isNotEmpty()) logger("已停止 " + stopping.size + " 个插件定时器")
    }

    private fun fireTimer(plugin: PluginRuntime, entry: String, intervalSeconds: Int, clock: PluginClock) {
        val payload = "{\"firedAt\":" + clock.nowMillis() + ",\"intervalSeconds\":" + intervalSeconds + "}"
        try {
            plugin.runEvent(entry, TIMER_EVENT_NAME, payload)
        } catch (t: Throwable) {
            // 定时器回调失败只影响这一轮：记日志，下一轮照常。
            logger("[plugin:" + plugin.manifest.id + "] 定时器回调失败（本轮跳过）：" + describe(t))
        }
    }

    private fun describe(t: Throwable): String = t.message ?: t.javaClass.simpleName

    companion object {
        /** 定时器回调里 ctx.name 的取值。 */
        const val TIMER_EVENT_NAME = "timer"
    }
}
