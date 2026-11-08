package com.aiclassroom.feature.plugin

import com.aiclassroom.feature.plugin.event.PluginEvent
import com.aiclassroom.feature.plugin.event.PluginEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 任务 2 的验收：timer / event 的校验规则与事件分发的隔离性。 */
class TriggerTest {

    private fun manifest(
        id: String = "probe",
        permissions: List<String> = listOf("storage"),
        extensions: String,
    ): String = """
        {
          "id": "$id",
          "name": "触发器探测",
          "version": "1.0.0",
          "minApiVersion": 1,
          "permissions": ${permissions.joinToString(",", "[", "]") { "\"" + it + "\"" }},
          "extensions": $extensions
        }
    """.trimIndent()

    private fun validation(manifestText: String, logic: String): ManifestValidation =
        ManifestValidator.validate(manifestText, logic, setOf("manifest.json", "logic.js"))

    private fun errorsOf(manifestText: String, logic: String): String =
        validation(manifestText, logic).errors.joinToString(" | ")

    private fun runtime(
        id: String = "probe",
        permissions: List<String> = listOf("storage"),
        logic: String,
        extensions: String,
    ): PluginRuntime = PluginRuntime.create(
        PluginPackage.ofText(mapOf("manifest.json" to manifest(id, permissions, extensions), "logic.js" to logic)),
        RecordingHostApi(),
        InMemoryStateStore(),
    )

    // ---------- manifest 校验 ----------

    @Test
    fun `timer 间隔小于 60 秒被拒绝`() {
        val errors = errorsOf(
            manifest(permissions = listOf("timer"), extensions = """[{"type":"timer","intervalSeconds":30,"entry":"onTimer"}]"""),
            "function onTimer(ctx) {}",
        )
        assertTrue(errors, errors.contains("intervalSeconds 越界"))
    }

    @Test
    fun `timer 间隔超过 86400 秒被拒绝`() {
        val errors = errorsOf(
            manifest(permissions = listOf("timer"), extensions = """[{"type":"timer","intervalSeconds":90000,"entry":"onTimer"}]"""),
            "function onTimer(ctx) {}",
        )
        assertTrue(errors, errors.contains("intervalSeconds 越界"))
    }

    @Test
    fun `timer 间隔非整数被拒绝`() {
        val errors = errorsOf(
            manifest(permissions = listOf("timer"), extensions = """[{"type":"timer","intervalSeconds":90.5,"entry":"onTimer"}]"""),
            "function onTimer(ctx) {}",
        )
        assertTrue(errors, errors.contains("intervalSeconds 必须是整数"))
    }

    @Test
    fun `合法 timer 通过校验并带上 intervalSeconds`() {
        val result = validation(
            manifest(permissions = listOf("timer"), extensions = """[{"type":"timer","intervalSeconds":600,"entry":"onTimer"}]"""),
            "function onTimer(ctx) {}",
        )
        assertTrue(result.errors.joinToString(" | "), result.ok)
        assertEquals(600, result.manifest?.timerExtension?.intervalSeconds)
    }

    @Test
    fun `声明 timer 扩展点必须同时申请 timer 权限`() {
        val errors = errorsOf(
            manifest(permissions = listOf("storage"), extensions = """[{"type":"timer","intervalSeconds":600,"entry":"onTimer"}]"""),
            "function onTimer(ctx) {}",
        )
        assertTrue(errors, errors.contains("必须同时申请 timer 权限"))
    }

    @Test
    fun `同一插件两个 timer 被拒绝`() {
        val errors = errorsOf(
            manifest(
                permissions = listOf("timer"),
                extensions = """[
                    {"type":"timer","intervalSeconds":600,"entry":"onTimer"},
                    {"type":"timer","intervalSeconds":900,"entry":"onTimer2"}
                ]""",
            ),
            "function onTimer(ctx) {} function onTimer2(ctx) {}",
        )
        assertTrue(errors, errors.contains("最多 1 个 timer"))
    }

    @Test
    fun `未知事件被拒绝`() {
        val errors = errorsOf(
            manifest(extensions = """[{"type":"event","event":"user_login","entry":"onLogin"}]"""),
            "function onLogin(ctx) {}",
        )
        assertTrue(errors, errors.contains("未知事件"))
    }

    @Test
    fun `六个白名单事件都能通过校验`() {
        val names = listOf(
            "message_sent",
            "assistant_replied",
            "app_started",
            "class_switched",
            "exam_submitted",
            "exam_started",
        )
        val extensions = names.mapIndexed { index, name ->
            """{"type":"event","event":"$name","entry":"onEvent$index"}"""
        }.joinToString(",", "[", "]")
        val logic = names.indices.joinToString(" ") { "function onEvent$it(ctx) {}" }
        val result = validation(manifest(extensions = extensions), logic)
        assertTrue(result.errors.joinToString(" | "), result.ok)
        assertEquals(6, result.manifest?.extensionsOf(PluginExtensionType.EVENT)?.size)
    }

    @Test
    fun `同一插件最多 8 个 event`() {
        val extensions = (0 until 9).joinToString(",", "[", "]") { index ->
            """{"type":"event","event":"app_started","entry":"onEvent$index"}"""
        }
        val logic = (0 until 9).joinToString(" ") { "function onEvent$it(ctx) {}" }
        val errors = errorsOf(manifest(extensions = extensions), logic)
        assertTrue(errors, errors.contains("最多 8 个 event"))
    }

    @Test
    fun `event 的 entry 必须在 logic js 里存在`() {
        val errors = errorsOf(
            manifest(extensions = """[{"type":"event","event":"app_started","entry":"onMissing"}]"""),
            "function onSomethingElse(ctx) {}",
        )
        assertTrue(errors, errors.contains("在 logic.js 中不存在"))
    }

    // ---------- 事件分发 ----------

    @Test
    fun `一个插件抛异常不影响另一个插件收到事件`() {
        val broken = runtime(
            id = "broken",
            logic = "function onMessageSent(ctx) { throw new Error('boom'); }",
            extensions = """[{"type":"event","event":"message_sent","entry":"onMessageSent"}]""",
        )
        val good = runtime(
            id = "good",
            logic = """
                function onMessageSent(ctx) {
                  ctx.storage.set('last', ctx.name + '|' + ctx.payload.userText);
                }
            """.trimIndent(),
            extensions = """[{"type":"event","event":"message_sent","entry":"onMessageSent"}]""",
        )
        val logs = mutableListOf<String>()
        val bus = PluginEventBus({ line -> logs += line })
        val report = bus.dispatch(
            listOf(broken, good),
            PluginEvent("message_sent", mapOf("userText" to "你好")),
        )
        assertEquals(listOf("good"), report.delivered)
        assertEquals(listOf("broken"), report.skipped.map { it.pluginId })
        assertEquals("message_sent|你好", good.evalValue("host.storageGet('last')") as String)
        assertTrue("坏插件必须留下日志：$logs", logs.any { it.contains("broken") && it.contains("已跳过") })
    }

    @Test
    fun `没有插件声明该事件时分发是空的且不调任何脚本`() {
        val plugin = runtime(
            id = "idle",
            logic = "function onAppStarted(ctx) { ctx.storage.set('hit', 'yes'); }",
            extensions = """[{"type":"event","event":"app_started","entry":"onAppStarted"}]""",
        )
        val report = PluginEventBus({}).dispatch(listOf(plugin), PluginEvent("message_sent"))
        assertTrue(report.isEmpty)
        assertEquals(null, plugin.evalValue("host.storageGet('hit')"))
    }

    @Test
    fun `空插件列表分发零开销`() {
        val report = PluginEventBus({}).dispatch(emptyList(), PluginEvent("app_started"))
        assertTrue(report.isEmpty)
    }

    // ---------- 定时器 ----------

    @Test
    fun `定时器按间隔触发且 stopTimers 之后不再触发`() = runBlocking {
        // 单测把 60 秒压成 20 毫秒：delayMillisProvider 只影响等待时长，不影响校验。
        val plugin = runtime(
            id = "ticker",
            permissions = listOf("storage", "timer"),
            logic = """
                function onTimer(ctx) {
                  var n = parseInt(ctx.storage.get('ticks') || '0', 10) + 1;
                  ctx.storage.set('ticks', String(n));
                }
            """.trimIndent(),
            extensions = """[{"type":"timer","intervalSeconds":60,"entry":"onTimer"}]""",
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val bus = PluginEventBus({}, delayMillisProvider = { 20L })
        bus.startTimers(listOf(plugin), scope)
        assertEquals(1, bus.timerCount)
        delay(150)
        val ticks = (plugin.evalValue("host.storageGet('ticks')") as String).toInt()
        assertTrue("应至少触发 2 次，实际 $ticks", ticks >= 2)
        bus.stopTimers()
        assertEquals(0, bus.timerCount)
        val frozen = (plugin.evalValue("host.storageGet('ticks')") as String).toInt()
        delay(120)
        assertEquals(frozen, (plugin.evalValue("host.storageGet('ticks')") as String).toInt())
        scope.cancel()
    }

    @Test
    fun `定时器回调抛异常只影响自己那一轮`() = runBlocking {
        val plugin = runtime(
            id = "flaky",
            permissions = listOf("storage", "timer"),
            logic = """
                function onTimer(ctx) {
                  var n = parseInt(ctx.storage.get('runs') || '0', 10) + 1;
                  ctx.storage.set('runs', String(n));
                  if (n === 1) throw new Error('first run fails');
                }
            """.trimIndent(),
            extensions = """[{"type":"timer","intervalSeconds":60,"entry":"onTimer"}]""",
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val bus = PluginEventBus({}, delayMillisProvider = { 20L })
        bus.startTimers(listOf(plugin), scope)
        delay(180)
        bus.stopTimers()
        val runs = (plugin.evalValue("host.storageGet('runs')") as String).toInt()
        assertTrue("首轮异常后应继续跑，实际 $runs", runs >= 2)
        scope.cancel()
    }
}
