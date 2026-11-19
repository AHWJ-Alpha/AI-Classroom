package com.aiclassroom.classroom.query

import com.aiclassroom.classroom.domain.SnapshotMaterial

/**
 * 资料检索：把长资料切块，只把与当前任务相关的块交给模型。
 *
 * 之前是「每份资料取前 4000 字」—— 一本讲义后半部分永远进不了上下文，
 * 而用户恰恰可能问的就是后半部分。
 *
 * 不做向量检索：用二元字组重合就够这次的目标（中文没有词边界，整句匹配没用）。
 */
object MaterialRetrieval {

    data class Excerpt(
        val name: String,
        val chunkIndex: Int,
        val text: String,
        val score: Int,
    )

    /** 中文按二元字组切，够用且不依赖分词库。 */
    fun grams(text: String): List<String> {
        val clean = text.filterNot { it.isWhitespace() || it in "，。！？、；：「」（）,.!?" }
        if (clean.length < 2) return if (clean.isEmpty()) emptyList() else listOf(clean)
        return (0..clean.length - 2).map { clean.substring(it, it + 2) }.distinct()
    }

    /** 按空行分段，再按长度合并成块；不切碎句子。 */
    fun chunk(content: String, chunkSize: Int = 800): List<String> {
        if (content.isBlank()) return emptyList()
        val paragraphs = content.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val chunks = mutableListOf<String>()
        val buffer = StringBuilder()
        for (p in paragraphs) {
            if (buffer.isNotEmpty() && buffer.length + p.length + 2 > chunkSize) {
                chunks += buffer.toString()
                buffer.clear()
            }
            if (p.length > chunkSize) {
                // 单段超长：硬切，保证后半段也能被检索到。
                var start = 0
                while (start < p.length) {
                    val end = minOf(start + chunkSize, p.length)
                    chunks += p.substring(start, end)
                    start = end
                }
                continue
            }
            if (buffer.isNotEmpty()) buffer.append("\n\n")
            buffer.append(p)
        }
        if (buffer.isNotEmpty()) chunks += buffer.toString()
        return chunks
    }

    /**
   * 选与 query 最相关的块。
   *
   * 每份资料至少保留一块（命中为 0 时取第一块），
   * 这样「没命中任何关键词」的资料仍会以开头出现，不会被静默丢掉。
   */
    fun select(
        materials: List<SnapshotMaterial>,
        query: String,
        maxChunksPerMaterial: Int = 2,
        chunkSize: Int = 800,
        totalBudget: Int = 6000,
    ): List<Excerpt> {
        if (materials.isEmpty()) return emptyList()
        val queryGrams = grams(query)
        val picked = mutableListOf<Excerpt>()

        for (m in materials) {
            val chunks = chunk(m.content, chunkSize)
            if (chunks.isEmpty()) continue
            val scored = chunks.mapIndexed { index, text ->
                val score = if (queryGrams.isEmpty()) 0 else queryGrams.count { text.contains(it) }
                Excerpt(m.name, index, text, score)
            }
            val top = if (queryGrams.isEmpty()) {
                scored.take(1)
            } else {
                scored.sortedWith(compareByDescending<Excerpt> { it.score }.thenBy { it.chunkIndex })
                    .take(maxChunksPerMaterial.coerceAtLeast(1))
            }
            // 按原文顺序返回，模型读起来才不会跳来跳去。
            picked += top.sortedBy { it.chunkIndex }
        }

        var used = 0
        val budgeted = mutableListOf<Excerpt>()
        for (e in picked) {
            if (used + e.text.length > totalBudget && budgeted.isNotEmpty()) break
            budgeted += e
            used += e.text.length
        }
        return budgeted
    }
}
