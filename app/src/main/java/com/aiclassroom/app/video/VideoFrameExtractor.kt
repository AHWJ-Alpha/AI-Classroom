package com.aiclassroom.app.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 视频抽帧引擎：探测元信息 + 按时间取帧 + 自适应选帧。
 *
 * MediaMetadataRetriever 不是线程安全的，也不便宜，所以每个公开方法各自开一个实例、
 * 用完立刻 release()；同一个方法内部的多帧取用则共用一个实例串行跑（安全且快得多）。
 * 所有阻塞都在 Dispatchers.IO 上执行，调用方（工具层）拿到的永远是 Result。
 */
class VideoFrameExtractor(private val context: Context) {

    data class Meta(val durationMs: Long, val width: Int, val height: Int, val rotation: Int) {
        /** 竖屏视频的宽高是按存储方向记的，rotation 90/270 时真实宽高要对调。 */
        val displayWidth: Int get() = if (rotation == 90 || rotation == 270) height else width
    }

    data class Sample(val timeMs: Long, val changeRatio: Float, val bitmap: Bitmap)

    enum class Mode { ADAPTIVE, UNIFORM }

    data class SelectOptions(
        val mode: Mode = Mode.ADAPTIVE,
        val maxFrames: Int = DEFAULT_MAX_FRAMES,
        /** 自适应模式＝粗扫间隔（兜底值）；均匀模式＝固定间隔。 */
        val intervalMs: Long = DEFAULT_SCAN_INTERVAL_MS,
        val startMs: Long = 0L,
        /** 0 表示到视频结尾。 */
        val endMs: Long = 0L,
        val minGapMs: Long = DEFAULT_MIN_GAP_MS,
        val maxWidth: Int = DEFAULT_MAX_WIDTH,
    )

    /** 只做路径准入，不打开视频；工具层用它拿规范化后的路径。 */
    fun resolveFile(rawPath: String): Result<File> =
        VideoPathGuard.validate(rawPath, privateRoots())

    suspend fun probe(rawPath: String): Result<Meta> = withContext(Dispatchers.IO) {
        val file = resolveFile(rawPath).getOrElse { return@withContext Result.failure(it) }
        try {
            Result.success(probeBlocking(file))
        } catch (throwable: Throwable) {
            Result.failure(
                VideoFrameException(
                    "探测视频信息失败（可能不是视频文件，或编码不受支持）：" +
                        (throwable.message ?: throwable.javaClass.simpleName)
                )
            )
        }
    }

    /**
     * 取指定时间点的一帧。单帧场景用 OPTION_CLOSEST：调用方要的就是 t 这一刻的画面，
     * 退到最近的关键帧可能差好几秒（screenrecord 出来的文件整个只有 1 个关键帧）。
     */
    suspend fun frameAt(
        rawPath: String,
        timeMs: Long,
        maxWidth: Int = DEFAULT_MAX_WIDTH,
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        val file = resolveFile(rawPath).getOrElse { return@withContext Result.failure(it) }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val frame = scaledFrame(retriever, timeMs, maxWidth, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: return@withContext Result.failure(
                    VideoFrameException("解码失败：${timeMs}ms 处取不到画面（可能超出视频时长）")
                )
            Result.success(frame)
        } catch (throwable: Throwable) {
            Result.failure(
                VideoFrameException(
                    "取帧失败：${timeMs}ms，" + (throwable.message ?: throwable.javaClass.simpleName)
                )
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * 抽关键帧。两阶段：
     * 1. 粗扫：按扫描间隔取 96px 缩略图，只比亮度，得到一条 0..1 的差异曲线；
     * 2. 选帧：交给 [KeyframePlanner] 分配预算（变化大的点多选、静止段落各留一个代表），
     *    再按选中的时间点取全尺寸帧。
     *
     * 两个必须守住的成本：粗扫点数上限 [MAX_SCAN_POINTS]（10 分钟按 1 秒扫是 600 点），
     * 以及精确模式的点数上限 [MAX_EXACT_SCAN_POINTS]（精确取帧要从前一个关键帧一路解过来）。
     */
    suspend fun selectKeyframes(
        rawPath: String,
        durationMs: Long,
        options: SelectOptions,
    ): Result<List<Sample>> = withContext(Dispatchers.IO) {
        val file = resolveFile(rawPath).getOrElse { return@withContext Result.failure(it) }
        try {
            val total = if (durationMs > 0L) durationMs else probeBlocking(file).durationMs
            val start = options.startMs.coerceIn(0L, max(0L, total - 1L))
            val end = if (options.endMs <= 0L) total else options.endMs.coerceIn(start + 1L, total)
            val window = (end - start).coerceAtLeast(1L)

            if (options.mode == Mode.UNIFORM) {
                val times = uniformTimes(start, end, window, options.intervalMs, options.maxFrames)
                // 均匀模式也把变化量算出来：用户要对比「均匀 vs 自适应」时，这一列就是证据。
                val scan = coarseScan(file, times, options.intervalMs)
                return@withContext extract(file, times, scan.curve, options.maxWidth, scan.option)
            }

            val scanInterval = effectiveScanInterval(window, options.intervalMs)
            val scanTimes = scanTimes(start, end, scanInterval)
            val scan = coarseScan(file, scanTimes, scanInterval)
            val picked = KeyframePlanner.plan(
                scan.curve,
                KeyframePlanner.Options(
                    maxFrames = options.maxFrames,
                    minGapMs = options.minGapMs,
                    intervalMs = scan.intervalMs,
                ),
            )
            if (picked.isEmpty()) {
                return@withContext Result.failure(VideoFrameException("这段视频里没有取到任何可选帧。"))
            }
            extract(
                file,
                picked.map { scan.times[it] },
                picked.map { scan.curve[it] },
                options.maxWidth,
                scan.option,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Result.failure(
                VideoFrameException("抽帧失败：" + (throwable.message ?: throwable.javaClass.simpleName))
            )
        }
    }

    /** 粗扫间隔：先尊重调用方给的 intervalMs，再按点数上限放大，最低 500ms。 */
    fun effectiveScanInterval(windowMs: Long, requestedMs: Long): Long {
        val requested = if (requestedMs > 0L) requestedMs else DEFAULT_SCAN_INTERVAL_MS
        val toFit = ceil(windowMs.toDouble() / MAX_SCAN_POINTS).toLong()
        return max(max(requested, toFit), MIN_SCAN_INTERVAL_MS)
    }

    // ---------- 内部实现 ----------

    /** 一次粗扫的结果：实际用的时间网格、差异曲线、网格间隔，以及取帧时用的解码选项。 */
    private class Scan(
        val times: List<Long>,
        val curve: List<Float>,
        val intervalMs: Long,
        val option: Int,
    )

    private fun privateRoots(): List<String> = buildList {
        add(context.filesDir.absolutePath)
        add(context.cacheDir.absolutePath)
        runCatching { context.noBackupFilesDir?.absolutePath }.getOrNull()?.let(::add)
        runCatching { context.getExternalFilesDir(null)?.absolutePath }.getOrNull()?.let(::add)
        runCatching { context.externalCacheDir?.absolutePath }.getOrNull()?.let(::add)
    }

    private fun probeBlocking(file: File): Meta {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            if (duration <= 0L) throw VideoFrameException("读不到视频时长，这个文件可能不是视频。")
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            if (width <= 0 || height <= 0) {
                throw VideoFrameException("读不到视频分辨率，这个文件可能不是视频。")
            }
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            return Meta(duration, width, height, rotation)
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * 取一帧并缩到 targetWidth（只缩小不放大）。
     *
     * [option] 决定「t 这一刻」的取法：OPTION_CLOSEST_SYNC 直接退到最近的关键帧（快），
     * OPTION_CLOSEST 从这个关键帧解码到 t（准）。
     */
    private fun scaledFrame(
        retriever: MediaMetadataRetriever,
        timeMs: Long,
        targetWidth: Int,
        option: Int,
    ): Bitmap? {
        val timeUs = timeMs.coerceAtLeast(0L) * 1000L
        val sourceWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: 0
        val sourceHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: 0
        val limit = targetWidth.coerceIn(MIN_FRAME_WIDTH, MAX_FRAME_WIDTH)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && sourceWidth > 0 && sourceHeight > 0) {
            // 先缩后解码：1080p+ 的视频直接取整帧再缩会白占十几 MB 内存，还慢好几倍。
            val width = min(limit, sourceWidth)
            val height = max(1, (sourceHeight * width.toFloat() / sourceWidth).roundToInt())
            val scaled = runCatching {
                retriever.getScaledFrameAtTime(timeUs, option, width, height)
            }.getOrNull()
            if (scaled != null) return scaled
        }
        val full = retriever.getFrameAtTime(timeUs, option) ?: return null
        if (full.width <= limit) return full
        val height = max(1, (full.height * limit.toFloat() / full.width).roundToInt())
        val scaled = Bitmap.createScaledBitmap(full, limit, height, true)
        if (scaled !== full) full.recycle()
        return scaled
    }

    /** 96px 缩略图 → 亮度数组；取不到帧时返回 null（调用方保留上一帧，别把丢帧误判成突变）。 */
    private fun thumbnailLuma(retriever: MediaMetadataRetriever, timeMs: Long, option: Int): IntArray? {
        val thumb = scaledFrame(retriever, timeMs, SCAN_THUMB_WIDTH, option) ?: return null
        return try {
            val pixels = IntArray(thumb.width * thumb.height)
            thumb.getPixels(pixels, 0, thumb.width, 0, 0, thumb.width, thumb.height)
            FrameDiff.lumaOf(pixels)
        } finally {
            thumb.recycle()
        }
    }

    /**
     * 这个文件里「按关键帧取帧」能不能取到不同画面？
     *
     * 实测教训：screenrecord 录一个基本静止的界面时，整个文件只有 1 个关键帧
     * （test.mp4：37 个样本 / stss 只有 1 条）。这时 OPTION_CLOSEST_SYNC 在任何时间点
     * 都返回第 0 帧，差异曲线会整条变成 0，自适应等于失效——而且是静默失效。
     * 所以先拿三个均匀分布的时间点探一下，取到的画面全一样就切成精确解码。
     */
    private fun hasDistinctSyncFrames(retriever: MediaMetadataRetriever, start: Long, end: Long): Boolean {
        val span = (end - start).coerceAtLeast(1L)
        val probes = listOf(start, start + span / 3, start + span * 2 / 3)
        var previous: IntArray? = null
        for (time in probes) {
            val luma = thumbnailLuma(retriever, time, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
            val baseline = previous
            if (baseline != null && FrameDiff.changeRatio(baseline, luma) > SYNC_DEGENERATE_THRESHOLD) return true
            previous = luma
        }
        return false
    }

    /** 串行跑完一次粗扫：先决定解码选项，再逐点算差异曲线，缩略图用完即回收。 */
    private fun coarseScan(file: File, requestedTimes: List<Long>, requestedIntervalMs: Long): Scan {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val exact = !hasDistinctSyncFrames(retriever, requestedTimes.first(), requestedTimes.last() + 1L)
            val option = if (exact) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            val times = if (exact && requestedTimes.size > MAX_EXACT_SCAN_POINTS) {
                subsample(requestedTimes, MAX_EXACT_SCAN_POINTS)
            } else {
                requestedTimes
            }
            val interval = if (times.size <= 1) requestedIntervalMs
            else (times.last() - times.first()) / (times.size - 1)
            val curve = ArrayList<Float>(times.size)
            var previous: IntArray? = null
            for (time in times) {
                val luma = thumbnailLuma(retriever, time, option)
                if (luma == null) {
                    curve += 0f
                    continue
                }
                val baseline = previous
                curve += if (baseline == null) 0f else FrameDiff.changeRatio(baseline, luma)
                previous = luma
            }
            return Scan(times, curve, interval, option)
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** 按选中的时间点取全尺寸帧；转 RGB_565 省一半内存（这些帧要留到落盘，12 张 720p 不小）。 */
    private fun extract(
        file: File,
        times: List<Long>,
        changes: List<Float>,
        maxWidth: Int,
        option: Int,
    ): Result<List<Sample>> {
        val samples = ArrayList<Sample>(times.size)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            for (index in times.indices) {
                val frame = scaledFrame(retriever, times[index], maxWidth, option) ?: continue
                val compact = runCatching { frame.copy(Bitmap.Config.RGB_565, false) }.getOrNull()
                val usable = if (compact != null && compact !== frame) {
                    frame.recycle()
                    compact
                } else {
                    frame
                }
                samples += Sample(
                    timeMs = times[index],
                    changeRatio = changes.getOrElse(index) { 0f },
                    bitmap = usable,
                )
            }
        } finally {
            runCatching { retriever.release() }
        }
        if (samples.isEmpty()) {
            return Result.failure(VideoFrameException("选中的时间点都取不到画面，解码失败。"))
        }
        return Result.success(samples)
    }

    /** 均匀抽样到 limit 个点，首尾都保留（缩到 120 点仍能覆盖整段）。 */
    private fun subsample(times: List<Long>, limit: Int): List<Long> {
        if (times.size <= limit) return times
        val result = ArrayList<Long>(limit)
        for (index in 0 until limit) {
            result += times[(index.toLong() * (times.size - 1) / (limit - 1)).toInt()]
        }
        return result
    }

    private fun scanTimes(start: Long, end: Long, interval: Long): List<Long> {
        val times = ArrayList<Long>()
        var time = start
        while (time < end && times.size < MAX_SCAN_POINTS) {
            times += time
            time += interval
        }
        if (times.isEmpty()) times += start
        return times
    }

    private fun uniformTimes(
        start: Long,
        end: Long,
        windowMs: Long,
        intervalMs: Long,
        maxFrames: Int,
    ): List<Long> {
        val limit = maxFrames.coerceAtLeast(1)
        // 固定间隔抽出来超过上限时把间隔放大到刚好装下，而不是硬截断（截断会丢掉视频后半段）。
        val fit = ceil(windowMs.toDouble() / limit).toLong()
        val step = max(max(intervalMs, MIN_SCAN_INTERVAL_MS), fit)
        val times = ArrayList<Long>(limit)
        var time = start
        while (time < end && times.size < limit) {
            times += time
            time += step
        }
        if (times.isEmpty()) times += start
        return times
    }

    companion object {
        const val DEFAULT_MAX_FRAMES: Int = 12
        const val MAX_FRAMES_LIMIT: Int = 24
        const val DEFAULT_SCAN_INTERVAL_MS: Long = 1000L
        const val DEFAULT_UNIFORM_INTERVAL_MS: Long = 2000L
        const val DEFAULT_MIN_GAP_MS: Long = 800L
        const val DEFAULT_MAX_WIDTH: Int = 720
        const val MIN_FRAME_WIDTH: Int = 96
        const val MAX_FRAME_WIDTH: Int = 1280
        const val SCAN_THUMB_WIDTH: Int = 96
        const val MAX_SCAN_POINTS: Int = 300
        const val MAX_EXACT_SCAN_POINTS: Int = 120
        const val MIN_SCAN_INTERVAL_MS: Long = 500L
        const val MAX_DURATION_MS: Long = VideoPathGuard.MAX_DURATION_MS
        const val MAX_FILE_BYTES: Long = VideoPathGuard.MAX_FILE_BYTES

        /** 三个探测点之间的变化小于这个值，就当「关键帧取不到不同画面」。 */
        private const val SYNC_DEGENERATE_THRESHOLD = 0.001f
    }
}
