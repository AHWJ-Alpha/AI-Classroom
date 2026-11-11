package com.aiclassroom.app.util

/** 考试记录的时间线与摘要文案。 */

import com.aiclassroom.app.model.ExamRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 考试记录的时间格式：月-日 时:分。 */
internal val EXAM_RECORD_TIME = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")

internal fun examRecordTime(record: ExamRecord): String {
    val at = if (record.submittedAt > 0L) record.submittedAt else record.startedAt
    if (at <= 0L) return "--"
    return java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).format(EXAM_RECORD_TIME)
}

internal fun examRecordSummary(record: ExamRecord): String =
    record.title.ifBlank { "AI 随堂考试" } + " · " + record.questionCount + " 题"
