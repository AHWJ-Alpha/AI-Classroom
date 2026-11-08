package com.aiclassroom.feature.plugin

import com.aiclassroom.feature.plugin.js.JsEngine
import com.aiclassroom.feature.plugin.js.JsSandbox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsSandboxTest {

    private fun bareEngine(timeoutMillis: Long = JsSandbox.DEFAULT_TIMEOUT_MILLIS): JsEngine {
        val engine = JsEngine(JsSandbox(timeoutMillis))
        engine.createScope { _, _ -> }
        return engine
    }

    private fun engineWith(logic: String, timeoutMillis: Long = JsSandbox.DEFAULT_TIMEOUT_MILLIS): JsEngine =
        bareEngine(timeoutMillis).also { it.evaluate(logic, "logic.js") }

    @Test
    fun `JS 沙箱里没有任何 Java 通道`() {
        val engine = bareEngine()
        val probes = engine.evalValue(
            """
            (function () {
              var out = [];
              out.push('java=' + typeof java);
              out.push('Packages=' + typeof Packages);
              out.push('JavaAdapter=' + typeof JavaAdapter);
              out.push('getClass=' + typeof this.getClass);
              try { new java.io.File('/'); out.push('File=REACHABLE'); } catch (e) { out.push('File=blocked'); }
              try { java.lang.Runtime.getRuntime().exec('cmd'); out.push('Runtime=REACHABLE'); } catch (e) { out.push('Runtime=blocked'); }
              try { Packages.java.lang.Class.forName('java.lang.Class'); out.push('Reflect=REACHABLE'); } catch (e) { out.push('Reflect=blocked'); }
              return out.join('|');
            })()
            """.trimIndent(),
        ) as String
        println("[sandbox] $probes")
        assertTrue(probes, probes.contains("java=undefined"))
        assertTrue(probes, probes.contains("Packages=undefined"))
        assertTrue(probes, probes.contains("JavaAdapter=undefined"))
        assertTrue(probes, probes.contains("getClass=undefined"))
        assertTrue(probes, probes.contains("File=blocked"))
        assertTrue(probes, probes.contains("Runtime=blocked"))
        assertTrue(probes, probes.contains("Reflect=blocked"))
        assertTrue(probes, !probes.contains("REACHABLE"))
    }

    @Test
    fun `宿主也不能把 Java 对象塞进脚本（ClassShutter）`() {
        val engine = bareEngine()
        var exposed = false
        var callResult = "not-called"
        try {
            engine.putGlobal("secret", JavaSecret())
            exposed = true
            callResult = engine.evalValue(
                "(function () { try { return 'method=' + secret.reveal(); } catch (e) { return 'blocked:' + e; } })()",
            ) as String
        } catch (t: Throwable) {
            callResult = "blocked:" + t.message
        }
        println("[sandbox] putGlobal 成功=$exposed，脚本结果=$callResult")
        assertTrue(callResult, callResult.startsWith("blocked"))
        assertTrue(callResult, !callResult.contains("PWNED"))
    }

    @Test(timeout = 20_000)
    fun `顶层死循环在 3 秒内被中断`() {
        val engine = bareEngine(3_000)
        val startedAt = System.nanoTime()
        val failure = try {
            engine.evaluate("while (true) {}", "loop.js")
            null
        } catch (e: PluginScriptException) {
            e
        }
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
        println("[sandbox] while(true) 中止耗时=${elapsedMillis}ms，异常=${failure?.message}")
        assertNotNull("死循环必须被中断", failure)
        assertTrue("必须标记为 timeout", failure!!.timeout)
        assertTrue("应在 3 秒内中断，实际 ${elapsedMillis}ms", elapsedMillis <= 3_500)
    }

    @Test(timeout = 20_000)
    fun `函数内的死循环同样会超时`() {
        val engine = engineWith("function spin() { while (true) {} }", timeoutMillis = 3_000)
        val startedAt = System.nanoTime()
        val failure = try {
            engine.callFunction("spin") { _, _ -> emptyList() }
            null
        } catch (e: PluginScriptException) {
            e
        }
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
        println("[sandbox] spin() 中止耗时=${elapsedMillis}ms，异常=${failure?.message}")
        assertNotNull("函数内死循环必须被中断", failure)
        assertTrue("必须标记为 timeout", failure!!.timeout)
        assertTrue("应在 3 秒内中断，实际 ${elapsedMillis}ms", elapsedMillis <= 3_500)
    }

    @Test
    fun `不同插件的 Scope 互不共享全局变量`() {
        val first = engineWith("var shared = 'A';")
        val second = engineWith("var shared = 'B';")
        assertEquals("A", first.evalValue("shared") as String)
        assertEquals("B", second.evalValue("shared") as String)
        val third = bareEngine()
        assertEquals("undefined", third.evalValue("typeof shared") as String)
    }
}

/** 用来验证 ClassShutter 的宿主 Java 对象。 */
class JavaSecret {
    fun reveal(): String = "PWNED"
}
