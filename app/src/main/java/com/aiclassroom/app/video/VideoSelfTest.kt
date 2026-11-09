package com.aiclassroom.app.video

import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/**
 * 设备验收用的脚本化自测：adb 带 --ez video_selftest true 启动才会跑，正常使用走不到这里。
 *
 * 之所以让它走 [com.aiclassroom.feature.exam.tool.ToolRegistry.dispatch]——和线上完全同一条路径，
 * 所以跑通它能证明「注册 → 开关闸门 → 抽帧引擎 → 视觉分析」整条链是通的，而不是只证明了某个函数能用。
 * 结果写到 filesDir/video_selftest_result.txt，adb pull 出来就是证据。
 */
object VideoSelfTest {

    suspend fun run(
        videoPath: String,
        longVideoPath: String?,
        dispatch: suspend (toolName: String, argumentsJson: String) -> String,
        output: File,
        log: (String) -> Unit,
    ) {
        val report = StringBuilder()
        report.append("video_selftest\n")
        report.append("videoPath=").append(videoPath)
            .append(" exists=").append(File(videoPath).exists())
            .append(" size=").append(File(videoPath).length()).append("\n")

        val directory = File(videoPath).parentFile ?: File("/data/local/tmp")
        val notVideo = File(directory, "selftest_not_video.mp4")
        val tooLarge = File(directory, "selftest_over_500mb.mp4")
        runCatching { notVideo.writeBytes(ByteArray(8192) { (it * 31 % 251).toByte() }) }
        runCatching { RandomAccessFile(tooLarge, "rw").use { it.setLength(501L * 1024L * 1024L) } }

        val cases = ArrayList<Pair<String, String>>()
        // 公平对比：两边预算都是 12 帧、底层时间网格都是 500ms，只有选点策略不同。
        cases += "1) adaptive maxFrames=6 intervalMs=500" to args(videoPath, "adaptive", 6, 500L)
        cases += "2) uniform maxFrames=6 intervalMs=500" to args(videoPath, "uniform", 6, 500L)
        cases += "3) adaptive maxFrames=12 intervalMs=500" to args(videoPath, "adaptive", 12, 500L)
        cases += "4) uniform maxFrames=12 intervalMs=500" to args(videoPath, "uniform", 12, 500L)
        cases += "5) adaptive analyze=true maxFrames=8" to args(
            videoPath,
            "adaptive",
            8,
            1000L,
            analyze = true,
            question = "这个视频里画面发生了哪些变化？请按时间顺序说说你从每一帧看到了什么。",
        )
        cases += "6) error 文件不存在" to args("/sdcard/Movies/selftest_missing.mp4", "adaptive", 6, 1000L)
        cases += "7) error 不是视频" to args(notVideo.absolutePath, "adaptive", 6, 1000L)
        cases += "8) error 超过 500MB" to args(tooLarge.absolutePath, "adaptive", 6, 1000L)
        cases += "9) error 网页流媒体" to args("https://example.com/live/stream.m3u8", "adaptive", 6, 1000L)
        cases += "10) 公共目录 /sdcard 路径" to args("/sdcard/Movies/" + File(videoPath).name, "adaptive", 6, 1000L)
        if (longVideoPath != null) {
            cases += "11) error 整段超过 10 分钟" to args(longVideoPath, "adaptive", 6, 1000L)
            cases += "12) 长视频只取前 10 分钟（粗扫点数上限）" to args(
                longVideoPath,
                "adaptive",
                6,
                1000L,
                endMs = 600_000L,
            )
        }

        for ((label, arguments) in cases) {
            log("[selftest] " + label)
            report.append("\n===== ").append(label).append(" =====\n")
            report.append("args=").append(arguments).append("\n")
            val raw = runCatching { dispatch("video_frames", arguments) }
                .getOrElse { "DISPATCH_THREW " + (it.message ?: it.javaClass.simpleName) }
            report.append(describe(raw)).append("\n")
            log("[selftest] " + label + " -> " + raw.take(120))
        }

        runCatching { notVideo.delete() }
        runCatching { tooLarge.delete() }

        output.writeText(report.toString())
        log("[selftest] 结果已写入 " + output.absolutePath)
    }

    private fun args(
        path: String,
        mode: String,
        maxFrames: Int,
        intervalMs: Long,
        analyze: Boolean = false,
        question: String = "",
        endMs: Long = 0L,
    ): String = JSONObject()
        .put("path", path)
        .put("mode", mode)
        .put("maxFrames", maxFrames)
        .put("intervalMs", intervalMs)
        .put("analyze", analyze)
        .put("startMs", 0)
        .put("endMs", endMs)
        .apply { if (question.isNotBlank()) put("question", question) }
        .toString()

    /** 成功结果压成「一行摘要 + 每帧 t/change」的表格，失败原样保留错误文案。 */
    private fun describe(raw: String): String {
        if (!raw.startsWith("OK ")) return raw
        val json = runCatching { JSONObject(raw.removePrefix("OK ")) }.getOrNull() ?: return raw
        return buildString {
            append("ok=").append(json.optBoolean("ok"))
            append(" durationMs=").append(json.optLong("durationMs"))
            append(" startMs=").append(json.optLong("startMs"))
            append(" endMs=").append(json.optLong("endMs"))
            append(" sampledCount=").append(json.optInt("sampledCount"))
            append(" mode=").append(json.optString("mode"))
            append(" scanIntervalMs=").append(json.optLong("scanIntervalMs"))
            append(" elapsedMs=").append(json.optLong("elapsedMs"))
            append('\n')
            val frames = json.optJSONArray("frames")
            if (frames != null) {
                for (index in 0 until frames.length()) {
                    val frame = frames.optJSONObject(index) ?: continue
                    append("  frame ").append(index)
                        .append(": t=").append(frame.optLong("t")).append("ms")
                        .append(" change=").append(frame.optDouble("change"))
                        .append(" path=").append(frame.optString("path"))
                        .append('\n')
                }
            }
            if (json.has("analysis")) {
                append("analysisMode=").append(json.optString("analysisMode")).append('\n')
                append("analysis:\n").append(json.optString("analysis")).append('\n')
            }
            if (json.has("note")) append("note=").append(json.optString("note")).append('\n')
        }
    }
}
