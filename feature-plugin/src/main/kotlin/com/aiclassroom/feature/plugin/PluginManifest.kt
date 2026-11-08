package com.aiclassroom.feature.plugin

/** manifest.extensions 里的一项扩展点声明。 */
data class PluginExtension(
    val type: PluginExtensionType,
    val slot: String? = null,
    val label: String? = null,
    val config: String? = null,
    val entry: String? = null,
    val stage: String? = null,
    /** timer 扩展点：触发间隔（秒）。 */
    val intervalSeconds: Int? = null,
    /** event 扩展点：监听的事件名（白名单之一）。 */
    val event: String? = null,
) {
    /** config 省略时默认走包内的 logic.js。 */
    val configFile: String get() = config ?: PluginPackage.LOGIC_FILE
}

/** 通过校验的 manifest。 */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val minApiVersion: Int,
    val permissions: List<String>,
    val extensions: List<PluginExtension>,
) {
    fun extensionsOf(type: PluginExtensionType): List<PluginExtension> = extensions.filter { it.type == type }

    /** 该插件在指定阶段注册的钩子入口函数名，没有则返回 null。 */
    fun promptHookEntry(stage: String = "before_send"): String? =
        extensions.firstOrNull { it.type == PluginExtensionType.PROMPT_HOOK && it.stage == stage }?.entry

    /** 定时器声明（同一插件最多一个）。 */
    val timerExtension: PluginExtension? get() = extensions.firstOrNull { it.type == PluginExtensionType.TIMER }

    /** 该插件在指定事件上注册的入口函数名，没有则返回 null。 */
    fun eventEntry(event: String): String? =
        extensions.firstOrNull { it.type == PluginExtensionType.EVENT && it.event == event }?.entry
}
