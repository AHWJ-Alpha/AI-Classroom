package com.aiclassroom.feature.plugin

/**
 * 插件状态持久化：按插件 id 存一整段 JSON。
 * 主 app 用 prefs 实现；本模块只定义接口与读写语义（ctx.storage 建立在它之上）。
 */
interface PluginStateStore {
    fun read(pluginId: String): String?
    fun write(pluginId: String, json: String)
}
