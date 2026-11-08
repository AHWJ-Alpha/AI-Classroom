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
    /**
     * 认证方式：none / bearer / header / basic / query。
     * 默认 bearer 保持旧行为；真正的请求头 / query 拼装见 ImageAuth.kt 的 buildAuth()。
     */
    val authType: String = AUTH_TYPE_BEARER,
    /** authType = header 时的请求头名，例如 X-Api-Key；authType = query 时作为 query 参数名（默认 token）。 */
    val authHeader: String = "",
    /** 认证值前缀，例如 "" 或 "Bearer "（authType = bearer 时忽略，固定用 Bearer）。 */
    val authPrefix: String = "",
) {
    companion object {
        const val PROVIDER_OPENAI: String = "openai"
        const val PROVIDER_SDWEBUI: String = "sdwebui"
        const val PROVIDER_CUSTOM: String = "custom"

        const val AUTH_TYPE_NONE: String = "none"
        const val AUTH_TYPE_BEARER: String = "bearer"
        const val AUTH_TYPE_HEADER: String = "header"
        const val AUTH_TYPE_BASIC: String = "basic"
        const val AUTH_TYPE_QUERY: String = "query"
    }
}

/**
 * 保存能力：由主 app 实现，把图片字节写到应用目录 / 媒体库，
 * 返回可读路径或 content:// / file:// URI 字符串。
 */
interface ImageSink {
    suspend fun save(bytes: ByteArray, suggestedName: String, mime: String): Result<String>
}
