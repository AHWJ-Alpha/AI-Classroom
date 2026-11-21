package com.aiclassroom.classroom.domain

/**
 * 篇幅策略。
 *
 * 长篇要能写万字，就必须把「写多长」变成明确配置，而不是靠提示词里一句「多写点」。
 * 解析规则：用户明确说了就用用户的；没说按类型默认；故事时间跨度（一个月）不算字数。
 */
data class LengthPolicy(
    /** 整部目标字数；用户没提整部就只有单节目标。 */
    val totalTarget: Int?,
    /** 单节（单元）目标字数：这是分段续写的总配额。 */
    val unitTarget: Int,
    /** 单次生成的字数预算：一次调用最多要多少字，防止被输出上限截断。 */
    val perCallTarget: Int,
    /** 最大续写次数：unitTarget / perCallTarget 向上取整，并受硬上限约束。 */
    val maxSegments: Int,
    /** 是否来自用户的明确表述（用于界面展示与日志）。 */
    val fromUser: Boolean,
) {
    companion object {
        /** 单节目标的安全区间：太低没意义，太高会连续调用到崩。 */
        private val UNIT_RANGE = 1_500..20_000

        /** 单次调用预算：受模型输出上限约束，实用上限按 2500 字。 */
        private const val PER_CALL = 2_500

        /** 续写次数硬上限：避免无限续写。 */
        private const val MAX_SEGMENTS = 12

        fun parse(rawRequest: String, type: ProjectType): LengthPolicy {
            val text = rawRequest.replace("\\s+".toRegex(), "")

            // 每节/每章/单节 N 字
            val perUnit = Regex("(?:每|单)(?:节|章|篇)?([0-9]+(?:\\.[0-9]+)?)万字?").find(text)
                ?: Regex("(?:每|单)(?:节|章|篇)([0-9]+)字").find(text)
            val perUnitWords = perUnit?.groupValues?.get(1)?.toDoubleOrNull()?.let { w(it, perUnit.value.contains("万")) }

            // 整部 N 万字 / 总 N 字
            val total = Regex("(?:全篇|全文|整部|总共|合计)([0-9]+(?:\\.[0-9]+)?)万字?").find(text)
            val totalWords = total?.groupValues?.get(1)?.toDoubleOrNull()?.let { w(it, total.value.contains("万")) }

            // 节数：N 节 / N 章
            val units = Regex("([0-9]+)(?:节|章)").find(text)?.groupValues?.get(1)?.toIntOrNull()

            val derived = when {
                perUnitWords != null -> perUnitWords
                // 整部 + 节数 → 平摊到每一节
                totalWords != null && units != null && units > 0 -> totalWords / units
                else -> null
            }

            val defaults = when (type) {
                ProjectType.SCENE -> 4_000
                ProjectType.NOVEL -> 6_000
                ProjectType.TEXT_GAL -> 2_500
                ProjectType.TEACHING -> 2_000
            }
            val unitTarget = (derived ?: defaults).coerceIn(UNIT_RANGE)
            val perCall = minOf(PER_CALL, unitTarget)
            val segments = ((unitTarget + perCall - 1) / perCall).coerceIn(1, MAX_SEGMENTS)
            return LengthPolicy(
                totalTarget = totalWords,
                unitTarget = unitTarget,
                perCallTarget = perCall,
                maxSegments = segments,
                fromUser = derived != null,
            )
        }

        private fun w(value: Double, tenThousand: Boolean): Int =
            (if (tenThousand) value * 10_000 else value).toInt()
    }
}
