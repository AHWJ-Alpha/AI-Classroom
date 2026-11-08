package com.aiclassroom.feature.plugin

/**
 * 宿主能力接口：本模块只定义契约，:app 负责实现（复用它已有的生图 / TTS / 网络 / 对话 / prefs）。
 *
 * 挂起函数在 JS 侧以同步外观暴露（内部阻塞等待），因此实现方必须保证：
 * - 不会阻塞在等待主线程的调度上（详见 BlockingSuspend 的限制说明）；
 * - 失败时返回 PluginResult.Failure 而不是抛异常，异常也会被兜住并转成 host_error。
 */
interface PluginHostApi {

    /** 生图：复用主 app 已有的 image 能力，Success.path 返回落盘路径。 */
    suspend fun generateImage(prompt: String, width: Int?, height: Int?): PluginResult

    /** TTS：Success.path 返回音频文件路径。 */
    suspend fun speak(text: String): PluginResult

    /** HTTP：走已有 net 能力（受总开关约束）。 */
    suspend fun httpRequest(url: String, method: String, body: String?): PluginResult

    /** 读取当前课堂上下文 / 最近对话。 */
    suspend fun readDialog(limit: Int): PluginResult

    /** 往对话里插入内容。 */
    suspend fun insertDialog(role: String, text: String): PluginResult

    /** 插件私有存储（按插件 id 隔离的 key-value）。 */
    fun storageGet(pluginId: String, key: String): String?

    fun storagePut(pluginId: String, key: String, value: String)

    /** 插件要求刷新界面时调用。 */
    fun requestUiRefresh(pluginId: String)
}
