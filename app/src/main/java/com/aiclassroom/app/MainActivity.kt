package com.aiclassroom.app

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
    val themeMode: String = "青蓝默认",
    val primaryColor: Long = 0xFF10A7B5,
    val secondaryColor: Long = 0xFF2563EB
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
    var jumpToMessageIndex by remember { mutableStateOf<Int?>(null) }
    var examSession by remember { mutableStateOf<ExamSession?>(null) }
    var saveNotice by remember { mutableStateOf("所有内容自动保存在本机") }
    var modelStatus by remember { mutableStateOf("未获取模型") }
    var isLoading by remember { mutableStateOf(false) }
    val classes = remember { mutableStateListOf<Classroom>().apply { addAll(initialClasses) } }
    val models = remember { mutableStateListOf("gpt-4o-mini", "gpt-4o", "deepseek-chat", "qwen-plus") }
    val scope = rememberCoroutineScope()
    if (classIndex > classes.lastIndex) classIndex = classes.lastIndex.coerceAtLeast(0)
    val current = classes[classIndex]
    val activeModel = current.config.customModel.ifBlank { current.config.selectedModel }
    val palette = paletteFor(current.config)

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

    fun sendMessage(seed: String? = null) {
        val text = (seed ?: input).trim()
        if (text.isBlank() || isLoading) return
        val room = current
        input = ""
        room.messages.add(ChatMessage("user", filterNsfw(text, room.config.efficientMode)))
        room.memories.add(summarize("主课堂", room.messages.takeLast(6)))
        persist("对话已保存")
        isLoading = true
        scope.launch {
            val result = callChat(room.config.baseUrl, room.config.apiKey, activeModel, systemPrompt(room) + "\n" + EXAM_TOOL_PROMPT, room.messages.toList())
            room.messages.add(ChatMessage("assistant", filterNsfw(result, room.config.efficientMode)))
            detectExamSession(result)?.let { examSession = it }
            room.memories.add(summarize("主课堂", room.messages.takeLast(8)))
            room.chapters.clear()
            room.chapters.addAll(buildConversationChapters(room.messages, room.config, activeModel))
            isLoading = false
            persist("回复和记忆已保存")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Classroom 1.6", fontWeight = FontWeight.Bold)
                        Text("${current.name} · ${current.topic}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = { TextButton(onClick = { classMenuOpen = !classMenuOpen }) { Text("课堂") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.page)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = palette.surface) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(selected = tab == item, onClick = { tab = item; classMenuOpen = false }, icon = { Icon(item.icon, contentDescription = item.title) }, label = { Text(item.title) })
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
                            if (total < -80f || total > 80f) classMenuOpen = !classMenuOpen
                            total = 0f
                        },
                        onHorizontalDrag = { _, dragAmount -> total += dragAmount }
                    )
                },
            color = palette.page
        ) {
            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    Tab.Class -> ClassScreen(current, classIndex, classes.size, input, { input = it }, isLoading, jumpToMessageIndex, { jumpToMessageIndex = null }, current.config.reverseConversation, { classMenuOpen = true }, { sendMessage() }) { index ->
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
                AnimatedVisibility(
                    visible = classMenuOpen,
                    enter = slideInHorizontally(animationSpec = tween(220)) { -it },
                    exit = slideOutHorizontally(animationSpec = tween(180)) { -it }
                ) {
                    ClassroomMenu(classes, classIndex, saveNotice, onSelect = {
                        classIndex = it
                        classMenuOpen = false
                        persist("已切换课堂")
                    }, onNew = { addClassroom() }, onNewWithConfig = { addClassroom(classes[it]) }, onCopyConfig = ::copyConfigFrom, onDelete = ::deleteClassroom)
                }
                examSession?.let { session ->
                    ExamOverlay(session, current.messages, current.branches, onUpdate = { examSession = it }, onClose = { examSession = null }, onSubmit = { packed ->
                        examSession = null
                        sendMessage(packed)
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
            }
        }
    }
}

@Composable
private fun ClassroomMenu(
    classes: List<Classroom>,
    classIndex: Int,
    saveNotice: String,
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
                    Text(saveNotice, color = Green, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("新建课堂")
                    }
                }
                items(classes.indices.toList()) { i ->
                    val room = classes[i]
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(if (i == classIndex) Color(0xFFEAF2FF) else Color(0xFFF7F8FA))) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(room.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(room.config.customModel.ifBlank { room.config.selectedModel }, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (i == classIndex) Icon(Icons.Default.Check, null, tint = Green, modifier = Modifier.size(18.dp))
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
        Box(Modifier.fillMaxSize().background(Color(0x33000000)))
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
    jumpToMessageIndex: Int?,
    onJumpHandled: () -> Unit,
    reverseConversation: Boolean,
    onOpenMenu: () -> Unit,
    onSend: () -> Unit,
    onBranch: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val lastItemIndex = room.messages.size + 1
    val bottomIndex = if (reverseConversation) 0 else lastItemIndex
    val topIndex = if (reverseConversation) lastItemIndex else 0
    val isAtBottom by remember { derivedStateOf { listState.firstVisibleItemIndex == bottomIndex } }
    LaunchedEffect(room.messages.size) {
        if (room.messages.isNotEmpty()) listState.animateScrollToItem(bottomIndex)
    }
    LaunchedEffect(jumpToMessageIndex) {
        val target = jumpToMessageIndex ?: return@LaunchedEffect
        listState.animateScrollToItem((target + 1).coerceIn(0, lastItemIndex))
        onJumpHandled()
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, reverseLayout = reverseConversation, contentPadding = PaddingValues(vertical = 10.dp, horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("课堂 ${index + 1}/$count", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = onOpenMenu) { Text("切换") }
                    }
                    Text(room.topic, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            items(room.messages.size) { i -> MessageCard(i, room.messages[i], onBranch) }
            if (isLoading) item { AiThinkingRow() }
            item {
                InfoCard {
                    OutlinedTextField(input, onInput, Modifier.fillMaxWidth(), placeholder = { Text("输入学习目标或问题") }, minLines = 2)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onSend, enabled = !isLoading) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isLoading) "生成中" else "发送")
                    }
                }
            }
        }
        Button(
            onClick = { scope.launch { if (isAtBottom) listState.animateScrollToItem(topIndex) else listState.animateScrollToItem(bottomIndex) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 108.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(if (isAtBottom) Icons.Default.North else Icons.Default.South, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (isAtBottom) "开头" else "底部")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageCard(index: Int, message: ChatMessage, onBranch: (Int) -> Unit) {
    if (message.role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(
                Modifier
                    .fillMaxWidth(0.82f)
                    .background(Blue, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text("我", color = Color.White.copy(alpha = 0.82f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(message.text, color = Color.White, lineHeight = 21.sp)
                TextButton(onClick = { onBranch(index) }) { Text("从这里开分支", color = Color.White) }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text("AI 讲师", color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            MarkdownText(message.text)
            TextButton(onClick = { onBranch(index) }) { Text("从这里开分支") }
        }
    }
}

@Composable
private fun AiThinkingRow() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text("AI 讲师", color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Text("正在回复...", color = Muted)
    }
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
    var side by remember { mutableStateOf("草稿纸") }
    Surface(Modifier.fillMaxSize(), color = Page) {
        Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LazyColumn(Modifier.weight(1.15f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Quiz, null, tint = Blue)
                        Spacer(Modifier.width(8.dp))
                        Text(session.title, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = onClose) { Text("退出") }
                    }
                }
                items(session.questions.indices.toList()) { i ->
                    val q = session.questions[i]
                    InfoCard {
                        Text("前提", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(q.premise.ifBlank { "无额外前提" }, lineHeight = 21.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("问题 ${i + 1}", color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                item {
                    Button(onClick = { onSubmit(packExamAnswers(session)) }, modifier = Modifier.fillMaxWidth()) { Text("提交答案") }
                }
            }
            Column(Modifier.weight(0.85f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(side == "草稿纸", { side = "草稿纸" }, label = { Text("草稿纸") })
                    FilterChip(side == "过往对话", { side = "过往对话" }, label = { Text("过往对话") })
                    FilterChip(side == "分支", { side = "分支" }, label = { Text("分支") })
                }
                InfoCard {
                    when (side) {
                        "草稿纸" -> OutlinedTextField(session.draft, { onUpdate(session.copy(draft = it)) }, Modifier.fillMaxWidth(), minLines = 16, placeholder = { Text("草稿纸") })
                        "过往对话" -> LazyColumn(Modifier.height(420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(messages.takeLast(20)) { MarkdownText((if (it.role == "user") "我：" else "AI：") + it.text.take(500)) } }
                        else -> LazyColumn(Modifier.height(420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(branches.takeLast(8)) { Text(it.title, fontWeight = FontWeight.Bold); MarkdownText(it.messages.lastOrNull()?.text.orEmpty()) } }
                    }
                }
            }
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
                Text("界面与皮肤模块", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ColorLens, null, tint = Blue)
                    Spacer(Modifier.width(8.dp))
                    Text("对话从下到上", modifier = Modifier.weight(1f))
                    Switch(reverseConversation, { reverseConversation = it })
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("青蓝默认", "黑白", "跟随系统", "单主色", "双主色").forEach { mode ->
                        FilterChip(themeMode == mode, { themeMode = mode }, label = { Text(mode) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0xFF10A7B5, 0xFF2563EB, 0xFF2FB344, 0xFF7A5AF8).forEach { color ->
                        Button(onClick = { primaryColor = color }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp)) { Box(Modifier.fillMaxSize().background(Color(color))) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0xFF2563EB, 0xFF10A7B5, 0xFF101828, 0xFF667085).forEach { color ->
                        OutlinedButton(onClick = { secondaryColor = color }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp)) { Box(Modifier.fillMaxSize().background(Color(color))) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onConfig(config.copy(reverseConversation = reverseConversation, themeMode = themeMode, primaryColor = primaryColor, secondaryColor = secondaryColor)) }) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("保存界面与皮肤模块")
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
                    Icon(Icons.Default.HealthAndSafety, null, tint = Green)
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
        item { Text(saveNotice, color = Green, modifier = Modifier.padding(4.dp)) }
    }
}

@Composable
private fun MarkdownText(text: String) {
    val lines = text.lines()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("```") -> Text(trimmed, fontFamily = FontFamily.Monospace, color = Muted)
                trimmed.startsWith("#") -> Text(trimmed.trimStart('#', ' '), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
                trimmed.startsWith("-") || trimmed.startsWith("*") -> Text("• ${trimmed.drop(1).trim()}", color = Ink, lineHeight = 21.sp)
                trimmed.contains("$") || trimmed.contains("\\(") || trimmed.contains("\\[") -> Text(trimmed, fontFamily = FontFamily.Monospace, color = Purple, lineHeight = 21.sp)
                else -> Text(buildInlineMarkdown(trimmed), color = Ink, lineHeight = 21.sp)
            }
        }
    }
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
            themeMode = configJson.optString("themeMode", "青蓝默认"),
            primaryColor = configJson.optLong("primaryColor", 0xFF10A7B5),
            secondaryColor = configJson.optLong("secondaryColor", 0xFF2563EB)
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

private const val EXAM_TOOL_PROMPT = """
当你明确要发起考试、随堂测试或模拟测验时，必须在回复中使用以下格式，应用会自动进入沉浸式考试界面：
【考试】
前提：题目背景、材料、阅读短文或说明
问题：具体问题一
问题：具体问题二
可以重复多组“前提/问题”。不要把考试作为普通聊天问题发出。
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

private fun detectExamSession(text: String): ExamSession? {
    detectStructuredExamSession(text)?.let { return it }
    val trigger = listOf("开始考试", "进入考试", "模拟考试", "随堂测试", "考试模式", "【考试】")
    if (trigger.none { text.contains(it) }) return null
    val blocks = text.split(Regex("(?=题目\\s*\\d+|问题\\s*\\d+|Q\\d+)")).filter { it.isNotBlank() }
    val questions = blocks.mapNotNull { block ->
        val premise = Regex("前提[:：]([\\s\\S]*?)(问题[:：]|$)").find(block)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val question = Regex("问题[:：]([\\s\\S]*)").find(block)?.groupValues?.getOrNull(1)?.trim()
            ?: block.lines().firstOrNull { it.contains("？") || it.contains("?") }?.trim()
            ?: block.trim().take(160)
        if (question.isBlank()) null else ExamQuestion(premise, question)
    }.ifEmpty {
        listOf(ExamQuestion("请根据刚才课堂内容作答。", text.lines().firstOrNull { it.contains("？") || it.contains("?") } ?: "请完成本次测试。"))
    }.toMutableStateList()
    return ExamSession("AI 随堂考试", questions)
}

private fun detectStructuredExamSession(text: String): ExamSession? {
    val trigger = listOf("开始考试", "进入考试", "模拟考试", "随堂测试", "考试模式", "【考试】")
    if (trigger.none { text.contains(it) }) return null
    val normalized = text.replace("：", ":")
    val questions = Regex("前提:([\\s\\S]*?)(?=前提:|$)").findAll(normalized).flatMap { match ->
        val block = match.groupValues[1]
        val parts = block.split(Regex("(?=问题:)"))
        val premise = parts.firstOrNull().orEmpty().replace("【考试】", "").trim()
        parts.drop(1).mapNotNull { part ->
            val question = part.removePrefix("问题:").trim()
            if (question.isBlank()) null else ExamQuestion(premise, question)
        }
    }.toList().ifEmpty {
        val firstQuestion = normalized.lines().firstOrNull { it.contains("?") || it.contains("？") } ?: normalized.take(160)
        listOf(ExamQuestion("请根据课堂内容作答。", firstQuestion))
    }.toMutableStateList()
    return ExamSession("AI 随堂考试", questions)
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

private fun buildJson(model: String, system: String, messages: List<ChatMessage>): String {
    val all = listOf(ChatMessage("system", system)) + messages
    val jsonMessages = all.joinToString(",") { "{\"role\":\"${it.role}\",\"content\":\"${it.text.escapeJson()}\"}" }
    return "{\"model\":\"${model.escapeJson()}\",\"messages\":[$jsonMessages],\"temperature\":0.7}"
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
        "榛戠櫧", "黑白" -> AppPalette(Color(0xFFF6F6F6), Color.White, Color(0xFF171717), Color(0xFF666666), Color(0xFF111111), Color(0xFF555555), Color(0xFF2F2F2F))
        "鍗曚富鑹?", "单主色" -> AppPalette(primary.copy(alpha = 0.08f).compositeOnWhite(), Color.White, Ink, Muted, primary, primary.copy(alpha = 0.72f).compositeOnWhite(), primary.copy(alpha = 0.55f).compositeOnWhite())
        "鍙屼富鑹?", "双主色" -> AppPalette(primary.copy(alpha = 0.08f).compositeOnWhite(), Color.White, Ink, Muted, primary, secondary, secondary.copy(alpha = 0.72f).compositeOnWhite())
        else -> AppPalette(Page, Color.White, Ink, Muted, Green, Blue, Purple)
    }
}

private fun colorFromLong(value: Long): Color = Color(value.toULong())
private fun Color.compositeOnWhite(): Color = Color(
    red = red * alpha + (1f - alpha),
    green = green * alpha + (1f - alpha),
    blue = blue * alpha + (1f - alpha),
    alpha = 1f
)

private val Page = Color(0xFFF3F8FA)
private val Ink = Color(0xFF102027)
private val Muted = Color(0xFF60727A)
private val Blue = Color(0xFF2563EB)
private val Green = Color(0xFF10A7B5)
private val Purple = Color(0xFF0E7490)
private const val MEMORY_PROMPT_LIMIT = 24
private const val BRANCH_CONTEXT_LIMIT = 24
private const val CHAPTER_SIZE = 12
