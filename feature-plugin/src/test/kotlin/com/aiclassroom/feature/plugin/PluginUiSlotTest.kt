package com.aiclassroom.feature.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第一批 UI 插槽：manifest 白名单扩展 + 运行时给宿主读 ui.json / state / 派发界面事件。 */
class PluginUiSlotTest {

    private val logic = """
        var state = { line: "喵～", showBubble: false, taps: 0 };
        function onPetTap(ctx) { state.taps = state.taps + 1; state.showBubble = !state.showBubble; }
        function onInsertExam(ctx) { return null; }
        function decorateMessage(message) { return message; }
    """.trimIndent()

    private fun validate(extensions: String, extra: Map<String, String> = emptyMap()): ManifestValidation {
        val entries = linkedMapOf(
            "manifest.json" to """
                {
                  "id": "pet_cat", "name": "宠物猫", "version": "1.0.0", "minApiVersion": 1,
                  "permissions": ["storage"],
                  "extensions": $extensions
                }
            """.trimIndent(),
            "logic.js" to logic,
        )
        entries.putAll(extra)
        return PluginPackage.ofText(entries).validate()
    }

    @Test
    fun `三个新插槽都通过校验`() {
        val result = validate(
            """
            [
              { "type": "ui_overlay", "slot": "GLOBAL_OVERLAY", "config": "pet.json" },
              { "type": "message_decorator", "slot": "MESSAGE_BODY_TAIL", "config": "badge.json" },
              { "type": "bar_button", "slot": "TOP_BAR_ACTIONS", "label": "考试", "config": "btn.json" }
            ]
            """.trimIndent(),
            mapOf(
                "pet.json" to """{ "type": "text", "text": "喵" }""",
                "badge.json" to """{ "type": "text", "text": "1 字" }""",
                "btn.json" to """{ "type": "button", "label": "考试", "onTap": "onInsertExam" }""",
            ),
        )
        assertTrue(result.errors.joinToString("；"), result.ok)
        val manifest = requireNotNull(result.manifest)
        assertEquals(listOf("GLOBAL_OVERLAY"), manifest.extensionsInSlot("GLOBAL_OVERLAY").map { it.slot })
        assertEquals("badge.json", manifest.extensionsOf(PluginExtensionType.MESSAGE_DECORATOR).single().config)
    }

    @Test
    fun `旧 slot 与旧写法继续可用`() {
        val result = validate(
            """
            [
              { "type": "ui_overlay", "slot": "chat_bottom", "config": "ui.json" },
              { "type": "bar_button", "slot": "top_bar", "label": "总结", "entry": "onInsertExam" },
              { "type": "message_decorator", "entry": "decorateMessage" }
            ]
            """.trimIndent(),
            mapOf("ui.json" to """{ "type": "text", "text": "旧" }"""),
        )
        assertTrue(result.errors.joinToString("；"), result.ok)
    }

    @Test
    fun `未知 slot 报错并列出白名单`() {
        val overlay = validate("""[{ "type": "ui_overlay", "slot": "chat_side", "config": "ui.json" }]""")
        assertFalse(overlay.ok)
        assertTrue(overlay.errors.joinToString("；").contains("GLOBAL_OVERLAY"))

        val decorator = validate("""[{ "type": "message_decorator", "slot": "MESSAGE_HEAD", "config": "b.json" }]""")
        assertFalse(decorator.ok)
        assertTrue(decorator.errors.joinToString("；").contains("MESSAGE_BODY_TAIL"))

        val bar = validate("""[{ "type": "bar_button", "slot": "left_bar", "label": "x", "entry": "onInsertExam" }]""")
        assertFalse(bar.ok)
        assertTrue(bar.errors.joinToString("；").contains("TOP_BAR_ACTIONS"))
    }

    @Test
    fun `按钮既没有 entry 也没有 ui配置 时报错`() {
        val result = validate("""[{ "type": "bar_button", "slot": "TOP_BAR_ACTIONS", "label": "总结" }]""")
        assertFalse(result.ok)
        assertTrue(result.errors.joinToString("；").contains("缺少 entry"))
    }

    @Test
    fun `消息装饰没有 entry 也没有 ui配置 时报错`() {
        val result = validate("""[{ "type": "message_decorator", "slot": "MESSAGE_BODY_TAIL" }]""")
        assertFalse(result.ok)
        assertTrue(result.errors.joinToString("；").contains("缺少 entry"))
    }

    @Test
    fun `运行时能读包内 ui文件 并派发界面事件`() {
        val pkg = PluginPackage.ofText(
            linkedMapOf(
                "manifest.json" to """
                    {
                      "id": "pet_cat", "name": "宠物猫", "version": "1.0.0", "minApiVersion": 1,
                      "permissions": ["storage"],
                      "extensions": [{ "type": "ui_overlay", "slot": "GLOBAL_OVERLAY", "config": "pet.json" }]
                    }
                """.trimIndent(),
                "logic.js" to logic,
                "pet.json" to """{ "type": "text", "textFrom": "state.line" }""",
                "assets/cat.png" to "PNG",
            ),
        )
        val runtime = PluginRuntime.create(pkg, RecordingHostApi(), InMemoryStateStore())
        try {
            assertEquals("""{ "type": "text", "textFrom": "state.line" }""", runtime.textFile("pet.json"))
            assertEquals("PNG", runtime.binaryFile("assets/cat.png")?.toString(Charsets.UTF_8))
            assertEquals(null, runtime.textFile("nope.json"))

            val before = requireNotNull(runtime.stateJson())
            assertTrue(before, before.contains("\"taps\":0"))
            assertTrue(before, before.contains("\"showBubble\":false"))

            runtime.runUiEvent("onPetTap", "tap", null)
            val after = requireNotNull(runtime.stateJson())
            assertTrue(after, after.contains("\"taps\":1"))
            assertTrue(after, after.contains("\"showBubble\":true"))
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `界面事件把 value 交给脚本`() {
        val pkg = PluginPackage.ofText(
            linkedMapOf(
                "manifest.json" to """
                    {
                      "id": "slider_demo", "name": "滑杆", "version": "1.0.0", "minApiVersion": 1,
                      "permissions": [],
                      "extensions": [{ "type": "message_decorator", "slot": "MESSAGE_BODY_TAIL", "config": "b.json" }]
                    }
                """.trimIndent(),
                "logic.js" to """
                    var state = { value: 0, event: "", ticks: 0 };
                    function onChange(ctx) { state.value = ctx.value.n; state.event = ctx.event; state.ticks = state.ticks + 1; }
                """.trimIndent(),
                "b.json" to """{ "type": "progress", "valueFrom": "state.value" }""",
            ),
        )
        val runtime = PluginRuntime.create(pkg, RecordingHostApi(), InMemoryStateStore())
        try {
            runtime.runUiEvent("onChange", "change", "{\"n\":42}")
            val snapshot = requireNotNull(runtime.stateJson())
            assertTrue(snapshot, snapshot.contains("\"value\":42"))
            assertTrue(snapshot, snapshot.contains("\"event\":\"change\""))
        } finally {
            runtime.close()
        }
    }
}
