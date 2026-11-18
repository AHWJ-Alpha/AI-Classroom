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
            normalizeVisibility(item.optString("visibility", "author_private")),
            item.optJSONArray("knownBy").toStrings().map { name -> name.trim() }.filter { name -> name.isNotBlank() }.distinct().take(12),
            item.optJSONArray("unknownBy").toStrings().map { name -> name.trim() }.filter { name -> name.isNotBlank() }.distinct().take(12),
            item.optString("evidence", "").replace(Regex("\\s+"), " ").trim().take(160),
            item.optString("id", "").trim().take(100)
        )
    }.filter { it.summary.isNotBlank() }
    LearningMemoryPack(premises, cards)
}.getOrNull()

/** 本地兜底只保存用户原话，不把模型自己的回复猜测成已确认事实。 */
internal fun localLearningMemoryPack(
    messages: List<ChatMessage>, startIndex: Int = (messages.size - MEMORY_CARD_BUILD_MESSAGE_LIMIT).coerceAtLeast(0)
): LearningMemoryPack {
    val cards = messages.drop(startIndex).mapIndexedNotNull { offset, message ->
        if (message.role != "user" || message.text.isBlank()) return@mapIndexedNotNull null
        val index = startIndex + offset
        MemoryCard(
            title = "用户原话 $index",
            summary = message.text.replace(Regex("\\s+"), " ").take(260),
            keywords = extractKeywords(message.text).take(12),
            type = "quotation", priority = 1, startIndex = index, endIndex = index,
            visibility = "character_private",
            knownBy = message.audience,
            evidence = "用户在第 ${index + 1} 条消息中直接表达；原话中的假设不是已发生事实",
            factId = "quotation:$index"
        )
    }
    return LearningMemoryPack(emptyList(), cards)
}

internal fun memoryFactKey(card: MemoryCard): String = card.factId.ifBlank {
    "${card.type}:${card.title.trim().lowercase(Locale.ROOT)}"
}

/** 保存事实修订历史，供从旧消息创建分支时按时间取状态；同窗口重试幂等。 */
internal fun mergeMemoryCards(cards: List<MemoryCard>): List<MemoryCard> = cards
    .filter { it.summary.isNotBlank() }
    .groupBy { "${memoryFactKey(it)}@${it.endIndex}" }
    .values.map { versions -> versions.last().copy(
        keywords = versions.flatMap { it.keywords }.distinct().take(14),
        knownBy = versions.last().knownBy.distinct(), unknownBy = versions.last().unknownBy.distinct()
    ) }.sortedBy { it.endIndex }

/** 必须先选事实最新版本，再做可见性过滤，防止已失效的公开版本重新浮出。 */
internal fun latestMemoryCards(cards: List<MemoryCard>): List<MemoryCard> = cards
    .groupBy(::memoryFactKey).values.map { versions -> versions.maxByOrNull { it.endIndex }!! }

internal fun memoryVisibleTo(card: MemoryCard, viewerIds: Set<String>?): Boolean {
    if (viewerIds == null) return true // 作者视角
    fun matches(names: List<String>) = names.any { name -> viewerIds.any { it.equals(name, true) } }
    if (matches(card.unknownBy)) return false
    return when (normalizeVisibility(card.visibility)) {
        "public" -> true
        "character_private" -> matches(card.knownBy)
        else -> false
    }
}

/** 先筛可见范围，再按相关性排序；优先级不能让零命中的卡片挤进上下文。 */
internal fun recallMemoryCards(
    cards: List<MemoryCard>, query: String, limit: Int, viewerIds: Set<String>? = null
): List<MemoryCard> {
    val visible = latestMemoryCards(cards).filter { memoryVisibleTo(it, viewerIds) }
    val queryWords = extractKeywords(query).toSet()
    if (queryWords.isEmpty()) return visible.sortedWith(
        compareByDescending<MemoryCard> { it.priority }.thenByDescending { it.endIndex }
    ).take(limit)
    return visible.mapNotNull { card ->
        val haystack = "${card.title} ${card.summary} ${card.keywords.joinToString(" ")}".lowercase(Locale.ROOT)
        val hits = queryWords.count { haystack.contains(it) }
        if (hits == 0) null else card to (hits * 4 + card.priority)
    }.sortedWith(compareByDescending<Pair<MemoryCard, Int>> { it.second }.thenByDescending { it.first.endIndex })
        .take(limit).map { it.first }
}

internal fun extractKeywords(text: String): List<String> {
    val cleaned = text.lowercase(Locale.ROOT)
    val latin = Regex("[a-zA-Z][a-zA-Z0-9_+#.-]{1,28}").findAll(cleaned).map { it.value }.toList()
    val chinese = Regex("[\\u4e00-\\u9fa5]{2,8}").findAll(cleaned).flatMap { match ->
        // 中文没有空格；保留整段及二字词，使“钥匙在哪里”能召回“钥匙藏在阁楼”。
        (listOf(match.value) + match.value.windowed(2)).asSequence()
    }.toList()
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

/** 索引只保留名字，避免把未出场角色的秘密作为索引注入。 */
internal fun formatCharacterCardIndex(cards: List<KnowledgeFile>): String =
    truncatePromptSection(cards.joinToString("、") { characterCardName(it) }, 1200)

/** 每个设定小节公平分配预算，避免口吻、边界、对话示例因排在末尾而被整体裁掉。 */
internal fun compactCharacterCard(content: String, budget: Int): String {
    if (content.length <= budget) return content
    val sections = content.split(Regex("(?m)(?=^#{1,3}\\s)" )).filter { it.isNotBlank() }
    if (sections.size <= 1) {
        val marker = "\n[设定已节选]\n"
        val available = (budget - marker.length).coerceAtLeast(0)
        return content.take(available * 2 / 3) + marker + content.takeLast(available - available * 2 / 3)
    }
    val allowance = ((budget - sections.size * 2) / sections.size).coerceAtLeast(0)
    return sections.joinToString("\n\n") { it.trim().take(allowance) }.take(budget)
}

internal fun formatCharacterCards(cards: List<KnowledgeFile>, query: String = ""): String {
    if (cards.isEmpty()) return ""
    val words = extractKeywords(query)
    val ranked = cards.map { file ->
        val name = characterCardName(file)
        val named = if (query.contains(name, ignoreCase = true)) 100 else 0
        val text = file.content.lowercase(Locale.ROOT)
        file to (named + words.count { text.contains(it) })
    }.sortedByDescending { it.second }
    val namedCards = cards.filter { query.contains(characterCardName(it), ignoreCase = true) }
    // “角色12”不能同时被当成“角色1”出场；优先最长的显式名字。
    val exact = namedCards.filter { candidate ->
        val name = characterCardName(candidate)
        namedCards.none { other ->
            val longer = characterCardName(other)
            longer.length > name.length && longer.contains(name, ignoreCase = true)
        }
    }
    val selected = if (exact.isNotEmpty()) exact.take(4) else ranked.filter { it.second > 0 }.take(4).map { it.first }
        .ifEmpty { if (cards.size == 1) cards else emptyList() }
    if (selected.isEmpty()) return ""
    val budget = (CHARACTER_CARD_CONTEXT_LIMIT / selected.size - 100).coerceAtLeast(200)
    return selected.joinToString("\n\n") { file ->
        "[角色：${characterCardName(file)}]\n" + compactCharacterCard(file.content, budget)
    }.take(CHARACTER_CARD_CONTEXT_LIMIT)
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
    val selected = ranked.filter { it.score > 0 }
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
    "public", "公开" -> "public"
    else -> "author_private"
}
internal fun inferVisibility(text: String): String {
    val privateSignals = listOf("作者私密", "幕后", "暗中", "偷偷", "心理活动", "内心独白", "对方不知道", "不可让", "不能让", "隐瞒", "伪装", "假装", "背叛")
    val characterSignals = listOf("心理", "内心", "想法", "打算", "盘算")
    if (privateSignals.none { text.contains(it, ignoreCase = true) }) return "public"
    return if (characterSignals.any { text.contains(it, ignoreCase = true) }) "character_private" else "author_private"
}
