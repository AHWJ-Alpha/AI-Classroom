package com.aiclassroom.app.video

import android.graphics.Bitmap
import com.aiclassroom.feature.exam.tool.ToolHandler
import com.aiclassroom.feature.exam.tool.ToolRegistry
import com.aiclassroom.feature.exam.tool.ToolResult
import com.aiclassroom.app.video.VideoFrameExtractor.Companion.DEFAULT_MAX_FRAMES
import com.aiclassroom.app.video.VideoFrameExtractor.Companion.DEFAULT_MAX_WIDTH
import com.aiclassroom.app.video.VideoFrameExtractor.Companion.DEFAULT_SCAN_INTERVAL_MS
import com.aiclassroom.app.video.VideoFrameExtractor.Companion.DEFAULT_UNIFORM_INTERVAL_MS
import com.aiclassroom.app.video.VideoFrameExtractor.Companion.MAX_DURATION_MS
import com.aiclassroom.app.video.VideoFrameExtractor.Companion.MAX_FRAMES_LIMIT
import com.aiclassroom.app.video.VideoFrameExtractor.Companion.MAX_FRAME_WIDTH
import com.aiclassroom.app.video.VideoFrameExtractor.Companion.MIN_FRAME_WIDTH
import com.aiclassroom.app.video.VideoFrameExtractor.Mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.CancellationException
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * video 工具集的运行时依赖，全部由主 app 注入（本包不需要认识 Compose / Activity）：
 * 抽帧靠 [VideoFrameExtractor]，落盘借已有的 ImageSink，分析复用 callVisionWithFallback。
 */
class VideoToolDeps(
    val extractor: VideoFrameExtractor,
    /** 把 PNG 存到 filesDir/images，返回绝对路径。失败给 Result.failure。 */
    val saveFrame: suspend (ByteArray, String) -> Result<String>,
    /** 一次性交给视觉模型若干张图（按顺序），失败给 Result.failure 以便退化为逐帧。 */
    val analyze: suspend (system: String, prompt: String, imageDataUrls: List<String>) -> Result<String>,
    val log: (String) -> Unit,
)

/**
 * video_frames：从设备本地视频里按自适应密度抽关键帧，可选交给视觉模型理解。
 *
 * 为什么是自适应而不是定时抽：均匀抽帧对教程视频几乎必错——「点开菜单」只占 1 秒，
 * 均匀撒点大概率跳过它；反过来讲 PPT 的 30 秒静止画面会被抽成一堆一模一样的废帧。
 * 而且每一帧都是一次视觉模型调用，帧数直接等于钱，所以「少而准」是核心。
 */
class VideoFramesTool(private val deps: VideoToolDeps) : ToolHandler {

    override val name: String = "video_frames"

    override val description: String =
        "从设备本地视频文件里抽关键帧（默认自适应：画面变化大的地方多抽，长时间静止的段落只留代表帧），" +
            "可选把这些帧交给视觉模型，用来回答「这个视频讲了什么」「教程的第二步是什么」。" +
            "只支持设备本地文件：网页流媒体（HLS / m3u8 / rtsp）无法按帧解码，会直接报错，不要试图让它下载网址。" +
            "限制：文件 ≤ 500MB、单次分析的片段 ≤ 10 分钟；超过就先用 startMs / endMs 只取一段。" +
            "每一帧都会产生一次识图开销，maxFrames 越大越贵越慢：只想大致看看就 6-8 帧，" +
            "要看清操作细节再加到 12-16。返回里每帧带 change（与上一帧的画面变化比，0..1），" +
            "数值大的时间点通常就是「有动作发生」的位置，" +
            "你可以据此告诉用户「第 3.2 秒发生了什么」，或者用 frameAt 的语义再取一次单帧。"

    override val parametersJson: String = """
        {
          "type": "object",
          "properties": {
            "path": {"type": "string", "description": "设备本地可读的视频绝对路径，例如 /sdcard/Movies/demo.mp4。"},
            "mode": {"type": "string", "enum": ["adaptive", "uniform"], "description": "adaptive＝按画面变化自适应选帧（默认，推荐）；uniform＝固定间隔均匀抽帧，用于对比。"},
            "maxFrames": {"type": "integer", "description": "抽帧总数上限，默认 12，范围 1-24。"},
            "intervalMs": {"type": "integer", "description": "uniform 模式的固定间隔（毫秒，默认 2000）；adaptive 模式下作为粗扫间隔的兜底（默认 1000）。"},
            "startMs": {"type": "integer", "description": "只分析从这一毫秒开始的片段，默认 0。"},
            "endMs": {"type": "integer", "description": "片段结束的毫秒时间点，0（默认）表示到视频结尾。"},
            "maxWidth": {"type": "integer", "description": "每帧最长边像素，默认 720，范围 96-1280。视觉模型不需要原始分辨率，调大只会更慢更贵。"},
            "analyze": {"type": "boolean", "description": "是否把所有关键帧一次性交给视觉模型分析，默认 false。"},
            "question": {"type": "string", "description": "analyze=true 时的问题，例如「这个视频教程里用户是怎么完成登录的」。不填则让模型描述视频内容。"}
          },
          "required": ["path"]
        }
    """.trimIndent()

    override suspend fun execute(argumentsJson: String): ToolResult {
        val args = runCatching { JSONObject(argumentsJson.ifBlank { "{}" }) }.getOrNull()
            ?: return fail("参数不是合法 JSON。")
        val path = args.optString("path").trim()
        if (path.isEmpty()) return fail("缺少 path。")

        val modeName = args.optString("mode").trim().lowercase().ifBlank { MODE_ADAPTIVE }
        val mode = when (modeName) {
            MODE_ADAPTIVE -> Mode.ADAPTIVE
            MODE_UNIFORM -> Mode.UNIFORM
            else -> return fail("mode 只能是 adaptive 或 uniform，收到：" + modeName)
        }
        val maxFrames = args.optInt("maxFrames", DEFAULT_MAX_FRAMES).coerceIn(1, MAX_FRAMES_LIMIT)
        val requestedInterval = args.optLong("intervalMs", 0L)
        val interval = when {
            requestedInterval > 0L -> requestedInterval
            mode == Mode.UNIFORM -> DEFAULT_UNIFORM_INTERVAL_MS
            else -> DEFAULT_SCAN_INTERVAL_MS
        }
        val start = args.optLong("startMs", 0L).coerceAtLeast(0L)
        val requestedEnd = args.optLong("endMs", 0L)
        val maxWidth = args.optInt("maxWidth", DEFAULT_MAX_WIDTH).coerceIn(MIN_FRAME_WIDTH, MAX_FRAME_WIDTH)
        val analyze = args.optBoolean("analyze", false)
        val question = args.optString("question").trim()

        val started = System.currentTimeMillis()

        // —— 准入：路径 / 大小 / 是否视频，都在这两步里换成可读原因 ——
        val file = deps.extractor.resolveFile(path).getOrElse { return fail(reasonOf(it)) }
        val meta = deps.extractor.probe(path).getOrElse { return fail(reasonOf(it)) }
        val duration = meta.durationMs
        if (start >= duration) {
            return fail("startMs=" + start + " 已经超出视频时长 " + duration + "ms。")
        }
        val end = if (requestedEnd <= 0L) duration else min(requestedEnd, duration)
        if (end <= start) {
            return fail("endMs 必须大于 startMs（收到 startMs=" + start + "，endMs=" + requestedEnd + "）。")
        }
        val window = end - start
        if (window > MAX_DURATION_MS) {
            return fail(
                "片段时长超限：需要分析 " + seconds(window) + "，超过 10 分钟上限。" +
                    "请用 startMs / endMs 只取一段，例如 startMs=0, endMs=300000 分析前 5 分钟。"
            )
        }

        val samples = deps.extractor.selectKeyframes(
            path,
            duration,
            VideoFrameExtractor.SelectOptions(
                mode = mode,
                maxFrames = maxFrames,
                intervalMs = interval,
                startMs = start,
                endMs = end,
                maxWidth = maxWidth,
            ),
        ).getOrElse { return fail(reasonOf(it)) }

        val frames = JSONArray()
        val built = ArrayList<Frame>(samples.size)
        val stamp = System.currentTimeMillis()
        try {
            var sequence = 0
            for (sample in samples) {
                val png = withContext(Dispatchers.IO) { encode(sample.bitmap, Bitmap.CompressFormat.PNG, 100) }
                    ?: continue
                val savedPath = deps.saveFrame(png, "video_" + stamp + "_" + sequence + ".png")
                    .getOrElse { error -> return fail("帧已经抽出来了，但保存失败：" + reasonOf(error)) }
                val dataUrl = if (analyze) {
                    withContext(Dispatchers.IO) { encode(sample.bitmap, Bitmap.CompressFormat.JPEG, ANALYSIS_JPEG_QUALITY) }
                        ?.let { "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(it) }
                } else {
                    null
                }
                frames.put(
                    JSONObject()
                        .put("t", sample.timeMs)
                        .put("path", savedPath)
                        .put("change", round3(sample.changeRatio))
                )
                built += Frame(sample.timeMs, savedPath, dataUrl)
                sequence++
            }
        } finally {
            samples.forEach { runCatching { it.bitmap.recycle() } }
        }

        if (built.isEmpty()) return fail("这个片段里没有取到任何可用画面（解码失败）。")

        val result = JSONObject()
            .put("ok", true)
            .put("path", file.absolutePath)
            .put("mode", modeName)
            .put("durationMs", duration)
            .put("width", meta.width)
            .put("height", meta.height)
            .put("rotation", meta.rotation)
            .put("startMs", start)
            .put("endMs", end)
            .put("maxFrames", maxFrames)
            .put("sampledCount", frames.length())
            // 自适应模式的实际粗扫间隔：10 分钟片段会自动放大间隔把粗扫点数压在 300 以内。
            .put("scanIntervalMs", if (mode == Mode.ADAPTIVE) deps.extractor.effectiveScanInterval(window, interval) else interval)
            .put("frames", frames)

        if (analyze) {
            val asked = question.ifBlank { DEFAULT_QUESTION }
            when (val outcome = analyzeFrames(asked, built)) {
                is AnalysisOutcome.Done -> {
                    result.put("question", asked)
                    result.put("analysis", outcome.text)
                    result.put("analysisMode", outcome.mode)
                }
                is AnalysisOutcome.Failed -> {
                    // 帧已经落盘了，别让整次调用白干：把原因和路径一起回填，用户还能自己看图。
                    return fail(
                        "关键帧已抽出并保存成功，但视觉分析失败：" + outcome.reason +
                            "（共 " + built.size + " 帧，第一帧：" + built.first().path +
                            "；如需重试可以调 analyze=false 先只拿帧）"
                    )
                }
            }
        } else {
            result.put("note", "analyze=false：只抽帧并把路径返回，没有调用视觉模型。")
        }
        result.put("elapsedMs", System.currentTimeMillis() - started)

        deps.log(
            "[video] video_frames " + path + " -> " + built.size + " 帧 / " + modeName +
                " / " + (System.currentTimeMillis() - started) + "ms / analyze=" + analyze
        )
        return ToolResult.Ok(result.toString())
    }

    /**
     * 多图优先：一次请求把所有关键帧按时间顺序发过去，模型能自己串起前后关系。
     * 模型不接受多图时退化为「逐帧问 + 本地汇总」，只取前 [PER_FRAME_LIMIT] 帧，避免请求爆炸。
     */
    private suspend fun analyzeFrames(question: String, frames: List<Frame>): AnalysisOutcome {
        val withImages = frames.filter { it.dataUrl != null }
        if (withImages.isEmpty()) return AnalysisOutcome.Failed("帧图片编码失败，没有可发送的图片。")
        val timing = withImages.joinToString("，") { "第 " + it.timeMs + " 毫秒" }
        val prompt = "问题：" + question + "\n\n" +
            "下面这些图片是同一个视频里按时间顺序抽出的关键帧，依次对应：" + timing + "。\n" +
            "请结合这些关键帧回答问题；提到具体步骤时用时间点指代（例如「约 3.2 秒处」）。" +
            "如果关键帧上看不出足够信息，就直接说看不出来，不要编造没出现的画面。"

        val multi = deps.analyze(SYSTEM_PROMPT, prompt, withImages.mapNotNull { it.dataUrl })
        multi.getOrNull()?.takeIf { it.isNotBlank() }?.let { return AnalysisOutcome.Done(it, "multi") }

        val reason = reasonOf(multi.exceptionOrNull())
        deps.log("[video] 多图分析失败，退化为逐帧：" + reason.take(200))

        val parts = ArrayList<String>()
        var firstFailure: String? = null
        for (frame in withImages.take(PER_FRAME_LIMIT)) {
            val single = deps.analyze(
                SYSTEM_PROMPT,
                "这是视频第 " + frame.timeMs + " 毫秒处的关键帧。只根据这一张图能看到的画面回答：" + question,
                listOfNotNull(frame.dataUrl),
            )
            single.getOrNull()?.takeIf { it.isNotBlank() }?.let { answer ->
                parts += "第 " + frame.timeMs + " 毫秒：" + answer.trim()
            }
            if (single.isFailure && firstFailure == null) firstFailure = reasonOf(single.exceptionOrNull())
        }
        if (parts.isEmpty()) {
            return AnalysisOutcome.Failed(firstFailure ?: reason)
        }
        val text = "（多图一次请求失败，已退化为逐帧分析，最多取前 " + PER_FRAME_LIMIT + " 帧）\n" +
            "退化原因：" + reason + "\n\n" + parts.joinToString("\n\n")
        return AnalysisOutcome.Done(text, "per-frame")
    }

    private fun fail(message: String): ToolResult {
        deps.log("[video] video_frames -> REJECTED " + message.take(200))
        return ToolResult.Error(message)
    }

    private data class Frame(val timeMs: Long, val path: String, val dataUrl: String?)

    private sealed class AnalysisOutcome {
        data class Done(val text: String, val mode: String) : AnalysisOutcome()
        data class Failed(val reason: String) : AnalysisOutcome()
    }

    private companion object {
        const val MODE_ADAPTIVE = "adaptive"
        const val MODE_UNIFORM = "uniform"
        const val ANALYSIS_JPEG_QUALITY = 82
        /** 逐帧退化模式最多问几帧：再多就变成十几次独立请求，等得久也费钱。 */
        const val PER_FRAME_LIMIT = 6
        const val DEFAULT_QUESTION = "请按时间顺序描述这个视频讲了什么：有哪些主要步骤或画面变化。"
        const val SYSTEM_PROMPT =
            "你是视频理解助手。用户会给你同一个视频里按时间顺序抽出的若干关键帧截图，" +
                "每张图对应的拍摄时间会在提问里列出。只描述图上真实可见的内容，" +
                "不要臆测没有出现在画面里的部分；信息不足就直说。"

        fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray? = runCatching {
            ByteArrayOutputStream().use { stream ->
                if (!bitmap.compress(format, quality, stream)) null else stream.toByteArray()
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }

        fun reasonOf(throwable: Throwable?): String {
            if (throwable == null) return "未知错误"
            return throwable.message?.takeIf { it.isNotBlank() } ?: throwable.javaClass.simpleName
        }

        fun round3(value: Float): Double = (value * 1000.0).roundToInt() / 1000.0

        fun seconds(ms: Long): String = (ms / 1000.0).let { value ->
            val rounded = (value * 10).roundToInt() / 10.0
            rounded.toString() + " 秒"
        }
    }
}

/** 把视频工具注册进同一个 [ToolRegistry]；返回工具名列表，方便宿主打日志。 */
fun registerVideoTools(registry: ToolRegistry, deps: VideoToolDeps): List<String> {
    val tools: List<ToolHandler> = listOf(VideoFramesTool(deps))
    tools.forEach(registry::register)
    return tools.map { it.name }
}
