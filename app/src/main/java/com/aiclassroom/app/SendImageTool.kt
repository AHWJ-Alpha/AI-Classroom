package com.aiclassroom.app

import android.graphics.BitmapFactory
import android.util.Log
import com.aiclassroom.feature.exam.tool.ToolHandler
import com.aiclassroom.feature.exam.tool.ToolResult
import org.json.JSONObject
import java.io.File

/** 一轮对话最多能附加的图片张数；超出的 send_image 直接回错，不会悄悄丢掉。 */
internal const val MAX_ATTACHED_IMAGES = 4

/** 单张图片的体积上限（20MB）：再大就不往界面和请求体里塞了。 */
internal const val MAX_ATTACHED_IMAGE_BYTES = 20L * 1024 * 1024

/**
 * 图片条目 = 本地路径 [+ 分隔符 + caption]。
 * ChatMessage.images 只存字符串，caption（图片下方说明）跟着条目一起存，
 * 这样一条消息的图片和它的说明不会在存档里错位。分隔符用控制字符 U+0001，
 * 文件路径里不会出现它。
 */
internal const val IMAGE_CAPTION_SEPARATOR = '\u0001'

internal fun imageEntry(path: String, caption: String): String =
    if (caption.isBlank()) path else path + IMAGE_CAPTION_SEPARATOR + caption

internal fun imagePathOf(entry: String): String = entry.substringBefore(IMAGE_CAPTION_SEPARATOR)

internal fun imageCaptionOf(entry: String): String =
    if (entry.contains(IMAGE_CAPTION_SEPARATOR)) entry.substringAfter(IMAGE_CAPTION_SEPARATOR) else ""

/**
 * AI 在本轮对话里通过 send_image 附加的图片。
 * sendMessage 在开新的一轮之前 clear()，这一轮结束后 drain() 取走并挂到那条 assistant 消息上。
 * 同一时刻只允许一轮回复在跑（isLoading 闸门），所以一个实例就够，不必按会话区分。
 */
internal class TurnImageBuffer {

    private val entries = mutableListOf<String>()

    fun clear() {
        synchronized(this) { entries.clear() }
    }

    /** 满 [MAX_ATTACHED_IMAGES] 张返回 false，由调用方回错给模型。 */
    fun add(entry: String): Boolean = synchronized(this) {
        if (entries.size >= MAX_ATTACHED_IMAGES) return false
        entries.add(entry)
        true
    }

    fun size(): Int = synchronized(this) { entries.size }

    /**
     * 取走本轮攒下的图片。
     *
     * [existingImages] 是目标消息上已有的图：正常情况下它一定是空的（新加的那条 assistant 消息刚开始是空的），
     * 非空说明攒的图串到了别的消息上（例如将来分支课堂也调 send_image，两条回复同时往里写）。
     * 那种情况没法自动判定该归谁，所以不静默覆盖——记一条日志把问题暴露出来，图仍然附上。
     */
    fun drain(existingImages: List<String> = emptyList()): List<String> = synchronized(this) {
        val copy = entries.toList()
        entries.clear()
        if (copy.isNotEmpty() && existingImages.isNotEmpty()) {
            Log.w(TAG, "待附加图片与目标消息已有图片冲突：本轮 " + copy.size + " 张，消息已有 " + existingImages.size + " 张（可能有多轮回复同时在写）")
        }
        copy
    }

    private companion object {
        const val TAG = "TurnImageBuffer"
    }
}

/**
 * send_image：把一张已经存在本机的图片附加到 AI 当前这条回复上。
 *
 * 工具本身不生成图片，只做三件事——校验文件（存在 / 不超过 20MB / 真能解码成图片）、
 * 记进本轮待附加列表、回填 {"ok":true,"path":..} 给模型。
 * 权限：由「设置 → 插件 → 生图」总开关约束（gateTools("send_image", ...) 在注册时套上）。
 */
internal class SendImageTool(
    private val buffer: TurnImageBuffer,
    private val filesDir: () -> File,
) : ToolHandler {

    override val name: String = "send_image"

    override val description: String =
        "把一张已存在的本机图片附加到本轮回复里，用户会直接在聊天界面看到这张图。" +
            "path 必填，是图片文件的绝对路径（例如刚用 generate_image 生成的 path，或 universal_call 保存下来的 path），" +
            "也可以写 filesDir 相对路径（如 images/xxx.png）。caption 可选，是显示在图片下方的说明文字。" +
            "一轮最多附加 " + MAX_ATTACHED_IMAGES + " 张，图片超过 20MB 会被拒绝。" +
            "只调用这个工具不会产生文字回复，需要说明时请在同一轮正文里写出来。"

    override val parametersJson: String = """
        {
          "type": "object",
          "properties": {
            "path": {
              "type": "string",
              "description": "本机图片文件的绝对路径，或相对应用私有目录的路径（如 images/generate_image_20250101_120000_000.png）。"
            },
            "caption": {
              "type": "string",
              "description": "可选：显示在图片下方的说明文字，一句话即可，最多 300 字。"
            }
          },
          "required": ["path"],
          "additionalProperties": false
        }
    """.trimIndent()

    override suspend fun execute(argumentsJson: String): ToolResult {
        val arguments = try {
            JSONObject(argumentsJson.ifBlank { "{}" })
        } catch (failure: Throwable) {
            return ToolResult.Error("send_image 的参数不是合法 JSON：" + (failure.message ?: "解析失败"))
        }
        val rawPath = arguments.text("path")
            ?: return ToolResult.Error("send_image 需要 path 参数：本机图片文件的绝对路径，例如 generate_image 返回的 path。")
        val file = resolve(rawPath)
            ?: return ToolResult.Error("找不到图片文件：" + rawPath + "；请传 generate_image 等工具返回的真实 path。")
        if (!file.isFile) return ToolResult.Error("不是文件或不存在：" + file.absolutePath)
        val size = file.length()
        if (size <= 0L) return ToolResult.Error("图片文件是空的：" + file.name)
        if (size > MAX_ATTACHED_IMAGE_BYTES) {
            return ToolResult.Error("图片太大（" + (size / 1024 / 1024) + "MB），上限 20MB，这张没有发出去。")
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return ToolResult.Error("这个文件不是可识别的图片：" + file.name)
        }
        val caption = arguments.text("caption").orEmpty().take(300)
        if (!buffer.add(imageEntry(file.absolutePath, caption))) {
            return ToolResult.Error("本轮最多附加 " + MAX_ATTACHED_IMAGES + " 张图片，这张没有发出去。")
        }
        return ToolResult.Ok(
            JSONObject()
                .put("ok", true)
                .put("path", file.absolutePath)
                .put("width", bounds.outWidth)
                .put("height", bounds.outHeight)
                .put("count", buffer.size())
                .toString()
        )
    }

    /** 绝对路径直接用；相对路径按应用私有目录（filesDir）展开。 */
    private fun resolve(rawPath: String): File? {
        val trimmed = rawPath.trim()
        if (trimmed.isEmpty()) return null
        val direct = File(trimmed)
        if (direct.isAbsolute) return direct
        return File(filesDir(), trimmed)
    }

    private fun JSONObject.text(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = optString(key, "").trim()
        return value.ifEmpty { null }
    }
}
