package com.aiclassroom.feature.plugin.ui

import com.aiclassroom.feature.plugin.PluginExtensionType
import com.aiclassroom.feature.plugin.PluginLogger
import com.aiclassroom.feature.plugin.PluginRuntime
import com.aiclassroom.feature.plugin.PluginSlots
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 界面驱动：让插件既能**主动刷**（ui_ticker 每秒跑一次），又能**被宿主问**（ui_overlay.provider）。
 *
 * 两个概念分开：
 * - [paused] 是**应用级**的总闸（切后台 / 生命周期 ON_STOP），宿主一处控制；
 * - [visiblePlugins] 是**插件级**的可见集合（插件未启用、所在插槽不在当前页面时为不可见）。
 *   只有两者都「开」的插件才真的在跑 ticker —— 沉浸模式没打开时，时钟插件不应该空转耗电。
 *
 * 单例约束：同一插件的 ui_ticker 最多 1 个，manifest 校验已经拦住了声明侧，
 * 这里再按 pluginId 去重一次，防止宿主重复装载同一个插件时起两条循环。
 */
class PluginUiDriver(
    private val logger: PluginLogger = {},
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** 触发一次重绘：宿主把某个插件的 stateVersion 自增。 */
    private val onTick: (String) -> Unit = {},
) {

    private val lock = Any()
    private val jobs = LinkedHashMap<String, Job>()

    @Volatile
    private var paused: Boolean = false

    @Volatile
    private var visible: Set<String> = emptySet()

    /** 当前在跑的 ticker 数量（宿主切前后台 / 卸载时自检用）。 */
    val runningCount: Int get() = synchronized(lock) { jobs.size }

    /** 应用级暂停/恢复：切后台暂停，回前台恢复。 */
    fun setPaused(paused: Boolean) {
        synchronized(lock) { this.paused = paused }
    }

    /**
     * 插件级可见性：出现在当前页面的插件 id 集合（FULLSCREEN 只有在全屏层真的打开时才算可见）。
     * 传进来的集合与当前一致时不动任何协程，避免每次重组都重启一轮循环。
     */
    fun setVisiblePlugins(pluginIds: Set<String>) {
        synchronized(lock) { visible = pluginIds }
    }

    /**
     * 按当前（已启用且可见）的插件集合重算 ticker 循环；重复调用幂等，**不会**重置已经在跑的循环。
     * 不可见 / 未启用 / 没声明 ui_ticker 的插件，其循环在这里被停掉。
     */
    fun start(plugins: List<PluginRuntime>, scope: CoroutineScope) {
        val desired = LinkedHashMap<String, Pair<PluginRuntime, Int>>()
        val enabledAndVisible = synchronized(lock) { visible }
        for (plugin in plugins) {
            val id = plugin.manifest.id
            if (id !in enabledAndVisible) continue
            if (synchronized(lock) { jobs.containsKey(id) }) continue
            val ticker = plugin.manifest.uiTickerExtension ?: continue
            val entry = ticker.entry ?: continue
            val intervalMillis = (ticker.intervalMillis ?: DEFAULT_INTERVAL_MILLIS).coerceAtLeast(1)
            desired[id] = plugin to intervalMillis
            logger("[plugin:" + id + "] 界面时钟已启动（" + intervalMillis + "ms，" + entry + "）")
        }
        synchronized(lock) {
            for ((id, spec) in desired) {
                if (jobs.containsKey(id)) continue
                val (plugin, intervalMillis) = spec
                val entry = plugin.manifest.uiTickerExtension?.entry ?: continue
                jobs[id] = scope.launch(dispatcher) { runLoop(plugin, entry, intervalMillis) }
            }
        }
        stopInvisible()
    }

    /** 停掉所有不满足「已启用 + 可见」条件的循环；已经暂停（整体）时也停。 */
    fun stopInvisible() {
        val stopping = synchronized(lock) {
            val allowed = visible
            val removed = mutableListOf<Job>()
            val iterator = jobs.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key !in allowed) {
                    removed += entry.value
                    iterator.remove()
                }
            }
            removed
        }
        stopping.forEach { it.cancel() }
    }

    /** 全停：卸载插件、关闭页面、app 退出时调。 */
    fun stopAll() {
        val stopping = synchronized(lock) {
            val snapshot = jobs.values.toList()
            jobs.clear()
            snapshot
        }
        stopping.forEach { it.cancel() }
        if (stopping.isNotEmpty()) logger("已停止 " + stopping.size + " 个插件界面时钟")
    }

    private suspend fun runLoop(plugin: PluginRuntime, entry: String, intervalMillis: Int) {
        while (true) {
            // 暂停时只是「这一轮不跑」，协程本身继续等下一个周期：
            // 恢复后立刻回到固定节奏，不需要宿主再调一次 start。
            if (!paused) {
                fireTick(plugin, entry, intervalMillis)
            }
            delay(intervalMillis.toLong())
        }
    }

    private fun fireTick(plugin: PluginRuntime, entry: String, intervalMillis: Int) {
        val payload = "{\"firedAt\":" + System.currentTimeMillis() + ",\"intervalMs\":" + intervalMillis + "}"
        try {
            plugin.runEvent(entry, TICK_EVENT_NAME, payload)
        } catch (t: Throwable) {
            // 单轮失败只影响这一帧：记日志，下一帧照常，界面保留上一次的值。
            logger("[plugin:" + plugin.manifest.id + "] ui_ticker 回调失败（本帧跳过）：" + describe(t))
            return
        }
        // entry 返回什么都被忽略：它只负责更新 storage / 内部状态，界面去 provider 或 state 取最新值。
        runCatching { onTick(plugin.manifest.id) }
            .onFailure { logger("[plugin:" + plugin.manifest.id + "] ui_ticker 重绘失败：" + describe(it)) }
    }

    private fun describe(t: Throwable): String = t.message ?: t.javaClass.simpleName

    companion object {
        /** ui_ticker 回调里 ctx.name 的取值（与 timer 的 "timer" 区分开，插件可以据此分流）。 */
        const val TICK_EVENT_NAME = "ui_tick"

        private const val DEFAULT_INTERVAL_MILLIS = 1_000
    }
}

/**
 * FULLSCREEN 插槽的**单占用**仲裁。
 *
 * 规则：同一时刻只有一个插件占用全屏层；多个插件都声明了 FULLSCREEN 时，
 * 按插件启用顺序（宿主给的列表顺序，即装载顺序）取第一个，其余忽略并记一条日志。
 * 插件自己 enter/exit 只改变「它想不想独占」；最终谁在画还要过 [resolve]。
 */
class FullscreenArbiter(private val logger: PluginLogger = {}) {

    /** 插件自己声明的意愿（host.enterFullscreen / exitFullscreen）。 */
    private val requested = LinkedHashSet<String>()

    /** 声明了 FULLSCREEN 插槽的插件，按启用顺序。 */
    @Volatile
    private var declared: List<String> = emptyList()

    /** 宿主装载结果变化时重算声明集合；消失的插件同时清掉它的意愿。 */
    fun setDeclared(pluginIds: List<String>) {
        synchronized(requested) {
            declared = pluginIds
            requested.retainAll(pluginIds.toSet())
        }
    }

    fun request(pluginId: String) {
        synchronized(requested) { requested += pluginId }
    }

    fun release(pluginId: String) {
        synchronized(requested) { requested -= pluginId }
    }

    fun isRequested(pluginId: String): Boolean = synchronized(requested) { pluginId in requested }

    /** 上一次记过日志的冲突组合；相同就不重复记，否则每秒一次仲裁会把 logcat 刷满。 */
    private var lastConflict: String? = null

    /** 当前真正占用全屏层的插件 id；没有则 null。 */
    fun resolve(): String? {
        val order = declared
        val want = synchronized(requested) { requested.toSet() }
        val winner = order.firstOrNull { it in want }
        // 有多个插件都想独占：只画第一个，其余明确记日志（不静默）。
        val ignored = if (winner == null) emptyList() else order.filter { it != winner && it in want }
        val signature = winner?.let { w -> (listOf(w) + ignored).joinToString(",") }.orEmpty()
        if (ignored.isNotEmpty() && signature != lastConflict) {
            logger("FULLSCREEN 同一时刻只允许一个插件占用，已选择 " + winner + "，忽略 " + ignored.joinToString(","))
        }
        lastConflict = if (ignored.isEmpty()) null else signature
        return winner
    }

    /** 声明了 FULLSCREEN 的插件 id 列表（宿主用它算可见集合）。 */
    fun declaredPlugins(): List<String> = declared

    companion object {
        /** 从一个插件的 manifest 里取它声明 FULLSCREEN 用的 slot 名。 */
        const val SLOT: String = PluginSlots.FULLSCREEN

        /** 该 runtime 是否声明了 FULLSCREEN 插槽。 */
        fun declaresFullscreen(runtime: PluginRuntime): Boolean =
            runtime.manifest.extensions.any {
                it.type == PluginExtensionType.UI_OVERLAY && it.slot == SLOT
            }
    }
}
