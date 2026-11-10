package com.aiclassroom.app.exam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** 答案图片最长边上限：再大对模型没帮助，只会把存档和请求体一起撑爆。 */
internal const val EXAM_IMAGE_MAX_SIDE = 1600

internal fun examImagesDir(context: Context): File = File(context.filesDir, "images").apply {
    if (!isDirectory) mkdirs()
}

/** 系统相机落盘目标：photo_<时间戳>.jpg（FileProvider 只暴露这个目录）。 */
internal fun newExamPhotoFile(context: Context): File =
    File(examImagesDir(context), "photo_" + System.currentTimeMillis() + ".jpg")

/**
 * 就地压缩 / 转正：相机拍的和相册选的都过一遍这里。
 * 返回 false 表示这张图读不出来或全是空白，调用方应提示用户重试。
 */
internal fun normalizeExamImage(file: File, maxSide: Int = EXAM_IMAGE_MAX_SIDE): Boolean {
    if (!file.isFile || file.length() <= 0L) return false
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return false
    val longest = max(decoded.width, decoded.height)
    val scaled = if (longest > maxSide) {
        val ratio = maxSide.toFloat() / longest.toFloat()
        Bitmap.createScaledBitmap(decoded, (decoded.width * ratio).toInt().coerceAtLeast(1), (decoded.height * ratio).toInt().coerceAtLeast(1), true)
            .also { if (it !== decoded) decoded.recycle() }
    } else decoded
    // 相机常把方向写进 EXIF，像素没转正：模型会把横躺的字当乱码，这里先转回来再落盘。
    val upright = rotateByExif(file, scaled)
    val jpeg = file.extension.lowercase() in setOf("jpg", "jpeg")
    val format = if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
    val ok = runCatching {
        FileOutputStream(file).use { out -> upright.compress(format, 88, out) }
    }.isSuccess
    if (upright !== scaled) upright.recycle()
    scaled.recycle()
    return ok && file.length() > 0L
}

private fun rotateByExif(file: File, bitmap: Bitmap): Bitmap {
    if (file.extension.lowercase() !in setOf("jpg", "jpeg")) return bitmap
    val degrees = runCatching {
        when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
    if (degrees == 0f) return bitmap
    return runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
    }.getOrDefault(bitmap)
}

/** 相册选择：SAF 授权重启即失效，所以必须复制进 filesDir 而不是只存 uri。 */
internal fun importExamImage(context: Context, uri: Uri): File? = runCatching {
    val source = context.contentResolver.openInputStream(uri) ?: return@runCatching null
    val bytes = source.use { it.readBytes() }
    if (bytes.isEmpty()) return@runCatching null
    val extension = when (context.contentResolver.getType(uri)?.lowercase()) {
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        else -> "png"
    }
    // 先按原扩展名落一份临时文件，缩压在原文件上做；webp 统一转成 png 便于统一处理。
    val targetExtension = if (extension == "webp") "png" else extension
    val target = File(examImagesDir(context), "photo_" + System.currentTimeMillis() + "." + targetExtension)
    target.outputStream().use { it.write(bytes) }
    if (!normalizeExamImage(target)) {
        target.delete()
        null
    } else {
        target
    }
}.getOrNull()

internal fun deleteExamImage(path: String) {
    runCatching { File(path).takeIf { it.isFile }?.delete() }
}

internal fun examImageExists(path: String): Boolean = path.isNotBlank() && runCatching { File(path).isFile }.getOrDefault(false)
