package com.aiclassroom.classroom.domain

/**
 * 篇幅策略的解析结果。
 *
 * 两条硬规则：
 * 1) 用户明确说的就是事实，不做静默夹取 —— 「每节 200 字」就是 200，不能悄悄变成 1500；
 * 2) 超出支持范围要**明确拒绝**，而不是改小后假装接受。
 */
sealed interface LengthPlan {

    data class Ok(
        /** 单节目标字数。 */
        val unitTarget: Int,
        /** 整部目标；用户没提整部就是 null。 */
        val totalTarget: Int?,
        /** 单次调用的建议新增字数（受输出上限约束的估算值）。 */
        val perCallTarget: Int,
        /** 安全上限内的最大分段数；达到上限仍不足要报待处理，不能宣称完成。 */
        val maxSegments: Int,
        /** 是否来自用户的明确表述。 */
        val fromUser: Boolean,
        /** 需要向用户展示的说明（例如总量与单节冲突）。 */
        val notes: List<String> = emptyList(),
    ) : LengthPlan

    /** 无法按用户要求执行；调用方必须把这句原因显示出来，不能继续。 */
    data class Unsupported(val reason: String) : LengthPlan

    companion object {
        /** 单节支持范围。超出即 Unsupported，不做静默夹取。 */
        const val MIN_UNIT = 1
        const val MAX_UNIT = 20_000

        /** 单次调用的推荐文本上限（不是 token，也不是截断保证）。 */
        const val PER_CALL_RECOMMENDED = 2_500

        /** 分段硬上限。 */
        const val MAX_SEGMENTS_HARD = 12

        private val DEFAULTS = mapOf(
            ProjectType.SCENE to 4_000,
            ProjectType.NOVEL to 6_000,
            ProjectType.TEXT_GAL to 2_500,
            ProjectType.TEACHING to 2_000,
        )

        /**
         * 解析篇幅。
         *
         * @param outlineUnitCount 正式总纲的节数：只有它才能把「全篇」摊到每一节，
         *   正则猜出来的节数不可靠。
         */
        fun parse(
            rawRequest: String,
            type: ProjectType,
            outlineUnitCount: Int? = null,
        ): LengthPlan {
            val text = rawRequest.replace("\\s+".toRegex(), "")
            val notes = mutableListOf<String>()

            val perUnit = perUnitWords(text)
            val total = totalWords(text)
            val statedUnits = statedUnitCount(text)
            val unitCount = outlineUnitCount?.takeIf { it > 0 } ?: statedUnits

            val derived: Int? = when {
                perUnit != null -> perUnit
                // 只有整部总量：按**正式总纲节数**平摊，尾差给后面的节。
                total != null && unitCount != null && unitCount > 0 -> ceilDiv(total, unitCount)
                else -> null
            }

            if (perUnit != null && total != null && unitCount != null && unitCount > 0) {
                val product = perUnit.toLong() * unitCount
                if (product != total.toLong()) {
                    notes += "用户同时给了单节 " + perUnit + " 字与全篇 " + total +
                        " 字、" + unitCount + " 节，两者相乘为 " + product + "，与全篇不一致；单节以显式值为准。"
                }
            }

            val unitTarget = derived ?: DEFAULTS[type] ?: 4_000
            if (unitTarget > MAX_UNIT) {
                return Unsupported(
                    "单节目标 " + unitTarget + " 字超出当前支持范围（最多 " + MAX_UNIT + " 字/节）；" +
                        "请拆成更多节，或把单节字数降到范围内。",
                )
            }
            if (unitTarget < MIN_UNIT) {
                return Unsupported("单节目标为 " + unitTarget + " 字，无法生成。")
            }

            val perCall = minOf(PER_CALL_RECOMMENDED, unitTarget)
            // 基础估算之外留两段余量：模型常常写不到目标，一次就满很罕见。
            val base = ceilDiv(unitTarget, perCall)
            val segments = (base + 2).coerceAtMost(MAX_SEGMENTS_HARD)

            return Ok(
                unitTarget = unitTarget,
                totalTarget = total,
                perCallTarget = perCall,
                maxSegments = segments,
                fromUser = derived != null,
                notes = notes,
            )
        }

        private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b

        /** 每节/每章 N 字（支持 1.2 万字）。 */
        private fun perUnitWords(text: String): Int? {
            val m = Regex("(?:(?:每|单)(?:节|章|段)|至少)([0-9]+(?:\\.[0-9]+)?)万字?").find(text)
            if (m != null) return scaled(m.groupValues[1], tenThousand = true)
            val m2 = Regex("(?:(?:每|单)(?:节|章|段)|至少)([0-9]+)字").find(text)
            return m2?.groupValues?.get(1)?.toIntOrNull()
        }

        /** 全篇/总共 N 字（支持 10 万字、1.2 万字、100000 字）。 */
        private fun totalWords(text: String): Int? {
            val m = Regex("(?:全篇|全文|整部|总共|合计)([0-9]+(?:\\.[0-9]+)?)万字?").find(text)
            if (m != null) return scaled(m.groupValues[1], tenThousand = true)
            val m2 = Regex("(?:全篇|全文|整部|总共|合计)([0-9]+)字").find(text)
            return m2?.groupValues?.get(1)?.toIntOrNull()
        }

        private fun statedUnitCount(text: String): Int? {
            val m = Regex("(?:共|一共)?([0-9]+)(?:节|章)").find(text) ?: return null
            val value = m.groupValues[1].toIntOrNull() ?: return null
            // 「过一个月」「22 天」这类是故事时间，不是节数。
            val span = Regex("([0-9]+)(?:天|日|个月)").containsMatchIn(text)
            return if (span && value <= 2) null else value
        }

        private fun scaled(raw: String, tenThousand: Boolean): Int? {
            val value = raw.toDoubleOrNull() ?: return null
            val scaled = if (tenThousand) value * 10_000 else value
            return scaled.toInt()
        }
    }
}
