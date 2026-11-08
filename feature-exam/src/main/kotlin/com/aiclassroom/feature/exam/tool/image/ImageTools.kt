package com.aiclassroom.feature.exam.tool.image

import com.aiclassroom.feature.exam.tool.ToolHandler
import com.aiclassroom.feature.exam.tool.ToolRegistry
import com.aiclassroom.feature.exam.tool.ToolResult
import com.aiclassroom.feature.exam.tool.exam.JsonFormatException
import com.aiclassroom.feature.exam.tool.exam.JsonValue
import com.aiclassroom.feature.exam.tool.exam.asIntOrNull
import com.aiclassroom.feature.exam.tool.exam.asObjectOrNull
import com.aiclassroom.feature.exam.tool.exam.asStringOrNull
import com.aiclassroom.feature.exam.tool.exam.jsonObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CancellationException

private const val MAX_PROMPT_LENGTH = 8000
private const val MIN_SIDE = 16
private const val MAX_SIDE = 4096
private const val MAX_STEPS = 150
private const val MAX_CFG_SCALE = 30.0

/**
 * 生图工具集。配置与保存能力全部由主 app 注入（纯 JVM 模块没有 Context）：
 * registerImageTools(registry, { readImageConfig() }, AndroidImageSink(context))
 *
 * execute 永不抛异常，失败一律 [ToolResult.Error]；协程取消照常向上传递。
 */
class ImageTools(
    private val config: () -> ImageConfig,
    private val sink: ImageSink,
) {

    /** 按注册顺序暴露给模型的工具。 */
    val handlers: List<ToolHandler> = listOf(GenerateImageTool())

    /** 阶段集成：把生图工具注册进同一个 [ToolRegistry]。 */
    fun register(registry: ToolRegistry): ImageTools {
        handlers.forEach(registry::register)
        return this
    }

    private inner class GenerateImageTool : ToolHandler {
        override val name: String = "generate_image"
        override val description: String =
            "根据你的提示词调用生图后端生成图片，并自动保存到本机（返回保存路径）。" +
                "prompt 必填，要写清主体、场景、风格、光线；本地 Stable Diffusion 后端还可以给 negativePrompt、" +
                "width / height、steps、cfgScale。生图较慢、图片较大，一次调用只生成一张，不要为了「多试几张」连续重复调用。"

        override val parametersJson: String = ImageToolSchemas.GENERATE_IMAGE

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val arguments = parseArguments(argumentsJson)
                val prompt = arguments.requiredPrompt()
                val request = ImageRequest(
                    prompt = prompt,
                    negativePrompt = arguments.optionalText("negativePrompt"),
                    width = arguments.int("width", MIN_SIDE, MAX_SIDE),
                    height = arguments.int("height", MIN_SIDE, MAX_SIDE),
                    steps = arguments.int("steps", 1, MAX_STEPS),
                    cfgScale = arguments.decimal("cfgScale", 0.0, MAX_CFG_SCALE),
                    size = null,
                    timeoutSeconds = arguments.int("timeoutSeconds", 1, MAX_TIMEOUT_SECONDS) ?: DEFAULT_TIMEOUT_SECONDS,
                )
                val currentConfig = config()
                val provider = ImageProviders.resolve(currentConfig.provider)
                val payload = provider.generate(currentConfig, request)
                val suggestedName = "generate_image_" + timestamp() + "." + extensionFor(payload.mime)
                val saved = sink.save(payload.bytes, suggestedName, payload.mime)
                val path = saved.getOrElse { error ->
                    throw ImageFailure(
                        "图片已生成（" + payload.bytes.size + " 字节）但保存失败：" +
                            (error.message ?: error::class.java.simpleName)
                    )
                }
                if (path.isBlank()) throw ImageFailure("图片保存后返回了空路径")
                ToolResult.Ok(
                    JsonValue.write(
                        jsonObject(
                            "ok" to JsonValue.Bool(true),
                            "path" to JsonValue.Str(path),
                            "bytes" to JsonValue.Num(payload.bytes.size.toDouble()),
                            "provider" to JsonValue.Str(provider.id),
                            "prompt" to JsonValue.Str(prompt),
                        )
                    )
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: ImageFailure) {
                ToolResult.Error(failure.message ?: "生图失败")
            } catch (throwable: Throwable) {
                ToolResult.Error("生图失败：" + (throwable.message ?: throwable::class.java.simpleName))
            }
        }
    }

    private fun parseArguments(argumentsJson: String): Map<String, JsonValue> {
        val text = argumentsJson.trim()
        if (text.isEmpty()) throw ImageFailure("generate_image 缺少参数")
        val root = try {
            JsonValue.parse(text)
        } catch (failure: JsonFormatException) {
            throw ImageFailure("generate_image 的参数不是合法 JSON：" + failure.message)
        }
        return root.asObjectOrNull() ?: throw ImageFailure("generate_image 的参数必须是 JSON 对象")
    }

    private fun Map<String, JsonValue>.requiredPrompt(): String {
        val prompt = optionalText("prompt")
            ?: throw ImageFailure("generate_image 需要非空的 prompt 参数")
        if (prompt.length > MAX_PROMPT_LENGTH) {
            throw ImageFailure("prompt 太长（" + prompt.length + " 字符），上限 " + MAX_PROMPT_LENGTH)
        }
        return prompt
    }

    /** 可选文本参数：null / 非字符串 / 空串都会得到明确结论，不静默忽略。 */
    private fun Map<String, JsonValue>.optionalText(name: String): String? {
        val raw = this[name] ?: return null
        if (raw === JsonValue.Null) return null
        val value = raw.asStringOrNull() ?: throw ImageFailure(name + " 必须是字符串")
        val trimmed = value.trim()
        if (trimmed.length > MAX_PROMPT_LENGTH) {
            throw ImageFailure(name + " 太长（" + trimmed.length + " 字符），上限 " + MAX_PROMPT_LENGTH)
        }
        return trimmed.ifEmpty { null }
    }

    private fun Map<String, JsonValue>.int(name: String, min: Int, max: Int): Int? {
        val raw = this[name] ?: return null
        if (raw === JsonValue.Null) return null
        val value = raw.asIntOrNull() ?: throw ImageFailure(name + " 必须是整数")
        if (value < min || value > max) {
            throw ImageFailure(name + " 必须在 " + min + "–" + max + " 之间，收到 " + value)
        }
        return value
    }

    private fun Map<String, JsonValue>.decimal(name: String, min: Double, max: Double): Double? {
        val raw = this[name] ?: return null
        if (raw === JsonValue.Null) return null
        val value = when (raw) {
            is JsonValue.Num -> raw.value
            is JsonValue.Str -> raw.value.trim().toDoubleOrNull()
            else -> null
        } ?: throw ImageFailure(name + " 必须是数字")
        if (!value.isFinite() || value < min || value > max) {
            throw ImageFailure(name + " 必须在 " + min + "–" + max + " 之间，收到 " + value)
        }
        return value
    }

    private fun timestamp(): String = LocalDateTime.now().format(NAME_STAMP)

    private fun extensionFor(mime: String): String = when (mime) {
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "png"
    }

    private companion object {
        val NAME_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")
    }
}

/** 主 app 集成入口：注册 generate_image（同名旧工具会被覆盖）。 */
fun registerImageTools(registry: ToolRegistry, config: () -> ImageConfig, sink: ImageSink) {
    ImageTools(config, sink).register(registry)
}
