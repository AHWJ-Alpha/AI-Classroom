package com.aiclassroom.classroom.content

import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.gateway.StructuredPayload
import com.aiclassroom.classroom.json.*

/**
 * 解析审查器输出。
 *
 * 关键约定：**解析不出结构化结果时按「有阻断问题」处理**。
 * 审查的意义就是拦住坏内容，读不懂审查结果时放行等于没有审查。
 */
object ReviewParser {

    data class Parsed(val report: ReviewReport?, val problems: List<String>)

    fun parse(raw: String, revisionId: RevisionId, now: EpochMillis): Parsed {
        val jsonText = StructuredPayload.extract(raw)
            ?: return Parsed(null, listOf("审查输出里没有完整的 JSON"))
        val root = runCatching { JsonParser.parse(jsonText) }.getOrNull()
            ?: return Parsed(null, listOf("审查 JSON 无法解析"))

        val o = root.asObject()
        if (o["issues"] !is Json.Arr) return Parsed(null, listOf("审查缺少 issues 数组"))
        val issues = o["issues"].asArray().mapIndexed { i, item ->
            val m = item.asObject()
            ReviewIssue(
                id = m["id"].asString() ?: ("i" + (i + 1)),
                severity = when (m["severity"].asString()) {
                    "ERROR" -> ReviewIssue.Severity.ERROR
                    "INFO" -> ReviewIssue.Severity.INFO
                    else -> ReviewIssue.Severity.WARN
                },
                revisionId = revisionId,
                blockId = m["blockId"].asString(),
                rule = m["rule"].asString() ?: "",
                evidence = m["evidence"].asString() ?: "",
                suggestedScope = m["suggestedScope"].asString(),
                blocking = m["blocking"].asBool() ?: false,
            )
        }

        return Parsed(
            ReviewReport(issues = issues, reviewedAt = now),
            problems = emptyList(),
        )
    }
}
