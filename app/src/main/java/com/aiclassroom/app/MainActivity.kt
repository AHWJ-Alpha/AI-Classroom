package com.aiclassroom.app

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import com.aiclassroom.app.media.MediaAccess
import com.aiclassroom.app.media.MusicLauncher
import com.aiclassroom.app.media.NowPlaying
import com.aiclassroom.app.web.WebCaptureEngine
import com.aiclassroom.app.web.WebConsentBroker
import com.aiclassroom.app.web.WebConsentDialog
import com.aiclassroom.app.web.WebDomainStore
import com.aiclassroom.app.web.WebDomains
import com.aiclassroom.app.web.WebToolDeps
import com.aiclassroom.app.web.WebSelfTest
import com.aiclassroom.app.web.registerWebTools
import com.aiclassroom.app.video.VideoFrameExtractor
import com.aiclassroom.app.video.VideoSelfTest
import com.aiclassroom.app.video.VideoToolDeps
import com.aiclassroom.app.video.registerVideoTools
import android.content.res.Configuration
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.FileProvider
import com.aiclassroom.app.exam.HandwritingPadDialog
import com.aiclassroom.app.exam.deleteExamImage
import com.aiclassroom.app.exam.examImageExists
import com.aiclassroom.app.exam.importExamImage
import com.aiclassroom.app.exam.newExamPhotoFile
import com.aiclassroom.app.exam.normalizeExamImage
import android.view.HapticFeedbackConstants
import android.media.MediaPlayer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.South
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiclassroom.app.ui.components.AppDialogBase
import com.aiclassroom.app.ui.components.AppPrimaryButton
import com.aiclassroom.app.ui.components.AppSecondaryButton
import com.aiclassroom.app.ui.components.LocalAppHazeState
import com.aiclassroom.app.ui.theme.DesignTokens
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import com.aiclassroom.feature.exam.EXAM_TOOL_PROMPT
import com.aiclassroom.feature.exam.EXAM_TOOL_PROMPT_V2
import com.aiclassroom.feature.exam.ExamQuestion
import com.aiclassroom.app.model.ChatMessage
import com.aiclassroom.app.model.BranchClass
import com.aiclassroom.app.model.KnowledgeFile
import com.aiclassroom.app.model.ConversationChapter
import com.aiclassroom.app.model.MemoryCard
import com.aiclassroom.app.model.LearningMemoryPack
import com.aiclassroom.app.model.UpdateInfo
import com.aiclassroom.app.model.DrawStroke
import com.aiclassroom.app.model.AppPalette
import com.aiclassroom.app.model.ClassroomConfig
import com.aiclassroom.app.model.ExamRecord
import com.aiclassroom.app.model.Classroom
import com.aiclassroom.app.model.EXAM_RECORD_LIMIT
import com.aiclassroom.app.model.DEFAULT_MENTOR_PROMPT
import com.aiclassroom.app.model.MEMORY_PROMPT_LIMIT
import com.aiclassroom.app.model.MEMORY_CONTEXT_LIMIT
import com.aiclassroom.app.util.CORE_PREMISE_LIMIT
import com.aiclassroom.app.util.MEMORY_CARD_RECALL_LIMIT
import com.aiclassroom.app.util.MEMORY_CARD_BUILD_MESSAGE_LIMIT
import com.aiclassroom.app.util.RECENT_MESSAGE_CONTEXT_LIMIT
import com.aiclassroom.app.util.toStrings
import com.aiclassroom.app.util.normalizeVisibility
import com.aiclassroom.feature.exam.ExamSession
import com.aiclassroom.feature.exam.detectExamSession
import com.aiclassroom.feature.exam.isExamRequest
import com.aiclassroom.feature.exam.packExamAnswers
import com.aiclassroom.feature.exam.stripExamBlock
import com.aiclassroom.feature.exam.tool.ToolHandler
import com.aiclassroom.feature.exam.tool.ToolRegistry
import com.aiclassroom.feature.exam.tool.ToolResult
import com.aiclassroom.feature.exam.tool.config.registerConfigTools
import com.aiclassroom.feature.exam.tool.exam.registerExamTools
import com.aiclassroom.feature.exam.tool.custom.CustomToolSpec
import com.aiclassroom.feature.exam.tool.custom.previewCustomTool
import com.aiclassroom.feature.exam.tool.custom.registerCustomTools
import com.aiclassroom.feature.exam.tool.custom.validateCustomTool
import com.aiclassroom.feature.exam.tool.custom.validateCustomToolExecution
import com.aiclassroom.feature.exam.tool.image.ImageConfig
import com.aiclassroom.feature.exam.tool.image.ImageSink
import com.aiclassroom.feature.exam.tool.image.ImageTools
import com.aiclassroom.feature.exam.tool.image.registerImageTools
import com.aiclassroom.feature.exam.tool.universal.UniversalAsync
import com.aiclassroom.feature.exam.tool.universal.UniversalAuth
import com.aiclassroom.feature.exam.tool.universal.UniversalProfile
import com.aiclassroom.feature.exam.tool.universal.UniversalRequest
import com.aiclassroom.feature.exam.tool.universal.UniversalResponse
import com.aiclassroom.feature.exam.tool.universal.registerUniversalTools
import com.aiclassroom.feature.fs.FileTools
import com.aiclassroom.feature.net.NetRequestTool
import com.aiclassroom.feature.net.registerNetTools
import com.aiclassroom.app.plugin.BuiltinPlugins
import com.aiclassroom.app.plugin.AppPluginHost
import com.aiclassroom.app.plugin.LoadedPluginInfo
import com.aiclassroom.app.plugin.MediaArtworkCache
import com.aiclassroom.app.plugin.PLUGIN_LOG_TAG
import com.aiclassroom.app.plugin.PluginImportResult
import com.aiclassroom.app.plugin.PluginInstallTool
import com.aiclassroom.app.plugin.PluginManager
import com.aiclassroom.app.plugin.PluginUiBinding
import com.aiclassroom.app.plugin.ui.PluginUiEvent
import com.aiclassroom.app.plugin.ui.PluginUiFullscreen
import com.aiclassroom.app.plugin.ui.PluginUiMessageTail
import com.aiclassroom.app.plugin.ui.PluginUiOverlay
import com.aiclassroom.app.plugin.ui.PluginUiTopBarActions
import com.aiclassroom.app.plugin.PrefsPluginStateStore
import com.aiclassroom.app.plugin.importPluginArchive
import com.aiclassroom.app.plugin.cappedJson
import com.aiclassroom.feature.plugin.PluginClock
import com.aiclassroom.feature.plugin.PluginEventNames
import com.aiclassroom.feature.plugin.PluginLimits
import com.aiclassroom.feature.plugin.PluginResult
import com.aiclassroom.feature.plugin.PluginSetting
import com.aiclassroom.feature.plugin.PluginSettingType
import com.aiclassroom.feature.plugin.PluginSettingsCodec
import com.aiclassroom.feature.plugin.event.PluginEvent
import com.aiclassroom.feature.plugin.event.PluginEventBus
import com.aiclassroom.feature.plugin.hook.PromptHookPipeline
import com.aiclassroom.feature.plugin.hook.PromptMeta
import com.aiclassroom.feature.plugin.ui.FullscreenArbiter
import com.aiclassroom.feature.plugin.ui.PluginUiDriver
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
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
import java.util.Locale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import com.aiclassroom.app.util.isDarkInterface
import com.aiclassroom.app.util.appColorScheme
import com.aiclassroom.app.util.paletteFor
import com.aiclassroom.app.util.colorFromLong
import com.aiclassroom.app.util.argbToHex
import com.aiclassroom.app.util.parseHexColor
import com.aiclassroom.app.util.normalizeThemeMode
import com.aiclassroom.app.util.normalizeInterfaceMode
import com.aiclassroom.app.util.legacyInterfaceMode
import com.aiclassroom.app.util.luminanceValue
import com.aiclassroom.app.util.compositeOn
import com.aiclassroom.app.util.summarize
import com.aiclassroom.app.util.stripMarkdownForSpeech
import com.aiclassroom.app.util.buildInlineMarkdown
import com.aiclassroom.app.util.sanitizeMathText
import com.aiclassroom.app.util.isMathLikeLine
import com.aiclassroom.app.util.looksLikeFailure
import com.aiclassroom.app.util.mentorPriorityBlock
import com.aiclassroom.app.util.promptPriorityReminder
import com.aiclassroom.app.util.conversationConsistencyRules
import com.aiclassroom.app.util.responseBoundaryRules
import com.aiclassroom.app.util.promptMessagesForRoom
import com.aiclassroom.app.util.promptMessagesForBranch
import com.aiclassroom.app.util.truncatePromptSection
import com.aiclassroom.app.util.parseLearningMemoryPack
import com.aiclassroom.app.util.localLearningMemoryPack
import com.aiclassroom.app.util.mergeMemoryCards
import com.aiclassroom.app.util.normalizeCharacterCardJson
import com.aiclassroom.app.util.extractPngCardText
import com.aiclassroom.app.util.formatCharacterCards
import com.aiclassroom.app.util.MEDIA_DISABLED_HINT
import com.aiclassroom.app.util.MEDIA_NO_SESSION_HINT
import com.aiclassroom.app.util.MEDIA_PERMISSION_HINT
import com.aiclassroom.app.util.TOOL_LOOP_MAX_ROUNDS
import com.aiclassroom.app.util.TOOL_LOG_TAG
import com.aiclassroom.app.util.NO_CONTENT_REPLY
import com.aiclassroom.app.util.RELEASE_NOTES_TEXT
import com.aiclassroom.app.util.USER_MANUAL_TEXT
import com.aiclassroom.app.util.CORE_PREMISE_CONTEXT_LIMIT
import com.aiclassroom.app.util.WORLD_BOOK_CONTEXT_LIMIT
import com.aiclassroom.app.util.MEMORY_CARD_STORE_LIMIT
import com.aiclassroom.app.util.CHAPTER_PROMPT_LIMIT
import com.aiclassroom.app.util.CHAPTER_CONTEXT_LIMIT
import com.aiclassroom.app.util.BRANCH_CONTEXT_LIMIT
import com.aiclassroom.app.util.BRANCH_CONTEXT_LIMIT_CHARS
import com.aiclassroom.app.util.CHAPTER_SIZE
import com.aiclassroom.app.util.MEMORY_BATCH_MESSAGE_COUNT
import com.aiclassroom.app.util.MEMORY_BATCH_DELAY_MS
import com.aiclassroom.app.util.MAIN_MEMORY_PREFIX
import com.aiclassroom.app.util.DAY_MS
import com.aiclassroom.app.util.GITHUB_RELEASES_URL
import com.aiclassroom.app.util.GITHUB_LATEST_RELEASE_API
import com.aiclassroom.app.util.MESSAGE_IMAGE_MAX_SIDE
import com.aiclassroom.app.util.FULLSCREEN_IMAGE_MAX_SIDE
import com.aiclassroom.app.model.ChapterLink
import com.aiclassroom.app.model.ToolCall
import com.aiclassroom.app.model.ChatTurn
import com.aiclassroom.app.util.defaultBaseUrl
import com.aiclassroom.app.util.isRemoteVersionNewer
import com.aiclassroom.app.util.isApiFailure
import com.aiclassroom.app.util.parseStreamDelta
import com.aiclassroom.app.util.parseToolCalls
import com.aiclassroom.app.util.parseNonStreamTurn
import com.aiclassroom.app.util.toolsJsonOf
import com.aiclassroom.app.util.readBody
import com.aiclassroom.app.util.buildJson
import com.aiclassroom.app.util.buildVisionJson
import com.aiclassroom.app.util.filterNsfw
import com.aiclassroom.app.util.ToolCallSink
import com.aiclassroom.app.util.escapeJson
import com.aiclassroom.app.util.optNonNullString
import com.aiclassroom.app.util.buildChapterLinks
import com.aiclassroom.app.util.fallbackChapters
import com.aiclassroom.app.util.localChapterSummary
import com.aiclassroom.app.util.parseChapter
import com.aiclassroom.app.util.displayNameOf
import com.aiclassroom.app.util.mediaJson
import com.aiclassroom.app.util.extensionOf
import com.aiclassroom.app.util.saveUserImage
import com.aiclassroom.app.util.imageDataUrl
import com.aiclassroom.app.util.responseModeLabel
import com.aiclassroom.app.util.togglePluginConfig
import com.aiclassroom.app.util.todayDateText
import com.aiclassroom.app.util.localStudyQuote
import com.aiclassroom.app.util.examRecordTime
import com.aiclassroom.app.util.examRecordSummary
import com.aiclassroom.app.util.parseClassroomIdentity
import com.aiclassroom.app.ui.chat.MessageActionMenu
import com.aiclassroom.app.ui.chat.AiThinkingRow
import androidx.compose.material.icons.filled.Close
import com.aiclassroom.app.ui.splash.SplashCg

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 要在 super.onCreate 之前切主题：windowBackground 在窗口创建那一刻就定了，
        // 晚一步就只能看到默认的浅色底 —— 那就是「开屏先闪一下白」的来源。
        // 只有真会播 CG 的那次启动才用深色底，平时启动仍是浅色，不会闪黑。
        //
        // 标记用 played（值是版本号），不是旧的 seen —— 后者在开发期被写成过 3.5.0，
        // 沿用会让「装完这一版却不播」。
        val splashPrefs = getSharedPreferences("splash_cg", MODE_PRIVATE)
        val willShowSplash = splashPrefs.getString("played", "") != BuildConfig.VERSION_NAME
        if (willShowSplash) setTheme(R.style.Theme_AIClassroom_Splash)
        super.onCreate(savedInstanceState)
        // 音乐插件读播放信息前先拿到 application context；服务常驻时这条是空操作。
        NowPlaying.init(applicationContext)
        setContent {
            AIClassroomTheme {
                // 开屏 CG：**只在安装新版本后的第一次启动播放**。
                // 主界面同时开始组合、被 CG 盖住；CG 末段整体淡出时自然露出来。
                // 点击屏幕任意位置可跳过。
                var showSplash by remember { mutableStateOf(willShowSplash) }
                Box(Modifier.fillMaxSize()) {
                    AIClassroomApp(splashDone = !showSplash, onReplaySplash = { showSplash = true })
                    if (showSplash) {
                        SplashCg(onFinished = {
                            splashPrefs.edit().putString("played", BuildConfig.VERSION_NAME).apply()
                            showSplash = false
                        })
                    }
                }
            }
        }

    }
}

private enum class Tab(val title: String, val icon: ImageVector) {
    Class("课堂", Icons.Default.School),
    Branch("分支", Icons.Default.AccountTree),
    Memory("记忆", Icons.Default.Memory),
    Knowledge("知识库", Icons.Default.Bookmarks),
    Model("设置", Icons.Default.Key)
}

/** 底栏悬浮面板总高（含导航 inset）：固定底元素据此抬升，滚动内容透到面板之下。 */
private val LocalBottomBarLift = compositionLocalOf { 0.dp }

/**
 * 一条对话消息。
 * [toolCallsJson] / [toolCallId] 只服务于 function calling 的临时消息列表：
 * 默认 null，旧存档（只写 role/text）读回来就是这两个默认值，
 * 序列化端也不写这两个字段，因此纯聊天链路的数据格式完全不变。
 *
 * [images] 是这条消息附带的本地图片（AI 用 send_image 发的、用户自己发的各走一条路进来），
 * 每个元素是 [imageEntry] 拼出来的「绝对路径 + [IMAGE_CAPTION_SEPARATOR] + caption」。
 * 默认空列表：旧存档没有这个字段就读成空列表，序列化端也只在不为空时才写。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AIClassroomApp(
    splashDone: Boolean = true,
    /** 彩蛋：设置页连击版本号 5 下时，让外层重放开屏 CG。 */
    onReplaySplash: () -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { ClassroomStore(context) }
    val initialClasses = remember { store.load() }
    var tab by remember { mutableStateOf(Tab.Class) }
    var classIndex by remember { mutableIntStateOf(store.loadIndex(initialClasses.lastIndex)) }
    var input by remember { mutableStateOf("") }
    var classMenuOpen by remember { mutableStateOf(false) }
    var jumpToMessageIndex by remember { mutableStateOf<Int?>(null) }
    var examSession by remember { mutableStateOf<ExamSession?>(null) }
    // 这份考试的起始时刻：会话在多处被创建（AI 触发 / 考试工具 / 存档恢复），
    // 没法塞进 ExamSession（不在本模块），所以在它刚出现时打一次点，提交时写进记录。
    var examStartedAt by remember { mutableStateOf(0L) }
    // NEW 介绍界面的触发点：**CG 结束（或被点击跳过）之后**。
    //
    // 之前用固定 delay 等 5.6s —— 那是假设 CG 一定会播满。
    // 现在 CG 会停在 NEW 上等点击，时长由用户决定，定时就不可靠了，
    // 所以改成由 splashDone 驱动：splash 一收，这里立刻判断要不要弹。
    var showNewDialog by remember { mutableStateOf(false) }
    LaunchedEffect(splashDone) {
        if (splashDone) showNewDialog = !store.hasSeenReleaseNotes(APP_VERSION)
    }
    var activeBranchIndex by remember { mutableIntStateOf(-1) }
    var branchInput by remember { mutableStateOf("") }
    var branchLoading by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showManualDialog by remember { mutableStateOf(false) }
    var saveNotice by remember { mutableStateOf("所有内容自动保存在本机") }
    var modelStatus by remember { mutableStateOf("未获取模型") }
    var isLoading by remember { mutableStateOf(false) }
    var immersiveMode by remember { mutableStateOf(false) }
    var homeQuote by remember { mutableStateOf(localStudyQuote()) }
    val classes = remember { mutableStateListOf<Classroom>().apply { addAll(initialClasses) } }
    val models = remember { mutableStateListOf("gpt-4o-mini", "gpt-4o", "deepseek-chat", "qwen-plus") }
    val scope = rememberCoroutineScope()
    val hazeState = SharedHazeState
    val memoryJobs = remember { java.util.IdentityHashMap<Classroom, Job>() }
    val memoryWatermarks = remember { java.util.IdentityHashMap<Classroom, Int>() }
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

    // 考试存档：exam 字段随课堂一起写进 ai_classroom_14_classes.json，退出考试 / 重建 Activity 都不丢。
    // 作答与草稿是逐字输入的，走 300ms 防抖落盘；工具调用、标记不会、退出与提交属于结构性改动，立即写盘。
    var examSaveJob by remember { mutableStateOf<Job?>(null) }
    // 只盯「有没有考试」这个布尔量：作答每敲一个字都会换一份 ExamSession，
    // 用它当 key 会把起始时间一直往后推。
    LaunchedEffect(examSession == null) {
        examStartedAt = if (examSession == null) 0L else System.currentTimeMillis()
    }

    /**
     * 提交考试：exam 保留一份 submitted 存档（回看用），同时往 examRecords 追加一条历史。
     * 一次落盘，避免「历史写了 / 当前考试没写」这种半截状态。
     */
    fun archiveSubmittedExam(session: ExamSession) {
        examSaveJob?.cancel()
        examSaveJob = null
        val index = classIndex
        if (index in classes.indices) {
            val record = ExamRecord(
                id = java.util.UUID.randomUUID().toString(),
                title = session.title.ifBlank { "AI 随堂考试" },
                startedAt = if (examStartedAt > 0L) examStartedAt else System.currentTimeMillis(),
                submittedAt = System.currentTimeMillis(),
                questionCount = session.questions.size,
                // 只交了图片答案的题也算已答，否则记忆页会显示「0/2」而明显有作答。
                answeredCount = session.questions.count { it.answer.isNotBlank() || it.unknown || it.answerImages.isNotEmpty() },
                submitted = true,
                questions = session.questions.toList(),
            )
            classes[index] = classes[index].copy(
                exam = session,
                examRecords = (classes[index].examRecords + record).takeLast(EXAM_RECORD_LIMIT),
            )
        }
        examStartedAt = 0L
        persist("考试已提交并保存")
    }

    fun saveExam(session: ExamSession?, message: String = "考试已保存", immediate: Boolean = true) {
        val index = classIndex
        if (index in classes.indices) classes[index] = classes[index].copy(exam = session)
        examSaveJob?.cancel()
        if (immediate) {
            examSaveJob = null
            persist(message)
        } else {
            examSaveJob = scope.launch {
                delay(300)
                persist(message)
            }
        }
    }

    // function calling 工具注册表：考试 / 文件 / 网络三条线共用同一个实例，
    // 按注册顺序生成请求里的 tools 数组。
    var netToolsEnabled by remember { mutableStateOf(store.loadNetToolsEnabled()) }
    // 网页工具：截图 / 搜索跟随「网络工具」总开关；web_act 另有一道域名白名单。
    var webActDomains by remember { mutableStateOf(store.loadWebActDomains()) }
    val webConsent = remember { WebConsentBroker() }
    val webEngine = remember { WebCaptureEngine(context) }
    // 生图插件：开关与配置都落在 prefs；工具每次调用现读，设置页保存后立即生效。
    var imageToolsEnabled by remember { mutableStateOf(store.loadImageToolsEnabled()) }
    // 音乐（正在播放 + 播放控制）：独立开关，不挂在网络 / 生图下面。
    // 它读的是别的 App 的播放状态（需要通知使用权），与「能发 http」是两回事：
    // 用户可能想听歌看封面却不想让任何插件联网，所以必须能只关一头。
    var mediaToolsEnabled by remember { mutableStateOf(store.loadMediaToolsEnabled()) }
    // 视频工具：抽帧很吃 CPU（一次自适应扫描要解码上百个缩略图），所以给它一个独立开关，
    // 不跟「网络工具」绑在一起——用户可能想看图却不想让 AI 碰视频文件。
    var videoToolsEnabled by remember { mutableStateOf(store.loadVideoToolsEnabled()) }
    var imageConfig by remember { mutableStateOf(store.loadImageConfig()) }
    // 考试 / 文件 / 配置三组插件也由「设置 → 插件」统一开关，默认开启，状态落在 prefs。
    var examToolsEnabled by remember { mutableStateOf(store.loadExamToolsEnabled()) }
    var fsToolsEnabled by remember { mutableStateOf(store.loadFsToolsEnabled()) }
    var configToolsEnabled by remember { mutableStateOf(store.loadConfigToolsEnabled()) }
    // 用户自定义插件（一个 = 一个工具 + 一条 HTTP 配置）：整库 JSON 存在 prefs，
    // 做成 state 是为了「保存 → registry 重建 → AI 立刻能调」，不用重启 app。
    var customToolsJson by remember { mutableStateOf(store.loadCustomTools()) }
    val imageSink = remember { ImageSinkImpl(context) }
    // AI 本轮要附加到回复上的图片：send_image 往里写，回复落定后 drain 出来挂到那条 assistant 消息。
    // 只活在内存里，不进存档；同一时刻只有一轮回复在跑（isLoading 闸门），所以一个实例就够。
    val turnImages = remember { TurnImageBuffer() }

    // ---- 插件运行时（:feature-plugin 接入）----
    // 插件包落在 filesDir/plugins/<id>/，启动时扫描装载；导入 / 卸载后 pluginVersion 自增触发重组与刷新。
    // 装载失败只打日志，绝不影响课堂与发送链路。
    var pluginVersion by remember { mutableIntStateOf(0) }
    val pluginStateStore = remember { PrefsPluginStateStore(context) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    // ---- 界面活状态 ----
    // pluginStates 是「这一刻要画的 state」，pluginStateVersions 是它的版本号：
    // 哪个插件变了就只动它自己那一项，A 插件刷新不会连带把 B 插件的节点重新组合。
    val pluginStates = remember { mutableStateMapOf<String, JSONObject?>() }
    val pluginStateVersions = remember { mutableStateMapOf<String, Int>() }
    // 全屏层：同一时刻只允许一个插件占用，仲裁在 feature-plugin 的 FullscreenArbiter 里。
    val fullscreenArbiter = remember { FullscreenArbiter({ line -> Log.i(PLUGIN_LOG_TAG, line) }) }
    var fullscreenPluginId by remember { mutableStateOf<String?>(null) }
    // 宿主能力回调（宿主对象在 pluginManager 之前构造）用一个待办队列回到组合里处理，
    // 避免为了拿 pluginManager 把两个 remember 绑成循环依赖。
    val pendingUiRefreshes = remember { java.util.Collections.synchronizedSet(LinkedHashSet<String>()) }
    var pluginRefreshTick by remember { mutableIntStateOf(0) }
    var fullscreenEpoch by remember { mutableIntStateOf(0) }
    var pluginManagerRef by remember { mutableStateOf<PluginManager?>(null) }
    // 图片选择：设置页的 image 字段与 JS 的 host.pickImage 共用同一个系统选择器。
    var pendingImagePick by remember { mutableStateOf<((Uri?) -> Unit)?>(null) }
    val pluginImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val handler = pendingImagePick
        pendingImagePick = null
        handler?.invoke(uri)
    }
    // 生图 / 网络能力直接复用已有工具实现，插件侧与 AI 侧走的是同一条链路。
    val pluginImageTools = remember { ImageTools(config = { store.loadImageConfig() }, sink = imageSink) }
    val pluginNetTool = remember { NetRequestTool({ line -> Log.i("NetTool", line) }) }
    // 封面落盘缓存：只有换歌才重新编码 PNG，插件每秒轮询也不会变成每秒写一次文件。
    val mediaArtworkCache = remember {
        MediaArtworkCache(File(context.filesDir, "images")) { line -> Log.i(PLUGIN_LOG_TAG, line) }
    }
    val pluginHost = remember {
        AppPluginHost(
            stateStore = pluginStateStore,
            // 生图：复用 generate_image 工具（ImageProvider + ImageSink），config 每次现读 prefs。
            generateImageImpl = { prompt, width, height ->
                when {
                    prompt.isBlank() -> PluginResult.Failure("invalid_argument", "prompt 不能为空")
                    !imageToolsEnabled -> PluginResult.Failure("image_disabled", "生图插件已在设置里关闭")
                    else -> {
                        val arguments = JSONObject().put("prompt", prompt)
                        width?.let { arguments.put("width", it) }
                        height?.let { arguments.put("height", it) }
                        val outcome = withContext(Dispatchers.IO) {
                            pluginImageTools.handlers.first { it.name == "generate_image" }.execute(arguments.toString())
                        }
                        when (outcome) {
                            is ToolResult.Ok -> {
                                val path = runCatching { JSONObject(outcome.contentJson).optString("path") }.getOrDefault("")
                                if (path.isBlank()) {
                                    PluginResult.Failure("image_failed", "生图没有返回落盘路径")
                                } else {
                                    PluginResult.Success(outcome.contentJson, path)
                                }
                            }

                            is ToolResult.Error -> PluginResult.Failure("image_failed", outcome.message)
                        }
                    }
                }
            },
            // TTS：复用 speakText 那条链路的 callTts（同一套密钥 / 模型 / 音色），音频落到 cacheDir 后把路径交给插件。
            speakImpl = { text ->
                val config = classes[classIndex.coerceIn(0, classes.lastIndex)].config
                val clean = stripMarkdownForSpeech(text).take(1800)
                when {
                    clean.isBlank() -> PluginResult.Failure("invalid_argument", "text 不能为空")
                    config.ttsApiKey.isBlank() || config.ttsModel.isBlank() ->
                        PluginResult.Failure("tts_not_configured", "未配置语音密钥或模型，请到「设置 → 语音」配置")

                    else -> {
                        val audio = callTts(config, clean)
                        if (audio.isEmpty()) {
                            PluginResult.Failure("tts_failed", "TTS 接口没有返回音频")
                        } else {
                            val file = File(context.cacheDir, "plugin_tts_" + System.currentTimeMillis() + ".mp3")
                            withContext(Dispatchers.IO) { FileOutputStream(file).use { it.write(audio) } }
                            PluginResult.Success("已合成语音 " + audio.size + " 字节", file.absolutePath)
                        }
                    }
                }
            },
            // 网络：复用 net_request，并受「网络工具」总开关约束——关闭时插件拿不到网络能力。
            httpImpl = { url, method, body ->
                when {
                    !netToolsEnabled -> PluginResult.Failure("net_disabled", "网络工具总开关已关闭，插件不能发起网络请求")
                    !url.startsWith("http://") && !url.startsWith("https://") ->
                        PluginResult.Failure("invalid_argument", "只支持 http / https：" + url)

                    else -> {
                        val arguments = JSONObject().put("url", url).put("method", method.trim().ifBlank { "GET" }.uppercase())
                        body?.takeIf { it.isNotBlank() }?.let { arguments.put("body", it) }
                        when (val outcome = withContext(Dispatchers.IO) { pluginNetTool.execute(arguments.toString()) }) {
                            is ToolResult.Ok -> PluginResult.Success(outcome.contentJson)
                            is ToolResult.Error -> PluginResult.Failure("net_failed", outcome.message)
                        }
                    }
                }
            },
            // 对话读取：只看当前课堂，最多 50 条，单条截断到 300 字，避免把整段历史塞给脚本。
            readDialogImpl = { limit ->
                val room = classes[classIndex.coerceIn(0, classes.lastIndex)]
                val lines = room.messages.takeLast(limit.coerceIn(1, 50)).map { message ->
                    message.role + ": " + message.text.replace("\n", " ").take(300)
                }
                PluginResult.Success(lines.joinToString("\n"))
            },
            // 对话写入：BlockingSuspend 会阻塞脚本线程，切主线程会互相等待，
            // 所以把写入投递到主线程排队，脚本立刻拿到成功结果，重组由快照列表自己触发。
            insertDialogImpl = { role, text ->
                val cleanRole = if (role.trim() == "user") "user" else "assistant"
                val cleanText = text.trim()
                if (cleanText.isBlank()) {
                    PluginResult.Failure("invalid_argument", "text 不能为空")
                } else {
                    mainHandler.post {
                        val index = classIndex.coerceIn(0, classes.lastIndex)
                        classes[index].messages.add(ChatMessage(cleanRole, cleanText))
                        persist("插件写入了一条对话")
                    }
                    PluginResult.Success("已插入一条 " + cleanRole + " 消息")
                }
            },
            // ---- 第二批能力：课堂 / 考试 / 记忆 / 知识库 / 跨课堂对话 ----
            // 全部只读复用现有内存数据（classes + config），不另建存储；写操作一律 post 到主线程排队，
            // 与 insertDialog 同一套路：脚本线程阻塞等待，所以不能在这里切主线程再等回来。
            listClassroomsImpl = {
                val items = classes.mapIndexed { index, room ->
                    JSONObject()
                        .put("id", index.toString())
                        .put("name", room.name)
                        .put("topic", room.topic)
                        .put("messageCount", room.messages.size)
                        .put("branchCount", room.branches.size)
                        .put("knowledgeCount", room.files.size)
                        .put("current", index == classIndex)
                        .put("exam", room.exam?.let { if (it.submitted) "submitted" else "in_progress" } ?: "none")
                        // 课堂数据模型里既没有创建时间、消息也不带时间戳，这里如实给 null 而不是编一个。
                        .put("createdAt", JSONObject.NULL)
                }
                PluginResult.Success(
                    cappedJson("classrooms", items, mapOf("currentClassroomId" to classIndex.toString())),
                )
            },
            writeClassroomImpl = { patchJson ->
                val patch = runCatching { JSONObject(patchJson) }.getOrNull()
                val select = patch?.optJSONObject("select")
                val rename = patch?.optJSONObject("rename")
                val targetIndex = (select ?: rename)?.optInt("index", -1) ?: -1
                when {
                    patch == null -> PluginResult.Failure("invalid_argument", "patch 不是合法 JSON")
                    select == null && rename == null ->
                        PluginResult.Failure("invalid_argument", "patch 里需要有 select 或 rename")
                    targetIndex !in classes.indices ->
                        PluginResult.Failure("invalid_argument", "课堂下标越界：" + targetIndex)
                    else -> {
                        val newName = rename?.optString("name").orEmpty().trim().take(64)
                        mainHandler.post {
                            if (select != null) classIndex = targetIndex
                            if (newName.isNotBlank()) {
                                classes[targetIndex] = classes[targetIndex].copy(name = newName)
                            }
                            persist("插件修改了课堂")
                        }
                        PluginResult.Success(
                            """{"classroomId":""" + targetIndex + ""","switched":""" + (select != null) + "}",
                        )
                    }
                }
            },
            readExamImpl = {
                val session = examSession ?: classes.getOrNull(classIndex)?.exam
                if (session == null) {
                    PluginResult.Success("""{"exam":null}""")
                } else {
                    val questions = session.questions.mapIndexed { index, question ->
                        JSONObject()
                            .put("index", index)
                            .put("premise", question.premise.take(800))
                            .put("question", question.question.take(800))
                            .put("answer", question.answer.take(800))
                            .put("unknown", question.unknown)
                            .put("referenceAnswer", question.referenceAnswer.take(1200))
                    }
                    PluginResult.Success(
                        cappedJson(
                            "questions",
                            questions,
                            mapOf(
                                "title" to session.title,
                                "submitted" to session.submitted,
                                "questionCount" to session.questions.size,
                            ),
                        ),
                    )
                }
            },
            writeExamImpl = { patchJson ->
                val patch = runCatching { JSONObject(patchJson) }.getOrNull()
                val base = examSession ?: classes.getOrNull(classIndex)?.exam
                when {
                    patch == null -> PluginResult.Failure("invalid_argument", "patch 不是合法 JSON")
                    base == null -> PluginResult.Failure("no_exam", "当前课堂没有考试")
                    else -> {
                        val questions = base.questions.toMutableList()
                        var applied = 0
                        val patches = patch.optJSONArray("questions")
                        if (patches != null) {
                            for (position in 0 until patches.length()) {
                                val item = patches.optJSONObject(position) ?: continue
                                val index = item.optInt("index", -1)
                                if (index !in questions.indices) continue
                                val question = questions[index]
                                questions[index] = question.copy(
                                    answer = if (item.has("answer")) item.optString("answer").take(4000) else question.answer,
                                    unknown = if (item.has("unknown")) item.optBoolean("unknown", question.unknown) else question.unknown,
                                    referenceAnswer = if (item.has("referenceAnswer")) {
                                        item.optString("referenceAnswer").take(4000)
                                    } else {
                                        question.referenceAnswer
                                    },
                                )
                                applied++
                            }
                        }
                        val updated = base.copy(
                            questions = questions,
                            submitted = if (patch.has("submitted")) patch.optBoolean("submitted", base.submitted) else base.submitted,
                            draft = if (patch.has("draft")) patch.optString("draft").take(4000) else base.draft,
                        )
                        mainHandler.post {
                            examSession = if (updated.submitted) null else updated.withComposeState()
                            saveExam(updated, "插件改写了考试")
                        }
                        PluginResult.Success("""{"applied":""" + applied + ""","questionCount":""" + questions.size + "}")
                    }
                }
            },
            readMemoryImpl = {
                val room = classes.getOrNull(classIndex)
                if (room == null) {
                    PluginResult.Failure("no_classroom", "当前没有课堂")
                } else {
                    val memories = room.memories.map { JSONObject().put("text", it.take(500)) }
                    PluginResult.Success(
                        cappedJson(
                            "memories",
                            memories,
                            mapOf(
                                "worldBook" to room.config.worldBook.take(8000),
                                "corePremises" to JSONArray(room.corePremises.map { it.take(300) }),
                                "memoryCardCount" to room.memoryCards.size,
                            ),
                        ),
                    )
                }
            },
            writeMemoryImpl = { text ->
                val clean = text.trim().take(2000)
                if (clean.isBlank()) {
                    PluginResult.Failure("invalid_argument", "text 不能为空")
                } else {
                    mainHandler.post {
                        val index = classIndex.coerceIn(0, classes.lastIndex)
                        classes[index].memories.add(clean)
                        persist("插件写入了一条记忆")
                    }
                    PluginResult.Success("已追加一条长期记忆")
                }
            },
            readKnowledgeImpl = {
                val items = classes.getOrNull(classIndex)?.files?.map { file ->
                    JSONObject()
                        .put("name", file.name)
                        .put("type", file.type)
                        .put("kind", file.kind)
                        .put("chars", file.chars)
                        .put("content", file.content.take(2000))
                } ?: emptyList()
                PluginResult.Success(cappedJson("files", items))
            },
            readAllDialogsImpl = { classroomId, limitPerClassroom ->
                val perRoom = limitPerClassroom.coerceIn(1, PluginLimits.MAX_DIALOGS_PER_CLASSROOM)
                val indices = if (classroomId.isNullOrBlank()) {
                    classes.indices.toList()
                } else {
                    val byIndex = classroomId.trim().toIntOrNull()
                    val resolved = when {
                        byIndex != null && byIndex in classes.indices -> byIndex
                        else -> classes.indexOfFirst { it.name == classroomId }
                    }
                    if (resolved in classes.indices) listOf(resolved) else emptyList()
                }
                val rooms = indices.map { index ->
                    val room = classes[index]
                    fun linesOf(messages: List<ChatMessage>): JSONArray {
                        val array = JSONArray()
                        messages.takeLast(perRoom).forEach { message ->
                            array.put(
                                JSONObject()
                                    .put("role", message.role)
                                    .put("text", message.text.replace("\n", " ").take(PluginLimits.MAX_DIALOG_CHARS)),
                            )
                        }
                        return array
                    }
                    val branches = JSONArray()
                    room.branches.forEach { branch ->
                        branches.put(
                            JSONObject()
                                .put("title", branch.title)
                                .put("messages", linesOf(branch.messages)),
                        )
                    }
                    JSONObject()
                        .put("id", index.toString())
                        .put("name", room.name)
                        .put("messages", linesOf(room.messages))
                        .put("branches", branches)
                        .put("truncated", room.messages.size > perRoom)
                }
                PluginResult.Success(
                    cappedJson("classrooms", rooms, mapOf("limitPerClassroom" to perRoom)),
                )
            },
            uiRefreshImpl = { pluginId ->
                Log.d(PLUGIN_LOG_TAG, "插件 " + pluginId + " 请求刷新界面")
                // 只把这个插件标脏：下一次组合只重读它自己的 state，别的插件不受影响。
                mainHandler.post {
                    pendingUiRefreshes.add(pluginId)
                    pluginRefreshTick++
                }
            },
            enterFullscreenImpl = { pluginId ->
                fullscreenArbiter.request(pluginId)
                mainHandler.post { fullscreenEpoch++ }
            },
            exitFullscreenImpl = { pluginId ->
                fullscreenArbiter.release(pluginId)
                mainHandler.post { fullscreenEpoch++ }
            },
            pickImageImpl = { pluginId ->
                // 系统选择器只能在主线程弹；用 CompletableDeferred 把结果接回挂起调用。
                val deferred = CompletableDeferred<Uri?>()
                mainHandler.post { pendingImagePick = { uri -> deferred.complete(uri) }; pluginImageLauncher.launch("image/*") }
                val uri = deferred.await()
                val manager = pluginManagerRef
                if (uri == null) {
                    PluginResult.Failure("cancelled", "用户取消了图片选择")
                } else if (manager == null) {
                    PluginResult.Failure("not_available", "插件系统尚未就绪")
                } else {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val stream = context.contentResolver.openInputStream(uri)
                            if (stream == null) {
                                PluginResult.Failure("image_failed", "无法打开选中的图片")
                            } else {
                                val file = manager.importPluginImage(pluginId, "picked", stream, extensionOf(context, uri))
                                PluginResult.Success(file.absolutePath, file.absolutePath)
                            }
                        }.getOrElse {
                            PluginResult.Failure("image_failed", it.message ?: it.javaClass.simpleName)
                        }
                    }
                }
            },
            // ---- 正在播放 / 播放控制（权限 media.read / media.control）----
            // 三条共同的前提：用户开了「音乐」开关，且系统通知使用权已授权。
            // 没授权时给的是 media_permission_required + 一句「去哪儿开」的提示，
            // 而不是一个空曲目 —— 静默失败会让插件作者以为自己的代码写错了。
            mediaCurrentImpl = {
                // 顺序很重要：先判开关，再判通知使用权，最后才去读会话。
                //
                // 以前没有中间那道权限判断，于是「没授权」被当成「没会话」：
                // getActiveSessions 抛 SecurityException → syncSessions 静默吞掉 →
                // current() 恒为 null → 返回一个只有 playing:false 的空曲目。
                // 用户看到的是「未在播放 / 打开酷狗放一首歌试试」，怎么点播放都没反应，
                // 却完全不知道真正的原因是权限没给。
                if (!mediaToolsEnabled) {
                    PluginResult.Failure("media_disabled", MEDIA_DISABLED_HINT)
                } else if (!MediaAccess.isGranted(context)) {
                    PluginResult.Failure("media_permission_required", MEDIA_PERMISSION_HINT)
                } else {
                    var track = NowPlaying.current()
                    if (track == null) {
                        // 兜底：主动向系统要一次活跃会话。
                        // `refreshIfIdle` 只挂在 onNotificationPosted 上 —— 应用重装后、
                        // 或播放器压根不发通知时，那个回调永远不来，sessions 表一直是空的。
                        // 正常情况零开销（current() 非空直接跳过）。
                        withContext(Dispatchers.IO) { runCatching { NowPlaying.refresh() } }
                        track = NowPlaying.current()
                    }
                    PluginResult.Success(mediaJson(context, track, mediaArtworkCache))
                }
            },
            mediaControlImpl = { action ->
                val normalized = action.trim().lowercase(Locale.US)
                val track = NowPlaying.current()
                when {
                    !mediaToolsEnabled -> PluginResult.Failure("media_disabled", MEDIA_DISABLED_HINT)
                    // 一键播放：会话在不在都要处理，所以排在 track == null 之前。
                    // **也排在权限判断之前** —— 它只是用 intent 拉起播放器 App，
                    // 不需要读别人的会话；放在权限后面会让「还没授权」的人连启动都做不到。
                    normalized == "playorshow" -> {
                        val outcome = MusicLauncher.playOrLaunch(context)
                        PluginResult.Success(
                            JSONObject()
                                .put("state", outcome.state)
                                .put("packageName", outcome.packageName ?: "")
                                .put("playing", outcome.playing)
                                .put("message", outcome.message ?: "")
                                .toString(),
                        )
                    }

                    // 同 mediaCurrent：没权限却按「没会话」报，会让用户以为播放器没开。
                    !MediaAccess.isGranted(context) ->
                        PluginResult.Failure("media_permission_required", MEDIA_PERMISSION_HINT)

                    track == null -> PluginResult.Failure("media_no_session", MEDIA_NO_SESSION_HINT)
                    normalized == "play" || normalized == "pause" ||
                        normalized == "next" || normalized == "prev" -> {
                        val done = when (normalized) {
                            "play" -> NowPlaying.play()
                            "pause" -> NowPlaying.pause()
                            "next" -> NowPlaying.next()
                            else -> NowPlaying.previous()
                        }
                        if (done) {
                            PluginResult.Success(normalized)
                        } else {
                            PluginResult.Failure("media_control_failed", "播放器没有接受这次 " + normalized + " 操作")
                        }
                    }

                    normalized.startsWith("seek:") -> {
                        val ms = normalized.removePrefix("seek:").trim().toLongOrNull()
                        if (ms == null || ms < 0) {
                            PluginResult.Failure("invalid_argument", "seek 的写法是 seek:<毫秒非负整数>，实际 '" + action + "'")
                        } else if (NowPlaying.seekTo(ms)) {
                            PluginResult.Success("seek:" + ms)
                        } else {
                            PluginResult.Failure("media_control_failed", "播放器没有接受这次拖动")
                        }
                    }

                    else -> PluginResult.Failure(
                        "invalid_argument",
                        "action 只能是 play|pause|next|prev|playOrShow|seek:<毫秒>，实际 '" + action + "'",
                    )
                }
            },
            mediaArtworkImpl = {
                val track = NowPlaying.current()
                val bitmap = track?.artwork
                when {
                    !mediaToolsEnabled -> PluginResult.Failure("media_disabled", MEDIA_DISABLED_HINT)
                    track == null -> PluginResult.Failure("media_no_session", MEDIA_NO_SESSION_HINT)
                    bitmap == null -> PluginResult.Failure("media_no_artwork", "当前这首歌没有内嵌封面")
                    else -> withContext(Dispatchers.IO) {
                        runCatching {
                            val path = mediaArtworkCache.store(
                                MediaArtworkCache.keyOf(track.packageName, track.title, track.album),
                                bitmap,
                            )
                            PluginResult.Success(path, path)
                        }.getOrElse {
                            PluginResult.Failure("media_artwork_failed", it.message ?: it.javaClass.simpleName)
                        }
                    }
                }
            },
        )
    }
    // 插件包的配置与总开关都落在 prefs：装载时读一次，设置页保存后 reload 重建 runtime。
    val pluginManager = remember {
        // 先把随 APK 发布的内置插件释放到插件目录。
        // 之前它们只存在于 feature-plugin 的测试资源里，从没打进 APK ——
        // 结果每台新设备装完插件列表都是空的，必须手动 adb push 才有得用。
        runCatching {
            BuiltinPlugins.install(context, java.io.File(context.filesDir, "plugins"))
        }.onFailure { Log.w("BuiltinPlugins", "释放内置插件失败：" + it.message) }
        PluginManager(
            context = context,
            host = pluginHost,
            stateStore = pluginStateStore,
            pluginSettings = { id -> store.loadPluginSettings(id) },
            pluginEnabled = { id -> store.loadPluginEnabled(id) },
        )
    }

    /**
     * 重读一个插件的界面 state 并把版本号 +1（只动它自己那一项）。
     * 这是「活状态」的唯一入口：requestUiRefresh / ui_ticker / 界面回调 都走它。
     *
     * provider 是插件脚本，**不能在主线程调**（一次卡顿就是掉帧），所以取数在 IO、写回在主线程。
     */
    suspend fun refreshPluginStateOf(pluginId: String) {
        val state = withContext(Dispatchers.IO) { pluginManager.uiStateOf(pluginId) }
        pluginStates[pluginId] = state
        pluginStateVersions[pluginId] = (pluginStateVersions[pluginId] ?: 0) + 1
    }

    /** 重读所有插件的 state（装载 / 启用开关 / 全屏进出这类全局变化时用）。 */
    suspend fun refreshAllPluginStatesNow() {
        val all = withContext(Dispatchers.IO) { pluginManager.uiStates() }
        for ((id, state) in all) {
            pluginStates[id] = state
            pluginStateVersions[id] = (pluginStateVersions[id] ?: 0) + 1
        }
    }

    // 宿主能力回调（pickImage）晚于本函数构造，用 SideEffect 把引用补上：直接赋值会在组合期写状态。
    androidx.compose.runtime.SideEffect { pluginManagerRef = pluginManager }

    /** 全屏层重算：谁想独占、谁最终被选中，都交给仲裁器，宿主只跟着结果画。 */
    fun resolveFullscreen() {
        val next = fullscreenArbiter.resolve()
        if (next == fullscreenPluginId) return
        val closing = fullscreenPluginId
        fullscreenPluginId = next
        if (next != null) scope.launch { refreshPluginStateOf(next) }
        // 退出的插件也要刷一次：它在全屏层里可能改过 state，回到普通插槽要按新值画。
        if (closing != null) scope.launch { refreshPluginStateOf(closing) }
        Log.i(PLUGIN_LOG_TAG, "FULLSCREEN 当前占用：" + (next ?: "无"))
    }
    val promptHookPipeline = remember { PromptHookPipeline(logger = { line -> Log.i(PLUGIN_LOG_TAG, line) }) }
    // ---- 触发器（第二批）：timer / event ----
    // 分发永远在后台线程、永远被 runCatching 兜住：插件坏掉不能影响对话、考试、启动。
    val pluginEventBus = remember { PluginEventBus({ line -> Log.i(PLUGIN_LOG_TAG, line) }) }
    // 界面时钟（ui_ticker）：每秒跑一次插件回调、跑完就重绘。单次触发失败只丢这一帧。
    val pluginUiDriver = remember {
        PluginUiDriver(
            logger = { line -> Log.i(PLUGIN_LOG_TAG, line) },
            onTick = { pluginId ->
                // 回调跑在后台线程：只标脏，重绘回主线程做。
                mainHandler.post {
                    pendingUiRefreshes.add(pluginId)
                    pluginRefreshTick++
                }
            },
        )
    }
    fun dispatchPluginEvent(event: String, payload: Map<String, String> = emptyMap()) {
        runCatching {
            val plugins = pluginManager.snapshot()
            if (plugins.isEmpty()) return@runCatching
            scope.launch(Dispatchers.IO) {
                runCatching { pluginEventBus.dispatch(plugins, PluginEvent(event, payload)) }
                    .onFailure { Log.w(PLUGIN_LOG_TAG, "插件事件 " + event + " 分发失败", it) }
            }
        }.onFailure { Log.w(PLUGIN_LOG_TAG, "插件事件 " + event + " 派发失败", it) }
    }
    // 定时器跟着「插件装载结果」与「前后台状态」走：插件装了 / 卸载了要重算，
    // 切后台必须停（否则白耗电），回前台再起。
    val pluginLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(pluginLifecycleOwner, pluginVersion) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> runCatching {
                    val plugins = pluginManager.snapshot()
                    // 已经在跑就别重启：startTimers 会先 stop 再起，白重置一轮倒计时。
                    if (plugins.isNotEmpty() && pluginEventBus.timerCount == 0) {
                        pluginEventBus.startTimers(plugins, scope, PluginClock.SYSTEM)
                    }
                    // 界面时钟：回前台先解暂停，再按当前可见集合重算。
                    pluginUiDriver.setPaused(false)
                    pluginUiDriver.start(plugins, scope)
                }.onFailure { Log.w(PLUGIN_LOG_TAG, "启动插件定时器失败", it) }

                Lifecycle.Event.ON_STOP -> runCatching {
                    pluginEventBus.stopTimers()
                    // 切后台只暂停、不停协程：回来后立刻回到原来的节奏，不会累积补跑。
                    pluginUiDriver.setPaused(true)
                    Log.i(PLUGIN_LOG_TAG, "应用退到后台，插件界面时钟已暂停")
                }.onFailure { Log.w(PLUGIN_LOG_TAG, "停止插件定时器失败", it) }

                else -> Unit
            }
        }
        pluginLifecycleOwner.lifecycle.addObserver(observer)
        runCatching {
            val plugins = pluginManager.snapshot()
            if (plugins.isNotEmpty() &&
                pluginEventBus.timerCount == 0 &&
                pluginLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            ) {
                pluginEventBus.startTimers(plugins, scope, PluginClock.SYSTEM)
            }
        }.onFailure { Log.w(PLUGIN_LOG_TAG, "启动插件定时器失败", it) }
        onDispose {
            pluginLifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { pluginEventBus.stopTimers() }
            runCatching { pluginUiDriver.stopAll() }
        }
    }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { pluginManager.loadAll() }
        pluginVersion++
        Log.i(PLUGIN_LOG_TAG, "启动时装载插件 " + loaded + " 个")
        // app_started：扫描装载完成之后才分发，插件此刻已经能读到自己的 storage。
        dispatchPluginEvent(PluginEventNames.APP_STARTED, mapOf("loaded" to loaded.toString()))
    }
    val pluginInfos = remember(pluginVersion) { pluginManager.infos() }

    // ---- 界面插槽（GLOBAL_OVERLAY / MESSAGE_BODY_TAIL / TOP_BAR_ACTIONS / FULLSCREEN）----
    // 没有插件声明插槽时这几个列表都是空的，渲染点第一行就 return，组合开销为零。
    val uiOverlayBindings = remember(pluginVersion) { pluginManager.uiBindings("GLOBAL_OVERLAY") }
    val uiMessageTailBindings = remember(pluginVersion) { pluginManager.uiBindings("MESSAGE_BODY_TAIL") }
    val uiTopBarBindings = remember(pluginVersion) { pluginManager.uiBindings("TOP_BAR_ACTIONS") }
    val uiFullscreenBinding = remember(pluginVersion, fullscreenPluginId) {
        fullscreenPluginId?.let { pluginManager.fullscreenBinding(it) }
    }
    // 用户图片：设置页里 type=image 的字段（只在真声明了的时候读，没有插件时是空表）。
    val uiUserImages = remember(pluginVersion) {
        if (uiOverlayBindings.isEmpty() && uiMessageTailBindings.isEmpty() && uiTopBarBindings.isEmpty()) {
            emptyMap()
        } else {
            pluginManager.allRuntimes().associate { it.manifest.id to pluginManager.pluginImagePaths(it.manifest.id) }
        }
    }
    val uiUserImageFits = remember(pluginVersion) {
        if (uiUserImages.isEmpty()) emptyMap()
        else pluginManager.allRuntimes().associate { it.manifest.id to pluginManager.pluginImageFits(it.manifest.id) }
    }
    // 宿主能力（host.requestUiRefresh / ui_ticker）标脏的插件，在这里重读 state 并把版本号 +1：
    // 只有被标脏的插件会重新取值 + 重组，其它插件的节点保持原样（provider 也就只被问一次）。
    // 只重读被标脏的那几个插件：每秒的 tick 不会牵连到别的插件（provider 也就只被问一次）。
    LaunchedEffect(pluginRefreshTick) {
        val dirty = synchronized(pendingUiRefreshes) {
            val copy = pendingUiRefreshes.toList()
            pendingUiRefreshes.clear()
            copy
        }
        for (id in dirty) refreshPluginStateOf(id)
    }
    // 装载 / 启用开关 / 全屏进出是全局变化：整体重读一遍，保证新出现的插件有初始 state。
    LaunchedEffect(pluginVersion, fullscreenEpoch) {
        fullscreenArbiter.setDeclared(pluginManager.fullscreenPluginIds())
        resolveFullscreen()
        refreshAllPluginStatesNow()
    }
    // 渲染器直接吃这份快照：读取是 Compose 快照读，某一项变了只重组读它的那个插件。
    val uiStateSnapshot: Map<String, JSONObject?> = pluginStates

    // ---- 全屏层的系统栏 ----
    // 进入时隐藏状态栏与导航栏、退出时恢复。用 WindowInsetsController 而不是 manifest 的
    // 系统 UI 标志：同一套窗口在不同页面来回切，manifest 做不到「只在这一层隐藏」。
    val windowView = LocalView.current
    DisposableEffect(fullscreenPluginId) {
        val window = (windowView.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, windowView) }
        if (fullscreenPluginId != null) {
            controller?.let {
                it.systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        } else {
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            // 离开这个页面 / 插件被卸载时也要恢复：否则一个坏插件能把用户的系统栏永久藏掉。
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    // ---- ui_ticker 的可见性 ----
    // 只有「插件已启用 + 所在插槽此刻真的在页面上」才跑时钟：
    // 沉浸模式没打开时，时钟插件不该在后台空转耗电。切后台由 lifecycle 那一段统一暂停。
    val uiVisibleTickerPlugins: Set<String> = remember(
        pluginVersion,
        fullscreenPluginId,
        tab,
        uiOverlayBindings,
        uiMessageTailBindings,
        uiTopBarBindings,
    ) {
        val visible = LinkedHashSet<String>()
        // 常驻插槽：只要插件启用了就在页面上（课堂页一直有顶栏与消息列表）。
        uiOverlayBindings.forEach { visible += it.pluginId }
        if (tab == Tab.Class) {
            uiTopBarBindings.forEach { visible += it.pluginId }
            uiMessageTailBindings.forEach { visible += it.pluginId }
        }
        // FULLSCREEN：只有真的占用了全屏层才算可见。
        fullscreenPluginId?.let { visible += it }
        visible
    }

    // 可见集合（或装载结果）一变就重算界面时钟：新可见的起循环、不可见的立刻停。
    LaunchedEffect(pluginVersion, uiVisibleTickerPlugins) {
        runCatching {
            pluginUiDriver.setVisiblePlugins(uiVisibleTickerPlugins)
            pluginUiDriver.start(pluginManager.snapshot(), scope)
            pluginUiDriver.stopInvisible()
        }.onFailure { Log.w(PLUGIN_LOG_TAG, "重算插件界面时钟失败", it) }
    }
    // 界面回调：渲染器只递事件，真正调用插件函数在 IO 线程，跑完只刷新这一个插件。
    val onPluginUiEvent: (PluginUiEvent) -> Unit = remember(pluginManager) {
        { event ->
            scope.launch(Dispatchers.IO) {
                pluginManager.invokeUiEntry(event.pluginId, event.entry, event.event, event.value)
                mainHandler.post {
                    pendingUiRefreshes.add(event.pluginId)
                    pluginRefreshTick++
                }
            }
        }
    }

    // 白名单读写走 prefs；每次现读，设置页删掉一个域名后下一次调用立刻生效。
    val webDomainStore = remember(store) {
        object : WebDomainStore {
            override fun loadAllowed(): List<String> = store.loadWebActDomains()
            override fun saveAllowed(domains: List<String>) {
                store.saveWebActDomains(domains)
                mainHandler.post { webActDomains = store.loadWebActDomains() }
            }
        }
    }

    val toolRegistry = remember(customToolsJson) {
        ToolRegistry().apply {
            // 考试工具：直接操作当前进行中的考试；update 给到的已是新实例，换回 Compose 快照列表以便作答重绘，
            // 同时写回课堂存档（dispatch 跑在 IO 线程，落盘借 scope 回到主线程，避免与组合快照竞争）。
            registerExamTools(this, current = { examSession }, update = { updated ->
                val snapshot = updated.withComposeState()
                examSession = snapshot
                scope.launch { saveExam(snapshot, "考试已保存") }
            })
            // 文件工具：边界＝应用私有目录（用户已确认）。外部目录暂未开放。
            runCatching { FileTools.registerFileTools(this, listOf(context.filesDir.absolutePath)) }
            // 网络工具：用户已授权任意 host:port；保留总开关与脱敏日志两道护栏。
            registerNetTools(this, enabled = { netToolsEnabled }, logger = { line -> Log.i("NetTool", line) })
            // 网页工具：截图 / 搜索 / 受控点击。三个都受「网络工具」总开关约束（下面统一加闸门）；
            // web_act 额外要求域名在白名单里，未授权域名当场弹框问用户。
            registerWebTools(this, WebToolDeps(
                engine = webEngine,
                domains = webDomainStore,
                consent = webConsent,
                saveScreenshot = { bytes, name -> imageSink.save(bytes, name, "image/png") },
                analyze = { system, prompt, dataUrl ->
                    val room = classes.getOrNull(classIndex.coerceIn(0, classes.lastIndex))
                    if (room == null) "当前没有可用课堂配置，无法调用视觉模型。" else
                        callVisionWithFallback(room.config, room.config.visionModels(), system, prompt, dataUrl)
                },
                log = { line -> Log.i("WebTool", line) },
            ))
            // 视频工具：从设备本地视频按自适应密度抽帧，可选交给视觉模型理解。
            // 分析复用注入进来的视觉链路（一次可以带多张图），抽帧本身跑在 Dispatchers.IO。
            registerVideoTools(this, VideoToolDeps(
                extractor = VideoFrameExtractor(context),
                saveFrame = { bytes, fileName -> imageSink.save(bytes, fileName, "image/png") },
                analyze = { system, prompt, dataUrls ->
                    val room = classes.getOrNull(classIndex.coerceIn(0, classes.lastIndex))
                    if (room == null) {
                        Result.failure(IllegalStateException("当前没有可用课堂配置，无法调用视觉模型。"))
                    } else {
                        // 视觉链路把失败也当普通字符串返回（"调用失败：…"），这里换成 Result，
                        // 工具层才分得清「分析成功」和「模型不干了，该退化成逐帧再试」。
                        runCatching {
                            callVisionWithFallback(
                                room.config,
                                room.config.visionModels(),
                                system,
                                prompt,
                                dataUrls,
                                emptyList(),
                            )
                        }.mapCatching { answer ->
                            if (isApiFailure(answer)) error(answer) else answer
                        }
                    }
                },
                log = { line -> Log.i("VideoTool", line) },
            ))
            // 生图 / 网络工具都要访问「本机或局域网里的 http 端口」：本地 SD WebUI、其它应用的 API 端口
            // 只提供明文 http，而 targetSdk 35 默认拦截明文，所以 Manifest 里开了 usesCleartextTraffic。
            // 影响面：只有 http 走不加密链路（建议只填本地 / 可信局域网地址），https 仍走系统信任链。
            // 生图插件：config 用 lambda 每次现读 prefs，设置页一保存就生效；
            // 保存能力由 app 侧 ImageSinkImpl 注入（feature-exam 是纯 JVM 模块，拿不到 Context）。
            registerImageTools(this, config = { store.loadImageConfig() }, sink = imageSink)
            // 通用元工具：一个 universal_call 能表达任何 HTTP 服务（含 nightmare 这种非标准协议），
            // 新增服务只需一份 profile JSON，不必再写 provider 类。library 每次现读，导入即生效。
            registerUniversalTools(this, sink = imageSink, library = { store.loadUniversalProfiles() })
            // 配置工具：让 AI 能在对话里切换模型 / 改 BaseURL / 调温度；密钥只读且脱敏，无写入入口。
            registerConfigTools(this, ConfigAccessAdapter(
                readConfig = { classes[classIndex.coerceIn(0, classes.lastIndex)].config },
                writeConfig = { updated ->
                    val index = classIndex.coerceIn(0, classes.lastIndex)
                    classes[index] = classes[index].copy(config = updated)
                    persist("配置已更新")
                }
            ))
            // 每组插件统一套一层总开关闸门：关闭时在本地直接回错，工具实现体一行都不会跑。
            gateTools("exam_", { examToolsEnabled }, "考试插件已在设置里关闭，本次没有创建或改动任何考试；需要考试工具请到「设置 → 插件」打开开关。")
            gateTools("fs_", { fsToolsEnabled }, "文件插件已在设置里关闭，本次没有读写任何文件；需要文件工具请到「设置 → 插件」打开开关。")
            gateTools("config_", { configToolsEnabled }, "配置插件已在设置里关闭，本次没有改动任何配置；需要配置工具请到「设置 → 插件」打开开关。")
            gateTools("generate_image", { imageToolsEnabled }, "生图插件已在设置里关闭，本次没有发起任何请求；需要生图请到「设置 → 插件 → 生图」打开开关。")
            // 网页工具与网络工具同属「上网」这一件事：总开关关掉时三个工具一起拒绝，
            // 免得用户以为关了网络 AI 还能悄悄开网页。
            gateTools("web_", { netToolsEnabled }, "网络工具已在设置里关闭，本次没有打开任何网页；需要网页工具请到「设置 → 插件 → 网络工具」打开开关。")
            // 插件元工具：AI 可以在对话里现场造一个 .aip 插件（manifest + logic.js）并立刻生效。
            register(PluginInstallTool(pluginManager) { info ->
                mainHandler.post { pluginVersion++ }
                Log.i(PLUGIN_LOG_TAG, "AI 安装了插件 " + info.id + "（" + info.name + "）")
            })
            // 用户自定义插件放在闸门之后注册：spec 现读 state（保存后 json 变 → registry 重建），
            // 每个 spec 生成一个 ToolHandler，工具参数名就是 bodyTemplate 里的占位符。
            registerCustomTools(this, { CustomToolSpec.listFromJson(customToolsJson) }, imageSink)
            // 主动发图放在自定义插件之后注册：同名的自定义工具顶不掉它，也就绕不过下面那道生图总开关。
            register(SendImageTool(turnImages, filesDir = { context.filesDir }))
            gateTools("send_image", { imageToolsEnabled }, "生图插件已在设置里关闭，本次没有发送任何图片；需要发图请到「设置 → 插件 → 生图」打开开关。")
            // 视频工具走自己的开关：抽帧吃 CPU，用户要能单独关掉，不必连生图一起关。
            gateTools("video_", { videoToolsEnabled }, "视频插件已在设置里关闭，本次没有读取任何视频文件、也没有抽帧；需要视频工具请到「设置 → 插件 → 视频工具」打开开关。")
        }
    }
    // 注册表非空即外发 tools；若将来把工具全部关闭，这里自然返回 null，请求体回到纯聊天形态。
    val requestToolsJson = remember(toolRegistry) {
        if (toolRegistry.all().isEmpty()) null else toolsJsonOf(toolRegistry)
    }

    // 验收用的一次性自查：adb 带 --ez web_selftest true 启动才会跑，正常使用走不到这里。
    // 走 toolRegistry.dispatch 这条与线上完全相同的路径，所以能证明「注册 → 闸门 → 引擎」整条链接通。
    LaunchedEffect(Unit) {
        if ((context as? android.app.Activity)?.intent?.getBooleanExtra("web_selftest", false) != true) return@LaunchedEffect
        scope.launch(Dispatchers.IO) {
            WebSelfTest.run(
                dispatch = { toolName, toolArgs ->
                    when (val outcome = toolRegistry.dispatch(toolName, toolArgs)) {
                        is ToolResult.Ok -> outcome.contentJson
                        is ToolResult.Error -> "{\"error\":\"" + outcome.message.escapeJson() + "\"}"
                    }
                },
                seedWhitelist = { domains -> store.saveWebActDomains(domains) },
                log = { line -> Log.i("WebSelfTest", line) },
                output = File(context.filesDir, "web_selftest_result.txt"),
            )
        }
    }

    // 视频工具的一次性自查：adb 带 --ez video_selftest true 启动才会跑，正常使用走不到这里。
    // 走 toolRegistry.dispatch 这条与线上完全相同的路径，覆盖自适应 / 均匀 / 视觉分析 / 各类错误路径。
    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        if (!activity.intent.getBooleanExtra("video_selftest", false)) return@LaunchedEffect
        val videoPath = activity.intent.getStringExtra("video_selftest_path")?.takeIf { it.isNotBlank() }
            ?: File(context.filesDir, "test.mp4").absolutePath
        scope.launch(Dispatchers.IO) {
            VideoSelfTest.run(
                videoPath = videoPath,
                longVideoPath = activity.intent.getStringExtra("video_selftest_long_path")
                    ?.takeIf { it.isNotBlank() },
                dispatch = { toolName, argumentsJson ->
                    when (val outcome = toolRegistry.dispatch(toolName, argumentsJson)) {
                        is ToolResult.Ok -> "OK " + outcome.contentJson
                        is ToolResult.Error -> "ERROR " + outcome.message
                    }
                },
                output = File(context.filesDir, "video_selftest_result.txt"),
                log = { line -> Log.i("VideoSelfTest", line) },
            )
        }
    }

    if (classIndex > classes.lastIndex) classIndex = classes.lastIndex.coerceAtLeast(0)
    val current = classes[classIndex]
    val activeModel = current.config.chatModels().firstOrNull().orEmpty()
    val activeModelChain = current.config.chatModels()
    val systemDark = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val darkInterface = isDarkInterface(current.config, systemDark)
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

    LaunchedEffect(current.name, current.config.apiKey, activeModel) {
        if (current.messages.isEmpty()) {
            homeQuote = generateHomeQuote(current.config, activeModelChain).ifBlank { localStudyQuote() }
        }
    }

    // 冷启动 / 切课堂时恢复该课堂的存档考试：未提交的自动打开接着做，已提交的只在课堂页留「继续上次考试」入口。
    LaunchedEffect(classIndex) {
        val saved = classes.getOrNull(classIndex)?.exam
        examSession = if (saved != null && !saved.submitted) saved.withComposeState() else null
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
            val previousIndex = classIndex
            classes.removeAt(deleteIndex)
            classIndex = when {
                deleteIndex < previousIndex -> (previousIndex - 1).coerceAtLeast(0)
                previousIndex > classes.lastIndex -> classes.lastIndex
                else -> previousIndex
            }
        }
        persist("课堂已删除")
    }

    fun systemPrompt(room: Classroom): String {
        val characterCards = formatCharacterCards(room.files.filter { it.kind == "character_card" })
        val memory = truncatePromptSection(room.memories.takeLast(MEMORY_PROMPT_LIMIT).joinToString("\n"), MEMORY_CONTEXT_LIMIT)
        val corePremises = truncatePromptSection(room.corePremises.takeLast(CORE_PREMISE_LIMIT).joinToString("\n") { "- $it" }, CORE_PREMISE_CONTEXT_LIMIT)
        val worldBook = truncatePromptSection(room.config.worldBook, WORLD_BOOK_CONTEXT_LIMIT)
        val chapterIndex = truncatePromptSection(room.chapters.takeLast(CHAPTER_PROMPT_LIMIT).joinToString("\n") { chapter ->
            "第 ${chapter.startIndex + 1}-${chapter.endIndex + 1} 条｜${chapter.title}：${chapter.summary}"
        }, CHAPTER_CONTEXT_LIMIT)
        val safety = if (room.config.efficientMode) "高效模式：过滤 NSFW、色情、血腥、违法、仇恨和自伤内容。" else ""
        return buildString {
            appendLine(mentorPriorityBlock(room.config))
            appendLine()
            appendLine(conversationConsistencyRules())
            appendLine(responseBoundaryRules())
            appendLine("[课堂信息]")
            appendLine("讲师名字：${room.config.mentorName.ifBlank { "AI 讲师" }}")
            appendLine("对用户的称呼：${room.config.userAlias.ifBlank { "同学" }}")
            appendLine("课堂：${room.name}")
            appendLine("学习内容：${room.topic}")
            if (worldBook.isNotBlank()) {
                appendLine()
                appendLine("[用户手写世界书，优先级低于讲师人格，高于自动摘要]")
                appendLine(worldBook)
            }
            if (corePremises.isNotBlank()) {
                appendLine()
                appendLine("[常驻极简前提，每次对话都必须用于维持长期连续性]")
                appendLine(corePremises)
            }
            if (chapterIndex.isNotBlank()) {
                appendLine()
                appendLine("[章节索引，仅用于定位长期进度，不代表完整原文]")
                appendLine(chapterIndex)
            }
            if (memory.isNotBlank()) {
                appendLine()
                appendLine("[长期记忆，仅作参考；若与最近连续对话冲突，以最近连续对话为准]")
                appendLine("记忆视角：当前回答者是讲师/作者协调视角，可以读取私密记忆来维持因果，但不得把 author_private 或 character_private 内容当作公开事实，也不得让未知角色据此行动或发言。")
                appendLine(memory)
            }
            if (characterCards.isNotBlank()) {
                appendLine()
                appendLine("[小说创作角色卡｜作者级人物参考]")
                appendLine("以下角色卡用于保持人物设定、关系、口吻、动机、行为边界和情绪反应连续。不要机械复述角色卡；角色只能根据自身可见事实和已知信息行动。角色卡与最近连续对话已发生的事实冲突时，以最近连续对话为准并自然协调。角色卡中的私密内容不能变成其他角色的知识。")
                appendLine(characterCards)
            }
            // 网页工具的用法：登录墙 + 视频定位这两件事最容易卡住模型，明确写出来。
            appendLine()
            appendLine("[网页工具的用法]")
            appendLine("遇到需要登录才能看的页面时，按这个顺序做：")
            appendLine("1. 先用 web_capture 截整页，看清登录区在页面的哪个位置；")
            appendLine("2. 再用 clipX / clipY / clipW / clipH 只截二维码那一块（坐标是截图自身像素，不是网页 CSS 像素）；")
            appendLine("3. 用 send_image 把这张截图发给用户，说明「请用手机扫码登录，登录后告诉我」；")
            appendLine("4. 等用户确认后再继续，不要反复截图。")
            appendLine("绝对不要把二维码描述成文字或链接 —— 用户扫不出来。")
            appendLine()
            appendLine("用户明确提到某个网站（给了链接或站名）而它还没被放行时，用 web_allow_domain 加进白名单，")
            appendLine("并在回复里告诉他放行了哪个站点。不要自己猜域名，也不要批量放行。")
            appendLine()
            appendLine("要在网页上精确定位（例如把视频跳到某一秒再看画面）时，用 web_act 的 js 动作：")
            appendLine("  actions = [{\"type\":\"js\",\"script\":\"document.querySelector('video').currentTime = 83\"}]")
            appendLine("之后再用 web_capture 截图，就能看到那一秒的画面。")
            appendLine("js 动作能执行任意脚本，只用来读页面状态或控制播放器；不要用它提交表单、转账、删除数据。")
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
            appendLine("分支视角隔离：分支可以使用主课堂的公开事实和创建时上下文；涉及 author_private 或 character_private 的记忆时，只能作为作者层面的因果约束，禁止让未标记为知道者的角色直接获知。")
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
            val memoryPack = buildLearningMemoryPack(snapshot, room.config, model)
            val branchMemory = room.memories.filterNot { it.startsWith(MAIN_MEMORY_PREFIX) }
            room.chapters.clear()
            room.chapters.addAll(chapters)
            room.corePremises.clear()
            room.corePremises.addAll(memoryPack.corePremises.takeLast(CORE_PREMISE_LIMIT))
            room.memoryCards.clear()
            room.memoryCards.addAll(mergeMemoryCards(memoryPack.cards).takeLast(MEMORY_CARD_STORE_LIMIT))
            room.memories.clear()
            room.memories.addAll(branchMemory.takeLast(MEMORY_PROMPT_LIMIT / 2))
            room.memories.addAll(chapters.takeLast(MEMORY_PROMPT_LIMIT).map { chapter ->
                "$MAIN_MEMORY_PREFIX ${chapter.title}: ${chapter.summary}"
            })
            memoryWatermarks[room] = snapshot.size
            persist("记忆已在后台整理并保存")
        }
    }

    /**
     * 工具执行循环（function calling 骨架）：
     * 模型返回 tool_calls 时逐个 ToolRegistry.dispatch，把结果以 role=tool + tool_call_id
     * 追加到本轮临时消息列表后重发一次；终止条件二选一：本轮没有 tool_calls，
     * 或已经发满 [TOOL_LOOP_MAX_ROUNDS] 次请求（最多 5 次，含首次），避免无限调用。
     * 返回最后一轮模型的普通文本回复；工具消息只活在临时列表里，不写回 room.messages。
     */
    suspend fun runToolLoop(room: Classroom, system: String, toolsJson: String, onDelta: (String) -> Unit): String {
        val messages = promptMessagesForRoom(room, room.messages.dropLast(1).toList()).toMutableList()
        var requestRound = 0
        while (true) {
            val sink = ToolCallSink()
            val reply = callChatStreamWithFallback(room.config, activeModelChain, system, messages, toolsJson, sink, onDelta)
            val callsJson = sink.resultJson()
            val calls = parseToolCalls(callsJson)
            val lastRound = requestRound >= TOOL_LOOP_MAX_ROUNDS - 1
            Log.d(TOOL_LOG_TAG, "round=$requestRound reply=${reply.take(120)} tool_calls=${callsJson.orEmpty()}")
            if (calls.isEmpty() || lastRound || isApiFailure(reply)) {
                if (calls.isNotEmpty() && lastRound) Log.w(TOOL_LOG_TAG, "工具循环达到 $TOOL_LOOP_MAX_ROUNDS 次请求上限，丢弃本轮 tool_calls")
                return reply
            }
            // 只调用工具的回复内容为空，历史里按 OpenAI 规范写成空 content，不把占位提示发给模型。
            messages += ChatMessage("assistant", if (reply == NO_CONTENT_REPLY) "" else reply, toolCallsJson = callsJson)
            calls.forEach { call ->
                // 文件与网络工具都是阻塞 IO（HttpURLConnection 无异步 API），必须切到 IO 线程；
                // 否则一次网络工具超时就会把主线程卡住，直接 ANR。
                val outcome = withContext(Dispatchers.IO) { toolRegistry.dispatch(call.name, call.argumentsJson) }
                val output = when (outcome) {
                    is ToolResult.Ok -> outcome.contentJson
                    is ToolResult.Error -> "{\"error\":\"" + outcome.message.escapeJson() + "\"}"
                }
                Log.d(TOOL_LOG_TAG, "dispatch ${call.name}(${call.argumentsJson}) -> ${output.take(300)}")
                messages += ChatMessage("tool", output, toolCallId = call.id)
            }
            requestRound++
        }
    }

    fun sendMessage(seed: String? = null, allowExamTrigger: Boolean = true, images: List<String> = emptyList()) {
        val text = (seed ?: input).trim()
        if (text.isBlank() || isLoading) return
        val room = current
        val roomIndex = classIndex
        val isFirstConversation = room.messages.isEmpty()
        input = ""
        // images 用与消息气泡相同的条目格式（绝对路径 + '\u0001' + 说明），
        // 所以图片答案既能在气泡里显示，也能被识图链路取出路径重发。
        room.messages.add(ChatMessage("user", filterNsfw(text, room.config.efficientMode), images = images))
        persist("对话已保存")
        // message_sent：用户消息落进课堂之后立刻分发（钩子只改 prompt，这里给插件一个「刚发生」的时机）。
        dispatchPluginEvent(
            PluginEventNames.MESSAGE_SENT,
            mapOf(
                "classroomId" to roomIndex.toString(),
                "classroomName" to room.name,
                "userText" to text.take(200),
                "messageCount" to room.messages.size.toString(),
            ),
        )
        isLoading = true
        // 新一轮开始：清掉上一轮可能残留的待附加图片，本轮 send_image 会重新往里写。
        turnImages.clear()
        scope.launch {
            val assistantIndex = room.messages.size
            room.messages.add(ChatMessage("assistant", ""))
            var streamed = ""
            val chatSystemPrompt = systemPrompt(room) + "\n" + EXAM_TOOL_PROMPT + "\n" + EXAM_TOOL_PROMPT_V2
            // 插件 prompt 钩子：真正发请求之前串行改写 system prompt。
            // 整段 runCatching 兜住——插件报错 / 超时 / 列表为空都退回原 prompt，绝不阻塞发送。
            // 钩子在 IO 线程执行（宿主能力的阻塞桥不能在主线程等）。
            val sendSystemPrompt = withContext(Dispatchers.IO) {
                runCatching {
                    val plugins = pluginManager.snapshot()
                    if (plugins.isEmpty()) {
                        chatSystemPrompt
                    } else {
                        val hooked = promptHookPipeline.apply(
                            plugins,
                            chatSystemPrompt,
                            PromptMeta(System.currentTimeMillis(), roomIndex, text),
                        )
                        hooked.skipped.forEach { skipped ->
                            Log.w(PLUGIN_LOG_TAG, "跳过 " + skipped.pluginId + " 的 prompt 钩子：" + skipped.reason)
                        }
                        if (hooked.appliedPluginIds.isNotEmpty()) {
                            Log.i(
                                PLUGIN_LOG_TAG,
                                "prompt 钩子生效 " + hooked.appliedPluginIds.joinToString(",") +
                                    " 尾段=" + hooked.prompt.takeLast(200),
                            )
                        }
                        hooked.prompt
                    }
                }.getOrElse { error ->
                    Log.w(PLUGIN_LOG_TAG, "prompt 钩子失败，改用原 prompt 发送", error)
                    chatSystemPrompt
                }
            }
            val onDelta: (String) -> Unit = { delta ->
                streamed += delta
                room.messages[assistantIndex] = ChatMessage("assistant", filterNsfw(stripExamBlock(streamed), room.config.efficientMode))
            }
            // 带图片的提交（考试手写 / 拍照答案）：图片挂在最后一条 user 消息上，走识图链路一次出结果。
            // 识图不可用（模型不支持视觉 / 图片读不出来）时退化成纯文字批改，绝不让整场考试因为图片而失败。
            val result = if (images.isNotEmpty()) {
                val visionPaths = images.map { imagePathOf(it) }.filter { it.isNotBlank() }
                val visionHistory = promptMessagesForRoom(room, room.messages.dropLast(2).toList())
                val vision = if (visionPaths.isEmpty()) "" else {
                    callVisionWithFallback(room.config, room.config.visionModels(), sendSystemPrompt, text, visionPaths, visionHistory)
                }
                if (visionPaths.isNotEmpty() && vision.isNotBlank() && !isApiFailure(vision)) {
                    vision
                } else {
                    val plain = callChatStreamWithFallback(room.config, activeModelChain, sendSystemPrompt, promptMessagesForRoom(room, room.messages.dropLast(1).toList()), onDelta = onDelta)
                    if (visionPaths.isNotEmpty()) "（图片答案未能识别，以下按文字作答批改）\n" + plain else plain
                }
            } else if (requestToolsJson == null) {
                // toolsJson 为空时走的仍是原来那条纯聊天通路，请求体与响应解析一字不改。
                callChatStreamWithFallback(room.config, activeModelChain, sendSystemPrompt, promptMessagesForRoom(room, room.messages.dropLast(1).toList()), onDelta = onDelta)
            } else {
                runToolLoop(room, sendSystemPrompt, requestToolsJson, onDelta)
            }
            // 文本格式的【考试】块只作兜底：本轮工具已经建好考试时不再采用，
            // 否则会把工具写入的参考解析（referenceAnswer）覆盖掉。
            val detectedExam = if (allowExamTrigger && examSession == null) detectExamSession(result, userRequestedExam = isExamRequest(text)) else null
            val visibleResult = stripExamBlock(result).ifBlank { if (detectedExam != null) "已为你准备好本次测试。" else result }
            // 本轮 send_image 攒下的图片跟着这条回复一起落定；只发图不打字时正文可能为空，这是合法的。
            val attachedImages = turnImages.drain(room.messages[assistantIndex].images)
            val finalText = if (attachedImages.isNotEmpty() && (visibleResult == NO_CONTENT_REPLY || visibleResult.isBlank())) "" else visibleResult
            room.messages[assistantIndex] = ChatMessage(
                "assistant",
                filterNsfw(finalText, room.config.efficientMode),
                images = attachedImages
            )
            detectedExam?.let { detected ->
                val snapshot = detected.withComposeState()
                examSession = snapshot
                saveExam(snapshot, "考试已保存", immediate = false)
                dispatchPluginEvent(
                    PluginEventNames.EXAM_STARTED,
                    mapOf(
                        "classroomId" to roomIndex.toString(),
                        "title" to snapshot.title,
                        "questionCount" to snapshot.questions.size.toString(),
                    ),
                )
            }
            isLoading = false
            persist("回复已保存，记忆将在后台整理")
            // assistant_replied：AI 回复已经写回消息并落盘，此刻才分发，插件读到的是最终文本。
            dispatchPluginEvent(
                PluginEventNames.ASSISTANT_REPLIED,
                mapOf(
                    "classroomId" to roomIndex.toString(),
                    "classroomName" to room.name,
                    "chars" to visibleResult.length.toString(),
                    "truncatedText" to visibleResult.take(200),
                    "messageCount" to room.messages.size.toString(),
                ),
            )
            if (room.config.ttsAutoRead && visibleResult.isNotBlank()) speakText(context, room.config, visibleResult)
            scheduleMemoryBuild(room, activeModel)
            if (isFirstConversation && room.name.matches(Regex("课堂 \\d+")) && room.messages.size >= 2) {
                val firstUser = room.messages.firstOrNull { it.role == "user" }?.text.orEmpty()
                val firstAssistant = room.messages.firstOrNull { it.role == "assistant" && it.text.isNotBlank() }?.text.orEmpty()
                generateClassroomIdentity(room.config, activeModelChain, firstUser, firstAssistant)?.let { (name, summary) ->
                    if (roomIndex in classes.indices && classes[roomIndex].name.matches(Regex("课堂 \\d+"))) {
                        replaceClassroomAt(roomIndex, classes[roomIndex].copy(name = name, summary = summary), "课堂名称和简介已自动生成")
                    }
                }
            }
        }
    }


    /**
     * 用户发图：先把选中的 uri 复制进 filesDir/images/（SAF 授权重启后会失效，不能只存 uri），
     * 再把这张图作为真正的图片附件挂进用户消息，最后走识图链路拿回复。
     * 用户额外打的字（input）作为问题一起发；没打字就用默认的看图请求。
     */
    fun sendImageMessage(uri: Uri) {
        if (isLoading) return
        val room = current
        val typed = input.trim()
        input = ""
        val question = typed.ifBlank { "请分析这张图片，并结合当前课堂内容解答。" }
        isLoading = true
        scope.launch {
            val saved = withContext(Dispatchers.IO) { saveUserImage(context, uri) }
            if (saved == null) {
                isLoading = false
                saveNotice = "图片读取失败，请重新选择照片"
                return@launch
            }
            room.messages.add(
                ChatMessage(
                    "user",
                    filterNsfw(question, room.config.efficientMode),
                    images = listOf(imageEntry(saved.absolutePath, ""))
                )
            )
            persist("图片问题已保存")
            val assistantIndex = room.messages.size
            room.messages.add(ChatMessage("assistant", ""))
            val system = systemPrompt(room) + "\n" + EXAM_TOOL_PROMPT + "\n" + EXAM_TOOL_PROMPT_V2
            // 带上下文：这一轮之前的对话一并给识图模型，图片本身走 vision 的 content 数组。
            val history = promptMessagesForRoom(room, room.messages.dropLast(2).toList())
            val result = callVisionWithFallback(
                room.config,
                room.config.visionModels(),
                system,
                question,
                listOf(saved.absolutePath),
                history
            )
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

    // 插件导入（设置页「⊕ 导入插件文件」）：SAF 选文件 → 读字节 → 解析校验 → 落盘注册。
    // 同 id 插件已存在时先问「覆盖 / 取消」；非法包只提示原因，不写盘、不影响启动。
    var pendingPluginConflict by remember { mutableStateOf<PluginImportResult.Conflict?>(null) }
    var pluginImportError by remember { mutableStateOf<String?>(null) }
    fun applyPluginImport(result: PluginImportResult) {
        when (result) {
            is PluginImportResult.Installed -> {
                pluginVersion++
                saveNotice = "插件「" + result.info.name + "」" + (if (result.replaced) "已覆盖生效" else "已导入并生效")
                Log.i(PLUGIN_LOG_TAG, "导入插件 " + result.info.id + " v" + result.info.version)
            }

            is PluginImportResult.Conflict -> pendingPluginConflict = result

            is PluginImportResult.Failed -> {
                saveNotice = "插件导入失败"
                pluginImportError = result.message
                Log.w(PLUGIN_LOG_TAG, "插件导入失败：" + result.message)
            }
        }
    }
    // 设置页改插件配置时的防抖句柄：连着打字只重建一次 runtime。
    var pendingPluginReload by remember { mutableStateOf<Job?>(null) }

    /** 设置页选了图片：复制进插件私有目录，路径写 prefs，再重建 runtime 让插件读到新值。 */
    fun applyPluginImagePick(pluginId: String, key: String, uri: Uri) {
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = context.contentResolver.openInputStream(uri)
                    if (stream == null) {
                        null
                    } else {
                        pluginManager.importPluginImage(pluginId, key, stream, extensionOf(context, uri))
                    }
                }.getOrNull()
            }
            if (saved == null) {
                saveNotice = "图片导入失败"
                return@launch
            }
            store.savePluginSetting(pluginId, key, saved.absolutePath)
            withContext(Dispatchers.IO) { pluginManager.reload(pluginId) }
            pluginManager.invalidate()
            pluginVersion++
            saveNotice = "背景图片已保存"
        }
    }
    val pluginImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                saveNotice = "正在导入插件…"
                val result = withContext(Dispatchers.IO) {
                    val bytes = runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                    if (bytes == null || bytes.isEmpty()) {
                        PluginImportResult.Failed("读取插件文件失败")
                    } else {
                        importPluginArchive(pluginManager, bytes, displayNameOf(context, uri), replace = false)
                    }
                }
                applyPluginImport(result)
            }
        }
    }
    pendingPluginConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { pendingPluginConflict = null },
            title = { Text("已存在同 id 插件") },
            text = { Text("插件「" + conflict.name + "」（" + conflict.pluginId + "）已经装好了。要用选中的文件覆盖它吗？") },
            confirmButton = {
                TextButton(onClick = {
                    val pending = conflict
                    pendingPluginConflict = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            importPluginArchive(pluginManager, pending.bytes, pending.sourceName, replace = true)
                        }
                        applyPluginImport(result)
                    }
                }) { Text("覆盖") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingPluginConflict = null
                    saveNotice = "已取消导入插件"
                }) { Text("取消") }
            }
        )
    }
    pluginImportError?.let { message ->
        AlertDialog(
            onDismissRequest = { pluginImportError = null },
            title = { Text("插件导入失败") },
            text = { Text(message + "\n\n插件没有被装载，其它功能不受影响。") },
            confirmButton = { TextButton(onClick = { pluginImportError = null }) { Text("知道了") } }
        )
    }
    // 网页工具的当面确认：域名白名单、敏感操作都由这个弹框接住用户的取舍。
    WebConsentDialog(webConsent)

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
            val chatHistory = promptMessagesForBranch(room, branch.context, branch.messages.dropLast(1).toList())
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
        colorScheme = appColorScheme(palette, darkInterface)
    ) {
    CompositionLocalProvider(LocalAppHazeState provides hazeState) {
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize().hazeSource(hazeState),
        topBar = { if (!immersiveMode) {
            // 与底栏一致的毛玻璃：内容从栏下透出，避免顶栏像一块死板的白条。
            // 用 TopHazeState 独立于 BottomHazeState —— 同一份 state 会在两处互相干扰模糊源。
            // 顶栏毛玻璃。注意：Scaffold 的 topBar 会占位，内容被推到它下方，
            // 所以这里采不到「背后内容」——真正的通透感来自底色半透明，
            // 再叠 hazeEffect 让状态栏区域（有内容经过时）产生模糊。
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(palette.surface.copy(alpha = 0.82f))
                    .hazeEffect(state = TopHazeState) {
                        blurRadius = 16.dp
                        backgroundColor = palette.surface.copy(alpha = 0.70f)
                        tints = listOf(HazeTint(palette.surface.copy(alpha = 0.20f)))
                        noiseFactor = 0.10f
                        fallbackTint = HazeTint(palette.surface.copy(alpha = 0.94f))
                    }
            ) {
                Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                    Row(
                        Modifier.fillMaxWidth().height(66.dp).padding(horizontal = DesignTokens.Spacing.Page),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Md)
                    ) {
                        Surface(
                            onClick = {
                                if (tab != Tab.Class) {
                                    // 不在主界面：先回课堂首页（连带清掉分支详情与二级菜单）
                                    tab = Tab.Class
                                    activeBranchIndex = -1
                                    classMenuOpen = false
                                } else {
                                    // 已在主界面：点它切出/收起二级菜单
                                    classMenuOpen = !classMenuOpen
                                }
                            },
                            modifier = Modifier.size(DesignTokens.Size.Tile),
                            shape = RoundedCornerShape(DesignTokens.Radius.Tile),
                            color = palette.card,
                            border = BorderStroke(DesignTokens.Border.Hairline, palette.outline)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.School,
                                    contentDescription = if (tab == Tab.Class) "打开课堂菜单" else "回到课堂首页",
                                    tint = palette.ink,
                                    modifier = Modifier.size(DesignTokens.Size.Icon)
                                )
                            }
                        }
                        // 标题列。Kicker（AI CLASSROOM）在宽度吃紧时让位：
                        // 插件按钮多起来以后它会和标题挤在一起，所以低于阈值就整行不画，标题独占。
                        BoxWithConstraints(Modifier.weight(1f)) {
                            val showKicker = maxWidth >= 150.dp
                            Column(Modifier.fillMaxWidth()) {
                                if (showKicker) {
                                    Text("AI CLASSROOM", style = DesignTokens.TextStyles.Kicker, color = palette.muted)
                                }
                                Text(
                                    if (tab == Tab.Class) current.name else tab.title,
                                    color = palette.ink,
                                    fontSize = DesignTokens.Type.Subtitle,
                                    fontWeight = DesignTokens.Weight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        // 「自定义学习内容」这类 topic 常驻在顶栏，和插件按钮抢宽度，已移除。
                        // 位置不够时的降级见标题列（BoxWithConstraints）。
                        // TOP_BAR_ACTIONS：插件按钮插在现有图标那一排的左侧（无插件时这里什么都不渲染）。
                        PluginUiTopBarActions(
                            bindings = uiTopBarBindings,
                            states = uiStateSnapshot,
                            palette = palette,
                            onEvent = onPluginUiEvent,
                            stateVersions = pluginStateVersions,
                            userImages = uiUserImages,
                            userImageFits = uiUserImageFits,
                        )
                        IconButton(
                            onClick = { classMenuOpen = !classMenuOpen },
                            modifier = Modifier.size(DesignTokens.Size.IconButton),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = DesignTokens.Accent)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = "打开课堂菜单", modifier = Modifier.size(DesignTokens.Size.Icon))
                        }
                    }
                    HorizontalDivider(thickness = DesignTokens.Border.Hairline, color = palette.outline)
                }
            }
        }
        },
        bottomBar = { if (!immersiveMode) {
            val panelShape = RoundedCornerShape(DesignTokens.Radius.Card)
            val panelColor = palette.surface
            val glassColor = palette.surface.copy(alpha = 0.70f)
            var dragTabIndex by remember { mutableStateOf(-1) }
            Box(
                Modifier
                    .fillMaxWidth()
                    // 加宽：左右 12dp → 4dp，面板更贴边（仍留悬浮余量，不做通栏）
                    .padding(start = DesignTokens.Spacing.Xs, end = DesignTokens.Spacing.Xs, bottom = DesignTokens.Spacing.Md)
                    // 阴影包边：先压一层浮起的影，再裁圆角
                    .shadow(DesignTokens.Shadow.Bar, panelShape)
                    .clip(panelShape)
                    .hazeEffect(state = BottomHazeState) {
                        blurRadius = 18.dp
                        backgroundColor = panelColor
                        tints = listOf(HazeTint(palette.surface.copy(alpha = 0.25f)))
                        noiseFactor = 0.10f
                        fallbackTint = HazeTint(Color.White.copy(alpha = if (dev.chrisbanes.haze.HazeDefaults.blurEnabled()) 0.28f else 0.80f))
                    }
                    .background(glassColor, panelShape)
                    .border(DesignTokens.Border.Hairline, palette.outline, panelShape)
                    .pointerInput(Unit) {
                        fun tabIndexAt(x: Float) =
                            ((x / size.width) * Tab.entries.size).toInt().coerceIn(0, Tab.entries.size - 1)
                        detectHorizontalDragGestures(
                            onDragStart = { pos -> dragTabIndex = tabIndexAt(pos.x) },
                            onHorizontalDrag = { change, _ -> dragTabIndex = tabIndexAt(change.position.x) },
                            onDragEnd = {
                                if (dragTabIndex >= 0) tab = Tab.entries[dragTabIndex]
                                dragTabIndex = -1
                            },
                            onDragCancel = { dragTabIndex = -1 }
                        )
                    }
            ) {
                NavigationBar(
                    // 收紧上下：默认 windowInsets 会在内容外再叠一层系统栏避让，把面板从 80dp 撑到约 117dp；归零后只留最小高
                    windowInsets = WindowInsets(0.dp),
                    // 两侧按钮：item 行两侧各留 8dp，长按放大 1.15 后的胶囊不会压到面板圆角
                    modifier = Modifier.padding(horizontal = DesignTokens.Spacing.Md),
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp
                ) {
                Tab.entries.forEachIndexed { index, item ->
                    val selected = if (dragTabIndex >= 0) dragTabIndex == index else tab == item
                    val iconScale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = when {
                            dragTabIndex >= 0 -> if (dragTabIndex == index) 1.15f else 1f
                            tab == item -> 1.06f
                            else -> 1f
                        },
                        animationSpec = if (dragTabIndex >= 0) {
                            tween(DesignTokens.Motion.FastMs, easing = androidx.compose.animation.core.LinearEasing)
                        } else {
                            tween(DesignTokens.Motion.BaseMs, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
                        },
                        label = "bottomTabIconScale"
                    )
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tab = item; classMenuOpen = false },
                                                icon = {
                            // 长按拖动才出现的高亮胶囊：圆角/描边取底栏面板同一套 token（Radius.Card + Border.Hairline + outline），平时不加任何底色
                            Box(
                                modifier = Modifier
                                    .then(
                                        if (selected && dragTabIndex >= 0) Modifier
                                            .background(palette.ink, RoundedCornerShape(DesignTokens.Radius.Card))
                                            .border(DesignTokens.Border.Hairline, palette.outline, RoundedCornerShape(DesignTokens.Radius.Card))
                                        else Modifier
                                    )
                                    .padding(horizontal = DesignTokens.Spacing.Md, vertical = DesignTokens.Spacing.Xs),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    item.icon,
                                    contentDescription = item.title,
                                    modifier = Modifier.size(DesignTokens.Size.IconLg * iconScale)
                                )
                            }
                        },
                                                label = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Xxs)
                            ) {
                                Text(
                                    item.title,
                                    fontSize = DesignTokens.Type.Caption,
                                    fontWeight = if (selected) DesignTokens.Weight.Bold else DesignTokens.Weight.Regular
                                )
                                // 正常选中的强调：label 下方一条短线；胶囊出现时它让位（高亮只画在图标上）
                                Box(
                                    Modifier
                                        .height(DesignTokens.Spacing.Xxs)
                                        .width(DesignTokens.Spacing.Xxxl)
                                        .background(
                                            if (selected && dragTabIndex < 0) DesignTokens.Accent else Color.Transparent,
                                            RoundedCornerShape(DesignTokens.Radius.Pill)
                                        )
                                )
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (selected && dragTabIndex >= 0) palette.page else palette.ink,
                            selectedTextColor = palette.ink,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = palette.muted,
                            unselectedTextColor = palette.muted
                        )
                    )
                }
                }
            }
        }
        }
    ) { padding ->
        // contentPadding 申报改动：底部避让归零，滚动内容透到悬浮毛玻璃底栏之下被折射；
        // 固定底元素（输入条/回底按钮）经 LocalBottomBarLift 抬升，绝对位置与改动前一致。
        val bottomLift = padding.calculateBottomPadding()
        CompositionLocalProvider(LocalBottomBarLift provides bottomLift) {
        Surface(
            Modifier
                .fillMaxSize()
                .hazeSource(BottomHazeState)
                .hazeSource(TopHazeState)
                .padding(
                    PaddingValues(
                        start = padding.calculateLeftPadding(LocalLayoutDirection.current),
                        top = padding.calculateTopPadding(),
                        end = padding.calculateRightPadding(LocalLayoutDirection.current)
                    )
                )
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
                    Tab.Class -> ClassScreen(current, input, { input = it }, isLoading, palette, jumpToMessageIndex, { jumpToMessageIndex = null }, current.config.reverseConversation, homeQuote,
                        savedExam = if (examSession == null) current.exam else null,
                        onResumeExam = { classes.getOrNull(classIndex)?.exam?.let { examSession = it.withComposeState() } },
                        onDeepThinkingChange = { enabled ->
                        replaceCurrent(current.copy(config = current.config.copy(deepThinkingEnabled = enabled)), if (enabled) "已启用深度思考" else "已关闭深度思考")
                    }, onModelChange = { model ->
                        replaceCurrent(current.copy(config = current.config.copy(activeChatModel = model)), "对话模型已切换")
                    }, onReasoningEffortChange = { effort ->
                        replaceCurrent(current.copy(config = current.config.copy(reasoningEffort = effort)), "推理强度已调整")
                    }, immersiveMode = immersiveMode, onImmersiveChange = { immersiveMode = it }, onQuickThemeToggle = {
                        val nextMode = if (darkInterface) "light" else "dark"
                        replaceCurrent(current.copy(config = current.config.copy(interfaceMode = nextMode)), if (nextMode == "dark") "已切换深色界面" else "已切换浅色界面")
                    }, onSend = { sendMessage() }, onImage = { imageLauncher.launch("image/*") }, onDeleteAfter = { index ->
                        if (index in current.messages.indices) {
                            for (i in current.messages.lastIndex downTo index) current.messages.removeAt(i)
                            current.chapters.clear()
                            persist("已删除后续对话")
                        }
                    }, onSpeak = { text ->
                        speakText(context, current.config, text)
                    }, pluginOverlay = uiOverlayBindings, pluginMessageTail = uiMessageTailBindings,
                        pluginStates = uiStateSnapshot, onPluginUiEvent = onPluginUiEvent,
                        pluginStateVersions = pluginStateVersions,
                        pluginUserImages = uiUserImages, pluginUserImageFits = uiUserImageFits) { index ->
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
                    Tab.Memory -> MemoryScreen(current.chapters, current.messages, current.examRecords) { index ->
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
                        examToolsEnabled = examToolsEnabled,
                        onExamToolsEnabledChange = { enabled ->
                            examToolsEnabled = enabled
                            store.saveExamToolsEnabled(enabled)
                            saveNotice = if (enabled) "已启用考试工具" else "已关闭考试工具"
                        },
                        fsToolsEnabled = fsToolsEnabled,
                        onFsToolsEnabledChange = { enabled ->
                            fsToolsEnabled = enabled
                            store.saveFsToolsEnabled(enabled)
                            saveNotice = if (enabled) "已启用文件工具" else "已关闭文件工具"
                        },
                        configToolsEnabled = configToolsEnabled,
                        onConfigToolsEnabledChange = { enabled ->
                            configToolsEnabled = enabled
                            store.saveConfigToolsEnabled(enabled)
                            saveNotice = if (enabled) "已启用配置工具" else "已关闭配置工具"
                        },
                        netToolsEnabled = netToolsEnabled,
                        onNetToolsEnabledChange = { enabled ->
                            netToolsEnabled = enabled
                            store.saveNetToolsEnabled(enabled)
                            saveNotice = if (enabled) "已允许 AI 调用网络" else "已关闭 AI 网络调用"
                        },
                        webActDomains = webActDomains,
                        onWebActDomainsChange = { domains ->
                            store.saveWebActDomains(domains)
                            webActDomains = store.loadWebActDomains()
                            saveNotice = if (webActDomains.isEmpty()) "网页操作白名单已清空" else "网页操作白名单已更新"
                        },
                        imageToolsEnabled = imageToolsEnabled,
                        onImageToolsEnabledChange = { enabled ->
                            imageToolsEnabled = enabled
                            store.saveImageToolsEnabled(enabled)
                            saveNotice = if (enabled) "已启用生图插件" else "已关闭生图插件"
                        },
                        mediaToolsEnabled = mediaToolsEnabled,
                        onMediaToolsEnabledChange = { enabled ->
                            mediaToolsEnabled = enabled
                            store.saveMediaToolsEnabled(enabled)
                            saveNotice = if (enabled) "已启用音乐插件" else "已关闭音乐插件"
                        },
                        videoToolsEnabled = videoToolsEnabled,
                        onVideoToolsEnabledChange = { enabled ->
                            videoToolsEnabled = enabled
                            store.saveVideoToolsEnabled(enabled)
                            saveNotice = if (enabled) "已启用视频工具" else "已关闭视频工具"
                        },
                        imageConfig = imageConfig,
                        onImageConfig = { updated ->
                            store.saveImageConfig(updated)
                            imageConfig = updated
                            saveNotice = "生图配置已保存"
                        },
                        customToolsJson = customToolsJson,
                        onCustomToolsJson = { json ->
                            // 写 prefs + 改 state：registry 依赖 customToolsJson，这一改就重建重注册，
                            // 回到课堂下一次对话模型就能看见这个新工具。
                            store.saveCustomTools(json)
                            customToolsJson = json
                            saveNotice = "自定义插件已保存，AI 下一次调用立即生效"
                        },
                        existingToolNames = { toolRegistry.all().map { it.name } },
                        imageSink = imageSink,
                        pluginInfos = pluginInfos,
                        onImportPlugin = {
                            // 显式列出可能的 MIME：部分系统的文件选择器在 */* 下只展示"最近"的图片，
                            // 导致用户根本选不到 .aip 文件。这里把 zip 类的常见 MIME 都列上。
                            // 插件包本质是 ZIP。自定义后缀 .aip 在多数系统的文件选择器里会被隐藏
                            // （媒体库不认识该类型），所以 .zip 是首选格式，两种后缀都接受。
                            pluginImportLauncher.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/octet-stream",
                                    "*/*",
                                )
                            )
                        },
                        onUninstallPlugin = { pluginId ->
                            scope.launch {
                                val removed = withContext(Dispatchers.IO) { pluginManager.uninstall(pluginId) }
                                pluginVersion++
                                saveNotice = if (removed) "插件已卸载" else "插件未装载"
                            }
                        },
                        onPluginEnabledChange = { pluginId, enabled ->
                            store.savePluginEnabled(pluginId, enabled)
                            // 开关只改快照过滤，不用重建 runtime：下一次快照就少/多这一项。
                            pluginManager.invalidate()
                            pluginVersion++
                            Log.i(
                                PLUGIN_LOG_TAG,
                                "插件 " + pluginId + (if (enabled) " 已启用" else " 已停用（钩子/事件/定时器/界面全部短路）"),
                            )
                            saveNotice = if (enabled) "已启用插件 $pluginId" else "已停用插件 $pluginId"
                        },
                        onPluginSettingChange = { pluginId, key, value ->
                            // 键值立刻落 prefs（关掉 app 也不丢）；runtime 重建推迟到停手之后，
                            // 否则每敲一个字符就要重开一次 Rhino Scope。
                            store.savePluginSetting(pluginId, key, value)
                            pendingPluginReload?.cancel()
                            pendingPluginReload = scope.launch {
                                delay(600)
                                val reloaded = withContext(Dispatchers.IO) { pluginManager.reload(pluginId) }
                                pluginManager.invalidate()
                                pluginVersion++
                                saveNotice = if (reloaded) "插件配置已保存，下一次调用立即生效" else "配置已保存，重新装载失败"
                            }
                        },
                        onPluginImagePick = { pluginId, key ->
                            pendingImagePick = { uri -> if (uri != null) applyPluginImagePick(pluginId, key, uri) }
                            pluginImageLauncher.launch("image/*")
                        },
                        onPluginImageClear = { pluginId, key ->
                            scope.launch {
                                withContext(Dispatchers.IO) { pluginManager.clearPluginImage(pluginId, key) }
                                store.savePluginSetting(pluginId, key, "")
                                pluginManager.invalidate()
                                pluginVersion++
                                saveNotice = "已清除图片"
                            }
                        },
                        onOpenManual = { showManualDialog = true },
                        // 彩蛋：设置页版本行连击 5 下重放开屏 CG
                        onReplaySplash = onReplaySplash,
                        onShowUpdate = { updateInfo = it },
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
                examSession?.let { session ->
                    ExamOverlay(session, current.messages, current.branches, palette, immersive = immersiveMode, onImmersiveChange = { immersiveMode = it }, onUpdate = { updated ->
                        // 作答 / 草稿是逐字输入的：内存立即同步，落盘走 300ms 防抖，避免每次按键都重写整份存档。
                        examSession = updated
                        saveExam(updated, "考试已保存", immediate = false)
                    }, onClose = {
                        // 退出考试只收起界面：存档留在课堂里，下次进来能接着做。
                        val kept = examSession
                        examSession = null
                        immersiveMode = false
                        saveExam(kept, "考试已存档，可随时继续")
                    }, onSubmit = { packed ->
                        // 提交：存档标记 submitted 后保留，便于回看；界面收起并把作答交给 AI 批改。
                        val submitted = examSession?.copy(submitted = true)
                        examSession = null
                        immersiveMode = false
                        if (submitted != null) archiveSubmittedExam(submitted)
                        // exam_submitted：先落盘再分发，插件读到的考试已经是 submitted 状态。
                        dispatchPluginEvent(
                            PluginEventNames.EXAM_SUBMITTED,
                            mapOf(
                                "classroomId" to classIndex.toString(),
                                "title" to (submitted?.title ?: ""),
                                "questionCount" to (submitted?.questions?.size ?: 0).toString(),
                                "unknownCount" to (submitted?.questions?.count { it.unknown } ?: 0).toString(),
                            ),
                        )
                        // 图片答案跟着这次提交一起发：每题的手写 / 拍照图排在自己的文字答案后面，
                        // 说明里带上题号，模型看图时能和题目对上号。
                        val answerImages = submitted?.questions.orEmpty().flatMapIndexed { index, question ->
                            question.answerImages.map { path -> imageEntry(path, "第" + (index + 1) + "题答案") }
                        }
                        sendMessage(packed, allowExamTrigger = false, images = answerImages)
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
    BlurOverlay(
        visible = classMenuOpen,
        hazeState = hazeState,
        enter = slideInHorizontally(animationSpec = tween(DesignTokens.Motion.DrawerEnterMs)) { -it } + fadeIn(tween(DesignTokens.Motion.SlowMs)),
        exit = slideOutHorizontally(animationSpec = tween(DesignTokens.Motion.DrawerExitMs)) { -it } + fadeOut(tween(DesignTokens.Motion.FastMs))
    ) {
        ClassroomMenu(classes, classIndex, saveNotice, palette, onHorizontalDragEnd = { total -> if (tab == Tab.Class && classMenuOpen && total < -80f) classMenuOpen = false }, onDismiss = { classMenuOpen = false }, onSelect = {
            val previousIndex = classIndex
            classIndex = it
            classMenuOpen = false
            persist("已切换课堂")
            // class_switched：只在真的换了课堂时分发。
            if (previousIndex != it) {
                dispatchPluginEvent(
                    PluginEventNames.CLASS_SWITCHED,
                    mapOf(
                        "fromClassroomId" to previousIndex.toString(),
                        "toClassroomId" to it.toString(),
                        "toClassroomName" to (classes.getOrNull(it)?.name ?: ""),
                    ),
                )
            }
        }, onRename = { index, name ->
            if (index in classes.indices) {
                replaceClassroomAt(index, classes[index].copy(name = name), "课堂名已保存")
            }
        }, onEditSummary = { index, summary ->
            if (index in classes.indices) replaceClassroomAt(index, classes[index].copy(summary = summary), "课堂简介已保存")
        }, onNew = { addClassroom() }, onNewWithConfig = { addClassroom(classes[it]) }, onCopyConfig = ::copyConfigFrom, onDelete = ::deleteClassroom)
    }
    ChatMenuOverlay(
        hazeState = hazeState,
        palette = palette,
        config = current.config,
        onModelChange = { model ->
            replaceCurrent(current.copy(config = current.config.copy(activeChatModel = model)), "对话模型已切换")
        },
        onDeepThinkingChange = { enabled ->
            replaceCurrent(current.copy(config = current.config.copy(deepThinkingEnabled = enabled)), if (enabled) "已启用深度思考" else "已关闭深度思考")
        },
        onReasoningEffortChange = { effort ->
            replaceCurrent(current.copy(config = current.config.copy(reasoningEffort = effort)), "推理强度已调整")
        }
    )
    // GLOBAL_OVERLAY：画在 Scaffold **之外**，才能覆盖顶栏和底栏。
    //
    // 以前它在课堂页内部（Scaffold 的 content 里），父容器只有消息区那么大 ——
    // 于是 bottom_right 锚点算出来永远偏上（实测音乐浮窗悬在屏幕中间），
    // 拖动也没法把它放到底栏附近。移到这一层后坐标就是整屏坐标。
    PluginUiOverlay(
        bindings = uiOverlayBindings,
        states = uiStateSnapshot,
        palette = palette,
        onEvent = onPluginUiEvent,
        stateVersions = pluginStateVersions,
        userImages = uiUserImages,
        userImageFits = uiUserImageFits,
    )

    // FULLSCREEN：最外层的最后一块，覆盖包括 GLOBAL_OVERLAY 在内的一切。
    // 同一时刻只有一个插件在这里（FullscreenArbiter 仲裁），右下角永远有宿主兜底退出按钮。
    // 沉浸模式就是靠它盖住悬浮窗的 —— 那时候音乐有自己的沉浸界面，不需要浮窗。
    uiFullscreenBinding?.let { binding ->
        PluginUiFullscreen(
            binding = binding,
            state = pluginStates[binding.pluginId],
            palette = palette,
            onEvent = onPluginUiEvent,
            onExit = {
                fullscreenArbiter.release(binding.pluginId)
                fullscreenEpoch++
            },
            stateVersion = pluginStateVersions[binding.pluginId] ?: 0,
            userImages = uiUserImages[binding.pluginId].orEmpty(),
            userImageFit = uiUserImageFits[binding.pluginId].orEmpty(),
        )
    }
    }
    }
    }
}

/** 毛玻璃浮层基座：全屏置顶容器，背后内容高斯轻模糊、无暗色蒙版；动画时长沿用 DesignTokens.Motion 三档。 */
@Composable
private fun BlurOverlay(
    visible: Boolean,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn(tween(DesignTokens.Motion.SlowMs)),
    exit: ExitTransition = fadeOut(tween(DesignTokens.Motion.FastMs)),
    content: @Composable () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val glassTint = surfaceColor.copy(alpha = 0.25f)
    val fallbackAlpha = if (dev.chrisbanes.haze.HazeDefaults.blurEnabled()) 0.28f else 0.80f
    AnimatedVisibility(
        visible = visible,
        enter = enter,
        exit = exit,
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .hazeEffect(state = hazeState) {
                    blurRadius = 18.dp
                    backgroundColor = surfaceColor
                    tints = listOf(HazeTint(glassTint))
                    noiseFactor = 0.10f
                    fallbackTint = HazeTint(Color.White.copy(alpha = fallbackAlpha))
                }
        ) {
            content()
        }
    }
}

@Composable
private fun ClassroomMenu(
    classes: List<Classroom>,
    classIndex: Int,
    saveNotice: String,
    palette: AppPalette,
    onHorizontalDragEnd: (Float) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onRename: (Int, String) -> Unit,
    onEditSummary: (Int, String) -> Unit,
    onNew: () -> Unit,
    onNewWithConfig: (Int) -> Unit,
    onCopyConfig: (Int) -> Unit,
    onDelete: (Int) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var summaryTarget by remember { mutableStateOf<Pair<Int, String>?>(null) }
    val filtered = classes.indices.filter { i ->
        val room = classes[i]
        query.isBlank() || room.name.contains(query, ignoreCase = true) || room.topic.contains(query, ignoreCase = true) || room.summary.contains(query, ignoreCase = true)
    }
    val glassColor = palette.surface.copy(alpha = 0.70f)
    // 沉浸：状态栏/导航栏的避让从面板本体挪到内层列表，面板才顶得到屏幕两端
    Row(Modifier.fillMaxSize()) {
        Surface(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.88f)
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            onHorizontalDragEnd(total)
                            total = 0f
                        },
                        onHorizontalDrag = { _, dragAmount -> total += dragAmount }
                    )
                },
            color = glassColor,
            shape = AppShapes.menu,
            shadowElevation = 0.dp,
            border = null
        ) {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 14.dp).statusBarsPadding().navigationBarsPadding(),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("我的课堂", fontWeight = DesignTokens.Weight.Bold, fontSize = DesignTokens.Type.Display, color = MaterialTheme.colorScheme.onSurface)
                            Text(saveNotice, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = DesignTokens.Type.Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = onNew, modifier = Modifier.size(DesignTokens.Size.IconButton), colors = IconButtonDefaults.iconButtonColors(contentColor = DesignTokens.Accent)) {
                            Icon(Icons.Default.Add, contentDescription = "新建", modifier = Modifier.size(DesignTokens.Size.Icon))
                        }
                    }
                    Spacer(Modifier.height(DesignTokens.Spacing.Lg))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(DesignTokens.Size.IconSm), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        placeholder = { Text("搜索课堂", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = DesignTokens.Type.Body) },
                        singleLine = true,
                        shape = AppShapes.control,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DesignTokens.Accent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            cursorColor = DesignTokens.Accent
                        )
                    )
                    Spacer(Modifier.height(DesignTokens.Spacing.Md))
                    AppPrimaryButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null, Modifier.size(DesignTokens.Size.IconSm))
                        Spacer(Modifier.width(DesignTokens.Spacing.Sm))
                        Text("新建课堂")
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "没有找到课堂",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = DesignTokens.Type.Body,
                            modifier = Modifier.padding(horizontal = DesignTokens.Spacing.Xs, vertical = DesignTokens.Spacing.Lg)
                        )
                    }
                }
                items(filtered, key = { it }) { i ->
                    val room = classes[i]
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (i == classIndex) DesignTokens.Accent.copy(alpha = 0.10f).compositeOn(MaterialTheme.colorScheme.surface) else Color.Transparent,
                        shape = AppShapes.card
                    ) {
                        Column(Modifier.padding(horizontal = DesignTokens.Spacing.Lg, vertical = DesignTokens.Spacing.Md)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = DesignTokens.Size.ListRowMin).clickable { onSelect(i) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Md)
                            ) {
                                Surface(
                                    Modifier.size(DesignTokens.Size.Tile),
                                    shape = RoundedCornerShape(DesignTokens.Radius.Tile),
                                    color = if (i == classIndex) DesignTokens.Accent else MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(DesignTokens.Border.Hairline, if (i == classIndex) DesignTokens.Accent else MaterialTheme.colorScheme.outline)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            (i + 1).toString(),
                                            color = if (i == classIndex) DesignTokens.AccentOn else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = DesignTokens.Type.Body,
                                            fontWeight = DesignTokens.Weight.Bold
                                        )
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(room.name, fontSize = DesignTokens.Type.Body, fontWeight = DesignTokens.Weight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { renameTarget = i to room.name })
                                    Text(
                                        room.summary.ifBlank { room.topic },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = DesignTokens.Type.Small,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable { summaryTarget = i to room.summary }
                                    )
                                }
                                if (i == classIndex) Icon(Icons.Default.Check, null, tint = DesignTokens.Accent, modifier = Modifier.size(DesignTokens.Size.Icon))
                            }
                            Spacer(Modifier.height(DesignTokens.Spacing.Sm))
                            Row(
                                Modifier.padding(start = DesignTokens.Size.Tile + DesignTokens.Spacing.Md),
                                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Xxs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val actionColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { renameTarget = i to room.name }, colors = actionColors) { Icon(Icons.Default.Edit, null, Modifier.size(DesignTokens.Size.IconSm)); Spacer(Modifier.width(DesignTokens.Spacing.Xs)); Text("改名", fontSize = DesignTokens.Type.Small) }
                                TextButton(onClick = { summaryTarget = i to room.summary }, colors = actionColors) { Text("简介", fontSize = DesignTokens.Type.Small) }
                                TextButton(onClick = { onNewWithConfig(i) }, colors = actionColors) { Text("复制", fontSize = DesignTokens.Type.Small) }
                                if (i != classIndex) TextButton(onClick = { onCopyConfig(i) }, colors = actionColors) { Text("复制配置", fontSize = DesignTokens.Type.Small) }
                                TextButton(onClick = { onDelete(i) }, colors = actionColors) { Icon(Icons.Default.Delete, null, Modifier.size(DesignTokens.Size.IconSm)); Text("删除", fontSize = DesignTokens.Type.Small) }
                            }
                        }
                    }
                }
            }
        }
        // 点空白处收回：面板右侧露出的那条背景带（与 ChatMenuOverlay 同一套行为）
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null
                ) { onDismiss() }
        )
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
    summaryTarget?.let { target ->
        EditClassroomSummaryDialog(
            initial = target.second,
            onClose = { summaryTarget = null },
            onSave = { summary ->
                onEditSummary(target.first, summary)
                summaryTarget = null
            }
        )
    }
}

@Composable
private fun RenameClassroomDialog(initial: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial) }
    AppDialogBase(
        onDismissRequest = onClose,
        title = { Text("修改课堂名字", fontWeight = FontWeight.Bold) },
        text = { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("课堂名字") }, singleLine = true) },
        confirmButton = { AppPrimaryButton(onClick = { onSave(name.trim().ifBlank { initial }) }) { Text("保存") } },
        dismissButton = { AppSecondaryButton(onClick = onClose) { Text("取消") } }
    )
}

@Composable
private fun EditClassroomSummaryDialog(initial: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    var summary by remember(initial) { mutableStateOf(initial) }
    AppDialogBase(
        onDismissRequest = onClose,
        title = { Text("修改课堂简介", fontWeight = FontWeight.Bold) },
        text = { OutlinedTextField(summary, { summary = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("一句话简介") }, minLines = 2, maxLines = 4) },
        confirmButton = { AppPrimaryButton(onClick = { onSave(summary.trim()) }) { Text("保存") } },
        dismissButton = { AppSecondaryButton(onClick = onClose) { Text("取消") } }
    )
}

/**
 * 课堂页顶部的「继续上次考试」入口：考试存档跟着课堂走，退出考试界面后靠它再进来接着做。
 * 统计口径与考试页一致——只有真正写了作答或标了「不会」的题才算已答。
 */
@Composable
private fun ExamResumeBanner(session: ExamSession, palette: AppPalette, onResume: () -> Unit, modifier: Modifier = Modifier) {
    val total = session.questions.size
    val answered = session.questions.count { it.unknown || it.answer.isNotBlank() || it.answerImages.isNotEmpty() }
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = DesignTokens.Spacing.Md, vertical = DesignTokens.Spacing.Md),
        shape = AppShapes.control,
        color = palette.card,
        contentColor = palette.ink,
        border = BorderStroke(DesignTokens.Border.Hairline, palette.outline),
        shadowElevation = 0.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = DesignTokens.Spacing.Lg, vertical = DesignTokens.Spacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Quiz, contentDescription = null, tint = palette.primary, modifier = Modifier.size(DesignTokens.Size.IconSm))
            Spacer(Modifier.width(DesignTokens.Spacing.Md))
            Column(Modifier.weight(1f)) {
                Text(
                    session.title.ifBlank { "上次的考试" },
                    fontWeight = DesignTokens.Weight.Bold,
                    fontSize = DesignTokens.Type.Body,
                    color = palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (session.submitted) "已提交 · 共 $total 题" else "已存档 · 已答 $answered 题 / 共 $total 题",
                    fontSize = DesignTokens.Type.Small,
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(DesignTokens.Spacing.Sm))
            AppPrimaryButton(onClick = onResume) { Text(if (session.submitted) "回看" else "继续") }
        }
    }
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
    homeQuote: String,
    savedExam: ExamSession?,
    onResumeExam: () -> Unit,
    onDeepThinkingChange: (Boolean) -> Unit,
    onModelChange: (String) -> Unit,
    onReasoningEffortChange: (String) -> Unit,
    immersiveMode: Boolean,
    onImmersiveChange: (Boolean) -> Unit,
    onQuickThemeToggle: () -> Unit,
    onSend: () -> Unit,
    onImage: () -> Unit,
    onDeleteAfter: (Int) -> Unit,
    onSpeak: (String) -> Unit,
    /** GLOBAL_OVERLAY 上的插件悬浮层（空列表 = 一个插件都没声明，渲染点直接不组合）。 */
    pluginOverlay: List<PluginUiBinding> = emptyList(),
    /** MESSAGE_BODY_TAIL 上的插件装饰（画在每条 AI 消息正文下方）。 */
    pluginMessageTail: List<PluginUiBinding> = emptyList(),
    /** 各插件 state 的 JSON 快照，供 `state.xxx` 取值。 */
    pluginStates: Map<String, JSONObject?> = emptyMap(),
    onPluginUiEvent: (PluginUiEvent) -> Unit = {},
    /** 每个插件自己的 state 版本号：变了就只重组它那一份节点。 */
    pluginStateVersions: Map<String, Int> = emptyMap(),
    /** 插件设置里 type=image 的字段（插件 id -> 字段 -> 文件）。 */
    pluginUserImages: Map<String, Map<String, File>> = emptyMap(),
    pluginUserImageFits: Map<String, Map<String, String>> = emptyMap(),
    onBranch: (Int) -> Unit
) {
    if (room.messages.isEmpty()) {
        // 空课堂也要能看到存档考试入口：存档可能在对话被清空之后仍然有效。
        Box(Modifier.fillMaxSize()) {
            EmptyClassroomHome(
                room = room,
                input = input,
                onInput = onInput,
                isLoading = isLoading,
                palette = palette,
                quote = homeQuote,
                onDeepThinkingChange = onDeepThinkingChange,
                onModelChange = onModelChange,
                onReasoningEffortChange = onReasoningEffortChange,
                immersiveMode = immersiveMode,
                onImmersiveChange = onImmersiveChange,
                onQuickThemeToggle = onQuickThemeToggle,
                onSend = onSend,
                onImage = onImage
            )
            savedExam?.let { archived ->
                ExamResumeBanner(archived, palette, onResumeExam, Modifier.align(Alignment.TopCenter))
            }
            // GLOBAL_OVERLAY 现在画在 Scaffold 之外（见主 Composable），这里不再重复绘制。
        }
        return
    }
    val scope = rememberCoroutineScope()
    val lastItemIndex = room.messages.size + 1
    val bottomIndex = if (reverseConversation) 0 else lastItemIndex
    // **首次进入直接落在最新一条**，不要「先渲染顶部、再动画滚到底」那一步 ——
    // 长对话里那一步特别明显：眼睛会看到列表从顶往底滑一次。
    // 反序布局（对话从下到上）的 0 就是底部，所以取 0。
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (reverseConversation) 0 else lastItemIndex,
    )
    val topIndex = if (reverseConversation) lastItemIndex else 0
    val isAtBottom by remember {
        derivedStateOf {
            // 与下方回底判定同一口径：只看 layoutInfo 快照，不用 messages.size（与布局不同源）。
            // 反序底部 = 最小 index（0），正序底部 = 最大 index（totalItemsCount-1），±1 容差。
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            visible.isEmpty() || if (reverseConversation) {
                visible.minOf { it.index } <= 1
            } else {
                visible.maxOf { it.index } >= info.totalItemsCount - 2
            }
        }
    }
    val compactInput by remember { derivedStateOf { !isAtBottom } }
    LaunchedEffect(room.messages.size) {
        if (room.messages.isEmpty()) return@LaunchedEffect
        // 仅当用户本来停在底部（或刚追加的是用户自己发的消息）才回底，浏览历史时不打断。
        // visibleItemsInfo/totalItemsCount 取自同一快照：正序末项=底部、反序 index 0=底部，
        // 故同一判定两种布局模式通用；±1 容差覆盖新项"已布局/未布局"两种时序。
        val info = listState.layoutInfo
        val visible = info.visibleItemsInfo
        val wasAtBottom = visible.isEmpty() || if (reverseConversation) {
            visible.minOf { it.index } <= 1
        } else {
            visible.maxOf { it.index } >= info.totalItemsCount - 2
        }
        if (wasAtBottom || room.messages.last().role == "user") {
            listState.animateScrollToItem(bottomIndex)
        }
    }
    LaunchedEffect(jumpToMessageIndex) {
        val target = jumpToMessageIndex ?: return@LaunchedEffect
        listState.animateScrollToItem((target + 1).coerceIn(0, lastItemIndex))
        onJumpHandled()
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, reverseLayout = reverseConversation, contentPadding = PaddingValues(top = 10.dp, bottom = (if (compactInput) 76.dp else 142.dp) + LocalBottomBarLift.current, start = 2.dp, end = 2.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(room.messages.size) { i ->
                MessageCard(
                    i, room.messages[i], palette, room.config.mentorName, onBranch, onDeleteAfter, onSpeak,
                    pluginMessageTail, pluginStates, onPluginUiEvent, pluginStateVersions,
                    pluginUserImages, pluginUserImageFits,
                )
            }
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
            roomConfig = room.config,
            onModelChange = onModelChange,
            onDeepThinkingChange = onDeepThinkingChange,
            onReasoningEffortChange = onReasoningEffortChange,
            immersiveMode = immersiveMode,
            onImmersiveChange = onImmersiveChange,
            onQuickThemeToggle = onQuickThemeToggle,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        val failureNotice by remember {
            derivedStateOf {
                val last = room.messages.lastOrNull()
                if (last?.role == "assistant" && looksLikeFailure(last.text)) last.text else null
            }
        }
        // 失败提示条**不能一直挂着**：它原来只在「最后一条是失败消息」时才消失，
        // 用户不发下一条就会永久占住屏幕顶部，很烦。
        // 现在给它 8 秒自动收起 + 手动关闭按钮 —— 只收提示条，失败消息本体仍在列表里，
        // 想回看往上翻还能看到原文。
        var failureDismissed by remember { mutableStateOf(false) }
        LaunchedEffect(failureNotice) {
            failureDismissed = false
            if (failureNotice != null) {
                delay(8_000)
                failureDismissed = true
            }
        }
        // 顶部提示位：存档考试入口与请求失败提示共用一列，避免两者互相遮挡。
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Sm)
        ) {
        savedExam?.let { archived ->
            ExamResumeBanner(archived, palette, onResumeExam)
        }
        if (!failureDismissed) failureNotice?.let { notice ->
            Surface(
                modifier = Modifier.padding(start = DesignTokens.Spacing.Md, end = DesignTokens.Spacing.Md, top = DesignTokens.Spacing.Lg),
                shape = AppShapes.control,
                color = palette.card,
                border = BorderStroke(DesignTokens.Border.Hairline, DesignTokens.Danger),
                shadowElevation = 0.dp
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = DesignTokens.Spacing.Lg, end = DesignTokens.Spacing.Sm, top = DesignTokens.Spacing.Md, bottom = DesignTokens.Spacing.Md),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Md),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Error, contentDescription = "请求失败", tint = DesignTokens.Danger, modifier = Modifier.size(DesignTokens.Size.Icon))
                    Text(
                        notice,
                        color = DesignTokens.Danger,
                        fontSize = DesignTokens.Type.Small,
                        fontWeight = DesignTokens.Weight.Medium,
                        lineHeight = DesignTokens.Type.LineSnug,
                        modifier = Modifier.weight(1f)
                    )
                    // 手动关闭：等不及 8 秒就用它。
                    IconButton(
                        onClick = { failureDismissed = true },
                        modifier = Modifier.size(DesignTokens.Size.IconLg),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭提示",
                            tint = palette.muted,
                            modifier = Modifier.size(DesignTokens.Size.IconSm),
                        )
                    }
                }
            }
        }
        }
        FloatingIconAction(
            icon = if (isAtBottom) Icons.Default.North else Icons.Default.South,
            contentDescription = if (isAtBottom) "回到顶部" else "回到底部",
            palette = palette,
            onClick = { scope.launch { if (isAtBottom) listState.animateScrollToItem(topIndex) else listState.animateScrollToItem(bottomIndex) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = DesignTokens.Spacing.Md, bottom = (if (compactInput) 122.dp else 188.dp) + LocalBottomBarLift.current),
        )
        // GLOBAL_OVERLAY 已提到 Scaffold 之外，这里不再绘制。
    }
}

@Composable
private fun EmptyClassroomHome(
    room: Classroom,
    input: String,
    onInput: (String) -> Unit,
    isLoading: Boolean,
    palette: AppPalette,
    quote: String,
    onDeepThinkingChange: (Boolean) -> Unit,
    onModelChange: (String) -> Unit,
    onReasoningEffortChange: (String) -> Unit,
    immersiveMode: Boolean,
    onImmersiveChange: (Boolean) -> Unit,
    onQuickThemeToggle: () -> Unit,
    onSend: () -> Unit,
    onImage: () -> Unit
) {
    val dateText = remember { todayDateText() }
    Box(Modifier.fillMaxSize().padding(horizontal = DesignTokens.Spacing.Lg, vertical = DesignTokens.Spacing.Xl)) {
        Column(Modifier.fillMaxWidth().align(Alignment.TopStart)) {
            Text(
                dateText.first,
                color = palette.muted,
                fontSize = DesignTokens.Type.Body,
                fontWeight = DesignTokens.Weight.Regular,
                modifier = Modifier
                    .background(palette.surface, RoundedCornerShape(DesignTokens.Radius.Sm))
                    .border(DesignTokens.Border.Hairline, palette.outline, RoundedCornerShape(DesignTokens.Radius.Sm))
                    .padding(horizontal = DesignTokens.Spacing.Lg, vertical = DesignTokens.Spacing.Sm)
            )
            if (quote.isNotBlank()) {
                Spacer(Modifier.height(DesignTokens.Spacing.Xl))
                Text(
                    quote,
                    color = palette.muted,
                    fontSize = DesignTokens.Type.Caption,
                    lineHeight = DesignTokens.Type.LineSnug
                )
            }
            Spacer(Modifier.height(DesignTokens.Spacing.Xl))
            Text(
                "在下方输入学习目标或问题，开始你的第一课",
                color = palette.ink,
                fontSize = DesignTokens.Type.Subtitle,
                fontWeight = DesignTokens.Weight.Medium,
                lineHeight = DesignTokens.Type.LineBody,
                modifier = Modifier.fillMaxWidth()
            )
        }
        ChatInputBar(
            input = input,
            onInput = onInput,
            isLoading = isLoading,
            compact = true,
            palette = palette,
            onSend = onSend,
            onImage = onImage,
            roomConfig = room.config,
            onModelChange = onModelChange,
            onDeepThinkingChange = onDeepThinkingChange,
            onReasoningEffortChange = onReasoningEffortChange,
            immersiveMode = immersiveMode,
            onImmersiveChange = onImmersiveChange,
            onQuickThemeToggle = onQuickThemeToggle,
            liftBottom = false,
            modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(top = 28.dp)
        )
    }
}

@Composable
private fun ChatControls(
    config: ClassroomConfig,
    palette: AppPalette,
    onModelChange: (String) -> Unit,
    onDeepThinkingChange: (Boolean) -> Unit,
    onReasoningEffortChange: (String) -> Unit,
    immersiveMode: Boolean,
    onImmersiveChange: (Boolean) -> Unit,
    onQuickThemeToggle: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Box {
            IconButton(onClick = { modelMenuOpen = true }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Memory, contentDescription = "切换模型", tint = palette.muted, modifier = Modifier.size(18.dp))
            }
        }
        Box {
            IconButton(onClick = { reasoningMenuOpen = true }, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = "推理设置",
                    tint = if (config.deepThinkingEnabled) palette.button else palette.muted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (immersiveMode) {
            IconButton(onClick = onQuickThemeToggle, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.ColorLens, contentDescription = "切换明暗", tint = palette.muted, modifier = Modifier.size(18.dp))
            }
        }
        IconButton(onClick = { onImmersiveChange(!immersiveMode) }, modifier = Modifier.size(34.dp)) {
            Icon(if (immersiveMode) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (immersiveMode) "退出沉浸模式" else "进入沉浸模式", tint = palette.muted, modifier = Modifier.size(18.dp))
        }
    }
}

/** 聊天控制条两个下拉的展开状态：提到文件级，供 ChatControls（触发端）与树内浮层（展示端）同读写。 */
private var modelMenuOpen by mutableStateOf(false)
private var reasoningMenuOpen by mutableStateOf(false)

/** 毛玻璃下拉浮层：全屏透明层（点空白处关闭）+ 贴近底栏的玻璃面板；与 BlurOverlay 同级挂在 Scaffold 的兄弟层，
 *  同窗口内经 hazeState 采样 Scaffold 根 hazeSource 的内容（Popup 是独立 window，采不到，故改为树内浮层）。 */
@Composable
private fun ChatMenuOverlay(
    hazeState: HazeState,
    palette: AppPalette,
    config: ClassroomConfig,
    onModelChange: (String) -> Unit,
    onDeepThinkingChange: (Boolean) -> Unit,
    onReasoningEffortChange: (String) -> Unit
) {
    val surfaceColor = palette.surface
    val blurEnabled = dev.chrisbanes.haze.HazeDefaults.blurEnabled()
    val models = config.chatModels().ifEmpty { listOf(config.selectedModel) }
    AnimatedVisibility(
        visible = modelMenuOpen || reasoningMenuOpen,
        enter = fadeIn(tween(DesignTokens.Motion.SlowMs)),
        exit = fadeOut(tween(DesignTokens.Motion.FastMs))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                    indication = null
                ) { modelMenuOpen = false; reasoningMenuOpen = false }
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 112.dp)
                    .clip(AppShapes.panel)
                    .hazeEffect(state = hazeState) {
                        blurRadius = 18.dp
                        backgroundColor = surfaceColor
                        tints = listOf(HazeTint(surfaceColor.copy(alpha = 0.25f)))
                        fallbackTint = HazeTint(surfaceColor.copy(alpha = if (blurEnabled) 0.28f else 0.80f))
                    }
                    .background(surfaceColor.copy(alpha = 0.70f))
                    .clickable(
                        interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                        indication = null
                    ) { }
                    .padding(vertical = 8.dp)
            ) {
                if (modelMenuOpen) {
                    Column {
                        Text("选择模型", color = palette.muted, fontSize = DesignTokens.Type.Small, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.shortModelName()) },
                                leadingIcon = { if (model == models.first()) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                onClick = { modelMenuOpen = false; onModelChange(model) }
                            )
                        }
                    }
                } else {
                    Column {
                        DropdownMenuItem(
                            text = { Text("深度思考") },
                            trailingIcon = { Switch(config.deepThinkingEnabled, onCheckedChange = null) },
                            onClick = { onDeepThinkingChange(!config.deepThinkingEnabled) }
                        )
                        if (config.deepThinkingEnabled) {
                            Text("推理强度", color = palette.muted, fontSize = DesignTokens.Type.Small, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            listOf("low" to "低", "medium" to "中", "high" to "高").forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    leadingIcon = { if (config.reasoningEffort == value) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    onClick = { onReasoningEffortChange(value); reasoningMenuOpen = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun String.shortModelName(): String = substringAfterLast('/').take(20)

@Composable
private fun FloatingIconAction(icon: ImageVector, palette: AppPalette, onClick: () -> Unit, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(DesignTokens.Size.Fab),
        color = palette.surface,
        shape = RoundedCornerShape(DesignTokens.Radius.Pill),
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(DesignTokens.Border.Hairline, palette.outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = DesignTokens.Accent, modifier = Modifier.size(DesignTokens.Size.IconLg))
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
    roomConfig: ClassroomConfig? = null,
    onModelChange: (String) -> Unit = {},
    onDeepThinkingChange: (Boolean) -> Unit = {},
    onReasoningEffortChange: (String) -> Unit = {},
    immersiveMode: Boolean = false,
    onImmersiveChange: (Boolean) -> Unit = {},
    onQuickThemeToggle: () -> Unit = {},
    liftBottom: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 6.dp).padding(bottom = if (liftBottom) LocalBottomBarLift.current else 0.dp),
        color = palette.surface,
        shape = AppShapes.panel,
        shadowElevation = 0.dp,
        border = null
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            if (roomConfig != null) ChatControls(roomConfig, palette, onModelChange, onDeepThinkingChange, onReasoningEffortChange, immersiveMode, onImmersiveChange, onQuickThemeToggle)
            Row(
            Modifier.fillMaxWidth(),
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.button,
                        contentColor = palette.onButton,
                        disabledContainerColor = palette.button.copy(alpha = 0.32f),
                        disabledContentColor = palette.onButton.copy(alpha = 0.85f)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = palette.onButton, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp), tint = palette.onButton)
                    }
                }
            }
        }
    }
}
}

/**
 * 消息图片的解码缓存。
 *
 * 为什么要这一层：像素钟那次闪烁就是「重组里重复解码」造成的。produceState(path) 只挡住
 * 同一处组合体内的重复解码，挡不住 LazyColumn 把条目回收后再滚回来——每次都要重新解码一张
 * 几 MB 的大图，界面就闪一下。这里按路径做 LRU（按位图字节数计量，上限 24MB），
 * 并且按目标边长做 inSampleSize 降采样，避免把 4096 见方的图整张读进内存。
 */
private object MessageImageLoader {
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    /** 已经在缓存里就直接返回（同步、极便宜），用来给 produceState 当初始值消除首帧空白。 */
    fun cached(path: String): ImageBitmap? = if (path.isBlank()) null else cache.get(path)

    fun load(path: String, maxSide: Int): ImageBitmap? {
        if (path.isBlank()) return null
        cache.get(path)?.let { return it }
        return runCatching {
            val file = File(path)
            if (!file.isFile || file.length() <= 0L) return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            if (bitmap == null) null else bitmap.asImageBitmap().also { cache.put(path, it) }
        }.getOrNull()
    }
}

/**
 * 消息里的图片附件渲染。
 *
 * 位图按「路径」缓存（见 [MessageImageLoader]）：同一条消息在反复重组、来回滚动时都不重新解码。
 * 解码在 IO 线程做，主线程只拿结果。点击任意一张进全屏查看（可双指缩放 / 拖动）。
 */
@Composable
private fun MessageImageAttachment(entry: String, palette: AppPalette, allowFullScreen: Boolean = true) {
    val path = imagePathOf(entry)
    val caption = imageCaptionOf(entry)
    val bitmap by produceState<ImageBitmap?>(initialValue = MessageImageLoader.cached(path), path) {
        value = if (path.isBlank()) null else withContext(Dispatchers.IO) { MessageImageLoader.load(path, MESSAGE_IMAGE_MAX_SIDE) }
    }
    var fullScreen by remember(path) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = DesignTokens.Spacing.Xs)) {
        val image = bitmap
        if (image == null) {
            // 文件被删 / 解码失败时给一行明确的占位，不留一块空白。
            Box(
                Modifier.fillMaxWidth().height(120.dp)
                    .background(palette.card, RoundedCornerShape(DesignTokens.Radius.Card))
                    .border(DesignTokens.Border.Hairline, palette.outline, RoundedCornerShape(DesignTokens.Radius.Card)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = palette.muted, modifier = Modifier.size(24.dp))
                    Text("图片已不存在或无法解码", color = palette.muted, fontSize = DesignTokens.Type.Small)
                }
            }
        } else {
            // 宽高比 = 宽/高，夹在 0.2–5 之间防止极端长条把气泡撑变形。
            val ratio = (image.width.toFloat() / image.height.toFloat()).coerceIn(0.2f, 5f)
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                val available = maxWidth
                // 先按「宽度填满」算高度；超过 320dp 上限就反过来按高度缩宽度，
                // 而不是留着宽高不变的框让 Fit 去补黑边。
                val filledHeight = available / ratio
                val width = if (filledHeight <= MESSAGE_IMAGE_MAX_HEIGHT) available else MESSAGE_IMAGE_MAX_HEIGHT * ratio
                val height = if (filledHeight <= MESSAGE_IMAGE_MAX_HEIGHT) filledHeight else MESSAGE_IMAGE_MAX_HEIGHT
                Image(
                    bitmap = image,
                    contentDescription = caption.ifBlank { "图片" },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(width)
                        .height(height)
                        .clip(RoundedCornerShape(DesignTokens.Radius.Card))
                        .clickable(enabled = allowFullScreen) { fullScreen = true }
                )
            }
        }
        if (caption.isNotBlank()) {
            Text(caption, color = palette.muted, fontSize = DesignTokens.Type.Small, lineHeight = 18.sp)
        }
    }
    if (fullScreen && bitmap != null) {
        ImageFullScreenDialog(path = path, palette = palette, onClose = { fullScreen = false })
    }
}

/**
 * 一条消息里的全部图片：单张铺满气泡按原比例；两张以上排成两列网格（每格方形、居中裁切），
 * 气泡本来就是窄栏，再横滑会挤，网格更好点选。
 */
@Composable
private fun MessageImages(images: List<String>, palette: AppPalette, allowFullScreen: Boolean = true) {
    if (images.isEmpty()) return
    if (images.size == 1) {
        MessageImageAttachment(images.first(), palette, allowFullScreen)
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Xs)) {
        images.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Xs)) {
                row.forEach { entry -> MessageImageThumbnail(entry, palette, allowFullScreen, Modifier.weight(1f)) }
                // 奇数张时把最后一格留空，保证两列对齐。
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 网格里的一格：方形裁切 + 说明文字放在格子下方（说明也参与网格，所以和方图同宽）。 */
@Composable
private fun MessageImageThumbnail(entry: String, palette: AppPalette, allowFullScreen: Boolean, modifier: Modifier = Modifier) {
    val path = imagePathOf(entry)
    val caption = imageCaptionOf(entry)
    val bitmap by produceState<ImageBitmap?>(initialValue = MessageImageLoader.cached(path), path) {
        value = if (path.isBlank()) null else withContext(Dispatchers.IO) { MessageImageLoader.load(path, MESSAGE_IMAGE_MAX_SIDE) }
    }
    var fullScreen by remember(path) { mutableStateOf(false) }
    Column(modifier) {
        val image = bitmap
        if (image == null) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f)
                    .background(palette.card, RoundedCornerShape(DesignTokens.Radius.Card))
                    .border(DesignTokens.Border.Hairline, palette.outline, RoundedCornerShape(DesignTokens.Radius.Card)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Image, contentDescription = null, tint = palette.muted, modifier = Modifier.size(20.dp))
            }
        } else {
            Image(
                bitmap = image,
                contentDescription = caption.ifBlank { "图片" },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(DesignTokens.Radius.Card))
                    .clickable(enabled = allowFullScreen) { fullScreen = true }
            )
        }
        if (caption.isNotBlank()) {
            Text(caption, color = palette.muted, fontSize = DesignTokens.Type.Small, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (fullScreen && bitmap != null) {
            ImageFullScreenDialog(path = path, palette = palette, onClose = { fullScreen = false })
        }
    }
}

/** 全屏看图：双指缩放 0.6–4 倍、单指拖动，点空白或右上角关闭。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageFullScreenDialog(path: String, palette: AppPalette, onClose: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(initialValue = MessageImageLoader.cached(path), path) {
        value = withContext(Dispatchers.IO) { MessageImageLoader.load(path, FULLSCREEN_IMAGE_MAX_SIDE) }
    }
    var scale by remember(path) { mutableStateOf(1f) }
    var pan by remember(path) { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onClose) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f))
                .pointerInput(path) {
                    detectTransformGestures { _, panChange, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.6f, 4f)
                        pan += panChange
                    }
                }
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            val image = bitmap
            if (image == null) {
                CircularProgressIndicator(color = palette.onButton, strokeWidth = 2.dp)
            } else {
                Image(
                    bitmap = image,
                    contentDescription = "查看图片",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y }
                )
            }
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                Text("关闭", color = Color.White)
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
    onSpeak: (String) -> Unit,
    pluginMessageTail: List<PluginUiBinding> = emptyList(),
    pluginStates: Map<String, JSONObject?> = emptyMap(),
    onPluginUiEvent: (PluginUiEvent) -> Unit = {},
    pluginStateVersions: Map<String, Int> = emptyMap(),
    pluginUserImages: Map<String, Map<String, File>> = emptyMap(),
    pluginUserImageFits: Map<String, Map<String, String>> = emptyMap(),
) {
    var menuOpen by remember { mutableStateOf(false) }
    val bubbleUser = if (palette.page == DesignTokens.Dark.Page) DesignTokens.Dark.BubbleUser else DesignTokens.Light.BubbleUser
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
                    .background(bubbleUser, RoundedCornerShape(DesignTokens.Radius.Card))
                    .padding(horizontal = DesignTokens.Spacing.CardHorizontal, vertical = DesignTokens.Spacing.CardVertical)
            ) {
                Text("YOU", style = DesignTokens.TextStyles.Kicker, color = palette.muted)
                Spacer(Modifier.height(DesignTokens.Spacing.Xs))
                if (message.text.isNotBlank()) Text(message.text, style = DesignTokens.TextStyles.Body, color = palette.ink)
                MessageImages(message.images, palette)
                TextButton(onClick = { onBranch(index) }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) { Text("↳ 从这里开分支", color = palette.secondary, fontSize = DesignTokens.Type.Small) }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().then(actionModifier).padding(horizontal = DesignTokens.Spacing.Xxs), horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Md), verticalAlignment = Alignment.Top) {
            Surface(Modifier.size(32.dp), shape = RoundedCornerShape(DesignTokens.Radius.Button), color = palette.button) {
                Box(contentAlignment = Alignment.Center) { Text("✦", color = palette.onButton, fontSize = DesignTokens.Type.Subtitle, fontWeight = FontWeight.Bold) }
            }
            Column(Modifier.weight(1f)) {
                Text(mentorName.ifBlank { "AI 讲师" }, style = DesignTokens.TextStyles.Kicker, color = palette.muted)
                Spacer(Modifier.height(DesignTokens.Spacing.Xs))
                Column(
                    Modifier.fillMaxWidth()
                        .background(palette.surface, RoundedCornerShape(DesignTokens.Radius.Card))
                        .border(DesignTokens.Border.Hairline, palette.outline, RoundedCornerShape(DesignTokens.Radius.Card))
                        .padding(horizontal = DesignTokens.Spacing.CardHorizontal, vertical = DesignTokens.Spacing.CardVertical)
                ) {
                    if (message.text.isNotBlank()) MarkdownText(message.text)
                    // AI 用 send_image 附加的图片，排在正文下方、插件装饰之上。
                    MessageImages(message.images, palette)
                    // MESSAGE_BODY_TAIL：每条 AI 消息正文下方的插件装饰（无插件时不组合、不建对象）。
                    if (pluginMessageTail.isNotEmpty()) {
                        val messageContext = remember(message.text, index) {
                            JSONObject()
                                .put("chars", message.text.length)
                                .put("index", index)
                                .put("role", message.role)
                        }
                        PluginUiMessageTail(
                            bindings = pluginMessageTail,
                            states = pluginStates,
                            message = messageContext,
                            palette = palette,
                            onEvent = onPluginUiEvent,
                            stateVersions = pluginStateVersions,
                            userImages = pluginUserImages,
                            userImageFits = pluginUserImageFits,
                        )
                    }
                }
                TextButton(onClick = { onBranch(index) }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) { Text("↳ 从这里开分支", color = palette.secondary, fontSize = DesignTokens.Type.Small) }
            }
        }
    }
        val clipboard = LocalClipboardManager.current
        MessageActionMenu(
            expanded = menuOpen,
            palette = palette,
            onDismiss = { menuOpen = false },
            onCopy = { clipboard.setText(AnnotatedString(message.text)) },
            onBranch = { onBranch(index) },
            onDeleteAfter = { onDeleteAfter(index) },
        )
    }
}

/** 根 hazeState 共享实例：长按菜单浮层需与 Scaffold 根 hazeSource 同源采样，而消息卡片组件内无法取到根作用域状态。 */
private val SharedHazeState = HazeState()
/**
 * 底栏专用采样源。
 * 底栏是 Scaffold（即 SharedHazeState 的 hazeSource 节点）的子孙节点——在 source 节点内部再挂 hazeEffect，
 * 采到的是自己而非背后内容，haze 输出为空（实测：栏下内容仅按面板 alpha 0.70 透出 30%，锐度与栏上完全一致）。
 * 故对内容层单独开一个 source：内容层绘制顺序先于底栏、且不是底栏的祖先，采样成立。
 * 菜单/浮层仍用 SharedHazeState，保持对顶栏与底栏的整屏模糊。
 */
private val BottomHazeState = HazeState()

/**
 * 顶栏专用采样源。理由同 [BottomHazeState]：顶栏也是 Scaffold（SharedHazeState 的 hazeSource）的子孙，
 * 直接复用会采到自己。顶栏与底栏各开一个独立 source，互不干扰。
 */
private val TopHazeState = HazeState()



/** 展示层失败识别：覆盖数据层实际产出的失败文案，用于给失败消息套错误样式；命中时原样展示消息内容。 */

@Composable
private fun ReleaseNotesDialog(palette: AppPalette, onClose: () -> Unit) {
    AppDialogBase(
        onDismissRequest = onClose,
        title = { Text("New!", fontWeight = FontWeight.Bold) },
        text = {
            // 整份说明是一个不可分割的 Markdown 块，用 LazyColumn 的单 item 会被直接裁掉；
            // 改为可滚动的 Column，长文案才能滑动查看。
            Column(Modifier.height(360.dp).verticalScroll(rememberScrollState())) {
                MarkdownText(RELEASE_NOTES_TEXT, color = palette.ink)
            }
        },
        confirmButton = { AppPrimaryButton(onClick = onClose) { Text("知道了") } }
    )
}

@Composable
private fun UpdateDialog(info: UpdateInfo, onClose: () -> Unit) {
    AppDialogBase(
        onDismissRequest = onClose,
        title = { Text("发现新版本", fontWeight = FontWeight.Bold) },
        text = {
            // 同样需要可滚动：更新说明一长就超出弹窗高度。
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(info.name.ifBlank { info.version }, fontWeight = FontWeight.Bold)
                Text("GitHub Release 已发布更新，可前往仓库下载新版 APK。", color = Muted)
                if (info.notes.isNotBlank()) MarkdownText(info.notes)
                Text(info.url, color = MaterialTheme.colorScheme.primary, fontSize = DesignTokens.Type.Small)
            }
        },
        confirmButton = { AppPrimaryButton(onClick = onClose) { Text("知道了") } }
    )
}

@Composable
private fun UserManualDialog(palette: AppPalette, onClose: () -> Unit) {
    AppDialogBase(
        onDismissRequest = onClose,
        title = { Text("使用手册", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.height(460.dp).verticalScroll(rememberScrollState())) {
                MarkdownText(USER_MANUAL_TEXT, color = palette.ink)
            }
        },
        confirmButton = { AppPrimaryButton(onClick = onClose) { Text("关闭") } }
    )
}

@Composable
private fun ExamOverlay(
    session: ExamSession,
    messages: List<ChatMessage>,
    branches: List<BranchClass>,
    palette: AppPalette,
    immersive: Boolean,
    onImmersiveChange: (Boolean) -> Unit,
    onUpdate: (ExamSession) -> Unit,
    onClose: () -> Unit,
    onSubmit: (String) -> Unit,
    onUnknown: (Int, ExamQuestion) -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var sidePanel by remember { mutableStateOf<String?>(null) }
    var draftMode by remember { mutableStateOf("text") }
    val strokes = remember { mutableStateListOf<DrawStroke>() }
    // 横屏两栏占比：题目栏权重可拖动，范围 30%～80%（草稿纸因此恒有 20%～70%），不会再被拖没。
    var listFraction by remember { mutableFloatStateOf(0.58f) }
    // 草稿纸全屏：进入前记住当时的占比，双击退出时按原值还原。
    var panelFullscreen by remember { mutableStateOf(false) }
    var fractionBeforeFullscreen by remember { mutableFloatStateOf(0.58f) }
    LaunchedEffect(isLandscape) {
        if (!isLandscape) {
            sidePanel = null
            panelFullscreen = false
            onImmersiveChange(false)
        }
    }
    Surface(Modifier.fillMaxSize(), color = palette.page) {
        Box(Modifier.fillMaxSize()) {
            val openPanel = sidePanel
            if (isLandscape && openPanel != null) {
                BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = DesignTokens.Spacing.Lg, vertical = DesignTokens.Spacing.Md)) {
                    val rowWidthPx = with(LocalDensity.current) { maxWidth.toPx() }.coerceAtLeast(1f)
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Md)) {
                        if (!panelFullscreen) {
                            ExamQuestionList(session, Modifier.weight(listFraction.coerceIn(0.30f, 0.80f)).fillMaxHeight(), palette, true, immersive, openPanel, panelFullscreen = false, onTogglePanelFullscreen = {}, onSidePanel = { sidePanel = if (sidePanel == it) null else it }, onClose, onSubmit, onUpdate, onUnknown)
                        }
                        // 分界线：12dp 命中区 + 1dp 细线 + 三个抓握点；只有这一小块拦截触摸，不影响两栏滚动。
                        Box(
                            Modifier
                                .width(DesignTokens.Spacing.Lg)
                                .fillMaxHeight()
                                .pointerInput(rowWidthPx, panelFullscreen, fractionBeforeFullscreen) {
                                    detectDragGestures(
                                        onDragStart = {
                                            if (panelFullscreen) {
                                                panelFullscreen = false
                                                listFraction = fractionBeforeFullscreen
                                            }
                                        }
                                    ) { change, drag ->
                                        change.consume()
                                        listFraction = (listFraction + drag.x / rowWidthPx).coerceIn(0.30f, 0.80f)
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(onDoubleTap = {
                                        if (panelFullscreen) {
                                            panelFullscreen = false
                                            listFraction = fractionBeforeFullscreen
                                        } else {
                                            fractionBeforeFullscreen = listFraction
                                            panelFullscreen = true
                                        }
                                    })
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .width(DesignTokens.Border.Hairline)
                                    .fillMaxHeight(0.72f)
                                    .background(palette.outline, RoundedCornerShape(DesignTokens.Radius.Pill))
                            )
                            Column(
                                verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Xs),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                repeat(3) {
                                    Box(
                                        Modifier
                                            .size(DesignTokens.Spacing.Xs)
                                            .background(palette.muted.copy(alpha = 0.55f), RoundedCornerShape(DesignTokens.Radius.Pill))
                                    )
                                }
                            }
                        }
                        ExamSidePanel(openPanel, session, messages, branches, draftMode, palette, panelFullscreen = panelFullscreen, onTogglePanelFullscreen = {
                            if (panelFullscreen) {
                                panelFullscreen = false
                                listFraction = fractionBeforeFullscreen
                            } else {
                                fractionBeforeFullscreen = listFraction
                                panelFullscreen = true
                            }
                        }, onDraftMode = { draftMode = it }, strokes, onUpdate = onUpdate, modifier = Modifier.weight(if (panelFullscreen) 1f else (1f - listFraction).coerceIn(0.20f, 0.70f)).fillMaxHeight().padding(bottom = LocalBottomBarLift.current))
                    }
                }
            } else if (isLandscape) {
                ExamQuestionList(session, Modifier.fillMaxSize().padding(horizontal = DesignTokens.Spacing.Lg, vertical = DesignTokens.Spacing.Md), palette, true, immersive, null, panelFullscreen = false, onTogglePanelFullscreen = {}, onSidePanel = { sidePanel = if (sidePanel == it) null else it }, onClose, onSubmit, onUpdate, onUnknown)
            } else {
                ExamQuestionList(session, Modifier.fillMaxSize().padding(DesignTokens.Spacing.Lg), palette, false, false, null, panelFullscreen = false, onTogglePanelFullscreen = {}, onSidePanel = {}, onClose, onSubmit, onUpdate, onUnknown)
            }
            // 横屏沉浸开关：贴右下角的小悬浮键。沉浸时顶栏/底栏/提交条都收起，它必须留着自己以便退出。
            if (isLandscape) {
                val toggleBottom = if (immersive) {
                    DesignTokens.Spacing.Lg
                } else {
                    DesignTokens.Size.ButtonHeightLarge + DesignTokens.Spacing.Xxl
                }
                // 恒定贴右下角：侧栏展开时右下角是草稿纸的空白绘图区，既不压题目文字，
                // 也不压草稿纸输入框（它在左上方），更不压分界线（那是拖动热区）。
                Surface(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(start = DesignTokens.Spacing.Lg, end = DesignTokens.Spacing.Lg, bottom = toggleBottom + LocalBottomBarLift.current)
                        .size(DesignTokens.Size.IconButton),
                    shape = RoundedCornerShape(DesignTokens.Radius.Pill),
                    color = palette.surface.copy(alpha = 0.72f),
                    border = BorderStroke(DesignTokens.Border.Hairline, palette.outline.copy(alpha = 0.7f))
                ) {
                    Box(
                        Modifier.fillMaxSize().clickable { onImmersiveChange(!immersive) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (immersive) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (immersive) "退出沉浸模式" else "进入沉浸模式",
                            tint = if (immersive) palette.primary else palette.muted,
                            modifier = Modifier.size(DesignTokens.Size.IconSm)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamQuestionList(
    session: ExamSession,
    modifier: Modifier,
    palette: AppPalette,
    isLandscape: Boolean,
    immersive: Boolean,
    sidePanel: String?,
    panelFullscreen: Boolean,
    onTogglePanelFullscreen: () -> Unit,
    onSidePanel: (String) -> Unit,
    onClose: () -> Unit,
    onSubmit: (String) -> Unit,
    onUpdate: (ExamSession) -> Unit,
    onUnknown: (Int, ExamQuestion) -> Unit
) {
    val total = session.questions.size
    // 已答口径：考生真写了作答、交了手写/拍照图片、或标了「不会」的题才算；AI 给的参考解析（referenceAnswer）不计入。
    val answered = session.questions.count { it.unknown || it.answer.isNotBlank() || it.answerImages.isNotEmpty() }
    val answeredFraction = if (total == 0) 0f else answered.toFloat() / total.toFloat()
    // 图片答案的四个入口共用一套 launcher：拍照 / 相册的回调只能注册在稳定的位置上，
    // 放进 LazyColumn 的每一项里会在滚动时被回收，结果就回不来。
    val examContext = LocalContext.current
    val examScope = rememberCoroutineScope()
    val liveSession by rememberUpdatedState(session)
    val liveUpdate by rememberUpdatedState(onUpdate)
    var cameraTarget by remember { mutableIntStateOf(-1) }
    var galleryTarget by remember { mutableIntStateOf(-1) }
    var pendingPhotoPath by remember { mutableStateOf<String?>(null) }
    var handwritingIndex by remember { mutableIntStateOf(-1) }
    var previewPath by remember { mutableStateOf<String?>(null) }

    fun notice(text: String) {
        Toast.makeText(examContext, text, Toast.LENGTH_SHORT).show()
    }

    // 图片答案挂在题目上：改一张就整份回写一次，和文字作答走同一条存档通路。
    fun applyAnswerImages(index: Int, transform: (List<String>) -> List<String>) {
        val live = liveSession
        val question = live.questions.getOrNull(index) ?: return
        live.questions[index] = question.copy(answerImages = transform(question.answerImages))
        liveUpdate(live.copy(questions = live.questions))
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val path = pendingPhotoPath
        val index = cameraTarget
        pendingPhotoPath = null
        cameraTarget = -1
        if (path == null) {
            notice("拍照失败，请重试")
        } else if (!saved) {
            // 用户在相机里取消：把占位文件删掉，不留下垃圾图。
            deleteExamImage(path)
        } else {
            examScope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { normalizeExamImage(File(path)) }.getOrDefault(false) }
                if (ok) {
                    applyAnswerImages(index) { it + path }
                    notice("照片已添加")
                } else {
                    deleteExamImage(path)
                    notice("照片处理失败，请重拍")
                }
            }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val index = galleryTarget
        galleryTarget = -1
        // uri == null 是用户取消选择，什么都不做，不弹提示。
        if (uri != null && index >= 0) {
            examScope.launch {
                val file = withContext(Dispatchers.IO) { importExamImage(examContext, uri) }
                if (file != null) {
                    applyAnswerImages(index) { it + file.absolutePath }
                    notice("照片已添加")
                } else {
                    notice("图片读取失败，请重新选择")
                }
            }
        }
    }
    // 横屏高度吃紧：卡片纵向内边距从 13dp 收到 8dp，两张卡就能省出约 55px 给题卡正文。
    val examCardPadding = PaddingValues(
        horizontal = DesignTokens.Spacing.CardHorizontal,
        vertical = if (isLandscape) DesignTokens.Spacing.Md else DesignTokens.Spacing.CardVertical
    )
    @Composable
    fun ExamCard(content: @Composable ColumnScope.() -> Unit) {
        Surface(
            Modifier.fillMaxWidth(),
            shape = AppShapes.card,
            color = palette.surface,
            contentColor = palette.ink,
            border = BorderStroke(DesignTokens.Border.Hairline, palette.outline)
        ) {
            Column(Modifier.padding(examCardPadding), content = content)
        }
    }
    // 列表与提交条改为纵向占位布局：按钮作为正常子项占住自己的高度，
    // 不再叠在列表之上，题目文字因此永远不会被按钮遮住。
    Column(modifier) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = if (isLandscape) 0.dp else DesignTokens.Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) DesignTokens.Spacing.Xs else DesignTokens.Spacing.Lg)
        ) {
            item {
                InfoCard {
                    // 横屏可用高度只有约 392dp，草稿纸/总览入口并到标题行；栏宽被拖窄时自动换成第二行，避免标题被挤成竖排。
                    BoxWithConstraints {
                        val narrowHeader = maxWidth < 430.dp
                        val panelButtonPadding = PaddingValues(horizontal = DesignTokens.Spacing.Md, vertical = DesignTokens.Spacing.Xs)
                        Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Quiz, null, tint = palette.primary, modifier = Modifier.size(DesignTokens.Size.Icon))
                            Spacer(Modifier.width(DesignTokens.Spacing.Md))
                            if (isLandscape) {
                                // 横屏高度只有约 392dp：标题与进度并成一行、且不再画进度条，把省下的高度全给题目卡。
                                Text(session.title, fontWeight = DesignTokens.Weight.Bold, fontSize = DesignTokens.Type.Subtitle, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(DesignTokens.Spacing.Sm))
                                Text("已答 $answered 题 / 共 $total 题", fontSize = DesignTokens.Type.Small, color = palette.muted, maxLines = 1)
                                Spacer(Modifier.width(DesignTokens.Spacing.Sm))
                            } else {
                                Column(Modifier.weight(1f)) {
                                    Text(session.title, fontWeight = DesignTokens.Weight.Bold, fontSize = DesignTokens.Type.Headline, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(DesignTokens.Spacing.Xxs))
                                    Text("已答 $answered 题 / 共 $total 题", fontSize = DesignTokens.Type.Small, color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (isLandscape && !narrowHeader) {
                                OutlinedButton(onClick = { onSidePanel("draft") }, contentPadding = panelButtonPadding) {
                                    Text(if (sidePanel == "draft") "关闭草稿纸" else "打开草稿纸", fontSize = DesignTokens.Type.Small)
                                }
                                Spacer(Modifier.width(DesignTokens.Spacing.Sm))
                                OutlinedButton(onClick = { onSidePanel("overview") }, contentPadding = panelButtonPadding) {
                                    Text(if (sidePanel == "overview") "关闭总览" else "打开总览", fontSize = DesignTokens.Type.Small)
                                }
                                if (sidePanel != null) {
                                    Spacer(Modifier.width(DesignTokens.Spacing.Xs))
                                    IconButton(onClick = onTogglePanelFullscreen, modifier = Modifier.size(DesignTokens.Size.IconButton)) {
                                        Icon(
                                            if (panelFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                            contentDescription = if (panelFullscreen) "退出草稿纸全屏" else "草稿纸全屏",
                                            tint = palette.muted,
                                            modifier = Modifier.size(DesignTokens.Size.IconSm)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(DesignTokens.Spacing.Xs))
                            }
                            TextButton(onClick = onClose) { Text("退出") }
                        }
                        if (isLandscape && narrowHeader) {
                            Spacer(Modifier.height(DesignTokens.Spacing.Sm))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Sm)) {
                                OutlinedButton(onClick = { onSidePanel("draft") }, contentPadding = panelButtonPadding) {
                                    Text(if (sidePanel == "draft") "关闭草稿纸" else "打开草稿纸", fontSize = DesignTokens.Type.Small)
                                }
                                OutlinedButton(onClick = { onSidePanel("overview") }, contentPadding = panelButtonPadding) {
                                    Text(if (sidePanel == "overview") "关闭总览" else "打开总览", fontSize = DesignTokens.Type.Small)
                                }
                                if (sidePanel != null) {
                                    IconButton(onClick = onTogglePanelFullscreen, modifier = Modifier.size(DesignTokens.Size.IconButton)) {
                                        Icon(
                                            if (panelFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                            contentDescription = if (panelFullscreen) "退出草稿纸全屏" else "草稿纸全屏",
                                            tint = palette.muted,
                                            modifier = Modifier.size(DesignTokens.Size.IconSm)
                                        )
                                    }
                                }
                            }
                        }
                        if (!isLandscape) {
                            Spacer(Modifier.height(DesignTokens.Spacing.Md))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(DesignTokens.Border.Selected)
                                    .background(palette.outline, RoundedCornerShape(DesignTokens.Radius.Pill))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(answeredFraction)
                                        .fillMaxHeight()
                                        .background(palette.primary, RoundedCornerShape(DesignTokens.Radius.Pill))
                                )
                            }
                        }
                        }
                    }
                }
            }
            items(session.questions.indices.toList()) { i ->
                val q = session.questions[i]
                ExamCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("第 ${i + 1} 题 / 共 $total 题", fontSize = DesignTokens.Type.Small, fontWeight = DesignTokens.Weight.Bold, color = palette.primary)
                        Spacer(Modifier.weight(1f))
                        if (q.unknown) Text("已标记不会", fontSize = DesignTokens.Type.Small, color = palette.muted)
                    }
                    val sectionGap = if (isLandscape) DesignTokens.Spacing.Xs else DesignTokens.Spacing.Lg
                    Spacer(Modifier.height(sectionGap))
                    Text("前提", style = DesignTokens.TextStyles.SectionTitle, color = palette.muted)
                    Spacer(Modifier.height(DesignTokens.Spacing.Xs))
                    Text(q.premise.ifBlank { "无额外前提" }, color = palette.ink, lineHeight = DesignTokens.Type.LineBody)
                    Spacer(Modifier.height(sectionGap))
                    Text("问题", style = DesignTokens.TextStyles.SectionTitle, color = palette.muted)
                    Spacer(Modifier.height(DesignTokens.Spacing.Xs))
                    Text(q.question, color = palette.ink, lineHeight = DesignTokens.Type.LineBody)
                    Spacer(Modifier.height(sectionGap))
                    Text("作答", style = DesignTokens.TextStyles.SectionTitle, color = palette.muted)
                    Spacer(Modifier.height(DesignTokens.Spacing.Xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(q.unknown, {
                            val updated = q.copy(unknown = it, answer = if (it) "" else q.answer)
                            session.questions[i] = updated
                            onUpdate(session.copy(questions = session.questions))
                            if (it) onUnknown(i, updated)
                        })
                        Spacer(Modifier.width(DesignTokens.Spacing.Xs))
                        Text("不会", color = if (q.unknown) palette.primary else palette.muted, fontSize = DesignTokens.Type.Body)
                    }
                    Spacer(Modifier.height(DesignTokens.Spacing.Md))
                    OutlinedTextField(q.answer, {
                        // 作答框只绑定 q.answer（考生作答）；参考解析永远不会流到这里。
                        session.questions[i] = q.copy(answer = it)
                        onUpdate(session.copy(questions = session.questions))
                    }, Modifier.fillMaxWidth(), enabled = !q.unknown, minLines = 3, placeholder = { Text(if (q.unknown) "已标记不会" else "在这里作答") })
                    Spacer(Modifier.height(DesignTokens.Spacing.Xs))
                    ExamAnswerImageBar(
                        images = q.answerImages,
                        palette = palette,
                        enabled = !q.unknown,
                        onHandwriting = { handwritingIndex = i },
                        onCamera = {
                            val target = newExamPhotoFile(examContext)
                            pendingPhotoPath = target.absolutePath
                            cameraTarget = i
                            runCatching {
                                cameraLauncher.launch(FileProvider.getUriForFile(examContext, examContext.packageName + ".fileprovider", target))
                            }.onFailure { error ->
                                pendingPhotoPath = null
                                cameraTarget = -1
                                target.delete()
                                notice("相机启动失败：" + (error.message ?: "未知错误"))
                            }
                        },
                        onGallery = { galleryTarget = i; galleryLauncher.launch("image/*") },
                        onPreview = { previewPath = it },
                        onRemove = { path ->
                            applyAnswerImages(i) { list -> list - path }
                            deleteExamImage(path)
                        }
                    )
                    if (q.referenceAnswer.isNotBlank()) {
                        // 解析单独折叠在作答框下方，默认收起，交卷前后都能看，不污染作答区。
                        var showReference by remember(q.question, q.referenceAnswer) { mutableStateOf(false) }
                        TextButton(onClick = { showReference = !showReference }, contentPadding = PaddingValues(0.dp)) {
                            Text(if (showReference) "收起解析" else "查看解析", fontSize = DesignTokens.Type.Small)
                        }
                        if (showReference) {
                            Text(q.referenceAnswer, color = palette.muted, fontSize = DesignTokens.Type.Body, lineHeight = DesignTokens.Type.LineBody)
                        }
                    }
                }
            }
        }
        // 手写板 / 全屏看图都是 Dialog：放在列表外层只保留一份，避免每道题各挂一个。
        val padTarget = handwritingIndex
        if (padTarget >= 0) {
            HandwritingPadDialog(
                onDismiss = { handwritingIndex = -1 },
                onSaved = { path ->
                    applyAnswerImages(padTarget) { it + path }
                    handwritingIndex = -1
                    notice("手写答案已添加")
                }
            )
        }
        previewPath?.let { path ->
            ImageFullScreenDialog(path = path, palette = palette, onClose = { previewPath = null })
        }
        // 提交条固定在底部并占据布局空间（不是浮层），保证题目内容不被遮挡。
        // 横屏可视高度仍偏紧，但可用右下悬浮键切沉浸模式，一键收起顶栏/底栏/提交条。
        // 沉浸模式下整条不渲染，把全部高度让给题目。
        if (!immersive) {
            Surface(
                Modifier.fillMaxWidth(),
                color = palette.page
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = if (isLandscape) 0.dp else DesignTokens.Spacing.Xs, bottom = LocalBottomBarLift.current)
                ) {
                    AppPrimaryButton(onClick = { onSubmit(packExamAnswers(session)) }, modifier = Modifier.fillMaxWidth()) { Text("提交答案") }
                }
            }
        }
    }
}

@Composable
private fun ExamSidePanel(
    panel: String,
    session: ExamSession,
    messages: List<ChatMessage>,
    branches: List<BranchClass>,
    draftMode: String,
    palette: AppPalette,
    panelFullscreen: Boolean,
    onTogglePanelFullscreen: () -> Unit,
    onDraftMode: (String) -> Unit,
    strokes: MutableList<DrawStroke>,
    onUpdate: (ExamSession) -> Unit,
    modifier: Modifier
) {
    Box(modifier) {
        InfoCard {
            Column(Modifier.fillMaxHeight()) {
                if (panel == "draft") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("草稿纸", fontWeight = DesignTokens.Weight.Bold, color = palette.ink, modifier = Modifier.weight(1f))
                        // 草稿纸全屏时题目栏整列收起，这里保留退出全屏的入口，避免只能靠双击分界线。
                        IconButton(onClick = onTogglePanelFullscreen, modifier = Modifier.size(DesignTokens.Size.IconButton)) {
                            Icon(
                                if (panelFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (panelFullscreen) "退出草稿纸全屏" else "草稿纸全屏",
                                tint = palette.muted,
                                modifier = Modifier.size(DesignTokens.Size.IconSm)
                            )
                        }
                        AppFilterChip(draftMode == "text", { onDraftMode("text") }) { Text("打字") }
                        Spacer(Modifier.width(DesignTokens.Spacing.Sm))
                        AppFilterChip(draftMode == "draw", { onDraftMode("draw") }) { Text("涂绘") }
                    }
                    Spacer(Modifier.height(DesignTokens.Spacing.Lg))
                    if (draftMode == "text") {
                        OutlinedTextField(session.draft, { onUpdate(session.copy(draft = it)) }, Modifier.fillMaxWidth().weight(1f), minLines = 3, placeholder = { Text("草稿纸") })
                    } else {
                        DrawPad(strokes, palette, Modifier.weight(1f).fillMaxWidth())
                        Spacer(Modifier.height(DesignTokens.Spacing.Lg))
                        OutlinedButton(onClick = { strokes.clear() }, modifier = Modifier.fillMaxWidth()) { Text("清空涂绘") }
                    }
                } else {
                    Text("过去总览", fontWeight = DesignTokens.Weight.Bold, color = palette.ink)
                    Spacer(Modifier.height(DesignTokens.Spacing.Lg))
                    LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Md)) {
                        item { Text("过往对话", style = DesignTokens.TextStyles.SectionTitle, color = palette.muted) }
                        items(messages.takeLast(12)) { MarkdownText((if (it.role == "user") "我：" else "AI：") + it.text.take(360), color = palette.ink) }
                        item { Text("不会题分支", style = DesignTokens.TextStyles.SectionTitle, color = palette.muted) }
                        items(branches.takeLast(6)) {
                            Text(it.title, fontWeight = DesignTokens.Weight.Bold, color = palette.ink)
                            MarkdownText(it.messages.lastOrNull()?.text.orEmpty().take(360), color = palette.ink)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawPad(strokes: MutableList<DrawStroke>, palette: AppPalette, modifier: Modifier) {
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val strokeColor = palette.ink
    Canvas(
        modifier
            .background(palette.surface, RoundedCornerShape(DesignTokens.Radius.Tile))
            .border(DesignTokens.Border.Hairline, palette.outline, RoundedCornerShape(DesignTokens.Radius.Tile))
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
            points.zipWithNext().forEach { (from, to) -> drawLine(strokeColor, from, to, strokeWidth = 4f, cap = StrokeCap.Round) }
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 10.dp, bottom = 10.dp + LocalBottomBarLift.current), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (branches.isEmpty()) item { InfoCard { Text("在主课堂任意消息下开分支。分支会作为独立长对话保存，不会改写主课堂。", color = Muted) } }
        items(branches.indices.toList()) { index ->
            val branch = branches[index]
            InfoCard {
                Text(branch.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(branch.source, color = Muted, fontSize = DesignTokens.Type.Body)
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
    // 同上：分支课堂也直接落在最新一条，避免进入时滑一下。
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (branch.messages.isEmpty()) 0 else branch.messages.size + 1,
    )
    LaunchedEffect(branch.messages.size) {
        if (branch.messages.isNotEmpty()) listState.animateScrollToItem(branch.messages.size + 1)
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = 10.dp, bottom = 138.dp + LocalBottomBarLift.current, start = 2.dp, end = 2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                InfoCard {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(DesignTokens.Border.Focus)
                            .background(DesignTokens.Accent, RoundedCornerShape(DesignTokens.Radius.Pill))
                    )
                    Spacer(Modifier.height(DesignTokens.Spacing.Md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "分支",
                            style = DesignTokens.TextStyles.Kicker,
                            color = DesignTokens.AccentOn,
                            modifier = Modifier
                                .background(DesignTokens.Accent, RoundedCornerShape(DesignTokens.Radius.Pill))
                                .padding(horizontal = DesignTokens.Spacing.Md, vertical = DesignTokens.Spacing.Xxs)
                        )
                        Spacer(Modifier.width(DesignTokens.Spacing.Md))
                        Text(branch.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        OutlinedButton(onClick = onBack) { Text("分支列表") }
                    }
                    Text(branch.source, color = Muted, fontSize = DesignTokens.Type.Body)
                    Spacer(Modifier.height(6.dp))
                    Text("这是与主课堂平行的支线长对话，主课堂内容不会被改写。", color = Muted, fontSize = DesignTokens.Type.Body)
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
    val bubbleUser = if (palette.page == DesignTokens.Dark.Page) DesignTokens.Dark.BubbleUser else DesignTokens.Light.BubbleUser
    if (message.role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(
                Modifier
                    .fillMaxWidth(0.82f)
                    .background(bubbleUser, RoundedCornerShape(DesignTokens.Radius.Card))
                    .padding(horizontal = DesignTokens.Spacing.CardHorizontal, vertical = DesignTokens.Spacing.CardVertical)
            ) {
                Text("YOU", style = DesignTokens.TextStyles.Kicker, color = palette.muted)
                Spacer(Modifier.height(DesignTokens.Spacing.Xs))
                if (message.text.isNotBlank()) Text(message.text, style = DesignTokens.TextStyles.Body, color = palette.ink)
                MessageImages(message.images, palette)
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(horizontal = DesignTokens.Spacing.Xxs), horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Md), verticalAlignment = Alignment.Top) {
            Surface(Modifier.size(32.dp), shape = RoundedCornerShape(DesignTokens.Radius.Button), color = palette.button) {
                Box(contentAlignment = Alignment.Center) { Text("✦", color = palette.onButton, fontSize = DesignTokens.Type.Subtitle, fontWeight = FontWeight.Bold) }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        append(mentorName.ifBlank { "AI 讲师" })
                        withStyle(SpanStyle(color = DesignTokens.Accent, fontWeight = DesignTokens.Weight.Bold)) {
                            append(" · 分支")
                        }
                    },
                    style = DesignTokens.TextStyles.Kicker,
                    color = palette.muted
                )
                Spacer(Modifier.height(DesignTokens.Spacing.Xs))
                Column(
                    Modifier.fillMaxWidth()
                        .background(palette.surface, RoundedCornerShape(DesignTokens.Radius.Card))
                        .border(DesignTokens.Border.Hairline, palette.outline, RoundedCornerShape(DesignTokens.Radius.Card))
                        .padding(horizontal = DesignTokens.Spacing.CardHorizontal, vertical = DesignTokens.Spacing.CardVertical)
                ) {
                    if (message.text.isNotBlank()) MarkdownText(message.text)
                    MessageImages(message.images, palette)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoryScreen(chapters: List<ConversationChapter>, messages: List<ChatMessage>, examRecords: List<ExamRecord>, onJump: (Int) -> Unit) {
    val visibleChapters = chapters.ifEmpty { fallbackChapters(messages) }
    var overview by remember { mutableStateOf(true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 10.dp, bottom = 10.dp + LocalBottomBarLift.current), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ExamRecordSection(examRecords) }
        item {
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("对话章节", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    AppFilterChip(overview, { overview = true }) { Text("全览") }
                    Spacer(Modifier.width(6.dp))
                    AppFilterChip(!overview, { overview = false }) { Text("章节") }
                }
                Text("双击章节可跳到主课堂对应对话。", color = Muted, fontSize = DesignTokens.Type.Body)
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
                    shape = RoundedCornerShape(DesignTokens.Radius.Tile),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(chapter.title, fontWeight = FontWeight.Bold)
                        Text("第 ${chapter.startIndex + 1} 到 ${chapter.endIndex + 1} 条", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = DesignTokens.Type.Small)
                        Spacer(Modifier.height(6.dp))
                        Text(chapter.summary, color = MaterialTheme.colorScheme.onSurface, lineHeight = 21.sp)
                    }
                }
            }
        }
    }
}

/** 记录的显示时间：优先提交时刻，没有就用起始时刻；格式与记忆页其它位置一致（月-日 时:分）。 */


/** 折叠态那一行：最近一次考的是什么、几道题。 */

/**
 * 记忆页顶部的「考试记录」区块。
 *
 * 折叠态只给总数和最近一次；展开后逐条列出，点某一条再展开那一次的题目、作答与参考解析（只读）。
 * 折叠 / 展开 / 点开哪一条都是页面级 state：不持久化，离开记忆页就复位。
 */
@Composable
private fun ExamRecordSection(records: List<ExamRecord>) {
    var expanded by remember { mutableStateOf(false) }
    var openId by remember { mutableStateOf<String?>(null) }
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📋 考试记录 · ${records.size} 次", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起" else "展开", color = MaterialTheme.colorScheme.primary)
            }
        }
        val latest = records.lastOrNull()
        when {
            latest == null -> Text("还没有考试记录。让 AI 出一套题试试。", color = Muted, fontSize = DesignTokens.Type.Body)
            !expanded -> Text("最近：" + examRecordSummary(latest), color = Muted, fontSize = DesignTokens.Type.Body)
            else -> records.asReversed().forEach { record ->
                ExamRecordRow(record, openId == record.id) {
                    openId = if (openId == record.id) null else record.id
                }
            }
        }
    }
}

/** 一条记录：标题 + 时间 + 题数 / 已答 / 是否提交；点开显示题目（只读）。 */
@Composable
private fun ExamRecordRow(record: ExamRecord, open: Boolean, onToggle: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DesignTokens.Radius.Tile))
            .clickable(onClick = onToggle)
            .padding(vertical = DesignTokens.Spacing.Xs)
    ) {
        HorizontalDivider(thickness = DesignTokens.Border.Hairline, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        Spacer(Modifier.height(DesignTokens.Spacing.Xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                record.title.ifBlank { "AI 随堂考试" },
                fontWeight = FontWeight.SemiBold,
                fontSize = DesignTokens.Type.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(examRecordTime(record), color = Muted, fontSize = DesignTokens.Type.Small)
        }
        Text(
            record.questionCount.toString() + " 题 · 已答 " + record.answeredCount + "/" + record.questionCount +
                " · " + (if (record.submitted) "已提交" else "未提交"),
            color = Muted,
            fontSize = DesignTokens.Type.Small
        )
        if (!open) return@Column
        Spacer(Modifier.height(DesignTokens.Spacing.Sm))
        if (record.questions.isEmpty()) {
            Text("这次考试没有留下题目内容。", color = Muted, fontSize = DesignTokens.Type.Body)
        } else {
            record.questions.forEachIndexed { index, question -> ExamRecordQuestion(index, question) }
        }
    }
}

/**
 * 一道题的图片答案区：两个入口（手写 / 拍照，外加相册直选），已添加的图排成一行缩略图。
 * 打字作答与手写/拍照互不影响，可以叠加（比如手写两步 + 拍一张草稿纸）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExamAnswerImageBar(
    images: List<String>,
    palette: AppPalette,
    enabled: Boolean,
    onHandwriting: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onPreview: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var pendingRemove by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth()) {
        // 三个入口用胶囊 + Material 图标，不用 emoji：
        // emoji 的字体、基线、色彩都不受宿主控制，和本项目「全局限定 DesignTokens」的视觉语言是两个世界；
        // 而且不同 ROM 的 emoji 字形差异极大（有的甚至渲染成方块）。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Sm),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            ExamAnswerAction(Icons.Default.Edit, "手写", palette, enabled, onHandwriting)
            ExamAnswerAction(Icons.Default.PhotoCamera, "拍照", palette, enabled, onCamera)
            ExamAnswerAction(Icons.Default.PhotoLibrary, "相册", palette, enabled, onGallery)
            if (images.isNotEmpty()) {
                Text(
                    "已添加 " + images.size + " 张",
                    color = palette.muted,
                    fontSize = DesignTokens.Type.Small,
                    modifier = Modifier.padding(start = DesignTokens.Spacing.Xs),
                )
            }
        }
        if (images.isNotEmpty()) {
            Spacer(Modifier.height(DesignTokens.Spacing.Xs))
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Xs)) {
                images.forEach { path ->
                    ExamAnswerThumb(
                        path = path,
                        palette = palette,
                        modifier = Modifier.size(64.dp),
                        onClick = { onPreview(path) },
                        onLongClick = { pendingRemove = path }
                    )
                }
            }
            Text("点开可放大，长按移除", color = palette.muted, fontSize = DesignTokens.Type.Small)
        }
    }
    pendingRemove?.let { path ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("移除这张图片？") },
            text = { Text("移除后它不会再跟着答案提交给 AI 批改。") },
            confirmButton = { TextButton(onClick = { onRemove(path); pendingRemove = null }) { Text("移除") } },
            dismissButton = { TextButton(onClick = { pendingRemove = null }) { Text("取消") } }
        )
    }
}

/**
 * 答题图片入口的胶囊按钮：细边框 + 图标 + 文字。
 *
 * 与设置页的 chip、顶栏按钮同一套语言（Radius.Pill + Hairline + IconSm）。
 * 禁用时整体降到 muted，不用 Material 的 38% alpha —— 那个透明度和本主题的浅底叠起来几乎看不见。
 */
@Composable
private fun ExamAnswerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    palette: AppPalette,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tint = if (enabled) palette.ink else palette.muted
    Row(
        Modifier
            .clip(RoundedCornerShape(DesignTokens.Radius.Pill))
            .background(if (enabled) palette.surface else palette.card)
            .border(DesignTokens.Border.Hairline, palette.outline, RoundedCornerShape(DesignTokens.Radius.Pill))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = DesignTokens.Spacing.Lg, vertical = DesignTokens.Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(DesignTokens.Size.IconSm), tint = tint)
        Spacer(Modifier.width(DesignTokens.Spacing.Xs))
        Text(label, color = tint, fontSize = DesignTokens.Type.Small)
    }
}

/** 答案图片缩略图：文件被删 / 读不出来时显示占位，不崩也不留白。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExamAnswerThumb(
    path: String,
    palette: AppPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val exists = remember(path) { examImageExists(path) }
    val bitmap by produceState<ImageBitmap?>(initialValue = MessageImageLoader.cached(path), path) {
        value = if (path.isBlank()) null else withContext(Dispatchers.IO) { MessageImageLoader.load(path, MESSAGE_IMAGE_MAX_SIDE) }
    }
    val image = bitmap
    Box(
        modifier
            .clip(RoundedCornerShape(DesignTokens.Radius.Card))
            .background(palette.card)
            .border(DesignTokens.Border.Hairline, palette.outline, RoundedCornerShape(DesignTokens.Radius.Card))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(bitmap = image, contentDescription = "答案图片", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                if (exists) "加载中" else "图片已不存在",
                color = palette.muted,
                fontSize = DesignTokens.Type.Small
            )
        }
    }
}

/** 题目明细：题干 + 考生作答 + 参考解析，全部只读。 */
@Composable
private fun ExamRecordQuestion(index: Int, question: ExamQuestion) {
    Column(Modifier.fillMaxWidth().padding(vertical = DesignTokens.Spacing.Xs)) {
        Text(
            (index + 1).toString() + ". " + question.premise.ifBlank { question.question },
            fontWeight = FontWeight.SemiBold,
            fontSize = DesignTokens.Type.Body
        )
        if (question.premise.isNotBlank() && question.question.isNotBlank()) {
            Text(question.question, fontSize = DesignTokens.Type.Body, lineHeight = DesignTokens.Type.LineBody)
        }
        Text(
            "作答：" + when {
                question.unknown -> "标记不会"
                question.answer.isNotBlank() -> question.answer
                question.answerImages.isNotEmpty() -> "见附图"
                else -> "未作答"
            },
            color = Muted,
            fontSize = DesignTokens.Type.Small
        )
        if (question.answerImages.isNotEmpty()) {
            // 历史记录里的手写/拍照答案：只读缩略图，点不开全屏（记忆页没有全屏看图容器）。
            Text("附 " + question.answerImages.size + " 张手写/拍照答案", color = Muted, fontSize = DesignTokens.Type.Small)
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Xs)) {
                question.answerImages.forEach { path -> ExamRecordAnswerThumb(path) }
            }
        }
        if (question.referenceAnswer.isNotBlank()) {
            Text(
                "解析：" + question.referenceAnswer,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = DesignTokens.Type.Small,
                lineHeight = DesignTokens.Type.LineSnug
            )
        }
    }
}

/** 记忆页里的答案图片缩略图：不依赖主题调色板，颜色走 MaterialTheme，避免多传一层参数。 */
@Composable
private fun ExamRecordAnswerThumb(path: String) {
    val bitmap by produceState<ImageBitmap?>(initialValue = MessageImageLoader.cached(path), path) {
        value = withContext(Dispatchers.IO) { MessageImageLoader.load(path, MESSAGE_IMAGE_MAX_SIDE) }
    }
    val image = bitmap
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(DesignTokens.Radius.Card))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(bitmap = image, contentDescription = "答案图片", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            Text("图片已不存在", color = Muted, fontSize = DesignTokens.Type.Small)
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
        Text("双指可任意放大缩小，拖动查看；双击节点跳到主课堂对应对话。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = DesignTokens.Type.Body)
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
                    drawLine(button.copy(alpha = (0.18f + weight * 0.24f).coerceIn(0.18f, 0.46f)), a, b, strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round)
                }
                positions.values.forEach { point ->
                    drawCircle(button.copy(alpha = 0.14f), radius = 34f * scale.coerceIn(0.7f, 1.6f), center = point)
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
                    border = null
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text(chapter.title, fontWeight = FontWeight.Bold, fontSize = DesignTokens.Type.Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(chapter.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = DesignTokens.Type.Caption, lineHeight = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (nodes.size < chapters.size) Text("已显示最近 ${nodes.size} 个章节。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = DesignTokens.Type.Small)
    }
}






@Composable
private fun KnowledgeScreen(files: MutableList<KnowledgeFile>, onSave: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var viewing by remember { mutableStateOf<KnowledgeFile?>(null) }
    var deleteTarget by remember { mutableStateOf<KnowledgeFile?>(null) }
    var importNotice by remember { mutableStateOf<String?>(null) }
    fun importFile(uri: Uri, kind: String): Boolean {
        val resolver = context.contentResolver
        val displayName = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull().orEmpty().ifBlank { uri.lastPathSegment?.substringAfterLast('/') ?: "knowledge" }
        val ext = displayName.substringAfterLast('.', "").lowercase().ifBlank {
            when (resolver.getType(uri)?.lowercase()) {
                "text/markdown" -> "md"
                "application/json", "text/json" -> "json"
                "image/png" -> "png"
                else -> "txt"
            }
        }
        val cardTypeAllowed = ext == "md" || ext == "json" || ext == "png"
        val referenceTypeAllowed = ext == "md" || ext == "txt" || resolver.getType(uri)?.startsWith("text/") == true
        if ((kind == "character_card" && !cardTypeAllowed) || (kind == "reference" && !referenceTypeAllowed)) {
            importNotice = if (kind == "character_card") "角色卡仅支持 .md、.json、.png 文件" else "资料仅支持 .md 或 .txt 文件"
            return false
        }
        run {
            val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null) { importNotice = "无法读取文件"; return false }
            val text = when (ext) {
                "json" -> normalizeCharacterCardJson(bytes.toString(Charsets.UTF_8))
                "png" -> extractPngCardText(bytes)
                else -> bytes.toString(Charsets.UTF_8)
            }
            if (ext == "png" && text.isBlank()) { importNotice = "PNG 中未找到角色卡文本元数据"; return false }
            if (text.isNotBlank()) {
                val normalizedName = if (displayName.contains('.')) displayName else "$displayName.$ext"
                files.removeAll { it.name == normalizedName && it.kind == kind }
                files.add(KnowledgeFile(normalizedName, ext, text.length, text.take(1000), text, kind))
                onSave()
                importNotice = if (kind == "character_card") "已导入角色卡：$normalizedName" else "已导入资料：$normalizedName"
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                return true
            } else { importNotice = "文件为空，未导入"; return false }
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); uris.count { importFile(it, "reference") }.let { importNotice = "已导入 $it/${uris.size} 个资料文件" } }
    }
    val cardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); uris.count { importFile(it, "character_card") }.let { importNotice = "已导入 $it/${uris.size} 张角色卡" } }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 10.dp, bottom = 10.dp + LocalBottomBarLift.current), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            InfoCard {
                Text("知识库", fontWeight = FontWeight.Bold)
                Text("资料按当前问题检索；角色卡支持 .md、.json、.png，可批量导入并持续作为人物参照。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = DesignTokens.Type.Body, lineHeight = 19.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { launcher.launch(arrayOf("text/plain", "text/markdown", "application/json", "image/png", "application/octet-stream")) }, shape = AppShapes.button) { Text("批量上传资料") }
                    OutlinedButton(onClick = { cardLauncher.launch(arrayOf("text/markdown", "application/json", "image/png", "text/plain")) }, shape = AppShapes.button) { Text("批量导入角色卡") }
                }
                importNotice?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = DesignTokens.Type.Small) }
            }
        }
        if (files.isEmpty()) item { InfoCard { Text("还没有上传知识库文件。点击上方「批量上传资料」即可导入资料。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = DesignTokens.Type.Body, lineHeight = DesignTokens.Type.LineBody) } }
        items(files, key = { it.kind + it.name + it.chars }) { file ->
            KnowledgeFileRow(file, onOpen = { viewing = file }, onDelete = { deleteTarget = file })
        }
    }
    viewing?.let { file -> KnowledgeFileDialog(file, onClose = { viewing = null }) }
    deleteTarget?.let { file ->
        AppDialogBase(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除文件", fontWeight = FontWeight.Bold) },
            text = { Text("确定从知识库删除 ${file.name} 吗？") },
            confirmButton = { AppPrimaryButton(onClick = { files.remove(file); deleteTarget = null; onSave(); view.performHapticFeedback(HapticFeedbackConstants.CONFIRM) }) { Text("删除") } },
            dismissButton = { AppSecondaryButton(onClick = { deleteTarget = null }) { Text("取消") } }
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
            Text("${if (file.kind == "character_card") "角色卡" else "资料"} · ${file.type} · ${file.chars} 字", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = DesignTokens.Type.Body)
            Spacer(Modifier.height(6.dp))
            MarkdownText(file.preview)
        }
    }
}

@Composable
private fun KnowledgeFileDialog(file: KnowledgeFile, onClose: () -> Unit) {
    AppDialogBase(
        onDismissRequest = onClose,
        title = { Text(file.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            LazyColumn(Modifier.height(460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { MarkdownText(file.content) }
            }
        },
        confirmButton = { AppPrimaryButton(onClick = onClose) { Text("关闭") } }
    )
}

@Composable
private fun ModelScreen(
    config: ClassroomConfig,
    models: List<String>,
    modelStatus: String,
    saveNotice: String,
    onConfig: (ClassroomConfig) -> Unit,
    examToolsEnabled: Boolean,
    onExamToolsEnabledChange: (Boolean) -> Unit,
    fsToolsEnabled: Boolean,
    onFsToolsEnabledChange: (Boolean) -> Unit,
    configToolsEnabled: Boolean,
    onConfigToolsEnabledChange: (Boolean) -> Unit,
    netToolsEnabled: Boolean,
    onNetToolsEnabledChange: (Boolean) -> Unit,
    webActDomains: List<String>,
    onWebActDomainsChange: (List<String>) -> Unit,
    imageToolsEnabled: Boolean,
    onImageToolsEnabledChange: (Boolean) -> Unit,
    mediaToolsEnabled: Boolean,
    onMediaToolsEnabledChange: (Boolean) -> Unit,
    videoToolsEnabled: Boolean,
    onVideoToolsEnabledChange: (Boolean) -> Unit,
    imageConfig: ImageConfig,
    onImageConfig: (ImageConfig) -> Unit,
    customToolsJson: String,
    onCustomToolsJson: (String) -> Unit,
    existingToolNames: () -> List<String>,
    imageSink: ImageSink,
    pluginInfos: List<LoadedPluginInfo>,
    onImportPlugin: () -> Unit,
    onUninstallPlugin: (String) -> Unit,
    onPluginEnabledChange: (String, Boolean) -> Unit,
    onPluginSettingChange: (String, String, String) -> Unit,
    /** settings 里 type=image 的字段：弹系统图片选择器（由上层拿 Activity context）。 */
    onPluginImagePick: (String, String) -> Unit,
    /** 清除某个图片字段（文件 + prefs 一起删）。 */
    onPluginImageClear: (String, String) -> Unit,
    onOpenManual: () -> Unit,
    onFetchModels: () -> Unit,
    /** 彩蛋：连击版本号 5 下时重放开屏 CG。 */
    onReplaySplash: () -> Unit = {},
    /** 手动查到新版本时，把结果交给上层弹更新框。 */
    onShowUpdate: (com.aiclassroom.app.model.UpdateInfo) -> Unit = {}
) {
    val context = LocalContext.current
    // 自定义插件库：json 是唯一真相，改一次就序列化回上层（写 prefs + 触发 registry 重建）。
    val customTools = remember(customToolsJson) { CustomToolSpec.listFromJson(customToolsJson) }
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
    var worldBook by remember(config.worldBook) { mutableStateOf(config.worldBook) }
    var efficientMode by remember(config.efficientMode) { mutableStateOf(config.efficientMode) }
    var reverseConversation by remember(config.reverseConversation) { mutableStateOf(config.reverseConversation) }
    var imageProvider by remember(imageConfig.provider) { mutableStateOf(imageConfig.provider) }
    var imageBaseUrl by remember(imageConfig.baseUrl) { mutableStateOf(imageConfig.baseUrl) }
    var imageApiKey by remember(imageConfig.apiKey) { mutableStateOf(imageConfig.apiKey) }
    var imageModel by remember(imageConfig.model) { mutableStateOf(imageConfig.model) }
    var imageSize by remember(imageConfig.size) { mutableStateOf(imageConfig.size) }
    var imageSteps by remember(imageConfig.steps) { mutableStateOf(imageConfig.steps.toString()) }
    var imageCfgScale by remember(imageConfig.cfgScale) { mutableStateOf(imageConfig.cfgScale.toString()) }
    var imageSaveStatus by remember { mutableStateOf("") }
    var imageDetailExpanded by remember { mutableStateOf(false) }
    // 插件配置的展开状态挂在列表外层：放在行内部的话，LazyColumn 滚动回收该行时会被重置。
    // 默认全部收起：插件行只留「图标 + 名称·工具数 + 开关」，描述与提示收进折叠。
    var expandedPluginConfigs by remember { mutableStateOf(emptySet<String>()) }
    var expandedPluginRows by remember { mutableStateOf(emptySet<String>()) }
    var expandedPluginCards by remember { mutableStateOf(emptySet<String>()) }
    var expandedCustomPlugins by remember { mutableStateOf(emptySet<String>()) }
    var themeMode by remember(config.themeMode) { mutableStateOf(normalizeThemeMode(config.themeMode)) }
    var interfaceMode by remember(config.interfaceMode) { mutableStateOf(normalizeInterfaceMode(config.interfaceMode)) }
    var primaryColor by remember(config.primaryColor) { mutableStateOf(config.primaryColor) }
    var secondaryColor by remember(config.secondaryColor) { mutableStateOf(config.secondaryColor) }
    var primaryHex by remember(config.primaryColor) { mutableStateOf(argbToHex(config.primaryColor)) }
    // 橙色排最前面：它是默认色，用户进来第一眼就看到自己现在的颜色。
    val colorSwatches = listOf(0xFFFF7A1A, 0xFFE85D04, 0xFF58D7E6, 0xFF6C63FF, 0xFF22C55E, 0xFFEF4444, 0xFF8B7BFF, 0xFF302044)
    val primaryValid = parseHexColor(primaryHex) != null
    val scope = rememberCoroutineScope()
    var expandedModule by remember { mutableStateOf<String?>("model") }
    var visionStatus by remember { mutableStateOf("未获取识图模型") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 10.dp, bottom = 10.dp + LocalBottomBarLift.current), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SettingsCard("模型模块", expandedModule == "model", { expandedModule = if (expandedModule == "model") null else "model" }) {
                Text("普通课堂对话使用此模块的 API。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = DesignTokens.Type.Small)
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
                Text("按钮皮肤", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = DesignTokens.Type.Small, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppFilterChip(themeMode == "custom", {
                        themeMode = "custom"
                        primaryHex = argbToHex(primaryColor)
                    }) { Text("自定义") }
                }
                Spacer(Modifier.height(10.dp))
                Text("界面明暗", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = DesignTokens.Type.Small, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "自动", "light" to "白色", "dark" to "黑色").forEach { option ->
                        AppFilterChip(interfaceMode == option.first, {
                            interfaceMode = option.first
                            onConfig(config.copy(interfaceMode = normalizeInterfaceMode(option.first)))
                        }) { Text(option.second) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (themeMode == "custom") {
                    ButtonColorPreview(primaryColor)
                    Spacer(Modifier.height(10.dp))
                    Text("调色盘", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = DesignTokens.Type.Small, fontWeight = FontWeight.Bold)
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
                    if (!primaryValid) Text("请输入 #RRGGBB 或 #AARRGGBB", color = DesignTokens.Danger, fontSize = DesignTokens.Type.Small)
                    Spacer(Modifier.height(8.dp))
                    Text("自定义颜色只应用到按钮和可点击强调，不再染色背景、卡片或正文。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = DesignTokens.Type.Small, lineHeight = 18.sp)
                } else {
                    ButtonColorPreview(primaryColor)
                    Spacer(Modifier.height(8.dp))
                    Text("默认强调色是橙色：按钮用 #FF7A1A，插件里的强调元素（节奏线锚点、播放键、进度填充）用更深的 #E85D04。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = DesignTokens.Type.Small, lineHeight = 18.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        themeMode = "custom"
                        primaryColor = 0xFFFF7A1A
                        secondaryColor = 0xFFE85D04
                        primaryHex = argbToHex(primaryColor)
                    }) { Text("恢复默认橙色") }
                    Button(
                        onClick = {
                            val cleanThemeMode = normalizeThemeMode(themeMode)
                            // 现在只有一种主题模型：颜色一律来自输入框，没有「预设主题覆盖」这回事。
                            val cleanPrimary = parseHexColor(primaryHex) ?: primaryColor
                            val cleanSecondary = secondaryColor
                            primaryColor = cleanPrimary
                            secondaryColor = cleanSecondary
                            primaryHex = argbToHex(cleanPrimary)
                            themeMode = cleanThemeMode
                            onConfig(config.copy(reverseConversation = reverseConversation, themeMode = cleanThemeMode, interfaceMode = normalizeInterfaceMode(interfaceMode), primaryColor = cleanPrimary, secondaryColor = cleanSecondary))
                            expandedModule = null
                        },
                        enabled = primaryValid
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
                OutlinedTextField(worldBook, { worldBook = it }, Modifier.fillMaxWidth(), label = { Text("手写世界书") }, minLines = 4)
                Text("可写入长期固定设定、课程边界、学习习惯或叙事隔离规则；例如“作者私密信息不得被未知角色直接利用”。", color = Muted, fontSize = DesignTokens.Type.Small, lineHeight = 18.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.HealthAndSafety, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("高效模式", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Switch(efficientMode, { efficientMode = it })
                }
                Text("过滤 NSFW 内容。", color = Muted)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onConfig(config.copy(mentorName = mentorName.trim().ifBlank { "AI 讲师" }, userAlias = userAlias.trim().ifBlank { "同学" }, mentorPrompt = mentorPrompt.trim().ifBlank { DEFAULT_MENTOR_PROMPT }, worldBook = worldBook.trim(), efficientMode = efficientMode)); expandedModule = null }) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("保存讲师人格与模式模块")
                }
            }
        }
        item {
            SettingsCard("插件", expandedModule == "plugin", { expandedModule = if (expandedModule == "plugin") null else "plugin" }) {
                val enabledPlugins = listOf(examToolsEnabled, fsToolsEnabled, netToolsEnabled, configToolsEnabled, imageToolsEnabled, videoToolsEnabled).count { it }
                Text("已启用 " + enabledPlugins + " / 6 个插件 · 共 19 个工具", fontWeight = FontWeight.Bold)
                Text("插件就是 AI 可以自己决定要不要调用的工具。关掉某个插件后，对应工具会在本地直接拒绝并回一条错误给模型，不产生任何副作用。开关对所有课堂生效。", color = Muted, fontSize = DesignTokens.Type.Small, lineHeight = 18.sp)
                Spacer(Modifier.height(DesignTokens.Spacing.Md))
                PluginRow(
                    Icons.Default.School, "考试工具", 7, "创建/编辑考试并保存到课堂",
                    examToolsEnabled, onExamToolsEnabledChange,
                    expanded = "考试工具" in expandedPluginRows,
                    onToggleExpanded = { expandedPluginRows = togglePluginConfig(expandedPluginRows, "考试工具") }
                )
                PluginRow(
                    Icons.Default.Folder, "文件工具", 4, "读写应用私有目录内的文件",
                    fsToolsEnabled, onFsToolsEnabledChange,
                    expanded = "文件工具" in expandedPluginRows,
                    onToggleExpanded = { expandedPluginRows = togglePluginConfig(expandedPluginRows, "文件工具") }
                )
                PluginRow(
                    Icons.Default.Public, "网络工具", 1, "调用任意 http/https 地址",
                    netToolsEnabled, onNetToolsEnabledChange,
                    expanded = "网络工具" in expandedPluginRows,
                    onToggleExpanded = { expandedPluginRows = togglePluginConfig(expandedPluginRows, "网络工具") },
                    notes = listOf(
                        "关闭后 net_request 在本地直接拒绝，不发起任何网络请求。http:// 为明文传输，建议只填本地或可信局域网地址。",
                        "网页工具（web_capture 截图、web_search 搜索、web_act 代操作）同样受这个开关约束：关掉后 AI 打不开任何网页。"
                    )
                ) {
                    Spacer(Modifier.height(DesignTokens.Spacing.Sm))
                    WebActWhitelistSection(webActDomains, onWebActDomainsChange)
                }
                PluginRow(
                    Icons.Default.Tune, "配置工具", 5, "让 AI 修改模型/BaseURL/温度（密钥不可写）",
                    configToolsEnabled, onConfigToolsEnabledChange,
                    expanded = "配置工具" in expandedPluginRows,
                    onToggleExpanded = { expandedPluginRows = togglePluginConfig(expandedPluginRows, "配置工具") }
                )
                PluginRow(
                    Icons.Default.Image, "生图", 2, "调用云端或本地生图服务生成图片，并把图片发到对话里",
                    imageToolsEnabled, onImageToolsEnabledChange,
                    expanded = "生图" in expandedPluginRows,
                    onToggleExpanded = { expandedPluginRows = togglePluginConfig(expandedPluginRows, "生图") }
                )
                // 通知使用权是「能不能读到播放信息」的硬前提，但它藏在系统设置里，
                // 而且「音乐」这一行要展开两层才看得到里面的按钮 —— 用户找不到入口，
                // 就会以为这个功能坏了。所以状态提到行描述里，不展开也能看见。
                // 每秒复查：从系统设置授权回来，这里和描述都会立刻变。
                var mediaGranted by remember { mutableStateOf(MediaAccess.isGranted(context)) }
                LaunchedEffect(mediaToolsEnabled) {
                    while (true) {
                        mediaGranted = MediaAccess.isGranted(context)
                        delay(1_500)
                    }
                }
                PluginRow(
                    Icons.Default.PlayArrow, "音乐", 3,
                    if (mediaGranted) "读取正在播放的歌名/歌手/封面，并控制播放"
                    else "缺少「通知使用权」，读不到歌名也控制不了播放（展开这里可直接去授权）",
                    mediaToolsEnabled, onMediaToolsEnabledChange,
                    expanded = "音乐" in expandedPluginRows,
                    onToggleExpanded = { expandedPluginRows = togglePluginConfig(expandedPluginRows, "音乐") },
                    notes = listOf("与网络、生图是三个独立开关：音乐只读系统「正在播放」并转发播放/暂停，不发任何网络请求。关闭后 media.* 在本地直接拒绝，插件拿不到任何播放信息。")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (mediaGranted) Icons.Default.Check else Icons.Default.Error,
                            null,
                            tint = if (mediaGranted) MaterialTheme.colorScheme.primary else DesignTokens.Accent,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (mediaGranted) "通知使用权已授权"
                            else "还没有通知使用权，读不到歌名也控制不了播放",
                            color = if (mediaGranted) Muted else DesignTokens.Accent,
                            fontSize = DesignTokens.Type.Small,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (!mediaGranted) {
                        // 不需要 root：通知使用权是普通用户在系统设置里勾选即可的特殊权限。
                        // 国产 ROM 会改写入口，所以这里既给直达按钮，也给文字路径兜底。
                        Text(
                            "不需要 root。手机上找不到的话，按这个路径走：" +
                                "设置 → 应用设置 → 应用管理 → 特殊权限（或右上角 ⋮）→ 通知使用权 → " +
                                "找到「AI Classroom 音乐」并打开。部分机型在「通知与控制中心 → 通知使用权」。",
                            color = Muted,
                            fontSize = DesignTokens.Type.Small,
                            lineHeight = 18.sp,
                        )
                        OutlinedButton(onClick = { MediaAccess.openSettings(context) }) {
                            Text("去授权通知使用权", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                PluginRow(
                    Icons.Default.Movie, "视频工具", 1, "从本地视频按画面变化自适应抽帧，交给视觉模型理解",
                    videoToolsEnabled, onVideoToolsEnabledChange,
                    expanded = "视频工具" in expandedPluginRows,
                    onToggleExpanded = { expandedPluginRows = togglePluginConfig(expandedPluginRows, "视频工具") },
                    notes = listOf("video_frames 只读设备本地的视频文件：网页流媒体（HLS / m3u8）无法按帧解码，会直接报错。自适应抽帧会先按 1 秒扫一遍缩略图算画面变化，所以比较吃 CPU；不想让 AI 碰视频就关掉这个开关。限制：文件 ≤ 500MB、单次 ≤ 10 分钟。")
                )
                TextButton(onClick = { imageDetailExpanded = !imageDetailExpanded }) {
                    Text(if (imageDetailExpanded) "收起生图配置" else "生图详细配置（运营商 / Key / 模型 / 尺寸）", color = MaterialTheme.colorScheme.primary)
                }
                if (imageDetailExpanded) {
                    Text("运营商", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = DesignTokens.Type.Small, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppFilterChip(imageProvider == ImageConfig.PROVIDER_OPENAI, {
                            imageProvider = ImageConfig.PROVIDER_OPENAI
                            imageBaseUrl = "https://api.openai.com/v1"
                            imageModel = imageModel.ifBlank { "dall-e-3" }
                            imageSize = imageSize.ifBlank { "1024x1024" }
                        }) { Text("OpenAI Images") }
                        AppFilterChip(imageProvider == ImageConfig.PROVIDER_SDWEBUI, {
                            imageProvider = ImageConfig.PROVIDER_SDWEBUI
                            imageBaseUrl = "http://127.0.0.1:7860"
                        }) { Text("SD WebUI") }
                        AppFilterChip(imageProvider == ImageConfig.PROVIDER_CUSTOM, {
                            imageProvider = ImageConfig.PROVIDER_CUSTOM
                        }) { Text("自定义 HTTP") }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(imageBaseUrl, { imageBaseUrl = it }, Modifier.fillMaxWidth(), label = { Text("Base URL") }, singleLine = true)
                    Text(
                        when (imageProvider) {
                            ImageConfig.PROVIDER_SDWEBUI -> "本地软件示例：http://127.0.0.1:7860（会调用 /sdapi/v1/txt2img）"
                            ImageConfig.PROVIDER_CUSTOM -> "填自己的生图接口地址，按默认模板 {\"prompt\":\"{{prompt}}\"} 提交"
                            else -> "OpenAI 兼容服务示例：https://api.openai.com/v1"
                        },
                        color = Muted, fontSize = DesignTokens.Type.Small, lineHeight = 18.sp
                    )
                    Text("http:// 为明文传输，建议只填本地或可信局域网地址。", color = Muted, fontSize = DesignTokens.Type.Small, lineHeight = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(imageApiKey, { imageApiKey = it }, Modifier.fillMaxWidth(), label = { Text("API Key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    Text(
                        if (imageProvider == ImageConfig.PROVIDER_SDWEBUI) "本地 SD WebUI 通常留空（开了 --api-auth 才需要填）。" else "密钥只保存在本机，不会显示明文。",
                        color = Muted, fontSize = DesignTokens.Type.Small, lineHeight = 18.sp
                    )
                    if (imageProvider == ImageConfig.PROVIDER_OPENAI) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(imageModel, { imageModel = it }, Modifier.fillMaxWidth(), label = { Text("模型名称（如 dall-e-3 / gpt-image-1）") }, singleLine = true)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(imageSize, { imageSize = it }, Modifier.fillMaxWidth(), label = { Text("尺寸（如 1024x1024 / 1792x1024）") }, singleLine = true)
                    }
                    if (imageProvider == ImageConfig.PROVIDER_SDWEBUI) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(imageSteps, { imageSteps = it }, Modifier.weight(1f), label = { Text("步数") }, singleLine = true)
                            OutlinedTextField(imageCfgScale, { imageCfgScale = it }, Modifier.weight(1f), label = { Text("CFG") }, singleLine = true)
                        }
                        Text("采样器沿用默认值（" + imageConfig.sampler + "），宽高默认 1024x1024；模型调用时给了 width / height 就以它为准。", color = Muted, fontSize = DesignTokens.Type.Small, lineHeight = 18.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        val cleanSteps = imageSteps.trim().toIntOrNull()?.coerceIn(1, 150) ?: imageConfig.steps
                        val cleanCfg = imageCfgScale.trim().toDoubleOrNull()?.coerceIn(0.0, 30.0) ?: imageConfig.cfgScale
                        imageSteps = cleanSteps.toString()
                        imageCfgScale = cleanCfg.toString()
                        onImageConfig(imageConfig.copy(
                            provider = imageProvider,
                            baseUrl = imageBaseUrl.trim(),
                            apiKey = imageApiKey.trim(),
                            model = imageModel.trim().ifBlank { imageConfig.model },
                            size = imageSize.trim().ifBlank { imageConfig.size },
                            steps = cleanSteps,
                            cfgScale = cleanCfg
                        ))
                        imageSaveStatus = "生图配置已保存，AI 下一次调用立即生效"
                    }) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("保存生图配置")
                    }
                    if (imageSaveStatus.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(imageSaveStatus, color = MaterialTheme.colorScheme.primary, fontSize = DesignTokens.Type.Small)
                    }
                }
                CustomToolSection(
                    specs = customTools,
                    existingToolNames = existingToolNames,
                    imageSink = imageSink,
                    expandedNames = expandedCustomPlugins,
                    onToggleExpanded = { name -> expandedCustomPlugins = togglePluginConfig(expandedCustomPlugins, name) },
                    onSpecsChange = { onCustomToolsJson(CustomToolSpec.encodeList(it)) }
                )
                Spacer(Modifier.height(DesignTokens.Spacing.Md))
                // 系统插件（沉浸模式 / 音乐）不属于「用户装的插件包」，单独列一节。
                // 它们只是不能卸载，但仍要能停用 —— 所以是分组，不是隐藏。
                val systemPlugins = pluginInfos.filter { BuiltinPlugins.isSystem(it.id) }
                val samplePlugins = pluginInfos.filter {
                    !BuiltinPlugins.isSystem(it.id) && BuiltinPlugins.isSample(it.id)
                }
                val userPlugins = pluginInfos.filterNot {
                    BuiltinPlugins.isSystem(it.id) || BuiltinPlugins.isSample(it.id)
                }
                if (systemPlugins.isNotEmpty()) {
                    Text("内置功能 · " + systemPlugins.size + " 个", fontWeight = FontWeight.Bold)
                    Text(
                        "应用自带的能力模块，可以停用，但不能卸载。",
                        color = Muted, fontSize = DesignTokens.Type.Small, lineHeight = 18.sp
                    )
                    systemPlugins.forEach { info ->
                    PluginPackageRow(
                        info = info,
                        rowExpanded = info.id in expandedPluginCards,
                        onToggleRow = { expandedPluginCards = togglePluginConfig(expandedPluginCards, info.id) },
                        expanded = info.id in expandedPluginConfigs,
                        onToggleExpanded = { expandedPluginConfigs = togglePluginConfig(expandedPluginConfigs, info.id) },
                        onEnabledChange = { v -> onPluginEnabledChange(info.id, v) },
                        onSettingChange = { k, v -> onPluginSettingChange(info.id, k, v) },
                        onImagePick = { k -> onPluginImagePick(info.id, k) },
                        onImageClear = { k -> onPluginImageClear(info.id, k) },
                        onUninstall = { onUninstallPlugin(info.id) },
                        system = BuiltinPlugins.isSystem(info.id),
                    )
                    }
                    Spacer(Modifier.height(DesignTokens.Spacing.Md))
                }
                if (samplePlugins.isNotEmpty()) {
                    Text("示例插件 · " + samplePlugins.size + " 个", fontWeight = FontWeight.Bold)
                    Text(
                        "演示用，不是应用功能：界面插槽动画、以及 manifest.settings 各种配置类型的样例。可以直接卸载。",
                        color = Muted, fontSize = DesignTokens.Type.Small, lineHeight = 18.sp
                    )
                    samplePlugins.forEach { info ->
                        PluginPackageRow(
                            info = info,
                            rowExpanded = info.id in expandedPluginCards,
                            onToggleRow = { expandedPluginCards = togglePluginConfig(expandedPluginCards, info.id) },
                            expanded = info.id in expandedPluginConfigs,
                            onToggleExpanded = { expandedPluginConfigs = togglePluginConfig(expandedPluginConfigs, info.id) },
                            onEnabledChange = { v -> onPluginEnabledChange(info.id, v) },
                            onSettingChange = { k, v -> onPluginSettingChange(info.id, k, v) },
                            onImagePick = { k -> onPluginImagePick(info.id, k) },
                            onImageClear = { k -> onPluginImageClear(info.id, k) },
                            onUninstall = { onUninstallPlugin(info.id) },
                            system = false,
                        )
                    }
                    Spacer(Modifier.height(DesignTokens.Spacing.Md))
                }
                                // 0 个时不要说「0 个」—— 那会让人以为坏了
                if (userPlugins.isEmpty()) {
                    Text("还没有导入过插件", fontWeight = FontWeight.Bold)
                    Text(
                        "用下面的「导入插件文件」添加 .zip 或 .aip。",
                        color = Muted, fontSize = DesignTokens.Type.Small, lineHeight = 18.sp
                    )
                } else {
                    Text("已导入的插件 · " + userPlugins.size + " 个", fontWeight = FontWeight.Bold)
                }
                Text(
                    "插件包是一个 ZIP，里面是 manifest.json（权限与扩展点）加 logic.js（脚本）。支持 .zip 与 .aip 两种后缀 —— 自定义的 .aip 在部分系统的文件选择器里会被隐藏，通常直接用 .zip 更方便。" +
                        "导入后按 manifest 声明的能力工作。",
                    color = Muted, fontSize = DesignTokens.Type.Small, lineHeight = 18.sp
                )
                userPlugins.forEach { info ->
                    PluginPackageRow(
                        info = info,
                        rowExpanded = info.id in expandedPluginCards,
                        onToggleRow = { expandedPluginCards = togglePluginConfig(expandedPluginCards, info.id) },
                        expanded = info.id in expandedPluginConfigs,
                        onToggleExpanded = { expandedPluginConfigs = togglePluginConfig(expandedPluginConfigs, info.id) },
                        onEnabledChange = { v -> onPluginEnabledChange(info.id, v) },
                        onSettingChange = { k, v -> onPluginSettingChange(info.id, k, v) },
                        onImagePick = { k -> onPluginImagePick(info.id, k) },
                        onImageClear = { k -> onPluginImageClear(info.id, k) },
                        onUninstall = { onUninstallPlugin(info.id) },
                        system = BuiltinPlugins.isSystem(info.id),
                    )
                }
                TextButton(onClick = onImportPlugin) {
                    Text("⊕ 导入插件文件（.zip / .aip）", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            InfoCard {
                Text("使用手册", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("查看 AI Classroom 的课堂、分支、记忆、知识库、模型配置、皮肤和插件（含生图）说明。", color = Muted, lineHeight = 21.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenManual) { Text("打开使用手册") }
            }
        }
        item {
                    Spacer(Modifier.height(DesignTokens.Spacing.Lg))
                    // 版本行：需要同时满足「单击检查更新」和「连击 5 下播彩蛋」。
                    // 用 600ms 防抖区分：5 连击在计数到 5 时立刻触发，不等防抖；
                    // 1~4 次点击则在停手 600ms 后才真正去查更新，避免连击时发五次请求。
                    var tapCount by remember { mutableIntStateOf(0) }
                    var checkState by remember { mutableStateOf<String?>(null) }
                    val tapScope = rememberCoroutineScope()
                    var pendingTap by remember { mutableStateOf<Job?>(null) }
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            "版本 " + APP_VERSION,
                            color = Muted,
                            fontSize = DesignTokens.Type.Caption,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    tapCount++
                                    pendingTap?.cancel()
                                    if (tapCount >= 5) {
                                        // 彩蛋：连击 5 下重放开屏 CG
                                        tapCount = 0
                                        checkState = null
                                        onReplaySplash()
                                    } else {
                                        pendingTap = tapScope.launch {
                                            delay(600)
                                            tapCount = 0
                                            // 1~4 次点击：查更新
                                            checkState = "正在检查…"
                                            val info = checkGitHubUpdate()
                                            checkState = when {
                                                info == null -> "检查失败：网络不通"
                                                isRemoteVersionNewer(info.version, APP_VERSION) -> "发现新版本 " + info.version
                                                else -> "已是最新版本"
                                            }
                                            if (info != null && isRemoteVersionNewer(info.version, APP_VERSION)) {
                                                onShowUpdate(info)
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = DesignTokens.Spacing.Sm),
                        )
                        checkState?.let {
                            Text(
                                it,
                                color = if (it.startsWith("发现") || it.startsWith("已是最新")) MaterialTheme.colorScheme.primary else Muted,
                                fontSize = DesignTokens.Type.Caption,
                            )
                        }
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
        shape = RoundedCornerShape(DesignTokens.Radius.Control),
        color = color,
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
    ) {
        if (selected) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, tint = if (color.luminanceValue() > 0.58f) Color.Black else Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * 插件总开关的本地闸门：禁用时直接给模型一条错误结论，
 * 不触发 delegate（因此不会有任何 HTTP 请求出去）。
 */
private class PluginGateHandler(
    private val delegate: ToolHandler,
    private val enabled: () -> Boolean,
    private val disabledMessage: String
) : ToolHandler {
    override val name: String get() = delegate.name
    override val description: String get() = delegate.description
    override val parametersJson: String get() = delegate.parametersJson

    override suspend fun execute(argumentsJson: String): ToolResult =
        if (enabled()) delegate.execute(argumentsJson) else ToolResult.Error(disabledMessage)
}

/** 给 [prefix] 开头的已注册工具逐个套上总开关；关闭时直接回错，实现体一行都不会跑。 */
private fun ToolRegistry.gateTools(prefix: String, enabled: () -> Boolean, disabledMessage: String) {
    all().filter { it.name == prefix || it.name.startsWith(prefix) }.forEach { inner ->
        register(PluginGateHandler(inner, enabled, disabledMessage))
    }
}


/**
 * 「插件」卡片最下面那块：已添加的自定义插件列表 + ⊕ 新增插件。
 *
 * 这里是自定义插件的唯一入口（新增 / 编辑 / 开关 / 删除），
 * 所有改动都序列化回 prefs 并由上层重建 registry，所以「保存后立刻能调」不需要重启 app。
 */
@Composable
private fun CustomToolSection(
    specs: List<CustomToolSpec>,
    existingToolNames: () -> List<String>,
    imageSink: ImageSink,
    /** 折叠状态由设置页持有（列表回收不该把展开态重置掉），键是插件名。 */
    expandedNames: Set<String>,
    onToggleExpanded: (String) -> Unit,
    onSpecsChange: (List<CustomToolSpec>) -> Unit
) {
    // null = 表单关闭；-1 = 新增；>=0 = 编辑第几条。用 specs 做 key：保存后列表变了，表单自动收起。
    var editorIndex by remember(specs) { mutableStateOf<Int?>(null) }
    Spacer(Modifier.height(DesignTokens.Spacing.Md))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    Spacer(Modifier.height(DesignTokens.Spacing.Md))
    Text("自定义工具 · " + specs.size + " 个", fontWeight = FontWeight.Bold)
    Text(
        "一个自定义插件 = 一个 AI 能调用的工具 + 一条 HTTP 执行配置。保存后立刻注册给模型，课堂对话里它就能自己决定调用；关掉开关后，当次调用会被本地直接拒绝、不发任何请求。",
        color = Muted, fontSize = DesignTokens.Type.Small, lineHeight = 18.sp
    )
    specs.forEachIndexed { index, spec ->
        Spacer(Modifier.height(DesignTokens.Spacing.Sm))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(DesignTokens.Radius.Control),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)) {
                // 默认只有一行：名称 + 开关 + ▸。用途描述、执行地址、编辑/删除都收进折叠里。
                val rowExpanded = spec.name in expandedNames
                Row(
                    Modifier.fillMaxWidth().clickable { onToggleExpanded(spec.name) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        spec.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = DesignTokens.Type.Small,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Switch(
                        checked = spec.enabled,
                        onCheckedChange = { enabled ->
                            onSpecsChange(specs.mapIndexed { i, item -> if (i == index) item.copy(enabled = enabled) else item })
                        }
                    )
                    Text(
                        if (rowExpanded) "▾" else "▸",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = DesignTokens.Type.Small,
                        modifier = Modifier.padding(start = DesignTokens.Spacing.Xs)
                    )
                }
                AnimatedVisibility(rowExpanded) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            spec.description.ifBlank { "（没有用途描述，AI 不会调用）" },
                            color = Muted, fontSize = DesignTokens.Type.Small, lineHeight = 16.sp
                        )
                        Text(
                            if (spec.enabled) {
                                spec.profile.request.method + " " + spec.profile.baseUrl.trimEnd('/') + "/" +
                                    spec.profile.request.path.trimStart('/') + " · " + responseModeLabel(spec.profile.response.type)
                            } else {
                                "已关闭：AI 调用这个工具会被直接拒绝"
                            },
                            color = if (spec.enabled) Muted else MaterialTheme.colorScheme.error,
                            fontSize = DesignTokens.Type.Small
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { editorIndex = index },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                            ) {
                                Text("编辑", color = MaterialTheme.colorScheme.primary, fontSize = DesignTokens.Type.Small)
                            }
                            TextButton(
                                onClick = { onSpecsChange(specs.filterIndexed { i, _ -> i != index }) },
                                contentPadding = PaddingValues(start = 16.dp, end = 0.dp, top = 2.dp, bottom = 2.dp),
                            ) {
                                Text("删除", color = MaterialTheme.colorScheme.error, fontSize = DesignTokens.Type.Small)
                            }
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(DesignTokens.Spacing.Sm))
    Surface(
        onClick = { editorIndex = -1 },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.Radius.Control),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("⊕ 新增插件", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
    val editing = editorIndex
    if (editing != null) {
        val initial = if (editing >= 0) specs.getOrNull(editing) else null
        CustomToolEditorDialog(
            initial = initial,
            existingNames = existingToolNames().filter { name -> initial == null || name != initial.name },
            imageSink = imageSink,
            onSave = { saved ->
                val updated = if (editing >= 0 && editing < specs.size) {
                    specs.mapIndexed { i, item -> if (i == editing) saved else item }
                } else {
                    specs + saved
                }
                editorIndex = null
                onSpecsChange(updated)
            },
            onDismiss = { editorIndex = null }
        )
    }
}

/** 响应形态的人话标签：data 是「数据回给 AI」，其余是「结果存成文件，路径回给 AI」。 */

/**
 * 新增 / 编辑表单。校验全部走 feature-exam 的 validateCustomTool + validateCustomToolExecution：
 * 只要还有一条错误，[保存] 就是灰的——校验规则和工具实现同住一个模块，不会两边漂移。
 */
@Composable
private fun CustomToolEditorDialog(
    initial: CustomToolSpec?,
    existingNames: List<String>,
    imageSink: ImageSink,
    onSave: (CustomToolSpec) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val profile = initial?.profile
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember(initial) { mutableStateOf(initial?.description.orEmpty()) }
    var parametersJson by remember(initial) { mutableStateOf(initial?.parametersJson ?: CustomToolSpec.NO_PARAMETERS_JSON) }
    var baseUrl by remember(initial) { mutableStateOf(profile?.baseUrl.orEmpty()) }
    var method by remember(initial) { mutableStateOf(profile?.request?.method ?: "GET") }
    var path by remember(initial) { mutableStateOf(profile?.request?.path.orEmpty()) }
    var bodyTemplate by remember(initial) { mutableStateOf(profile?.request?.bodyTemplate.orEmpty()) }
    var authType by remember(initial) { mutableStateOf(profile?.auth?.type ?: UniversalAuth.TYPE_NONE) }
    var authHeader by remember(initial) { mutableStateOf(profile?.auth?.header.orEmpty()) }
    var authToken by remember(initial) { mutableStateOf(profile?.auth?.token.orEmpty()) }
    var dataMode by remember(initial) { mutableStateOf(profile == null || profile.response.type == UniversalResponse.TYPE_JSON) }
    var fileType by remember(initial) {
        mutableStateOf(
            profile?.response?.type?.takeIf { it != UniversalResponse.TYPE_JSON } ?: UniversalResponse.TYPE_JSON_URL
        )
    }
    var extract by remember(initial) { mutableStateOf(profile?.response?.extract.orEmpty()) }
    var asyncEnabled by remember(initial) { mutableStateOf(profile?.async?.enabled ?: false) }
    var pollPath by remember(initial) { mutableStateOf(profile?.async?.pollPath.orEmpty()) }
    var doneField by remember(initial) { mutableStateOf(profile?.async?.doneField.orEmpty()) }
    var testOutput by remember(initial) { mutableStateOf("") }
    var testing by remember(initial) { mutableStateOf(false) }

    fun draft(): CustomToolSpec = CustomToolSpec(
        name = name.trim(),
        description = description.trim(),
        parametersJson = parametersJson.trim(),
        profile = UniversalProfile(
            name = name.trim(),
            description = description.trim(),
            baseUrl = baseUrl.trim(),
            auth = UniversalAuth(type = authType, header = authHeader.trim(), token = authToken.trim()),
            request = UniversalRequest(method = method, path = path.trim(), bodyTemplate = bodyTemplate.trim()),
            response = UniversalResponse(
                extract = extract.trim(),
                type = if (dataMode) UniversalResponse.TYPE_JSON else fileType
            ),
            async = UniversalAsync(enabled = asyncEnabled, pollPath = pollPath.trim(), doneField = doneField.trim())
        ),
        enabled = initial?.enabled ?: true
    )

    val draftSpec = draft()
    val errors = validateCustomTool(name, description, parametersJson, existingNames) +
        validateCustomToolExecution(draftSpec.profile)

    AppDialogBase(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) "新增插件" else "编辑插件「" + initial.name + "」",
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(
                Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("工具名") },
                    singleLine = true
                )
                Text(
                    "小写字母 / 数字 / 下划线，2-32 位，不能与已注册的工具重名。示例：clash_version、weather_today。",
                    color = Muted, fontSize = DesignTokens.Type.Small, lineHeight = 17.sp
                )
                errors.filter { it.contains("工具名") }.forEach {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = DesignTokens.Type.Small, lineHeight = 17.sp)
                }

                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    label = { Text("用途描述（AI 靠这句话决定要不要调用）") }
                )
                Text("好例子：「查询某城市当天的天气」——说清了什么时候该用。", color = MaterialTheme.colorScheme.primary, fontSize = DesignTokens.Type.Small, lineHeight = 17.sp)
                Text("坏例子：「天气插件」——只说了是什么，没说什么时候用，AI 无法判断。", color = Muted, fontSize = DesignTokens.Type.Small, lineHeight = 17.sp)
                errors.filter { it.contains("用途描述") }.forEach {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = DesignTokens.Type.Small, lineHeight = 17.sp)
                }

                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = parametersJson,
                    onValueChange = { parametersJson = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                    label = { Text("参数定义（JSON Schema）") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { parametersJson = CustomToolSpec.NO_PARAMETERS_JSON }) {
                        Text("无参数", fontSize = DesignTokens.Type.Small)
                    }
                    TextButton(onClick = {
                        parametersJson = "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\",\"description\":\"城市名，例如武汉\"}},\"required\":[\"city\"]}"
                    }) {
                        Text("带参数模板", fontSize = DesignTokens.Type.Small)
                    }
                }
                Text(
                    "参数名就是请求体模板里的占位符：参数 city → {{city}}。无参工具点「无参数」即可。",
                    color = Muted, fontSize = DesignTokens.Type.Small, lineHeight = 17.sp
                )
                errors.filter { it.contains("参数定义") || it.contains("properties") || it.contains("required") }.forEach {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = DesignTokens.Type.Small, lineHeight = 17.sp)
                }

                Spacer(Modifier.height(4.dp))
                Text("执行配置", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL（如 http://127.0.0.1:48597）") },
                    singleLine = true
                )
                Text("请求方法", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = DesignTokens.Type.Small, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("GET", "POST", "PUT", "PATCH", "DELETE").forEach { candidate ->
                        AppFilterChip(method == candidate, { method = candidate }) { Text(candidate, fontSize = DesignTokens.Type.Small) }
                    }
                }
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("路径（如 /version）") },
                    singleLine = true
                )
                Text("认证方式", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = DesignTokens.Type.Small, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        UniversalAuth.TYPE_NONE to "无",
                        UniversalAuth.TYPE_BEARER to "Bearer",
                        UniversalAuth.TYPE_HEADER to "请求头",
                        UniversalAuth.TYPE_BASIC to "Basic",
                        UniversalAuth.TYPE_QUERY to "Query"
                    ).forEach { (type, label) ->
                        AppFilterChip(authType == type, { authType = type }) { Text(label, fontSize = DesignTokens.Type.Small) }
                    }
                }
                if (authType == UniversalAuth.TYPE_HEADER || authType == UniversalAuth.TYPE_QUERY) {
                    OutlinedTextField(
                        value = authHeader,
                        onValueChange = { authHeader = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (authType == UniversalAuth.TYPE_HEADER) "请求头名（如 X-Api-Key）" else "Query 参数名（如 token）") },
                        singleLine = true
                    )
                }
                if (authType != UniversalAuth.TYPE_NONE) {
                    OutlinedTextField(
                        value = authToken,
                        onValueChange = { authToken = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("凭据（只保存在本机）") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = bodyTemplate,
                    onValueChange = { bodyTemplate = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    label = { Text("请求体模板（GET 不需要；{{city}} 这种占位符用参数名）") }
                )
                Text("作用方式", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = DesignTokens.Type.Small, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppFilterChip(dataMode, { dataMode = true }) { Text("数据回给 AI", fontSize = DesignTokens.Type.Small) }
                    AppFilterChip(!dataMode, { dataMode = false }) { Text("结果存成文件", fontSize = DesignTokens.Type.Small) }
                }
                if (!dataMode) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            UniversalResponse.TYPE_JSON_URL to "URL 下载",
                            UniversalResponse.TYPE_JSON_BASE64 to "base64 解码",
                            UniversalResponse.TYPE_BINARY to "原始二进制"
                        ).forEach { (type, label) ->
                            AppFilterChip(fileType == type, { fileType = type }) { Text(label, fontSize = DesignTokens.Type.Small) }
                        }
                    }
                }
                OutlinedTextField(
                    value = extract,
                    onValueChange = { extract = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (dataMode) "取值路径（可留空 = 整个响应）" else "取值路径（如 data[0].b64_json）") },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("异步：先提交再从响应取结果地址", fontSize = DesignTokens.Type.Small, modifier = Modifier.weight(1f))
                    Switch(asyncEnabled, { asyncEnabled = it })
                }
                if (asyncEnabled) {
                    OutlinedTextField(
                        value = pollPath,
                        onValueChange = { pollPath = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("轮询路径（可留空 = 提交响应里直接带结果）") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = doneField,
                        onValueChange = { doneField = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("完成标志字段（如 status）") },
                        singleLine = true
                    )
                }
                errors.filter {
                    it.contains("Base URL") || it.contains("路径") || it.contains("响应类型")
                }.forEach {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = DesignTokens.Type.Small, lineHeight = 17.sp)
                }
                if (testOutput.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(DesignTokens.Radius.Control),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Text("测试结果", fontWeight = FontWeight.Bold, fontSize = DesignTokens.Type.Small)
                            Text(testOutput.take(1500), fontSize = DesignTokens.Type.Small, lineHeight = 16.sp)
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        testing = true
                        testOutput = ""
                        scope.launch {
                            val spec = draftSpec
                            testOutput = withContext(Dispatchers.IO) { previewCustomTool(spec, imageSink, "{}") }
                            testing = false
                        }
                    },
                    enabled = !testing
                ) {
                    Text(if (testing) "测试中…" else "测试")
                }
                Button(onClick = { onSave(draftSpec) }, enabled = errors.isEmpty()) { Text("保存") }
            }
        }
    )
}

/**
 * 「插件包」列表里的一项：默认只有一行（名字 · 版本 + 开关 + ▸），点开才看到权限摘要、
 * 可折叠的配置行和卸载按钮。
 *
 * 开关的语义与原生插件（考试/文件/网络/配置/生图）一致：关掉后该插件的一切能力
 * （prompt 钩子、事件、定时器、界面插槽、工具）立即失效，状态存 prefs plugin_enabled_<id>。
 * 插件没有声明 settings 时，「配置」这一行根本不渲染。
 */
@Composable
private fun PluginPackageRow(
    info: LoadedPluginInfo,
    rowExpanded: Boolean,
    onToggleRow: () -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSettingChange: (String, String) -> Unit,
    onImagePick: (String) -> Unit,
    onImageClear: (String) -> Unit,
    onUninstall: () -> Unit,
    /** 系统自带插件（沉浸模式 / 音乐）：可以停用，但不给卸载入口。 */
    system: Boolean = false,
) {
    val unfinished = info.unfinishedSettings
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = DesignTokens.Spacing.Xs)
    ) {
        // 默认只有这一行：名字 · 版本 + 开关 + ▸。摘要（含权限）、配置、卸载都收进折叠里。
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggleRow),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                info.name + " · v" + info.version,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Switch(info.enabled, onEnabledChange)
            Text(
                if (rowExpanded) "▾" else "▸",
                color = MaterialTheme.colorScheme.primary,
                fontSize = DesignTokens.Type.Small,
                modifier = Modifier.padding(start = DesignTokens.Spacing.Xs),
            )
        }
        AnimatedVisibility(rowExpanded) {
            Column(Modifier.fillMaxWidth().padding(start = 4.dp)) {
                Text(info.summary, color = Muted, fontSize = DesignTokens.Type.Small, lineHeight = 17.sp)
                if (info.settings.isNotEmpty()) {
                    TextButton(
                        onClick = onToggleExpanded,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                    ) {
                        Text(
                            (if (expanded) "▾ " else "▸ ") + "配置（" + info.settings.size + " 项" +
                                (if (unfinished > 0) "，未完成 " + unfinished + " 项" else "") + "）",
                            color = if (unfinished > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            fontSize = DesignTokens.Type.Small,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                AnimatedVisibility(expanded) {
                    Column(Modifier.fillMaxWidth()) {
                        info.settings.forEach { setting ->
                            PluginSettingField(
                                setting = setting,
                                value = info.settingValues[setting.key].orEmpty(),
                                fit = info.settingValues[setting.key + PluginManager.FIT_SUFFIX].orEmpty(),
                                locked = !info.enabled,
                                onValueChange = { onSettingChange(setting.key, it) },
                                onPickImage = { onImagePick(setting.key) },
                                onClearImage = { onImageClear(setting.key) },
                                onFitChange = { onSettingChange(setting.key + PluginManager.FIT_SUFFIX, it) },
                            )
                        }
                        Text(
                            "改完立即生效，插件下一次被调用就能读到新值。",
                            color = Muted,
                            fontSize = DesignTokens.Type.Caption,
                            lineHeight = 16.sp,
                        )
                    }
                }
                if (system) {
                    // 系统插件：说明为什么没有卸载按钮（只说「不给按钮」会让人以为是 bug）。
                    Text(
                        "系统自带插件，可停用但不能卸载",
                        color = Muted,
                        fontSize = DesignTokens.Type.Caption,
                        lineHeight = 16.sp,
                    )
                } else {
                    TextButton(
                        onClick = onUninstall,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                    ) {
                        Text("卸载", color = MaterialTheme.colorScheme.error, fontSize = DesignTokens.Type.Small)
                    }
                }
            }
        }
    }
}

/** 折叠 / 展开某个插件的配置行（展开集合是不可变的，直接换一个新集合）。 */

/** 一个配置字段：按 type 渲染控件，必填未填 / number 越界时整行标红并给出原因。 */
@Composable
private fun PluginSettingField(
    setting: PluginSetting,
    value: String,
    /** image 字段的裁切方式（crop / fit / fill），存在 <key>Fit 这个辅助键里。 */
    fit: String = "",
    locked: Boolean,
    onValueChange: (String) -> Unit,
    onPickImage: () -> Unit = {},
    onClearImage: () -> Unit = {},
    /** 改了裁切方式：写 <key>Fit 这个辅助键，不动图片路径本身。 */
    onFitChange: (String) -> Unit = {},
) {
    // 表单自己持有草稿：宿主快照要等防抖后的 reload 才刷新，直接绑快照会把用户刚敲的
    // 每个字符立刻回滚掉。prefs 在每次改动时同步写入，所以草稿丢了也能从磁盘恢复。
    var draft by remember(setting.key) { mutableStateOf(value) }
    val update: (String) -> Unit = { next ->
        draft = next
        onValueChange(next)
    }
    val numberProblem = if (setting.type == PluginSettingType.NUMBER && draft.isNotBlank()) {
        PluginSettingsCodec.numberProblem(setting, draft)
    } else {
        null
    }
    val blank = draft.isBlank()
    val missing = setting.required && (blank || numberProblem != null)
    val labelColor = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                setting.label + (if (setting.required) " *" else ""),
                color = labelColor,
                fontWeight = FontWeight.Bold,
                fontSize = DesignTokens.Type.Small,
                modifier = Modifier.weight(1f),
            )
            if (setting.type == PluginSettingType.BOOLEAN) {
                Switch(draft == "true", { update(if (it) "true" else "false") }, enabled = !locked)
            }
        }
        when (setting.type) {
            PluginSettingType.BOOLEAN -> Unit
            PluginSettingType.SELECT -> PluginSelectField(setting, draft, locked, update)
            PluginSettingType.SECRET -> PluginSecretField(setting, draft, locked, update)
            // image：显示缩略图 + 选择 / 清除，再加一个三选一的裁切方式。
            PluginSettingType.IMAGE -> PluginImageField(
                value = draft,
                fit = fit,
                locked = locked,
                onPick = onPickImage,
                onClear = {
                    update("")
                    onClearImage()
                },
                onFitChange = onFitChange,
            )

            // number + slider：用滑条而不是输入框。拖动时每动一下就写回设置，
            // 宿主保存后会重建插件 runtime，插件下一次 provideState 读到新的 ctx.settings，
            // 于是「拖一下就变」是自动成立的（不需要额外的实时通道）。
            PluginSettingType.NUMBER -> if (setting.slider && setting.min != null && setting.max != null) {
                PluginSliderField(setting, draft, locked, update)
            } else {
                PluginTextField(setting, draft, locked, numberProblem != null, update)
            }

            else -> PluginTextField(setting, draft, locked, numberProblem != null, update)
        }
        // 说明与错误各占一行：填错时说明仍然看得见，用户才知道这一项该去哪儿拿。
        val description = setting.description
        if (!description.isNullOrBlank()) {
            Text(
                description,
                color = Muted,
                fontSize = DesignTokens.Type.Caption,
                lineHeight = 16.sp,
            )
        }
        val problem = when {
            missing && blank -> "必填项未填"
            numberProblem != null -> numberProblem
            else -> null
        }
        if (problem != null) {
            Text(
                problem,
                color = MaterialTheme.colorScheme.error,
                fontSize = DesignTokens.Type.Caption,
                lineHeight = 16.sp,
            )
        }
    }
}

/**
 * number 用滑条呈现（manifest 写 `"slider": true` + min/max）。
 *
 * 右侧实时显示当前值，用户拖的时候能看见数字在变；
 * 拖动过程中就调 onValueChange —— 设置页保存后会重建插件 runtime，
 * 所以悬浮窗会跟着一起变，不需要重启。
 */
@Composable
private fun PluginSliderField(
    setting: PluginSetting,
    value: String,
    locked: Boolean,
    onValueChange: (String) -> Unit,
) {
    val min = (setting.min ?: 0.0).toFloat()
    val max = (setting.max ?: 100.0).toFloat()
    val current = value.toFloatOrNull()?.coerceIn(min, max) ?: min
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = current,
            onValueChange = { v ->
                // 整数化：尺寸这类参数没必要拖出小数。
                onValueChange(v.roundToInt().toString())
            },
            valueRange = min..max,
            enabled = !locked,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(DesignTokens.Spacing.Md))
        Text(
            text = current.roundToInt().toString(),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = DesignTokens.Type.Caption,
            modifier = Modifier.width(40.dp),
        )
    }
}

@Composable
private fun PluginTextField(
    setting: PluginSetting,
    value: String,
    locked: Boolean,
    isError: Boolean,
    onValueChange: (String) -> Unit,
) {
    val keyboard = if (setting.type == PluginSettingType.NUMBER) {
        KeyboardOptions(keyboardType = KeyboardType.Number)
    } else {
        KeyboardOptions.Default
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        // 字段名已经在上面那行，这里只放 placeholder，避免同一个名字出现两次。
        placeholder = setting.placeholder?.let { { Text(it) } },
        singleLine = true,
        enabled = !locked,
        isError = isError,
        keyboardOptions = keyboard,
    )
}

/**
 * secret：默认掩码；点「显示」明文显示 15 秒后自动回掩码，
 * 免得用户把 token 举在屏幕上被摄像头或旁人看到。
 */
@Composable
private fun PluginSecretField(
    setting: PluginSetting,
    value: String,
    locked: Boolean,
    onValueChange: (String) -> Unit,
) {
    var revealed by remember(setting.key) { mutableStateOf(false) }
    LaunchedEffect(revealed, value) {
        if (revealed) {
            delay(15_000)
            revealed = false
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = setting.placeholder?.let { { Text(it) } },
        singleLine = true,
        enabled = !locked,
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(onClick = { revealed = !revealed }) {
                Text(
                    if (revealed) "隐藏" else "显示",
                    fontSize = DesignTokens.Type.Caption,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

/**
 * image：一张用户自选的本地图片。
 *
 * 文件由宿主复制进插件私有目录（filesDir/plugins/<id>/user-images/），prefs 里只存路径，
 * 插件脚本永远拿不到原始 uri，也不需要处理二进制 —— ui.json 里写 `"src": "user:<key>"` 即可。
 */
@Composable
private fun PluginImageField(
    value: String,
    fit: String,
    locked: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
    onFitChange: (String) -> Unit,
) {
    val bitmap = rememberPluginImage(value)
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(DesignTokens.Radius.Control),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(DesignTokens.Border.Hairline, MaterialTheme.colorScheme.outline),
        ) {
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
                }
            }
        }
        TextButton(onClick = onPick, enabled = !locked) { Text("选择图片") }
        if (value.isNotBlank()) {
            TextButton(onClick = onClear, enabled = !locked) {
                Text("清除", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (value.isNotBlank()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("crop" to "居中裁切", "fit" to "适应", "fill" to "填充").forEach { (key, label) ->
                AppFilterChip((if (fit.isBlank()) "crop" else fit) == key, { if (!locked) onFitChange(key) }) { Text(label) }
            }
        }
    }
}

/** 读设置里那张图的缩略图：路径来自 prefs，读不到就当作没选（不抛异常）。 */
@Composable
private fun rememberPluginImage(path: String): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(path)
                    if (!file.isFile) null else BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return bitmap
}

/** select：与设置页其它单选一致，用横向 chips（选项多时可横向滑动）。 */
@Composable
private fun PluginSelectField(
    setting: PluginSetting,
    value: String,
    locked: Boolean,
    onValueChange: (String) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        setting.options.forEach { option ->
            AppFilterChip(value == option, { if (!locked) onValueChange(option) }) { Text(option) }
        }
    }
}


/**
 * 设置页里的一行插件：默认只占一行（图标 + 名称·工具数 + 开关 + ▸）。
 *
 * 一句话说明、补充提示、行内附加内容（网络白名单）全部收进折叠里，
 * 点整行（或右侧 ▸）展开。展开状态由设置页持有，滚动回收不会把它重置掉。
 */
@Composable
private fun PluginRow(
    icon: ImageVector,
    title: String,
    toolCount: Int,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    /** 原来贴在行下面的几句提示小字，折叠后才显示。 */
    notes: List<String> = emptyList(),
    /** 行内附加的折叠内容（如网络工具的网页操作白名单）。 */
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    Column(Modifier.fillMaxWidth().padding(vertical = DesignTokens.Spacing.Xs)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                title + " · " + toolCount + " 个工具",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Switch(enabled, onEnabledChange)
            Text(
                if (expanded) "▾" else "▸",
                color = MaterialTheme.colorScheme.primary,
                fontSize = DesignTokens.Type.Small,
                modifier = Modifier.padding(start = DesignTokens.Spacing.Xs)
            )
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(description, color = Muted, fontSize = DesignTokens.Type.Small, lineHeight = 18.sp)
                notes.forEach { note ->
                    Text(note, color = Muted, fontSize = DesignTokens.Type.Small, lineHeight = 18.sp)
                }
                extra()
            }
        }
    }
}

/**
 * 网页操作白名单：AI 只有在这个清单里的站点上，才能替用户点页面 / 填表。
 *
 * 这张卡是用户唯一的入口——AI 没有工具能改它（web_act 遇到新域名只能"申请"，用户点了「允许」
 * 才会经由弹框里的同一条路径写进来）。留空就等于彻底禁止代操作，只保留截图与搜索。
 */
@Composable
private fun WebActWhitelistSection(domains: List<String>, onChange: (List<String>) -> Unit) {
    var draft by remember(domains) { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        Text("网页操作白名单 · " + domains.size + " 个站点", fontWeight = FontWeight.Bold)
        Text(
            "只有这个清单里的网站，AI 才能替你点按钮、填表。第一次碰到新网站时会当场弹框问你；拒绝就什么都不会做。" +
                "密码框任何时候都填不了——密码必须由你自己输入。",
            color = Muted, fontSize = DesignTokens.Type.Small, lineHeight = 18.sp
        )
        Spacer(Modifier.height(DesignTokens.Spacing.Sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                draft, { draft = it }, Modifier.weight(1f),
                label = { Text("添加域名，例如 example.com") }, singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val added = WebDomains.parseList(draft)
                    if (added.isNotEmpty()) {
                        onChange((domains + added).distinct())
                        draft = ""
                    }
                },
                shape = AppShapes.button
            ) { Text("添加") }
        }
        if (domains.isEmpty()) {
            Text("清单为空：AI 目前不能在任何网站代你操作。", color = Muted, fontSize = DesignTokens.Type.Small)
        } else {
            domains.forEach { domain ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(domain, Modifier.weight(1f), fontSize = DesignTokens.Type.Body)
                    TextButton(onClick = { onChange(domains - domain) }) { Text("移除", color = MaterialTheme.colorScheme.error) }
                }
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
                Spacer(Modifier.height(DesignTokens.Spacing.Md))
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
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(DesignTokens.Spacing.Sm)
                        .background(if (selected) DesignTokens.Accent else Color.Transparent, RoundedCornerShape(DesignTokens.Radius.Pill))
                )
                Spacer(Modifier.width(DesignTokens.Spacing.Xs))
                label()
            }
        },
        shape = RoundedCornerShape(DesignTokens.Radius.Chip),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.onSurface,
            selectedLabelColor = MaterialTheme.colorScheme.background,
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = MaterialTheme.colorScheme.onSurface
        )
    )
}


@Composable
private fun ButtonColorPreview(buttonValue: Long) {
    val button = colorFromLong(buttonValue)
    val onButton = if (button.luminanceValue() > 0.58f) DesignTokens.Legacy.OnButtonOnLight else Color.White
    Card(Modifier.fillMaxWidth(), shape = AppShapes.card, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("按钮预览", fontWeight = FontWeight.Bold)
            Text("背景和文字保持原生明暗风格，只有按钮与可点击强调使用此颜色。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = DesignTokens.Type.Small)
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
    val codeBg = if (MaterialTheme.colorScheme.background.luminanceValue() > 0.5f) DesignTokens.Light.CodeBg else DesignTokens.Dark.CodeBg
    val hairline = MaterialTheme.colorScheme.outline
    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge.copy(fontSize = DesignTokens.Type.Body, lineHeight = DesignTokens.Type.LineBody)) {
    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Xs)) {
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("```") -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(codeBg, RoundedCornerShape(DesignTokens.Radius.Tile))
                        .border(DesignTokens.Border.Hairline, hairline, RoundedCornerShape(DesignTokens.Radius.Tile))
                        .padding(horizontal = DesignTokens.Spacing.Md, vertical = DesignTokens.Spacing.Sm)
                ) {
                    Text(trimmed, fontFamily = FontFamily.Monospace, fontSize = DesignTokens.Type.Small, lineHeight = DesignTokens.Type.LineBody, color = mutedColor)
                }
                trimmed.startsWith("###") -> Text(trimmed.trimStart('#', ' '), fontWeight = DesignTokens.Weight.Bold, fontSize = DesignTokens.Type.Subtitle, lineHeight = DesignTokens.Type.LineSnug, color = textColor, modifier = Modifier.padding(top = DesignTokens.Spacing.Xs))
                trimmed.startsWith("##") -> Text(trimmed.trimStart('#', ' '), fontWeight = DesignTokens.Weight.Bold, fontSize = DesignTokens.Type.Title, lineHeight = DesignTokens.Type.LineSnug, color = textColor, modifier = Modifier.padding(top = DesignTokens.Spacing.Xs))
                trimmed.startsWith("#") -> Text(trimmed.trimStart('#', ' '), fontWeight = DesignTokens.Weight.Bold, fontSize = DesignTokens.Type.Headline, lineHeight = DesignTokens.Type.LineBody, color = textColor, modifier = Modifier.padding(top = DesignTokens.Spacing.Xs))
                trimmed.startsWith("-") || trimmed.startsWith("*") -> Text("• ${trimmed.drop(1).trim()}", color = textColor, fontSize = DesignTokens.Type.Body, lineHeight = DesignTokens.Type.LineBody, modifier = Modifier.padding(start = DesignTokens.Spacing.Md))
                isMathLikeLine(trimmed) -> Text(trimmed, fontFamily = FontFamily.Monospace, color = mathColor, lineHeight = 21.sp)
                else -> Text(buildInlineMarkdown(trimmed), color = textColor, lineHeight = 21.sp)
            }
        }
    }
    }
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = AppShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(DesignTokens.Border.Hairline, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(horizontal = DesignTokens.Spacing.CardHorizontal, vertical = DesignTokens.Spacing.CardVertical), content = content)
    }
}

private fun newClassroom(number: Int, config: ClassroomConfig = ClassroomConfig()) = Classroom(
    name = "课堂 $number",
    topic = "自定义学习内容",
    summary = "",
    messages = mutableStateListOf(),
    branches = mutableStateListOf(),
    memories = mutableStateListOf("等待开始。"),
    corePremises = mutableStateListOf(),
    memoryCards = mutableStateListOf(),
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

    /** AI 网络调用总开关，默认开启；关闭后 net_request 在本地直接拒绝，不发任何请求。 */
    fun loadNetToolsEnabled(): Boolean = prefs.getBoolean("net_tools_enabled", true)

    fun saveNetToolsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("net_tools_enabled", enabled).apply()
    }

    /** 考试 / 文件 / 配置三组插件的开关，默认开启；关闭后对应工具在本地直接拒绝，实现体不执行。 */
    fun loadExamToolsEnabled(): Boolean = prefs.getBoolean("plugin_exam_enabled", true)

    fun saveExamToolsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("plugin_exam_enabled", enabled).apply()
    }

    /** 视频工具独立开关，默认开启；抽帧吃 CPU，关掉后 video_frames 在本地直接拒绝。 */
    fun loadVideoToolsEnabled(): Boolean = prefs.getBoolean("video_tools_enabled", true)

    fun saveVideoToolsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("video_tools_enabled", enabled).apply()
    }

    fun loadFsToolsEnabled(): Boolean = prefs.getBoolean("plugin_fs_enabled", true)

    fun saveFsToolsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("plugin_fs_enabled", enabled).apply()
    }

    fun loadConfigToolsEnabled(): Boolean = prefs.getBoolean("plugin_config_enabled", true)

    fun saveConfigToolsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("plugin_config_enabled", enabled).apply()
    }

    /**
     * web_act 的域名白名单：只有用户显式放行的站点，AI 才能替他点页面 / 填表。
     * 存成一串逗号分隔的 host，空串表示还没放行任何站点。
     */
    fun loadWebActDomains(): List<String> = WebDomains.parseList(prefs.getString("web_act_domains", "").orEmpty())

    fun saveWebActDomains(domains: List<String>) {
        prefs.edit().putString("web_act_domains", WebDomains.joinList(domains.map { WebDomains.normalize(it) }.filter { it.isNotBlank() }.distinct())).apply()
    }

    /** 生图插件总开关，默认开启；关闭后 generate_image 在本地直接拒绝，不发任何请求。 */
    /** 插件包总开关：一次一个键 plugin_enabled_<id>，缺省为开。 */
    fun loadPluginEnabled(pluginId: String): Boolean = prefs.getBoolean("plugin_enabled_$pluginId", true)

    fun savePluginEnabled(pluginId: String, enabled: Boolean) {
        prefs.edit().putBoolean("plugin_enabled_$pluginId", enabled).apply()
    }

    /**
     * 插件配置：整张表以 JSON 存在 plugin_settings_<id>。解析失败按「没配过」处理，
     * 插件写坏的配置不能连带把设置页打挂。
     */
    fun loadPluginSettings(pluginId: String): Map<String, String> {
        val raw = prefs.getString("plugin_settings_$pluginId", null)
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            val result = LinkedHashMap<String, String>()
            for (key in obj.keys()) result[key] = obj.optString(key, "")
            result
        }.getOrElse { emptyMap() }
    }

    fun savePluginSetting(pluginId: String, key: String, value: String) {
        savePluginSettings(pluginId, loadPluginSettings(pluginId) + (key to value))
    }

    fun savePluginSettings(pluginId: String, values: Map<String, String>) {
        val obj = JSONObject()
        values.forEach { (key, value) -> obj.put(key, value) }
        prefs.edit().putString("plugin_settings_$pluginId", obj.toString()).apply()
    }

    /**
     * 音乐插件总开关（读取正在播放 / 控制播放器），默认开启。
     *
     * 独立于网络与生图：它读的是别的 App 的播放状态，与「能不能发 http」没有关系。
     */
    fun loadMediaToolsEnabled(): Boolean = prefs.getBoolean("media_tools_enabled", true)

    fun saveMediaToolsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("media_tools_enabled", enabled).apply()
    }

    fun loadImageToolsEnabled(): Boolean = prefs.getBoolean("image_tools_enabled", true)

    fun saveImageToolsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("image_tools_enabled", enabled).apply()
    }

    /** 用户导入/自定义的 universal profile 库（JSON）；空串表示只用内置的 nightmare / openai-images / sd-webui。 */
    fun loadUniversalProfiles(): String = prefs.getString("universal_profiles", "").orEmpty()

    fun saveUniversalProfiles(json: String) {
        prefs.edit().putString("universal_profiles", json).apply()
    }

    /** 用户自定义插件库（JSON 数组）；空串表示一个都没加。 */
    fun loadCustomTools(): String = prefs.getString("custom_tools", "").orEmpty()

    fun saveCustomTools(json: String) {
        prefs.edit().putString("custom_tools", json).apply()
    }

    /** 生图配置逐项存在 prefs 里：apiKey 单独一条，读不到就用 [ImageConfig] 的默认构造。 */
    fun loadImageConfig(): ImageConfig {
        val defaults = ImageConfig()
        return ImageConfig(
            provider = prefs.getString("image_provider", null) ?: defaults.provider,
            baseUrl = prefs.getString("image_base_url", null) ?: defaults.baseUrl,
            apiKey = prefs.getString("image_api_key", null) ?: defaults.apiKey,
            model = prefs.getString("image_model", null) ?: defaults.model,
            size = prefs.getString("image_size", null) ?: defaults.size,
            steps = prefs.getInt("image_steps", defaults.steps),
            // prefs 没有 putDouble：cfgScale 存成字符串，避免 float 截断。
            cfgScale = prefs.getString("image_cfg_scale", null)?.toDoubleOrNull() ?: defaults.cfgScale,
            sampler = prefs.getString("image_sampler", null) ?: defaults.sampler,
            customUrl = prefs.getString("image_custom_url", null) ?: defaults.customUrl,
            customBodyTemplate = prefs.getString("image_custom_body_template", null) ?: defaults.customBodyTemplate
        )
    }

    fun saveImageConfig(config: ImageConfig) {
        prefs.edit()
            .putString("image_provider", config.provider)
            .putString("image_base_url", config.baseUrl)
            .putString("image_api_key", config.apiKey)
            .putString("image_model", config.model)
            .putString("image_size", config.size)
            .putInt("image_steps", config.steps)
            .putString("image_cfg_scale", config.cfgScale.toString())
            .putString("image_sampler", config.sampler)
            .putString("image_custom_url", config.customUrl)
            .putString("image_custom_body_template", config.customBodyTemplate)
            .apply()
    }

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
        summary = optString("summary", ""),
        messages = optJSONArray("messages").toMessages().filterNot { it.isLegacyWelcomeMessage() }.toMutableStateList(),
        branches = optJSONArray("branches").toBranches().toMutableStateList(),
        memories = optJSONArray("memories").toStrings().ifEmpty { listOf("等待开始。") }.toMutableStateList(),
        corePremises = optJSONArray("corePremises").toStrings().toMutableStateList(),
        memoryCards = optJSONArray("memoryCards").toMemoryCards().toMutableStateList(),
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
            reasoningEffort = configJson.optString("reasoningEffort", "medium"),
            activeChatModel = configJson.optString("activeChatModel", ""),
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
            worldBook = configJson.optString("worldBook", ""),
            efficientMode = configJson.optBoolean("efficientMode", true),
            reverseConversation = configJson.optBoolean("reverseConversation", false),
            themeMode = normalizeThemeMode(configJson.optString("themeMode", "custom")),
            interfaceMode = normalizeInterfaceMode(configJson.optString("interfaceMode", legacyInterfaceMode(configJson.optString("themeMode", "custom")))),
            primaryColor = configJson.optLong("primaryColor", 0xFF667085),
            secondaryColor = configJson.optLong("secondaryColor", 0xFF667085)
        ),
        // 向后兼容：旧存档没有 exam 字段 → 读成 null；单份考试解析失败也只丢考试，不影响整份课堂。
        exam = runCatching { this.optJSONObject("exam")?.toExamSession() }.getOrNull(),
        // 同理：旧存档没有 examRecords → 空列表，记忆页显示空态而不是报错。
        examRecords = runCatching { toExamRecords() }.getOrDefault(emptyList())
    )
}

private fun Classroom.toJson() = JSONObject().apply {
    put("name", name)
    put("topic", topic)
    put("summary", summary)
    put("messages", JSONArray(messages.map { it.toMessageJson() }))
    put("branches", JSONArray(branches.map { branch ->
        JSONObject().put("title", branch.title).put("source", branch.source).put("memory", branch.memory)
            .put("messages", JSONArray(branch.messages.map { it.toMessageJson() }))
            .put("context", JSONArray(branch.context.map { it.toMessageJson() }))
    }))
    put("memories", JSONArray(memories))
    put("corePremises", JSONArray(corePremises))
    put("memoryCards", JSONArray(memoryCards.map { JSONObject().put("title", it.title).put("summary", it.summary).put("keywords", JSONArray(it.keywords)).put("type", it.type).put("priority", it.priority).put("startIndex", it.startIndex).put("endIndex", it.endIndex).put("visibility", it.visibility).put("knownBy", JSONArray(it.knownBy)).put("unknownBy", JSONArray(it.unknownBy)).put("evidence", it.evidence) }))
    put("chapters", JSONArray(chapters.map { JSONObject().put("title", it.title).put("summary", it.summary).put("startIndex", it.startIndex).put("endIndex", it.endIndex) }))
    put("files", JSONArray(files.map { JSONObject().put("name", it.name).put("type", it.type).put("chars", it.chars).put("preview", it.preview).put("content", it.content).put("kind", it.kind) }))
    exam?.let { put("exam", it.toExamJson()) }
    put("examRecords", JSONArray(examRecords.map { it.toExamRecordJson() }))
    put("config", JSONObject().put("provider", config.provider).put("apiKey", config.apiKey).put("baseUrl", config.baseUrl).put("selectedModel", config.selectedModel).put("customModel", config.customModel).put("modelChain", config.modelChain).put("deepThinkingEnabled", config.deepThinkingEnabled).put("deepThinkingModel", config.deepThinkingModel).put("reasoningEffort", config.reasoningEffort).put("activeChatModel", config.activeChatModel).put("visionProvider", config.visionProvider).put("visionApiKey", config.visionApiKey).put("visionBaseUrl", config.visionBaseUrl).put("visionModel", config.visionModel).put("ttsProvider", config.ttsProvider).put("ttsApiKey", config.ttsApiKey).put("ttsBaseUrl", config.ttsBaseUrl).put("ttsModel", config.ttsModel).put("ttsVoice", config.ttsVoice).put("ttsAutoRead", config.ttsAutoRead).put("mentorName", config.mentorName).put("userAlias", config.userAlias).put("mentorPrompt", config.mentorPrompt).put("worldBook", config.worldBook).put("efficientMode", config.efficientMode).put("reverseConversation", config.reverseConversation).put("themeMode", config.themeMode).put("interfaceMode", config.interfaceMode).put("primaryColor", config.primaryColor).put("secondaryColor", config.secondaryColor))
}

/** 一道题的写入格式：answer = 考生作答，referenceAnswer = 参考解析，两者独立字段。 */
private fun ExamQuestion.toExamQuestionJson(): JSONObject = JSONObject().apply {
    put("premise", premise)
    put("question", question)
    put("answer", answer)
    put("unknown", unknown)
    put("referenceAnswer", referenceAnswer)
    // 图片答案：只在真有图时才写这个字段，纯文字考试的存档格式与改造前逐字节一致。
    if (answerImages.isNotEmpty()) put("answerImages", JSONArray(answerImages))
}

private fun JSONArray.toExamQuestions(): List<ExamQuestion> = List(length()) { index ->
    optJSONObject(index)
}.filterNotNull().map { item ->
    ExamQuestion(
        premise = item.optString("premise", ""),
        question = item.optString("question", ""),
        answer = item.optString("answer", ""),
        unknown = item.optBoolean("unknown", false),
        referenceAnswer = item.optString("referenceAnswer", ""),
        // 旧存档没有 answerImages → 空列表（不报错）；条目坏掉只丢那一张。
        answerImages = runCatching {
            item.optJSONArray("answerImages")?.let { array ->
                List(array.length()) { index -> array.optString(index, "") }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }.orEmpty()
        }.getOrDefault(emptyList())
    )
}

/** 考试存档的写入格式：题目格式与历史记录共用 [toExamQuestionJson]。 */
private fun ExamSession.toExamJson(): JSONObject = JSONObject().apply {
    put("title", title)
    put("draft", draft)
    put("submitted", submitted)
    put("questions", JSONArray(questions.map { it.toExamQuestionJson() }))
}

/** 旧存档没有 exam 字段 → null（不报错、不丢其它数据）；字段缺失一律走默认值。 */
private fun JSONObject.toExamSession(): ExamSession? {
    val questions = optJSONArray("questions")?.toExamQuestions().orEmpty()
    if (questions.isEmpty()) return null
    return ExamSession(
        title = optString("title", "AI 随堂考试"),
        questions = questions.toMutableStateList(),
        draft = optString("draft", ""),
        submitted = optBoolean("submitted", false)
    )
}

/** 一条考试记录：题目与作答一起留档，否则记忆页点开只能看到数字。 */
private fun ExamRecord.toExamRecordJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("startedAt", startedAt)
    put("submittedAt", submittedAt)
    put("questionCount", questionCount)
    put("answeredCount", answeredCount)
    put("submitted", submitted)
    put("questions", JSONArray(questions.map { it.toExamQuestionJson() }))
}

/** 旧存档没有 examRecords → 空列表；单条坏掉只丢那一条，不影响整份课堂。 */
private fun JSONObject.toExamRecords(): List<ExamRecord> {
    val items = optJSONArray("examRecords") ?: return emptyList()
    return List(items.length()) { index -> items.optJSONObject(index) }
        .filterNotNull()
        .map { item ->
            ExamRecord(
                id = item.optString("id"),
                title = item.optString("title", "AI 随堂考试"),
                startedAt = item.optLong("startedAt", 0L),
                submittedAt = item.optLong("submittedAt", 0L),
                questionCount = item.optInt("questionCount", 0),
                answeredCount = item.optInt("answeredCount", 0),
                submitted = item.optBoolean("submitted", true),
                questions = item.optJSONArray("questions")?.toExamQuestions().orEmpty()
            )
        }
}

/**
 * 读一条消息。images 是后加的字段：旧存档没有它 → 空列表（不报错、不影响其它字段）。
 * 条目本身坏掉只丢那一张图，不让整条消息读不出来。
 */
private fun JSONObject.toChatMessage(): ChatMessage = ChatMessage(
    role = optString("role"),
    text = optString("text"),
    images = runCatching {
        optJSONArray("images")?.let { array ->
            List(array.length()) { index -> array.optString(index, "") }
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }.orEmpty()
    }.getOrDefault(emptyList())
)

private fun JSONArray?.toMessages(): List<ChatMessage> = if (this == null) emptyList() else List(length()) { getJSONObject(it).toChatMessage() }

/** 只写有图的那些条目；纯文字消息的存档格式与改造前逐字节一致。 */
private fun ChatMessage.toMessageJson(): JSONObject = JSONObject().put("role", role).put("text", text).apply {
    if (images.isNotEmpty()) put("images", JSONArray(images))
}
private fun ChatMessage.isLegacyWelcomeMessage(): Boolean = role == "assistant" && text.startsWith("输入学习目标，我会开始主课堂教学。")
private fun JSONArray?.toBranches(): List<BranchClass> = if (this == null) emptyList() else List(length()) { getJSONObject(it).let { item ->
    val messages = item.optJSONArray("messages").toMessages().toMutableStateList()
    val context = item.optJSONArray("context").toMessages().ifEmpty { messages.take(BRANCH_CONTEXT_LIMIT) }.toMutableStateList()
    BranchClass(item.optString("title"), item.optString("source"), messages, item.optString("memory"), context)
} }
private fun JSONArray?.toChapters(): List<ConversationChapter> = if (this == null) emptyList() else List(length()) { getJSONObject(it).let { item -> ConversationChapter(item.optString("title"), item.optString("summary"), item.optInt("startIndex"), item.optInt("endIndex")) } }
private fun JSONArray?.toMemoryCards(): List<MemoryCard> = if (this == null) emptyList() else List(length()) { getJSONObject(it).let { item ->
    MemoryCard(
        item.optString("title"),
        item.optString("summary"),
        item.optJSONArray("keywords").toStrings().filter { word -> word.isNotBlank() },
        item.optString("type", "concept"),
        item.optInt("priority", 1),
        item.optInt("startIndex", 0),
        item.optInt("endIndex", 0),
        normalizeVisibility(item.optString("visibility", "public")),
        item.optJSONArray("knownBy").toStrings().map { name -> name.trim() }.filter { name -> name.isNotBlank() }.distinct(),
        item.optJSONArray("unknownBy").toStrings().map { name -> name.trim() }.filter { name -> name.isNotBlank() }.distinct(),
        item.optString("evidence", "")
    )
} }
private fun JSONArray?.toFiles(): List<KnowledgeFile> = if (this == null) emptyList() else List(length()) { getJSONObject(it).let { item ->
    val preview = item.optString("preview")
    KnowledgeFile(item.optString("name"), item.optString("type"), item.optInt("chars"), preview, item.optString("content", preview), item.optString("kind", "reference"))
} }
private fun <T> List<T>.toMutableStateList() = mutableStateListOf<T>().also { it.addAll(this) }


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

private suspend fun generateHomeQuote(config: ClassroomConfig, models: List<String>): String {
    if (config.apiKey.isBlank()) return ""
    val result = callChatWithFallback(
        config,
        models,
        "你只输出一句简短中文学习格言，不超过 24 个汉字，不要解释，不要加引号。",
        listOf(ChatMessage("user", "生成一句适合今天开始学习的短格言。"))
    ).trim().trim('"', '“', '”')
    if (result.isBlank() || isApiFailure(result) || result.contains("API Key") || result.contains("调用失败")) return ""
    return result.replace(Regex("\\s+"), " ").take(36)
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





private suspend fun callChat(baseUrl: String, apiKey: String, model: String, system: String, messages: List<ChatMessage>, deepThinkingEnabled: Boolean = false, reasoningEffort: String = "medium", temperature: Double = 0.35): String = withContext(Dispatchers.IO) {
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
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(buildJson(model, system, messages, deepThinkingEnabled = deepThinkingEnabled, reasoningEffort = reasoningEffort, temperature = temperature)) }
        Regex("\\\"content\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").find(readBody(connection))?.groupValues?.get(1)?.unescapeJson() ?: NO_CONTENT_REPLY
    }.getOrElse { "调用失败：${it.message ?: "未知错误"}" }
}

private suspend fun callChatStream(
    baseUrl: String,
    apiKey: String,
    model: String,
    system: String,
    messages: List<ChatMessage>,
    onDelta: (String) -> Unit,
    deepThinkingEnabled: Boolean = false,
    reasoningEffort: String = "medium",
    toolsJson: String? = null,
    toolSink: ToolCallSink? = null,
    temperature: Double = 0.35
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
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(buildJson(model, system, messages, stream = true, deepThinkingEnabled = deepThinkingEnabled, reasoningEffort = reasoningEffort, toolsJson = toolsJson, temperature = temperature)) }
        if (connection.responseCode !in 200..299) return@runCatching readBody(connection).ifBlank { "调用失败：HTTP ${connection.responseCode}" }
        val builder = StringBuilder()
        // 只有开了工具的请求才做整包 JSON 兜底：纯聊天路径保持"读不到 data: 行就是没有内容"的老行为。
        val wholeBody = StringBuilder()
        var sawEventStream = false
        BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).useLines { lines ->
            lines.forEach { line ->
                if (!sawEventStream && !line.startsWith("data:")) {
                    if (toolSink != null) wholeBody.append(line).append('\n')
                    return@forEach
                }
                sawEventStream = true
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") return@forEach
                // 流式 tool_calls 是分片到达的，按 index 累积后统一归一化。
                toolSink?.mergeDelta(payload)
                val delta = parseStreamDelta(payload)
                if (delta.isNotEmpty()) {
                    builder.append(delta)
                    withContext(Dispatchers.Main) { onDelta(delta) }
                }
            }
        }
        if (!sawEventStream && toolSink != null) {
            // 服务端忽略了 stream=true：整包解析 message.content / message.tool_calls。
            val turn = parseNonStreamTurn(wholeBody.toString())
            toolSink.setRawJson(turn.toolCallsJson)
            if (turn.content.isNotEmpty()) {
                builder.append(turn.content)
                withContext(Dispatchers.Main) { onDelta(turn.content) }
            }
        }
        builder.toString().ifBlank { NO_CONTENT_REPLY }
    }.getOrElse { "调用失败：${it.message ?: "未知错误"}" }
}

private suspend fun callChatWithFallback(config: ClassroomConfig, models: List<String>, system: String, messages: List<ChatMessage>): String {
    val candidates = models.ifEmpty { config.orderedModels() }
    var last = "请先选择或填写模型名。"
    candidates.forEach { model ->
        val result = callChat(config.baseUrl, config.apiKey, model, system, messages, config.deepThinkingEnabled, config.reasoningEffort, config.temperature)
        if (!isApiFailure(result)) return result
        last = result
    }
    return last
}

private suspend fun generateClassroomIdentity(
    config: ClassroomConfig,
    models: List<String>,
    firstUserMessage: String,
    firstAssistantMessage: String
): Pair<String, String>? {
    if (firstUserMessage.isBlank()) return null
    val prompt = """
请根据下面 AI 课堂的第一轮对话，为课堂生成一个简洁、准确的身份信息。
严格只输出两行纯文本：
名称：4-16 个中文字符的主题短语，不要加引号，不要使用“课堂”后缀；
简介：一句 20-40 字的话，概括这节课要学习或解决什么。
不要编造对话中没有出现的目标，不要输出 Markdown 或其他内容。

用户：$firstUserMessage
AI：$firstAssistantMessage
""".trimIndent()
    val raw = callChatWithFallback(
        config,
        models,
        "你是一个保守、准确的课堂信息整理助手。",
        listOf(ChatMessage("user", prompt))
    )
    return parseClassroomIdentity(raw, firstUserMessage)
}



private suspend fun callChatStreamWithFallback(
    config: ClassroomConfig,
    models: List<String>,
    system: String,
    messages: List<ChatMessage>,
    toolsJson: String? = null,
    toolSink: ToolCallSink? = null,
    onDelta: (String) -> Unit
): String {
    val candidates = models.ifEmpty { config.orderedModels() }
    var last = "请先选择或填写模型名。"
    candidates.forEach { model ->
        // 换模型重试时清空上一轮累积的 tool_calls，避免把两个模型的调用拼在一起。
        toolSink?.reset()
        val result = callChatStream(config.baseUrl, config.apiKey, model, system, messages, onDelta, config.deepThinkingEnabled, config.reasoningEffort, toolsJson, toolSink, config.temperature)
        if (!isApiFailure(result)) return result
        last = result
    }
    return last
}

private suspend fun callVisionWithFallback(config: ClassroomConfig, models: List<String>, system: String, prompt: String, dataUrl: String): String =
    callVisionWithFallback(config, models, system, prompt, listOf(dataUrl), emptyList())

/**
 * 识图调用：图片可以是 data URL（历史里的调用方式），也可以传本地路径由这里转 data URL。
 * [history] 是这一轮之前的对话，按普通聊天气泡拼在最后一条带图消息之前——
 * 只把图片放在最后一条 user 消息上，避免每轮把历史图片重复 base64 一遍。
 */
private suspend fun callVisionWithFallback(
    config: ClassroomConfig,
    models: List<String>,
    system: String,
    prompt: String,
    imageSources: List<String>,
    history: List<ChatMessage> = emptyList()
): String = withContext(Dispatchers.IO) {
    if (config.apiKey.isBlank()) return@withContext "请先填写 API Key。"
    // 图片来源有两种：data URL（老调用方式）和本地路径（用户发图 / 历史附件）。
    val dataUrls = imageSources.mapNotNull { source ->
        if (source.startsWith("data:")) source else imageDataUrl(source)
    }
    if (dataUrls.isEmpty()) return@withContext "图片读取失败，请重新选择照片。"
    val candidates = models.ifEmpty { config.visionModels() }
    var last = "请先选择或填写识图模型名。"
    candidates.forEach { model ->
        val result = callVision(config.visionBaseUrlOrMain(), config.visionApiKeyOrMain(), model, system, prompt, dataUrls, history)
        if (!isApiFailure(result)) return@withContext result
        last = result
    }
    last
}

private suspend fun callVision(baseUrl: String, apiKey: String, model: String, system: String, prompt: String, dataUrls: List<String>, history: List<ChatMessage> = emptyList()): String = withContext(Dispatchers.IO) {
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
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(buildVisionJson(model, system, prompt, dataUrls, history)) }
        if (connection.responseCode !in 200..299) return@runCatching readBody(connection).ifBlank { "调用失败：HTTP ${connection.responseCode}" }
        Regex("\\\"content\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").find(readBody(connection))?.groupValues?.get(1)?.unescapeJson() ?: NO_CONTENT_REPLY
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


/** SAF 选中的文件展示名：优先 DISPLAY_NAME，读不到再退回路径最后一段。 */

/**
 * 选中图片的后缀：先看 MIME（SAF 给的 displayName 常常没有后缀），再退回名字后缀。
 * 只用于给落盘文件挑一个后缀，插件拿不到这个值，所以不做严格校验。
 */
/** 音乐总开关关闭时的提示：说清楚它和网络/生图是两回事，以及去哪儿打开。 */

/** 当前没有活跃的播放会话时的提示。 */

/**
 * 把当前曲目拼成插件侧看的紧凑 JSON。
 *
 * artworkPath 直接回填缓存文件里已有的那一张（没有就不给）：mediaCurrent 是每秒都可能被调用的
 * 高频能力，在这里顺手把封面再编码一次就等于每秒写一个 PNG。真正的落盘只在换歌时由
 * mediaArtwork 做一次。
 */

/**
 * 封面路径：缓存里已经存的是「当前这首」才回填。
 *
 * 判据是缓存键（包名 + 歌名 + 专辑）—— 它只看歌，不看播放进度，
 * 所以暂停、拖动进度、每秒 tick 都不会让这张图失效。
 */




// ---------- function calling 基础设施（阶段B 线①） ----------



/** OpenAI 兼容 tool_calls 里的一条调用。 */

/** 一次带工具请求的结果：普通文本 + 归一化后的 tool_calls（没有调用时为 null）。 */

/**
 * 流式 tool_calls 累积器：delta.tool_calls 是按 index 分片下发的，
 * id / function.name 取后到的非空值，function.arguments 是字符串拼接。
 */

/** 把 tool_calls JSON 解析成待执行的调用列表；名字为空的分片直接丢弃。 */

/** 非流式（服务端忽略 stream=true）响应解析：取 message.content 与 message.tool_calls。 */

/** 注册表 → OpenAI 兼容的 tools 数组（不含外层 "tools" 键）；空注册表返回 null。 */



private suspend fun buildConversationChapters(messages: List<ChatMessage>, config: ClassroomConfig, model: String): List<ConversationChapter> = withContext(Dispatchers.IO) {
    val chunks = messages.chunked(CHAPTER_SIZE)
    chunks.mapIndexed { chunkIndex, chunk ->
        val start = chunkIndex * CHAPTER_SIZE
        val end = (start + chunk.size - 1).coerceAtLeast(start)
        val local = localChapterSummary(chunkIndex, chunk, start, end)
        if (config.apiKey.isBlank() || model.isBlank()) {
            local
        } else {
            val prompt = "请把下面这段课堂对话整理成长期记忆索引。只返回 JSON：{\"title\":\"...\",\"summary\":\"...\",\"facts\":[\"明确事实\"],\"open\":[\"待确认问题\"]}。summary 必须只写对话中明确出现的内容，不要补充、推断或编造。\n" +
                chunk.joinToString("\n") { "${if (it.role == "user") "用户" else "AI"}：${it.text}" }.take(4000)
            val result = callChat(config.baseUrl, config.apiKey, model, "你负责为 AI 课堂生成保守、可追溯的对话章节索引。不得编造对话中没有出现的事实。", listOf(ChatMessage("user", prompt)))
            parseChapter(result, start, end) ?: local
        }
    }
}

private suspend fun buildLearningMemoryPack(messages: List<ChatMessage>, config: ClassroomConfig, model: String): LearningMemoryPack = withContext(Dispatchers.IO) {
    val recent = messages.takeLast(MEMORY_CARD_BUILD_MESSAGE_LIMIT)
    val local = localLearningMemoryPack(messages)
    if (recent.isEmpty() || config.apiKey.isBlank() || model.isBlank()) return@withContext local
    val prompt = buildString {
        appendLine("请把下面课堂对话整理为可长期使用的学习世界书。只返回 JSON，不要解释。")
        appendLine("格式：{\"corePremises\":[\"极简前提\"],\"cards\":[{\"title\":\"标题\",\"summary\":\"保守摘要\",\"keywords\":[\"关键词\"],\"type\":\"concept/mistake/progress/preference/exam/branch/narrative\",\"priority\":1,\"visibility\":\"public/author_private/character_private\",\"knownBy\":[\"知道者\"],\"unknownBy\":[\"不知道者\"],\"evidence\":\"角色知道该信息的依据\"}]}")
        appendLine("要求：corePremises 是每次对话都适合携带的极简事实，最多 12 条；cards 是可按关键词召回的记忆卡，最多 12 张。只写对话明确出现的内容，不要推断用户已掌握，不要编造。")
        appendLine("叙事隔离要求：如果用户描述心理活动、暗中策划、幕后设定、作者补充、对方不知道等内容，必须标为 author_private 或 character_private，并填写 knownBy/unknownBy/evidence。模型知道不等于角色知道；不要把私密信息整理成公开事实。")
        appendLine("课堂对话：")
        append(recent.joinToString("\n") { "${if (it.role == "user") "用户" else "AI"}：${it.text}" }.take(7000))
    }
    val result = callChat(config.baseUrl, config.apiKey, model, "你负责为 AI 课堂维护保守、可追溯的长期学习世界书。", listOf(ChatMessage("user", prompt)))
    val parsed = parseLearningMemoryPack(result, messages.size - recent.size, messages.lastIndex)
    if (parsed != null && (parsed.corePremises.isNotEmpty() || parsed.cards.isNotEmpty())) parsed else local
}















// 考试数据层已迁到纯 JVM 模块 :feature-exam（不依赖 Compose），解析结果回到 UI 前换成
// Compose 快照列表，保证作答时列表项写入能触发重绘，与迁移前 toMutableStateList 行为一致。
private fun ExamSession.withComposeState(): ExamSession = copy(questions = questions.toMutableStateList())






/** 单条消息的 JSON：没有工具字段时就是老的 {"role":..,"content":..}。 */

/**
 * 图片条目 → data URL。文件不存在 / 读不出来 / 超出体积上限一律返回 null（这一张就不发了），
 * mime 按扩展名推断，与 ImageSink 写盘时用的扩展名一致。
 */
/**
 * 用户从系统选择器选中的图片 → filesDir/images/ 里的一份副本。
 * 必须先落盘：SAF 给的 uri 授权只在本次授权期内有效，重启后读不到，
 * 而消息里的图片要能长期回看（也在重启后能再次发给模型）。
 * 复制失败返回 null，由调用方提示「重新选择照片」。
 */


/**
 * 识图请求体。history 为空、只有一张图时与改造前逐字节一致；
 * 有历史就按「system → 历史气泡 → 最后一条带图消息」的顺序拼，图片只在最后一条 user 消息上。
 */



private fun String.unescapeJson(): String = replace("\\n", "\n").replace("\\\"", "\"").replace("\\/", "/").replace("\\\\", "\\")

@Composable
private fun AIClassroomTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(primary = Blue, secondary = Green, tertiary = Purple, background = Page, surface = Color.White, onBackground = Ink, onSurface = Ink),
        // 全站衬线（思源宋体）：设在最外层主题；内层 MaterialTheme 没传 typography，
        // 其默认参数读的就是这里的环境值，所以整棵树（含 Markdown 处的 LocalTextStyle 覆盖）都继承。
        typography = DesignTokens.serifTypography(androidx.compose.material3.Typography()),
        content = content
    )
}










// 直接取构建产物里的 versionName，避免与 build.gradle.kts 脱节（曾因写死 3.3.0 而在 3.3.5 上误报更新）。
private val APP_VERSION: String = BuildConfig.VERSION_NAME

private object AppShapes {
    val panel = RoundedCornerShape(DesignTokens.Radius.Panel)
    val card = RoundedCornerShape(DesignTokens.Radius.Card)
    val control = RoundedCornerShape(DesignTokens.Radius.Control)
    val button = RoundedCornerShape(DesignTokens.Radius.Button)
    val menu = RoundedCornerShape(topEnd = DesignTokens.Radius.Menu, bottomEnd = DesignTokens.Radius.Menu)
}


private val Page = DesignTokens.Legacy.Page
private val Ink = DesignTokens.Legacy.Ink
private val Muted = DesignTokens.Legacy.Muted
private val Blue = DesignTokens.Legacy.Blue
private val Green = DesignTokens.Legacy.Green
private val Purple = DesignTokens.Legacy.Purple
/** 消息气泡里图片的解码边长上限（气泡最宽也就三百多 dp，再大纯属浪费内存）。 */
/** 全屏看图的解码边长上限：能放大到 4 倍，所以给得比气泡高。 */
/** 气泡里单张图片的最大显示高度：再高就把一屏占满了，长图请点开全屏看。 */
private val MESSAGE_IMAGE_MAX_HEIGHT = 320.dp
