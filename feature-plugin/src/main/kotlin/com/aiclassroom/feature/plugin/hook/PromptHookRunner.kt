package com.aiclassroom.feature.plugin.hook

import com.aiclassroom.feature.plugin.PluginRuntime

/** 钩子调用时宿主提供的上下文（时间可伪造，便于测试与「时间旅行」类插件）。 */
data class PromptMeta(
    val timestampMillis: Long,
    val classIndex: Int,
    val userInput: String,
)

/** 单插件 prompt 钩子执行器。 */
interface PromptHookRunner {
    /**
     * 在构造 system prompt / 发送消息前调用。
     * 返回改写后的文本；返回 null 表示该插件不改写。
     *
     * 实现可以抛异常（插件脚本报错、超时），由 [PromptHookPipeline] 负责跳过并记录。
     */
    fun runBeforeSend(plugin: PluginRuntime, currentPrompt: String, meta: PromptMeta): String?
}
