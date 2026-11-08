package com.aiclassroom.feature.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostFacadeTest {

    private fun runtime(
        permissions: List<String>,
        logic: String = "",
        host: PluginHostApi = RecordingHostApi(),
        store: PluginStateStore = InMemoryStateStore(),
    ): PluginRuntime {
        val manifest = """
            {
              "id": "host_probe",
              "name": "宿主能力探测",
              "version": "1.0.0",
              "minApiVersion": 1,
              "permissions": ${permissions.joinToString(",", "[", "]") { "\"$it\"" }},
              "extensions": []
            }
        """.trimIndent()
        val pkg = PluginPackage.ofText(mapOf("manifest.json" to manifest, "logic.js" to logic))
        return PluginRuntime.create(pkg, host, store)
    }

    @Test
    fun `未声明权限时宿主能力调用被拒绝`() {
        val plugin = runtime(permissions = emptyList())
        val code = plugin.evalValue("host.generateImage({prompt: '一只猫'}).code") as String
        assertEquals("permission_denied", code)
    }

    @Test
    fun `声明权限后 host generateImage 走宿主实现`() {
        val host = RecordingHostApi()
        val plugin = runtime(permissions = listOf("image.generate"), host = host)
        val rendered = plugin.evalValue(
            """
            (function () {
              var r = host.generateImage({ prompt: '一只猫', width: 512, height: 512 });
              return [String(r.ok), r.value, r.path].join('|');
            })()
            """.trimIndent(),
        ) as String
        assertEquals("true|ok|/tmp/image.png", rendered)
        assertEquals(listOf("generateImage:一只猫:512:512"), host.calls)
    }

    @Test
    fun `宿主抛异常时脚本拿到 host_error 而不是崩溃`() {
        val failing = object : PluginHostApi by RecordingHostApi() {
            override suspend fun speak(text: String): PluginResult = throw IllegalStateException("TTS 引擎没电了")
        }
        val plugin = runtime(permissions = listOf("tts.speak"), host = failing)
        val rendered = plugin.evalValue("(function(){ var r = host.speak('你好'); return r.ok + '|' + r.code; })()") as String
        assertEquals("false|host_error", rendered)
    }

    @Test
    fun `ctx log 会打到宿主日志`() {
        val logs = mutableListOf<String>()
        val manifest = """
            {
              "id": "log_probe",
              "name": "日志探测",
              "version": "1.0.0",
              "minApiVersion": 1,
              "permissions": [],
              "extensions": [ { "type": "prompt_hook", "stage": "before_send", "config": "logic.js", "entry": "onBeforePrompt" } ]
            }
        """.trimIndent()
        val logic = "function onBeforePrompt(ctx) { ctx.log('hello from plugin'); return null; }"
        val plugin = PluginRuntime.create(
            PluginPackage.ofText(mapOf("manifest.json" to manifest, "logic.js" to logic)),
            RecordingHostApi(),
            InMemoryStateStore(),
            logger = { logs += it },
        )
        plugin.runBeforeSend("PROMPT", com.aiclassroom.feature.plugin.hook.PromptMeta(0L, 0, ""))
        assertTrue("日志应带上插件 id：$logs", logs.any { it.contains("[plugin:log_probe]") && it.contains("hello from plugin") })
    }

    @Test
    fun `requestUiRefresh 直达宿主`() {
        val host = RecordingHostApi()
        val plugin = runtime(permissions = emptyList(), host = host)
        plugin.evalValue("host.requestUiRefresh()")
        assertEquals(1, host.refreshCount)
    }
}
