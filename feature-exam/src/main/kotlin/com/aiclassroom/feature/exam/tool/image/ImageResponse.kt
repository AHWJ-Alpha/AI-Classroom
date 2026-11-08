package com.aiclassroom.feature.exam.tool.image

import com.aiclassroom.feature.exam.tool.exam.JsonFormatException
import com.aiclassroom.feature.exam.tool.exam.JsonValue
import java.util.Base64

internal class ImagePayload(val bytes: ByteArray, val mime: String)

/**
 * 各家生图接口的响应形状不一致，这里统一收敛成图片字节：
 * 优先取 base64 字段（b64_json / base64 / images[0] ...），
 * 没有 base64 就取 url 再下载一次（同样受 20MB 与超时约束）。
 */
internal object ImageResponse {

    private const val SNIPPET_LIMIT: Int = 300

    private val DATA_URI_PREFIX: Regex = Regex("^data:[^,]*,")

    private val BASE64_PATHS: List<List<Any>> = listOf(
        listOf("data", 0, "b64_json"),
        listOf("images", 0),
        listOf("b64_json"),
        listOf("base64"),
        listOf("image"),
        listOf("data", 0, "base64"),
        listOf("images", 0, "b64_json"),
        listOf("data"),
    )

    private val URL_PATHS: List<List<Any>> = listOf(
        listOf("data", 0, "url"),
        listOf("url"),
        listOf("images", 0, "url"),
        listOf("data", 0, "image_url"),
    )

    fun resolve(responseText: String, timeoutSeconds: Int, label: String): ImagePayload {
        val text = responseText.trim()
        if (text.isEmpty()) throw ImageFailure("$label 返回了空响应体")
        val root = try {
            JsonValue.parse(text)
        } catch (failure: JsonFormatException) {
            throw ImageFailure("$label 的响应不是合法 JSON：${snippet(text)}")
        }
        firstString(root, BASE64_PATHS)?.let { return payload(decodeBase64(it, label), label) }
        firstString(root, URL_PATHS)?.let { url ->
            return payload(ImageHttp.getImageBytes(url.trim(), timeoutSeconds), "$label 的图片地址")
        }
        throw ImageFailure("$label 的响应里既没有 base64 图片，也没有可下载的 url：${snippet(text)}")
    }

    private fun payload(bytes: ByteArray, label: String): ImagePayload {
        if (bytes.isEmpty()) throw ImageFailure("$label 返回的图片字节为空")
        if (bytes.size > MAX_IMAGE_BYTES) {
            throw ImageFailure(
                "$label 返回的图片 ${bytes.size / (1024 * 1024)}MB 超过上限 ${MAX_IMAGE_BYTES / (1024 * 1024)}MB"
            )
        }
        return ImagePayload(bytes, sniffMime(bytes))
    }

    private fun decodeBase64(raw: String, label: String): ByteArray {
        val body = raw.replace(DATA_URI_PREFIX, "").filterNot { it.isWhitespace() }
        if (body.isEmpty()) throw ImageFailure("$label 返回的 base64 是空串")
        try {
            return Base64.getDecoder().decode(body)
        } catch (standard: IllegalArgumentException) {
            try {
                return Base64.getUrlDecoder().decode(body)
            } catch (urlSafe: IllegalArgumentException) {
                throw ImageFailure("$label 返回的 base64 无法解码：${snippet(body)}")
            }
        }
    }

    private fun firstString(root: JsonValue, paths: List<List<Any>>): String? {
        for (path in paths) {
            val value = navigate(root, path) ?: continue
            when (value) {
                is JsonValue.Str -> if (value.value.isNotBlank()) return value.value
                is JsonValue.Obj -> {
                    val nested = value.fields["b64_json"] ?: value.fields["base64"] ?: value.fields["url"]
                    if (nested is JsonValue.Str && nested.value.isNotBlank()) return nested.value
                }
                else -> Unit
            }
        }
        return null
    }

    private fun navigate(root: JsonValue, path: List<Any>): JsonValue? {
        var current: JsonValue = root
        for (step in path) {
            current = when (step) {
                is String -> (current as? JsonValue.Obj)?.fields?.get(step) ?: return null
                is Int -> (current as? JsonValue.Arr)?.items?.getOrNull(step) ?: return null
                else -> return null
            }
        }
        return current
    }

    private fun sniffMime(bytes: ByteArray): String {
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            return "image/png"
        }
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return "image/jpeg"
        }
        if (bytes.size >= 12 &&
            bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
        ) {
            return "image/webp"
        }
        if (bytes.size >= 4 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte()
        ) {
            return "image/gif"
        }
        return "image/png"
    }

    private fun snippet(text: String): String {
        val collapsed = text.replace(Regex("\\s+"), " ").trim()
        return if (collapsed.length <= SNIPPET_LIMIT) collapsed else collapsed.take(SNIPPET_LIMIT) + "…（已截断）"
    }
}
