package com.aiclassroom.app.util

/**
 * 媒体与本地文件工具。
 *
 * - mediaJson / traceArtworkPath：把 NowPlaying 的快照转成插件能读的 JSON，并回填封面落盘路径
 * - displayNameOf / extensionOf：SAF Uri 的展示名与扩展名
 * - saveUserImage：把用户选的图片复制进应用私有目录
 * - imageDataUrl：本地图片转 data URL（视觉模型要 base64）
 */

import android.content.Context
import java.util.Base64
import android.net.Uri
import android.provider.OpenableColumns
import com.aiclassroom.app.MAX_ATTACHED_IMAGE_BYTES
import com.aiclassroom.app.imagePathOf
import com.aiclassroom.app.plugin.MediaArtworkCache
import com.aiclassroom.app.media.NowPlaying
import org.json.JSONObject
import java.io.File

internal fun displayNameOf(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
    }
}.getOrNull()?.takeIf { it.isNotBlank() }
    ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    ?: "plugin.zip"

internal fun mediaJson(context: Context, track: NowPlaying.Track?, cache: MediaArtworkCache): String {
    val obj = JSONObject()
    if (track == null) {
        obj.put("playing", false)
        return obj.toString()
    }
    obj.put("playing", track.isPlaying)
    obj.put("title", track.title)
    obj.put("artist", track.artist)
    obj.put("album", track.album)
    obj.put("durationMs", track.durationMs)
    obj.put("positionMs", track.positionMs)
    obj.put("packageName", track.packageName)
    traceArtworkPath(obj, track, cache)
    return obj.toString()
}

internal fun traceArtworkPath(obj: JSONObject, track: NowPlaying.Track, cache: MediaArtworkCache) {
    val path = cache.existingPath()
    if (path != null && cache.matches(MediaArtworkCache.keyOf(track.packageName, track.title, track.album))) {
        obj.put("artworkPath", path)
    }
}

internal fun extensionOf(context: Context, uri: Uri): String? {
    val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    val fromMime = when (mime) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/jpeg" -> "jpg"
        else -> null
    }
    if (fromMime != null) return fromMime
    return displayNameOf(context, uri)?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotBlank() }
}

internal fun saveUserImage(context: Context, uri: Uri): File? = runCatching {
    val directory = File(context.filesDir, "images")
    if (!directory.isDirectory && !directory.mkdirs()) return@runCatching null
    val extension = when (context.contentResolver.getType(uri)?.lowercase()) {
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "png"
    }
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
    if (bytes.isEmpty()) return@runCatching null
    val target = File(directory, "user_image_" + System.currentTimeMillis() + "." + extension)
    target.outputStream().use { it.write(bytes) }
    target
}.getOrNull()

internal fun imageDataUrl(entry: String): String? {
    val path = imagePathOf(entry)
    if (path.isBlank()) return null
    return runCatching {
        val file = File(path)
        if (!file.isFile || file.length() <= 0L || file.length() > MAX_ATTACHED_IMAGE_BYTES) return@runCatching null
        val bytes = file.readBytes()
        val mime = when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/png"
        }
        "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes)
    }.getOrNull()
}
