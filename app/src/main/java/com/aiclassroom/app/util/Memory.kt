package com.aiclassroom.app.util

/**
 * 记忆卡片与知识库检索。
 *
 * - 记忆：从模型输出里解析出 LearningMemoryPack，合并去重，按关键词召回
 * - 知识库：分块、角色卡解析（JSON / PNG 内嵌）、按关键词召回片段
 */

import com.aiclassroom.app.model.ChatMessage
import com.aiclassroom.app.model.KnowledgeFile
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import com.aiclassroom.app.model.LearningMemoryPack
import com.aiclassroom.app.model.MemoryCard
import com.aiclassroom.app.util.CHARACTER_CARD_CONTEXT_LIMIT
import com.aiclassroom.app.util.KNOWLEDGE_CHUNK_SIZE
import com.aiclassroom.app.util.KNOWLEDGE_SNIPPET_RECALL_LIMIT
import com.aiclassroom.app.util.KNOWLEDGE_SNIPPET_CONTEXT_LIMIT
import com.aiclassroom.app.util.CORE_PREMISE_LIMIT
import com.aiclassroom.app.util.MEMORY_CARD_BUILD_MESSAGE_LIMIT
import com.aiclassroom.app.model.KnowledgeSnippet

internal fun parseLearningMemoryPack(raw: String, start: Int, end: Int): LearningMemoryPack? = runCatching {
    val jsonText = Regex("""\{[\s\S]*\}""").find(raw)?.value ?: return@runCatching null
    val json = JSONObject(jsonText)
    val premises = json.optJSONArray("corePremises").toStrings()
        .map { it.replace(Regex("\\s+"), " ").trim() }
        .filter { it.length in 4..120 }
        .distinct()
        .take(CORE_PREMISE_LIMIT)
    val cardsJson = json.optJSONArray("cards") ?: JSONArray()
    val cards = List(cardsJson.length()) { index ->
        val item = cardsJson.getJSONObject(index)
        val title = item.optString("title").replace(Regex("\\s+"), " ").trim().take(36)
        val summary = item.optString("summary").replace(Regex("\\s+"), " ").trim().take(260)
        val keywords = item.optJSONArray("keywords").toStrings().flatMap { extractKeywords(it) }.distinct().take(12)
        MemoryCard(
            title.ifBlank { "长期记忆" },
            summary,
            keywords,
            item.optString("type", "concept").ifBlank { "concept" },
            item.optInt("priority", 1).coerceIn(1, 5),
            start,
            end,
            normalizeVisibility(item.optString("visibility", inferVisibility(summary))),
            item.optJSONArray("knownBy").toStrings().map { name -> name.trim() }.filter { name -> name.isNotBlank() }.distinct().take(12),
            item.optJSONArray("unknownBy").toStrings().map { name -> name.trim() }.filter { name -> name.isNotBlank() }.distinct().take(12),
            item.optString("evidence", "").replace(Regex("\\s+"), " ").trim().take(160)
        )
    }.filter { it.summary.isNotBlank() }
    LearningMemoryPack(premises, cards)
}.getOrNull()

internal fun localLearningMemoryPack(messages: List<ChatMessage>): LearningMemoryPack {
    val recent = messages.takeLast(MEMORY_CARD_BUILD_MESSAGE_LIMIT)
    val topicLine = recent.lastOrNull { it.role == "user" }?.text?.replace(Regex("\\s+"), " ")?.take(80).orEmpty()
    val fullText = recent.joinToString(" ") { it.text }
    val keywords = extractKeywords(fullText)
    val visibility = inferVisibility(fullText)
    val premises = listOfNotNull(
        topicLine.takeIf { it.isNotBlank() }?.let { "最近用户正在围绕“$it”继续学习。" },
        keywords.take(5).joinToString("、").takeIf { it.isNotBlank() }?.let { "近期高频主题包括：$it。" }
    )
    val card = MemoryCard(
        title = topicLine.ifBlank { "近期课堂内容" }.take(28),
        summary = recent.joinToString(" ") { it.text }.replace(Regex("\\s+"), " ").take(220).ifBlank { "暂无可整理内容。" },
        keywords = keywords.take(12),
        type = if (visibility == "public") "concept" else "narrative",
        priority = 1,
        startIndex = (messages.size - recent.size).coerceAtLeast(0),
        endIndex = messages.lastIndex.coerceAtLeast(0),
        visibility = visibility,
        knownBy = if (visibility == "public") emptyList() else listOf("作者"),
        unknownBy = if (visibility == "public") emptyList() else listOf("未获知该秘密的角色"),
        evidence = if (visibility == "public") "公开课堂内容" else "用户叙述中出现心理、暗中、幕后或对方不知道等私密信号"
    )
    return LearningMemoryPack(premises, listOf(card).filter { it.summary.isNotBlank() })
}

internal fun mergeMemoryCards(cards: List<MemoryCard>): List<MemoryCard> = cards
    .filter { it.summary.isNotBlank() }
    .groupBy { "${it.title.lowercase(Locale.ROOT)}|${it.visibility}|${it.knownBy.joinToString(",")}|${it.unknownBy.joinToString(",")}" }
    .map { (_, group) ->
        val latest = group.last()
        latest.copy(
            keywords = group.flatMap { it.keywords }.distinct().take(14),
            priority = group.maxOf { it.priority },
            knownBy = group.flatMap { it.knownBy }.distinct().take(16),
            unknownBy = group.flatMap { it.unknownBy }.distinct().take(16),
            evidence = group.map { it.evidence }.lastOrNull { it.isNotBlank() }.orEmpty()
        )
    }

internal fun recallMemoryCards(cards: List<MemoryCard>, query: String, limit: Int): List<MemoryCard> {
    val queryWords = extractKeywords(query).toSet()
    if (queryWords.isEmpty()) return cards.sortedByDescending { it.priority }.take(limit)
    return cards.map { card ->
        val titleWords = extractKeywords(card.title).toSet()
        val summaryWords = extractKeywords(card.summary).toSet()
        val keywordHits = card.keywords.count { it in queryWords } * 4
        val titleHits = titleWords.count { it in queryWords } * 3
        val summaryHits = summaryWords.count { it in queryWords }
        val score = keywordHits + titleHits + summaryHits + card.priority
        card to score
    }.filter { it.second > 0 }
        .sortedWith(compareByDescending<Pair<MemoryCard, Int>> { it.second }.thenByDescending { it.first.endIndex })
        .map { it.first }
        .take(limit)
}

internal fun extractKeywords(text: String): List<String> {
    val cleaned = text.lowercase(Locale.ROOT)
    val latin = Regex("[a-zA-Z][a-zA-Z0-9_+#.-]{1,28}").findAll(cleaned).map { it.value }.toList()
    val chinese = Regex("[\\u4e00-\\u9fa5]{2,8}").findAll(cleaned).map { it.value }.toList()
    val stop = setOf("这个", "那个", "什么", "为什么", "怎么", "如何", "可以", "进行", "一个", "我们", "你是", "用户", "讲师", "回答", "问题", "课堂", "学习", "内容", "需要")
    return (latin + chinese)
        .map { it.trim('，', '。', '、', '：', ':', ';', '；', '.', ',', '?', '？', '!', '！', '“', '”', '"') }
        .filter { it.length >= 2 && it !in stop && !it.all { ch -> ch.isDigit() } }
        .groupingBy { it }.eachCount()
        .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.length })
        .map { it.key }
        .take(40)
}

internal fun characterCardName(file: KnowledgeFile): String =
    Regex("(?m)^#\\s+(.+)$").find(file.content)?.groupValues?.getOrNull(1)?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: file.name.substringBeforeLast('.').ifBlank { "未命名角色" }

internal fun normalizeCharacterCardJson(raw: String): String {
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return raw
    val keys = listOf("name", "description", "personality", "scenario", "first_mes", "mes_example", "system_prompt", "data")
    val source = root.optJSONObject("data") ?: root
    return buildString {
        keys.forEach { key ->
            val value = source.optString(key, "").trim()
            if (value.isNotBlank()) appendLine("## ${key.replace('_', ' ').replaceFirstChar { it.uppercase() }}\n$value")
        }
        if (isBlank()) append(raw)
    }
}

internal fun extractPngCardText(bytes: ByteArray): String {
    val raw = bytes.toString(Charsets.ISO_8859_1)
    val markers = listOf("chara", "character", "description", "personality", "scenario", "first_mes")
    val hits = markers.flatMap { marker ->
        Regex("(?i)$marker[^\\u0000-\\u001f]{0,4000}").findAll(raw).map { it.value }.toList()
    }
    return hits.joinToString("\n").replace(Regex("[^\\u0009\\u000A\\u000D\\u0020-\\u007E\\u4E00-\\u9FFF]"), " ").trim()
}

internal fun formatCharacterCards(cards: List<KnowledgeFile>): String {
    if (cards.isEmpty()) return ""
    val perCardLimit = (CHARACTER_CARD_CONTEXT_LIMIT / cards.size).coerceIn(700, 2200)
    return truncatePromptSection(cards.joinToString("\n\n") { file ->
        "[角色：${characterCardName(file)}｜来源：${file.name}]\n" + truncatePromptSection(file.content, perCardLimit)
    }, CHARACTER_CARD_CONTEXT_LIMIT)
}

internal fun splitKnowledgeChunks(text: String, chunkSize: Int = KNOWLEDGE_CHUNK_SIZE): List<String> {
    val paragraphs = text.replace("\r\n", "\n").split(Regex("\\n\\s*\\n"))
        .map { it.trim() }.filter { it.isNotBlank() }
    if (paragraphs.isEmpty()) return emptyList()
    val chunks = mutableListOf<String>()
    val current = StringBuilder()
    fun flush() { if (current.isNotBlank()) { chunks += current.toString().trim(); current.clear() } }
    paragraphs.forEach { paragraph ->
        if (paragraph.length > chunkSize) {
            flush()
            paragraph.chunked(chunkSize).forEach { chunks += it }
        } else {
            if (current.length + paragraph.length + 2 > chunkSize) flush()
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(paragraph)
        }
    }
    flush()
    return chunks
}

internal fun recallKnowledgeSnippets(files: List<KnowledgeFile>, query: String, limit: Int = KNOWLEDGE_SNIPPET_RECALL_LIMIT): List<KnowledgeSnippet> {
    val queryWords = extractKeywords(query).take(24)
    val candidates = files.filter { it.kind != "character_card" }.flatMap { file ->
        splitKnowledgeChunks(file.content).mapIndexed { index, chunk ->
            val lowerChunk = chunk.lowercase(Locale.ROOT)
            val hits = queryWords.fold(0) { total, word -> total + if (lowerChunk.contains(word)) 2 else 0 }
            val nameHits = queryWords.count { word -> file.name.lowercase(Locale.ROOT).contains(word) } * 3
            KnowledgeSnippet(file.name, chunk, hits + nameHits - (index / 12))
        }
    }
    if (candidates.isEmpty()) return emptyList()
    val ranked = candidates.sortedByDescending { it.score }
    val selected = if (ranked.first().score > 0) {
        ranked
    } else {
        candidates.groupBy { it.fileName }
            .values
            .mapNotNull { it.firstOrNull() }
    }
    val output = mutableListOf<KnowledgeSnippet>()
    var used = 0
    selected.forEach { snippet ->
        if (output.size < limit && used + snippet.text.length <= KNOWLEDGE_SNIPPET_CONTEXT_LIMIT) {
            output += snippet
            used += snippet.text.length
        }
    }
    return output
}

internal fun normalizeVisibility(raw: String): String = when (raw.trim().lowercase(Locale.ROOT)) {
    "author_private", "private", "author", "作者私密", "幕后", "幕后设定" -> "author_private"
    "character_private", "character", "角色私密", "心理", "心理活动" -> "character_private"
    else -> "public"
}
internal fun inferVisibility(text: String): String {
    val privateSignals = listOf("作者私密", "幕后", "暗中", "偷偷", "秘密", "心理", "内心", "策划", "计划", "对方不知道", "不知道", "不可让", "不能让", "隐瞒", "伪装", "假装", "背叛")
    val characterSignals = listOf("心理", "内心", "想法", "打算", "盘算")
    if (privateSignals.none { text.contains(it, ignoreCase = true) }) return "public"
    return if (characterSignals.any { text.contains(it, ignoreCase = true) }) "character_private" else "author_private"
}
