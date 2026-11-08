package com.aiclassroom.feature.plugin

/**
 * 宿主能力调用的统一返回值。
 * 插件看不到 Kotlin 异常，只能看到 {ok:true,value,path} / {ok:false,code,message} 的 JS 对象。
 */
sealed class PluginResult {
    /** [value] 是给插件看的文本结果；[path] 是落盘路径（生图 / TTS 这类有文件产物的能力才有）。 */
    data class Success(val value: String, val path: String? = null) : PluginResult()

    /** [code] 是稳定的机器可读错误码，[message] 面向调试。 */
    data class Failure(val code: String, val message: String) : PluginResult()
}

/** 插件日志出口；宿主接进自己的日志系统即可，默认丢弃。 */
typealias PluginLogger = (String) -> Unit
