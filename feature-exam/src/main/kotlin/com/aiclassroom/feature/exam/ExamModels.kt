package com.aiclassroom.feature.exam

// 考试数据模型：从主 app MainActivity.kt 原样迁出，字段名与类型保持不变，UI 只改 import。
data class ExamQuestion(val premise: String, val question: String, val answer: String = "", val unknown: Boolean = false)
data class ExamSession(val title: String, val questions: MutableList<ExamQuestion>, val draft: String = "", val submitted: Boolean = false)
