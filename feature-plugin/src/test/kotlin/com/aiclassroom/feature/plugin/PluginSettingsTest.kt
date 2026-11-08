package com.aiclassroom.feature.plugin

import com.aiclassroom.feature.plugin.hook.PromptHookPipeline
import com.aiclassroom.feature.plugin.hook.PromptMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * manifest.settings 的校验、合并与注入。
 *
 * 三块各自独立：
 * 1. 校验：错误信息必须指到 settings[i] 与具体字段；
 * 2. 合并：只认声明过的 key，空值回落 default；
 * 3. 注入：ctx.settings 拿到的是真类型（number 是数字、boolean 是布尔）。
 */
class PluginSettingsTest {

    private val defaultLogic = "function onBeforePrompt(ctx) { return ctx.prompt; }"

    private fun validation(settings: String?, extra: Map<String, String> = emptyMap()): ManifestValidation {
        val settingsField = if (settings == null) "" else ", \"settings\": " + settings
        val entries = LinkedHashMap<String, String>()
        entries["manifest.json"] = """
            {
              "id": "nightmare",
              "name": "夜梦",
              "version": "1.0.0",
              "description": "带 token 的示例插件",
              "author": "AI Classroom",
              "minApiVersion": 1,
              "permissions": ["storage"],
              "extensions": [{ "type": "prompt_hook", "stage": "before_send", "entry": "onBeforePrompt" }]
              $settingsField
            }
        """.trimIndent()
        entries["logic.js"] = defaultLogic
        entries.putAll(extra)
        return PluginPackage.ofText(entries).validate()
    }

    private fun errorsOf(validation: ManifestValidation): String {
        assertFalse("这个 manifest 本应校验失败，却通过了", validation.ok)
        return validation.errors.joinToString("；")
    }

    private val fullSettings = """
        [
          { "key": "token",   "label": "访问令牌", "type": "secret", "required": true,
            "description": "nightmare → 设置 → API → Copy token", "placeholder": "粘贴 token" },
          { "key": "city",    "label": "默认城市", "type": "text",   "required": false, "default": "北京" },
          { "key": "enabled", "label": "启用音频", "type": "boolean", "default": true },
          { "key": "steps",   "label": "步数",     "type": "number", "default": 20, "min": 1, "max": 150 },
          { "key": "quality", "label": "画质",     "type": "select", "options": ["low","medium","high"], "default": "medium" }
        ]
    """.trimIndent()

    @Test
    fun `五种类型齐全的 settings 通过校验`() {
        val result = validation(fullSettings)
        assertTrue("本应通过：${result.errors}", result.ok)
        val settings = result.manifest!!.settings
        assertEquals(5, settings.size)
        assertEquals(listOf("token", "city", "enabled", "steps", "quality"), settings.map { it.key })
        assertEquals(
            listOf(
                PluginSettingType.SECRET,
                PluginSettingType.TEXT,
                PluginSettingType.BOOLEAN,
                PluginSettingType.NUMBER,
                PluginSettingType.SELECT,
            ),
            settings.map { it.type },
        )
        val token = settings.first { it.key == "token" }
        assertTrue(token.required)
        assertNull("secret 没写 default 时就是 null", token.defaultValue)
        assertEquals("nightmare → 设置 → API → Copy token", token.description)
        assertEquals("粘贴 token", token.placeholder)
        assertEquals(PluginSettingDefault.Text("北京"), settings.first { it.key == "city" }.defaultValue)
        assertEquals(PluginSettingDefault.Flag(true), settings.first { it.key == "enabled" }.defaultValue)
        assertEquals(PluginSettingDefault.Number(20.0), settings.first { it.key == "steps" }.defaultValue)
        assertEquals(listOf("low", "medium", "high"), settings.first { it.key == "quality" }.options)
        assertEquals(1.0, settings.first { it.key == "steps" }.min!!, 0.0)
        assertEquals(150.0, settings.first { it.key == "steps" }.max!!, 0.0)
    }

    @Test
    fun `没有 settings 段时为空列表`() {
        val result = validation(null)
        assertTrue(result.errors.joinToString("；"), result.ok)
        assertTrue(result.manifest!!.settings.isEmpty())
    }

    @Test
    fun `settings 必须是数组`() {
        val errors = errorsOf(validation("""{ "key": "token" }"""))
        assertTrue(errors, errors.contains("settings 必须是数组"))
    }

    @Test
    fun `key 非法要指名第几项`() {
        val errors = errorsOf(validation("""[{ "key": "Token-Key", "label": "令牌", "type": "secret" }]"""))
        assertTrue(errors, errors.contains("settings[0].key 非法"))
        assertTrue(errors, errors.contains("Token-Key"))
    }

    @Test
    fun `key 超长与空 key 都被拒`() {
        val tooLong = "a".repeat(33)
        assertTrue(
            errorsOf(validation("""[{ "key": "$tooLong", "label": "x", "type": "text" }]"""))
                .contains("settings[0].key 非法"),
        )
        assertTrue(
            errorsOf(validation("""[{ "key": "", "label": "x", "type": "text" }]"""))
                .contains("settings[0].key 非法"),
        )
    }

    @Test
    fun `key 重复要指出重复的那一项`() {
        val errors = errorsOf(
            validation(
                """[
                    { "key": "token", "label": "令牌", "type": "secret" },
                    { "key": "token", "label": "再来一个", "type": "text" }
                ]""",
            ),
        )
        assertTrue(errors, errors.contains("settings[1].key 重复声明：'token'"))
    }

    @Test
    fun `label 为空或过长被拒`() {
        assertTrue(
            errorsOf(validation("""[{ "key": "a", "label": "  ", "type": "text" }]"""))
                .contains("settings[0].label 不能为空"),
        )
        val longLabel = "标".repeat(33)
        assertTrue(
            errorsOf(validation("""[{ "key": "a", "label": "$longLabel", "type": "text" }]"""))
                .contains("settings[0].label 过长"),
        )
    }

    @Test
    fun `type 未知要列出允许的取值`() {
        val errors = errorsOf(validation("""[{ "key": "a", "label": "A", "type": "richtext" }]"""))
        assertTrue(errors, errors.contains("未知配置类型 settings[0].type='richtext'"))
        assertTrue(errors, errors.contains("text, secret, number, boolean, select"))
    }

    @Test
    fun `缺少 type 被拒`() {
        assertTrue(
            errorsOf(validation("""[{ "key": "a", "label": "A" }]"""))
                .contains("settings[0] 缺少字符串字段 type"),
        )
    }

    @Test
    fun `required 必须是布尔`() {
        assertTrue(
            errorsOf(validation("""[{ "key": "a", "label": "A", "type": "text", "required": "yes" }]"""))
                .contains("settings[0].required 必须是布尔值"),
        )
    }

    @Test
    fun `default 类型与 type 不匹配被拒`() {
        assertTrue(
            errorsOf(validation("""[{ "key": "a", "label": "A", "type": "number", "default": "20" }]"""))
                .contains("settings[0].default 必须是数字（type=number）"),
        )
        assertTrue(
            errorsOf(validation("""[{ "key": "a", "label": "A", "type": "boolean", "default": 1 }]"""))
                .contains("settings[0].default 必须是布尔值（type=boolean）"),
        )
        assertTrue(
            errorsOf(validation("""[{ "key": "a", "label": "A", "type": "text", "default": true }]"""))
                .contains("settings[0].default 必须是字符串（type=text）"),
        )
        assertTrue(
            errorsOf(
                validation("""[{ "key": "a", "label": "A", "type": "select", "options": ["x"], "default": 1 }]"""),
            ).contains("settings[0].default 必须是字符串（type=select）"),
        )
    }

    @Test
    fun `number 的 default 越界被拒`() {
        assertTrue(
            errorsOf(validation("""[{ "key": "s", "label": "步数", "type": "number", "default": 0, "min": 1 }]"""))
                .contains("settings[0].default 越界"),
        )
        assertTrue(
            errorsOf(validation("""[{ "key": "s", "label": "步数", "type": "number", "default": 200, "max": 150 }]"""))
                .contains("settings[0].default 越界：200（不得大于 max=150）"),
        )
    }

    @Test
    fun `min 大于 max 被拒`() {
        assertTrue(
            errorsOf(validation("""[{ "key": "s", "label": "步数", "type": "number", "min": 10, "max": 1 }]"""))
                .contains("的 min 不能大于 max"),
        )
    }

    @Test
    fun `min 和 max 只能用于 number`() {
        val errors = errorsOf(
            validation("""[{ "key": "c", "label": "城市", "type": "text", "min": 1, "max": 2 }]"""),
        )
        assertTrue(errors, errors.contains("settings[0].min 只对 type=number 有效"))
        assertTrue(errors, errors.contains("settings[0].max 只对 type=number 有效"))
    }

    @Test
    fun `select 缺少 options 被拒`() {
        assertTrue(
            errorsOf(validation("""[{ "key": "q", "label": "画质", "type": "select" }]"""))
                .contains("settings[0] (select) 缺少 options"),
        )
    }

    @Test
    fun `select 的 options 为空被拒`() {
        assertTrue(
            errorsOf(validation("""[{ "key": "q", "label": "画质", "type": "select", "options": [] }]"""))
                .contains("settings[0].options 不能为空"),
        )
    }

    @Test
    fun `options 只能用于 select`() {
        assertTrue(
            errorsOf(validation("""[{ "key": "c", "label": "城市", "type": "text", "options": ["a"] }]"""))
                .contains("settings[0].options 只对 type=select 有效"),
        )
    }

    @Test
    fun `select 的 default 必须在 options 里`() {
        val q = """[{ "key": "q", "label": "画质", "type": "select", "options": ["low","high"], "default": "medium" }]"""
        assertTrue(errorsOf(validation(q)).contains("settings[0].default='medium' 不在 options 里"))
    }

    @Test
    fun `超过 16 项被拒`() {
        val items = (1..17).joinToString(",") { """{ "key": "k$it", "label": "K$it", "type": "text" }""" }
        val errors = errorsOf(validation("[$items]"))
        assertTrue(errors, errors.contains("settings 最多 16 项"))
    }

    @Test
    fun `正好 16 项可以通过`() {
        val items = (1..16).joinToString(",") { """{ "key": "k$it", "label": "K$it", "type": "text" }""" }
        val result = validation("[$items]")
        assertTrue(result.errors.joinToString("；"), result.ok)
        assertEquals(16, result.manifest!!.settings.size)
    }

    @Test
    fun `description 与 placeholder 过长被拒`() {
        val long = "字".repeat(121)
        val errors = errorsOf(
            validation(
                """[{ "key": "a", "label": "A", "type": "text", "description": "$long", "placeholder": "$long" }]""",
            ),
        )
        assertTrue(errors, errors.contains("settings[0].description 过长"))
        assertTrue(errors, errors.contains("settings[0].placeholder 过长"))
    }

    // ------------------------------------------------------------------ 脱敏

    @Test
    fun `secret 的默认值不出现在任何 toString 输出里`() {
        val result = validation(
            """[{ "key": "token", "label": "令牌", "type": "secret", "default": "sk-super-secret-value" }]""",
        )
        assertTrue(result.errors.joinToString("；"), result.ok)
        val manifest = result.manifest!!
        val setting = manifest.settings.first()
        val renders = listOf(
            setting.toString(),
            setting.copy().toString(),
            manifest.toString(),
            manifest.settings.toString(),
            result.toString(),
            PluginSettingsCodec.resolve(manifest.settings, emptyMap()).toString(),
        )
        for (text in renders) {
            assertFalse("secret 明文泄露到 toString：$text", text.contains("sk-super-secret-value"))
        }
        assertTrue(setting.toString(), setting.toString().contains("<hidden>"))
        // 但填进存储的值本身要能取出来（脚本真的要用它）
        assertEquals("sk-super-secret-value", setting.defaultValue?.asStorageValue())
    }

    // ------------------------------------------------------------------ 合并与注入

    @Test
    fun `resolve 补齐 default 且只认声明过的 key`() {
        val manifest = validation(fullSettings).manifest!!
        val resolved = PluginSettingsCodec.resolve(
            manifest.settings,
            mapOf("token" to "abc", "city" to "", "legacy_key" to "旧版本遗留"),
        )
        assertEquals("abc", resolved["token"])
        assertEquals("空值回落 default", "北京", resolved["city"])
        assertEquals("true", resolved["enabled"])
        assertEquals("20", resolved["steps"])
        assertEquals("medium", resolved["quality"])
        assertFalse("未声明的 key 不该流进 ctx.settings", resolved.containsKey("legacy_key"))
    }

    @Test
    fun `toJson 按 type 给出真类型`() {
        val manifest = validation(fullSettings).manifest!!
        val resolved = PluginSettingsCodec.resolve(manifest.settings, mapOf("steps" to "42", "enabled" to "false"))
        val json = PluginSettingsCodec.toJson(manifest.settings, resolved)
        assertTrue(json, json.contains("\"steps\":42"))
        assertTrue(json, json.contains("\"enabled\":false"))
        assertTrue(json, json.contains("\"city\":\"北京\""))
        assertFalse("没配的 secret 不该出现在 JSON 里", json.contains("\"token\""))
    }

    @Test
    fun `未完成计数只数必填且没填好的项`() {
        val manifest = validation(fullSettings).manifest!!
        val blank = PluginSettingsCodec.resolve(manifest.settings, emptyMap())
        assertEquals(listOf("token"), PluginSettingsCodec.missingRequired(manifest.settings, blank))
        val filled = PluginSettingsCodec.resolve(manifest.settings, mapOf("token" to "abc"))
        assertTrue(PluginSettingsCodec.missingRequired(manifest.settings, filled).isEmpty())
    }

    @Test
    fun `number 越界算未完成`() {
        val required = """[{ "key": "s", "label": "步数", "type": "number", "required": true, "min": 1, "max": 150 }]"""
        val manifest = validation(required).manifest!!
        val outOfRange = PluginSettingsCodec.resolve(manifest.settings, mapOf("s" to "999"))
        assertEquals(listOf("s"), PluginSettingsCodec.missingRequired(manifest.settings, outOfRange))
        assertEquals("不能大于 150", PluginSettingsCodec.numberProblem(manifest.settings.first(), "999"))
        assertNull(PluginSettingsCodec.numberProblem(manifest.settings.first(), "20"))
    }

    @Test
    fun `ctx settings 里 number 是数字 boolean 是布尔`() {
        val pkg = PluginPackage.ofText(
            linkedMapOf(
                "manifest.json" to """
                    {
                      "id": "nightmare",
                      "name": "夜梦",
                      "version": "1.0.0",
                      "description": "",
                      "author": "",
                      "minApiVersion": 1,
                      "permissions": ["storage"],
                      "settings": $fullSettings,
                      "extensions": [{ "type": "prompt_hook", "stage": "before_send", "entry": "onBeforePrompt" }]
                    }
                """.trimIndent(),
                "logic.js" to """
                    function onBeforePrompt(ctx) {
                      var s = ctx.settings;
                      return [typeof s.steps, s.steps, typeof s.enabled, s.enabled,
                              s.city, s.quality, String(s.token)].join('|');
                    }
                """.trimIndent(),
            ),
        )
        val runtime = PluginRuntime.create(
            pkg, RecordingHostApi(), InMemoryStateStore(),
            settingValues = mapOf("token" to "abc", "steps" to "77"),
        )
        try {
            val out = PromptHookPipeline().apply(listOf(runtime), "P", PromptMeta(1L, 0, "hi")).prompt
            assertEquals("number|77|boolean|true|北京|medium|abc", out)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `没配置的 secret 在脚本里是 undefined`() {
        val pkg = PluginPackage.ofText(
            linkedMapOf(
                "manifest.json" to """
                    {
                      "id": "nightmare",
                      "name": "夜梦",
                      "version": "1.0.0",
                      "description": "",
                      "author": "",
                      "minApiVersion": 1,
                      "permissions": ["storage"],
                      "settings": [{ "key": "token", "label": "令牌", "type": "secret", "required": true }],
                      "extensions": [{ "type": "prompt_hook", "stage": "before_send", "entry": "onBeforePrompt" }]
                    }
                """.trimIndent(),
                "logic.js" to "function onBeforePrompt(ctx) { return typeof ctx.settings.token; }",
            ),
        )
        val runtime = PluginRuntime.create(pkg, RecordingHostApi(), InMemoryStateStore())
        try {
            val out = PromptHookPipeline().apply(listOf(runtime), "P", PromptMeta(1L, 0, "hi")).prompt
            assertEquals("undefined", out)
        } finally {
            runtime.close()
        }
    }
}