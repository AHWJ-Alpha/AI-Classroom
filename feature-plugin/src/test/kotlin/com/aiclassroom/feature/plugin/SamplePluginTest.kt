package com.aiclassroom.feature.plugin

import com.aiclassroom.feature.plugin.hook.PromptMeta
import com.aiclassroom.feature.plugin.ui.PluginUiParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 三个示例插件既是「自由度」的证据，也是回归样本：
 * 它们必须始终通过 manifest 校验、ui.json 解析、入口函数静态检查，打包成 .aip 之后还要能直接导入。
 */
class SamplePluginTest {

    /** 有预打包 .aip 的三个示例（导入路径的回归样本）。 */
    private val sampleIds = listOf("pet_cat", "msg_badge", "top_quick")

    /** 全部示例插件：只用源文件（manifest.json + logic.js 等）也必须通过校验。 */
    private val allSampleIds = sampleIds + listOf("nightmare", "time_flow")

    private fun bytesOf(path: String): ByteArray =
        checkNotNull(SamplePluginTest::class.java.classLoader?.getResourceAsStream(path)) {
            "找不到测试资源：$path"
        }.use { it.readBytes() }

    private fun packageOf(pluginId: String): PluginPackage {
        val text = linkedMapOf<String, String>()
        text["manifest.json"] = readTestResource("plugins/$pluginId/manifest.json")
        text["logic.js"] = readTestResource("plugins/$pluginId/logic.js")
        val extra = when (pluginId) {
            "pet_cat" -> listOf("pet.json")
            "msg_badge" -> listOf("badge.json")
            "top_quick" -> listOf("btn.json")
            else -> emptyList()
        }
        for (file in extra) text[file] = readTestResource("plugins/$pluginId/$file")
        val entries = linkedMapOf<String, ByteArray>()
        for ((path, content) in text) entries[path] = content.toByteArray(Charsets.UTF_8)
        if (pluginId == "pet_cat") {
            for (asset in listOf("assets/cat_idle.png", "assets/cat_tap.png")) {
                entries[asset] = bytesOf("plugins/pet_cat/$asset")
            }
        }
        return PluginPackage.of(entries)
    }

    @Test
    fun `示例插件都通过 manifest 校验`() {
        for (pluginId in allSampleIds) {
            val validation = packageOf(pluginId).validate()
            assertTrue(pluginId + " 校验失败：" + validation.errors.joinToString("；"), validation.ok)
        }
    }

    @Test
    fun `示例插件的 ui文件都能解析`() {
        val cases = mapOf(
            "pet_cat" to "pet.json",
            "msg_badge" to "badge.json",
            "top_quick" to "btn.json",
        )
        for ((pluginId, file) in cases) {
            val result = PluginUiParser.parse(readTestResource("plugins/$pluginId/$file"))
            assertTrue(pluginId + "/" + file + " 解析失败：" + result.errors.joinToString("；"), result.ok)
            assertNotNull(result.root)
        }
    }

    @Test
    fun `示例插件覆盖三种截然不同的插槽形态`() {
        assertEquals(
            listOf("GLOBAL_OVERLAY"),
            packageOf("pet_cat").validate().manifest!!.extensionsOf(PluginExtensionType.UI_OVERLAY).map { it.slot },
        )
        assertEquals(
            listOf("MESSAGE_BODY_TAIL"),
            packageOf("msg_badge").validate().manifest!!.extensionsOf(PluginExtensionType.MESSAGE_DECORATOR).map { it.slot },
        )
        assertEquals(
            listOf("TOP_BAR_ACTIONS"),
            packageOf("top_quick").validate().manifest!!.extensionsOf(PluginExtensionType.BAR_BUTTON).map { it.slot },
        )
    }

    @Test
    fun `nightmare 示例覆盖了全部五种配置类型`() {
        val manifest = packageOf("nightmare").validate().manifest!!
        assertEquals(
            listOf(
                PluginSettingType.SECRET,
                PluginSettingType.TEXT,
                PluginSettingType.BOOLEAN,
                PluginSettingType.NUMBER,
                PluginSettingType.SELECT,
            ),
            manifest.settings.map { it.type },
        )
        assertEquals("必填的只有 token", listOf("token"), manifest.settings.filter { it.required }.map { it.key })
        // 没填 token 时钩子什么都不做，填了才注入参数
        val blank = PluginRuntime.create(packageOf("nightmare"), RecordingHostApi(), InMemoryStateStore())
        try {
            assertNull(
                "必填项没填时插件应当安静地不做事",
                blank.runBeforeSend("P", PromptMeta(1L, 0, "hi")),
            )
        } finally {
            blank.close()
        }
        val filled = PluginRuntime.create(
            packageOf("nightmare"), RecordingHostApi(), InMemoryStateStore(),
            settingValues = mapOf("token" to "tk", "quality" to "high", "steps" to "60"),
        )
        try {
            val out = filled.runBeforeSend("P", PromptMeta(1L, 0, "hi"))!!
            assertTrue(out, out.contains("画质：high"))
            assertTrue(out, out.contains("步数：60"))
            assertFalse("token 不能被拼进 prompt", out.contains("tk"))
        } finally {
            filled.close()
        }
    }

    @Test
    fun `示例插件可以真的装载并跑起来`() {
        // pet_cat：点一下 -> 气泡打开、好感度 +1、TTS 被调用
        val host = RecordingHostApi()
        val pet = PluginRuntime.create(packageOf("pet_cat"), host, InMemoryStateStore())
        try {
            pet.runUiEvent("onPetTap", "tap", null)
            val snapshot = pet.stateJson().orEmpty()
            assertTrue(snapshot, snapshot.contains("\"showBubble\":true"))
            assertTrue(snapshot, snapshot.contains("\"affinity\":1"))
            assertEquals(listOf("speak:这题不难，我盯着你写"), host.calls.filter { it.startsWith("speak:") })
        } finally {
            pet.close()
        }

        // msg_badge：assistant_replied 累计字数与用时
        val badgeHost = RecordingHostApi()
        val badge = PluginRuntime.create(packageOf("msg_badge"), badgeHost, InMemoryStateStore())
        try {
            badge.runEvent("onSent", PluginEventNames.MESSAGE_SENT, "{}")
            badge.runEvent("onReplied", PluginEventNames.ASSISTANT_REPLIED, "{\"chars\":\"128\"}")
            val snapshot = badge.stateJson().orEmpty()
            assertTrue(snapshot, snapshot.contains("\"count\":1"))
            assertTrue(snapshot, snapshot.contains("\"totalChars\":128"))
            assertTrue(snapshot, snapshot.contains("累计 1 条 / 128 字"))
        } finally {
            badge.close()
        }

        // top_quick：exam.read + dialog.insert
        val quickHost = RecordingHostApi()
        quickHost.examResult = PluginResult.Success(
            "{\"title\":\"第一次小测\",\"questions\":[{\"question\":\"1+1=?\"}],\"questionCount\":1}",
        )
        val quick = PluginRuntime.create(packageOf("top_quick"), quickHost, InMemoryStateStore())
        try {
            quick.runUiEvent("onInsertExam", "tap", null)
            val inserted = quickHost.calls.firstOrNull { it.startsWith("insertDialog:") }.orEmpty()
            assertTrue(inserted, inserted.contains("第一次小测"))
            assertTrue(inserted, inserted.contains("1+1=?"))
        } finally {
            quick.close()
        }
    }

    @Test
    fun `三个 aip 包可以直接导入`() {
        for (pluginId in sampleIds) {
            val pkg = PluginPackage.read(bytesOf("aip/$pluginId.aip"), "$pluginId.aip")
            val validation = pkg.validate()
            assertTrue(pluginId + ".aip 校验失败：" + validation.errors.joinToString("；"), validation.ok)
            assertEquals(pluginId, validation.manifest?.id)
        }
        // pet_cat 的素材要真的在包里，否则实机上只有一只透明的猫
        val pet = PluginPackage.read(bytesOf("aip/pet_cat.aip"), "pet_cat.aip")
        assertNotNull(pet.bytes("assets/cat_idle.png"))
        assertNotNull(pet.bytes("assets/cat_tap.png"))
    }
}
