package com.aiclassroom.feature.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 任务 1 的验收：新增能力面的权限闸门与 32KB 截断。 */
class CapabilityTest {

    private fun runtime(permissions: List<String>, host: PluginHostApi = RecordingHostApi()): PluginRuntime {
        val manifest = """
            {
              "id": "cap_probe",
              "name": "能力探测",
              "version": "1.0.0",
              "minApiVersion": 1,
              "permissions": ${permissions.joinToString(",", "[", "]") { "\"" + it + "\"" }},
              "extensions": []
            }
        """.trimIndent()
        return PluginRuntime.create(
            PluginPackage.ofText(mapOf("manifest.json" to manifest, "logic.js" to "")),
            host,
            InMemoryStateStore(),
        )
    }

    @Test
    fun `未声明 exam read 时 readExam 返回 permission_denied`() {
        val plugin = runtime(permissions = listOf("storage"))
        assertEquals("permission_denied", plugin.evalValue("host.readExam().code") as String)
    }

    @Test
    fun `声明 exam read 后 readExam 走宿主实现`() {
        val host = RecordingHostApi().apply { examResult = PluginResult.Success("{\"title\":\"第一次测验\"}") }
        val plugin = runtime(permissions = listOf("exam.read"), host = host)
        val rendered = plugin.evalValue(
            "(function(){ var r = host.readExam(); return r.ok + '|' + r.value + '|' + r.truncated; })()",
        ) as String
        assertEquals("true|{\"title\":\"第一次测验\"}|false", rendered)
        assertEquals(listOf("readExam"), host.calls)
    }

    @Test
    fun `未声明的每一项新能力都被拒绝`() {
        val plugin = runtime(permissions = emptyList())
        val codes = listOf(
            "host.listClassrooms()",
            "host.writeClassroom('{}')",
            "host.readExam()",
            "host.writeExam('{}')",
            "host.readMemory()",
            "host.writeMemory('x')",
            "host.readKnowledge()",
            "host.readAllDialogs({classroomId: null})",
        ).map { call -> plugin.evalValue(call + ".code") as String }
        assertEquals(List(8) { "permission_denied" }, codes)
    }

    @Test
    fun `声明权限后每项新能力都到达宿主`() {
        val host = RecordingHostApi()
        val plugin = runtime(
            permissions = listOf(
                "classroom.read",
                "classroom.write",
                "exam.read",
                "exam.write",
                "memory.read",
                "memory.write",
                "knowledge.read",
                "dialogs.readAll",
            ),
            host = host,
        )
        val patch = "{\"rename\":{\"index\":0,\"name\":\"新名字\"}}"
        val examPatch = "{\"questions\":[{\"index\":0,\"unknown\":true}]}"
        plugin.evalValue("host.listClassrooms()")
        plugin.evalValue("host.writeClassroom('" + patch + "')")
        plugin.evalValue("host.readExam()")
        plugin.evalValue("host.writeExam('" + examPatch + "')")
        plugin.evalValue("host.readMemory()")
        plugin.evalValue("host.writeMemory('记住了')")
        plugin.evalValue("host.readKnowledge()")
        plugin.evalValue("host.readAllDialogs({ classroomId: '0', limitPerClassroom: 5 })")
        assertEquals(
            listOf(
                "listClassrooms",
                "writeClassroom:" + patch,
                "readExam",
                "writeExam:" + examPatch,
                "readMemory",
                "writeMemory:记住了",
                "readKnowledge",
                "readAllDialogs:0:5",
            ),
            host.calls,
        )
    }

    @Test
    fun `写接口接受 JS 对象参数（现场 stringify）`() {
        val host = RecordingHostApi()
        val plugin = runtime(permissions = listOf("classroom.write", "exam.write"), host = host)
        plugin.evalValue("host.writeClassroom({ select: { index: 0 } })")
        plugin.evalValue("host.writeExam({ questions: [{ index: 1, unknown: true }] })")
        assertEquals(
            listOf(
                "writeClassroom:{\"select\":{\"index\":0}}",
                "writeExam:{\"questions\":[{\"index\":1,\"unknown\":true}]}",
            ),
            host.calls,
        )
    }

    @Test
    fun `readAllDialogs 的每课堂条数被夹到 50`() {
        val host = RecordingHostApi()
        val plugin = runtime(permissions = listOf("dialogs.readAll"), host = host)
        plugin.evalValue("host.readAllDialogs({ classroomId: null, limitPerClassroom: 9999 })")
        assertEquals(listOf("readAllDialogs:null:50"), host.calls)
    }

    @Test
    fun `readAllDialogs 不传条数时默认 50`() {
        val host = RecordingHostApi()
        val plugin = runtime(permissions = listOf("dialogs.readAll"), host = host)
        plugin.evalValue("host.readAllDialogs({ classroomId: 1 })")
        assertEquals(listOf("readAllDialogs:1:50"), host.calls)
    }

    @Test
    fun `超过 32KB 的返回值被截断并标记 truncated`() {
        val host = RecordingHostApi().apply { examResult = PluginResult.Success("x".repeat(64 * 1024)) }
        val plugin = runtime(permissions = listOf("exam.read"), host = host)
        val rendered = plugin.evalValue(
            "(function(){ var r = host.readExam(); return r.value.length + '|' + r.truncated; })()",
        ) as String
        assertEquals("32768|true", rendered)
    }

    @Test
    fun `刚好 32KB 的返回值不算截断`() {
        val host = RecordingHostApi().apply {
            examResult = PluginResult.Success("y".repeat(PluginLimits.MAX_RESULT_BYTES))
        }
        val plugin = runtime(permissions = listOf("exam.read"), host = host)
        val rendered = plugin.evalValue(
            "(function(){ var r = host.readExam(); return r.value.length + '|' + r.truncated; })()",
        ) as String
        assertEquals("32768|false", rendered)
    }

    @Test
    fun `中文内容截断不会切出半个字符`() {
        // 每个汉字 3 字节：32768 不能被 3 整除，必须回退到字符边界。
        val chinese = "课堂".repeat(11000)
        assertTrue("样本必须超过上限", chinese.toByteArray(Charsets.UTF_8).size > PluginLimits.MAX_RESULT_BYTES)
        val limited = PluginLimits.truncate(chinese)
        assertTrue(limited.truncated)
        val bytes = limited.text.toByteArray(Charsets.UTF_8)
        assertTrue("字节数不得超过上限", bytes.size <= PluginLimits.MAX_RESULT_BYTES)
        assertFalse("不能出现替换字符", limited.text.contains('\uFFFD'))
        assertEquals(0, limited.text.length % 2)
        assertTrue(chinese.startsWith(limited.text))
    }

    @Test
    fun `权限拒绝时不调用宿主实现`() {
        val host = RecordingHostApi()
        val plugin = runtime(permissions = listOf("dialogs.readAll"), host = host)
        plugin.evalValue("host.readExam()")
        assertTrue(host.calls.isEmpty())
    }
}
