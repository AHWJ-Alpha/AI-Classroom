package com.aiclassroom.feature.plugin

/**
 * 插件脚本层面的失败：语法错误、找不到入口函数、返回值类型不对、执行超时。
 * [timeout] 为 true 表示是指令计数观察者强制中断，便于宿主区分「插件写错」和「插件卡死」。
 */
class PluginScriptException(
    message: String,
    cause: Throwable? = null,
    val timeout: Boolean = false,
) : Exception(message, cause)
