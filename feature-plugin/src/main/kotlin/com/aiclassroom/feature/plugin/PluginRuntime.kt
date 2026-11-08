package com.aiclassroom.feature.plugin

import com.aiclassroom.feature.plugin.hook.PromptMeta
import com.aiclassroom.feature.plugin.host.HostFacade
import com.aiclassroom.feature.plugin.host.PluginKvStorage
import com.aiclassroom.feature.plugin.js.JsEngine
import com.aiclassroom.feature.plugin.js.JsSandbox
import com.aiclassroom.feature.plugin.js.jsFunction
import com.aiclassroom.feature.plugin.js.jsObject
import com.aiclassroom.feature.plugin.js.jsString
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import java.io.Closeable

/** 插件装载失败（manifest 校验不过、入口函数不存在、logic.js 语法错误）。 */
class PluginValidationException(val errors: List<String>) :
    Exception("插件校验失败：" + errors.joinToString("；"))

/**
 * 一个已装载、可调用的插件实例：manifest + 独立 JS Scope + 权限 + 存储 + 时间源。
 *
 * 一个实例只服务一个插件，非线程安全：宿主应串行使用（prompt 钩子本来就是串行的）。
 */
class PluginRuntime internal constructor(
    val manifest: PluginManifest,
    private val engine: JsEngine,
    private val kv: PluginKvStorage,
    private val clock: PluginClock,
    private val permissions: Set<String>,
    private val logger: PluginLogger,
) : Closeable {

    /** 该插件在 before_send 阶段注册的钩子入口函数名，没有则 null。 */
    val promptHookEntry: String? get() = manifest.promptHookEntry()

    fun hasFunction(name: String): Boolean = engine.hasFunction(name)

    /** 执行任意表达式，主要给宿主自检与测试用。 */
    fun evalValue(expression: String): Any? = engine.evalValue(expression)

    /**
     * 执行 before_send 钩子：JS 侧 onBeforePrompt(ctx) 返回字符串即替换，返回 null/undefined 表示不改。
     * 脚本报错 / 超时都向上抛出，由 PromptHookPipeline 跳过该插件。
     */
    fun runBeforeSend(currentPrompt: String, meta: PromptMeta): String? {
        val entry = promptHookEntry ?: return null
        val raw = engine.callFunction(entry) { cx, scope -> listOf(buildHookContext(cx, scope, currentPrompt, meta)) }
        return when {
            raw == null || raw is Undefined -> null
            raw is String -> raw
            raw is CharSequence -> raw.toString()
            else -> throw PluginScriptException("钩子 $entry 必须返回字符串或 null，实际返回：${Context.toString(raw)}")
        }
    }

    override fun close() {
        engine.close()
    }

    /** ctx：{prompt, meta, now(), storage.get/set, log}。 */
    private fun buildHookContext(cx: Context, scope: Scriptable, prompt: String, meta: PromptMeta): Scriptable {
        val storagePermitted = PluginPermissions.STORAGE in permissions
        return jsObject(
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
                "now" to jsFunction("now") { _, _, _ -> clock.nowMillis().toDouble() },
                "storage" to jsObject(
                    cx,
                    scope,
                    mapOf(
                        "get" to jsFunction("get") { _, _, args ->
                            if (!storagePermitted) {
                                logger("[plugin:${manifest.id}] 未声明 storage 权限，ctx.storage.get 被忽略")
                                null
                            } else {
                                kv.get(jsString(args.getOrNull(0)) ?: "")
                            }
                        },
                        "set" to jsFunction("set") { _, _, args ->
                            if (!storagePermitted) {
                                logger("[plugin:${manifest.id}] 未声明 storage 权限，ctx.storage.set 被忽略")
                            } else {
                                kv.set(jsString(args.getOrNull(0)) ?: "", jsString(args.getOrNull(1)) ?: "")
                            }
                            Undefined.instance
                        },
                    ),
                ),
                "log" to jsFunction("log") { _, _, args ->
                    logger("[plugin:${manifest.id}] " + args.joinToString(" ") { Context.toString(it) })
                    Undefined.instance
                },
            ),
        )
    }

    companion object {
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
        ): PluginRuntime {
            val validation = pkg.validate()
            val manifest = validation.manifest
            if (!validation.ok || manifest == null) throw PluginValidationException(validation.errors)

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
                            "${extension.type.wire} 的 entry 函数 '$entry' 在 ${PluginPackage.LOGIC_FILE} 中未定义",
                        )
                    }
                }
            } catch (t: Throwable) {
                engine.close()
                throw t
            }
            return PluginRuntime(manifest, engine, kv, clock, granted, logger)
        }
    }
}
