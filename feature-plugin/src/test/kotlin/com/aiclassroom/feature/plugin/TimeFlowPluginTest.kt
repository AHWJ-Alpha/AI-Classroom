package com.aiclassroom.feature.plugin

import com.aiclassroom.feature.plugin.hook.PromptHookPipeline
import com.aiclassroom.feature.plugin.hook.PromptMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 端到端证明：一个真实 .aip 包（ZIP）走完 解析 -> 校验 -> 装载 Rhino -> 连续三次改写 prompt。
 */
class TimeFlowPluginTest {

    /** 2023-11-14T22:13:20Z，宿主伪造的「当前时间」。 */
    private val fixedNow = 1_700_000_000_000L

    private fun loadRuntime(store: PluginStateStore): PluginRuntime {
        val bytes = zipOf(
            mapOf(
                "manifest.json" to readTestResource("plugins/time_flow/manifest.json"),
                "logic.js" to readTestResource("plugins/time_flow/logic.js"),
            ),
        )
        val pkg = PluginPackage.read(bytes, "time_flow.aip")
        assertTrue("示例插件必须通过校验：${pkg.validate().errors}", pkg.validate().ok)
        return PluginRuntime.create(pkg, RecordingHostApi(), store, PluginClock { fixedNow })
    }

    @Test
    fun `连续三次改写，时间依次推进 30 60 90 分钟`() {
        val store = InMemoryStateStore()
        val plugin = loadRuntime(store)
        val pipeline = PromptHookPipeline()
        val meta = PromptMeta(fixedNow, 0, "现在几点了？")
        val base = "你是一位初中英语老师。"
        val outputs = (1..3).map { round ->
            val result = pipeline.apply(listOf(plugin), base, meta)
            assertEquals("第 $round 次必须由 time_flow 改写", listOf("time_flow"), result.appliedPluginIds)
            println("[time_flow 第 $round 次] ${result.prompt}")
            result.prompt
        }
        assertEquals(
            base + "\n\n[系统信息] 当前时间：2023-11-14T22:43:20.000Z（已推进 30 分钟）",
            outputs[0],
        )
        assertEquals(
            base + "\n\n[系统信息] 当前时间：2023-11-14T23:13:20.000Z（已推进 60 分钟）",
            outputs[1],
        )
        assertEquals(
            base + "\n\n[系统信息] 当前时间：2023-11-14T23:43:20.000Z（已推进 90 分钟）",
            outputs[2],
        )
    }

    @Test
    fun `storage 持久化累计的 offsetMs`() {
        val store = InMemoryStateStore()
        val plugin = loadRuntime(store)
        val pipeline = PromptHookPipeline()
        val meta = PromptMeta(fixedNow, 0, "现在几点了？")
        repeat(3) { pipeline.apply(listOf(plugin), "PROMPT", meta) }
        val snapshot = store.snapshot("time_flow")
        println("[time_flow storage] $snapshot")
        assertTrue("存储里应留下累计 90 分钟的 offsetMs：$snapshot", snapshot!!.contains("\"offsetMs\":5400000"))
        assertTrue("storage 必须按插件 id 隔离", store.snapshot("another_plugin") == null)
    }

    @Test
    fun `重建插件后从 store 继续推进（状态在外，不在 JS 内存里）`() {
        val store = InMemoryStateStore()
        val meta = PromptMeta(fixedNow, 0, "现在几点了？")
        PromptHookPipeline().apply(listOf(loadRuntime(store)), "PROMPT", meta)
        // 换一个全新的 runtime（模拟 App 重启），storage 仍在
        val second = PromptHookPipeline().apply(listOf(loadRuntime(store)), "PROMPT", meta)
        assertTrue(second.prompt, second.prompt.endsWith("（已推进 60 分钟）"))
    }
}
