package com.aiclassroom.feature.plugin.js

import com.aiclassroom.feature.plugin.PluginScriptException
import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextAction
import org.mozilla.javascript.ContextFactory

/** 全禁 ClassShutter：任何 Java 类对脚本都不可见，反射 / 文件 / 网络入口一律封死。 */
private object DenyAllClassShutter : ClassShutter {
    override fun visibleToScripts(fullClassName: String): Boolean = false
}

/** 指令计数到点后抛出，用来中止死循环；由 [JsSandbox] 转成 [PluginScriptException]。 */
internal class ScriptTimeoutSignal : RuntimeException("插件脚本执行超时")

/** Context 的 protected 构造器只能被子类调用，所以单独开一个薄子类。 */
private class SandboxContext(factory: ContextFactory) : Context(factory)

/**
 * Rhino 沙箱。
 *
 * - Scope 用 initSafeStandardObjects 初始化：没有 Packages / java / JavaAdapter 等 Java 通道；
 * - ClassShutter 全禁，即使某个 Java 对象被塞进脚本也无法被包装 / 反射；
 * - 解释模式（optimizationLevel = -1）+ 指令计数观察者：脚本连续执行超过 [timeoutMillis] 立即中止；
 * - 非线程安全：一个实例只服务一个插件，调用方必须串行使用。
 */
class JsSandbox(val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS) {

    private var deadlineNanos = 0L

    private val factory: ContextFactory = object : ContextFactory() {
        override fun makeContext(): Context {
            val cx = SandboxContext(this)
            // 指令计数只在解释执行下生效，因此固定走解释模式（也顺便不生成任何字节码）。
            cx.setOptimizationLevel(-1)
            cx.setLanguageVersion(Context.VERSION_ES6)
            cx.setClassShutter(DenyAllClassShutter)
            cx.setInstructionObserverThreshold(INSTRUCTION_OBSERVER_THRESHOLD)
            cx.setGenerateObserverCount(true)
            return cx
        }

        override fun observeInstructionCount(cx: Context, instructionCount: Int) {
            checkDeadline()
        }
    }

    /** 在受保护的 Context 里执行 [block]；超时会被翻译成 [PluginScriptException]（timeout = true）。 */
    fun <T> call(block: (Context) -> T): T {
        val previous = deadlineNanos
        deadlineNanos = System.nanoTime() + timeoutMillis * NANOS_PER_MILLI
        try {
            return factory.call(object : ContextAction<T> {
                override fun run(cx: Context): T = block(cx)
            })
        } catch (t: Throwable) {
            throw translate(t)
        } finally {
            deadlineNanos = previous
        }
    }

    private fun checkDeadline() {
        val deadline = deadlineNanos
        if (deadline != 0L && System.nanoTime() > deadline) throw ScriptTimeoutSignal()
    }

    private fun translate(t: Throwable): Throwable {
        var cause: Throwable? = t
        while (cause != null) {
            if (cause is ScriptTimeoutSignal) {
                return PluginScriptException("插件脚本执行超过 ${timeoutMillis}ms，已中断", t, timeout = true)
            }
            cause = cause.cause
        }
        return t
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 3_000L

        /** 每执行这么多条指令检查一次是否超时（越小越灵敏，CPU 开销越大）。 */
        internal const val INSTRUCTION_OBSERVER_THRESHOLD = 10_000

        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
