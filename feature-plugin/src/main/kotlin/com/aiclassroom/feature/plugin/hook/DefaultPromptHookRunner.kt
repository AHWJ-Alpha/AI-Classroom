package com.aiclassroom.feature.plugin.hook

import com.aiclassroom.feature.plugin.PluginRuntime

/**
 * 默认执行器：调用插件 manifest 里 prompt_hook(stage=before_send) 声明的 entry 函数。
 * JS 侧契约：onBeforePrompt(ctx) 收到 {prompt, meta}，返回字符串即替换，返回 null/undefined 表示不改。
 */
class DefaultPromptHookRunner : PromptHookRunner {
    override fun runBeforeSend(plugin: PluginRuntime, currentPrompt: String, meta: PromptMeta): String? =
        plugin.runBeforeSend(currentPrompt, meta)
}
