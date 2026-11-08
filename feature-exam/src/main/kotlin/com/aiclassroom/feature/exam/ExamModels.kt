package com.aiclassroom.feature.exam

// 考试数据模型：从主 app MainActivity.kt 原样迁出，字段名与类型保持不变，UI 只改 import。
//
// answer 的语义已收窄为「考生作答」：只有考生在作答框里填的内容、或标记「不会」的状态会写进它。
// AI 通过考试工具提供的标准答案 / 解析写进 referenceAnswer，永远不会落进作答框。
// referenceAnswer 放在参数表末尾，历史上所有位置参数调用（ExamQuestion(premise, question)）语义不变。
data class ExamQuestion(
    val premise: String,
    val question: String,
    val answer: String = "",
    val unknown: Boolean = false,
    val referenceAnswer: String = ""
)

data class ExamSession(val title: String, val questions: MutableList<ExamQuestion>, val draft: String = "", val submitted: Boolean = false)
