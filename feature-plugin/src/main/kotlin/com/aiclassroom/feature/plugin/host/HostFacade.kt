package com.aiclassroom.feature.plugin.host

import com.aiclassroom.feature.plugin.PluginClock
import com.aiclassroom.feature.plugin.PluginHostApi
import com.aiclassroom.feature.plugin.PluginLogger
import com.aiclassroom.feature.plugin.PluginPermissions
import com.aiclassroom.feature.plugin.PluginResult
import com.aiclassroom.feature.plugin.js.jsFunction
import com.aiclassroom.feature.plugin.js.jsInt
import com.aiclassroom.feature.plugin.js.jsObject
import com.aiclassroom.feature.plugin.js.jsProperty
import com.aiclassroom.feature.plugin.js.jsString
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

/**
 * 注进插件 Scope 的全局 host 对象：同步外观 + 权限校验 + 阻塞等待 + 统一错误形状。
 *
 * JS 侧写法（同步）：
 *   var r = host.generateImage({ prompt: '一只猫', width: 512, height: 512 });
 *   if (r.ok) { host.log(r.path); } else { host.log(r.code); }
 *
 * 所有失败都返回 {ok:false, code, message}，不向脚本抛异常；超时返回 code=host_timeout。
 */
internal class HostFacade(
    private val api: PluginHostApi,
    private val kv: PluginKvStorage,
    private val pluginId: String,
    private val permissions: Set<String>,
    private val logger: PluginLogger,
    private val hostCallTimeoutMillis: Long = DEFAULT_HOST_CALL_TIMEOUT_MILLIS,
    private val clock: PluginClock = PluginClock.SYSTEM,
) {

    fun install(cx: Context, scope: Scriptable) {
        val host = jsObject(
            cx,
            scope,
            mapOf(
                "generateImage" to jsFunction("generateImage") { c, s, args -> generateImage(c, s, args) },
                "speak" to jsFunction("speak") { c, s, args -> speak(c, s, args) },
                "httpRequest" to jsFunction("httpRequest") { c, s, args -> httpRequest(c, s, args) },
                "readDialog" to jsFunction("readDialog") { c, s, args -> readDialog(c, s, args) },
                "insertDialog" to jsFunction("insertDialog") { c, s, args -> insertDialog(c, s, args) },
                "storageGet" to jsFunction("storageGet") { _, _, args ->
                    kv.get(jsString(args.getOrNull(0)) ?: "")
                },
                "storagePut" to jsFunction("storagePut") { _, _, args ->
                    kv.set(jsString(args.getOrNull(0)) ?: "", jsString(args.getOrNull(1)) ?: "")
                    Undefined.instance
                },
                "requestUiRefresh" to jsFunction("requestUiRefresh") { _, _, _ ->
                    api.requestUiRefresh(pluginId)
                    Undefined.instance
                },
                "now" to jsFunction("now") { _, _, _ -> clock.nowMillis().toDouble() },
            ),
        )
        ScriptableObject.putProperty(scope, "host", host)
    }

    private fun generateImage(cx: Context, scope: Scriptable, args: Array<out Any?>): Any {
        val options = args.getOrNull(0)
        val prompt = jsString(jsProperty(options, "prompt")) ?: jsString(options) ?: ""
        val width = jsInt(jsProperty(options, "width"))
        val height = jsInt(jsProperty(options, "height"))
        if (!permitted(PluginPermissions.IMAGE_GENERATE)) {
            return denied(cx, scope, PluginPermissions.IMAGE_GENERATE)
        }
        val result = attempt { api.generateImage(prompt, width, height) }
        return render(cx, scope, result)
    }

    private fun speak(cx: Context, scope: Scriptable, args: Array<out Any?>): Any {
        val options = args.getOrNull(0)
        val text = jsString(jsProperty(options, "text")) ?: jsString(options) ?: ""
        if (!permitted(PluginPermissions.TTS_SPEAK)) return denied(cx, scope, PluginPermissions.TTS_SPEAK)
        return render(cx, scope, attempt { api.speak(text) })
    }

    private fun httpRequest(cx: Context, scope: Scriptable, args: Array<out Any?>): Any {
        val options = args.getOrNull(0)
        val url = jsString(jsProperty(options, "url")) ?: jsString(options) ?: ""
        val method = jsString(jsProperty(options, "method")) ?: "GET"
        val body = jsString(jsProperty(options, "body"))
        if (!permitted(PluginPermissions.HTTP_REQUEST)) return denied(cx, scope, PluginPermissions.HTTP_REQUEST)
        return render(cx, scope, attempt { api.httpRequest(url, method, body) })
    }

    private fun readDialog(cx: Context, scope: Scriptable, args: Array<out Any?>): Any {
        val limit = jsInt(args.getOrNull(0)) ?: jsInt(jsProperty(args.getOrNull(0), "limit")) ?: 10
        if (!permitted(PluginPermissions.DIALOG_READ)) return denied(cx, scope, PluginPermissions.DIALOG_READ)
        return render(cx, scope, attempt { api.readDialog(limit) })
    }

    private fun insertDialog(cx: Context, scope: Scriptable, args: Array<out Any?>): Any {
        val options = args.getOrNull(0)
        val role = jsString(jsProperty(options, "role")) ?: jsString(args.getOrNull(0)) ?: "assistant"
        val text = jsString(jsProperty(options, "text")) ?: jsString(args.getOrNull(1)) ?: ""
        if (!permitted(PluginPermissions.DIALOG_INSERT)) {
            return denied(cx, scope, PluginPermissions.DIALOG_INSERT)
        }
        return render(cx, scope, attempt { api.insertDialog(role, text) })
    }

    private fun attempt(action: suspend () -> PluginResult): PluginResult = try {
        BlockingSuspend.runBlocking(hostCallTimeoutMillis, action)
    } catch (e: PluginHostTimeoutException) {
        PluginResult.Failure("host_timeout", e.message ?: "宿主能力调用超时")
    } catch (e: Throwable) {
        PluginResult.Failure("host_error", e.message ?: e.javaClass.simpleName)
    }

    private fun render(cx: Context, scope: Scriptable, result: PluginResult): Any = when (result) {
        is PluginResult.Success -> jsObject(
            cx,
            scope,
            mapOf("ok" to true, "value" to result.value, "path" to result.path),
        )

        is PluginResult.Failure -> jsObject(
            cx,
            scope,
            mapOf("ok" to false, "code" to result.code, "message" to result.message),
        )
    }

    private fun permitted(permission: String): Boolean {
        val granted = permissions.contains(permission)
        if (!granted) logger("[plugin:$pluginId] 未声明权限 $permission，调用被拒绝")
        return granted
    }

    private fun denied(cx: Context, scope: Scriptable, permission: String): Any = jsObject(
        cx,
        scope,
        mapOf(
            "ok" to false,
            "code" to "permission_denied",
            "message" to "插件未在 manifest.permissions 中声明 $permission",
        ),
    )

    companion object {
        const val DEFAULT_HOST_CALL_TIMEOUT_MILLIS = 15_000L
    }
}