package com.aiclassroom.app.util

/**
 * 系统提示词的拼装。
 *
 * 一块一块拼：最高优先级的讲师人格、优先级提醒、对话一致性规则、回复边界规则，
 * 最后按 token 预算做截断。整条链路都是纯字符串处理，不碰 UI 也不碰状态。
 */

import com.aiclassroom.app.model.ChatMessage
import com.aiclassroom.app.model.Classroom
import com.aiclassroom.app.model.ClassroomConfig
import com.aiclassroom.app.model.DEFAULT_MENTOR_PROMPT
import com.aiclassroom.app.model.MEMORY_PROMPT_LIMIT
import com.aiclassroom.app.util.recallMemoryCards
import com.aiclassroom.app.util.recallKnowledgeSnippets
import com.aiclassroom.app.util.BRANCH_CONTEXT_LIMIT
import com.aiclassroom.app.util.MEMORY_CARD_RECALL_LIMIT
import com.aiclassroom.app.util.RECENT_MESSAGE_CONTEXT_LIMIT

internal fun mentorPriorityBlock(config: ClassroomConfig): String {
    val prompt = config.mentorPrompt.trim().ifBlank { DEFAULT_MENTOR_PROMPT }
    val mentorName = config.mentorName.trim().ifBlank { "AI 讲师" }
    val userAlias = config.userAlias.trim().ifBlank { "同学" }
    return buildString {
        appendLine("[最高优先级：用户自定义讲师人格]")
        appendLine(prompt)
        appendLine("你必须优先遵守以上用户保存的讲师人格、教学风格、表达方式和边界要求。")
        appendLine("即使后续长期记忆、知识库、考试工具或分支上下文更长，也只能作为参考资料，不能覆盖或弱化本段要求。")
        appendLine("讲师名字：$mentorName")
        append("用户称呼：$userAlias")
    }
}

internal fun promptPriorityReminder(config: ClassroomConfig): String {
    val mentorName = config.mentorName.trim().ifBlank { "AI 讲师" }
    val userAlias = config.userAlias.trim().ifBlank { "同学" }
    return "[优先级提醒] 回答时再次确认：始终优先遵守用户自定义讲师人格提示词；你是 $mentorName，并按设定称呼用户为 $userAlias。用户手写世界书用于补充固定设定；自动记忆、知识库和分支上下文只用于补充事实与连续性。"
}

internal fun conversationConsistencyRules(): String = """
[长期对话一致性规则]
1. 回答必须优先依据最近连续对话，其次参考长期记忆、章节索引和知识库。
2. 长期记忆和章节索引只代表压缩摘要，不能当作完整原文；若摘要与最近连续对话冲突，以最近连续对话为准。
3. 不要编造用户没有说过的学习目标、进度、结论、考试结果或知识库内容。
4. 当上下文不足、指代不清或记忆冲突时，先明确说明不确定并追问，不要硬接硬编。
5. 延续教学时要保持当前章节、当前问题和上一轮回答逻辑一致。
6. 若处于故事、角色扮演或剧情写作，模型知道的信息不等于角色知道的信息。角色只能依据公开事实、自己已知信息、明确证据和合理推理行动；作者私密、心理活动、暗中计划、幕后设定、对方不知道的信息不得让未知角色直接利用。
""".trimIndent()

internal fun responseBoundaryRules(): String = """
[回答边界与剧情推进规则]
1. 默认只回应用户当前明确表达的内容，不要在末尾强行添加“接下来你应该……”“我建议你……”等强引导式外拓。
2. 用户没有明确询问下一步、后续安排、继续剧情、如何推进、该怎么办时，不要替用户规划行动路线、立场结论或外部目标。
3. 可以自然收束回答，但不要用强迫式问题或引导句结尾；除非确实缺少继续回答所必需的关键信息。
4. 如果用户明确要求继续、推进、安排事件、生成后续剧情，或当前剧情明显停在必须由事件承接的位置，则根据世界书、角色可知信息、剧情因果和当前气氛策划一个完整合理事件。
5. 策划事件时必须保持角色知识边界：未知角色不能因为作者私密信息而提前防备；事件要通过可见线索、行动、误会、证据或合理推理推进。
6. 若不确定用户要“解释/评价”还是“继续剧情”，优先短促回应当前内容，不要主动夺取叙事方向。
""".trimIndent()

internal fun detectsForwardPlanningIntent(text: String): Boolean {
    val normalized = text.trim()
    if (normalized.isBlank()) return false
    val triggers = listOf(
        "下一步", "接下来", "往后", "后续", "继续", "推进", "然后呢", "该怎么办", "怎么办",
        "发生什么", "剧情发展", "安排事件", "设计事件", "策划", "完整事件", "往下写", "继续写",
        "续写", "下一段", "下一幕", "下一章", "怎么发展", "如何发展"
    )
    return triggers.any { normalized.contains(it, ignoreCase = true) }
}

internal fun promptMessagesForRoom(room: Classroom, history: List<ChatMessage>): List<ChatMessage> {
    val recent = history.takeLast(RECENT_MESSAGE_CONTEXT_LIMIT)
    val retrievalQuery = (history.takeLast(4).joinToString("\n") { it.text } + "\n" + room.topic).take(2400)
    val recalledCards = recallMemoryCards(room.memoryCards, retrievalQuery, MEMORY_CARD_RECALL_LIMIT)
    val knowledgeSnippets = recallKnowledgeSnippets(room.files, retrievalQuery)
    val latestUserText = history.lastOrNull { it.role == "user" }?.text.orEmpty()
    val wantsForwardPlanning = detectsForwardPlanningIntent(latestUserText)
    val state = buildString {
        appendLine("[当前课堂状态包]")
        appendLine("课堂名：${room.name}")
        appendLine("学习内容：${room.topic}")
        appendLine("总消息数：${history.size}")
        room.chapters.lastOrNull()?.let { appendLine("当前最近章节：${it.title}｜${it.summary}") }
        if (latestUserText.isNotBlank()) appendLine("最近用户问题：${latestUserText.take(600)}")
        appendLine(
            if (wantsForwardPlanning) {
                "用户意图判断：检测到明确的后续推进、事件策划或续写请求；可以在遵守世界书和角色知识边界的前提下生成完整合理事件。"
            } else {
                "用户意图判断：未检测到明确后续推进请求；请避免强引导式外拓，只回应当前内容，除非剧情已明显需要事件承接。"
            }
        )
        if (recalledCards.isNotEmpty()) {
            appendLine()
            appendLine("[本轮按关键词召回的世界书记忆卡；它们是历史摘要，不是完整原文]")
            appendLine("叙事隔离：可见性为 author_private 或 character_private 的内容只能作为作者/对应角色私密信息维持逻辑，未知角色不得提前知道、说破、防备或针对性行动。")
            recalledCards.forEach { card ->
                appendLine("- ${card.title}｜可见性：${card.visibility}｜知道者：${card.knownBy.ifEmpty { listOf("未标注") }.joinToString("、")}｜不知道者：${card.unknownBy.ifEmpty { listOf("未标注") }.joinToString("、")}｜依据：${card.evidence.ifBlank { "未标注" }}｜关键词：${card.keywords.take(8).joinToString("、")}｜${card.summary}")
            }
        }
        if (knowledgeSnippets.isNotEmpty()) {
            appendLine()
            appendLine("[本轮资料检索｜只依据下列片段回答，不要补造成资料中没有的事实]")
            knowledgeSnippets.forEach { snippet ->
                appendLine("- 来源：${snippet.fileName}")
                appendLine(snippet.text)
            }
        }
        append("请只把这个状态包作为定位信息，真正展开回答时优先遵守后面的最近连续对话。")
    }
    return listOf(ChatMessage("system", state)) + recent
}

internal fun promptMessagesForBranch(room: Classroom, context: List<ChatMessage>, history: List<ChatMessage>): List<ChatMessage> {
    val branchAnchor = context.takeLast(BRANCH_CONTEXT_LIMIT).joinToString("\n") { "${if (it.role == "user") "用户" else "AI"}：${it.text.take(700)}" }
    val retrievalQuery = (history.takeLast(4).joinToString("\n") { it.text } + "\n" + room.topic).take(2400)
    val knowledgeSnippets = recallKnowledgeSnippets(room.files, retrievalQuery)
    val state = buildString {
        appendLine("[分支课堂状态包]")
        appendLine("分支创建时的主课堂锚点如下，仅用于理解分支起点，不要把分支回答写回主课堂：")
        append(branchAnchor.ifBlank { "暂无锚点。" })
        if (knowledgeSnippets.isNotEmpty()) {
            appendLine()
            appendLine("[本轮资料检索｜只依据下列片段回答，不要补造成资料中没有的事实]")
            knowledgeSnippets.forEach { snippet ->
                appendLine("- 来源：${snippet.fileName}")
                appendLine(snippet.text)
            }
        }
    }
    return listOf(ChatMessage("system", state)) + history.takeLast(RECENT_MESSAGE_CONTEXT_LIMIT)
}

internal fun truncatePromptSection(text: String, limit: Int): String {
    val clean = text.trim()
    if (clean.length <= limit) return clean
    return clean.take(limit) + "\n[以上资料已按上下文预算截断，截断部分不可臆测。]"
}
