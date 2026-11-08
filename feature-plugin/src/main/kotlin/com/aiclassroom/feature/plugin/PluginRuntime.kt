package com.aiclassroom.feature.plugin

import com.aiclassroom.feature.plugin.hook.PromptMeta
import com.aiclassroom.feature.plugin.host.HostFacade
import com.aiclassroom.feature.plugin.host.PluginKvStorage
import com.aiclassroom.feature.plugin.js.JsEngine
import com.aiclassroom.feature.plugin.js.JsSandbox
import com.aiclassroom.feature.plugin.js.jsFunction
import com.aiclassroom.feature.plugin.js.jsObject
import com.aiclassroom.feature.plugin.js.jsPayload
import com.aiclassroom.feature.plugin.js.jsString
import com.aiclassroom.feature.plugin.ui.UiProviderState
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import java.io.Closeable

/** 插件装载失败（manifest 校验不过、入口函数不存在、logic.js 语法错误）。 */
class PluginValidationException(val errors: List<String>) :
    Exception("插件校验失败：" + errors.joinToString("；"))

/**
 * 一个已装载、可调用的插件实例：manifest + 独立 JS Scope + 权限 + 存储 + 时间源。
 *
 * 一个实例只服务一个插件，非线程安全：宿主应串行使用（prompt 钩子与事件分发都在单线程内串行）。
 */
class PluginRuntime internal constructor(
    val manifest: PluginManifest,
    private val engine: JsEngine,
    private val kv: PluginKvStorage,
    private val clock: PluginClock,
    private val permissions: Set<String>,
    private val logger: PluginLogger,
    // 包内文件原始字节：宿主渲染界面（ui.json、图片素材）时从这里取，不再回磁盘读。
    private val files: Map<String, ByteArray> = emptyMap(),
    // 用户当前生效的配置值（已按 manifest.settings 补齐 default），由宿主在装载时注入。
    private val settingsJson: String = "{}",
) : Closeable {

    /**
     * 同一个插件实例的 JS 引擎非线程安全（Rhino Scope 不可并发）。
     * prompt 钩子、事件分发、定时器回调来自不同线程，全部经这把锁串行化。
     */
    private val invocationLock = Any()

    /** 该插件在 before_send 阶段注册的钩子入口函数名，没有则 null。 */
    val promptHookEntry: String? get() = manifest.promptHookEntry()

    /** 界面取数函数名（ui_overlay.provider）；没有则走「插件主动 setState」的旧路径。 */
    val uiProviderEntry: String? get() = manifest.extensionsOf(PluginExtensionType.UI_OVERLAY)
        .firstNotNullOfOrNull { it.provider }

    /**
     * provider 最近一次成功返回的扁平 state 表（key -> 字符串值）。
     * 失败时**保留上一次的值**，因此界面不会因为一次脚本报错闪成空白。
     */
    private var providerState: Map<String, String> = emptyMap()

    fun hasFunction(name: String): Boolean = synchronized(invocationLock) { engine.hasFunction(name) }

    /** 读包内文本文件（ui.json 等）；不存在返回 null。 */
    fun textFile(path: String): String? = files[path]?.toString(Charsets.UTF_8)

    /** 包内二进制文件（图片素材）；不存在返回 null。 */
    fun binaryFile(path: String): ByteArray? = files[path]



    /**
     * 把插件脚本里的顶层 `state` 序列化成 JSON，供宿主解析 `state.xxx` 取值表达式。
     * 没有 state 时返回 null；脚本抛错会向上抛（由调用方兜住，界面照常渲染其它插件）。
     */
    fun stateJson(): String? = synchronized(invocationLock) {
        val raw = engine.evalValue(STATE_JSON_EXPRESSION)
        when (raw) {
            null, is Undefined -> null
            is CharSequence -> raw.toString()
            else -> Context.toString(raw)
        }
    }

    /**
     * 执行界面回调（onTap / onLongPress / onChange）：entry(ctx)，
     * ctx = {state, event, value, now(), storage.get/set, log}。
     *
     * event 是宿主事件名（tap / long_press / change），value 是控件值（开关为布尔、滑杆为数字），
     * state 直接引用插件脚本里的顶层对象，插件改了它，下一次界面刷新就能读到。
     */
    fun runUiEvent(entry: String, eventName: String, valueJson: String?) {
        synchronized(invocationLock) {
            engine.callFunction(entry) { cx, scope ->
                listOf(buildUiContext(cx, scope, eventName, valueJson))
            }
        }
    }

    /** 执行任意表达式，主要给宿主自检与测试用。 */
    fun evalValue(expression: String): Any? = synchronized(invocationLock) { engine.evalValue(expression) }

    /**
     * 界面取数：调 provider(ctx) 拿最新 state，ctx = {now(), state, settings, storage, log}。
     *
     * 三条硬约束：
     * 1. **超时独立且更短**：[PluginLimits.UI_PROVIDER_TIMEOUT_MILLIS]（3 秒），provider 在渲染路径上，
     *    卡一次就是整屏卡一帧；
     * 2. **异常隔离**：脚本报错 / 超时 / 返回值不是对象，一律**沿用上一次的值**并返回 ok=false，
     *    界面不会因为一次失败闪成空白；
     * 3. **扁平化**：返回值按 JS 语义转成字符串（数字给 "7"，布尔给 "true"），供 ui.json 的
     *    `textFrom: "state.hh"` 直接取用；嵌套对象 / 数组不是界面能画的东西，跳过并记一条日志。
     */
    fun provideUiState(timeoutMillis: Long = PluginLimits.UI_PROVIDER_TIMEOUT_MILLIS): UiProviderState {
        val entry = uiProviderEntry ?: return UiProviderState(null, false, "插件没有声明 provider")
        return synchronized(invocationLock) {
            try {
                val raw = engine.callFunction(entry, timeoutMillis) { cx, scope ->
                    listOf(buildProviderContext(cx, scope))
                }
                val parsed = flattenProviderValue(raw, entry)
                if (parsed == null) {
                    UiProviderState(providerState, false, "provider $entry 没有返回对象")
                } else {
                    providerState = parsed
                    UiProviderState(parsed, true, null)
                }
            } catch (t: Throwable) {
                UiProviderState(providerState, false, t.message ?: t.javaClass.simpleName)
            }
        }
    }

    /** 上一次成功的 provider 值（失败时宿主继续用它渲染）。 */
    fun lastProvidedState(): Map<String, String> = synchronized(invocationLock) { providerState }

    /** JS 对象 -> Map<String,String>；null / undefined / 非对象返回 null（调用方沿用旧值）。 */
    private fun flattenProviderValue(raw: Any?, entry: String): Map<String, String>? =
        when {
            raw == null || raw is Undefined -> null
            raw is Scriptable -> {
                val result = LinkedHashMap<String, String>()
                for (id in raw.ids) {
                    val key = Context.toString(id)
                    val value = ScriptableObject.getProperty(raw, key)
                    if (value == Scriptable.NOT_FOUND || value is Undefined) continue
                    if (value is Scriptable) {
                        logger(
                            "[plugin:" + manifest.id + "] provider $entry 的字段 '$key' 是对象/数组，" +
                                "界面只能取标量，已跳过",
                        )
                        continue
                    }
                    result[key] = Context.toString(value)
                }
                result
            }

            else -> null
        }

    /** provider 的 ctx：{now(), state, settings, storage, log}，没有 event/value —— 它不是事件回调。 */
    private fun buildProviderContext(cx: Context, scope: Scriptable): Scriptable {
        val state = scope.get("state", scope).let { if (it == Scriptable.NOT_FOUND) null else it }
        return jsObject(
            cx,
            scope,
            mapOf(
                "now" to nowFunction(),
                "state" to state,
                "storage" to storageObject(cx, scope),
                "log" to logFunction(),
                "settings" to settingsObject(cx, scope),
            ),
        )
    }

    /**
     * 执行 before_send 钩子：JS 侧 onBeforePrompt(ctx) 返回字符串即替换，返回 null/undefined 表示不改。
     * 脚本报错 / 超时都向上抛出，由 PromptHookPipeline 跳过该插件。
     */
    fun runBeforeSend(currentPrompt: String, meta: PromptMeta): String? {
        val entry = promptHookEntry ?: return null
        val raw = synchronized(invocationLock) {
            engine.callFunction(entry) { cx, scope -> listOf(buildHookContext(cx, scope, currentPrompt, meta)) }
        }
        return when {
            raw == null || raw is Undefined -> null
            raw is String -> raw
            raw is CharSequence -> raw.toString()
            else -> throw PluginScriptException("钩子 $entry 必须返回字符串或 null，实际返回：${Context.toString(raw)}")
        }
    }

    /**
     * 执行一个事件 / 定时器回调：entry(ctx)，ctx = {name, payload, payloadJson, now(), storage, log}。
     *
     * 脚本抛异常或超时都会向上抛出（由 PluginEventBus 记入 DispatchReport.skipped），
     * 因此这里不做 runCatching —— 隔离责任在分发器，一个插件坏掉不能悄悄变成静默失败。
     */
    fun runEvent(entry: String, eventName: String, payloadJson: String) {
        synchronized(invocationLock) {
            engine.callFunction(entry) { cx, scope ->
                listOf(buildEventContext(cx, scope, eventName, payloadJson))
            }
        }
    }

    override fun close() {
        engine.close()
    }

    /** ctx：{prompt, meta, now(), storage.get/set, log}。 */
    private fun buildHookContext(cx: Context, scope: Scriptable, prompt: String, meta: PromptMeta): Scriptable =
        jsObject(
            cx,
            scope,
            mapOf(
                "prompt" to prompt,
                "meta" to jsObject(
                    cx,
                    scope,
                    mapOf(
                        "timestampMillis" to meta.timestampMillis.toDouble(),
                        "classIndex" to meta.classIndex,
                        "userInput" to meta.userInput,
                    ),
                ),
                "now" to nowFunction(),
                "storage" to storageObject(cx, scope),
                "log" to logFunction(),
                "settings" to settingsObject(cx, scope),
            ),
        )

    /** ctx：{name, payload, payloadJson, now(), storage.get/set, log}。 */
    private fun buildEventContext(cx: Context, scope: Scriptable, eventName: String, payloadJson: String): Scriptable =
        jsObject(
            cx,
            scope,
            mapOf(
                "name" to eventName,
                "payload" to jsPayload(cx, scope, payloadJson),
                "payloadJson" to payloadJson,
                "now" to nowFunction(),
                "storage" to storageObject(cx, scope),
                "log" to logFunction(),
                "settings" to settingsObject(cx, scope),
            ),
        )

    /**
     * ctx：{state, event, value, now(), storage.get/set, log, settings, host}。
     * valueJson 为 null 表示无值。
     *
     * [host] 是脚本全局作用域里由 HostFacade 装好的那张能力表——UI 交互同样需要能调用宿主
     * （进入/退出全屏、播放声音、读数据）。权限校验在 HostFacade 内部完成，
     * 未在 manifest 声明的能力会返回 permission_denied，所以这里直接透传是安全的。
     */
    private fun buildUiContext(cx: Context, scope: Scriptable, eventName: String, valueJson: String?): Scriptable {
        val state = scope.get("state", scope).let { if (it == Scriptable.NOT_FOUND) null else it }
        val host = scope.get("host", scope).let { if (it == Scriptable.NOT_FOUND) null else it }
        return jsObject(
            cx,
            scope,
            mapOf(
                "state" to state,
                "event" to eventName,
                "value" to valueJson?.let { jsPayload(cx, scope, it) },
                "now" to nowFunction(),
                "storage" to storageObject(cx, scope),
                "log" to logFunction(),
                "settings" to settingsObject(cx, scope),
                "host" to host,
            ),
        )
    }

    /**
     * ctx.settings：只读对象。值在装载时定死（宿主在设置页保存后会重建 runtime 再装载），
     * 因此这里不需要每次调用回读宿主；插件改它也不会影响宿主存储。
     * 未配置且无 default 的键不会出现在对象里，脚本读到 undefined。
     */
    private fun settingsObject(cx: Context, scope: Scriptable): Any =
        jsPayload(cx, scope, settingsJson) ?: cx.newObject(scope)

    private fun nowFunction() = jsFunction("now") { _, _, _ -> clock.nowMillis().toDouble() }

    private fun logFunction(): Any = jsFunction("log") { _, _, args ->
        logger("[plugin:" + manifest.id + "] " + args.joinToString(" ") { Context.toString(it) })
        Undefined.instance
    }

    private fun storageObject(cx: Context, scope: Scriptable): Scriptable {
        val storagePermitted = PluginPermissions.STORAGE in permissions
        return jsObject(
            cx,
            scope,
            mapOf(
                "get" to jsFunction("get") { _, _, args ->
                    if (!storagePermitted) {
                        logger("[plugin:" + manifest.id + "] 未声明 storage 权限，ctx.storage.get 被忽略")
                        null
                    } else {
                        kv.get(jsString(args.getOrNull(0)) ?: "")
                    }
                },
                "set" to jsFunction("set") { _, _, args ->
                    if (!storagePermitted) {
                        logger("[plugin:" + manifest.id + "] 未声明 storage 权限，ctx.storage.set 被忽略")
                    } else {
                        kv.set(jsString(args.getOrNull(0)) ?: "", jsString(args.getOrNull(1)) ?: "")
                    }
                    Undefined.instance
                },
            ),
        )
    }

    companion object {
        /** 取 state 快照的表达式：state 未声明时返回 null，不抛错。 */
        private const val STATE_JSON_EXPRESSION =
            "(typeof state === 'undefined' || state === null) ? null : JSON.stringify(state)"

        /**
         * 校验并装载插件。校验不通过抛 [PluginValidationException]（带完整错误列表）；
         * logic.js 语法错误 / 入口函数缺失抛 [PluginScriptException]。
         */
        fun create(
            pkg: PluginPackage,
            host: PluginHostApi,
            stateStore: PluginStateStore,
            clock: PluginClock = PluginClock.SYSTEM,
            logger: PluginLogger = {},
            scriptTimeoutMillis: Long = JsSandbox.DEFAULT_TIMEOUT_MILLIS,
            hostCallTimeoutMillis: Long = HostFacade.DEFAULT_HOST_CALL_TIMEOUT_MILLIS,
            settingValues: Map<String, String> = emptyMap(),
        ): PluginRuntime {
            val validation = pkg.validate()
            val manifest = validation.manifest
            if (!validation.ok || manifest == null) throw PluginValidationException(validation.errors)
            // 用户已存的值按 manifest 补齐 default；只认声明过的 key。
            val resolved = PluginSettingsCodec.resolve(manifest.settings, settingValues)
            val settingsJson = PluginSettingsCodec.toJson(manifest.settings, resolved)

            val engine = JsEngine(JsSandbox(scriptTimeoutMillis))
            val kv = PluginKvStorage(stateStore, manifest.id)
            val granted = manifest.permissions.toSet()
            try {
                val facade = HostFacade(host, kv, manifest.id, granted, logger, hostCallTimeoutMillis, clock)
                engine.createScope { cx, scope -> facade.install(cx, scope) }
                val logic = pkg.logicText
                if (!logic.isNullOrBlank()) engine.evaluate(logic, PluginPackage.LOGIC_FILE)
                for (extension in manifest.extensions) {
                    val entry = extension.entry ?: continue
                    if (!engine.hasFunction(entry)) {
                        throw PluginScriptException(
                            extension.type.wire + " 的 entry 函数 '" + entry + "' 在 " + PluginPackage.LOGIC_FILE + " 中未定义",
                        )
                    }
                }
            } catch (t: Throwable) {
                engine.close()
                throw t
            }
            return PluginRuntime(manifest, engine, kv, clock, granted, logger, pkg.entries, settingsJson)
        }
    }
}
