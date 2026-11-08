package com.aiclassroom.feature.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestValidationTest {

    private val defaultLogic = """
        function onBeforePrompt(ctx) { return ctx.prompt; }
        function onButtonTap() { return null; }
        function decorateMessage(message) { return message; }
        function renderPage() { return {}; }
    """.trimIndent()

    private val defaultHook = """[{ "type": "prompt_hook", "stage": "before_send", "config": "logic.js", "entry": "onBeforePrompt" }]"""

    private fun manifestJson(
        id: String? = "pet_cat",
        name: String? = "宠物猫",
        version: String? = "1.0.0",
        minApiVersion: Int? = 1,
        permissions: List<String>? = listOf("image.generate", "tts.speak", "dialog.read", "dialog.insert", "storage"),
        extensions: String? = defaultHook,
        settings: String? = null,
    ): String {
        val fields = mutableListOf<String>()
        id?.let { fields += "\"id\": \"$it\"" }
        name?.let { fields += "\"name\": \"$it\"" }
        version?.let { fields += "\"version\": \"$it\"" }
        fields += "\"description\": \"一只会说话、会动的猫\""
        fields += "\"author\": \"\""
        minApiVersion?.let { fields += "\"minApiVersion\": $it" }
        permissions?.let { list ->
            fields += "\"permissions\": " + list.joinToString(",", "[", "]") { "\"$it\"" }
        }
        extensions?.let { fields += "\"extensions\": $it" }
        settings?.let { fields += "\"settings\": $it" }
        return "{ " + fields.joinToString(", ") + " }"
    }

    private fun validation(
        manifest: String?,
        logic: String? = defaultLogic,
        extra: Map<String, String> = emptyMap(),
    ): ManifestValidation {
        val entries = LinkedHashMap<String, String>()
        if (manifest != null) entries["manifest.json"] = manifest
        if (logic != null) entries["logic.js"] = logic
        entries.putAll(extra)
        return PluginPackage.ofText(entries).validate()
    }

    private fun errorsOf(validation: ManifestValidation): String {
        assertFalse("这个 manifest 本应校验失败，却通过了", validation.ok)
        return validation.errors.joinToString("；")
    }

    @Test
    fun `完整示例插件通过校验`() {
        val full = manifestJson(
            extensions = """[
                { "type": "ui_overlay", "slot": "chat_bottom", "config": "ui.json" },
                { "type": "prompt_hook", "stage": "before_send", "config": "logic.js", "entry": "onBeforePrompt" },
                { "type": "bar_button", "slot": "top_bar", "label": "总结", "entry": "onButtonTap" },
                { "type": "message_decorator", "entry": "decorateMessage" },
                { "type": "page", "label": "单词卡片", "entry": "renderPage" }
            ]""",
        )
        val result = validation(full, extra = mapOf("ui.json" to "{\"nodes\":[]}"))
        assertTrue("本应通过：${result.errors}", result.ok)
        val manifest = result.manifest!!
        assertEquals("pet_cat", manifest.id)
        assertEquals("1.0.0", manifest.version)
        assertEquals(5, manifest.extensions.size)
        assertEquals("onBeforePrompt", manifest.promptHookEntry())
        assertEquals(listOf("image.generate", "tts.speak", "dialog.read", "dialog.insert", "storage"), manifest.permissions)
        // config 省略时默认 logic.js
        assertEquals("logic.js", manifest.extensions.first { it.type == PluginExtensionType.PAGE }.configFile)
    }

    @Test
    fun `缺少 id`() {
        val errors = errorsOf(validation(manifestJson(id = null)))
        assertTrue(errors, errors.contains("缺少必需字段 id"))
    }

    @Test
    fun `id 非法`() {
        val errors = errorsOf(validation(manifestJson(id = "Pet-Cat")))
        assertTrue(errors, errors.contains("id 非法"))
    }

    @Test
    fun `id 过短`() {
        val errors = errorsOf(validation(manifestJson(id = "a")))
        assertTrue(errors, errors.contains("id 非法"))
    }

    @Test
    fun `未知权限被拒`() {
        val errors = errorsOf(validation(manifestJson(permissions = listOf("image.generate", "image.hack"))))
        assertTrue(errors, errors.contains("未知权限"))
        assertTrue(errors, errors.contains("image.hack"))
    }

    @Test
    fun `未知扩展类型被拒`() {
        val errors = errorsOf(
            validation(manifestJson(extensions = """[{ "type": "floating_widget", "entry": "onButtonTap" }]""")),
        )
        assertTrue(errors, errors.contains("未知扩展类型"))
        assertTrue(errors, errors.contains("floating_widget"))
    }

    @Test
    fun `引用不存在的配置文件被拒`() {
        val errors = errorsOf(
            validation(manifestJson(extensions = """[{ "type": "ui_overlay", "slot": "chat_bottom", "config": "ui.json" }]""")),
        )
        assertTrue(errors, errors.contains("引用的文件不存在：'ui.json'"))
    }

    @Test
    fun `引用穿越路径被拒`() {
        val errors = errorsOf(
            validation(manifestJson(extensions = """[{ "type": "ui_overlay", "slot": "chat_bottom", "config": "../evil.json" }]""")),
        )
        assertTrue(errors, errors.contains("非法路径"))
    }

    @Test
    fun `entry 函数在 logic js 里不存在`() {
        val errors = errorsOf(
            validation(manifestJson(extensions = """[{ "type": "prompt_hook", "stage": "before_send", "entry": "onMissing" }]""")),
        )
        assertTrue(errors, errors.contains("'onMissing' 在 logic.js 中不存在"))
    }

    @Test
    fun `logic js 缺失时 entry 也报错`() {
        val errors = errorsOf(validation(manifestJson(), logic = null))
        assertTrue(errors, errors.contains("logic.js"))
    }

    @Test
    fun `version 不是语义化版本`() {
        val errors = errorsOf(validation(manifestJson(version = "1.0")))
        assertTrue(errors, errors.contains("version 非法"))
    }

    @Test
    fun `minApiVersion 高于宿主`() {
        val errors = errorsOf(validation(manifestJson(minApiVersion = 2)))
        assertTrue(errors, errors.contains("宿主只支持"))
    }

    @Test
    fun `prompt_hook 缺 stage`() {
        val errors = errorsOf(
            validation(manifestJson(extensions = """[{ "type": "prompt_hook", "entry": "onBeforePrompt" }]""")),
        )
        assertTrue(errors, errors.contains("(prompt_hook) 缺少 stage"))
    }

    @Test
    fun `prompt_hook 未知 stage`() {
        val errors = errorsOf(
            validation(manifestJson(extensions = """[{ "type": "prompt_hook", "stage": "after_send", "entry": "onBeforePrompt" }]""")),
        )
        assertTrue(errors, errors.contains("未知 stage"))
    }

    @Test
    fun `bar_button 缺 label`() {
        val errors = errorsOf(
            validation(manifestJson(extensions = """[{ "type": "bar_button", "slot": "top_bar", "entry": "onButtonTap" }]""")),
        )
        assertTrue(errors, errors.contains("(bar_button) 缺少 label"))
    }

    @Test
    fun `bar_button 未知 slot`() {
        val errors = errorsOf(
            validation(manifestJson(extensions = """[{ "type": "bar_button", "slot": "side_bar", "label": "总结", "entry": "onButtonTap" }]""")),
        )
        assertTrue(errors, errors.contains("未知 slot"))
    }

    @Test
    fun `ui_overlay 缺 config`() {
        val errors = errorsOf(
            validation(manifestJson(extensions = """[{ "type": "ui_overlay", "slot": "chat_bottom" }]""")),
        )
        assertTrue(errors, errors.contains("缺少 config"))
    }

    @Test
    fun `manifest 不是合法 JSON`() {
        val errors = errorsOf(validation("{ \"id\": \"pet_cat\", }"))
        assertTrue(errors, errors.contains("不是合法 JSON"))
    }

    @Test
    fun `manifest 顶层不是对象`() {
        val errors = errorsOf(validation("[]"))
        assertTrue(errors, errors.contains("顶层必须是 JSON 对象"))
    }

    @Test
    fun `包里缺少 manifest json`() {
        val result = validation(manifest = null)
        assertFalse(result.ok)
        assertTrue(result.errors.toString(), result.errors.any { it.contains("缺少必需的 manifest.json") })
    }

    @Test
    fun `ZIP 里的路径穿越被拒绝`() {
        val bytes = zipOf(mapOf("manifest.json" to "{}", "../evil.js" to "function x(){}"))
        try {
            PluginPackage.read(bytes, "evil.aip")
            throw AssertionError("路径穿越的包本应被拒绝")
        } catch (e: PluginPackageException) {
            assertTrue(e.message!!, e.message!!.contains("非法路径"))
        }
    }

    @Test
    fun `非 ZIP 内容被拒绝`() {
        try {
            PluginPackage.read("not a zip".toByteArray(), "broken.aip")
            throw AssertionError("非 ZIP 内容本应被拒绝")
        } catch (e: PluginPackageException) {
            assertTrue(e.message!!, e.message!!.contains("ZIP") || e.message!!.contains("空的"))
        }
    }
}
