package com.aiclassroom.feature.exam.tool.image

import com.aiclassroom.feature.exam.tool.exam.JsonValue
import com.aiclassroom.feature.exam.tool.exam.jsonObject

/** 一次生图请求：工具参数 + 配置合成后的中间形态。 */
internal data class ImageRequest(
    val prompt: String,
    val negativePrompt: String?,
    val width: Int?,
    val height: Int?,
    val steps: Int?,
    val cfgScale: Double?,
    val size: String?,
    val timeoutSeconds: Int,
)

/**
 * 生图后端抽象。加一个新后端只需要再实现一个 [ImageProvider] 并在
 * [ImageProviders.resolve] 里挂个名字，工具与主 app 的集成代码都不用动。
 */
internal interface ImageProvider {
    /** 稳定的 provider 标识，会原样回填在 generate_image 的结果里。 */
    val id: String

    fun generate(config: ImageConfig, request: ImageRequest): ImagePayload
}

internal object ImageProviders {

    fun resolve(name: String): ImageProvider = when (name.trim().lowercase()) {
        "", ImageConfig.PROVIDER_OPENAI, "openai_images", "dall-e", "dalle" -> OpenAiImagesProvider
        ImageConfig.PROVIDER_SDWEBUI, "sd", "sd-webui", "a1111", "automatic1111", "forge", "stable-diffusion" ->
            SdWebUiProvider
        ImageConfig.PROVIDER_CUSTOM, "http", "custom-http" -> CustomHttpProvider
        else -> throw ImageFailure(
            "不支持的 provider「$name」，可选：openai（Images API）/ sdwebui（Stable Diffusion WebUI 兼容）/ custom（自定义 HTTP）"
        )
    }
}

internal object OpenAiImagesProvider : ImageProvider {
    override val id: String = ImageConfig.PROVIDER_OPENAI

    override fun generate(config: ImageConfig, request: ImageRequest): ImagePayload {
        val url = appendQueryAuth(joinUrl(config.baseUrl, "/images/generations"), config)
        val body = JsonValue.write(
            jsonObject(
                "model" to JsonValue.Str(config.model),
                "prompt" to JsonValue.Str(request.prompt),
                "size" to JsonValue.Str(request.size ?: config.size),
                "n" to JsonValue.Num(1.0),
                "response_format" to JsonValue.Str("b64_json"),
            )
        )
        val text = ImageHttp.postJson(url, body, request.timeoutSeconds, buildAuth(config))
        return ImageResponse.resolve(text, request.timeoutSeconds, "OpenAI Images（$url）")
    }
}

internal object SdWebUiProvider : ImageProvider {
    override val id: String = ImageConfig.PROVIDER_SDWEBUI

    override fun generate(config: ImageConfig, request: ImageRequest): ImagePayload {
        val url = appendQueryAuth(joinUrl(sdRoot(config.baseUrl), "/sdapi/v1/txt2img"), config)
        val fallback = parseSize(request.size ?: config.size)
        val width = request.width ?: fallback.first
        val height = request.height ?: fallback.second
        val steps = request.steps ?: config.steps
        val cfgScale = request.cfgScale ?: config.cfgScale
        val fields = ArrayList<Pair<String, JsonValue>>(7)
        fields.add("prompt" to JsonValue.Str(request.prompt))
        request.negativePrompt?.takeIf { it.isNotBlank() }?.let { fields.add("negative_prompt" to JsonValue.Str(it)) }
        fields.add("width" to JsonValue.Num(width.toDouble()))
        fields.add("height" to JsonValue.Num(height.toDouble()))
        fields.add("steps" to JsonValue.Num(steps.toDouble()))
        fields.add("cfg_scale" to JsonValue.Num(cfgScale))
        config.sampler.takeIf { it.isNotBlank() }?.let { fields.add("sampler_name" to JsonValue.Str(it)) }
        val text = ImageHttp.postJson(
            url,
            JsonValue.write(jsonObject(*fields.toTypedArray())),
            request.timeoutSeconds,
            buildAuth(config)
        )
        return ImageResponse.resolve(text, request.timeoutSeconds, "SD WebUI（$url）")
    }

    /**
     * 宽容处理地址：用户常把 http://127.0.0.1:7860/v1 甚至
     * /sdapi/v1/txt2img 整条粘进来，这里统一收回服务根地址。
     */
    private fun sdRoot(baseUrl: String): String {
        var root = baseUrl.trim().trimEnd('/')
        root = root.removeSuffix("/sdapi/v1/txt2img").removeSuffix("/sdapi/v1").removeSuffix("/v1")
        return root.trimEnd('/')
    }
}

/**
 * 兜底：用户自带 URL 与请求体模板。占位符本身不带引号，
 * 例如 {"prompt":"{{prompt}}","width":{{width}}}。
 */
internal object CustomHttpProvider : ImageProvider {
    override val id: String = ImageConfig.PROVIDER_CUSTOM

    private const val DEFAULT_TEMPLATE: String = """{"prompt":"{{prompt}}"}"""

    override fun generate(config: ImageConfig, request: ImageRequest): ImagePayload {
        val url = appendQueryAuth(config.customUrl.trim().ifEmpty { config.baseUrl.trim() }, config)
        if (url.isEmpty()) throw ImageFailure("自定义 provider 需要配置 customUrl 或 baseUrl")
        val template = config.customBodyTemplate.trim().ifEmpty { DEFAULT_TEMPLATE }
        val fallback = parseSize(request.size ?: config.size)
        val width = request.width ?: fallback.first
        val height = request.height ?: fallback.second
        val steps = request.steps ?: config.steps
        val cfgScale = request.cfgScale ?: config.cfgScale
        val body = template
            .replace("{{prompt}}", escape(request.prompt))
            .replace("{{negativePrompt}}", escape(request.negativePrompt.orEmpty()))
            .replace("{{width}}", width.toString())
            .replace("{{height}}", height.toString())
            .replace("{{steps}}", steps.toString())
            .replace("{{cfgScale}}", cfgScale.toString())
            .replace("{{size}}", escape(request.size ?: config.size))
            .replace("{{model}}", escape(config.model))
            .replace("{{apiKey}}", escape(config.apiKey))
        val text = ImageHttp.postJson(url, body, request.timeoutSeconds, buildAuth(config))
        return ImageResponse.resolve(text, request.timeoutSeconds, "自定义生图接口（$url）")
    }
}

/** 拼 URL：baseUrl 已经写成完整端点时原样使用，避免拼出重复路径。 */
internal fun joinUrl(baseUrl: String, path: String): String {
    val base = baseUrl.trim().trimEnd('/')
    if (base.isEmpty()) throw ImageFailure("生图 baseUrl 为空，请在生图设置里填写服务地址")
    return if (base.endsWith(path)) base else base + path
}

/** "1024x1024" → 1024 x 1024；解析不出来就按 512x512（SD 的默认习惯）。 */
internal fun parseSize(size: String): Pair<Int, Int> {
    val match = Regex("""^\s*(\d+)\s*[xX*]\s*(\d+)\s*$""").find(size.trim())
        ?: return 512 to 512
    val width = match.groupValues[1].toIntOrNull() ?: return 512 to 512
    val height = match.groupValues[2].toIntOrNull() ?: return 512 to 512
    if (width <= 0 || height <= 0) return 512 to 512
    return width to height
}

private fun escape(value: String): String {
    val quoted = JsonValue.write(JsonValue.Str(value))
    return quoted.substring(1, quoted.length - 1)
}
