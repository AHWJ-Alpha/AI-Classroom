package com.aiclassroom.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.aiclassroom.feature.exam.tool.image.ImageSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException

/**
 * [ImageSink] 的 Android 实现：图片永远先落到应用私有目录 filesDir/images（无需任何权限，
 * 分区存储下同样安全），再尽量往系统相册补一份——相册那条路纯加分，失败只记日志。
 *
 * 失败一律用 Result.failure 带上可读原因，交给 generate_image 原样回填给模型。
 */
class ImageSinkImpl(private val context: Context) : ImageSink {

    override suspend fun save(bytes: ByteArray, suggestedName: String, mime: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(write(bytes, suggestedName, mime))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                val reason = throwable.message?.takeIf { it.isNotBlank() }
                    ?: throwable.javaClass.simpleName
                Log.w(TAG, "保存图片失败：" + reason)
                Result.failure(IllegalStateException("保存图片失败：" + reason, throwable))
            }
        }

    private fun write(bytes: ByteArray, suggestedName: String, mime: String): String {
        if (bytes.isEmpty()) throw IOException("图片字节为空，没有可保存的内容")
        val directory = File(context.filesDir, DIRECTORY)
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("无法创建图片目录：" + directory.absolutePath)
        }
        val target = uniqueFile(directory, fileNameFor(suggestedName, mime))
        try {
            target.outputStream().use { it.write(bytes) }
        } catch (io: IOException) {
            target.delete()
            throw IOException("写入 " + target.name + " 失败：" + (io.message ?: io.javaClass.simpleName), io)
        }
        val written = target.length()
        if (written != bytes.size.toLong()) {
            target.delete()
            throw IOException("写入不完整：" + written + "/" + bytes.size + " 字节")
        }
        // 相册是加分项：任何异常都不影响已经落盘的私有文件。
        runCatching { addToGallery(target, mime) }
            .onFailure { Log.w(TAG, "写入相册失败（已保存到私有目录）：" + it.message) }
        return target.absolutePath
    }

    /** 文件名 = 清洗后的 suggestedName + 按 mime 推断的扩展名。 */
    private fun fileNameFor(suggestedName: String, mime: String): String {
        val extension = extensionFor(mime)
        val raw = suggestedName.substringAfterLast('/').substringAfterLast('\\')
        val base = raw.substringBeforeLast('.', raw)
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('.', '_')
        val stem = base.ifBlank { "image" }
        return stem + "." + extension
    }

    private fun extensionFor(mime: String): String = when (mime.trim().lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "png"
    }

    /** 同名追加 -1 / -2 …，绝不覆盖已有文件。 */
    private fun uniqueFile(directory: File, fileName: String): File {
        val candidate = File(directory, fileName)
        if (!candidate.exists()) return candidate
        val stem = fileName.substringBeforeLast('.')
        val extension = fileName.substringAfterLast('.', "")
        var index = 1
        while (true) {
            val next = File(directory, stem + "-" + index + "." + extension)
            if (!next.exists()) return next
            index++
            if (index > MAX_DUPLICATES) break
        }
        // 极端撞名（同名文件已经排到 MAX_DUPLICATES）：用时间戳兜底，保证不覆盖。
        return File(directory, stem + "-" + System.currentTimeMillis() + "." + extension)
    }

    /**
     * Android 10+ 走 MediaStore + RELATIVE_PATH，不需要任何存储权限；
     * 低版本要写公共目录必须先申请权限，索性只在 10+ 补相册。
     */
    private fun addToGallery(file: File, mime: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, mime.ifBlank { "image/png" })
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + ALBUM)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return
        try {
            val output = resolver.openOutputStream(uri)
                ?: throw IOException("无法打开相册写入流")
            output.use { sink -> file.inputStream().use { it.copyTo(sink) } }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (throwable: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw throwable
        }
    }

    private companion object {
        const val TAG: String = "ImageSink"
        const val DIRECTORY: String = "images"
        const val ALBUM: String = "AIClassroom"
        const val MAX_DUPLICATES: Int = 999
    }
}
