package com.aiclassroom.app

import android.content.Context
import android.content.res.Configuration
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.South
import androidx.compose.material.icons.filled.North
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
private data class KnowledgeFile(val name: String, val type: String, val chars: Int, val preview: String)
private data class ConversationChapter(val title: String, val summary: String, val startIndex: Int, val endIndex: Int)
private data class ExamQuestion(val premise: String, val question: String, val answer: String = "", val unknown: Boolean = false)
private data class ExamSession(val title: String, val questions: MutableList<ExamQuestion>, val draft: String = "", val submitted: Boolean = false)
private data class UpdateInfo(val version: String, val name: String, val url: String, val notes: String)
private data class DrawStroke(val points: List<Offset>)
private data class ThemePreset(val mode: String, val title: String, val subtitle: String, val primary: Long, val secondary: Long)
private data class AppPalette(
    val page: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val primary: Color,
    val secondary: Color,
    val accent: Color
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
    val mentorPrompt: String = "你是一名耐心、结构清晰的 AI 讲师。默认使用中文教学，保持主线课程连续，并在必要时用 Markdown 和公式文本表达。",
    val efficientMode: Boolean = true,
    val reverseConversation: Boolean = false,
    val themeMode: String = "ocean",
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
    val palette = remember(current.config) { paletteFor(current.config) }

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
        val knowledge = room.files.joinToString("\n") { "[${it.name}] ${it.preview}" }.take(3000)
        val memory = room.memories.takeLast(MEMORY_PROMPT_LIMIT).joinToString("\n")
        val safety = if (room.config.efficientMode) "高效模式：过滤 NSFW、色情、血腥、违法、仇恨和自伤内容。" else ""
        return "${room.config.mentorPrompt}\n课堂：${room.name}\n学习内容：${room.topic}\n记忆：$memory\n知识库：$knowledge\n$safety"
    }

    fun branchSystemPrompt(room: Classroom, branch: BranchClass): String {
        val context = branch.context.joinToString("\n") { "${if (it.role == "user") "用户" else "AI"}：${it.text}" }.take(5000)
        return systemPrompt(room) + "\n当前处于分支课堂。分支是与主课堂平行的长对话，不会改写主课堂；请只延续本分支。\n分支来源：${branch.source}\n分支创建时的主课堂上下文：\n$context\n分支摘要：${branch.memory}"
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
        }
    }

    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = palette.secondary,
            secondary = palette.primary,
            tertiary = palette.accent,
            background = palette.page,
            surface = palette.surface,
            surfaceVariant = palette.primary.copy(alpha = 0.10f).compositeOnWhite(),
            primaryContainer = palette.secondary.copy(alpha = 0.16f).compositeOnWhite(),
            secondaryContainer = palette.primary.copy(alpha = 0.14f).compositeOnWhite(),
            outline = palette.secondary.copy(alpha = 0.34f),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = palette.ink,
            onSurface = palette.ink,
            onSurfaceVariant = palette.muted
        )
    ) {
    Scaffold(
        topBar = {
            if (chromeVisible || tab != Tab.Class) {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Classroom $APP_VERSION", fontWeight = FontWeight.Bold, color = palette.ink)
                        Text("${current.name} · ${current.topic}", color = palette.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = { TextButton(onClick = { classMenuOpen = !classMenuOpen }) { Text("课堂", color = palette.secondary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.page)
            )
            }
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
                            selectedIconColor = palette.secondary,
                            selectedTextColor = palette.secondary,
                            indicatorColor = palette.secondary.copy(alpha = 0.13f).compositeOnWhite(),
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
                .pointerInput(classes.size, classIndex, classMenuOpen) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (!classMenuOpen && total > 80f) classMenuOpen = true
                            if (classMenuOpen && total < -80f) classMenuOpen = false
                            total = 0f
                        },
                        onHorizontalDrag = { _, dragAmount -> total += dragAmount }
                    )
                },
            color = palette.page
        ) {
            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    Tab.Class -> ClassScreen(current, classIndex, classes.size, input, { input = it }, isLoading, palette, jumpToMessageIndex, { jumpToMessageIndex = null }, current.config.reverseConversation, onOpenMenu = { classMenuOpen = true }, onSend = { sendMessage() }, onImage = { imageLauncher.launch("image/*") }, onDeleteAfter = { index ->
                        if (index in current.messages.indices) {
                            for (i in current.messages.lastIndex downTo index) current.messages.removeAt(i)
                            current.chapters.clear()
                            persist("已删除后续对话")
                        }
                    }, onRewrite = { index, text ->
                        if (index in current.messages.indices) {
                            current.messages[index] = current.messages[index].copy(text = text)
                            current.chapters.clear()
                            persist("对话已重写")
                        }
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
                    Tab.Branch -> BranchScreen(current.branches, activeBranchIndex, branchInput, { branchInput = it }, branchLoading, palette, onSelect = { activeBranchIndex = it }, onBack = { activeBranchIndex = -1 }, onSend = { sendBranchMessage(activeBranchIndex) })
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
                    ChromeToggleButton(
                        visible = chromeVisible,
                        palette = palette,
                        onClick = { chromeVisible = !chromeVisible },
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)
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
                    ReleaseNotesDialog(onClose = {
                        store.markReleaseNotesSeen(APP_VERSION)
                        showNewDialog = false
                    })
                }
                updateInfo?.let { info ->
                    UpdateDialog(info, onClose = { updateInfo = null })
                }
                if (showManualDialog) {
                    UserManualDialog(onClose = { showManualDialog = false })
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
    onNew: () -> Unit,
    onNewWithConfig: (Int) -> Unit,
    onCopyConfig: (Int) -> Unit,
    onDelete: (Int) -> Unit
) {
    Row(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxHeight().fillMaxWidth(0.78f), color = MaterialTheme.colorScheme.surface, shape = AppShapes.menu) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentPadding = PaddingValues(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("课堂", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(saveNotice, color = palette.secondary, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("新建课堂")
                    }
                }
                items(classes.indices.toList()) { i ->
                    val room = classes[i]
                    Card(Modifier.fillMaxWidth(), shape = AppShapes.card, colors = CardDefaults.cardColors(if (i == classIndex) palette.secondary.copy(alpha = 0.10f).compositeOnWhite() else Color(0xFFF7F8FA))) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(room.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(room.config.customModel.ifBlank { room.config.selectedModel }, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (i == classIndex) Icon(Icons.Default.Check, null, tint = palette.secondary, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { onSelect(i) }) { Text("进入") }
                                OutlinedButton(onClick = { onNewWithConfig(i) }) { Text("复制") }
                                TextButton(onClick = { onDelete(i) }) { Icon(Icons.Default.Delete, null, Modifier.size(16.dp)); Text("删除") }
                            }
                            if (i != classIndex) TextButton(onClick = { onCopyConfig(i) }) { Text("复制配置到当前课堂") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassScreen(
    room: Classroom,
    index: Int,
    count: Int,
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
    onRewrite: (Int, String) -> Unit,
    onBranch: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var rewriteTarget by remember { mutableStateOf<Pair<Int, String>?>(null) }
    val lastItemIndex = room.messages.size + 1
    val bottomIndex = if (reverseConversation) 0 else lastItemIndex
    val topIndex = if (reverseConversation) lastItemIndex else 0
    val isAtBottom by remember { derivedStateOf { listState.firstVisibleItemIndex == bottomIndex } }
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
            item {
                InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("课堂 ${index + 1}/$count", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = onOpenMenu) { Text("切换") }
                    }
                    Text(room.topic, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            items(room.messages.size) { i -> MessageCard(i, room.messages[i], palette, onBranch, onDeleteAfter, { rewriteTarget = i to room.messages[i].text }) }
            if (isLoading) item { AiThinkingRow(palette) }
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
        Button(
            onClick = { scope.launch { if (isAtBottom) listState.animateScrollToItem(topIndex) else listState.animateScrollToItem(bottomIndex) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = if (compactInput) 122.dp else 188.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Icon(if (isAtBottom) Icons.Default.North else Icons.Default.South, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (isAtBottom) "开头" else "底部")
        }
        rewriteTarget?.let { target ->
            RewriteDialog(
                initial = target.second,
                onClose = { rewriteTarget = null },
                onSave = { newText ->
                    onRewrite(target.first, newText)
                    rewriteTarget = null
                }
            )
        }
    }
}

@Composable
private fun ChromeToggleButton(visible: Boolean, palette: AppPalette, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = palette.secondary.copy(alpha = 0.14f).compositeOnWhite(),
        shape = RoundedCornerShape(999.dp),
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.secondary.copy(alpha = 0.22f))
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Tune, contentDescription = null, tint = palette.secondary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            Text(if (visible) "隐藏导航" else "显示导航", color = palette.secondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    contentColor = palette.secondary,
                    disabledContentColor = palette.secondary.copy(alpha = 0.35f)
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
                        focusedBorderColor = palette.secondary,
                        unfocusedBorderColor = palette.secondary.copy(alpha = 0.28f),
                        cursorColor = palette.secondary,
                        focusedContainerColor = palette.page.copy(alpha = 0.45f),
                        unfocusedContainerColor = palette.page.copy(alpha = 0.35f)
                    )
                )
                Button(
                    onClick = onSend,
                    enabled = !isLoading,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 5.dp, bottom = 5.dp).size(if (compact) 40.dp else 44.dp),
                    shape = AppShapes.button,
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.secondary, contentColor = Color.White)
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
    onBranch: (Int) -> Unit,
    onDeleteAfter: (Int) -> Unit,
    onRewrite: (Int) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val actionModifier = Modifier.combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
    Box(Modifier.fillMaxWidth()) {
    if (message.role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(
                Modifier
                    .fillMaxWidth(0.82f)
                    .then(actionModifier)
                    .background(palette.secondary.copy(alpha = 0.13f).compositeOnWhite(), AppShapes.card)
                    .border(1.dp, palette.secondary.copy(alpha = 0.24f), AppShapes.card)
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
            Text("AI 讲师", color = palette.secondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            MarkdownText(message.text)
            TextButton(onClick = { onBranch(index) }) { Text("从这里开分支") }
        }
    }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("从这里开分支") }, onClick = { menuOpen = false; onBranch(index) })
            DropdownMenuItem(text = { Text("重写这段内容") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuOpen = false; onRewrite(index) })
            DropdownMenuItem(text = { Text("删除此处及之后") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menuOpen = false; onDeleteAfter(index) })
        }
    }
}

@Composable
private fun RewriteDialog(initial: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("重写对话内容", fontWeight = FontWeight.Bold) },
        text = { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 5) },
        confirmButton = { Button(onClick = { onSave(text) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onClose) { Text("取消") } }
    )
}

@Composable
private fun AiThinkingRow(palette: AppPalette) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text("AI 讲师", color = palette.secondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Text("正在回复...", color = Muted)
    }
}

@Composable
private fun ReleaseNotesDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("New!", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { MarkdownText(RELEASE_NOTES_TEXT) }
            }
        },
        confirmButton = { Button(onClick = onClose) { Text("知道了") } }
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
private fun UserManualDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("使用手册", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.height(460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { MarkdownText(USER_MANUAL_TEXT) }
            }
        },
        confirmButton = { Button(onClick = onClose) { Text("关闭") } }
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
    onSelect: (Int) -> Unit,
    onBack: () -> Unit,
    onSend: () -> Unit
) {
    if (activeIndex in branches.indices) {
        BranchChatScreen(branches[activeIndex], input, onInput, isLoading, palette, onBack, onSend)
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
                SimpleMessageCard(branch.messages[i], palette)
            }
            if (isLoading) item { AiThinkingRow(palette) }
        }
        ChatInputBar(input, onInput, isLoading, compact = false, palette = palette, onSend = onSend, onImage = {}, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SimpleMessageCard(message: ChatMessage, palette: AppPalette) {
    if (message.role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(
                Modifier
                    .fillMaxWidth(0.82f)
                    .background(palette.secondary.copy(alpha = 0.11f).compositeOnWhite(), AppShapes.card)
                    .border(1.dp, palette.secondary.copy(alpha = 0.2f), AppShapes.card)
                    .padding(12.dp)
            ) {
                Text("我", color = palette.secondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(message.text, color = palette.ink, lineHeight = 21.sp)
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text("AI 分支讲师", color = palette.secondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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

@Composable
private fun MemoryMindMap(chapters: List<ConversationChapter>, onJump: (Int) -> Unit) {
    val nodes = chapters.take(18)
    val links = remember(nodes) { buildChapterLinks(nodes) }
    val width = 320.dp
    val height = 420.dp
    InfoCard {
        Text("实时向量思维导图", fontWeight = FontWeight.Bold)
        Text("章节会按摘要关键词相似度自动连接，点击节点可跳回对应对话。", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(height)) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension * 0.36f
                val positions = nodes.indices.associateWith { index ->
                    if (nodes.size == 1) center else Offset(
                        center.x + cos((index * 2.0 * Math.PI / nodes.size) - Math.PI / 2).toFloat() * radius,
                        center.y + sin((index * 2.0 * Math.PI / nodes.size) - Math.PI / 2).toFloat() * radius
                    )
                }
                links.forEach { (from, to, weight) ->
                    val a = positions[from] ?: return@forEach
                    val b = positions[to] ?: return@forEach
                    drawLine(Color(0xFF00AEEF).copy(alpha = (0.18f + weight * 0.34f).coerceIn(0.18f, 0.52f)), a, b, strokeWidth = 2.5f + weight * 3f, cap = StrokeCap.Round)
                }
                positions.values.forEach { point ->
                    drawCircle(Color(0xFF39C5BB).copy(alpha = 0.18f), radius = 34f, center = point)
                    drawCircle(Color(0xFF00AEEF), radius = 12f, center = point)
                }
            }
            nodes.forEachIndexed { index, chapter ->
                val angle = if (nodes.size == 1) -Math.PI / 2 else (index * 2.0 * Math.PI / nodes.size) - Math.PI / 2
                val x = 50 + (cos(angle) * 118).roundToInt()
                val y = 178 + (sin(angle) * 156).roundToInt()
                Surface(
                    onClick = { onJump(chapter.startIndex) },
                    modifier = Modifier
                        .width(116.dp)
                        .offset(x.dp, y.dp),
                    shape = RoundedCornerShape(9.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00AEEF).copy(alpha = 0.2f))
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text(chapter.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(chapter.summary, color = Muted, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (nodes.size < chapters.size) Text("已显示最近 ${nodes.size} 个章节。", color = Muted, fontSize = 12.sp)
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
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "knowledge"
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext == "md" || ext == "txt") {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            files.add(KnowledgeFile(name, ext, text.length, text.take(1000)))
            onSave()
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { InfoCard { Text("可直接读取：.md、.txt", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Button(onClick = { launcher.launch("text/*") }) { Text("读取文件") } } }
        items(files) { file -> InfoCard { Text(file.name, fontWeight = FontWeight.Bold); Text("${file.type} · ${file.chars} 字", color = Muted, fontSize = 13.sp); Spacer(Modifier.height(6.dp)); MarkdownText(file.preview) } }
    }
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
    var ttsStatus by remember { mutableStateOf("未获取语音模型") }
    var mentorPrompt by remember(config.mentorPrompt) { mutableStateOf(config.mentorPrompt) }
    var efficientMode by remember(config.efficientMode) { mutableStateOf(config.efficientMode) }
    var reverseConversation by remember(config.reverseConversation) { mutableStateOf(config.reverseConversation) }
    var themeMode by remember(config.themeMode) { mutableStateOf(config.themeMode) }
    var primaryColor by remember(config.primaryColor) { mutableStateOf(config.primaryColor) }
    var secondaryColor by remember(config.secondaryColor) { mutableStateOf(config.secondaryColor) }
    var primaryHex by remember(config.primaryColor) { mutableStateOf(argbToHex(config.primaryColor)) }
    var secondaryHex by remember(config.secondaryColor) { mutableStateOf(argbToHex(config.secondaryColor)) }
    val canCustomizeColors = themeMode == "ocean" || themeMode == "single"
    val primaryValid = parseHexColor(primaryHex) != null
    val secondaryValid = parseHexColor(secondaryHex) != null
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { onConfig(config.copy(ttsProvider = ttsProvider, ttsApiKey = ttsApiKey.trim(), ttsBaseUrl = ttsBaseUrl.trim(), ttsModel = ttsModel.trim(), ttsVoice = ttsVoice.trim())); expandedModule = null }) {
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
                Text("皮肤风格", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    THEME_PRESETS.forEach { preset ->
                        ThemePresetChip(
                            preset = preset,
                            selected = themeMode == preset.mode,
                            onClick = {
                                themeMode = preset.mode
                                primaryColor = preset.primary
                                secondaryColor = preset.secondary
                                primaryHex = argbToHex(preset.primary)
                                secondaryHex = argbToHex(preset.secondary)
                            }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (canCustomizeColors) {
                    ThemePreview(primaryColor, secondaryColor)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        primaryHex,
                        {
                            primaryHex = it
                            parseHexColor(it)?.let { color ->
                                primaryColor = color
                                if (themeMode == "single") secondaryColor = color
                            }
                        },
                        Modifier.fillMaxWidth(),
                        label = { Text(if (themeMode == "single") "单色 Hex" else "主色 Hex") },
                        singleLine = true,
                        isError = !primaryValid
                    )
                    if (!primaryValid) Text("请输入 #RRGGBB 或 #AARRGGBB", color = Color(0xFFB42318), fontSize = 12.sp)
                    if (themeMode == "ocean") {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            secondaryHex,
                            {
                                secondaryHex = it
                                parseHexColor(it)?.let { color -> secondaryColor = color }
                            },
                            Modifier.fillMaxWidth(),
                            label = { Text("辅色 Hex") },
                            singleLine = true,
                            isError = !secondaryValid
                        )
                        if (!secondaryValid) Text("请输入 #RRGGBB 或 #AARRGGBB", color = Color(0xFFB42318), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("二次元可自定义主色和辅色；单色只使用一个颜色生成界面层次。", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val ocean = THEME_PRESETS.first()
                        themeMode = ocean.mode
                        primaryColor = ocean.primary
                        secondaryColor = ocean.secondary
                        primaryHex = argbToHex(ocean.primary)
                        secondaryHex = argbToHex(ocean.secondary)
                    }) { Text("恢复二次元默认") }
                    Button(
                        onClick = {
                            val cleanPrimary = parseHexColor(primaryHex) ?: primaryColor
                            val cleanSecondary = if (themeMode == "single") cleanPrimary else parseHexColor(secondaryHex) ?: secondaryColor
                            primaryColor = cleanPrimary
                            secondaryColor = cleanSecondary
                            primaryHex = argbToHex(cleanPrimary)
                            secondaryHex = argbToHex(cleanSecondary)
                            val cleanThemeMode = when {
                                themeMode == "mono" || themeMode == "system" || themeMode == "ocean" -> themeMode
                                cleanPrimary == cleanSecondary -> "single"
                                else -> "ocean"
                            }
                            themeMode = cleanThemeMode
                            onConfig(config.copy(reverseConversation = reverseConversation, themeMode = cleanThemeMode, primaryColor = cleanPrimary, secondaryColor = cleanSecondary))
                            expandedModule = null
                        },
                        enabled = primaryValid && secondaryValid
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
                Button(onClick = { onConfig(config.copy(mentorPrompt = mentorPrompt.trim(), efficientMode = efficientMode)); expandedModule = null }) {
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
private fun ThemePresetChip(preset: ThemePreset, selected: Boolean, onClick: () -> Unit) {
    val primary = colorFromLong(preset.primary)
    val secondary = colorFromLong(preset.secondary)
    Card(
        onClick = onClick,
        modifier = Modifier.width(178.dp).height(96.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(if (selected) primary.copy(alpha = 0.13f).compositeOnWhite() else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(if (selected) 3.dp else 1.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(18.dp).background(primary, RoundedCornerShape(5.dp)))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(18.dp).background(secondary, RoundedCornerShape(5.dp)))
                Spacer(Modifier.width(8.dp))
                Text(preset.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (selected) Icon(Icons.Default.Check, null, tint = primary, modifier = Modifier.size(18.dp)) else Spacer(Modifier.size(18.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(preset.subtitle, color = Muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
private fun ThemePreview(primaryValue: Long, secondaryValue: Long) {
    val primary = colorFromLong(primaryValue)
    val secondary = colorFromLong(secondaryValue)
    Card(Modifier.fillMaxWidth(), shape = AppShapes.card, colors = CardDefaults.cardColors(primary.copy(alpha = 0.08f).compositeOnWhite()), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).background(primary, RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(34.dp).background(secondary, RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("当前预览", fontWeight = FontWeight.Bold)
                    Text("${argbToHex(primaryValue)} / ${argbToHex(secondaryValue)}", color = Muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = primary, shape = AppShapes.button) { Text("主操作", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
                Surface(color = secondary.copy(alpha = 0.14f).compositeOnWhite(), shape = AppShapes.button) { Text("辅助强调", color = secondary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
            }
        }
    }
}

@Composable
private fun MarkdownText(text: String) {
    val lines = sanitizeMathText(text).lines()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("```") -> Text(trimmed, fontFamily = FontFamily.Monospace, color = Muted)
                trimmed.startsWith("#") -> Text(trimmed.trimStart('#', ' '), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
                trimmed.startsWith("-") || trimmed.startsWith("*") -> Text("• ${trimmed.drop(1).trim()}", color = Ink, lineHeight = 21.sp)
                isMathLikeLine(trimmed) -> Text(trimmed, fontFamily = FontFamily.Monospace, color = Purple, lineHeight = 21.sp)
                else -> Text(buildInlineMarkdown(trimmed), color = Ink, lineHeight = 21.sp)
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
    messages = mutableStateListOf(ChatMessage("assistant", "输入学习目标，我会开始主课堂教学。支持 Markdown 与公式文本，例如 `f(x)=x^2` 或 ${'$'}E=mc^2${'$'}。")),
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
        messages = optJSONArray("messages").toMessages().toMutableStateList(),
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
            mentorPrompt = configJson.optString("mentorPrompt", ClassroomConfig().mentorPrompt),
            efficientMode = configJson.optBoolean("efficientMode", true),
            reverseConversation = configJson.optBoolean("reverseConversation", false),
            themeMode = normalizeThemeMode(configJson.optString("themeMode", "ocean")),
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
    put("files", JSONArray(files.map { JSONObject().put("name", it.name).put("type", it.type).put("chars", it.chars).put("preview", it.preview) }))
    put("config", JSONObject().put("provider", config.provider).put("apiKey", config.apiKey).put("baseUrl", config.baseUrl).put("selectedModel", config.selectedModel).put("customModel", config.customModel).put("modelChain", config.modelChain).put("deepThinkingEnabled", config.deepThinkingEnabled).put("deepThinkingModel", config.deepThinkingModel).put("visionProvider", config.visionProvider).put("visionApiKey", config.visionApiKey).put("visionBaseUrl", config.visionBaseUrl).put("visionModel", config.visionModel).put("ttsProvider", config.ttsProvider).put("ttsApiKey", config.ttsApiKey).put("ttsBaseUrl", config.ttsBaseUrl).put("ttsModel", config.ttsModel).put("ttsVoice", config.ttsVoice).put("mentorPrompt", config.mentorPrompt).put("efficientMode", config.efficientMode).put("reverseConversation", config.reverseConversation).put("themeMode", config.themeMode).put("primaryColor", config.primaryColor).put("secondaryColor", config.secondaryColor))
}

private fun JSONArray?.toMessages(): List<ChatMessage> = if (this == null) emptyList() else List(length()) { getJSONObject(it).let { item -> ChatMessage(item.optString("role"), item.optString("text")) } }
private fun JSONArray?.toBranches(): List<BranchClass> = if (this == null) emptyList() else List(length()) { getJSONObject(it).let { item ->
    val messages = item.optJSONArray("messages").toMessages().toMutableStateList()
    val context = item.optJSONArray("context").toMessages().ifEmpty { messages.take(BRANCH_CONTEXT_LIMIT) }.toMutableStateList()
    BranchClass(item.optString("title"), item.optString("source"), messages, item.optString("memory"), context)
} }
private fun JSONArray?.toStrings(): List<String> = if (this == null) emptyList() else List(length()) { optString(it) }
private fun JSONArray?.toChapters(): List<ConversationChapter> = if (this == null) emptyList() else List(length()) { getJSONObject(it).let { item -> ConversationChapter(item.optString("title"), item.optString("summary"), item.optInt("startIndex"), item.optInt("endIndex")) } }
private fun JSONArray?.toFiles(): List<KnowledgeFile> = if (this == null) emptyList() else List(length()) { getJSONObject(it).let { item -> KnowledgeFile(item.optString("name"), item.optString("type"), item.optInt("chars"), item.optString("preview")) } }
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

private fun paletteFor(config: ClassroomConfig): AppPalette {
    val primary = colorFromLong(config.primaryColor)
    val secondary = colorFromLong(config.secondaryColor)
    return when (config.themeMode) {
        "mono" -> AppPalette(Color(0xFFF7F7F8), Color(0xFFFFFFFF), Color(0xFF171717), Color(0xFF686868), Color(0xFF111111), Color(0xFF555555), Color(0xFF2F2F2F))
        "single" -> AppPalette(primary.copy(alpha = 0.07f).compositeOnWhite(), primary.copy(alpha = 0.035f).compositeOnWhite(), Ink, Muted, primary, primary.copy(alpha = 0.72f).compositeOnWhite(), primary.copy(alpha = 0.55f).compositeOnWhite())
        else -> AppPalette(blendOnWhite(primary, secondary, 0.055f), blendOnWhite(primary, secondary, 0.025f), Color(0xFF0F2630), Color(0xFF5B6F78), primary, secondary, blendOnWhite(primary, secondary, 0.65f))
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
    "mono", "黑白", "榛戠櫧" -> "mono"
    "single", "单主色", "鍗曚富鑹?" -> "single"
    "dual", "双主色", "鍙屼富鑹?" -> "ocean"
    "system", "Follow System", "跟随系统" -> "system"
    else -> "ocean"
}
private fun Color.compositeOnWhite(): Color = Color(
    red = red * alpha + (1f - alpha),
    green = green * alpha + (1f - alpha),
    blue = blue * alpha + (1f - alpha),
    alpha = 1f
)

private const val APP_VERSION = "2.1.1"

private object AppShapes {
    val panel = RoundedCornerShape(22.dp)
    val card = RoundedCornerShape(18.dp)
    val control = RoundedCornerShape(18.dp)
    val button = RoundedCornerShape(16.dp)
    val menu = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
}

private val THEME_PRESETS = listOf(
    ThemePreset("ocean", "二次元", "清透青绿、亮蓝点缀", 0xFF39C5BB, 0xFF00AEEF),
    ThemePreset("mono", "黑白", "克制灰阶界面", 0xFF111111, 0xFF666666),
    ThemePreset("single", "单主色", "用主色生成轻重层次", 0xFF39C5BB, 0xFF39C5BB),
    ThemePreset("system", "跟随系统", "保留系统浅色基调", 0xFF39C5BB, 0xFF00AEEF)
)

private const val RELEASE_NOTES_TEXT = """
# AI Classroom 2.1.1

# 这次更新

- 主课堂图片按钮去掉外框，移动到输入框左侧，输入区重新留白避免压线。
- 发送按钮压缩为更轻量的圆角图标按钮，输入区更适合手机单手操作。
- 应用核心面板、输入框和按钮统一为更大的圆角风格。
- README、首次打开弹窗和版本号同步到 2.1.1。

# 延续优化

- 默认皮肤仍为 #39C5BB 与 #00AEEF。
- 继续保留 2.1 的独立模型、多模态和 TTS 配置逻辑。
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
知识库目前可直接读取 `.md` 和 `.txt` 文件。文件摘要会加入课堂上下文，帮助 AI 结合你的材料教学。

## API 与模型
模型模块用于保存普通课堂对话的服务商、Base URL、API Key 和模型名。它支持自动获取模型，也可以手动输入模型名。

模型优先级支持一行一个模型。应用会先调用第一行模型，如果连接失败或接口返回错误，会自动尝试下一行模型。

深度思考模式开启后，会优先使用单独填写的深度思考模型。关闭后，应用只按普通模型优先级调用。

## 多模态图片
多模态模块拥有独立的服务商、Base URL、API Key 和识图/转述模型名称。它可以与普通对话模型不同。主课堂上传照片时会优先调用多模态模块；如果该模块未填写 API Key，则会回退使用模型模块的 API Key。

## TTS
TTS 模块用于保存语音服务配置，包括服务商、API Key、Base URL、模型和音色。预设服务商可快速填入常见 Base URL，也可以选择自定义。获取模型按钮会尝试从兼容接口读取模型列表；如果失败，可以手动填写模型名。

当前版本先完成 TTS 配置保存，为后续语音朗读和讲师发声功能预留。

## 讲师人格
讲师人格提示词可以自定义，例如大学教授、企业工程师、考研老师、幽默导师或二次元导师。保存后当前课堂会持续使用该人格。

## 高效模式
高效模式用于过滤 NSFW 等不适合学习场景的内容，默认开启。它本质上是健康模式，适合学习、自习和考试场景。

## 界面与皮肤
皮肤模块可切换对话方向和界面色调。默认是二次元青蓝色调，也可以选择黑白、跟随系统或单色。二次元和单色皮肤支持自定义颜色，默认颜色为 `#39C5BB` 和 `#00AEEF`。导航栏、按钮、输入框、筛选项和主要卡片都会跟随皮肤。

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
private const val MEMORY_PROMPT_LIMIT = 24
private const val BRANCH_CONTEXT_LIMIT = 24
private const val CHAPTER_SIZE = 12
private const val MEMORY_BATCH_MESSAGE_COUNT = 8
private const val MEMORY_BATCH_DELAY_MS = 45000L
private const val MAIN_MEMORY_PREFIX = "主课堂记忆："
private const val DAY_MS = 86_400_000L
private const val GITHUB_RELEASES_URL = "https://github.com/AHWJ-Alpha/AI-Classroom/releases"
private const val GITHUB_LATEST_RELEASE_API = "https://api.github.com/repos/AHWJ-Alpha/AI-Classroom/releases/latest"


