package com.aiclassroom.feature.exam.tool.image

import java.net.URLEncoder
import java.util.Base64

// 统一的认证构造器。三个 provider 都只调这里，不再各自判断 apiKey 长什么样：
// 一次请求 = appendQueryAuth(url) + buildAuth(config)，请求头与 URL 各管一半。

/** 按 [ImageConfig.authType] 生成请求头。 */
internal fun buildAuth(config: ImageConfig, headerOnly: Boolean = false): Map<String, String> {
    val key = config.apiKey.trim()
    val type = resolveAuthType(config.authType, headerOnly)
    // query 认证只动 URL（见 appendQueryAuth），请求头侧永远是空的。
    if (type == ImageConfig.AUTH_TYPE_QUERY) return emptyMap()
    // 没填凭据就是匿名请求，不要拼出 "Bearer " 这种半截头。
    if (key.isEmpty()) return emptyMap()
    return when (type) {
        ImageConfig.AUTH_TYPE_NONE -> emptyMap()
        ImageConfig.AUTH_TYPE_BEARER -> mapOf("Authorization" to "Bearer " + key)
        ImageConfig.AUTH_TYPE_BASIC -> mapOf("Authorization" to basicValue(key))
        ImageConfig.AUTH_TYPE_HEADER -> {
            val header = config.authHeader.trim()
            if (header.isEmpty()) {
                // 明确报错，不静默退化成匿名请求。
                throw ImageFailure("authType=header 需要同时填写 authHeader（例如 X-Api-Key）")
            }
            mapOf(header to config.authPrefix + key)
        }
        else -> emptyMap()
    }
}

/**
 * authType = query 时把凭据拼到 URL 上，参数名取 [ImageConfig.authHeader]（留空用 token）。
 * 其它认证方式、或者没填凭据时原样返回。
 */
internal fun appendQueryAuth(url: String, config: ImageConfig): String {
    if (resolveAuthType(config.authType, headerOnly = true) != ImageConfig.AUTH_TYPE_QUERY) return url
    val key = config.apiKey.trim()
    if (key.isEmpty()) return url
    val name = config.authHeader.trim().ifEmpty { DEFAULT_QUERY_PARAM }
    val value = encodeQueryValue(config.authPrefix + key)
    val fragment = url.indexOf('#')
    val head = if (fragment >= 0) url.substring(0, fragment) else url
    val tail = if (fragment >= 0) url.substring(fragment) else ""
    val separator = when {
        !head.contains('?') -> "?"
        head.endsWith("?") || head.endsWith("&") -> ""
        else -> "&"
    }
    return head + separator + encodeQueryValue(name) + "=" + value + tail
}

/** query 认证的参数名默认值：token。 */
private const val DEFAULT_QUERY_PARAM: String = "token"

/**
 * 归一化 authType。空串按 none 处理（旧配置没这个字段，等价于「不额外认证」会破坏 bearer 默认，
 * 所以这里只对显式空串生效，默认值本身是 bearer）。
 *
 * headerOnly = true 表示调用方只关心请求头形态（连接测试 / UI 预览）：无法识别的 authType 不抛错。
 */
private fun resolveAuthType(raw: String, headerOnly: Boolean): String {
    val type = raw.trim().lowercase()
    return when (type) {
        "" -> ImageConfig.AUTH_TYPE_NONE
        ImageConfig.AUTH_TYPE_NONE,
        ImageConfig.AUTH_TYPE_BEARER,
        ImageConfig.AUTH_TYPE_HEADER,
        ImageConfig.AUTH_TYPE_BASIC,
        ImageConfig.AUTH_TYPE_QUERY,
        -> type
        else -> if (headerOnly) {
            ImageConfig.AUTH_TYPE_NONE
        } else {
            throw ImageFailure(
                "不支持的认证方式 authType「" + raw + "」，可选：none / bearer / header / basic / query"
            )
        }
    }
}

/** Basic：凭据写成 user:pass（A1111 的 --api-auth 就是这个形态，对应 authType=basic）。 */
private fun basicValue(key: String): String =
    "Basic " + Base64.getEncoder().encodeToString(key.toByteArray(Charsets.UTF_8))

private fun encodeQueryValue(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")
