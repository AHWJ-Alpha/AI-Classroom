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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
private data class BranchClass(val title: String, val source: String, val messages: MutableList<ChatMessage>, val memory: String)
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
    val mentorPrompt: String = "你是一名耐心、结构清晰的 AI 讲师。默认使用中文教学，保持主线课程连续，并在必要时用 Markdown 和公式文本表达。",
    val efficientMode: Boolean = true,
    val reverseConversation: Boolean = false,
    val themeMode: String = "ocean",
    val primaryColor: Long = 0xFF39C5BB,
    val secondaryColor: Long = 0xFF00AEEF
)

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
    val activeModel = current.config.customModel.ifBlank { current.config.selectedModel }
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
            val result = callChatStream(room.config.baseUrl, room.config.apiKey, activeModel, systemPrompt(room) + "\n" + EXAM_TOOL_PROMPT + "\n" + EXAM_TOOL_PROMPT_V2, room.messages.dropLast(1).toList()) { delta ->
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

    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = palette.secondary,
            secondary = palette.primary,
            tertiary = palette.accent,
            background = palette.page,
            surface = palette.surface,
            onBackground = palette.ink,
            onSurface = palette.ink
        )
    ) {
    Scaffold(
        topBar = {
            if (chromeVisible || tab != Tab.Class) {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Classroom $APP_VERSION", fontWeight = FontWeight.Bold)
                        Text("${current.name} · ${current.topic}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = { TextButton(onClick = { classMenuOpen = !classMenuOpen }) { Text("课堂") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.page)
            )
            }
        },
        bottomBar = {
            if (chromeVisible || tab != Tab.Class) {
            NavigationBar(containerColor = palette.surface) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(selected = tab == item, onClick = { tab = item; classMenuOpen = false }, icon = { Icon(item.icon, contentDescription = item.title) }, label = { Text(item.title) })
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
                    Tab.Class -> ClassScreen(current, classIndex, classes.size, input, { input = it }, isLoading, palette, jumpToMessageIndex, { jumpToMessageIndex = null }, current.config.reverseConversation, onOpenMenu = { classMenuOpen = true }, onSend = { sendMessage() }, onDeleteAfter = { index ->
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
                        val selected = current.messages.drop(index)
                        val branchMessages = selected.take(BRANCH_CONTEXT_LIMIT).toMutableStateList()
                        val title = selected.firstOrNull()?.text?.take(18)?.ifBlank { "分支课堂" } ?: "分支课堂"
                        current.branches.add(BranchClass(title, "${current.name} 第 ${index + 1} 条起", branchMessages, summarize("分支", selected)))
                        current.memories.add(summarize("分支回写", selected))
                        persist("分支和记忆已保存")
                        tab = Tab.Branch
                    }
                    Tab.Branch -> BranchScreen(current.branches) { branch ->
                        tab = Tab.Class
                        sendMessage("继续这个分支：${branch.source}\n${branch.messages.joinToString("\n") { it.role + ":" + it.text }}")
                    }
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
                            val answer = callChat(current.config.baseUrl, current.config.apiKey, activeModel, systemPrompt(current), listOf(ChatMessage("user", branchText)))
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
        Surface(Modifier.fillMaxHeight().fillMaxWidth(0.78f), color = Color.White, shape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)) {
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
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(if (i == classIndex) palette.secondary.copy(alpha = 0.10f).compositeOnWhite() else Color(0xFFF7F8FA))) {
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
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 6.dp),
        color = palette.surface,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 3.dp
    ) {
        Box(Modifier.fillMaxWidth().padding(8.dp)) {
            OutlinedTextField(
                input,
                onInput,
                Modifier.fillMaxWidth().padding(end = 84.dp),
                placeholder = { Text("输入学习目标或问题") },
                minLines = if (compact) 1 else 3,
                maxLines = if (compact) 1 else 5,
                singleLine = compact
            )
            Button(
                onClick = onSend,
                enabled = !isLoading,
                modifier = Modifier.align(Alignment.BottomEnd),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                if (!compact) {
                    Spacer(Modifier.width(4.dp))
                    Text(if (isLoading) "生成中" else "发送")
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
                    .background(palette.secondary.copy(alpha = 0.13f).compositeOnWhite(), RoundedCornerShape(10.dp))
                    .border(1.dp, palette.secondary.copy(alpha = 0.24f), RoundedCornerShape(10.dp))
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
                    FilterChip(draftMode == "text", { onDraftMode("text") }, label = { Text("打字") })
                    Spacer(Modifier.width(6.dp))
                    FilterChip(draftMode == "draw", { onDraftMode("draw") }, label = { Text("涂绘") })
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
            .background(Color.White, RoundedCornerShape(8.dp))
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
private fun BranchScreen(branches: List<BranchClass>, onContinue: (BranchClass) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (branches.isEmpty()) item { InfoCard { Text("在主课堂任意消息下开分支。", color = Muted) } }
        items(branches) { branch ->
            InfoCard {
                Text(branch.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(branch.source, color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                MarkdownText(branch.memory)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onContinue(branch) }) { Text("继续分支") }
            }
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
                    FilterChip(overview, { overview = true }, label = { Text("全览") })
                    Spacer(Modifier.width(6.dp))
                    FilterChip(!overview, { overview = false }, label = { Text("章节") })
                }
                Text("双击章节可跳到主课堂对应对话。", color = Muted, fontSize = 13.sp)
            }
        }
        if (visibleChapters.isEmpty()) item { InfoCard { Text("开始对话后会自动生成章节索引。", color = Muted) } }
        if (overview) {
            items(visibleChapters) { chapter ->
                InfoCard {
                    Text(chapter.title, fontWeight = FontWeight.Bold)
                    Text(chapter.summary, color = Ink, lineHeight = 21.sp)
                }
            }
        } else {
            items(visibleChapters) { chapter ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = {}, onDoubleClick = { onJump(chapter.startIndex) }),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(Color.White),
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
    var apiProvider by remember(config.provider) { mutableStateOf(config.provider) }
    var apiBaseUrl by remember(config.baseUrl) { mutableStateOf(config.baseUrl) }
    var apiKey by remember(config.apiKey) { mutableStateOf(config.apiKey) }
    var modelName by remember(config.customModel, config.selectedModel) { mutableStateOf(config.customModel.ifBlank { config.selectedModel }) }
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            InfoCard {
                Text("API 模块", fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    providers.forEach { provider ->
                        FilterChip(apiProvider == provider, {
                            apiProvider = provider
                            apiBaseUrl = defaultBaseUrl(provider)
                        }, label = { Text(provider) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(apiBaseUrl, { apiBaseUrl = it }, Modifier.fillMaxWidth(), label = { Text("Base URL") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), label = { Text("API Key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onConfig(config.copy(provider = apiProvider, baseUrl = apiBaseUrl.trim(), apiKey = apiKey.trim())) }) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("保存 API 模块")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onFetchModels) { Text("获取模型") }
                Text(modelStatus, color = Muted)
            }
        }
        item {
            InfoCard {
                Text("模型模块", fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    models.forEach { model ->
                        FilterChip(modelName == model, { modelName = model }, label = { Text(model) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(modelName, { modelName = it }, Modifier.fillMaxWidth(), label = { Text("模型名称") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    val cleanName = modelName.trim()
                    onConfig(config.copy(selectedModel = cleanName.ifBlank { config.selectedModel }, customModel = if (cleanName in models) "" else cleanName))
                }) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("保存模型模块")
                }
            }
        }
        item {
            InfoCard {
                Text("皮肤与界面", fontWeight = FontWeight.Bold)
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
            InfoCard {
                Text("讲师人格与模式模块", fontWeight = FontWeight.Bold)
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
                Button(onClick = { onConfig(config.copy(mentorPrompt = mentorPrompt.trim(), efficientMode = efficientMode)) }) {
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
        colors = CardDefaults.cardColors(if (selected) primary.copy(alpha = 0.13f).compositeOnWhite() else Color.White),
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
private fun ThemePreview(primaryValue: Long, secondaryValue: Long) {
    val primary = colorFromLong(primaryValue)
    val secondary = colorFromLong(secondaryValue)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(primary.copy(alpha = 0.08f).compositeOnWhite()), elevation = CardDefaults.cardElevation(0.dp)) {
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
                Surface(color = primary, shape = RoundedCornerShape(8.dp)) { Text("主操作", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
                Surface(color = secondary.copy(alpha = 0.14f).compositeOnWhite(), shape = RoundedCornerShape(8.dp)) { Text("辅助强调", color = secondary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
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
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
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
    }))
    put("memories", JSONArray(memories))
    put("chapters", JSONArray(chapters.map { JSONObject().put("title", it.title).put("summary", it.summary).put("startIndex", it.startIndex).put("endIndex", it.endIndex) }))
    put("files", JSONArray(files.map { JSONObject().put("name", it.name).put("type", it.type).put("chars", it.chars).put("preview", it.preview) }))
    put("config", JSONObject().put("provider", config.provider).put("apiKey", config.apiKey).put("baseUrl", config.baseUrl).put("selectedModel", config.selectedModel).put("customModel", config.customModel).put("mentorPrompt", config.mentorPrompt).put("efficientMode", config.efficientMode).put("reverseConversation", config.reverseConversation).put("themeMode", config.themeMode).put("primaryColor", config.primaryColor).put("secondaryColor", config.secondaryColor))
}

private fun JSONArray?.toMessages(): List<ChatMessage> = if (this == null) emptyList() else List(length()) { getJSONObject(it).let { item -> ChatMessage(item.optString("role"), item.optString("text")) } }
private fun JSONArray?.toBranches(): List<BranchClass> = if (this == null) emptyList() else List(length()) { getJSONObject(it).let { item -> BranchClass(item.optString("title"), item.optString("source"), item.optJSONArray("messages").toMessages().toMutableStateList(), item.optString("memory")) } }
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
        "mono" -> AppPalette(Color(0xFFF7F7F8), Color.White, Color(0xFF171717), Color(0xFF686868), Color(0xFF111111), Color(0xFF555555), Color(0xFF2F2F2F))
        "single" -> AppPalette(primary.copy(alpha = 0.08f).compositeOnWhite(), Color.White, Ink, Muted, primary, primary.copy(alpha = 0.72f).compositeOnWhite(), primary.copy(alpha = 0.55f).compositeOnWhite())
        else -> AppPalette(Color(0xFFEFF9FB), Color.White, Color(0xFF0F2630), Color(0xFF5B6F78), primary, secondary, Color(0xFF0891B2))
    }
}

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

private const val APP_VERSION = "1.9.0"

private val THEME_PRESETS = listOf(
    ThemePreset("ocean", "二次元", "清透青绿、亮蓝点缀", 0xFF39C5BB, 0xFF00AEEF),
    ThemePreset("mono", "黑白", "克制灰阶界面", 0xFF111111, 0xFF666666),
    ThemePreset("single", "单主色", "用主色生成轻重层次", 0xFF39C5BB, 0xFF39C5BB),
    ThemePreset("system", "跟随系统", "保留系统浅色基调", 0xFF39C5BB, 0xFF00AEEF)
)

private const val RELEASE_NOTES_TEXT = """
# AI Classroom 1.8.0

- 顶部 AI Classroom 区域和底部导航不再自动弹出，改为右上角按键手动显示或隐藏。
- 右上角新增“显示导航 / 隐藏导航”胶囊按键，主课堂阅读时可以保持沉浸界面。
- “开头 / 底部”跳转按键整体上移，避免贴近输入栏。
- 底部输入栏继续保持：在历史对话中为单行可输入，在底部时为更完整的多行输入。
- 优化主课堂悬浮按钮间距和层级，让导航、跳转、输入三者互不抢位置。

# 延续优化

- 数学公式展示会修正常见损坏的 LaTeX 命令文本。
- 长按对话可选择开分支、重写该段或删除此处及之后内容。
"""
private const val USER_MANUAL_TEXT = """
# AI Classroom 使用手册

## 主课堂
在主课堂输入学习目标或问题，AI 会围绕当前课堂持续教学。课堂内容、对话、章节索引和摘要会保存在本地。

## 分支课堂
在任意对话下选择开分支，可以从那一段内容继续追问。分支不会打断主课堂，重要内容会回写到记忆摘要。

## 记忆
记忆页用于查看对话章节索引。全览模式会显示每段一句话摘要，章节模式可双击跳回主课堂对应位置。

## 知识库
知识库目前可直接读取 `.md` 和 `.txt` 文件。文件摘要会加入课堂上下文，帮助 AI 结合你的材料教学。

## API 与模型
在设置页保存 API 模块，可填写服务商、Base URL 和 API Key。模型模块支持自动获取模型，也可以手动输入模型名称。

## 讲师人格
讲师人格提示词可以自定义，例如教授、工程师、考研老师或更轻松的风格。保存后当前课堂会持续使用该配置。

## 高效模式
高效模式会过滤 NSFW 等不适合学习场景的内容，默认开启。

## 界面与皮肤
设置页可切换对话方向和皮肤。默认是二次元青蓝色调，也可以选择黑白、跟随系统或单色。只有二次元和单色皮肤支持自定义颜色。

## 多课堂
主界面滑出二级菜单后可以切换课堂、新建课堂、删除课堂，也可以复制其他课堂配置。

## 考试工具
考试不是普通入口。AI 明确要进行考试时，应用会自动进入沉浸式考试界面。题目由前提和问题组成，用户在每个问题后填写答案并提交批改。标记“不会”会自动生成讲解分支。
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


