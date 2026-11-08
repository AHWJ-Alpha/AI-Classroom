package com.aiclassroom.feature.plugin

import com.aiclassroom.feature.plugin.ui.FullscreenArbiter
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第三批能力：界面活状态（provider）、高频时钟（ui_ticker）、全屏插槽（FULLSCREEN）、
 * 图片类型的配置字段。四组都只做「能力」，不实现具体插件。
 */
class PluginLiveUiTest {

    private fun packageOf(
        extensions: String,
        logic: String,
        settings: String = "[]",
        permissions: String = "\"ui.fullscreen\", \"storage\", \"timer\"",
        extra: Map<String, String> = emptyMap(),
    ): PluginPackage {
        val entries = linkedMapOf(
            "manifest.json" to """
                {
                  "id": "clock_probe", "name": "时钟探针", "version": "1.0.0", "minApiVersion": 1,
                  "permissions": [$permissions],
                  "settings": $settings,
                  "extensions": $extensions
                }
            """.trimIndent(),
            "logic.js" to logic,
        )
        entries.putAll(extra)
        return PluginPackage.ofText(entries)
    }

    private val clockLogic = """
        var state = { hh: "--", mm: "--", ss: "--" };
        var explode = false;
        function pad(n) { return (n < 10 ? "0" : "") + n; }
        function provideState(ctx) {
          if (explode) { throw new Error("provider 炸了"); }
          var d = new Date(ctx.now());
          return { hh: pad(d.getHours()), mm: pad(d.getMinutes()), ss: pad(d.getSeconds()), hits: 1 };
        }
        function onTick(ctx) { state.ss = String(ctx.now()); }
    """.trimIndent()

    private fun runtimeFor(
        extensions: String,
        logic: String = clockLogic,
        permissions: String = "\"ui.fullscreen\", \"storage\", \"timer\"",
        clock: PluginClock = PluginClock { 1_700_000_000_000L },
    ): PluginRuntime {
        val pkg = packageOf(
            extensions,
            logic,
            permissions = permissions,
            // 每种插槽的界面描述都要真实存在，否则 manifest 校验会因为「config 引用的文件不存在」失败。
            extra = mapOf(
                "clock.json" to """{ "type": "text", "textFrom": "state.ss", "size": "title" }""",
                "u.json" to """{ "type": "text", "text": "x" }""",
            ),
        )
        val validation = pkg.validate()
        assertTrue(validation.errors.joinToString("；"), validation.ok)
        return PluginRuntime.create(pkg, RecordingHostApi(), InMemoryStateStore(), clock)
    }

    // ---------------------------------------------------------------- provider

    @Test
    fun `provider 返回的对象被扁平化成 state 表`() {
        val runtime = runtimeFor(
            """[{ "type": "ui_overlay", "slot": "FULLSCREEN", "config": "clock.json", "provider": "provideState" }]""",
        )
        try {
            assertEquals("provideState", runtime.uiProviderEntry)
            val provided = runtime.provideUiState()
            assertTrue(provided.error ?: "", provided.ok)
            val values = requireNotNull(provided.values)
            assertTrue(values.toString(), values.containsKey("hh"))
            assertTrue(values.toString(), values.containsKey("mm"))
            assertTrue(values.toString(), values.containsKey("ss"))
            assertEquals("1", values["hits"])
            // stateJson 是 ui.json 里 state.xxx 取值表达式的输入。
            assertTrue(requireNotNull(provided.stateJson()), provided.stateJson()!!.contains("\"hh\":"))
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `provider 抛异常时沿用上一次的值`() {
        val runtime = runtimeFor(
            """[{ "type": "ui_overlay", "slot": "FULLSCREEN", "config": "clock.json", "provider": "provideState" }]""",
        )
        try {
            val first = runtime.provideUiState()
            assertTrue(first.ok)
            val good = requireNotNull(first.values)

            runtime.evalValue("explode = true;")
            val failed = runtime.provideUiState()
            assertFalse(failed.ok)
            assertTrue(failed.error ?: "", failed.error!!.contains("provider 炸了"))
            // 关键点：失败不是「空界面」，是继续用上一次的值。
            assertEquals(good, failed.values)
            assertEquals(good, runtime.lastProvidedState())
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `provider 死循环会被超时掐断并沿用旧值`() {
        val runtime = runtimeFor(
            """[{ "type": "ui_overlay", "slot": "GLOBAL_OVERLAY", "config": "u.json", "provider": "provideState" }]""",
            logic = """
                var state = { ok: "第一次" };
                var hang = false;
                function provideState(ctx) {
                  if (hang) { while (true) { } }
                  return { ok: "第一次" };
                }
            """.trimIndent(),
        )
        try {
            val first = runtime.provideUiState()
            assertTrue(first.ok)
            runtime.evalValue("hang = true;")
            val startedAt = System.currentTimeMillis()
            val second = runtime.provideUiState(timeoutMillis = 200L)
            val elapsed = System.currentTimeMillis() - startedAt
            assertFalse("死循环必须被掐断", second.ok)
            assertEquals(requireNotNull(first.values), second.values)
            assertTrue("超时应该在 200ms 量级返回，实际 " + elapsed + "ms", elapsed < 3_000)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `没有 provider 的插件仍走 state 快照`() {
        val runtime = runtimeFor(
            """[{ "type": "ui_overlay", "slot": "GLOBAL_OVERLAY", "config": "u.json" }]""",
        )
        try {
            assertNull(runtime.uiProviderEntry)
            val snapshot = requireNotNull(runtime.stateJson())
            assertTrue(snapshot, snapshot.contains("\"hh\":\"--\""))
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `provider 只在声明里出现且必须真实存在`() {
        val missing = packageOf(
            """[{ "type": "ui_overlay", "slot": "GLOBAL_OVERLAY", "config": "u.json", "provider": "nope" }]""",
            clockLogic,
        ).validate()
        assertFalse(missing.ok)
        assertTrue(missing.errors.joinToString("；").contains("provider 函数 'nope'"))

        val wrongType = packageOf(
            """[{ "type": "ui_ticker", "intervalMs": 1000, "entry": "onTick", "provider": "provideState" }]""",
            clockLogic,
        ).validate()
        assertFalse(wrongType.ok)
        assertTrue(wrongType.errors.joinToString("；").contains("只对 ui_overlay 有效"))
    }

    // ---------------------------------------------------------------- ui_ticker

    @Test
    fun `ui_ticker 合法区间与默认值`() {
        val ok = packageOf("""[{ "type": "ui_ticker", "intervalMs": 1000, "entry": "onTick" }]""", clockLogic).validate()
        assertTrue(ok.errors.joinToString("；"), ok.ok)
        assertEquals(1000, ok.manifest?.uiTickerExtension?.intervalMillis)

        // intervalMs 省略时默认 1000ms。
        val defaulted = packageOf("""[{ "type": "ui_ticker", "entry": "onTick" }]""", clockLogic).validate()
        assertTrue(defaulted.errors.joinToString("；"), defaulted.ok)
        assertEquals(PluginLimits.DEFAULT_UI_TICKER_INTERVAL_MILLIS, defaulted.manifest?.uiTickerExtension?.intervalMillis)
    }

    @Test
    fun `ui_ticker 越界被拒绝`() {
        for (bad in listOf(0, 249, 60_001, 600_000)) {
            val result = packageOf("""[{ "type": "ui_ticker", "intervalMs": $bad, "entry": "onTick" }]""", clockLogic).validate()
            assertFalse("intervalMs=$bad 应该被拒绝", result.ok)
            assertTrue(result.errors.joinToString("；"), result.errors.joinToString("；").contains("intervalMs 越界"))
        }
        // 边界值是合法的。
        for (good in listOf(250, 60_000)) {
            val result = packageOf("""[{ "type": "ui_ticker", "intervalMs": $good, "entry": "onTick" }]""", clockLogic).validate()
            assertTrue("intervalMs=$good 应该合法：" + result.errors.joinToString("；"), result.ok)
        }
    }

    @Test
    fun `同一插件只允许一个 ui_ticker`() {
        val result = packageOf(
            """
            [
              { "type": "ui_ticker", "intervalMs": 1000, "entry": "onTick" },
              { "type": "ui_ticker", "intervalMs": 2000, "entry": "onTick" }
            ]
            """.trimIndent(),
            clockLogic,
        ).validate()
        assertFalse(result.ok)
        assertTrue(result.errors.joinToString("；").contains("最多 1 个 ui_ticker"))
    }

    @Test
    fun `ui_ticker 必须有 entry`() {
        val result = packageOf("""[{ "type": "ui_ticker", "intervalMs": 1000 }]""", clockLogic).validate()
        assertFalse(result.ok)
        assertTrue(result.errors.joinToString("；").contains("缺少 entry"))
    }

    // ---------------------------------------------------------------- FULLSCREEN

    @Test
    fun `FULLSCREEN 插槽需要 ui-fullscreen 权限`() {
        val denied = packageOf(
            """[{ "type": "ui_overlay", "slot": "FULLSCREEN", "config": "clock.json" }]""",
            clockLogic,
            permissions = "\"storage\"",
            extra = mapOf("clock.json" to """{ "type": "text", "text": "12:00" }"""),
        ).validate()
        assertFalse(denied.ok)
        assertTrue(denied.errors.joinToString("；").contains(PluginPermissions.UI_FULLSCREEN))

        val granted = packageOf(
            """[{ "type": "ui_overlay", "slot": "FULLSCREEN", "config": "clock.json" }]""",
            clockLogic,
            extra = mapOf("clock.json" to """{ "type": "text", "text": "12:00" }"""),
        ).validate()
        assertTrue(granted.errors.joinToString("；"), granted.ok)
        assertEquals("FULLSCREEN", granted.manifest?.fullscreenExtension?.slot)
    }

    @Test
    fun `FULLSCREEN 同一时刻只有一个插件占用 其余忽略并记日志`() {
        val logs = mutableListOf<String>()
        val arbiter = FullscreenArbiter { line -> logs += line }
        // 顺序 = 插件启用顺序（装载顺序）。
        arbiter.setDeclared(listOf("clock_a", "clock_b"))

        assertNull("没人要求时不该有占用", arbiter.resolve())

        arbiter.request("clock_b")
        assertEquals("clock_b", arbiter.resolve())

        arbiter.request("clock_a")
        // 两个都想独占：按启用顺序取第一个。
        assertEquals("clock_a", arbiter.resolve())
        assertTrue(logs.joinToString("；"), logs.any { it.contains("只允许一个") && it.contains("clock_b") })

        arbiter.release("clock_a")
        assertEquals("clock_b", arbiter.resolve())

        arbiter.release("clock_b")
        assertNull(arbiter.resolve())
    }

    @Test
    fun `插件被卸载后不再参与全屏仲裁`() {
        val arbiter = FullscreenArbiter()
        arbiter.setDeclared(listOf("clock_a", "clock_b"))
        arbiter.request("clock_a")
        assertEquals("clock_a", arbiter.resolve())
        // 卸载 clock_a：声明集合与它的意愿一起清掉。
        arbiter.setDeclared(listOf("clock_b"))
        assertNull(arbiter.resolve())
        assertFalse(arbiter.isRequested("clock_a"))
    }

    // ---------------------------------------------------------------- image settings

    @Test
    fun `image 类型的配置字段通过校验`() {
        val result = packageOf(
            "[]",
            clockLogic,
            settings = """[{ "key": "bgImage", "label": "背景照片", "type": "image" }]""",
            permissions = "\"storage.readImage\"",
        ).validate()
        assertTrue(result.errors.joinToString("；"), result.ok)
        val setting = requireNotNull(result.manifest).settings.single()
        assertEquals(PluginSettingType.IMAGE, setting.type)
        assertEquals("bgImage", setting.key)
        // image 字段没有 default 时不出现在生效值里（=用户还没选图）。
        assertTrue(PluginSettingsCodec.resolve(listOf(setting), emptyMap()).isEmpty())
    }

    @Test
    fun `image 字段的 default 必须是字符串`() {
        val result = packageOf(
            "[]",
            clockLogic,
            settings = """[{ "key": "bgImage", "label": "背景照片", "type": "image", "default": 3 }]""",
        ).validate()
        assertFalse(result.ok)
        assertTrue(result.errors.joinToString("；").contains("必须是字符串（type=image）"))
    }

    @Test
    fun `image 字段不接受 min max options`() {
        val result = packageOf(
            "[]",
            clockLogic,
            settings = """[{ "key": "bgImage", "label": "背景照片", "type": "image", "min": 1 }]""",
        ).validate()
        assertFalse(result.ok)
        assertTrue(result.errors.joinToString("；").contains("只对 type=number 有效"))
    }

    @Test
    fun `ui_ticker 只在可见且未被暂停时推进`() {
        val ticks = java.util.concurrent.atomic.AtomicInteger()
        val driver = com.aiclassroom.feature.plugin.ui.PluginUiDriver(
            onTick = { ticks.incrementAndGet() },
            dispatcher = kotlinx.coroutines.Dispatchers.Default,
        )
        val runtime = runtimeFor("""[{ "type": "ui_ticker", "intervalMs": 250, "entry": "onTick" }]""")
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        try {
            // 不可见：一起循环都不该起。
            driver.setVisiblePlugins(emptySet())
            driver.start(listOf(runtime), scope)
            assertEquals(0, driver.runningCount)
            Thread.sleep(400)

            // 可见：起一个循环。
            driver.setVisiblePlugins(setOf("clock_probe"))
            driver.start(listOf(runtime), scope)
            assertEquals(1, driver.runningCount)
            // 重复 start 不应该起第二条（单例）。
            driver.start(listOf(runtime), scope)
            assertEquals(1, driver.runningCount)
            Thread.sleep(700)
            val afterVisible = ticks.get()
            assertTrue("可见时应该推进，实际 " + afterVisible, afterVisible >= 1)

            // 暂停：循环还在，但这一轮不跑。
            driver.setPaused(true)
            Thread.sleep(300)
            val pausedAt = ticks.get()
            Thread.sleep(600)
            assertEquals("暂停期间不应推进", pausedAt, ticks.get())
            driver.setPaused(false)

            // 不可见：循环被停掉。
            driver.setVisiblePlugins(emptySet())
            driver.stopInvisible()
            assertEquals(0, driver.runningCount)
        } finally {
            driver.stopAll()
            scope.cancel()
            runtime.close()
        }
    }

    @Test
    fun `storage-readImage 与 ui-fullscreen 都在权限白名单里`() {
        assertTrue(PluginPermissions.STORAGE_READ_IMAGE in PluginPermissions.ALL)
        assertTrue(PluginPermissions.UI_FULLSCREEN in PluginPermissions.ALL)
    }

    @Test
    fun `JS 侧 host enterFullscreen 真的调到了宿主`() {
        val host = RecordingHostApi()
        val pkg = packageOf(
            """[{ "type": "ui_overlay", "slot": "GLOBAL_OVERLAY", "config": "u.json" }]""",
            """
            function go(ctx) {
              host.enterFullscreen();
              host.exitFullscreen();
              return host.pickImage({}).path;
            }
            """.trimIndent(),
            permissions = "\"ui.fullscreen\", \"storage.readImage\"",
            extra = mapOf("u.json" to """{ "type": "text", "text": "x" }"""),
        )
        val runtime = PluginRuntime.create(pkg, host, InMemoryStateStore())
        try {
            val path = runtime.evalValue("(function(){ host.enterFullscreen(); host.exitFullscreen(); return host.pickImage({}).path; })()")
            assertEquals("/tmp/pick.png", path?.toString())
            assertEquals(listOf("enter:clock_probe", "exit:clock_probe"), host.fullscreenCalls)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `没有 ui-fullscreen 权限时宿主拒绝且不抛异常`() {
        val host = RecordingHostApi()
        val pkg = packageOf(
            """[{ "type": "ui_overlay", "slot": "GLOBAL_OVERLAY", "config": "u.json" }]""",
            "function noop(ctx) { }",
            permissions = "\"storage\"",
            extra = mapOf("u.json" to """{ "type": "text", "text": "x" }"""),
        )
        val runtime = PluginRuntime.create(pkg, host, InMemoryStateStore())
        try {
            val result = runtime.evalValue("(function(){ var r = host.enterFullscreen(); return r.code; })()")
            assertEquals("permission_denied", result?.toString())
            assertTrue(host.fullscreenCalls.isEmpty())
        } finally {
            runtime.close()
        }
    }
}
