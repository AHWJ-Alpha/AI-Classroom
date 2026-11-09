package com.aiclassroom.app.video

import java.io.File

/**
 * 视频路径的准入判定：只放行「公共媒体目录」和「本应用私有目录」。
 *
 * 刻意不做 root 越权，也不尝试读别的 App 的私有目录（/data/data/<别的包>）：
 * 那条路在真机上要么直接 EACCES，要么需要用户从没同意过的权限，写进工具里只会变成
 * 一个「看起来能用、实际永远失败」的功能。拒绝时把原因说清楚，模型才能转告用户换个路径。
 */
object VideoPathGuard {

    /** Android 上公共存储的标准挂载点（/sdcard 通常是指向 /storage/emulated/0 的软链）。 */
    private val SHARED_ROOTS = listOf("/sdcard", "/storage", "/mnt/sdcard", "/mnt/user/0")

    /** 文件大小上限：超过直接拒绝，别让一次抽帧把设备拖死。 */
    const val MAX_FILE_BYTES: Long = 500L * 1024L * 1024L

    /** 时长上限：10 分钟。超过就建议用 startMs/endMs 只取片段。 */
    const val MAX_DURATION_MS: Long = 10L * 60L * 1000L

    fun validate(rawPath: String, privateRoots: List<String>): Result<File> {
        val path = rawPath.trim()
        if (path.isEmpty()) return failure("path 不能为空。")
        if (path.contains("://")) {
            return failure(
                "只支持设备本地的视频文件（例如 /sdcard/Movies/demo.mp4）。" +
                    "网页里的流媒体（http / HLS / m3u8 / rtsp）没法按帧解码，本工具也不会去下载它；" +
                    "请先把视频保存到设备上，再把本地路径传进来。"
            )
        }
        val file = File(path)
        if (!file.isAbsolute) return failure("path 必须是绝对路径，收到：" + path)

        val canonical = canonicalOf(file)
        val allowed = (SHARED_ROOTS + privateRoots).any { root -> isUnder(canonical, canonicalOf(File(root))) }
        if (!allowed) {
            return failure(
                "不允许访问 " + canonical + "：只放行公共媒体目录（/sdcard、/storage）与本应用私有目录。" +
                    "其他应用的私有目录一律拒绝（本工具不做 root 越权）。"
            )
        }
        if (!file.exists()) return failure("文件不存在：" + canonical)
        if (file.isDirectory) return failure("路径是目录，不是视频文件：" + canonical)
        if (!file.isFile) return failure("不是普通文件：" + canonical)
        if (!file.canRead()) {
            return failure(
                "文件不可读：" + canonical + "。分区存储下应用只能直接读自己的目录，" +
                    "公共目录里的视频通常需要先授予媒体读取权限，或者把文件放到应用可读的位置。"
            )
        }
        val size = file.length()
        if (size > MAX_FILE_BYTES) {
            return failure(
                "文件过大：" + (size / 1024 / 1024) + "MB，超过 500MB 上限。" +
                    "请改用短视频，或者先用系统工具把要分析的那一段剪出来。"
            )
        }
        if (size == 0L) return failure("文件是空的：" + canonical)
        return Result.success(file)
    }

    private fun isUnder(path: String, root: String): Boolean {
        val cleanRoot = root.trimEnd('/')
        if (cleanRoot.isEmpty()) return false
        return path == cleanRoot || path.startsWith(cleanRoot + "/")
    }

    private fun canonicalOf(file: File): String = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)

    private fun failure(message: String): Result<File> = Result.failure(VideoFrameException(message))
}

/** 视频链路上所有「能说人话」的失败都用它，消息会原样回填给模型。 */
class VideoFrameException(message: String) : Exception(message)
