package com.aiclassroom.app.util

/**
 * 对话章节与分支之间的关联计算。
 *
 * - 章节切分与解析（模型输出 → ConversationChapter）
 * - 章节向量与余弦相似度：判断两段对话在讲同一件事
 * - 分支链接：从「从哪里开分支」的语义里找出章节之间的跳转关系
 */

import com.aiclassroom.app.model.ChatMessage
import com.aiclassroom.app.model.ChapterLink
import com.aiclassroom.app.model.ConversationChapter
import com.aiclassroom.app.util.CHAPTER_SIZE
import org.json.JSONObject

internal fun buildChapterLinks(chapters: List<ConversationChapter>): List<ChapterLink> {
    val vectors = chapters.map { chapterVector(it) }
    val links = mutableListOf<ChapterLink>()
    for (i in chapters.indices) {
        if (i < chapters.lastIndex) links.add(ChapterLink(i, i + 1, 0.35f))
        val best = chapters.indices.filter { it != i && kotlin.math.abs(it - i) > 1 }
            .map { it to cosineSimilarity(vectors[i], vectors[it]) }
            .filter { it.second > 0.08f }
            .maxByOrNull { it.second }
        best?.let { (target, score) ->
            if (i < target) links.add(ChapterLink(i, target, score.coerceIn(0.12f, 1f)))
        }
    }
    return links.distinctBy { minOf(it.from, it.to) to maxOf(it.from, it.to) }.take(32)
}

internal fun chapterVector(chapter: ConversationChapter): Map<String, Float> =
    Regex("[A-Za-z0-9_]+|[\\u4e00-\\u9fa5]{2,}").findAll(chapter.title + " " + chapter.summary)
        .map { it.value.lowercase() }
        .filter { it.length > 1 && it !in COMMON_MEMORY_WORDS }
        .groupingBy { it }
        .eachCount()
        .mapValues { it.value.toFloat() }

internal fun cosineSimilarity(a: Map<String, Float>, b: Map<String, Float>): Float {
    if (a.isEmpty() || b.isEmpty()) return 0f
    val dot = a.entries.sumOf { (key, value) -> (value * (b[key] ?: 0f)).toDouble() }
    val normA = kotlin.math.sqrt(a.values.sumOf { (it * it).toDouble() })
    val normB = kotlin.math.sqrt(b.values.sumOf { (it * it).toDouble() })
    return if (normA == 0.0 || normB == 0.0) 0f else (dot / (normA * normB)).toFloat()
}

internal fun fallbackChapters(messages: List<ChatMessage>): List<ConversationChapter> = messages.chunked(CHAPTER_SIZE).mapIndexed { index, chunk ->
    val start = index * CHAPTER_SIZE
    localChapterSummary(index, chunk, start, start + chunk.size - 1)
}

internal fun localChapterSummary(index: Int, messages: List<ChatMessage>, start: Int, end: Int): ConversationChapter {
    val firstUser = messages.firstOrNull { it.role == "user" }?.text?.replace(Regex("\\s+"), " ").orEmpty()
    val title = if (firstUser.isBlank()) "章节 ${index + 1}" else firstUser.take(18)
    val summary = messages.joinToString(" ") { it.text }.replace(Regex("\\s+"), " ").take(80).ifBlank { "本章暂无摘要。" }
    return ConversationChapter(title, summary, start, end)
}

internal fun parseChapter(raw: String, start: Int, end: Int): ConversationChapter? = runCatching {
    val jsonText = Regex("\\{[\\s\\S]*\\}").find(raw)?.value ?: return@runCatching null
    val json = JSONObject(jsonText)
    val title = json.optString("title").ifBlank { return@runCatching null }
    val summary = json.optString("summary").ifBlank { return@runCatching null }
    val facts = json.optJSONArray("facts").toStrings().filter { it.isNotBlank() }.take(3)
    val open = json.optJSONArray("open").toStrings().filter { it.isNotBlank() }.take(2)
    val enriched = buildString {
        append(summary)
        if (facts.isNotEmpty()) append(" 明确事实：${facts.joinToString("；")}")
        if (open.isNotEmpty()) append(" 待确认：${open.joinToString("；")}")
    }
    ConversationChapter(title.take(28), enriched.take(220), start, end)
}.getOrNull()

/** 分词后要去掉的通用词：它们在每段对话里都出现，不构成句子特征。 */
internal val COMMON_MEMORY_WORDS = setOf("用户", "课堂", "内容", "学习", "总结", "问题", "讲解", "the", "and", "for", "with", "this", "that")
