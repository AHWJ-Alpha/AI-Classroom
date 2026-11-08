package com.aiclassroom.feature.plugin.js

import com.aiclassroom.feature.plugin.PluginScriptException
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/**
 * 一个插件私有的 JS 引擎实例：独立 Scope，全局变量不与别的插件共享。
 * 所有入口（建 Scope、eval、调用函数）都经过 [JsSandbox] 的超时保护。
 */
class JsEngine(private val sandbox: JsSandbox = JsSandbox()) {

    private var scopeRef: ScriptableObject? = null

    private val scope: ScriptableObject
        get() = scopeRef ?: throw PluginScriptException("JS Scope 尚未初始化")

    /** 建立安全 Scope 并注入宿主对象；只能调用一次。 */
    fun createScope(configure: (Context, ScriptableObject) -> Unit) {
        check(scopeRef == null) { "JS Scope 只能初始化一次" }
        sandbox.call { cx ->
            val created = cx.initSafeStandardObjects()
            scopeRef = created
            configure(cx, created)
            Unit
        }
    }

    fun evaluate(source: String, sourceName: String) {
        sandbox.call { cx -> cx.evaluateString(scope, source, sourceName, 1, null) }
    }

    /** 执行一个表达式并取回值，主要给宿主做自检 / 测试用。 */
    fun evalValue(expression: String): Any? =
        sandbox.call { cx -> cx.evaluateString(scope, expression, "expr", 1, null) }

    fun hasFunction(name: String): Boolean {
        val target = scope
        return sandbox.call {
            val value = target.get(name, target)
            value != Scriptable.NOT_FOUND && value is Function
        }
    }

    /** [timeoutOverrideMillis] 见 [JsSandbox.call]：界面取数 provider 用它压到更短的预算。 */
    fun callFunction(
        name: String,
        timeoutOverrideMillis: Long? = null,
        argBuilder: (Context, Scriptable) -> List<Any?>,
    ): Any? {
        val target = scope
        return sandbox.call(timeoutOverrideMillis) { cx ->
            val value = target.get(name, target)
            if (value == Scriptable.NOT_FOUND || value !is Function) {
                throw PluginScriptException("${PluginScope.LOGIC_FILE} 里找不到可调用的函数：$name")
            }
            value.call(cx, target, target, argBuilder(cx, target).toTypedArray())
        }
    }

    fun putGlobal(name: String, value: Any?) {
        val target = scope
        sandbox.call { cx ->
            ScriptableObject.putProperty(target, name, if (value == null) null else Context.javaToJS(value, target))
            Unit
        }
    }

    fun close() {
        scopeRef = null
    }
}

/** 仅为错误信息里引用 logic.js 文件名，避免 js 包反向依赖 PluginPackage 的常量。 */
internal object PluginScope {
    const val LOGIC_FILE = "logic.js"
}
