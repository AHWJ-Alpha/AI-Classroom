package com.aiclassroom.feature.plugin

import com.aiclassroom.feature.plugin.hook.PromptHookPipeline
import com.aiclassroom.feature.plugin.hook.PromptMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptHookPipelineTest {

    private val meta = PromptMeta(1_700_000_000_000L, 3, "老师你好")

    private fun runtime(
        id: String,
        logic: String,
        permissions: List<String> = listOf("storage"),
        store: PluginStateStore = InMemoryStateStore(),
        host: PluginHostApi = RecordingHostApi(),
        scriptTimeoutMillis: Long = 3_000,
    ): PluginRuntime {
        val manifest = """
            {
              "id": "$id",
              "name": "$id",
              "version": "1.0.0",
              "minApiVersion": 1,
              "permissions": ${permissions.joinToString(",", "[", "]") { "\"$it\"" }},
              "extensions": [
                { "type": "prompt_hook", "stage": "before_send", "config": "logic.js", "entry": "onBeforePrompt" }
              ]
            }
        """.trimIndent()
        val pkg = PluginPackage.ofText(mapOf("manifest.json" to manifest, "logic.js" to logic))
        return PluginRuntime.create(pkg, host, store, scriptTimeoutMillis = scriptTimeoutMillis)
    }

    @Test
    fun `按传入顺序串联多个插件`() {
        val first = runtime("first_plugin", "function onBeforePrompt(ctx) { return ctx.prompt + ' [A]'; }")
        val second = runtime("second_plugin", "function onBeforePrompt(ctx) { return ctx.prompt + ' [B]'; }")
        val result = PromptHookPipeline().apply(listOf(first, second), "PROMPT", meta)
        assertEquals("PROMPT [A] [B]", result.prompt)
        assertEquals(listOf("first_plugin", "second_plugin"), result.appliedPluginIds)
        assertTrue(result.skipped.isEmpty())
    }

    @Test
    fun `返回 null 的插件不改写`() {
        val plugin = runtime("noop_plugin", "function onBeforePrompt(ctx) { return null; }")
        val result = PromptHookPipeline().apply(listOf(plugin), "PROMPT", meta)
        assertEquals("PROMPT", result.prompt)
        assertTrue(result.appliedPluginIds.isEmpty())
        assertTrue(result.skipped.isEmpty())
    }

    @Test
    fun `返回 undefined 的插件不改写`() {
        val plugin = runtime("undef_plugin", "function onBeforePrompt(ctx) { }")
        val result = PromptHookPipeline().apply(listOf(plugin), "PROMPT", meta)
        assertEquals("PROMPT", result.prompt)
        assertTrue(result.appliedPluginIds.isEmpty())
    }

    @Test
    fun `中间插件抛异常只跳过它，前后插件照常生效`() {
        val good1 = runtime("good_one", "function onBeforePrompt(ctx) { return ctx.prompt + ' [A]'; }")
        val broken = runtime("broken_plugin", "function onBeforePrompt(ctx) { throw new Error('boom'); }")
        val good2 = runtime("good_two", "function onBeforePrompt(ctx) { return ctx.prompt + ' [C]'; }")
        val logs = mutableListOf<String>()
        val result = PromptHookPipeline(logger = { logs += it }).apply(listOf(good1, broken, good2), "PROMPT", meta)
        assertEquals("PROMPT [A] [C]", result.prompt)
        assertEquals(listOf("good_one", "good_two"), result.appliedPluginIds)
        assertEquals(1, result.skipped.size)
        assertEquals("broken_plugin", result.skipped[0].pluginId)
        assertTrue("异常原因应被记录：${result.skipped[0].reason}", result.skipped[0].reason.contains("boom"))
        assertTrue("日志里要留下跳过记录：" + logs, logs.any { it.contains("broken_plugin") })
    }

    @Test(timeout = 30_000)
    fun `插件钩子死循环时超时跳过且不影响其他插件`() {
        val spinning = runtime(
            "spin_plugin",
            "function onBeforePrompt(ctx) { while (true) {} }",
            scriptTimeoutMillis = 400,
        )
        val normal = runtime("normal_plugin", "function onBeforePrompt(ctx) { return ctx.prompt + ' [OK]'; }")
        val result = PromptHookPipeline().apply(listOf(spinning, normal), "PROMPT", meta)
        assertEquals("PROMPT [OK]", result.prompt)
        assertEquals(listOf("spin_plugin"), result.skipped.map { it.pluginId })
        assertTrue("超时原因应被记录：${result.skipped[0].reason}", result.skipped[0].reason.contains("已中断"))
    }

    @Test
    fun `钩子返回非字符串时报错并被跳过`() {
        val bad = runtime("bad_return", "function onBeforePrompt(ctx) { return 42; }")
        val result = PromptHookPipeline().apply(listOf(bad), "PROMPT", meta)
        assertEquals("PROMPT", result.prompt)
        assertEquals(1, result.skipped.size)
        assertTrue(result.skipped[0].reason.contains("必须返回字符串"))
    }

    @Test
    fun `没有 prompt_hook 扩展点的插件不参与串联`() {
        val manifest = """
            {
              "id": "ui_only",
              "name": "只有界面",
              "version": "1.0.0",
              "minApiVersion": 1,
              "permissions": [],
              "extensions": [ { "type": "ui_overlay", "slot": "chat_bottom", "config": "ui.json" } ]
            }
        """.trimIndent()
        val pkg = PluginPackage.ofText(mapOf("manifest.json" to manifest, "ui.json" to "{\"nodes\":[]}", "logic.js" to ""))
        val plugin = PluginRuntime.create(pkg, RecordingHostApi(), InMemoryStateStore())
        assertEquals(null, plugin.promptHookEntry)
        val result = PromptHookPipeline().apply(listOf(plugin), "PROMPT", meta)
        assertEquals("PROMPT", result.prompt)
        assertEquals(0, result.skipped.size)
    }

    @Test
    fun `卸载后的插件不能再调用`() {
        val plugin = runtime("closable_plugin", "function onBeforePrompt(ctx) { return ctx.prompt; }")
        plugin.close()
        val failure = try {
            plugin.runBeforeSend("PROMPT", meta)
            null
        } catch (e: PluginScriptException) {
            e
        }
        assertTrue("关闭后应抛 PluginScriptException", failure != null)
    }
}
