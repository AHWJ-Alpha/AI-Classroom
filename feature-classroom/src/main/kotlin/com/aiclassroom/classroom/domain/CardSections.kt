package com.aiclassroom.classroom.domain

/**
 * 把角色卡正文按 Markdown 标题拆成「公开人格」与「作者私密」两部分。
 *
 * 为什么必须程序拆：说明里明确要求「角色卡中以 Markdown 标题标记的作者私密小节会被排除」。
 * 只靠模型自觉忽略，迟早会在角色视角里漏出幕后设定。
 *
 * 判定规则故意保守：
 * - 只有 **Markdown 二级及以下标题**（## / ###）才作为分节依据，正文里的普通加粗不算；
 * - 标题里出现「作者」「私密」「幕后」「隐藏设定」等词 → 这一段归作者私密；
 * - 没有任何分节时，整段都当公开人格 —— 不去猜哪句是秘密。
 */
object CardSections {

    private val AUTHOR_KEYS = listOf("作者", "私密", "幕后", "隐藏设定", "仅作者", "author only", "private")

    data class Split(val persona: String, val authorPrivate: String) {
        val hasPrivate: Boolean get() = authorPrivate.isNotBlank()
    }

    fun split(content: String): Split {
        if (content.isBlank()) return Split("", "")

        val lines = content.replace("\r\n", "\n").split("\n")
        // 记录每个标题行的下标与级别
        val headings = lines.mapIndexedNotNull { index, line ->
            val match = Regex("""^(#{2,6})\s+(.*)$""").find(line)
            if (match == null) null else index to match.groupValues[2].trim()
        }

        // 没有任何分节：整段都是公开人格。
        if (headings.isEmpty()) return Split(content.trim(), "")

        val publicParts = mutableListOf<String>()
        val privateParts = mutableListOf<String>()

        fun bucketOf(index: Int): Boolean {
            // 找到该行之前最近的标题
            val owner = headings.lastOrNull { it.first <= index } ?: return false
            val title = owner.second.lowercase()
            return AUTHOR_KEYS.any { title.contains(it.lowercase()) }
        }

        for ((index, line) in lines.withIndex()) {
            if (headings.any { it.first == index }) continue
            if (line.isBlank()) continue
            if (bucketOf(index)) privateParts += line else publicParts += line
        }

        // 标题本身也保留在对应一侧，方便模型理解结构。
        for ((index, title) in headings) {
            val isPrivate = AUTHOR_KEYS.any { title.lowercase().contains(it.lowercase()) }
            if (isPrivate) privateParts += "## " + title else publicParts += "## " + title
        }

        return Split(publicParts.joinToString("\n").trim(), privateParts.joinToString("\n").trim())
    }
}
