package com.aiclassroom.app.model

/**
 * 应用的数据模型集中地。
 *
 * 原来它们散落在 MainActivity.kt 里（和 2000 行的 AIClassroomApp() 混在一起），
 * 任何想复用模型的工具函数都只能继续往里塞，文件于是越滚越大。
 * 搬到这里之后依赖方向是单向的：model ← util ← ui ← MainActivity。
 *
 * 这里的类型全部 internal：只在 app 模块内可见，不对外暴露。
 */

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.aiclassroom.feature.exam.ExamQuestion
import com.aiclassroom.feature.exam.ExamSession
import java.io.File

internal data class ChatMessage(
    val role: String,
    val text: String,
    val toolCallsJson: String? = null,
    val toolCallId: String? = null,
    val images: List<String> = emptyList()
)
internal data class BranchClass(val title: String, val source: String, val messages: MutableList<ChatMessage>, val memory: String, val context: MutableList<ChatMessage> = mutableStateListOf())
internal data class KnowledgeFile(
    val name: String,
    val type: String,
    val chars: Int,
    val preview: String,
    val content: String = preview,
    val kind: String = "reference"
)
internal data class ConversationChapter(val title: String, val summary: String, val startIndex: Int, val endIndex: Int)
internal data class MemoryCard(
    val title: String,
    val summary: String,
    val keywords: List<String>,
    val type: String,
    val priority: Int,
    val startIndex: Int,
    val endIndex: Int,
    val visibility: String = "public",
    val knownBy: List<String> = emptyList(),
    val unknownBy: List<String> = emptyList(),
    val evidence: String = ""
)
internal data class LearningMemoryPack(val corePremises: List<String>, val cards: List<MemoryCard>)
internal data class UpdateInfo(val version: String, val name: String, val url: String, val notes: String)
internal data class DrawStroke(val points: List<Offset>)
internal data class ThemePreset(val mode: String, val title: String, val subtitle: String, val primary: Long, val secondary: Long)
internal data class AppPalette(
    val page: Color,
    val surface: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val button: Color,
    val onButton: Color,
    val outline: Color,
    val primary: Color = button,
    val secondary: Color = button,
    val accent: Color = button
)
internal data class ClassroomConfig(
    val provider: String = "OpenAI",
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com/v1",
    val selectedModel: String = "gpt-4o-mini",
    val customModel: String = "",
    val modelChain: String = "gpt-4o-mini",
    val deepThinkingEnabled: Boolean = false,
    val deepThinkingModel: String = "",
    val reasoningEffort: String = "medium",
    // 采样温度：此前写死在 buildJson 里，配置工具改了也没处生效；现在随课堂保存并真正参与请求。
    val temperature: Double = 0.35,
    val activeChatModel: String = "",
    val visionProvider: String = "OpenAI",
    val visionApiKey: String = "",
    val visionBaseUrl: String = "https://api.openai.com/v1",
    val visionModel: String = "gpt-4o-mini",
    val ttsProvider: String = "OpenAI",
    val ttsApiKey: String = "",
    val ttsBaseUrl: String = "https://api.openai.com/v1",
    val ttsModel: String = "tts-1",
    val ttsVoice: String = "alloy",
    val ttsAutoRead: Boolean = false,
    val mentorName: String = "AI 讲师",
    val userAlias: String = "同学",
    val mentorPrompt: String = DEFAULT_MENTOR_PROMPT,
    val worldBook: String = "",
    val efficientMode: Boolean = true,
    val reverseConversation: Boolean = false,
    val themeMode: String = "custom",
    val interfaceMode: String = "system",
    val primaryColor: Long = 0xFFFF7A1A,
    val secondaryColor: Long = 0xFFE85D04
) {
    fun primaryModel(): String = orderedModels().firstOrNull().orEmpty()

    fun chatModels(): List<String> {
        val base = orderedModels()
        val selected = activeChatModel.trim()
        return if (selected.isBlank()) base else listOf(selected) + base.filterNot { it == selected }
    }

    fun orderedModels(): List<String> {
        val normalModels = modelChain
            .split('\n', ',', '，', ';', '；')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(customModel.ifBlank { selectedModel }) }
        val deepModels = if (deepThinkingEnabled && deepThinkingModel.isNotBlank()) listOf(deepThinkingModel.trim()) else emptyList()
        return (deepModels + normalModels + customModel + selectedModel).map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    fun visionModels(): List<String> = (listOf(visionModel) + orderedModels()).map { it.trim() }.filter { it.isNotBlank() }.distinct()
    fun visionApiKeyOrMain(): String = visionApiKey.ifBlank { apiKey }
    fun visionBaseUrlOrMain(): String = visionBaseUrl.ifBlank { baseUrl }
}

/** 记忆页只回看最近这些次考试；再多的历史没人翻，还会把整份存档撑大。 */
internal const val EXAM_RECORD_LIMIT = 50

/**
 * 一次考试的历史记录。
 *
 * [Classroom.exam] 只存「当前这份」考试，提交后会被覆盖，所以历史必须单独留一份。
 * [questions] 一并留档：只记题数的话，记忆页点开记录就看不出考了什么、当时怎么答的、参考解析是什么。
 * 时间戳是毫秒；[startedAt] 在会话刚出现时打点，[submittedAt] 是提交那一刻。
 */
internal data class ExamRecord(
    val id: String,
    val title: String,
    val startedAt: Long,
    val submittedAt: Long,
    val questionCount: Int,
    val answeredCount: Int,
    val submitted: Boolean,
    val questions: List<ExamQuestion> = emptyList()
)

internal data class Classroom(
    val name: String,
    val topic: String,
    val summary: String = "",
    val messages: MutableList<ChatMessage>,
    val branches: MutableList<BranchClass>,
    val memories: MutableList<String>,
    val corePremises: MutableList<String>,
    val memoryCards: MutableList<MemoryCard>,
    val chapters: MutableList<ConversationChapter>,
    val files: MutableList<KnowledgeFile>,
    val config: ClassroomConfig,
    /** 该课堂的考试存档（未提交 / 已提交都留在这里），随课堂一起持久化；旧存档没有该字段时为 null。 */
    val exam: ExamSession? = null,
    /** 历次考试记录，最新的在最后；旧存档没有该字段 → 空列表。 */
    val examRecords: List<ExamRecord> = emptyList()
)


internal const val DEFAULT_MENTOR_PROMPT = "你是一名耐心、结构清晰的 AI 讲师。默认使用中文教学，保持主线课程连续，并在必要时用 Markdown 和公式文本表达。"
internal const val MEMORY_PROMPT_LIMIT = 24
internal const val MEMORY_CONTEXT_LIMIT = 6000
