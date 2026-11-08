package com.aiclassroom.feature.plugin.host

import com.aiclassroom.feature.plugin.PluginClock
import com.aiclassroom.feature.plugin.PluginHostApi
import com.aiclassroom.feature.plugin.PluginLimits
import com.aiclassroom.feature.plugin.PluginLogger
import com.aiclassroom.feature.plugin.PluginPermissions
import com.aiclassroom.feature.plugin.PluginResult
import com.aiclassroom.feature.plugin.js.jsFunction
import com.aiclassroom.feature.plugin.js.jsInt
import com.aiclassroom.feature.plugin.js.jsObject
import com.aiclassroom.feature.plugin.js.jsProperty
import com.aiclassroom.feature.plugin.js.jsString
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeJSON
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
 * 成功返回 {ok:true, value, path, truncated}：value 超过 [PluginLimits.MAX_RESULT_BYTES] 时
 * 在这里兜底截断并把 truncated 置 true。
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
                "listClassrooms" to jsFunction("listClassrooms") { c, s, _ ->
                    guarded(c, s, PluginPermissions.CLASSROOM_READ) { api.listClassrooms() }
                },
                "writeClassroom" to jsFunction("writeClassroom") { c, s, args ->
                    guarded(c, s, PluginPermissions.CLASSROOM_WRITE) {
                        api.writeClassroom(jsonArgument(c, s, args))
                    }
                },
                "readExam" to jsFunction("readExam") { c, s, _ ->
                    guarded(c, s, PluginPermissions.EXAM_READ) { api.readExam() }
                },
                "writeExam" to jsFunction("writeExam") { c, s, args ->
                    guarded(c, s, PluginPermissions.EXAM_WRITE) {
                        api.writeExam(jsonArgument(c, s, args))
                    }
                },
                "readMemory" to jsFunction("readMemory") { c, s, _ ->
                    guarded(c, s, PluginPermissions.MEMORY_READ) { api.readMemory() }
                },
                "writeMemory" to jsFunction("writeMemory") { c, s, args ->
                    guarded(c, s, PluginPermissions.MEMORY_WRITE) {
                        api.writeMemory(jsString(args.getOrNull(0)) ?: "")
                    }
                },
                "readKnowledge" to jsFunction("readKnowledge") { c, s, _ ->
                    guarded(c, s, PluginPermissions.KNOWLEDGE_READ) { api.readKnowledge() }
                },
                "readAllDialogs" to jsFunction("readAllDialogs") { c, s, args ->
                    readAllDialogs(c, s, args)
                },
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

    /** 跨课堂读对话：没声明 dialogs.readAll 直接 permission_denied；条数上限由宿主再夹一次。 */
    private fun readAllDialogs(cx: Context, scope: Scriptable, args: Array<out Any?>): Any {
        if (!permitted(PluginPermissions.DIALOGS_READ_ALL)) {
            return denied(cx, scope, PluginPermissions.DIALOGS_READ_ALL)
        }
        val options = args.getOrNull(0)
        // 只有直接把字符串当参数传时才回退成 classroomId；传对象时属性缺失必须保持 null（表示全部课堂）。
        val classroomId = jsString(jsProperty(options, "classroomId")) ?: jsString(options.takeIf { it is CharSequence })
        val requested = jsInt(jsProperty(options, "limitPerClassroom")) ?: jsInt(args.getOrNull(1)) ?: 0
        val limit = if (requested <= 0) {
            PluginLimits.MAX_DIALOGS_PER_CLASSROOM
        } else {
            requested.coerceAtMost(PluginLimits.MAX_DIALOGS_PER_CLASSROOM)
        }
        return render(cx, scope, attempt { api.readAllDialogs(classroomId, limit) })
    }

    /** 统一的「权限校验 + 兜异常 + 渲染」入口，新增能力全部走这里，避免每项重复四行样板。 */
    private fun guarded(
        cx: Context,
        scope: Scriptable,
        permission: String,
        block: suspend () -> PluginResult,
    ): Any = if (!permitted(permission)) {
        denied(cx, scope, permission)
    } else {
        render(cx, scope, attempt { block() })
    }

    private fun attempt(action: suspend () -> PluginResult): PluginResult = try {
        BlockingSuspend.runBlocking(hostCallTimeoutMillis, action)
    } catch (e: PluginHostTimeoutException) {
        PluginResult.Failure("host_timeout", e.message ?: "宿主能力调用超时")
    } catch (e: Throwable) {
        PluginResult.Failure("host_error", e.message ?: e.javaClass.simpleName)
    }

    private fun render(cx: Context, scope: Scriptable, result: PluginResult): Any = when (result) {
        is PluginResult.Success -> {
            val limited = PluginLimits.truncate(result.value)
            if (limited.truncated) {
                logger(
                    "[plugin:" + pluginId + "] 宿主能力返回值超过 " + PluginLimits.MAX_RESULT_BYTES +
                        " 字节，已截断（原长 " + result.value.length + " 字符）",
                )
            }
            jsObject(
                cx,
                scope,
                mapOf(
                    "ok" to true,
                    "value" to limited.text,
                    "path" to result.path,
                    "truncated" to limited.truncated,
                ),
            )
        }

        is PluginResult.Failure -> jsObject(
            cx,
            scope,
            mapOf("ok" to false, "code" to result.code, "message" to result.message, "truncated" to false),
        )
    }

    /**
     * 写接口的 patch 参数：既接受 JSON 字符串，也接受 JS 对象（现场 stringify）。
     * 不做这一层的话 host.writeClassroom({select:{index:0}}) 会变成 "[object Object]" 直接 invalid_argument。
     */
    private fun jsonArgument(cx: Context, scope: Scriptable, args: Array<out Any?>): String {
        val raw = args.getOrNull(0)
        if (raw is CharSequence) return raw.toString()
        if (raw is Scriptable) {
            val json = NativeJSON.stringify(cx, scope, raw, null, null)
            if (json is CharSequence) return json.toString()
        }
        return "{}"
    }

    private fun permitted(permission: String): Boolean {
        val granted = permissions.contains(permission)
        if (!granted) logger("[plugin:" + pluginId + "] 未声明权限 " + permission + "，调用被拒绝")
        return granted
    }

    private fun denied(cx: Context, scope: Scriptable, permission: String): Any = jsObject(
        cx,
        scope,
        mapOf(
            "ok" to false,
            "code" to "permission_denied",
            "message" to "插件未在 manifest.permissions 中声明 " + permission,
            "truncated" to false,
        ),
    )

    companion object {
        const val DEFAULT_HOST_CALL_TIMEOUT_MILLIS = 15_000L
    }
}
