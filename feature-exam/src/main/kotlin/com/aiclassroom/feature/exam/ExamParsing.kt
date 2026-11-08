package com.aiclassroom.feature.exam

// :feature-exam 是纯 JVM 模块（不引入 Compose），这里保留迁移函数调用的同名小工具，
// 使迁移过来的函数体逐字不变；进入 Compose 状态前由 app 侧换成快照列表。
internal fun List<ExamQuestion>.toMutableStateList(): MutableList<ExamQuestion> =
    mutableListOf<ExamQuestion>().also { it.addAll(this) }

fun isExamRequest(text: String): Boolean {
    val triggers = listOf("考试", "测验", "测试", "考我", "出题", "随堂", "模拟考", "quiz", "exam", "test")
    return triggers.any { text.contains(it, ignoreCase = true) }
}

fun detectExamSession(text: String, userRequestedExam: Boolean): ExamSession? {
    parseExamBlock(text)?.let { return it }
    parseReadableExam(text)?.let { return it }
    return if (userRequestedExam && hasExplicitExamStart(text)) fallbackExamFromText(text) else null
}

fun hasExplicitExamStart(text: String): Boolean {
    val triggers = listOf("【考试】", "[EXAM]", "开始考试", "进入考试", "模拟考试", "随堂测试", "本次测试", "开始测验", "进入测验")
    return triggers.any { text.contains(it, ignoreCase = true) }
}

fun stripExamBlock(text: String): String =
    text.replace(Regex("\\[EXAM][\\s\\S]*?\\[/EXAM]", RegexOption.IGNORE_CASE), "").trim()

fun parseExamBlock(text: String): ExamSession? {
    val block = Regex("\\[EXAM]([\\s\\S]*?)\\[/EXAM]", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1) ?: return null
    val questions = mutableListOf<ExamQuestion>()
    var premise = "请根据当前课堂内容作答。"
    block.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
        when {
            line.startsWith("premise:", true) -> premise = line.substringAfter(":").trim().ifBlank { premise }
            line.startsWith("question:", true) -> line.substringAfter(":").trim().takeIf { it.isNotBlank() }?.let { questions.add(ExamQuestion(premise, it)) }
        }
    }
    return questions.takeIf { it.isNotEmpty() }?.let { ExamSession("AI 随堂考试", it.toMutableStateList()) }
}

fun parseReadableExam(text: String): ExamSession? {
    val normalized = text.replace('：', ':')
    if (!hasExplicitExamStart(normalized)) return null
    val premise = Regex("(?:前提|材料|背景):([\\s\\S]*?)(?=(?:问题|题目|Q\\d*)[:：]|$)")
        .find(normalized)?.groupValues?.getOrNull(1)?.replace("【考试】", "")?.trim().orEmpty().ifBlank { "请根据当前课堂内容作答。" }
    val questions = Regex("(?:问题|题目|Q\\d*)[:：]([\\s\\S]*?)(?=(?:问题|题目|Q\\d*|前提|材料|背景)[:：]|$)")
        .findAll(normalized).mapNotNull { it.groupValues.getOrNull(1)?.trim()?.takeIf { q -> q.isNotBlank() }?.let { q -> ExamQuestion(premise, q) } }
        .toList()
    return questions.takeIf { it.isNotEmpty() }?.let { ExamSession("AI 随堂考试", it.toMutableStateList()) }
}

fun fallbackExamFromText(text: String): ExamSession {
    val candidates = text.lines().map { it.trim() }.filter { line ->
        line.length >= 8 && (line.contains("？") || line.contains("?") || line.matches(Regex(".*(简述|说明|分析|解释|计算|写出).*")))
    }.take(6)
    val questions = candidates.ifEmpty { listOf("请结合刚才课堂内容，回答本轮测试的核心问题。") }
        .map { ExamQuestion("请根据当前课堂内容作答。", it.removePrefix("问题:").removePrefix("题目:").trim()) }
    return ExamSession("AI 随堂考试", questions.toMutableStateList())
}

fun packExamAnswers(session: ExamSession): String = buildString {
    appendLine("以下是用户在沉浸式考试工具中的作答，请批改并给出讲解：")
    session.questions.forEachIndexed { index, q ->
        appendLine("题目 ${index + 1}")
        appendLine("前提：${q.premise}")
        appendLine("问题：${q.question}")
        appendLine("状态：${if (q.unknown) "不会" else if (q.answer.isBlank()) "未作答" else "已作答"}")
        appendLine("考生作答：${q.answer.ifBlank { "（空）" }}")
        if (q.referenceAnswer.isNotBlank()) appendLine("参考解析：${q.referenceAnswer}")
    }
    if (session.draft.isNotBlank()) appendLine("草稿：${session.draft}")
}
