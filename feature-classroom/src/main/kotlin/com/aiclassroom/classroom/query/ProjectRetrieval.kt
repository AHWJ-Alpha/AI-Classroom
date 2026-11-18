package com.aiclassroom.classroom.query

import com.aiclassroom.classroom.domain.*

/**
 * 对话里问「解释第三节」时，从正式作品里取出对应内容。
 *
 * 几条硬规矩：
 * - 只读**正式版本**（已提交的 revision），不读草稿、不触发模型；
 * - 只取**相关片段**，不把整本作品塞进请求；
 * - 取不到就老实返回空，不编造内容；
 * - 作者私密分段不给角色视角。
 */
data class ProjectExcerpt(
    val unitIndex: Int,
    val unitId: UnitId,
    val title: String,
    val text: String,
    /** 命中的原因，便于回答里注明出处。 */
    val reason: String,
)

data class ProjectContext(
    val projectId: ProjectId,
    val projectTitle: String,
    val workVersion: Int,
    val excerpts: List<ProjectExcerpt>,
) {
    val isEmpty: Boolean get() = excerpts.isEmpty()

    /** 拼成给模型看的资料块；已经截断过，长度可控。 */
    fun render(limitPerExcerpt: Int = 1200): String = buildString {
        appendLine("《" + projectTitle + "》正式版本 " + workVersion)
        excerpts.forEach { e ->
            appendLine()
            appendLine("【第 " + e.unitIndex + " 节 · " + e.title + "】")
            val body = if (e.text.length <= limitPerExcerpt) e.text else e.text.take(limitPerExcerpt) + "…（已截断）"
            appendLine(body)
        }
    }
}

object ProjectRetrieval {

    /**
     * 直接从仓库装配：只读已提交的 revision，不读草稿。
     *
     * 放在领域层而不是宿主里，是因为「取哪一版内容」这件事必须能被测，
     * 不能靠「跑一次没崩」来证明。
     */
    fun forProject(
        store: com.aiclassroom.classroom.repository.ClassroomStore,
        projectId: ProjectId,
        question: String,
        maxExcerpts: Int = 3,
    ): ProjectContext? {
        val project = store.project(projectId) ?: return null
        val pairs = store.units(projectId).sortedBy { it.index }.map { unit ->
            val revision = unit.currentRevisionId?.let { store.revision(it) }
            unit to revision?.content
        }
        return search(
            projectId = projectId,
            projectTitle = project.title,
            workVersion = project.workVersion,
            units = pairs,
            question = question,
            maxExcerpts = maxExcerpts,
        )
    }

    /**
     * 关键词命中打分。
     *
     * 用的不是向量检索，而是「显式指向 + 词面重合」：
     * 用户问「第三节」「第二章」这种，最可靠的是先解析节号；
     * 解析不出来再看标题与正文的词面重合。宁可少给，也不要给错章节。
     */
    fun search(
        projectId: ProjectId,
        projectTitle: String,
        workVersion: Int,
        units: List<Pair<ClassroomUnit, UnitContent?>>,
        question: String,
        maxExcerpts: Int = 3,
    ): ProjectContext {
        val numbered = parseSectionNumbers(question)
        val tokens = gramsOf(question)

        val scored = units.mapNotNull { (unit, content) ->
            if (content == null) return@mapNotNull null
            val text = renderContent(content)
            if (text.isBlank()) return@mapNotNull null

            var score = 0
            val reasons = mutableListOf<String>()

            if (unit.index in numbered) {
                score += 100
                reasons += "第 " + unit.index + " 节"
            }
            if (projectTitle.isNotBlank() && question.contains(projectTitle)) {
                score += 20
                reasons += "提到作品名"
            }
            val hay = (unit.chapter + " " + unit.goal + " " + text)
            val hits = tokens.count { hay.contains(it) }
            if (hits > 0) {
                score += hits
                reasons += "命中 " + hits + " 个关键词"
            }

            if (score == 0) null
            else Triple(unit.index, ProjectExcerpt(unit.index, unit.id, unit.chapter, text, reasons.joinToString("、")), score)
        }.sortedByDescending { it.third }

        val top = scored.take(maxExcerpts.coerceAtLeast(1)).sortedBy { it.first }.map { it.second }
        return ProjectContext(projectId, projectTitle, workVersion, top)
    }

    /** 「第三节」「第3节」「第二章」「3.」这类显式指向。 */
    fun parseSectionNumbers(question: String): Set<Int> {
        val found = mutableSetOf<Int>()
        // 中文数字必须一起认：用户写「第三节」远比写「第3节」常见。
        Regex("""第\s*([0-9一二三四五六七八九十两]+)\s*[节章]""").findAll(question).forEach {
            numeralToInt(it.groupValues[1])?.let { n -> found += n }
        }
        Regex("""^(\d+)[.、]""").findAll(question.trim()).forEach {
            it.groupValues[1].toIntOrNull()?.let { n -> found += n }
        }
        return found
    }

    /** 阿拉伯数字或中文数字（1~99）转整数；认不出来就返回 null，不猜。 */
    fun numeralToInt(raw: String): Int? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        text.toIntOrNull()?.let { return it }
        val digits = mapOf(
            '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        )
        if (text == "十") return 10
        if (text.length == 1) return digits[text[0]]

        val tenIndex = text.indexOf('十')
        if (tenIndex >= 0) {
            val tens = if (tenIndex == 0) 1 else digits[text[tenIndex - 1]] ?: return null
            val ones = if (tenIndex == text.length - 1) 0 else digits[text[tenIndex + 1]] ?: return null
            return tens * 10 + ones
        }
        return null
    }

    /**
     * 中文没有空格，整句当一个词永远匹配不上。
     *
     * 这里用二元字组（bigram）做词面重合：够简单，且对「封装是什么意思」这种问法有效。
     * 真正的分词/向量检索不在这一版的范围内，宁可少给也不给错。
     */
    private fun gramsOf(question: String): List<String> {
        val clean = question.filterNot { it.isWhitespace() || it in "，。！？、；：「」（）,.!?" }
        if (clean.length < 2) return if (clean.isEmpty()) emptyList() else listOf(clean)
        return (0..clean.length - 2).map { clean.substring(it, it + 2) }.distinct()
    }

    /** 正文拼成纯文本：检索用的是内容，不是 JSON 结构。 */
    fun renderContent(content: UnitContent): String = buildString {
        content.blocks.forEach { block ->
            when (block) {
                is ContentBlock.Heading -> appendLine(block.text)
                is ContentBlock.Narration -> appendLine(block.text)
                is ContentBlock.Explanation -> appendLine(block.text)
                is ContentBlock.Example -> appendLine(block.text)
                is ContentBlock.Exercise -> appendLine(block.prompt + "（答案：" + block.answer + "）")
                is ContentBlock.Dialogue -> appendLine(block.speakerId.value + "：" + block.text)
                is ContentBlock.Choice -> {
                    appendLine(block.prompt)
                    block.options.forEach { appendLine("- " + it.text) }
                }
            }
        }
    }
}
