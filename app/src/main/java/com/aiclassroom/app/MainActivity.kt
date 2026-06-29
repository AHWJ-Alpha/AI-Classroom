package com.aiclassroom.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Send
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

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
    Model("模型", Icons.Default.Key)
}

private data class ChatMessage(val role: String, val text: String)
private data class BranchClass(val title: String, val source: String, val messages: List<ChatMessage>, val memory: String)
private data class KnowledgeFile(val name: String, val type: String, val chars: Int, val preview: String)
private data class Classroom(
    val name: String,
    val topic: String,
    val messages: MutableList<ChatMessage>,
    val branches: MutableList<BranchClass>,
    val memories: MutableList<String>,
    val files: MutableList<KnowledgeFile>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AIClassroomApp() {
    var tab by remember { mutableStateOf(Tab.Class) }
    var classIndex by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("OpenAI") }
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://api.openai.com/v1") }
    var selectedModel by remember { mutableStateOf("gpt-4o-mini") }
    var customModel by remember { mutableStateOf("") }
    var modelStatus by remember { mutableStateOf("未获取模型") }
    var mentorPrompt by remember { mutableStateOf("你是一名耐心、结构清晰的 AI 讲师。使用 Markdown 和数学公式时保持清晰。") }
    var efficientMode by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    val classes = remember { mutableStateListOf(newClassroom(1)) }
    val models = remember { mutableStateListOf("gpt-4o-mini", "gpt-4o", "deepseek-chat", "qwen-plus") }
    val scope = rememberCoroutineScope()
    val current = classes[classIndex]
    val activeModel = customModel.ifBlank { selectedModel }

    fun addClassroom() {
        classes.add(newClassroom(classes.size + 1))
        classIndex = classes.lastIndex
        tab = Tab.Class
    }

    fun switchClassroom(delta: Int) {
        classIndex = (classIndex + delta).coerceIn(0, classes.lastIndex)
        tab = Tab.Class
    }

    fun systemPrompt(room: Classroom): String {
        val knowledge = room.files.joinToString("\n") { "[${it.name}] ${it.preview}" }.take(3000)
        val memory = room.memories.takeLast(8).joinToString("\n")
        val safety = if (efficientMode) "高效模式：过滤 NSFW、色情、血腥、违法、仇恨和自伤内容。" else ""
        return "$mentorPrompt\n课堂：${room.name}\n学习内容：${room.topic}\n记忆：$memory\n知识库：$knowledge\n$safety"
    }

    fun sendMessage(seed: String? = null) {
        val text = (seed ?: input).trim()
        if (text.isBlank() || isLoading) return
        val room = current
        input = ""
        room.messages.add(ChatMessage("user", filterNsfw(text, efficientMode)))
        room.memories.add(summarize("主课堂", room.messages.takeLast(6)))
        isLoading = true
        scope.launch {
            val result = callChat(baseUrl, apiKey, activeModel, systemPrompt(room), room.messages.toList())
            room.messages.add(ChatMessage("assistant", filterNsfw(result, efficientMode)))
            room.memories.add(summarize("主课堂", room.messages.takeLast(8)))
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Classroom 1.3", fontWeight = FontWeight.Bold)
                        Text("${current.name} · ${current.topic}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = { TextButton(onClick = ::addClassroom) { Icon(Icons.Default.Add, contentDescription = "新建课堂") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Page)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Icon(item.icon, contentDescription = item.title) }, label = { Text(item.title) })
                }
            }
        }
    ) { padding ->
        Surface(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 6.dp)
                .pointerInput(classes.size, classIndex) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (total < -80f) switchClassroom(1)
                            if (total > 80f) switchClassroom(-1)
                            total = 0f
                        },
                        onHorizontalDrag = { _, dragAmount -> total += dragAmount }
                    )
                },
            color = Page
        ) {
            when (tab) {
                Tab.Class -> ClassScreen(current, classIndex, classes.size, input, { input = it }, isLoading, ::addClassroom, { sendMessage() }) { index ->
                    val selected = current.messages.drop(index)
                    val title = selected.firstOrNull()?.text?.take(18)?.ifBlank { "分支课堂" } ?: "分支课堂"
                    current.branches.add(BranchClass(title, "${current.name} 第 ${index + 1} 条起", selected, summarize("分支", selected)))
                    current.memories.add(summarize("分支回写", selected))
                    tab = Tab.Branch
                }
                Tab.Branch -> BranchScreen(current.branches) { branch ->
                    tab = Tab.Class
                    sendMessage("继续这个分支：${branch.source}\n${branch.messages.joinToString("\n") { it.role + ":" + it.text }}")
                }
                Tab.Memory -> MemoryScreen(current.memories)
                Tab.Knowledge -> KnowledgeScreen(current.files)
                Tab.Model -> ModelScreen(
                    provider = provider,
                    onProvider = {
                        provider = it
                        baseUrl = defaultBaseUrl(it)
                    },
                    baseUrl = baseUrl,
                    onBaseUrl = { baseUrl = it },
                    apiKey = apiKey,
                    onApiKey = { apiKey = it },
                    models = models,
                    selectedModel = selectedModel,
                    onModel = { selectedModel = it },
                    customModel = customModel,
                    onCustomModel = { customModel = it },
                    modelStatus = modelStatus,
                    mentorPrompt = mentorPrompt,
                    onMentorPrompt = { mentorPrompt = it },
                    efficientMode = efficientMode,
                    onEfficientMode = { efficientMode = it },
                    onFetchModels = {
                        scope.launch {
                            modelStatus = "获取中..."
                            val fetched = fetchModels(baseUrl, apiKey)
                            if (fetched.isNotEmpty()) {
                                models.clear()
                                models.addAll(fetched)
                                selectedModel = fetched.first()
                                modelStatus = "已获取 ${fetched.size} 个模型"
                            } else {
                                modelStatus = "获取失败，可手动填写模型名"
                            }
                        }
                    }
                )
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
    onNewClass: () -> Unit,
    onSend: () -> Unit,
    onBranch: (Int) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("课堂 ${index + 1}/$count", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = onNewClass) { Text("新建") }
                }
                Text(room.topic, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        items(room.messages.size) { i -> MessageCard(i, room.messages[i], onBranch) }
        item {
            InfoCard {
                OutlinedTextField(input, onInput, Modifier.fillMaxWidth(), placeholder = { Text("输入问题或学习目标") }, minLines = 2)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onSend, enabled = !isLoading) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isLoading) "生成中" else "发送")
                }
            }
        }
    }
}

@Composable
private fun MessageCard(index: Int, message: ChatMessage, onBranch: (Int) -> Unit) {
    InfoCard {
        Text(if (message.role == "user") "我" else "AI 讲师", color = if (message.role == "user") Blue else Green, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        MarkdownText(message.text)
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { onBranch(index) }) { Text("从这里开分支") }
    }
}

@Composable
private fun BranchScreen(branches: List<BranchClass>, onContinue: (BranchClass) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
private fun MemoryScreen(memories: List<String>) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(memories.reversed()) { InfoCard { MarkdownText(it) } }
    }
}

@Composable
private fun KnowledgeScreen(files: MutableList<KnowledgeFile>) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "knowledge"
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext == "md" || ext == "txt") {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            files.add(KnowledgeFile(name, ext, text.length, text.take(1000)))
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { InfoCard { Text("可读取：.md、.txt", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Button(onClick = { launcher.launch("text/*") }) { Text("读取文件") } } }
        items(files) { file -> InfoCard { Text(file.name, fontWeight = FontWeight.Bold); Text("${file.type} · ${file.chars} 字", color = Muted, fontSize = 13.sp); Spacer(Modifier.height(6.dp)); MarkdownText(file.preview) } }
    }
}

@Composable
private fun ModelScreen(
    provider: String,
    onProvider: (String) -> Unit,
    baseUrl: String,
    onBaseUrl: (String) -> Unit,
    apiKey: String,
    onApiKey: (String) -> Unit,
    models: List<String>,
    selectedModel: String,
    onModel: (String) -> Unit,
    customModel: String,
    onCustomModel: (String) -> Unit,
    modelStatus: String,
    mentorPrompt: String,
    onMentorPrompt: (String) -> Unit,
    efficientMode: Boolean,
    onEfficientMode: (Boolean) -> Unit,
    onFetchModels: () -> Unit
) {
    val providers = listOf("OpenAI", "DeepSeek", "通义千问", "自定义")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            InfoCard {
                Text("API", fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    providers.forEach { FilterChip(provider == it, { onProvider(it) }, label = { Text(it) }) }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(baseUrl, onBaseUrl, Modifier.fillMaxWidth(), label = { Text("Base URL") }, singleLine = true)
                OutlinedTextField(apiKey, onApiKey, Modifier.fillMaxWidth(), label = { Text("API Key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onFetchModels) { Text("获取模型") }
                Text(modelStatus, color = Muted)
            }
        }
        item {
            InfoCard {
                Text("模型", fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    models.forEach { FilterChip(selectedModel == it && customModel.isBlank(), { onCustomModel(""); onModel(it) }, label = { Text(it) }) }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(customModel, onCustomModel, Modifier.fillMaxWidth(), label = { Text("自定义模型名") }, singleLine = true)
            }
        }
        item { InfoCard { Text("讲师人格", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); OutlinedTextField(mentorPrompt, onMentorPrompt, Modifier.fillMaxWidth(), minLines = 4) } }
        item { InfoCard { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.HealthAndSafety, null, tint = Green); Spacer(Modifier.width(8.dp)); Text("高效模式", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Switch(efficientMode, onEfficientMode) }; Text("过滤 NSFW 内容。", color = Muted) } }
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

private fun newClassroom(number: Int) = Classroom(
    name = "课堂 $number",
    topic = "自定义学习内容",
    messages = mutableStateListOf(ChatMessage("assistant", "输入学习目标，我会开始主课堂教学。支持 Markdown 与公式文本，如 `f(x)=x^2` 或 ${'$'}E=mc^2${'$'}。")),
    branches = mutableStateListOf(),
    memories = mutableStateListOf("等待开始。"),
    files = mutableStateListOf()
)

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

private val Page = Color(0xFFF6F8FB)
private val Ink = Color(0xFF101828)
private val Muted = Color(0xFF667085)
private val Blue = Color(0xFF1F6FEB)
private val Green = Color(0xFF2FB344)
private val Purple = Color(0xFF7A5AF8)
