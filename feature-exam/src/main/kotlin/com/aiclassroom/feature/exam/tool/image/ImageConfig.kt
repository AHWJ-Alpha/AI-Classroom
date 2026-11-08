package com.aiclassroom.feature.exam.tool.image

/**
 * 生图配置。[feature-exam] 是纯 JVM 模块，拿不到 Context，也没有 Settings，
 * 所以配置与保存能力都由主 app 在注册工具时注入。
 */
data class ImageConfig(
    val provider: String = PROVIDER_OPENAI,
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val model: String = "dall-e-3",
    val size: String = "1024x1024",
    val steps: Int = 20,
    val cfgScale: Double = 7.0,
    val sampler: String = "DPM++ 2M Karras",
    /** 仅 provider = custom 时使用；留空则回落到 [baseUrl]。 */
    val customUrl: String = "",
    /** 仅 provider = custom 时使用：请求体模板，支持 {{prompt}} 等占位符（占位符本身不带引号）。 */
    val customBodyTemplate: String = "",
) {
    companion object {
        const val PROVIDER_OPENAI: String = "openai"
        const val PROVIDER_SDWEBUI: String = "sdwebui"
        const val PROVIDER_CUSTOM: String = "custom"
    }
}

/**
 * 保存能力：由主 app 实现，把图片字节写到应用目录 / 媒体库，
 * 返回可读路径或 content:// / file:// URI 字符串。
 */
interface ImageSink {
    suspend fun save(bytes: ByteArray, suggestedName: String, mime: String): Result<String>
}
