package com.aiclassroom.app.plugin

import com.aiclassroom.feature.plugin.PluginHostApi
import com.aiclassroom.feature.plugin.PluginResult
import com.aiclassroom.feature.plugin.PluginStateStore
import com.aiclassroom.feature.plugin.host.PluginKvStorage

/**
 * :app 侧的 [PluginHostApi] 实现。
 *
 * 具体能力（生图 / TTS / 网络 / 对话）都由主 app 用 lambda 注入，这里只做三件事：
 * 1. 统一兜异常：任何实现抛出来都转成 PluginResult.Failure，绝不让脚本看到 Kotlin 异常；
 * 2. storageGet / storagePut 直接建在 PluginStateStore 上（与 ctx.storage 共用同一份 key-value）；
 * 3. requestUiRefresh 转给主 app 的重组回调。
 *
 * 说明：按插件的权限校验发生在 feature-plugin 的 HostFacade（那里才知道 pluginId）；
 * 宿主这一层再按「功能总开关」兜一层（网络关闭时任何插件都拿不到网络能力）。
 */
class AppPluginHost(
    private val stateStore: PluginStateStore,
    private val generateImageImpl: suspend (prompt: String, width: Int?, height: Int?) -> PluginResult,
    private val speakImpl: suspend (text: String) -> PluginResult,
    private val httpImpl: suspend (url: String, method: String, body: String?) -> PluginResult,
    private val readDialogImpl: suspend (limit: Int) -> PluginResult,
    private val insertDialogImpl: suspend (role: String, text: String) -> PluginResult,
    private val uiRefreshImpl: (String) -> Unit = {},
    private val logger: (String) -> Unit = { line -> android.util.Log.i(PLUGIN_LOG_TAG, line) },
) : PluginHostApi {

    override suspend fun generateImage(prompt: String, width: Int?, height: Int?): PluginResult =
        guard("image.generate") { generateImageImpl(prompt, width, height) }

    override suspend fun speak(text: String): PluginResult = guard("tts.speak") { speakImpl(text) }

    override suspend fun httpRequest(url: String, method: String, body: String?): PluginResult =
        guard("http.request") { httpImpl(url, method, body) }

    override suspend fun readDialog(limit: Int): PluginResult = guard("dialog.read") { readDialogImpl(limit) }

    override suspend fun insertDialog(role: String, text: String): PluginResult =
        guard("dialog.insert") { insertDialogImpl(role, text) }

    override fun storageGet(pluginId: String, key: String): String? = try {
        PluginKvStorage(stateStore, pluginId).get(key)
    } catch (t: Throwable) {
        logger("storageGet 失败（$pluginId/$key）：" + (t.message ?: t.javaClass.simpleName))
        null
    }

    override fun storagePut(pluginId: String, key: String, value: String) {
        try {
            PluginKvStorage(stateStore, pluginId).set(key, value)
        } catch (t: Throwable) {
            logger("storagePut 失败（$pluginId/$key）：" + (t.message ?: t.javaClass.simpleName))
        }
    }

    override fun requestUiRefresh(pluginId: String) {
        try {
            uiRefreshImpl(pluginId)
        } catch (t: Throwable) {
            logger("requestUiRefresh 失败（$pluginId）：" + (t.message ?: t.javaClass.simpleName))
        }
    }

    private suspend fun guard(capability: String, block: suspend () -> PluginResult): PluginResult = try {
        block()
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (t: Throwable) {
        logger("宿主能力 $capability 失败：" + (t.message ?: t.javaClass.simpleName))
        PluginResult.Failure("host_error", t.message ?: t.javaClass.simpleName)
    }
}
