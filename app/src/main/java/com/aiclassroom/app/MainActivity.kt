package com.aiclassroom.app

import android.content.Context
import android.content.res.Configuration
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.media.MediaPlayer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.South
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AIClassroomTheme { AIClassroomApp() } }
    }
}

private enum class Tab(val title: String, val icon: ImageVector) {
    Class("课堂", Icons.Default.School),
    Branch("分支", Icons.Default.AccountTree),
    Memory("记忆", Icons.Default.Memory),
    Knowledge("知识库", Icons.Default.Bookmarks),
    Model("设置", Icons.Default.Key)
}

private data class ChatMessage(val role: String, val text: String)
private data class BranchClass(val title: String, val source: String, val messages: MutableList<ChatMessage>, val memory: String, val context: MutableList<ChatMessage> = mutableStateListOf())
private data class KnowledgeFile(val name: String, val type: String, val chars: Int, val preview: String, val content: String = preview)
private data class ConversationChapter(val title: String, val summary: String, val startIndex: Int, val endIndex: Int)
private data class ExamQuestion(val premise: String, val question: String, val answer: String = "", val unknown: Boolean = false)
private data class ExamSession(val title: String, val questions: MutableList<ExamQuestion>, val draft: String = "", val submitted: Boolean = false)
private data class UpdateInfo(val version: String, val name: String, val url: String, val notes: String)
private data class DrawStroke(val points: List<Offset>)
private data class ThemePreset(val mode: String, val title: String, val subtitle: String, val primary: Long, val secondary: Long)
private data class AppPalette(
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
private data class ClassroomConfig(
    val provider: String = "OpenAI",
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com/v1",
    val selectedModel: String = "gpt-4o-mini",
    val customModel: String = "",
    val modelChain: String = "gpt-4o-mini",
    val deepThinkingEnabled: Boolean = false,
    val deepThinkingModel: String = "",
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
    val efficientMode: Boolean = true,
    val reverseConversation: Boolean = false,
    val themeMode: String = "ocean",
    val interfaceMode: String = "system",
    val primaryColor: Long = 0xFF39C5BB,
    val secondaryColor: Long = 0xFF00AEEF
) {
    fun primaryModel(): String = orderedModels().firstOrNull().orEmpty()

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

private data class Classroom(
    val name: String,
    val topic: String,
    val messages: MutableList<ChatMessage>,
    val branches: MutableList<BranchClass>,
    val memories: MutableList<String>,
    val chapters: MutableList<ConversationChapter>,
    val files: MutableList<KnowledgeFile>,
    val config: ClassroomConfig
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AIClassroomApp() {
    val context = LocalContext.current
    val store = remember { ClassroomStore(context) }
    val initialClasses = remember { store.load() }
    var tab by remember { mutableStateOf(Tab.Class) }
    var classIndex by remember { mutableIntStateOf(store.loadIndex(initialClasses.lastIndex)) }
    var input by remember { mutableStateOf("") }
    var classMenuOpen by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(false) }
    var jumpToMessageIndex by remember { mutableStateOf<Int?>(null) }
    var examSession by remember { mutableStateOf<ExamSession?>(null) }
    var showNewDialog by remember { mutableStateOf(!store.hasSeenReleaseNotes(APP_VERSION)) }
    var activeBranchIndex by remember { mutableIntStateOf(-1) }
    var branchInput by remember { mutableStateOf("") }
    var branchLoading by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showManualDialog by remember { mutableStateOf(false) }
    var saveNotice by remember { mutableStateOf("所有内容自动保存在本机") }
    var modelStatus by remember { mutableStateOf("未获取模型") }
    var isLoading by remember { mutableStateOf(false) }
    val classes = remember { mutableStateListOf<Classroom>().apply { addAll(initialClasses) } }
    val models = remember { mutableStateListOf("gpt-4o-mini", "gpt-4o", "deepseek-chat", "qwen-plus") }
    val scope = rememberCoroutineScope()
    val memoryJobs = remember { java.util.IdentityHashMap<Classroom, Job>() }
    val memoryWatermarks = remember { java.util.IdentityHashMap<Classroom, Int>() }
    if (classIndex > classes.lastIndex) classIndex = classes.lastIndex.coerceAtLeast(0)
    val current = classes[classIndex]
    val activeModel = current.config.primaryModel()
    val activeModelChain = current.config.orderedModels()
    val systemDark = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val palette = remember(current.config, systemDark) { paletteFor(current.config, systemDark) }

    LaunchedEffect(Unit) {
        if (store.canShowUpdateToday()) {
            checkGitHubUpdate()?.let { info ->
                if (isRemoteVersionNewer(info.version, APP_VERSION)) {
                    store.markUpdateCheckedToday()
                    updateInfo = info
                }
            }
        }
    }

    fun persist(message: String = "已保存到本机") {
        store.save(classes, classIndex)
        saveNotice = message
    }

    fun replaceCurrent(room: Classroom, message: String = "已保存到本机") {
        classes[classIndex] = room
        persist(message)
    }
    fun replaceClassroomAt(index: Int, room: Classroom, message: String = "已保存到本机") {
        if (index in classes.indices) {
            classes[index] = room
            persist(message)
        }
    }

    fun addClassroom(copyFrom: Classroom? = null) {
        val room = newClassroom(classes.size + 1, copyFrom?.config ?: ClassroomConfig())
        classes.add(room)
        classIndex = classes.lastIndex
        tab = Tab.Class
        classMenuOpen = false
        persist(if (copyFrom == null) "新课堂已保存" else "已复制配置并新建课堂")
    }

    fun copyConfigFrom(sourceIndex: Int) {
        if (sourceIndex !in classes.indices || sourceIndex == classIndex) return
        replaceCurrent(current.copy(config = classes[sourceIndex].config), "已复制课堂配置")
    }

    fun deleteClassroom(deleteIndex: Int) {
        if (deleteIndex !in classes.indices) return
        if (classes.size == 1) {
            classes[0] = newClassroom(1, classes[0].config)
            classIndex = 0
        } else {
            classes.removeAt(deleteIndex)
            classIndex = classIndex.coerceAtMost(classes.lastIndex)
        }
        classMenuOpen = false
        persist("课堂已删除")
    }

    fun systemPrompt(room: Classroom): String {
        val knowledge = truncatePromptSection(room.files.joinToString("\n\n") { file ->
            "[${file.name} / ${file.type} / ${file.chars} 字]\n${file.content}"
        }, KNOWLEDGE_CONTEXT_LIMIT)
        val memory = truncatePromptSection(room.memories.takeLast(MEMORY_PROMPT_LIMIT).joinToString("\n"), MEMORY_CONTEXT_LIMIT)
        val safety = if (room.config.efficientMode) "高效模式：过滤 NSFW、色情、血腥、违法、仇恨和自伤内容。" else ""
        return buildString {
            appendLine(mentorPriorityBlock(room.config))
            appendLine("[课堂信息]")
            appendLine("讲师名字：${room.config.mentorName.ifBlank { "AI 讲师" }}")
            appendLine("对用户的称呼：${room.config.userAlias.ifBlank { "同学" }}")
            appendLine("课堂：${room.name}")
            appendLine("学习内容：${room.topic}")
            if (memory.isNotBlank()) {
                appendLine()
                appendLine("[长期记忆，仅作参考]")
                appendLine(memory)
            }
            if (knowledge.isNotBlank()) {
                appendLine()
                appendLine("[知识库资料，仅作参考]")
                appendLine(knowledge)
            }
            if (safety.isNotBlank()) {
                appendLine()
                appendLine(safety)
            }
            appendLine()
            append(promptPriorityReminder(room.config))
        }
    }

    fun branchSystemPrompt(room: Classroom, branch: BranchClass): String {
        val context = truncatePromptSection(branch.context.joinToString("\n") { "${if (it.role == "user") "用户" else "AI"}：${it.text}" }, BRANCH_CONTEXT_LIMIT_CHARS)
        return buildString {
            appendLine(systemPrompt(room))
            appendLine("[分支课堂]")
            appendLine("当前处于分支课堂。分支是与主课堂平行的长对话，不会改写主课堂；请只延续本分支。")
            appendLine("分支来源：${branch.source}")
            if (context.isNotBlank()) {
                appendLine("分支创建时的主课堂上下文：")
                appendLine(context)
            }
            if (branch.memory.isNotBlank()) appendLine("分支摘要：${branch.memory}")
            appendLine()
            append(promptPriorityReminder(room.config))
        }
    }

    fun scheduleMemoryBuild(room: Classroom, model: String) {
        val lastBuilt = memoryWatermarks.getOrPut(room) { room.chapters.maxOfOrNull { it.endIndex + 1 } ?: 0 }
        if (room.messages.size - lastBuilt < MEMORY_BATCH_MESSAGE_COUNT) return
        memoryJobs[room]?.cancel()
        memoryJobs[room] = scope.launch {
            delay(MEMORY_BATCH_DELAY_MS)
            val snapshot = room.messages.toList()
            val chapters = buildConversationChapters(snapshot, room.config, model)
            val branchMemory = room.memories.filterNot { it.startsWith(MAIN_MEMORY_PREFIX) }
            room.chapters.clear()
            room.chapters.addAll(chapters)
            room.memories.clear()
            room.memories.addAll(branchMemory.takeLast(MEMORY_PROMPT_LIMIT / 2))
            room.memories.addAll(chapters.takeLast(MEMORY_PROMPT_LIMIT).map { chapter ->
                "$MAIN_MEMORY_PREFIX ${chapter.title}: ${chapter.summary}"
            })
            memoryWatermarks[room] = snapshot.size
            persist("记忆已在后台整理并保存")
        }
    }

    fun sendMessage(seed: String? = null, allowExamTrigger: Boolean = true) {
        val text = (seed ?: input).trim()
        if (text.isBlank() || isLoading) return
        val room = current
        input = ""
        room.messages.add(ChatMessage("user", filterNsfw(text, room.config.efficientMode)))
        persist("对话已保存")
        isLoading = true
        scope.launch {
            val assistantIndex = room.messages.size
            room.messages.add(ChatMessage("assistant", ""))
            var streamed = ""
            val result = callChatStreamWithFallback(room.config, activeModelChain, systemPrompt(room) + "\n" + EXAM_TOOL_PROMPT + "\n" + EXAM_TOOL_PROMPT_V2, room.messages.dropLast(1).toList()) { delta ->
                streamed += delta
                room.messages[assistantIndex] = ChatMessage("assistant", filterNsfw(stripExamBlock(streamed), room.config.efficientMode))
            }
            val detectedExam = if (allowExamTrigger) detectExamSession(result, userRequestedExam = isExamRequest(text)) else null
            val visibleResult = stripExamBlock(result).ifBlank { if (detectedExam != null) "已为你准备好本次测试。" else result }
            room.messages[assistantIndex] = ChatMessage("assistant", filterNsfw(visibleResult, room.config.efficientMode))
            detectedExam?.let { examSession = it }
            isLoading = false
            persist("回复已保存，记忆将在后台整理")
            if (room.config.ttsAutoRead && visibleResult.isNotBlank()) speakText(context, room.config, visibleResult)
            scheduleMemoryBuild(room, activeModel)
        }
    }

    fun sendImageMessage(uri: Uri) {
        if (isLoading) return
        val room = current
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "课堂图片"
        room.messages.add(ChatMessage("user", "[图片] $name\n请分析这张图片，并结合当前课堂内容解答。"))
        persist("图片问题已保存")
        isLoading = true
        scope.launch {
            val dataUrl = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                    if (bytes.isEmpty()) "" else "data:$mime;base64,${Base64.getEncoder().encodeToString(bytes)}"
                }.getOrDefault("")
            }
            val assistantIndex = room.messages.size
            room.messages.add(ChatMessage("assistant", ""))
            val result = if (dataUrl.isBlank()) {
                "图片读取失败，请重新选择照片。"
            } else {
                callVisionWithFallback(room.config, room.config.visionModels(), systemPrompt(room), "请分析这张图片，并结合当前课堂内容解答。", dataUrl)
            }
            room.messages[assistantIndex] = ChatMessage("assistant", filterNsfw(result, room.config.efficientMode))
            isLoading = false
            persist("图片分析已保存")
            if (room.config.ttsAutoRead && result.isNotBlank()) speakText(context, room.config, result)
            scheduleMemoryBuild(room, activeModel)
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { sendImageMessage(it) }
    }

    fun sendBranchMessage(branchIndex: Int) {
        if (branchIndex !in current.branches.indices || branchLoading) return
        val text = branchInput.trim()
        if (text.isBlank()) return
        val room = current
        val branch = room.branches[branchIndex]
        branchInput = ""
        branch.messages.add(ChatMessage("user", filterNsfw(text, room.config.efficientMode)))
        persist("分支对话已保存")
        branchLoading = true
        scope.launch {
            val assistantIndex = branch.messages.size
            branch.messages.add(ChatMessage("assistant", ""))
            var streamed = ""
            val chatHistory = branch.context.takeLast(BRANCH_CONTEXT_LIMIT) + branch.messages.dropLast(1)
            val result = callChatStreamWithFallback(room.config, activeModelChain, branchSystemPrompt(room, branch), chatHistory) { delta ->
                streamed += delta
                branch.messages[assistantIndex] = ChatMessage("assistant", filterNsfw(streamed, room.config.efficientMode))
            }
            branch.messages[assistantIndex] = ChatMessage("assistant", filterNsfw(result, room.config.efficientMode))
            branchLoading = false
            persist("分支回复已保存")
            if (room.config.ttsAutoRead && result.isNotBlank()) speakText(context, room.config, result)
        }
    }

    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = palette.button,
            secondary = palette.button,
            tertiary = palette.button,
            background = palette.page,
            surface = palette.surface,
            surfaceVariant = palette.card,
            primaryContainer = palette.button.copy(alpha = 0.14f).compositeOn(palette.surface),
            secondaryContainer = palette.button.copy(alpha = 0.10f).compositeOn(palette.surface),
            outline = palette.outline,
            onPrimary = palette.onButton,
            onSecondary = palette.onButton,
            onBackground = palette.ink,
            onSurface = palette.ink,
            onSurfaceVariant = palette.muted
        )
    ) {
    Scaffold(
        topBar = {
            Surface(Modifier.fillMaxWidth().statusBarsPadding().height(2.dp), color = palette.page) {}
        },
        bottomBar = {
            if (chromeVisible || tab != Tab.Class) {
            NavigationBar(containerColor = palette.surface, tonalElevation = 0.dp) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item; classMenuOpen = false },
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = palette.button,
                            selectedTextColor = palette.button,
                            indicatorColor = palette.button.copy(alpha = 0.13f).compositeOn(palette.surface),
                            unselectedIconColor = palette.muted,
                            unselectedTextColor = palette.muted
                        )
                    )
                }
            }
            }
        }
    ) { padding ->
        Surface(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 10.dp)
                .pointerInput(classes.size, classIndex, classMenuOpen, tab) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (tab == Tab.Class && !classMenuOpen && total > 80f) classMenuOpen = true
                            if (tab == Tab.Class && classMenuOpen && total < -80f) classMenuOpen = false
                            total = 0f
                        },
                        onHorizontalDrag = { _, dragAmount -> total += dragAmount }
                    )
                },
            color = palette.page
        ) {
            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    Tab.Class -> ClassScreen(current, input, { input = it }, isLoading, palette, jumpToMessageIndex, { jumpToMessageIndex = null }, current.config.reverseConversation, onOpenMenu = { classMenuOpen = true }, onSend = { sendMessage() }, onImage = { imageLauncher.launch("image/*") }, onDeleteAfter = { index ->
                        if (index in current.messages.indices) {
                            for (i in current.messages.lastIndex downTo index) current.messages.removeAt(i)
                            current.chapters.clear()
                            persist("已删除后续对话")
                        }
                    }, onSpeak = { text ->
                        speakText(context, current.config, text)
                    }) { index ->
                        val selected = current.messages.take(index + 1)
                        val branchMessages = mutableStateListOf<ChatMessage>()
                        val title = selected.firstOrNull()?.text?.take(18)?.ifBlank { "分支课堂" } ?: "分支课堂"
                        current.branches.add(BranchClass(title, "${current.name} 第 ${index + 1} 条起", branchMessages, summarize("分支上下文", selected), selected.takeLast(BRANCH_CONTEXT_LIMIT).toMutableStateList()))
                        activeBranchIndex = current.branches.lastIndex
                        branchInput = ""
                        persist("分支已保存")
                        tab = Tab.Branch
                    }
                    Tab.Branch -> BranchScreen(current.branches, activeBranchIndex, branchInput, { branchInput = it }, branchLoading, palette, current.config.mentorName, onSelect = { activeBranchIndex = it }, onBack = { activeBranchIndex = -1 }, onSend = { sendBranchMessage(activeBranchIndex) })
                    Tab.Memory -> MemoryScreen(current.chapters, current.messages) { index ->
                        jumpToMessageIndex = index
                        tab = Tab.Class
                    }
                    Tab.Knowledge -> KnowledgeScreen(current.files) { persist("知识库已保存") }
                    Tab.Model -> ModelScreen(
                        config = current.config,
                        models = models,
                        modelStatus = modelStatus,
                        saveNotice = saveNotice,
                        onConfig = { replaceCurrent(current.copy(config = it), "设置已保存") },
                        onOpenManual = { showManualDialog = true },
                        onFetchModels = {
                            scope.launch {
                                modelStatus = "获取中..."
                                val fetched = fetchModels(current.config.baseUrl, current.config.apiKey)
                                if (fetched.isNotEmpty()) {
                                    models.clear()
                                    models.addAll(fetched)
                                    replaceCurrent(current.copy(config = current.config.copy(selectedModel = fetched.first(), customModel = "")), "模型列表已保存")
                                    modelStatus = "已获取 ${fetched.size} 个模型"
                                } else {
                                    modelStatus = "获取失败，可手动填写模型名"
                                }
                            }
                        }
                    )
                }
                if (tab == Tab.Class) {
                    FloatingIconAction(
                        icon = if (chromeVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        visible = chromeVisible,
                        palette = palette,
                        onClick = { chromeVisible = !chromeVisible },
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 8.dp)
                    )
                }
                AnimatedVisibility(
                    visible = classMenuOpen,
                    enter = slideInHorizontally(animationSpec = tween(220)) { -it },
                    exit = slideOutHorizontally(animationSpec = tween(180)) { -it }
                ) {
                    ClassroomMenu(classes, classIndex, saveNotice, palette, onSelect = {
                        classIndex = it
                        classMenuOpen = false
                        persist("已切换课堂")
                    }, onRename = { index, name ->
                        if (index in classes.indices) {
                            replaceClassroomAt(index, classes[index].copy(name = name), "课堂名已保存")
                        }
                    }, onNew = { addClassroom() }, onNewWithConfig = { addClassroom(classes[it]) }, onCopyConfig = ::copyConfigFrom, onDelete = ::deleteClassroom)
                }
                examSession?.let { session ->
                    ExamOverlay(session, current.messages, current.branches, onUpdate = { examSession = it }, onClose = { examSession = null; persist("已退出考试模式") }, onSubmit = { packed ->
                        examSession = null
                        sendMessage(packed, allowExamTrigger = false)
                    }, onUnknown = { questionIndex, question ->
                        val branchText = "考试不会题：\n前提：${question.premise}\n问题：${question.question}\n请讲解这道题的思路。"
                        current.branches.add(BranchClass("考试不会题 ${questionIndex + 1}", "考试工具", mutableStateListOf(ChatMessage("user", branchText), ChatMessage("assistant", "正在生成讲解，可返回主课堂继续同步查看。")), summarize("考试不会题", listOf(ChatMessage("user", branchText)))))
                        persist("不会题分支已保存")
                        scope.launch {
                            val answer = callChatWithFallback(current.config, activeModelChain, systemPrompt(current), listOf(ChatMessage("user", branchText)))
                            current.branches.lastOrNull()?.messages?.add(ChatMessage("assistant", answer))
                            persist("不会题讲解已保存")
                        }
                    })
                }
                if (showNewDialog) {
                    ReleaseNotesDialog(palette, onClose = {
                        store.markReleaseNotesSeen(APP_VERSION)
                        showNewDialog = false
                    })
                }
                updateInfo?.let { info ->
                    UpdateDialog(info, onClose = { updateInfo = null })
                }
                if (showManualDialog) {
                    UserManualDialog(palette, onClose = { showManualDialog = false })
                }
            }
        }
    }
    }
}

@Composable
private fun ClassroomMenu(
    classes: List<Classroom>,
    classIndex: Int,
    saveNotice: String,
    palette: AppPalette,
    onSelect: (Int) -> Unit,
    onRename: (Int, String) -> Unit,
    onNew: () -> Unit,
    onNewWithConfig: (Int) -> Unit,
    onCopyConfig: (Int) -> Unit,
    onDelete: (Int) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<Pair<Int, String>?>(null) }
    val filtered = classes.indices.filter { i ->
        val room = classes[i]
        query.isBlank() || room.name.contains(query, ignoreCase = true) || room.topic.contains(query, ignoreCase = true)
    }
    Row(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxHeight().fillMaxWidth(0.84f), color = MaterialTheme.colorScheme.surface, shape = AppShapes.menu, shadowElevation = 8.dp) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("课堂", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(saveNotice, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = onNew, colors = IconButtonDefaults.iconButtonColors(contentColor = palette.button)) {
                            Icon(Icons.Default.Add, null)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        placeholder = { Text("搜索课堂") },
                        singleLine = true,
                        shape = AppShapes.control,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.button,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            cursorColor = palette.button
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onNew, modifier = Modifier.fillMaxWidth(), shape = AppShapes.button) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("新建课堂")
                    }
                }
                if (filtered.isEmpty()) {
                    item { Text("没有找到课堂", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp)) }
                }
                items(filtered) { i ->
                    val room = classes[i]
                    Surface(
                        onClick = { onSelect(i) },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (i == classIndex) palette.button.copy(alpha = 0.12f).compositeOn(MaterialTheme.colorScheme.surface) else Color.Transparent,
                        shape = AppShapes.card
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(Modifier.size(38.dp), shape = RoundedCornerShape(14.dp), color = if (i == classIndex) palette.button else MaterialTheme.colorScheme.surfaceVariant) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text((i + 1).toString(), color = if (i == classIndex) palette.onButton else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(room.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(room.topic, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (i == classIndex) Icon(Icons.Default.Check, null, tint = palette.button, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { renameTarget = i to room.name }) { Icon(Icons.Default.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(3.dp)); Text("改名") }
                                TextButton(onClick = { onNewWithConfig(i) }) { Text("复制") }
                                if (i != classIndex) TextButton(onClick = { onCopyConfig(i) }) { Text("复制配置") }
                                TextButton(onClick = { onDelete(i) }) { Icon(Icons.Default.Delete, null, Modifier.size(16.dp)); Text("删除") }
                            }
                        }
                    }
                }
            }
        }
    }
    renameTarget?.let { target ->
        RenameClassroomDialog(
            initial = target.second,
            onClose = { renameTarget = null },
            onSave = { name ->
                onRename(target.first, name)
                renameTarget = null
            }
        )
    }
}

@Composable
private fun RenameClassroomDialog(initial: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("修改课堂名字", fontWeight = FontWeight.Bold) },
        text = { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("课堂名字") }, singleLine = true) },
        confirmButton = { Button(onClick = { onSave(name.trim().ifBlank { initial }) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onClose) { Text("取消") } }
    )
}

@Composable
private fun ClassScreen(
    room: Classroom,
    input: String,
    onInput: (String) -> Unit,
    isLoading: Boolean,
    palette: AppPalette,
    jumpToMessageIndex: Int?,
    onJumpHandled: () -> Unit,
    reverseConversation: Boolean,
    onOpenMenu: () -> Unit,
    onSend: () -> Unit,
    onImage: () -> Unit,
    onDeleteAfter: (Int) -> Unit,
    onSpeak: (String) -> Unit,
    onBranch: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val lastItemIndex = room.messages.size + 1
    val bottomIndex = if (reverseConversation) 0 else lastItemIndex
    val topIndex = if (reverseConversation) lastItemIndex else 0
    val isAtBottom by remember {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            visible.any { it.index == bottomIndex } || listState.firstVisibleItemIndex == bottomIndex
        }
    }
    val compactInput by remember { derivedStateOf { !isAtBottom } }
    LaunchedEffect(room.messages.size) {
        if (room.messages.isNotEmpty()) listState.animateScrollToItem(bottomIndex)
    }
    LaunchedEffect(jumpToMessageIndex) {
        val target = jumpToMessageIndex ?: return@LaunchedEffect
        listState.animateScrollToItem((target + 1).coerceIn(0, lastItemIndex))
        onJumpHandled()
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, reverseLayout = reverseConversation, contentPadding = PaddingValues(top = 10.dp, bottom = if (compactInput) 76.dp else 142.dp, start = 2.dp, end = 2.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(room.messages.size) { i -> MessageCard(i, room.messages[i], palette, room.config.mentorName, onBranch, onDeleteAfter, onSpeak) }
            if (isLoading) item { AiThinkingRow(palette, room.config.mentorName) }
        }
        ChatInputBar(
            input = input,
            onInput = onInput,
            isLoading = isLoading,
            compact = compactInput,
            palette = palette,
            onSend = onSend,
            onImage = onImage,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        FloatingIconAction(
            icon = if (isAtBottom) Icons.Default.North else Icons.Default.South,
            visible = isAtBottom,
            palette = palette,
            onClick = { scope.launch { if (isAtBottom) listState.animateScrollToItem(topIndex) else listState.animateScrollToItem(bottomIndex) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = if (compactInput) 122.dp else 188.dp),
        )
    }
}

@Composable
private fun FloatingIconAction(icon: ImageVector, visible: Boolean, palette: AppPalette, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(46.dp),
        color = palette.surface,
        shape = RoundedCornerShape(999.dp),
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = palette.button, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun ChatInputBar(
    input: String,
    onInput: (String) -> Unit,
    isLoading: Boolean,
    compact: Boolean,
    palette: AppPalette,
    onSend: () -> Unit,
    onImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 6.dp),
        color = palette.surface,
        shape = AppShapes.panel,
        shadowElevation = 3.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = onImage,
                enabled = !isLoading,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = palette.button,
                    disabledContentColor = palette.button.copy(alpha = 0.35f)
                )
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(24.dp))
            }
            Box(Modifier.weight(1f)) {
                OutlinedTextField(
                    input,
                    onInput,
                    Modifier.fillMaxWidth().padding(end = if (compact) 48.dp else 58.dp),
                    placeholder = { Text("输入学习目标或问题") },
                    minLines = if (compact) 1 else 3,
                    maxLines = if (compact) 1 else 5,
                    singleLine = compact,
                    shape = AppShapes.control,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.button,
                        unfocusedBorderColor = palette.outline,
                        cursorColor = palette.button,
                        focusedContainerColor = palette.card,
                        unfocusedContainerColor = palette.card
                    )
                )
                Button(
                    onClick = onSend,
                    enabled = !isLoading,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 5.dp, bottom = 5.dp).size(if (compact) 40.dp else 44.dp),
                    shape = AppShapes.button,
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.button, contentColor = palette.onButton)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageCard(
    index: Int,
    message: ChatMessage,
    palette: AppPalette,
    mentorName: String,
    onBranch: (Int) -> Unit,
    onDeleteAfter: (Int) -> Unit,
    onSpeak: (String) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val actionModifier = Modifier.combinedClickable(
        onClick = {},
        onDoubleClick = { if (message.role == "assistant") onSpeak(message.text) },
        onLongClick = { menuOpen = true }
    )
    Box(Modifier.fillMaxWidth()) {
    if (message.role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(
                Modifier
                    .fillMaxWidth(0.82f)
                    .then(actionModifier)
                    .background(palette.card, AppShapes.card)
                    .border(1.dp, palette.outline, AppShapes.card)
                    .padding(12.dp)
            ) {
                Text("我", color = palette.secondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(message.text, color = palette.ink, lineHeight = 21.sp)
                TextButton(onClick = { onBranch(index) }) { Text("从这里开分支", color = palette.secondary) }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().then(actionModifier).padding(horizontal = 4.dp)) {
            Text(mentorName.ifBlank { "AI 讲师" }, color = palette.secondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            MarkdownText(message.text)
            TextButton(onClick = { onBranch(index) }) { Text("从这里开分支") }
        }
    }
        MessageActionMenu(menuOpen, palette, onDismiss = { menuOpen = false }, onBranch = { onBranch(index) }, onDeleteAfter = { onDeleteAfter(index) })
    }
}

@Composable
private fun MessageActionMenu(expanded: Boolean, palette: AppPalette, onDismiss: () -> Unit, onBranch: () -> Unit, onDeleteAfter: () -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(palette.surface)
    ) {
        DropdownMenuItem(
            text = { Text("从这里开分支", color = palette.ink) },
            leadingIcon = { Icon(Icons.Default.AccountTree, null, tint = palette.button) },
            onClick = { onDismiss(); onBranch() }
        )
        DropdownMenuItem(
            text = { Text("删除此处及之后", color = palette.ink) },
            leadingIcon = { Icon(Icons.Default.Delete, null, tint = palette.button) },
            onClick = { onDismiss(); onDeleteAfter() }
        )
    }
}

@Composable
private fun AiThinkingRow(palette: AppPalette, mentorName: String = "AI 讲师") {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(mentorName.ifBlank { "AI 讲师" }, color = palette.secondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Text("正在回复...", color = Muted)
    }
}

@Composable
private fun ReleaseNotesDialog(palette: AppPalette, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = palette.surface,
        titleContentColor = palette.ink,
        textContentColor = palette.ink,
        shape = AppShapes.panel,
        title = { Text("New!", color = palette.button, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { MarkdownText(RELEASE_NOTES_TEXT, color = palette.ink) }
            }
        },
        confirmButton = {
            Button(
                onClick = onClose,
                shape = AppShapes.button,
                colors = ButtonDefaults.buttonColors(containerColor = palette.button, contentColor = palette.onButton)
            ) { Text("知道了") }
        }
    )
}

@Composable
private fun UpdateDialog(info: UpdateInfo, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("发现新版本", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(info.name.ifBlank { info.version }, fontWeight = FontWeight.Bold)
                Text("GitHub Release 已发布更新，可前往仓库下载新版 APK。", color = Muted)
                if (info.notes.isNotBlank()) MarkdownText(info.notes.take(320))
                Text(info.url, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        },
        confirmButton = { Button(onClick = onClose) { Text("知道了") } }
    )
}

@Composable
private fun UserManualDialog(palette: AppPalette, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = palette.surface,
        titleContentColor = palette.ink,
        textContentColor = palette.ink,
        shape = AppShapes.panel,
        title = { Text("使用手册", color = palette.button, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.height(460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { MarkdownText(USER_MANUAL_TEXT, color = palette.ink) }
            }
        },
        confirmButton = {
            Button(
                onClick = onClose,
                shape = AppShapes.button,
                colors = ButtonDefaults.buttonColors(containerColor = palette.button, contentColor = palette.onButton)
            ) { Text("关闭") }
        }
    )
}

@Composable
private fun ExamOverlay(
    session: ExamSession,
    messages: List<ChatMessage>,
    branches: List<BranchClass>,
    onUpdate: (ExamSession) -> Unit,
    onClose: () -> Unit,
    onSubmit: (String) -> Unit,
    onUnknown: (Int, ExamQuestion) -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var sidePanel by remember { mutableStateOf<String?>(null) }
    var draftMode by remember { mutableStateOf("text") }
    val strokes = remember { mutableStateListOf<DrawStroke>() }
    LaunchedEffect(isLandscape) {
        if (!isLandscape) sidePanel = null
    }
    Surface(Modifier.fillMaxSize(), color = Page) {
        if (isLandscape) {
            Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExamQuestionList(session, Modifier.weight(if (sidePanel == null) 1f else 0.58f).fillMaxHeight(), true, sidePanel, onSidePanel = { sidePanel = if (sidePanel == it) null else it }, onClose, onSubmit, onUpdate, onUnknown)
                sidePanel?.let { panel ->
                    ExamSidePanel(panel, session, messages, branches, draftMode, onDraftMode = { draftMode = it }, strokes, onUpdate = onUpdate, modifier = Modifier.weight(0.42f).fillMaxHeight())
                }
            }
        } else {
            ExamQuestionList(session, Modifier.fillMaxSize().padding(12.dp), false, null, onSidePanel = {}, onClose, onSubmit, onUpdate, onUnknown)
        }
    }
}

@Composable
private fun ExamQuestionList(
    session: ExamSession,
    modifier: Modifier,
    isLandscape: Boolean,
    sidePanel: String?,
    onSidePanel: (String) -> Unit,
    onClose: () -> Unit,
    onSubmit: (String) -> Unit,
    onUpdate: (ExamSession) -> Unit,
    onUnknown: (Int, ExamQuestion) -> Unit
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Quiz, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(session.title, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = onClose) { Text("退出") }
                }
                if (isLandscape) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onSidePanel("draft") }) { Text(if (sidePanel == "draft") "关闭草稿纸" else "打开草稿纸") }
                        OutlinedButton(onClick = { onSidePanel("overview") }) { Text(if (sidePanel == "overview") "关闭总览" else "打开总览") }
                    }
                }
            }
        }
        items(session.questions.indices.toList()) { i ->
            val q = session.questions[i]
            InfoCard {
                Text("前提", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(q.premise.ifBlank { "无额外前提" }, lineHeight = 21.sp)
                Spacer(Modifier.height(8.dp))
                Text("问题 ${i + 1}", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(q.question, lineHeight = 21.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(q.unknown, {
                        val updated = q.copy(unknown = it, answer = if (it) "" else q.answer)
                        session.questions[i] = updated
                        onUpdate(session.copy(questions = session.questions))
                        if (it) onUnknown(i, updated)
                    })
                    Text("不会", color = if (q.unknown) Blue else Muted)
                }
                OutlinedTextField(q.answer, {
                    session.questions[i] = q.copy(answer = it)
                    onUpdate(session.copy(questions = session.questions))
                }, Modifier.fillMaxWidth(), enabled = !q.unknown, minLines = 3, placeholder = { Text(if (q.unknown) "已标记不会" else "在这里作答") })
            }
        }
        item { Button(onClick = { onSubmit(packExamAnswers(session)) }, modifier = Modifier.fillMaxWidth()) { Text("提交答案") } }
    }
}

@Composable
private fun ExamSidePanel(
    panel: String,
    session: ExamSession,
    messages: List<ChatMessage>,
    branches: List<BranchClass>,
    draftMode: String,
    onDraftMode: (String) -> Unit,
    strokes: MutableList<DrawStroke>,
    onUpdate: (ExamSession) -> Unit,
    modifier: Modifier
) {
    Box(modifier) {
        InfoCard {
            if (panel == "draft") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("草稿纸", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    AppFilterChip(draftMode == "text", { onDraftMode("text") }) { Text("打字") }
                    Spacer(Modifier.width(6.dp))
                    AppFilterChip(draftMode == "draw", { onDraftMode("draw") }) { Text("涂绘") }
                }
                Spacer(Modifier.height(8.dp))
                if (draftMode == "text") {
                    OutlinedTextField(session.draft, { onUpdate(session.copy(draft = it)) }, Modifier.fillMaxWidth(), minLines = 14, placeholder = { Text("草稿纸") })
                } else {
                    DrawPad(strokes, Modifier.height(430.dp).fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { strokes.clear() }, modifier = Modifier.fillMaxWidth()) { Text("清空涂绘") }
                }
            } else {
                Text("过去总览", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(470.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("过往对话", color = Muted, fontWeight = FontWeight.Bold) }
                    items(messages.takeLast(12)) { MarkdownText((if (it.role == "user") "我：" else "AI：") + it.text.take(360)) }
                    item { Text("不会题分支", color = Muted, fontWeight = FontWeight.Bold) }
                    items(branches.takeLast(6)) { Text(it.title, fontWeight = FontWeight.Bold); MarkdownText(it.messages.lastOrNull()?.text.orEmpty().take(360)) }
                }
            }
        }
    }
}

@Composable
private fun DrawPad(strokes: MutableList<DrawStroke>, modifier: Modifier) {
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    Canvas(
        modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE1E6EF), RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { current = listOf(it) },
                    onDrag = { change, _ -> current = current + change.position },
                    onDragEnd = {
                        if (current.size > 1) strokes.add(DrawStroke(current))
                        current = emptyList()
                    }
                )
            }
    ) {
        (strokes.map { it.points } + listOf(current)).forEach { points ->
            points.zipWithNext().forEach { (from, to) -> drawLine(Color(0xFF111827), from, to, strokeWidth = 4f, cap = StrokeCap.Round) }
        }
    }
}

@Composable
private fun BranchScreen(
    branches: List<BranchClass>,
    activeIndex: Int,
    input: String,
    onInput: (String) -> Unit,
    isLoading: Boolean,
    palette: AppPalette,
    mentorName: String,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit,
    onSend: () -> Unit
) {
    if (activeIndex in branches.indices) {
        BranchChatScreen(branches[activeIndex], input, onInput, isLoading, palette, mentorName, onBack, onSend)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (branches.isEmpty()) item { InfoCard { Text("在主课堂任意消息下开分支。分支会作为独立长对话保存，不会改写主课堂。", color = Muted) } }
        items(branches.indices.toList()) { index ->
            val branch = branches[index]
            InfoCard {
                Text(branch.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(branch.source, color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                MarkdownText(branch.memory)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSelect(index) }) { Text("进入分支") }
                    OutlinedButton(onClick = { onSelect(index) }) { Text("继续对话 ${branch.messages.size}") }
                }
            }
        }
    }
}

@Composable
private fun BranchChatScreen(
    branch: BranchClass,
    input: String,
    onInput: (String) -> Unit,
    isLoading: Boolean,
    palette: AppPalette,
    mentorName: String,
    onBack: () -> Unit,
    onSend: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(branch.messages.size) {
        if (branch.messages.isNotEmpty()) listState.animateScrollToItem(branch.messages.size + 1)
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = 10.dp, bottom = 138.dp, start = 2.dp, end = 2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(branch.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        OutlinedButton(onClick = onBack) { Text("分支列表") }
                    }
                    Text(branch.source, color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("这是与主课堂平行的支线长对话，主课堂内容不会被改写。", color = Muted, fontSize = 13.sp)
                }
            }
            if (branch.messages.isEmpty()) {
                item { InfoCard { Text("输入问题后，AI 会基于创建分支时的主课堂上下文继续讲解。", color = Muted) } }
            }
            items(branch.messages.size) { i ->
                SimpleMessageCard(branch.messages[i], palette, mentorName)
            }
            if (isLoading) item { AiThinkingRow(palette, mentorName) }
        }
        ChatInputBar(input, onInput, isLoading, compact = false, palette = palette, onSend = onSend, onImage = {}, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SimpleMessageCard(message: ChatMessage, palette: AppPalette, mentorName: String) {
    if (message.role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(
                Modifier
                    .fillMaxWidth(0.82f)
                    .background(palette.card, AppShapes.card)
                    .border(1.dp, palette.outline, AppShapes.card)
                    .padding(12.dp)
            ) {
                Text("我", color = palette.secondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(message.text, color = palette.ink, lineHeight = 21.sp)
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text("${mentorName.ifBlank { "AI 讲师" }} · 分支", color = palette.secondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            MarkdownText(message.text)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoryScreen(chapters: List<ConversationChapter>, messages: List<ChatMessage>, onJump: (Int) -> Unit) {
    val visibleChapters = chapters.ifEmpty { fallbackChapters(messages) }
    var overview by remember { mutableStateOf(true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("对话章节", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    AppFilterChip(overview, { overview = true }) { Text("全览") }
                    Spacer(Modifier.width(6.dp))
                    AppFilterChip(!overview, { overview = false }) { Text("章节") }
                }
                Text("双击章节可跳到主课堂对应对话。", color = Muted, fontSize = 13.sp)
            }
        }
        if (visibleChapters.isEmpty()) item { InfoCard { Text("开始对话后会自动生成章节索引。", color = Muted) } }
        if (overview) {
            item { MemoryMindMap(visibleChapters, onJump) }
        } else {
            items(visibleChapters) { chapter ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = {}, onDoubleClick = { onJump(chapter.startIndex) }),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(chapter.title, fontWeight = FontWeight.Bold)
                        Text("第 ${chapter.startIndex + 1} 到 ${chapter.endIndex + 1} 条", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(chapter.summary, color = Ink, lineHeight = 21.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoryMindMap(chapters: List<ConversationChapter>, onJump: (Int) -> Unit) {
    val nodes = chapters.take(18)
    val links = remember(nodes) { buildChapterLinks(nodes) }
    val height = 420.dp
    var scale by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val button = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    InfoCard {
        Text("实时向量思维导图", fontWeight = FontWeight.Bold)
        Text("双指可任意放大缩小，拖动查看；双击节点跳到主课堂对应对话。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .background(MaterialTheme.colorScheme.surfaceVariant, AppShapes.card)
                .clipToBounds()
                .pointerInput(nodes.size) {
                    detectTransformGestures { _, panChange, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.45f, 4f)
                        pan += panChange
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension * 0.38f * scale
                val shiftedCenter = center + pan
                val positions = nodes.indices.associateWith { index ->
                    if (nodes.size == 1) shiftedCenter else Offset(
                        shiftedCenter.x + cos((index * 2.0 * Math.PI / nodes.size) - Math.PI / 2).toFloat() * radius,
                        shiftedCenter.y + sin((index * 2.0 * Math.PI / nodes.size) - Math.PI / 2).toFloat() * radius
                    )
                }
                links.forEach { (from, to, weight) ->
                    val a = positions[from] ?: return@forEach
                    val b = positions[to] ?: return@forEach
                    drawLine(button.copy(alpha = (0.18f + weight * 0.34f).coerceIn(0.18f, 0.52f)), a, b, strokeWidth = (2.5f + weight * 3f) * scale.coerceIn(0.7f, 1.6f), cap = StrokeCap.Round)
                }
                positions.values.forEach { point ->
                    drawCircle(button.copy(alpha = 0.18f), radius = 34f * scale.coerceIn(0.7f, 1.6f), center = point)
                    drawCircle(button, radius = 12f * scale.coerceIn(0.7f, 1.6f), center = point)
                }
            }
            nodes.forEachIndexed { index, chapter ->
                val angle = if (nodes.size == 1) -Math.PI / 2 else (index * 2.0 * Math.PI / nodes.size) - Math.PI / 2
                val x = (160 + pan.x / 2.8f + cos(angle).toFloat() * 126f * scale).roundToInt()
                val y = (178 + pan.y / 2.8f + sin(angle).toFloat() * 162f * scale).roundToInt()
                Surface(
                    modifier = Modifier
                        .width((116 * scale.coerceIn(0.78f, 1.35f)).dp)
                        .offset(x.dp, y.dp)
                        .combinedClickable(onClick = {}, onDoubleClick = { onJump(chapter.startIndex) }),
                    shape = AppShapes.control,
                    color = surface,
                    shadowElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, button.copy(alpha = 0.22f))
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text(chapter.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(chapter.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (nodes.size < chapters.size) Text("已显示最近 ${nodes.size} 个章节。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

private data class ChapterLink(val from: Int, val to: Int, val weight: Float)

private fun buildChapterLinks(chapters: List<ConversationChapter>): List<ChapterLink> {
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

private fun chapterVector(chapter: ConversationChapter): Map<String, Float> =
    Regex("[A-Za-z0-9_]+|[\\u4e00-\\u9fa5]{2,}").findAll(chapter.title + " " + chapter.summary)
        .map { it.value.lowercase() }
        .filter { it.length > 1 && it !in COMMON_MEMORY_WORDS }
        .groupingBy { it }
        .eachCount()
        .mapValues { it.value.toFloat() }

private fun cosineSimilarity(a: Map<String, Float>, b: Map<String, Float>): Float {
    if (a.isEmpty() || b.isEmpty()) return 0f
    val dot = a.entries.sumOf { (key, value) -> (value * (b[key] ?: 0f)).toDouble() }
    val normA = kotlin.math.sqrt(a.values.sumOf { (it * it).toDouble() })
    val normB = kotlin.math.sqrt(b.values.sumOf { (it * it).toDouble() })
    return if (normA == 0.0 || normB == 0.0) 0f else (dot / (normA * normB)).toFloat()
}

private val COMMON_MEMORY_WORDS = setOf("用户", "课堂", "内容", "学习", "总结", "问题", "讲解", "the", "and", "for", "with", "this", "that")

@Composable
private fun KnowledgeScreen(files: MutableList<KnowledgeFile>, onSave: () -> Unit) {
    val context = LocalContext.current
    var viewing by remember { mutableStateOf<KnowledgeFile?>(null) }
    var deleteTarget by remember { mutableStateOf<KnowledgeFile?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "knowledge"
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext == "md" || ext == "txt") {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            files.add(KnowledgeFile(name, ext, text.length, text.take(1000), text))
            onSave()
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            InfoCard {
                Text("知识库", fontWeight = FontWeight.Bold)
                Text("可直接读取：.md、.txt。上传后会进入课堂提示词，AI 讲师可结合文件内容回答。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { launcher.launch("text/*") }, shape = AppShapes.button) { Text("上传文件") }
            }
        }
        if (files.isEmpty()) item { InfoCard { Text("还没有上传知识库文件。", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        items(files, key = { it.name + it.chars }) { file ->
            KnowledgeFileRow(file, onOpen = { viewing = file }, onDelete = { deleteTarget = file })
        }
    }
    viewing?.let { file -> KnowledgeFileDialog(file, onClose = { viewing = null }) }
    deleteTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除文件", fontWeight = FontWeight.Bold) },
            text = { Text("确定从知识库删除 ${file.name} 吗？") },
            confirmButton = { Button(onClick = { files.remove(file); deleteTarget = null; onSave() }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KnowledgeFileRow(file: KnowledgeFile, onOpen: () -> Unit, onDelete: () -> Unit) {
    InfoCard {
        Column(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = onDelete)
        ) {
            Text(file.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${file.type} · ${file.chars} 字", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            MarkdownText(file.preview)
        }
    }
}

@Composable
private fun KnowledgeFileDialog(file: KnowledgeFile, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(file.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            LazyColumn(Modifier.height(460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { MarkdownText(file.content) }
            }
        },
        confirmButton = { Button(onClick = onClose) { Text("关闭") } }
    )
}

@Composable
private fun ModelScreen(
    config: ClassroomConfig,
    models: List<String>,
    modelStatus: String,
    saveNotice: String,
    onConfig: (ClassroomConfig) -> Unit,
    onOpenManual: () -> Unit,
    onFetchModels: () -> Unit
) {
    val providers = listOf("OpenAI", "DeepSeek", "通义千问", "自定义")
    val ttsProviders = listOf("OpenAI", "通义千问", "自定义")
    var apiProvider by remember(config.provider) { mutableStateOf(config.provider) }
    var apiBaseUrl by remember(config.baseUrl) { mutableStateOf(config.baseUrl) }
    var apiKey by remember(config.apiKey) { mutableStateOf(config.apiKey) }
    var modelName by remember(config.customModel, config.selectedModel) { mutableStateOf(config.customModel.ifBlank { config.selectedModel }) }
    var modelChain by remember(config.modelChain) { mutableStateOf(config.modelChain.ifBlank { config.customModel.ifBlank { config.selectedModel } }) }
    var deepThinkingEnabled by remember(config.deepThinkingEnabled) { mutableStateOf(config.deepThinkingEnabled) }
    var deepThinkingModel by remember(config.deepThinkingModel) { mutableStateOf(config.deepThinkingModel) }
    var visionProvider by remember(config.visionProvider) { mutableStateOf(config.visionProvider) }
    var visionApiBaseUrl by remember(config.visionBaseUrl) { mutableStateOf(config.visionBaseUrl) }
    var visionApiKey by remember(config.visionApiKey) { mutableStateOf(config.visionApiKey) }
    var visionModel by remember(config.visionModel) { mutableStateOf(config.visionModel) }
    var ttsProvider by remember(config.ttsProvider) { mutableStateOf(config.ttsProvider) }
    var ttsApiKey by remember(config.ttsApiKey) { mutableStateOf(config.ttsApiKey) }
    var ttsBaseUrl by remember(config.ttsBaseUrl) { mutableStateOf(config.ttsBaseUrl) }
    var ttsModel by remember(config.ttsModel) { mutableStateOf(config.ttsModel) }
    var ttsVoice by remember(config.ttsVoice) { mutableStateOf(config.ttsVoice) }
    var ttsAutoRead by remember(config.ttsAutoRead) { mutableStateOf(config.ttsAutoRead) }
    var ttsStatus by remember { mutableStateOf("未获取语音模型") }
    var mentorName by remember(config.mentorName) { mutableStateOf(config.mentorName) }
    var userAlias by remember(config.userAlias) { mutableStateOf(config.userAlias) }
    var mentorPrompt by remember(config.mentorPrompt) { mutableStateOf(config.mentorPrompt) }
    var efficientMode by remember(config.efficientMode) { mutableStateOf(config.efficientMode) }
    var reverseConversation by remember(config.reverseConversation) { mutableStateOf(config.reverseConversation) }
    var themeMode by remember(config.themeMode) { mutableStateOf(normalizeThemeMode(config.themeMode)) }
    var interfaceMode by remember(config.interfaceMode) { mutableStateOf(normalizeInterfaceMode(config.interfaceMode)) }
    var primaryColor by remember(config.primaryColor) { mutableStateOf(config.primaryColor) }
    var secondaryColor by remember(config.secondaryColor) { mutableStateOf(config.secondaryColor) }
    var primaryHex by remember(config.primaryColor) { mutableStateOf(argbToHex(config.primaryColor)) }
    val colorSwatches = listOf(0xFF39C5BB, 0xFF00AEEF, 0xFF3B82F6, 0xFF6366F1, 0xFF8B5CF6, 0xFFEC4899, 0xFFEF4444, 0xFFF97316, 0xFF22C55E, 0xFF111827)
    val primaryValid = parseHexColor(primaryHex) != null
    val scope = rememberCoroutineScope()
    var expandedModule by remember { mutableStateOf<String?>("model") }
    var visionStatus by remember { mutableStateOf("未获取识图模型") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SettingsCard("模型模块", expandedModule == "model", { expandedModule = if (expandedModule == "model") null else "model" }) {
                Text("普通课堂对话使用此模块的 API。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    providers.forEach { provider ->
                        AppFilterChip(apiProvider == provider, {
                            apiProvider = provider
                            apiBaseUrl = defaultBaseUrl(provider)
                        }) { Text(provider) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(apiBaseUrl, { apiBaseUrl = it }, Modifier.fillMaxWidth(), label = { Text("Base URL") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), label = { Text("API Key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    models.forEach { model ->
                        AppFilterChip(modelName == model, { modelName = model; if (!modelChain.lines().map { it.trim() }.contains(model)) modelChain = listOf(model, modelChain).filter { it.isNotBlank() }.joinToString("\n") }) { Text(model) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(modelName, { modelName = it }, Modifier.fillMaxWidth(), label = { Text("模型名称") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(modelChain, { modelChain = it }, Modifier.fillMaxWidth(), label = { Text("模型优先级（一行一个，前面的失败后自动尝试后面的）") }, minLines = 3)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("深度思考模式", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Switch(deepThinkingEnabled, { deepThinkingEnabled = it })
                }
                OutlinedTextField(deepThinkingModel, { deepThinkingModel = it }, Modifier.fillMaxWidth(), label = { Text("深度思考模型") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    val cleanName = modelName.trim()
                    val cleanChain = modelChain.lines().flatMap { it.split(',', '，') }.map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("\n")
                    onConfig(config.copy(provider = apiProvider, baseUrl = apiBaseUrl.trim(), apiKey = apiKey.trim(), selectedModel = cleanName.ifBlank { config.selectedModel }, customModel = if (cleanName in models) "" else cleanName, modelChain = cleanChain.ifBlank { cleanName.ifBlank { config.selectedModel } }, deepThinkingEnabled = deepThinkingEnabled, deepThinkingModel = deepThinkingModel.trim()))
                    expandedModule = null
                }) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("保存模型模块")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onFetchModels) { Text("获取模型", color = MaterialTheme.colorScheme.primary) }
                Text(modelStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsCard("多模态模块", expandedModule == "vision", { expandedModule = if (expandedModule == "vision") null else "vision" }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("课堂图片分析模型", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    providers.forEach { provider ->
                        AppFilterChip(visionProvider == provider, {
                            visionProvider = provider
                            visionApiBaseUrl = defaultBaseUrl(provider)
                        }) { Text(provider) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(visionApiBaseUrl, { visionApiBaseUrl = it }, Modifier.fillMaxWidth(), label = { Text("多模态 Base URL") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(visionApiKey, { visionApiKey = it }, Modifier.fillMaxWidth(), label = { Text("多模态 API Key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(visionModel, { visionModel = it }, Modifier.fillMaxWidth(), label = { Text("识图/转述模型名称") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onConfig(config.copy(visionProvider = visionProvider, visionBaseUrl = visionApiBaseUrl.trim(), visionApiKey = visionApiKey.trim(), visionModel = visionModel.trim().ifBlank { config.visionModel })); expandedModule = null }) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("保存多模态模块")
                }
                    OutlinedButton(onClick = {
                        scope.launch {
                            visionStatus = "获取中..."
                            val fetched = fetchModels(visionApiBaseUrl, visionApiKey)
                            if (fetched.isNotEmpty()) {
                                visionModel = fetched.first()
                                visionStatus = "已获取 ${fetched.size} 个模型"
                            } else {
                                visionStatus = "获取失败，可手动填写"
                            }
                        }
                    }) { Text("获取模型", color = MaterialTheme.colorScheme.primary) }
                }
                Text(visionStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsCard("TTS 模块", expandedModule == "tts", { expandedModule = if (expandedModule == "tts") null else "tts" }) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ttsProviders.forEach { provider ->
                        AppFilterChip(ttsProvider == provider, {
                            ttsProvider = provider
                            ttsBaseUrl = defaultBaseUrl(provider)
                        }) { Text(provider) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(ttsBaseUrl, { ttsBaseUrl = it }, Modifier.fillMaxWidth(), label = { Text("TTS Base URL") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(ttsApiKey, { ttsApiKey = it }, Modifier.fillMaxWidth(), label = { Text("TTS API Key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(ttsModel, { ttsModel = it }, Modifier.weight(1f), label = { Text("TTS 模型") }, singleLine = true)
                    OutlinedTextField(ttsVoice, { ttsVoice = it }, Modifier.weight(1f), label = { Text("音色") }, singleLine = true)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RecordVoiceOver, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("长时开启 AI 回复朗读", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Switch(ttsAutoRead, { ttsAutoRead = it })
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { onConfig(config.copy(ttsProvider = ttsProvider, ttsApiKey = ttsApiKey.trim(), ttsBaseUrl = ttsBaseUrl.trim(), ttsModel = ttsModel.trim(), ttsVoice = ttsVoice.trim(), ttsAutoRead = ttsAutoRead)); expandedModule = null }) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("保存 TTS 模块")
                    }
                    OutlinedButton(onClick = {
                        scope.launch {
                            ttsStatus = "获取中..."
                            val fetched = fetchModels(ttsBaseUrl, ttsApiKey)
                            if (fetched.isNotEmpty()) {
                                ttsModel = fetched.first()
                                ttsStatus = "已获取 ${fetched.size} 个模型"
                            } else {
                                ttsStatus = "获取失败，可手动填写"
                            }
                        }
                    }) { Text("获取模型", color = MaterialTheme.colorScheme.primary) }
                }
                Text(ttsStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsCard("皮肤与界面", expandedModule == "theme", { expandedModule = if (expandedModule == "theme") null else "theme" }) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ColorLens, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("对话从下到上", modifier = Modifier.weight(1f))
                    Switch(reverseConversation, { reverseConversation = it })
                }
                Spacer(Modifier.height(10.dp))
                Text("按钮皮肤", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppFilterChip(themeMode == "ocean", {
                        themeMode = "ocean"
                        primaryColor = 0xFF39C5BB
                        secondaryColor = 0xFF00AEEF
                        primaryHex = argbToHex(primaryColor)
                    }) { Text("二次元") }
                    AppFilterChip(themeMode == "custom", {
                        themeMode = "custom"
                        primaryHex = argbToHex(primaryColor)
                    }) { Text("自定义") }
                }
                Spacer(Modifier.height(10.dp))
                Text("界面明暗", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "自动", "light" to "白色", "dark" to "黑色").forEach { option ->
                        AppFilterChip(interfaceMode == option.first, { interfaceMode = option.first }) { Text(option.second) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (themeMode == "custom") {
                    ButtonColorPreview(primaryColor)
                    Spacer(Modifier.height(10.dp))
                    Text("调色盘", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        colorSwatches.forEach { swatch ->
                            ColorSwatch(swatch, primaryColor == swatch) {
                                primaryColor = swatch
                                secondaryColor = swatch
                                primaryHex = argbToHex(swatch)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        primaryHex,
                        {
                            primaryHex = it
                            parseHexColor(it)?.let { color ->
                                primaryColor = color
                                secondaryColor = color
                            }
                        },
                        Modifier.fillMaxWidth(),
                        label = { Text("按钮颜色 Hex") },
                        singleLine = true,
                        isError = !primaryValid
                    )
                    if (!primaryValid) Text("请输入 #RRGGBB 或 #AARRGGBB", color = Color(0xFFB42318), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("自定义颜色只应用到按钮和可点击强调，不再染色背景、卡片或正文。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                } else {
                    ButtonColorPreview(0xFF39C5BB)
                    Spacer(Modifier.height(8.dp))
                    Text("二次元按钮色固定为 #39C5BB，避免旧自定义值导致颜色失效。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        themeMode = "ocean"
                        primaryColor = 0xFF39C5BB
                        secondaryColor = 0xFF00AEEF
                        primaryHex = argbToHex(primaryColor)
                    }) { Text("恢复二次元默认") }
                    Button(
                        onClick = {
                            val cleanThemeMode = normalizeThemeMode(themeMode)
                            val cleanPrimary = if (cleanThemeMode == "ocean") 0xFF39C5BB else parseHexColor(primaryHex) ?: primaryColor
                            val cleanSecondary = if (cleanThemeMode == "ocean") 0xFF00AEEF else cleanPrimary
                            primaryColor = cleanPrimary
                            secondaryColor = cleanSecondary
                            primaryHex = argbToHex(cleanPrimary)
                            themeMode = cleanThemeMode
                            onConfig(config.copy(reverseConversation = reverseConversation, themeMode = cleanThemeMode, interfaceMode = normalizeInterfaceMode(interfaceMode), primaryColor = cleanPrimary, secondaryColor = cleanSecondary))
                            expandedModule = null
                        },
                        enabled = themeMode == "ocean" || primaryValid
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("保存皮肤")
                    }
                }
            }
        }
        item {
            SettingsCard("讲师人格与模式模块", expandedModule == "mentor", { expandedModule = if (expandedModule == "mentor") null else "mentor" }) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(mentorName, { mentorName = it }, Modifier.weight(1f), label = { Text("讲师名字") }, singleLine = true)
                    OutlinedTextField(userAlias, { userAlias = it }, Modifier.weight(1f), label = { Text("对你的称呼") }, singleLine = true)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(mentorPrompt, { mentorPrompt = it }, Modifier.fillMaxWidth(), label = { Text("讲师人格提示词") }, minLines = 4)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.HealthAndSafety, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("高效模式", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Switch(efficientMode, { efficientMode = it })
                }
                Text("过滤 NSFW 内容。", color = Muted)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onConfig(config.copy(mentorName = mentorName.trim().ifBlank { "AI 讲师" }, userAlias = userAlias.trim().ifBlank { "同学" }, mentorPrompt = mentorPrompt.trim().ifBlank { DEFAULT_MENTOR_PROMPT }, efficientMode = efficientMode)); expandedModule = null }) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("保存讲师人格与模式模块")
                }
            }
        }
        item {
            InfoCard {
                Text("使用手册", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("查看 AI Classroom 的课堂、分支、记忆、知识库、模型配置、皮肤和考试工具说明。", color = Muted, lineHeight = 21.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenManual) { Text("打开使用手册") }
            }
        }
        item { Text(saveNotice, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(4.dp)) }
    }
}

@Composable
private fun ColorSwatch(value: Long, selected: Boolean, onClick: () -> Unit) {
    val color = colorFromLong(value)
    Surface(
        onClick = onClick,
        modifier = Modifier.size(42.dp),
        shape = RoundedCornerShape(14.dp),
        color = color,
        border = androidx.compose.foundation.BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
    ) {
        if (selected) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, tint = if (color.luminanceValue() > 0.58f) Color.Black else Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onToggle) { Text(if (expanded) "收起" else "展开", color = MaterialTheme.colorScheme.primary) }
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
private fun AppFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        shape = RoundedCornerShape(9.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = MaterialTheme.colorScheme.primary
        )
    )
}


@Composable
private fun ButtonColorPreview(buttonValue: Long) {
    val button = colorFromLong(buttonValue)
    val onButton = if (button.luminanceValue() > 0.58f) Color(0xFF062526) else Color.White
    Card(Modifier.fillMaxWidth(), shape = AppShapes.card, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("按钮预览", fontWeight = FontWeight.Bold)
            Text("背景和文字保持原生明暗风格，只有按钮与可点击强调使用此颜色。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = button, shape = AppShapes.button) { Text("主操作", color = onButton, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), fontWeight = FontWeight.Bold) }
                Surface(color = button.copy(alpha = 0.12f).compositeOn(MaterialTheme.colorScheme.surface), shape = AppShapes.button) { Text("次级", color = button, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun MarkdownText(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    val lines = sanitizeMathText(text).lines()
    val textColor = color
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val mathColor = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("```") -> Text(trimmed, fontFamily = FontFamily.Monospace, color = mutedColor)
                trimmed.startsWith("#") -> Text(trimmed.trimStart('#', ' '), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textColor)
                trimmed.startsWith("-") || trimmed.startsWith("*") -> Text("• ${trimmed.drop(1).trim()}", color = textColor, lineHeight = 21.sp)
                isMathLikeLine(trimmed) -> Text(trimmed, fontFamily = FontFamily.Monospace, color = mathColor, lineHeight = 21.sp)
                else -> Text(buildInlineMarkdown(trimmed), color = textColor, lineHeight = 21.sp)
            }
        }
    }
}

private fun isMathLikeLine(line: String): Boolean =
    line.contains("$") || line.contains("\\(") || line.contains("\\[") || line.contains("\\begin") || line.contains("\\text") || line.contains("\\phi") || line.contains("\\psi")

private fun sanitizeMathText(raw: String): String {
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

private fun buildInlineMarkdown(line: String) = buildAnnotatedString {
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
            if (end > next) { withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = Blue)) { append(line.substring(next + 1, end)) }; i = end + 1 } else { append('`'); i = next + 1 }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = AppShapes.card, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(12.dp), content = content)
    }
}

private fun newClassroom(number: Int, config: ClassroomConfig = ClassroomConfig()) = Classroom(
    name = "课堂 $number",
    topic = "自定义学习内容",
    messages = mutableStateListOf(),
    branches = mutableStateListOf(),
    memories = mutableStateListOf("等待开始。"),
    chapters = mutableStateListOf(),
    files = mutableStateListOf(),
    config = config
)

private class ClassroomStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ai_classroom_14", Context.MODE_PRIVATE)
    private val dataFile = File(context.filesDir, "ai_classroom_14_classes.json")
    private val tempFile = File(context.filesDir, "ai_classroom_14_classes.tmp")

    fun load(): List<Classroom> {
        val raw = when {
            dataFile.exists() -> dataFile.readText(Charsets.UTF_8)
            else -> prefs.getString("classes", null)
                ?: prefs.getString("classes_backup", null)
        } ?: return listOf(newClassroom(1))
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index -> array.getJSONObject(index).toClassroom(index + 1) }
        }.getOrDefault(listOf(newClassroom(1)))
    }

    fun loadIndex(lastIndex: Int): Int = prefs.getInt("class_index", 0).coerceIn(0, lastIndex.coerceAtLeast(0))

    fun hasSeenReleaseNotes(version: String): Boolean = prefs.getBoolean("seen_release_notes_$version", false)

    fun markReleaseNotesSeen(version: String) {
        prefs.edit().putBoolean("seen_release_notes_$version", true).apply()
    }

    fun canShowUpdateToday(): Boolean {
        val today = System.currentTimeMillis() / DAY_MS
        return prefs.getLong("last_update_prompt_day", -1L) != today
    }

    fun markUpdateCheckedToday() {
        prefs.edit().putLong("last_update_prompt_day", System.currentTimeMillis() / DAY_MS).apply()
    }

    fun save(classes: List<Classroom>, classIndex: Int) {
        val payload = JSONArray(classes.map { it.toJson() }).toString()
        runCatching {
            tempFile.writeText(payload, Charsets.UTF_8)
            runCatching {
                Files.move(tempFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.getOrElse {
                Files.move(tempFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }.onFailure {
            prefs.edit().putString("classes_backup", payload).apply()
        }
        prefs.edit()
            .putInt("class_index", classIndex)
            .apply()
    }
}

private fun JSONObject.toClassroom(number: Int): Classroom {
    val configJson = optJSONObject("config") ?: JSONObject()
    return Classroom(
        name = optString("name", "课堂 $number"),
        topic = optString("topic", "自定义学习内容"),
        messages = optJSONArray("messages").toMessages().filterNot { it.isLegacyWelcomeMessage() }.toMutableStateList(),
        branches = optJSONArray("branches").toBranches().toMutableStateList(),
        memories = optJSONArray("memories").toStrings().ifEmpty { listOf("等待开始。") }.toMutableStateList(),
        chapters = optJSONArray("chapters").toChapters().toMutableStateList(),
        files = optJSONArray("files").toFiles().toMutableStateList(),
        config = ClassroomConfig(
            provider = configJson.optString("provider", "OpenAI"),
            apiKey = configJson.optString("apiKey", ""),
            baseUrl = configJson.optString("baseUrl", "https://api.openai.com/v1"),
            selectedModel = configJson.optString("selectedModel", "gpt-4o-mini"),
            customModel = configJson.optString("customModel", ""),
            modelChain = configJson.optString("modelChain", configJson.optString("customModel", configJson.optString("selectedModel", "gpt-4o-mini"))),
            deepThinkingEnabled = configJson.optBoolean("deepThinkingEnabled", false),
            deepThinkingModel = configJson.optString("deepThinkingModel", ""),
            visionProvider = configJson.optString("visionProvider", configJson.optString("provider", "OpenAI")),
            visionApiKey = configJson.optString("visionApiKey", configJson.optString("apiKey", "")),
            visionBaseUrl = configJson.optString("visionBaseUrl", configJson.optString("baseUrl", "https://api.openai.com/v1")),
            visionModel = configJson.optString("visionModel", "gpt-4o-mini"),
            ttsProvider = configJson.optString("ttsProvider", "OpenAI"),
            ttsApiKey = configJson.optString("ttsApiKey", ""),
            ttsBaseUrl = configJson.optString("ttsBaseUrl", "https://api.openai.com/v1"),
            ttsModel = configJson.optString("ttsModel", "tts-1"),
            ttsVoice = configJson.optString("ttsVoice", "alloy"),
            ttsAutoRead = configJson.optBoolean("ttsAutoRead", false),
            mentorName = configJson.optString("mentorName", "AI 讲师"),
            userAlias = configJson.optString("userAlias", "同学"),
            mentorPrompt = configJson.optString("mentorPrompt", ClassroomConfig().mentorPrompt),
            efficientMode = configJson.optBoolean("efficientMode", true),
            reverseConversation = configJson.optBoolean("reverseConversation", false),
            themeMode = normalizeThemeMode(configJson.optString("themeMode", "ocean")),
            interfaceMode = normalizeInterfaceMode(configJson.optString("interfaceMode", legacyInterfaceMode(configJson.optString("themeMode", "ocean")))),
            primaryColor = configJson.optLong("primaryColor", 0xFF39C5BB),
            secondaryColor = configJson.optLong("secondaryColor", 0xFF00AEEF)
        )
    )
}

private fun Classroom.toJson() = JSONObject().apply {
    put("name", name)
    put("topic", topic)
    put("messages", JSONArray(messages.map { JSONObject().put("role", it.role).put("text", it.text) }))
    put("branches", JSONArray(branches.map { branch ->
        JSONObject().put("title", branch.title).put("source", branch.source).put("memory", branch.memory)
            .put("messages", JSONArray(branch.messages.map { JSONObject().put("role", it.role).put("text", it.text) }))
            .put("context", JSONArray(branch.context.map { JSONObject().put("role", it.role).put("text", it.text) }))
    }))
    put("memories", JSONArray(memories))
    put("chapters", JSONArray(chapters.map { JSONObject().put("title", it.title).put("summary", it.summary).put("startIndex", it.startIndex).put("endIndex", it.endIndex) }))
    put("files", JSONArray(files.map { JSONObject().put("name", it.name).put("type", it.type).put("chars", it.chars).put("preview", it.preview).put("content", it.content) }))
    put("config", JSONObject().put("provider", config.provider).put("apiKey", config.apiKey).put("baseUrl", config.baseUrl).put("selectedModel", config.selectedModel).put("customModel", config.customModel).put("modelChain", config.modelChain).put("deepThinkingEnabled", config.deepThinkingEnabled).put("deepThinkingModel", config.deepThinkingModel).put("visionProvider", config.visionProvider).put("visionApiKey", config.visionApiKey).put("visionBaseUrl", config.visionBaseUrl).put("visionModel", config.visionModel).put("ttsProvider", config.ttsProvider).put("ttsApiKey", config.ttsApiKey).put("ttsBaseUrl", config.ttsBaseUrl).put("ttsModel", config.ttsModel).put("ttsVoice", config.ttsVoice).put("ttsAutoRead", config.ttsAutoRead).put("mentorName", config.mentorName).put("userAlias", config.userAlias).put("mentorPrompt", config.mentorPrompt).put("efficientMode", config.efficientMode).put("reverseConversation", config.reverseConversation).put("themeMode", config.themeMode).put("interfaceMode", config.interfaceMode).put("primaryColor", config.primaryColor).put("secondaryColor", config.secondaryColor))
}

private fun JSONArray?.toMessages(): List<ChatMessage> = if (this == null) emptyList() else List(length()) { getJSONObject(it).let { item -> ChatMessage(item.optString("role"), item.optString("text")) } }
private fun ChatMessage.isLegacyWelcomeMessage(): Boolean = role == "assistant" && text.startsWith("输入学习目标，我会开始主课堂教学。")
private fun JSONArray?.toBranches(): List<BranchClass> = if (this == null) emptyList() else List(length()) { getJSONObject(it).let { item ->
    val messages = item.optJSONArray("messages").toMessages().toMutableStateList()
    val context = item.optJSONArray("context").toMessages().ifEmpty { messages.take(BRANCH_CONTEXT_LIMIT) }.toMutableStateList()
    BranchClass(item.optString("title"), item.optString("source"), messages, item.optString("memory"), context)
} }
private fun JSONArray?.toStrings(): List<String> = if (this == null) emptyList() else List(length()) { optString(it) }
private fun JSONArray?.toChapters(): List<ConversationChapter> = if (this == null) emptyList() else List(length()) { getJSONObject(it).let { item -> ConversationChapter(item.optString("title"), item.optString("summary"), item.optInt("startIndex"), item.optInt("endIndex")) } }
private fun JSONArray?.toFiles(): List<KnowledgeFile> = if (this == null) emptyList() else List(length()) { getJSONObject(it).let { item ->
    val preview = item.optString("preview")
    KnowledgeFile(item.optString("name"), item.optString("type"), item.optInt("chars"), preview, item.optString("content", preview))
} }
private fun <T> List<T>.toMutableStateList() = mutableStateListOf<T>().also { it.addAll(this) }

private fun defaultBaseUrl(provider: String) = when (provider) {
    "OpenAI" -> "https://api.openai.com/v1"
    "DeepSeek" -> "https://api.deepseek.com/v1"
    "通义千问" -> "https://dashscope.aliyuncs.com/compatible-mode/v1"
    else -> "https://api.openai.com/v1"
}

private suspend fun fetchModels(baseUrl: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
    if (apiKey.isBlank()) return@withContext emptyList()
    runCatching {
        val connection = URL(baseUrl.trimEnd('/') + "/models").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        Regex("\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").findAll(readBody(connection)).map { it.groupValues[1] }.take(40).toList()
    }.getOrDefault(emptyList())
}

private suspend fun checkGitHubUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL(GITHUB_LATEST_RELEASE_API).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "AI-Classroom-Android")
        connection.connectTimeout = 6000
        connection.readTimeout = 8000
        val raw = readBody(connection)
        if (connection.responseCode !in 200..299) return@runCatching null
        val json = JSONObject(raw)
        val version = json.optString("tag_name").ifBlank { json.optString("name") }
        if (version.isBlank()) return@runCatching null
        UpdateInfo(
            version = version,
            name = json.optString("name", version),
            url = json.optString("html_url", GITHUB_RELEASES_URL),
            notes = json.optString("body").take(500)
        )
    }.getOrNull()
}

private fun isRemoteVersionNewer(remote: String, local: String): Boolean {
    val remoteParts = versionParts(remote)
    val localParts = versionParts(local)
    val size = maxOf(remoteParts.size, localParts.size, 3)
    repeat(size) { index ->
        val r = remoteParts.getOrElse(index) { 0 }
        val l = localParts.getOrElse(index) { 0 }
        if (r != l) return r > l
    }
    return false
}

private fun versionParts(value: String): List<Int> =
    Regex("\\d+").findAll(value).map { it.value.toIntOrNull() ?: 0 }.toList()

private suspend fun callChat(baseUrl: String, apiKey: String, model: String, system: String, messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
    if (apiKey.isBlank()) return@withContext "请先填写 API Key。"
    if (model.isBlank()) return@withContext "请先选择或填写模型名。"
    runCatching {
        val connection = URL(baseUrl.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 20000
        connection.readTimeout = 60000
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(buildJson(model, system, messages.takeLast(16))) }
        Regex("\\\"content\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").find(readBody(connection))?.groupValues?.get(1)?.unescapeJson() ?: "模型没有返回内容。"
    }.getOrElse { "调用失败：${it.message ?: "未知错误"}" }
}

private suspend fun callChatStream(
    baseUrl: String,
    apiKey: String,
    model: String,
    system: String,
    messages: List<ChatMessage>,
    onDelta: (String) -> Unit
): String = withContext(Dispatchers.IO) {
    if (apiKey.isBlank()) return@withContext "请先填写 API Key。"
    if (model.isBlank()) return@withContext "请先选择或填写模型名。"
    runCatching {
        val connection = URL(baseUrl.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "text/event-stream")
        connection.connectTimeout = 20000
        connection.readTimeout = 120000
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(buildJson(model, system, messages.takeLast(16), stream = true)) }
        if (connection.responseCode !in 200..299) return@runCatching readBody(connection).ifBlank { "调用失败：HTTP ${connection.responseCode}" }
        val builder = StringBuilder()
        BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).useLines { lines ->
            lines.forEach { line ->
                if (!line.startsWith("data:")) return@forEach
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") return@forEach
                val delta = parseStreamDelta(payload)
                if (delta.isNotEmpty()) {
                    builder.append(delta)
                    withContext(Dispatchers.Main) { onDelta(delta) }
                }
            }
        }
        builder.toString().ifBlank { "模型没有返回内容。" }
    }.getOrElse { "调用失败：${it.message ?: "未知错误"}" }
}

private suspend fun callChatWithFallback(config: ClassroomConfig, models: List<String>, system: String, messages: List<ChatMessage>): String {
    val candidates = models.ifEmpty { config.orderedModels() }
    var last = "请先选择或填写模型名。"
    candidates.forEach { model ->
        val result = callChat(config.baseUrl, config.apiKey, model, system, messages)
        if (!isApiFailure(result)) return result
        last = result
    }
    return last
}

private suspend fun callChatStreamWithFallback(
    config: ClassroomConfig,
    models: List<String>,
    system: String,
    messages: List<ChatMessage>,
    onDelta: (String) -> Unit
): String {
    val candidates = models.ifEmpty { config.orderedModels() }
    var last = "请先选择或填写模型名。"
    candidates.forEach { model ->
        val result = callChatStream(config.baseUrl, config.apiKey, model, system, messages, onDelta)
        if (!isApiFailure(result)) return result
        last = result
    }
    return last
}

private suspend fun callVisionWithFallback(config: ClassroomConfig, models: List<String>, system: String, prompt: String, dataUrl: String): String = withContext(Dispatchers.IO) {
    if (config.apiKey.isBlank()) return@withContext "请先填写 API Key。"
    val candidates = models.ifEmpty { config.visionModels() }
    var last = "请先选择或填写识图模型名。"
    candidates.forEach { model ->
        val result = callVision(config.visionBaseUrlOrMain(), config.visionApiKeyOrMain(), model, system, prompt, dataUrl)
        if (!isApiFailure(result)) return@withContext result
        last = result
    }
    last
}

private suspend fun callVision(baseUrl: String, apiKey: String, model: String, system: String, prompt: String, dataUrl: String): String = withContext(Dispatchers.IO) {
    if (apiKey.isBlank()) return@withContext "请先填写 API Key。"
    if (model.isBlank()) return@withContext "请先选择或填写识图模型名。"
    runCatching {
        val connection = URL(baseUrl.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 20000
        connection.readTimeout = 90000
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(buildVisionJson(model, system, prompt, dataUrl)) }
        if (connection.responseCode !in 200..299) return@runCatching readBody(connection).ifBlank { "调用失败：HTTP ${connection.responseCode}" }
        Regex("\\\"content\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").find(readBody(connection))?.groupValues?.get(1)?.unescapeJson() ?: "模型没有返回内容。"
    }.getOrElse { "调用失败：${it.message ?: "未知错误"}" }
}

private fun speakText(context: Context, config: ClassroomConfig, text: String) {
    if (config.ttsApiKey.isBlank() || config.ttsModel.isBlank()) return
    val clean = stripMarkdownForSpeech(text).take(1800)
    if (clean.isBlank()) return
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        val audio = callTts(config, clean)
        if (audio.isEmpty()) return@launch
        runCatching {
            val file = File(context.cacheDir, "ai_classroom_tts.mp3")
            FileOutputStream(file).use { it.write(audio) }
            withContext(Dispatchers.Main) {
                MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnCompletionListener { player -> player.release() }
                    setOnErrorListener { player, _, _ -> player.release(); true }
                    prepare()
                    start()
                }
            }
        }
    }
}

private suspend fun callTts(config: ClassroomConfig, text: String): ByteArray = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL(config.ttsBaseUrl.trimEnd('/') + "/audio/speech").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer ${config.ttsApiKey}")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 20000
        connection.readTimeout = 90000
        val body = "{\"model\":\"${config.ttsModel.escapeJson()}\",\"voice\":\"${config.ttsVoice.escapeJson()}\",\"input\":\"${text.escapeJson()}\"}"
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
        if (connection.responseCode !in 200..299) ByteArray(0) else connection.inputStream.use { it.readBytes() }
    }.getOrDefault(ByteArray(0))
}

private fun stripMarkdownForSpeech(text: String): String = sanitizeMathText(text)
    .replace(Regex("```[\\s\\S]*?```"), " ")
    .replace(Regex("[#>*_`\\[\\]()]"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

private fun isApiFailure(result: String): Boolean {
    val text = result.lowercase()
    return result.startsWith("调用失败") || result.startsWith("请先") || text.contains("http 4") || text.contains("http 5") || text.contains("unauthorized") || text.contains("invalid api")
}

private fun parseStreamDelta(payload: String): String = runCatching {
    val choice = JSONObject(payload).optJSONArray("choices")?.optJSONObject(0) ?: return@runCatching ""
    choice.optJSONObject("delta")?.optNonNullString("content")
        ?: choice.optJSONObject("message")?.optNonNullString("content")
        ?: ""
}.getOrDefault("")

private fun JSONObject.optNonNullString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).takeUnless { it == "null" }
}

private const val EXAM_TOOL_PROMPT = """
当你明确要发起考试、随堂测试或模拟测验时，必须在回复中使用以下格式，应用会自动进入沉浸式考试界面：
【考试】
前提：题目背景、材料、阅读短文或说明
问题：具体问题一
问题：具体问题二
可以重复多组“前提/问题”。不要把考试作为普通聊天问题发出。
"""

private const val EXAM_TOOL_PROMPT_V2 = """
When you start an exam, quiz, mock test, or the user asks to be tested, append a machine-readable exam block at the end:
[EXAM]
premise: background, reading passage, data, or instructions
question: concrete question one
question: concrete question two
[/EXAM]
Repeat premise/question groups when needed. Put real exam questions inside the block.
"""

private suspend fun buildConversationChapters(messages: List<ChatMessage>, config: ClassroomConfig, model: String): List<ConversationChapter> = withContext(Dispatchers.IO) {
    val chunks = messages.chunked(CHAPTER_SIZE)
    chunks.mapIndexed { chunkIndex, chunk ->
        val start = chunkIndex * CHAPTER_SIZE
        val end = (start + chunk.size - 1).coerceAtLeast(start)
        val local = localChapterSummary(chunkIndex, chunk, start, end)
        if (config.apiKey.isBlank() || model.isBlank()) {
            local
        } else {
            val prompt = "请把下面这段课堂对话总结成一个章节标题和一句话摘要。只返回 JSON：{\"title\":\"...\",\"summary\":\"...\"}\n" +
                chunk.joinToString("\n") { "${if (it.role == "user") "用户" else "AI"}：${it.text}" }.take(4000)
            val result = callChat(config.baseUrl, config.apiKey, model, "你负责为 AI 课堂生成简洁的对话章节索引。", listOf(ChatMessage("user", prompt)))
            parseChapter(result, start, end) ?: local
        }
    }
}

private fun isExamRequest(text: String): Boolean {
    val triggers = listOf("考试", "测验", "测试", "考我", "出题", "随堂", "模拟考", "quiz", "exam", "test")
    return triggers.any { text.contains(it, ignoreCase = true) }
}

private fun detectExamSession(text: String, userRequestedExam: Boolean): ExamSession? {
    parseExamBlock(text)?.let { return it }
    parseReadableExam(text)?.let { return it }
    return if (userRequestedExam && hasExplicitExamStart(text)) fallbackExamFromText(text) else null
}

private fun hasExplicitExamStart(text: String): Boolean {
    val triggers = listOf("【考试】", "[EXAM]", "开始考试", "进入考试", "模拟考试", "随堂测试", "本次测试", "开始测验", "进入测验")
    return triggers.any { text.contains(it, ignoreCase = true) }
}

private fun stripExamBlock(text: String): String =
    text.replace(Regex("\\[EXAM][\\s\\S]*?\\[/EXAM]", RegexOption.IGNORE_CASE), "").trim()

private fun parseExamBlock(text: String): ExamSession? {
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

private fun parseReadableExam(text: String): ExamSession? {
    val normalized = text.replace('：', ':')
    if (!hasExplicitExamStart(normalized)) return null
    val premise = Regex("(?:前提|材料|背景):([\\s\\S]*?)(?=(?:问题|题目|Q\\d*)[:：]|$)")
        .find(normalized)?.groupValues?.getOrNull(1)?.replace("【考试】", "")?.trim().orEmpty().ifBlank { "请根据当前课堂内容作答。" }
    val questions = Regex("(?:问题|题目|Q\\d*)[:：]([\\s\\S]*?)(?=(?:问题|题目|Q\\d*|前提|材料|背景)[:：]|$)")
        .findAll(normalized).mapNotNull { it.groupValues.getOrNull(1)?.trim()?.takeIf { q -> q.isNotBlank() }?.let { q -> ExamQuestion(premise, q) } }
        .toList()
    return questions.takeIf { it.isNotEmpty() }?.let { ExamSession("AI 随堂考试", it.toMutableStateList()) }
}

private fun fallbackExamFromText(text: String): ExamSession {
    val candidates = text.lines().map { it.trim() }.filter { line ->
        line.length >= 8 && (line.contains("？") || line.contains("?") || line.matches(Regex(".*(简述|说明|分析|解释|计算|写出).*")))
    }.take(6)
    val questions = candidates.ifEmpty { listOf("请结合刚才课堂内容，回答本轮测试的核心问题。") }
        .map { ExamQuestion("请根据当前课堂内容作答。", it.removePrefix("问题:").removePrefix("题目:").trim()) }
    return ExamSession("AI 随堂考试", questions.toMutableStateList())
}

private fun packExamAnswers(session: ExamSession): String = buildString {
    appendLine("以下是用户在沉浸式考试工具中的作答，请批改并给出讲解：")
    session.questions.forEachIndexed { index, q ->
        appendLine("题目 ${index + 1}")
        appendLine("前提：${q.premise}")
        appendLine("问题：${q.question}")
        appendLine("状态：${if (q.unknown) "不会" else "已作答"}")
        appendLine("答案：${q.answer}")
    }
    if (session.draft.isNotBlank()) appendLine("草稿：${session.draft}")
}

private fun fallbackChapters(messages: List<ChatMessage>): List<ConversationChapter> = messages.chunked(CHAPTER_SIZE).mapIndexed { index, chunk ->
    val start = index * CHAPTER_SIZE
    localChapterSummary(index, chunk, start, start + chunk.size - 1)
}

private fun localChapterSummary(index: Int, messages: List<ChatMessage>, start: Int, end: Int): ConversationChapter {
    val firstUser = messages.firstOrNull { it.role == "user" }?.text?.replace(Regex("\\s+"), " ").orEmpty()
    val title = if (firstUser.isBlank()) "章节 ${index + 1}" else firstUser.take(18)
    val summary = messages.joinToString(" ") { it.text }.replace(Regex("\\s+"), " ").take(80).ifBlank { "本章暂无摘要。" }
    return ConversationChapter(title, summary, start, end)
}

private fun parseChapter(raw: String, start: Int, end: Int): ConversationChapter? = runCatching {
    val jsonText = Regex("\\{[\\s\\S]*\\}").find(raw)?.value ?: return@runCatching null
    val json = JSONObject(jsonText)
    val title = json.optString("title").ifBlank { return@runCatching null }
    val summary = json.optString("summary").ifBlank { return@runCatching null }
    ConversationChapter(title.take(28), summary.take(120), start, end)
}.getOrNull()

private fun readBody(connection: HttpURLConnection): String {
    val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
    return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
}

private fun buildJson(model: String, system: String, messages: List<ChatMessage>, stream: Boolean = false): String {
    val all = listOf(ChatMessage("system", system)) + messages
    val jsonMessages = all.joinToString(",") { "{\"role\":\"${it.role}\",\"content\":\"${it.text.escapeJson()}\"}" }
    return "{\"model\":\"${model.escapeJson()}\",\"messages\":[$jsonMessages],\"temperature\":0.7,\"stream\":$stream}"
}

private fun buildVisionJson(model: String, system: String, prompt: String, dataUrl: String): String {
    return """
        {"model":"${model.escapeJson()}","messages":[{"role":"system","content":"${system.escapeJson()}"},{"role":"user","content":[{"type":"text","text":"${prompt.escapeJson()}"},{"type":"image_url","image_url":{"url":"${dataUrl.escapeJson()}"}}]}],"temperature":0.7}
    """.trimIndent()
}

private fun summarize(scope: String, messages: List<ChatMessage>): String = "$scope 记忆：" + messages.joinToString(" ") { it.text }.replace(Regex("\\s+"), " ").take(180)

private fun filterNsfw(text: String, enabled: Boolean): String {
    if (!enabled) return text
    val blocked = listOf("色情", "裸露", "约炮", "血腥", "自残", "自杀", "nsfw", "porn", "nude", "kill myself")
    return if (blocked.any { text.contains(it, ignoreCase = true) }) "高效模式已过滤不适合学习场景的内容。" else text
}

private fun String.escapeJson(): String = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
private fun String.unescapeJson(): String = replace("\\n", "\n").replace("\\\"", "\"").replace("\\/", "/").replace("\\\\", "\\")

@Composable
private fun AIClassroomTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(primary = Blue, secondary = Green, tertiary = Purple, background = Page, surface = Color.White, onBackground = Ink, onSurface = Ink),
        content = content
    )
}

private fun paletteFor(config: ClassroomConfig, systemDark: Boolean): AppPalette {
    val dark = when (normalizeInterfaceMode(config.interfaceMode)) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val button = when (normalizeThemeMode(config.themeMode)) {
        "custom" -> colorFromLong(config.primaryColor)
        else -> Color(0xFF39C5BB)
    }
    val onButton = if (button.luminanceValue() > 0.58f) Color(0xFF062526) else Color.White
    return if (dark) {
        AppPalette(
            page = Color(0xFF101214),
            surface = Color(0xFF171A1D),
            card = Color(0xFF202428),
            ink = Color(0xFFF2F4F5),
            muted = Color(0xFFA6ADB4),
            button = button,
            onButton = onButton,
            outline = Color(0xFF343A40)
        )
    } else {
        AppPalette(
            page = Color(0xFFF6F7F9),
            surface = Color(0xFFFFFFFF),
            card = Color(0xFFF0F2F5),
            ink = Color(0xFF171A1D),
            muted = Color(0xFF68727D),
            button = button,
            onButton = onButton,
            outline = Color(0xFFE0E4EA)
        )
    }
}

private fun blendOnWhite(primary: Color, secondary: Color, alpha: Float): Color = Color(
    red = ((primary.red + secondary.red) / 2f) * alpha + (1f - alpha),
    green = ((primary.green + secondary.green) / 2f) * alpha + (1f - alpha),
    blue = ((primary.blue + secondary.blue) / 2f) * alpha + (1f - alpha),
    alpha = 1f
)

private fun colorFromLong(value: Long): Color = Color(value.toInt())
private fun argbToHex(value: Long): String = "#" + (value and 0xFFFFFFFFL).toString(16).padStart(8, '0').uppercase()
private fun parseHexColor(raw: String): Long? {
    val clean = raw.trim().removePrefix("#")
    val argb = when (clean.length) {
        6 -> "FF$clean"
        8 -> clean
        else -> return null
    }
    return argb.toLongOrNull(16)?.takeIf { it in 0..0xFFFFFFFFL }
}
private fun normalizeThemeMode(raw: String): String = when (raw) {
    "custom", "single", "自定义", "单主色", "鍗曚富鑹?" -> "custom"
    else -> "ocean"
}

private fun normalizeInterfaceMode(raw: String): String = when (raw) {
    "dark", "black", "黑色", "黑" -> "dark"
    "light", "white", "白色", "白" -> "light"
    else -> "system"
}

private fun legacyInterfaceMode(themeMode: String): String = when (themeMode) {
    "mono" -> "light"
    "system" -> "system"
    else -> "system"
}

private fun Color.luminanceValue(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
private fun Color.compositeOnWhite(): Color = Color(
    red = red * alpha + (1f - alpha),
    green = green * alpha + (1f - alpha),
    blue = blue * alpha + (1f - alpha),
    alpha = 1f
)
private fun Color.compositeOn(base: Color): Color = Color(
    red = red * alpha + base.red * (1f - alpha),
    green = green * alpha + base.green * (1f - alpha),
    blue = blue * alpha + base.blue * (1f - alpha),
    alpha = 1f
)

private fun mentorPriorityBlock(config: ClassroomConfig): String {
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

private fun promptPriorityReminder(config: ClassroomConfig): String {
    val mentorName = config.mentorName.trim().ifBlank { "AI 讲师" }
    val userAlias = config.userAlias.trim().ifBlank { "同学" }
    return "[优先级提醒] 回答时再次确认：始终优先遵守用户自定义讲师人格提示词；你是 $mentorName，并按设定称呼用户为 $userAlias。记忆、知识库和分支上下文只用于补充事实与连续性。"
}

private fun truncatePromptSection(text: String, limit: Int): String {
    val clean = text.trim()
    if (clean.length <= limit) return clean
    return clean.take(limit) + "\n[以上资料已按上下文预算截断，截断部分不可臆测。]"
}

private const val APP_VERSION = "2.3"

private object AppShapes {
    val panel = RoundedCornerShape(22.dp)
    val card = RoundedCornerShape(18.dp)
    val control = RoundedCornerShape(18.dp)
    val button = RoundedCornerShape(16.dp)
    val menu = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
}

private const val RELEASE_NOTES_TEXT = """
# AI Classroom 2.3

# 这次更新

- 优化讲师人格提示词模块，用户自定义提示词会作为最高优先级写入系统提示词。
- 长期记忆、知识库和分支上下文变长时，不再覆盖讲师人格与用户自定义教学要求。
- 系统提示词首尾都会保留人格优先级提醒，减少超长对话后风格漂移。
- 保存讲师人格时，如果提示词被误清空，会自动恢复默认中文教学提示词。

# 延续优化

- 主课堂和分支课堂中的讲师名称继续实时跟随设置更新。
- 所有课堂、分支、设置、知识库、记忆和考试记录继续保存在本机。
"""
private const val USER_MANUAL_TEXT = """
# AI Classroom 使用手册

## 快速开始
第一次使用时，先进入设置页，展开“模型模块”，填写 Base URL、API Key 和至少一个可用模型名并保存。回到主课堂后，输入你想学习的内容，例如“从零开始学 C 语言指针”，AI 会围绕这个课堂持续教学。

## 主课堂
主课堂是一门课程的主线。你可以输入学习目标、追问问题、让 AI 出例题、讲解代码或总结章节。课堂内容、对话、章节索引、摘要和配置都会保存在本地。

主课堂输入框右侧的图片按钮可以上传照片。上传后，应用会把图片交给设置页里的识图或转述模型分析，并把结果保存进当前课堂。

## 分支课堂
长按或点击对话下方的“从这里开分支”，可以从任意一段主课堂对话开始一条平行支线。分支会带上创建时的主课堂上下文，之后的所有问答只保存在分支中，不会复制、打包或改写主课堂。

适合在不打断主线的情况下追问背景知识、补基础、展开例子或处理临时问题。

## 记忆
记忆页用于快速查找长期对话。应用会在后台按批次整理课堂内容，生成章节标题和一句话摘要，不会在每一句对话后阻塞前台回复。

全览模式会生成实时向量思维导图，章节按摘要相似度自动连接；章节模式可双击章节跳回主课堂对应位置。

## 知识库
知识库目前可直接读取 `.md` 和 `.txt` 文件。上传后文件会保存在本地列表中，点击可查看全文，长按可删除。AI 讲师会读取知识库正文的可控长度片段，并结合你的材料教学。

## API 与模型
模型模块用于保存普通课堂对话的服务商、Base URL、API Key 和模型名。它支持自动获取模型，也可以手动输入模型名。

模型优先级支持一行一个模型。应用会先调用第一行模型，如果连接失败或接口返回错误，会自动尝试下一行模型。

深度思考模式开启后，会优先使用单独填写的深度思考模型。关闭后，应用只按普通模型优先级调用。

## 多模态图片
多模态模块拥有独立的服务商、Base URL、API Key 和识图/转述模型名称。它可以与普通对话模型不同。主课堂上传照片时会优先调用多模态模块；如果该模块未填写 API Key，则会回退使用模型模块的 API Key。

## TTS
TTS 模块用于保存语音服务配置，包括服务商、API Key、Base URL、模型和音色。预设服务商可快速填入常见 Base URL，也可以选择自定义。获取模型按钮会尝试从兼容接口读取模型列表；如果失败，可以手动填写模型名。

双击 AI 讲师回复会调用 TTS 朗读。开启“长时开启 AI 回复朗读”后，新的 AI 回复会在生成完成后自动朗读。

## 讲师人格
讲师人格提示词可以自定义，例如大学教授、企业工程师、考研老师、幽默导师或二次元导师。讲师名字会实时显示在主课堂和分支课堂中；讲师对你的称呼会写入提示词，让模型在教学时按这个称呼与你互动。保存后当前课堂会持续使用该人格。

讲师人格提示词拥有最高优先级。即使长期记忆、知识库和分支上下文不断变长，应用也会在系统提示词首尾重复人格优先级提醒，要求模型优先遵守用户保存的人格提示词和教学风格。

## 高效模式
高效模式用于过滤 NSFW 等不适合学习场景的内容，默认开启。它本质上是健康模式，适合学习、自习和考试场景。

## 界面与皮肤
皮肤模块可切换对话方向、按钮颜色和界面明暗。按钮皮肤只保留“二次元”和“自定义”：二次元固定使用 `#39C5BB`，自定义可通过调色盘或 Hex 输入选择一个按钮色。除按钮和可点击强调外，背景、卡片和正文不再被皮肤色染色。

界面明暗可选择自动跟随系统、白色或黑色，应用会自动保持文字反色，保证可读性。

## 多课堂
在主界面从左向右滑出课堂菜单，可以切换课堂、新建课堂、删除课堂，也可以复制其他课堂配置。每个课堂都可以有独立 API、模型、人格、皮肤和知识库配置。

## 考试工具
考试不是普通入口。AI 明确要进行考试、测试或模拟测验时，应用会自动进入沉浸式考试界面。

题目由“前提”和“问题”组成。用户在每个问题后填写答案并提交批改。标记“不会”后，这道题会自动生成讲解分支，方便考试结束后继续学习。

## 本地保存
所有课堂、分支、对话、知识库摘要、记忆章节、设置和考试记录都会保存在手机本地。手机重启或应用版本更新后，数据仍会保留。
"""

private val Page = Color(0xFFF3F8FA)
private val Ink = Color(0xFF102027)
private val Muted = Color(0xFF60727A)
private val Blue = Color(0xFF2563EB)
private val Green = Color(0xFF10A7B5)
private val Purple = Color(0xFF0E7490)
private const val DEFAULT_MENTOR_PROMPT = "你是一名耐心、结构清晰的 AI 讲师。默认使用中文教学，保持主线课程连续，并在必要时用 Markdown 和公式文本表达。"
private const val MEMORY_PROMPT_LIMIT = 24
private const val MEMORY_CONTEXT_LIMIT = 6000
private const val KNOWLEDGE_CONTEXT_LIMIT = 9000
private const val BRANCH_CONTEXT_LIMIT = 24
private const val BRANCH_CONTEXT_LIMIT_CHARS = 5000
private const val CHAPTER_SIZE = 12
private const val MEMORY_BATCH_MESSAGE_COUNT = 8
private const val MEMORY_BATCH_DELAY_MS = 45000L
private const val MAIN_MEMORY_PREFIX = "主课堂记忆："
private const val DAY_MS = 86_400_000L
private const val GITHUB_RELEASES_URL = "https://github.com/AHWJ-Alpha/AI-Classroom/releases"
private const val GITHUB_LATEST_RELEASE_API = "https://api.github.com/repos/AHWJ-Alpha/AI-Classroom/releases/latest"


