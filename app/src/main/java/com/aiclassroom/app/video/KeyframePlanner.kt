package com.aiclassroom.app.video

import kotlin.math.abs
import kotlin.math.ceil

/**
 * 自适应选帧算法本体：只吃「差异曲线」，不看视频——所以能脱离设备在单测里验证。
 *
 * 为什么需要它：均匀抽帧对教程视频几乎必错。「点开菜单」这个动作只占 1 秒，
 * 12 帧均匀撒在 45 秒里，正好跳过它；而讲 PPT 的 30 秒静止画面会被抽成 10 张一模一样的废帧。
 * 这里换一种预算分配：先把预算花在局部变化最大的点上，剩下的点再用来兜住静止段落。
 */
object KeyframePlanner {

    /** 单点差异低于这个绝对值就不算「画面变了」：这是压缩噪声的量级，再小就不是内容变化。 */
    private const val PEAK_FLOOR = 0.0015f

    /**
     * 或者低于整条曲线峰值的这个比例，也不算。
     *
     * 这里必须用相对阈值而不是一个固定数：不同视频的差异尺度差一个数量级——
     * 深色画面里翻页能到 0.3，白底设置页滚动只有 0.03。写死 0.01 会把后者整片判成「没变化」，
     * 自适应就退化成均匀抽帧了（实测：白底设置页滚动 0.03-0.04，写死阈值时选点全落在静止段）。
     */
    private const val PEAK_RELATIVE = 0.2f

    data class Options(
        /** 选点总数上限（含首帧锚点）。 */
        val maxFrames: Int,
        /** 相邻选点的最小时间间隔，单位毫秒。 */
        val minGapMs: Long,
        /** 差异曲线每个点代表多少毫秒（自适应模式的粗扫间隔）。 */
        val intervalMs: Long,
    )

    /**
     * 返回被选中的曲线下标，按时间升序。
     *
     * 三轮预算分配：
     * 1. 首帧锚点：视频开头必须有一张，否则「视频一上来是什么画面」永远答不了；
     * 2. 局部极大值：按变化量从大到小贪心收，相邻选点间隔必须 ≥ minGapMs；
     * 3. 最远点补齐：每次挑「离已选点最远」的位置（同距离时变化大的优先），
     *    专门兜住长时间静止的段落——那里曲线是平的，第 2 轮一个点都不会选。
     */
    fun plan(curve: List<Float>, options: Options): List<Int> {
        if (curve.isEmpty()) return emptyList()
        val limit = options.maxFrames.coerceAtLeast(1)
        if (curve.size == 1 || limit == 1) return listOf(0)

        val gap = gapInPoints(options.minGapMs, options.intervalMs)
        val selected = ArrayList<Int>(limit)
        selected += 0

        // —— 第 2 轮：局部极大值 ——
        val peakFloor = maxOf(PEAK_FLOOR, curve.max() * PEAK_RELATIVE)
        val peaks = curve.indices
            .filter { index -> index > 0 && isLocalPeak(curve, index) && curve[index] > peakFloor }
            .sortedWith(compareByDescending<Int> { curve[it] }.thenBy { it })
        for (peak in peaks) {
            if (selected.size >= limit) break
            if (selected.all { abs(it - peak) >= gap }) selected += peak
        }

        // —— 第 3 轮：最远点补齐 ——
        while (selected.size < limit) {
            var best = -1
            var bestScore = -1f
            for (candidate in curve.indices) {
                if (candidate in selected) continue
                val distance = selected.minOf { abs(it - candidate) }
                if (distance < gap) continue
                // 距离优先、变化量做同分时的加分项（权重远小于 1 个下标）。
                val score = distance + curve[candidate]
                if (score > bestScore) {
                    bestScore = score
                    best = candidate
                }
            }
            if (best < 0) break
            selected += best
        }

        return selected.sorted()
    }

    /** 局部极大值：比左邻高、且比右邻高（末点只比左邻）。 */
    private fun isLocalPeak(curve: List<Float>, index: Int): Boolean {
        val value = curve[index]
        if (value < curve[index - 1]) return false
        return index + 1 >= curve.size || value >= curve[index + 1]
    }

    /** minGapMs 换算成「至少隔几个扫描点」，至少 1。 */
    private fun gapInPoints(minGapMs: Long, intervalMs: Long): Int {
        if (intervalMs <= 0L) return 1
        return ceil(minGapMs.toDouble() / intervalMs).toInt().coerceAtLeast(1)
    }
}
