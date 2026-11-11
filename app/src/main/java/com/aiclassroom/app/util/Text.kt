package com.aiclassroom.app.util

/**
 * 文本与 Markdown 处理（从 MainActivity 搬出）。
 */

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.aiclassroom.app.model.ChatMessage
import com.aiclassroom.app.ui.theme.DesignTokens

internal fun looksLikeFailure(text: String): Boolean {
    if (text.isBlank()) return false
    val lower = text.lowercase()
    return text.startsWith("调用失败") || text.startsWith("请先选择") || text.startsWith("请先填写") ||
        lower.contains("http 4") || lower.contains("http 5") || lower.contains("unauthorized") || lower.contains("invalid api")
}

internal fun isMathLikeLine(line: String): Boolean =
    line.contains("$") || line.contains("\\(") || line.contains("\\[") || line.contains("\\begin") || line.contains("\\text") || line.contains("\\phi") || line.contains("\\psi")

internal fun sanitizeMathText(raw: String): String {
    var text = raw
        .replace('；', ';')
        .replace('（', '(')
        .replace('）', ')')
        .replace('《', '<')
        .replace('》', '>')
    val replacements = listOf(
        "ItextComplexityi" to "\\text{Complexity}",
        "ItextíComplexity)" to "\\text{Complexity}",
        "Itext Complexityj" to "\\text{Complexity}",
        "ItextfCon" to "\\text{Complexity}",
        "Itext（" to "\\text{",
        "Itext(" to "\\text{",
        "ltext（" to "\\text{",
        "ltext(" to "\\text{",
        "text《" to "\\text{",
        "text<" to "\\text{",
        "Ibeginfcases" to "\\begin{cases}",
        "Ibegin(cases" to "\\begin{cases}",
        "Iend(casesy" to "\\end{cases}",
        "Iend(cases" to "\\end{cases}",
        "lnot" to "\\lnot",
        "lor" to "\\lor",
        "land" to "\\land",
        "lpsi" to "\\psi",
        "Ipsi" to "\\psi",
        "Npsi" to "\\psi",
        "phil" to "\\phi",
        "Iphi" to "\\phi",
        "\\phil" to "\\phi",
        "\\lpsi" to "\\psi"
    )
    replacements.forEach { (bad, good) -> text = text.replace(bad, good) }
    text = text
        .replace(Regex("\\\\text\\{([^}\\n]*)(?=\\n|$)"), "\\\\text{$1}")
        .replace(Regex("\\s+小底部"), "")
        .replace("!\\phi", "\\phi")
    return text
}

internal fun buildInlineMarkdown(line: String) = buildAnnotatedString {
    var i = 0
    while (i < line.length) {
        val boldStart = line.indexOf("**", i)
        val codeStart = line.indexOf('`', i)
        val next = listOf(boldStart, codeStart).filter { it >= 0 }.minOrNull() ?: -1
        if (next < 0) { append(line.substring(i)); break }
        append(line.substring(i, next))
        if (next == boldStart) {
            val end = line.indexOf("**", next + 2)
            if (end > next) { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.substring(next + 2, end)) }; i = end + 2 } else { append("**"); i = next + 2 }
        } else {
            val end = line.indexOf('`', next + 1)
            if (end > next) { withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = DesignTokens.Legacy.Blue)) { append(line.substring(next + 1, end)) }; i = end + 1 } else { append('`'); i = next + 1 }
        }
    }
}

internal fun stripMarkdownForSpeech(text: String): String = sanitizeMathText(text)
    .replace(Regex("```[\\s\\S]*?```"), " ")
    .replace(Regex("[#>*_`\\[\\]()]"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

internal fun summarize(scope: String, messages: List<ChatMessage>): String = "$scope 记忆：" + messages.joinToString(" ") { it.text }.replace(Regex("\\s+"), " ").take(180)
