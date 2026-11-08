package com.aiclassroom.feature.exam.tool.universal

import com.aiclassroom.feature.exam.tool.exam.JsonFormatException
import com.aiclassroom.feature.exam.tool.exam.JsonValue
import com.aiclassroom.feature.exam.tool.exam.asObjectOrNull
import com.aiclassroom.feature.exam.tool.image.ImageFailure
import com.aiclassroom.feature.exam.tool.image.MAX_TIMEOUT_SECONDS

/**
 * profile 库：内置三份 + 主 app 注入的自定义 JSON。
 *
 * 内置的三份是同一套 schema 能表达三种完全不同 API 的证据：
 * - nightmare：非标准 API，POST /run 拿结果 URL，再带认证 GET 二进制（异步/同步都能走）
 * - openai-images：标准 JSON，base64 图片在 data[0].b64_json
 * - sd-webui：A1111 兼容，base64 在 images[0]，参数是扁平数字
 *
 * 内置 profile **不含 token**：凭据永远由调用方（用户）在调用时给，方便安全地分享配置。
 */
internal object UniversalProfiles {

    private const val NIGHTMARE_NAME: String = "nightmare"
    private const val OPENAI_NAME: String = "openai-images"
    private const val SDWEBUI_NAME: String = "sd-webui"

    private val BUILTIN: List<UniversalProfile> = listOf(
        UniversalProfile(
            name = NIGHTMARE_NAME,
            description = "nightmare（手机端 8820 端口）非标准 API：POST /run 提交，响应里带结果图片 URL，" +
                "再带同一个 Bearer 头 GET 二进制。token 需由用户提供。",
            baseUrl = "http://127.0.0.1:8820",
            auth = UniversalAuth(type = UniversalAuth.TYPE_BEARER),
            request = UniversalRequest(
                method = "POST",
                path = "/run",
                headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
                bodyTemplate = """{"flow":"txt2img","params":{"prompt":{"prompt":"{{prompt}}","negative":"{{negative}}"}}}""",
            ),
            response = UniversalResponse(
                extract = "results[0].url",
                type = UniversalResponse.TYPE_JSON_URL,
                mime = "image/png",
            ),
            // nightmare 的提交响应里直接带 results[0].url，所以 resultUrlFrom 一取到就能跳过轮询。
            async = UniversalAsync(
                enabled = true,
                resultUrlFrom = "results[0].url",
                timeoutSeconds = MAX_TIMEOUT_SECONDS,
            ),
            defaults = mapOf("negative" to "", "width" to "1024", "height" to "1024"),
        ),
        UniversalProfile(
            name = OPENAI_NAME,
            description = "OpenAI Images（/v1/images/generations）：Bearer 认证，图片以 base64 放在 data[0].b64_json。",
            baseUrl = "https://api.openai.com/v1",
            auth = UniversalAuth(type = UniversalAuth.TYPE_BEARER),
            request = UniversalRequest(
                method = "POST",
                path = "/images/generations",
                headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
                bodyTemplate = """{"model":"{{model}}","prompt":"{{prompt}}","n":1,"size":"1024x1024","response_format":"b64_json"}""",
            ),
            response = UniversalResponse(
                extract = "data[0].b64_json",
                type = UniversalResponse.TYPE_JSON_BASE64,
                mime = "image/png",
            ),
            defaults = mapOf("model" to "dall-e-3"),
        ),
        UniversalProfile(
            name = SDWEBUI_NAME,
            description = "Stable Diffusion WebUI / Forge（A1111 兼容）：POST /sdapi/v1/txt2img，扁平数字参数，" +
                "base64 图片放在 images[0]。默认匿名，也可用 basic 认证。",
            baseUrl = "http://127.0.0.1:7860",
            auth = UniversalAuth(type = UniversalAuth.TYPE_NONE),
            request = UniversalRequest(
                method = "POST",
                path = "/sdapi/v1/txt2img",
                headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
                bodyTemplate = """{"prompt":"{{prompt}}","negative_prompt":"{{negative}}","width":{{width}},"height":{{height}},"steps":{{steps}},"cfg_scale":{{cfg}}}""",
            ),
            response = UniversalResponse(
                extract = "images[0]",
                type = UniversalResponse.TYPE_JSON_BASE64,
                mime = "image/png",
            ),
            defaults = mapOf("width" to "512", "height" to "512", "steps" to "20", "cfg" to "7"),
        ),
    )

    fun builtin(): List<UniversalProfile> = BUILTIN

    fun builtinNames(): List<String> = BUILTIN.map { it.name }

    /**
     * 解析主 app 注入的 profile 库。宽容接受三种形态：
     * 单个对象、对象数组、或者 {"profiles":[ ... ]}。
     * 空串 / 空白表示「没有自定义 profile」，返回空列表。
     */
    fun parseLibrary(json: String): List<UniversalProfile> {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return emptyList()
        val root = try {
            JsonValue.parse(trimmed)
        } catch (failure: JsonFormatException) {
            throw ImageFailure("profile 库不是合法 JSON：" + failure.message)
        }
        val items = when (root) {
            is JsonValue.Arr -> root.items
            is JsonValue.Obj -> root.fields["profiles"]?.let { profiles ->
                (profiles as? JsonValue.Arr)?.items
                    ?: throw ImageFailure("profile 库的 profiles 字段必须是数组")
            } ?: listOf(root)
            else -> throw ImageFailure("profile 库必须是 JSON 对象或数组")
        }
        val parsed = ArrayList<UniversalProfile>(items.size)
        items.forEachIndexed { index, item ->
            if (item === JsonValue.Null) return@forEachIndexed
            if (item.asObjectOrNull() == null) {
                throw ImageFailure("profile 库第 " + (index + 1) + " 项不是 JSON 对象")
            }
            parsed.add(UniversalProfile.fromJsonValue(item))
        }
        return parsed
    }

    /** 自定义 profile 与内置同名时覆盖内置，方便用户改完内置配置直接生效。 */
    fun merge(builtin: List<UniversalProfile>, custom: List<UniversalProfile>): List<UniversalProfile> {
        val merged = LinkedHashMap<String, UniversalProfile>()
        builtin.forEach { merged[it.name] = it }
        custom.forEach { merged[it.name] = it }
        return merged.values.toList()
    }
}
