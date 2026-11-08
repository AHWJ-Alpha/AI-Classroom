package com.aiclassroom.feature.plugin.hook

import com.aiclassroom.feature.plugin.PluginLogger
import com.aiclassroom.feature.plugin.PluginRuntime

/** 被跳过的钩子及原因。 */
data class SkippedHook(val pluginId: String, val reason: String)

/** 一次串联的结果：最终 prompt、成功改写的插件、被跳过的插件。 */
data class PromptPipelineResult(
    val prompt: String,
    val appliedPluginIds: List<String>,
    val skipped: List<SkippedHook>,
)

/**
 * 多插件 prompt 钩子串联。
 *
 * 语义：
 * - 按传入顺序（= 宿主加载顺序 = manifest 声明顺序）串行作用于同一份 prompt，后一个插件收到前一个的输出；
 * - 返回 null 的插件不参与改写，prompt 保持不变；
 * - 任一插件抛异常 / 超时，只跳过该插件并记录，绝不中断发送流程。
 */
class PromptHookPipeline(
    private val runner: PromptHookRunner = DefaultPromptHookRunner(),
    private val logger: PluginLogger = {},
) {

    fun apply(plugins: List<PluginRuntime>, prompt: String, meta: PromptMeta): PromptPipelineResult {
        var current = prompt
        val applied = mutableListOf<String>()
        val skipped = mutableListOf<SkippedHook>()
        for (plugin in plugins) {
            if (plugin.promptHookEntry == null) continue
            try {
                val rewritten = runner.runBeforeSend(plugin, current, meta)
                if (rewritten != null) {
                    current = rewritten
                    applied += plugin.manifest.id
                }
            } catch (t: Throwable) {
                val reason = t.message ?: t.javaClass.simpleName
                skipped += SkippedHook(plugin.manifest.id, reason)
                logger("[plugin] 跳过 ${plugin.manifest.id} 的 prompt 钩子：$reason")
            }
        }
        return PromptPipelineResult(current, applied, skipped)
    }
}
