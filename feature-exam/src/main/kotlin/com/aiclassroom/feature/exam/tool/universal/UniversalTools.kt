package com.aiclassroom.feature.exam.tool.universal

import com.aiclassroom.feature.exam.tool.ToolHandler
import com.aiclassroom.feature.exam.tool.ToolRegistry
import com.aiclassroom.feature.exam.tool.ToolResult
import com.aiclassroom.feature.exam.tool.exam.JsonFormatException
import com.aiclassroom.feature.exam.tool.exam.JsonValue
import com.aiclassroom.feature.exam.tool.exam.asIntOrNull
import com.aiclassroom.feature.exam.tool.exam.asObjectOrNull
import com.aiclassroom.feature.exam.tool.exam.jsonArray
import com.aiclassroom.feature.exam.tool.exam.jsonObject
import com.aiclassroom.feature.exam.tool.image.DEFAULT_TIMEOUT_SECONDS
import com.aiclassroom.feature.exam.tool.image.ImageFailure
import com.aiclassroom.feature.exam.tool.image.ImageSink
import com.aiclassroom.feature.exam.tool.image.MAX_TIMEOUT_SECONDS
import java.util.concurrent.CancellationException

/** 模板变量名：顶层参数 → {{占位符}}。 */
private val TEMPLATE_KEYS: List<String> =
    listOf("prompt", "negative", "width", "height", "steps", "cfg", "model")

/**
 * 通用 HTTP 元工具：一个 universal_call + 一份 profile 配置 = 一个应用适配。
 *
 * 与 ImageTools 的分工：generate_image 是「已知后端」的快捷方式（三个 provider 各写一个类），
 * universal_call 是「未知后端」的通用入口（配置驱动，不再为每个服务写代码）。
 * 保存图片仍然走同一个 [ImageSink]，所以上层拿到的路径形态和 generate_image 完全一致；
 * 数据型接口（response.type = json / universal_get_json）不落盘，[sink] 传 null 也能用。
 */
class UniversalTools(
    private val sink: ImageSink?,
    private val library: () -> String = { "" },
) {

    val handlers: List<ToolHandler> = listOf(UniversalCallTool(), UniversalGetJsonTool(), ListProfilesTool())

    fun register(registry: ToolRegistry): UniversalTools {
        handlers.forEach(registry::register)
        return this
    }

    /**
     * 内置 profile + 主 app 注入的自定义 profile（同名覆盖内置）。
     * 库解析失败不让整个工具报错：返回内置库 + 一条错误说明，配置问题在 list_profiles 里可见。
     */
    private fun loadedProfiles(): Pair<List<UniversalProfile>, String?> {
        val builtin = UniversalProfiles.builtin()
        val custom = try {
            UniversalProfiles.parseLibrary(library())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: ImageFailure) {
            return builtin to (failure.message ?: "profile 库无法解析")
        } catch (throwable: Throwable) {
            return builtin to ("profile 库读取失败：" + (throwable.message ?: throwable::class.java.simpleName))
        }
        return UniversalProfiles.merge(builtin, custom) to null
    }

    private inner class UniversalCallTool : ToolHandler {
        override val name: String = "universal_call"
        override val description: String =
            "按「配置」调用任意 HTTP 生图服务：给 baseUrl + request（method/path/bodyTemplate）+ response（extract/type）" +
                "就能提交并取回图片，结果是已保存到本机的 {ok,path,bytes,mime,elapsedMs}。" +
                "请求体模板里的 {{prompt}} {{negative}} {{width}} {{height}} {{steps}} {{cfg}} {{model}} 会做 JSON 转义替换；" +
                "response.type 支持 json_base64 / json_url（相对地址会自动带同样的认证再下载）/ binary；" +
                "要「只拿数据、不落盘」（查询 / 控制 / 状态类接口）就用 response.type = json，结果是 {ok,status,json}；" +
                "async 支持「先提交、再从响应取结果地址、必要时轮询」。不知道服务协议时先 probe_endpoint 探测，" +
                "再用 list_profiles 看有没有现成 profile（nightmare / openai-images / sd-webui）。失败会带上服务端响应片段。"

        override val parametersJson: String = UniversalToolSchemas.UNIVERSAL_CALL

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val arguments = parseArguments(argumentsJson, name)
                ToolResult.Ok(UniversalCaller(sink).execute(buildPlan(arguments)))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: ImageFailure) {
                ToolResult.Error(failure.message ?: "universal_call 失败")
            } catch (throwable: Throwable) {
                ToolResult.Error("universal_call 失败：" + (throwable.message ?: throwable::class.java.simpleName))
            }
        }

        /** profile / 顶层参数合成一次调用的完整计划；缺什么就明确报什么。 */
        private fun buildPlan(arguments: Map<String, JsonValue>): UniversalCallPlan {
            val (profiles, _) = loadedProfiles()
            val inlineJson = text(arguments, "profileJson")?.trim()?.ifEmpty { null }
            val profileName = text(arguments, "profile")?.trim()?.ifEmpty { null }
            val selected: UniversalProfile? = when {
                inlineJson != null -> UniversalProfile.fromJsonString(inlineJson)
                profileName != null -> profiles.firstOrNull { it.name.equals(profileName, true) }
                    ?: throw ImageFailure(
                        "没有名为「" + profileName + "」的 profile。可用：" + profiles.joinToString(" / ") { it.name }
                    )
                else -> null
            }
            if (selected == null && arguments["response"] == null) {
                throw ImageFailure(
                    "universal_call 需要 response（extract / type）描述怎么取图，或者先用 list_profiles 选一个 profile"
                )
            }
            val base = selected ?: UniversalProfile(name = "inline")
            val baseUrl = text(arguments, "baseUrl")?.trim()?.ifEmpty { null } ?: base.baseUrl
            if (baseUrl.isBlank()) {
                throw ImageFailure("universal_call 需要 baseUrl，例如 http://127.0.0.1:8820")
            }
            val request = UniversalRequest.fromJson(arguments["request"], base.request)
            if (request.path.isBlank()) {
                throw ImageFailure("universal_call 需要 request.path（例如 /run 或 /sdapi/v1/txt2img）")
            }
            val values = LinkedHashMap<String, String>()
            values.putAll(base.defaults)
            TEMPLATE_KEYS.forEach { key -> scalarText(arguments[key], key)?.let { values[key] = it } }
            // 与既有 custom provider 的模板别名对齐，抄模板时不用改名。
            values[UniversalTemplate.NEGATIVE]?.let { values["negativePrompt"] = it }
            values[UniversalTemplate.CFG]?.let { values["cfgScale"] = it }
            stringMap(arguments["values"], "values")?.let { values.putAll(it) }
            val timeout = arguments.intArgument("timeoutSeconds") ?: DEFAULT_TIMEOUT_SECONDS
            if (timeout < 1 || timeout > MAX_TIMEOUT_SECONDS) {
                throw ImageFailure("timeoutSeconds 必须在 1-" + MAX_TIMEOUT_SECONDS + " 之间，收到 " + timeout)
            }
            return UniversalCallPlan(
                profileName = selected?.name ?: "inline",
                baseUrl = baseUrl,
                auth = UniversalAuth.fromJson(arguments["auth"], base.auth),
                request = request,
                response = UniversalResponse.fromJson(arguments["response"], base.response),
                async = UniversalAsync.fromJson(arguments["async"], base.async),
                timeoutSeconds = timeout,
                values = values,
            )
        }
    }

    /**
     * universal_call 的简化版：GET + response.type = json。
     * 数据型接口不必写嵌套对象，省得模型每次都拼一遍 {request,response}。
     */
    private inner class UniversalGetJsonTool : ToolHandler {
        override val name: String = "universal_get_json"
        override val description: String =
            "GET 一个返回 JSON 的接口，把数据直接回给模型（不落盘、不生成文件）：给 baseUrl + path" +
                "（可选 auth / extract）等价于 universal_call 的 GET + response.type=json。" +
                "适合查询 / 控制 / 状态类 API，例如 Clash 的 http://127.0.0.1:48597 + /version。" +
                "结果是 {ok,status,json}；响应体超过 64KB 会截断并标 truncated，非 JSON 响应给出 text 片段。"

        override val parametersJson: String = UniversalToolSchemas.UNIVERSAL_GET_JSON

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val arguments = parseArguments(argumentsJson, name)
                ToolResult.Ok(UniversalCaller(sink).execute(buildPlan(arguments)))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: ImageFailure) {
                ToolResult.Error(failure.message ?: "universal_get_json 失败")
            } catch (throwable: Throwable) {
                ToolResult.Error("universal_get_json 失败：" + (throwable.message ?: throwable::class.java.simpleName))
            }
        }

        private fun buildPlan(arguments: Map<String, JsonValue>): UniversalCallPlan {
            val baseUrl = text(arguments, "baseUrl")?.trim()?.ifEmpty { null }
                ?: throw ImageFailure("universal_get_json 需要 baseUrl，例如 http://127.0.0.1:48597")
            val path = text(arguments, "path")?.trim()?.ifEmpty { null }
                ?: throw ImageFailure("universal_get_json 需要 path，例如 /version")
            val timeout = arguments.intArgument("timeoutSeconds") ?: DEFAULT_TIMEOUT_SECONDS
            if (timeout < 1 || timeout > MAX_TIMEOUT_SECONDS) {
                throw ImageFailure("timeoutSeconds 必须在 1-" + MAX_TIMEOUT_SECONDS + " 之间，收到 " + timeout)
            }
            return UniversalCallPlan(
                profileName = "get_json",
                baseUrl = baseUrl,
                auth = UniversalAuth.fromJson(arguments["auth"], UniversalAuth()),
                request = UniversalRequest(method = "GET", path = path),
                response = UniversalResponse(
                    extract = text(arguments, "extract")?.trim().orEmpty(),
                    type = UniversalResponse.TYPE_JSON,
                ),
                async = UniversalAsync(),
                timeoutSeconds = timeout,
                values = emptyMap(),
            )
        }
    }

    private inner class ListProfilesTool : ToolHandler {
        override val name: String = "list_profiles"
        override val description: String =
            "列出 universal_call 可用的 profile（一份配置 = 一个应用适配）：内置 nightmare / openai-images / sd-webui，" +
                "加上主 app 注入的自定义 profile。给 name 返回该 profile 的完整 JSON（可复制修改后通过 profileJson 传回）。"

        override val parametersJson: String = UniversalToolSchemas.LIST_PROFILES

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val arguments = parseArguments(argumentsJson, name)
                val wanted = text(arguments, "name")?.trim()?.ifEmpty { null }
                val (profiles, libraryError) = loadedProfiles()
                val builtinNames = UniversalProfiles.builtinNames()
                val target = wanted?.let { name ->
                    profiles.firstOrNull { it.name.equals(name, true) }
                        ?: throw ImageFailure(
                            "没有名为「" + name + "」的 profile。可用：" + profiles.joinToString(" / ") { it.name }
                        )
                }
                val fields = ArrayList<Pair<String, JsonValue>>(5)
                fields.add("ok" to JsonValue.Bool(true))
                fields.add("count" to JsonValue.Num(profiles.size.toDouble()))
                if (target != null) {
                    fields.add("builtin" to JsonValue.Bool(target.name in builtinNames))
                    fields.add("profile" to target.toJsonValue())
                    fields.add("json" to JsonValue.Str(target.toJsonString()))
                } else {
                    fields.add(
                        "profiles" to jsonArray(
                            profiles.map { profile ->
                                jsonObject(
                                    "name" to JsonValue.Str(profile.name),
                                    "builtin" to JsonValue.Bool(profile.name in builtinNames),
                                    "description" to JsonValue.Str(profile.description),
                                    "baseUrl" to JsonValue.Str(profile.baseUrl),
                                    "authType" to JsonValue.Str(profile.auth.type),
                                    "request" to JsonValue.Str(profile.request.method + " " + profile.request.path),
                                    "response" to JsonValue.Str(
                                        profile.response.type + "(" + profile.response.extract + ")"
                                    ),
                                    "async" to JsonValue.Bool(profile.async.enabled),
                                )
                            }
                        )
                    )
                    fields.add(
                        "hint" to JsonValue.Str(
                            "用 list_profiles {name} 看某个 profile 的完整 JSON；内置 profile 不含 token，调用时用 auth.token 传凭据。"
                        )
                    )
                }
                if (libraryError != null) fields.add("libraryError" to JsonValue.Str(libraryError))
                ToolResult.Ok(JsonValue.write(jsonObject(*fields.toTypedArray())))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: ImageFailure) {
                ToolResult.Error(failure.message ?: "list_profiles 失败")
            } catch (throwable: Throwable) {
                ToolResult.Error("list_profiles 失败：" + (throwable.message ?: throwable::class.java.simpleName))
            }
        }
    }

    /** 参数解析：空串按「没有参数」处理（list_profiles 可以不带参数）。 */
    private fun parseArguments(argumentsJson: String, toolName: String): Map<String, JsonValue> {
        val raw = argumentsJson.trim()
        if (raw.isEmpty()) return emptyMap()
        val root = try {
            JsonValue.parse(raw)
        } catch (failure: JsonFormatException) {
            throw ImageFailure(toolName + " 的参数不是合法 JSON：" + failure.message)
        }
        return root.asObjectOrNull() ?: throw ImageFailure(toolName + " 的参数必须是 JSON 对象")
    }

    private fun Map<String, JsonValue>.intArgument(name: String): Int? {
        val raw = this[name] ?: return null
        if (raw === JsonValue.Null) return null
        return raw.asIntOrNull() ?: throw ImageFailure(name + " 必须是整数")
    }
}

/**
 * 主 app 集成入口：注册 universal_call / list_profiles。
 * [library] 返回自定义 profile 库的 JSON（对象数组 / {"profiles":[...]} / 空串），由主 app 从设置里读。
 */
fun registerUniversalTools(
    registry: ToolRegistry,
    sink: ImageSink?,
    library: () -> String = { "" },
): UniversalTools = UniversalTools(sink, library).register(registry)
