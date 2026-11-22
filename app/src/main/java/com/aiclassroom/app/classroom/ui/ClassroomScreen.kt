package com.aiclassroom.app.classroom.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiclassroom.app.classroom.ClassroomRunManager
import com.aiclassroom.app.classroom.ClassroomService
import com.aiclassroom.app.classroom.SourceResolution
import com.aiclassroom.classroom.gateway.ModelPolicy
import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.domain.PlaythroughSave
import com.aiclassroom.classroom.exec.RunControl
import com.aiclassroom.classroom.exec.RunOutcome
import kotlinx.coroutines.launch

/**
 * 课堂界面。
 *
 * 三种视图：项目列表 → 项目总览 → 阅读器。
 * 生成任务在协程里跑，界面只负责显示状态与发指令；
 * 离开这一屏不会取消已经开始的生成（服务层不依赖 Compose 生命周期）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassroomScreen(
    /**
     * 按项目解析来源连接。
     *
     * 传函数而不是固定 policy：从「全部项目」打开别的对话的项目时，
     * 必须用**那个项目自己的来源**，不能用当前对话的配置。
     */
    resolveSource: (ProjectId) -> SourceResolution,
    onClose: () -> Unit,
    /** 从通知/卡片点进来时要直接打开的项目。 */
    initialProjectId: ProjectId? = null,
    /** 卡片上固定的作品版本；null 表示看最新版。 */
    initialWorkVersion: Int? = null,
    /** 当前所在对话：新建项目默认绑定它。 */
    sourceConversationId: String? = null,
    sourceConversationName: String? = null,
    /** 创建成功后由宿主拍来源上下文快照（人格/世界书/资料/事实边界）。 */
    onProjectCreated: (ProjectId) -> Unit = {},
    /** 用户显式要求同步来源设定；返回给用户看的提示（null 表示成功）。 */
    onSyncSource: (ProjectId) -> String? = { null },
) {
    val context = LocalContext.current
    val service = remember { ClassroomService(context) }
    val scope = rememberCoroutineScope()

    // 默认只看本对话的项目；旧项目（未关联）不在这里被顺手接管。
    var showAllProjects by rememberSaveable { mutableStateOf(false) }
    var projects by remember {
        mutableStateOf(
            if (sourceConversationId != null) service.projectsOf(sourceConversationId)
            else service.allProjects(),
        )
    }
    fun reloadProjects() {
        projects = if (showAllProjects || sourceConversationId == null) {
            service.allProjects()
        } else {
            service.projectsOf(sourceConversationId)
        }
    }
    var notice by remember { mutableStateOf("") }
    var openProjectId by rememberSaveable(stateSaver = Saver<ProjectId?, String>(
        save = { it?.value.orEmpty() }, restore = { it.takeIf(String::isNotBlank)?.let(::ProjectId) },
    )) { mutableStateOf<ProjectId?>(null) }
      var readingUnitId by remember { mutableStateOf<UnitId?>(null) }
      // 一次性查看全部内容：把已提交的所有节按顺序连起来。
      var viewingAll by remember { mutableStateOf(false) }
    // 卡片指定了版本就一直按那个版本读，直到用户离开这个项目。
    var pinnedWorkVersion by remember { mutableStateOf(initialWorkVersion) }
    LaunchedEffect(initialWorkVersion) { pinnedWorkVersion = initialWorkVersion }
    var creating by remember { mutableStateOf(false) }
    // 任务跑在进程级作用域（ClassroomRunManager），界面只观察状态：
    // 退出这一屏不会中断生成，通知栏也能看到进度。
    val running by ClassroomRunManager.running.collectAsState()
    val managerNotice by ClassroomRunManager.notice.collectAsState()
    var planning by remember { mutableStateOf(false) }
    // 生成过程中定期重读：任务在后台跑，界面必须能看到进度，
    // 否则会一直显示「0 / 8」直到整轮结束（实测就是这样）。
    var refreshTick by remember { mutableStateOf(0) }
    var scheduledProjectKeys by remember { mutableStateOf(emptySet<String>()) }
    val showAllProjectsState by rememberUpdatedState(showAllProjects)
    // 通知/结果卡点进来：直接落到对应项目。
    //
    // 关键：目标项目可能属于别的对话、或者是未关联的旧项目 ——
    // 那样它不在「本对话」过滤后的列表里。这时必须自动切到「显示全部」，
    // 否则点了卡片什么也不会发生（实测就是这样）。
    LaunchedEffect(initialProjectId) {
        if (initialProjectId != null) {
            var visible = service.projectsOf(sourceConversationId.orEmpty())
            if (sourceConversationId == null || visible.none { it.id == initialProjectId }) {
                showAllProjects = true
                visible = service.allProjects()
            }
            projects = visible
            if (visible.any { it.id == initialProjectId }) openProjectId = initialProjectId
        }
    }
    var editingUnit by remember { mutableStateOf<ClassroomUnit?>(null) }
    LaunchedEffect(managerNotice) { managerNotice?.let { notice = it } }
    LaunchedEffect(Unit) {
        while (true) {
            // LaunchedEffect(Unit) 只启动一次；用 updated state 避免切换“显示全部”后
            // 定时刷新又用旧过滤条件把已打开项目从列表抹掉。
            projects = if (showAllProjectsState || sourceConversationId == null) {
                service.allProjects()
            } else {
                service.projectsOf(sourceConversationId)
            }
            val ids = service.allProjects().map { it.id }
            scheduledProjectKeys = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ids.filter { id ->
                    androidx.work.WorkManager.getInstance(context.applicationContext)
                        .getWorkInfosForUniqueWork(com.aiclassroom.app.classroom.ClassroomRunWorker.workName(id)).get()
                        .any { !it.state.isFinished }
                }.map { it.value }.toSet()
            }
            refreshTick++
            kotlinx.coroutines.delay(1000)
        }
    }
    // 通过它让「暂停」按钮真的能停下循环。
    var runToken by remember { mutableStateOf(0) }
    var pausedToken by remember { mutableStateOf(-1) }

    LaunchedEffect(Unit) {
        // 有任务正在跑时不要「恢复」：recover 会把 RUNNING 改成 PAUSED，
        // 而后台执行者仍在跑 —— 界面和真实状态就此分叉。
        if (ClassroomRunManager.running.value) return@LaunchedEffect
        val report = service.recover()
        if (report.changed) {
            notice = "上次生成被中断，已恢复为可继续状态（任务 " + report.pausedRuns + "，单元 " + report.resetUnits + "）"
            reloadProjects()
        }
    }

    // 即使当前列表按来源对话过滤，已打开的项目也必须继续可见；退出再进时
    // 直接从持久仓库补回项目对象，规划、草稿和已提交章节不会被 UI 过滤掉。
    val project = openProjectId?.let { id ->
        projects.firstOrNull { it.id == id } ?: service.store.project(id)
    }

    // 一级一级退：阅读器 → 总览 → 列表 → 主对话。
    // 侧滑和「返回」按钮走同一条，避免一划就掉回主界面。
    val goBack: () -> Unit = {
        when {
            readingUnitId != null -> readingUnitId = null
            openProjectId != null -> openProjectId = null
            else -> onClose()
        }
    }

    // 系统返回键与侧滑走同一条：先退一层，退无可退才关掉课堂。
    // 之前没有这个，返回键直接结束 Activity，用户以为「一按就退出应用」。
    BackHandler { goBack() }

    Scaffold(
        // 反向退出：和二级菜单一样，内容右滑就关掉，不依赖边缘触发。
        modifier = Modifier.pointerInput(Unit) {
            var total = 0f
            detectHorizontalDragGestures(
                onDragEnd = {
                    // 和返回按钮同一级：先退到上一层，退无可退才关掉课堂。
                    if (total > 80f) goBack()
                    total = 0f
                },
                onHorizontalDrag = { _, dragAmount -> total += dragAmount },
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            readingUnitId != null -> "阅读"
                            project != null -> project.title
                            else -> "课堂"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = goBack) { Text("返回") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                  viewingAll && project != null -> AllContentView(
                      service = service,
                      projectId = project.id,
                      onClose = { viewingAll = false },
                  )

                  readingUnitId != null && project != null -> ReaderView(
                    service = service,
                    projectId = project.id,
                    unitId = readingUnitId!!,
                    workVersion = pinnedWorkVersion,
                    onUnitChange = { readingUnitId = it },
                )

                project != null -> OverviewView(
                    service = service,
                    project = project,
                    notice = notice,
                    running = project.id.value in scheduledProjectKeys || service.store.runs(project.id).any {
                        it.status == RunStatus.RUNNING && it.owner != null && it.leaseUntil > service.clock()
                    },
                    onStart = {
                        val source = resolveSource(project.id)
                        if (source is SourceResolution.Ready) {
                            ClassroomRunManager.start(
                                context = context,
                                service = service,
                                projectId = project.id,
                                policy = source.connection.policy,
                                apiKey = source.connection.apiKey,
                            )
                        } else {
                            notice = source.describe()
                        }
                    },
                    onPause = {
                        ClassroomRunManager.requestPause(
                            context = context.applicationContext,
                            projectId = project.id,
                        )
                        notice = "正在暂停，已保存的正文保留，继续时从断点续写"
                    },
                      onViewAll = { viewingAll = true },
                      onRead = { tapped ->
                        // 明确点了哪一节，就进入哪一节。
                        readingUnitId = tapped
                    },
                    onEditUnit = { editingUnit = it },
                    onSync = {
                        notice = onSyncSource(project.id)
                            ?: "已按来源对话同步设定，受影响的单元已标记待复查"
                        reloadProjects()
                    },
                    onRegenerate = {
                        val source = resolveSource(project.id)
                        if (source is SourceResolution.Ready) {
                            ClassroomRunManager.start(
                                context = context,
                                service = service,
                                projectId = project.id,
                                policy = source.connection.policy,
                                apiKey = source.connection.apiKey,
                                staleOnly = true,
                            )
                        } else {
                            notice = source.describe()
                        }
                    },
                    onRefresh = { reloadProjects() },
                    planning = planning || service.isPlanning(project.id),
                    refreshTick = refreshTick,
                    onPlan = {
                        if (planning) return@OverviewView
                        planning = true
                        notice = "正在生成规格与总纲…"
                        val source = resolveSource(project.id)
                        if (source !is SourceResolution.Ready) {
                            // 解析不到来源就不要发请求：宁可停下来说明原因，也不能借用别的对话。
                            planning = false
                            notice = source.describe()
                            return@OverviewView
                        }
                        com.aiclassroom.app.classroom.ClassroomRunWorker.schedule(context, project.id, false, planOnly = true)
                        planning = false
                        notice = "规划已交给后台，退出页面后仍会保存；请稍候"
                    },
                )

                else -> ProjectListView(
                    projects = projects,
                    notice = notice,
                    hasSource = sourceConversationId != null,
                    showAll = showAllProjects,
                    onToggleAll = {
                        showAllProjects = !showAllProjects
                        reloadProjects()
                    },
                    onCreate = { creating = true },
                    onOpen = { pinnedWorkVersion = null; openProjectId = it.id },
                )
            }
        }
    }

    val editTarget = editingUnit
    val editProjectId = openProjectId
    if (editTarget != null && editProjectId != null) {
        UnitDirectiveDialog(
            unit = editTarget,
            onDismiss = { editingUnit = null },
            onConfirm = { goal, notes ->
                service.updateDirective(editProjectId, editTarget.id, goal, notes)
                editingUnit = null
                reloadProjects()
                refreshTick++
                notice = "已更新本单元方向（只影响之后的生成）"
            },
        )
    }

    if (creating) {
        CreateProjectDialog(
            onDismiss = { creating = false },
            onConfirm = { title, type, request ->
                val created = service.createProject(
                    title = title,
                    type = type,
                    rawRequest = request,
                    sourceConversationId = sourceConversationId,
                    sourceDisplayName = sourceConversationName,
                )
                onProjectCreated(created.id)
                reloadProjects()
                creating = false
                openProjectId = created.id
                notice = "已保存原始需求"
            },
        )
    }
}

private fun describe(outcome: RunOutcome): String = when (outcome) {
    is RunOutcome.Completed -> "全部单元完成，共 " + outcome.committedUnits + " 个"
    is RunOutcome.Paused -> "已暂停，已完成 " + outcome.committedUnits + " 个单元"
    is RunOutcome.Cancelled -> "已取消，已完成内容保留"
    is RunOutcome.NeedsAttention -> "需要处理：" + outcome.reason
    is RunOutcome.Stalled -> "无法继续：" + outcome.reason
}

@Composable
private fun ProjectListView(
    projects: List<ClassroomProject>,
    notice: String,
    hasSource: Boolean = false,
    showAll: Boolean = false,
    onToggleAll: () -> Unit = {},
    onCreate: () -> Unit,
    onOpen: (ClassroomProject) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (notice.isNotBlank()) {
            Text(notice, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }
        if (hasSource) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (showAll) "全部项目（含其他对话）" else "本对话的项目",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onToggleAll) {
                    Text(if (showAll) "只看本对话" else "显示全部")
                }
            }
        }
        if (projects.isEmpty()) {
            Text("还没有课堂项目。新建一个，让它按总纲一节一节地写完。", fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(projects) { p ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(p) }) {
                    Column(Modifier.padding(14.dp)) {
                        Text(p.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            p.type.label + " · " + lifecycleLabel(p.lifecycle) + " · 正式版本 " + p.workVersion,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("新建课堂") }
    }
}

private fun lifecycleLabel(lifecycle: ProjectLifecycle): String = when (lifecycle) {
    ProjectLifecycle.DRAFT -> "草稿"
    ProjectLifecycle.PLAN_READY -> "待确认总纲"
    ProjectLifecycle.ACTIVE -> "进行中"
    ProjectLifecycle.READY -> "已完成"
    ProjectLifecycle.NEEDS_ATTENTION -> "需要处理"
    ProjectLifecycle.ARCHIVED -> "已归档"
}

@Composable
private fun OverviewView(
    service: ClassroomService,
    project: ClassroomProject,
    notice: String,
    running: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onRead: (UnitId) -> Unit,
    onViewAll: () -> Unit = {},
    onRefresh: () -> Unit,
    onEditUnit: (ClassroomUnit) -> Unit = {},
    planning: Boolean = false,
    onPlan: () -> Unit = {},
    onRegenerate: () -> Unit = {},
    onSync: () -> Unit = {},
    refreshTick: Int = 0,
) {
    // refreshTick 作为 key：生成过程中要持续重读单元状态。
    val units = remember(project.id, project.workVersion, running, planning, refreshTick) {
        service.store.units(project.id)
    }
    val spec = remember(project.id, refreshTick) { service.store.latestSpec(project.id) }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            // 一次性看完所有已提交的内容，不必一节一节点。
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onViewAll) { Text("查看") }
            }

        if (notice.isNotBlank()) {
            Text(notice, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }
        Text("原始需求", fontWeight = FontWeight.Bold)
        Text(spec?.rawRequest.orEmpty(), fontSize = 13.sp)
        service.store.runs(project.id).maxByOrNull { it.startedAt }?.error?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(12.dp))

        Text("单元 " + units.count { it.status == UnitStatus.READY } + " / " + units.size + " 已完成", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        units.forEach { u ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(u.index.toString() + ". " + u.chapter, fontSize = 14.sp)
                    Text(u.directive?.effectiveGoal(u.goal) ?: u.goal, fontSize = 12.sp)
                    u.directive?.notes?.takeIf { it.isNotBlank() }?.let { Text("补充要求：$it", fontSize = 12.sp) }
                    Text(unitStatusLabel(u.status), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val draft = service.store.draft(project.id, u.id)
                    if (draft != null && draft.phase != DraftPhase.COMMITTED) {
                        val phase = when (draft.phase) {
                            DraftPhase.GENERATING -> "正在续写"
                            DraftPhase.ASSEMBLED -> "正文已组装"
                            DraftPhase.REVIEWING -> "正在审查"
                            DraftPhase.PATCHING -> "核对人物状态"
                            DraftPhase.READY_TO_COMMIT -> "准备保存"
                            DraftPhase.INVALIDATED -> "输入已变更"
                            DraftPhase.COMMITTED -> "已保存"
                        }
                        Text("已保存 ${draft.segments.size} 段 · 正文 ${draft.totalChars} 字 · $phase", fontSize = 11.sp)
                        if (draft.lastError.isNotBlank()) Text(draft.lastError, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = { onEditUnit(u) }) { Text("调整") }
                if (u.currentRevisionId != null || service.store.draft(project.id, u.id)?.segments?.isNotEmpty() == true) {
                    TextButton(onClick = { onRead(u.id) }) { Text(if (u.status == UnitStatus.READY) "阅读" else "查看已生成") }
                }
            }
        }

        if (units.isEmpty()) {
            Text(
                "还没有规划出单元。先生成规格与总纲，确认后再开始写正文。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onPlan, enabled = !planning) {
                Text(if (planning) "规划中…" else "生成总纲")
            }
        }

        Spacer(Modifier.height(16.dp))
        // 改过方向之后，之前写的正文就不再对应当前设定：给一个明确的重写入口。
        val staleCount = units.count { it.status == UnitStatus.STALE }
        if (staleCount > 0) {
            Button(
                onClick = onRegenerate,
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("重写待复查的 " + staleCount + " 节")
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStart, enabled = !running && !planning && units.any { it.status != UnitStatus.READY }) {
                Text(if (running) "生成中…" else if (units.isNotEmpty() && units.all { it.status == UnitStatus.READY }) "全部完成"
                    else if (units.any { service.store.draft(project.id, it.id)?.segments?.isNotEmpty() == true }) "继续生成" else "开始生成")
            }
            OutlinedButton(onClick = onPause, enabled = running) { Text("暂停") }
            OutlinedButton(onClick = onRefresh) { Text("刷新") }
        }
    }
}

private fun unitStatusLabel(status: UnitStatus): String = when (status) {
    UnitStatus.PLANNED -> "待生成"
    UnitStatus.GENERATING -> "生成中"
    UnitStatus.DRAFT_READY -> "草稿待审"
    UnitStatus.REVIEWING -> "审查中"
    UnitStatus.REVISING -> "修订中"
    UnitStatus.READY -> "已完成"
    UnitStatus.STALE -> "待复查"
    UnitStatus.FAILED -> "失败"
}

@Composable
private fun ReaderView(
    service: ClassroomService,
    projectId: ProjectId,
    unitId: UnitId,
    onUnitChange: (UnitId) -> Unit,
    /** 要读的版本；null 表示最新一版。 */
    workVersion: Int? = null,
) {
    var revisionTick by remember { mutableStateOf(0) }
    LaunchedEffect(projectId) {
        while (true) { kotlinx.coroutines.delay(1000); revisionTick++ }
    }
    val units = remember(projectId, revisionTick) { service.store.units(projectId).sortedBy { it.index } }
    // 按版本读：有清单就严格按清单，没有清单的旧项目才退回当前正文。
    val resolved = remember(unitId, workVersion, revisionTick) { service.revisionAt(projectId, unitId, workVersion) }
    val revision = when (resolved) {
        is com.aiclassroom.classroom.domain.ManifestResolver.Resolution.Found -> resolved.revision
        is com.aiclassroom.classroom.domain.ManifestResolver.Resolution.Legacy -> resolved.revision
        is com.aiclassroom.classroom.domain.ManifestResolver.Resolution.Missing -> null
    }
    // 记录阅读位置：重启后回到这一节。
    androidx.compose.runtime.LaunchedEffect(unitId, workVersion) {
        val version = (resolved as? com.aiclassroom.classroom.domain.ManifestResolver.Resolution.Found)
            ?.manifest?.workVersion ?: service.store.project(projectId)?.workVersion ?: 0
        runCatching { service.saveReaderProgress(projectId, unitId, version) }
    }

    val versionLabel = when (resolved) {
        is com.aiclassroom.classroom.domain.ManifestResolver.Resolution.Found ->
            "第 " + resolved.manifest.workVersion + " 版"
        is com.aiclassroom.classroom.domain.ManifestResolver.Resolution.Legacy -> "当前版本（旧项目无版本清单）"
        is com.aiclassroom.classroom.domain.ManifestResolver.Resolution.Missing -> resolved.reason
    }
    // 还没有正式版本时，展示正在写的草稿（已通过校验的分段）——
    // 否则一节写完之前完全看不到任何内容。
    val draftContent = remember(unitId, workVersion, revision, revisionTick) {
        if (revision == null && workVersion == null) service.draftContent(projectId, unitId) else null
    }
    val index = units.indexOfFirst { it.id == unitId }

    // 文字 GAL：选择只写进自己的存档，不改作品正文，也不影响别的存档。
    var galSave by remember(projectId) {
        mutableStateOf(service.store.playthroughs(projectId).firstOrNull())
    }
    fun recordChoice(optionId: String) {
        val base = galSave ?: PlaythroughSave(
            id = SaveId.new(),
            projectId = projectId,
            workVersion = service.store.project(projectId)?.workVersion ?: 0,
            currentNodeId = "",
            variables = emptyMap(),
            choiceHistory = emptyList(),
            seenEndingIds = emptyList(),
            updatedAt = System.currentTimeMillis(),
        )
        val next = base.copy(
            choiceHistory = base.choiceHistory + optionId,
            updatedAt = System.currentTimeMillis(),
        )
        service.store.savePlaythrough(next)
        galSave = next
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            units.getOrNull(index)?.chapter.orEmpty(),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Text(if (draftContent != null) "已保存草稿（待整节审查）" else versionLabel,
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (revision == null && draftContent == null) {
                  Text("这一节还没有正式内容。", fontSize = 13.sp)
              } else if (revision == null && draftContent != null) {
                  Text("已保存 " + service.store.draft(projectId, unitId)?.segments.orEmpty().size +
                      " 段 · 正文 " + com.aiclassroom.classroom.content.TextCount.units(draftContent.blocks) + " 字", fontSize = 11.sp)
                  Spacer(Modifier.height(6.dp))
                  draftContent.blocks.forEach { block ->
                      when (block) {
                          is ContentBlock.Heading -> { Text(block.text, fontWeight = FontWeight.Bold, fontSize = 17.sp); Spacer(Modifier.height(6.dp)) }
                          is ContentBlock.Dialogue -> { Text(block.text, fontSize = 15.sp, lineHeight = 24.sp); Spacer(Modifier.height(6.dp)) }
                          is ContentBlock.Narration -> { Text(block.text, fontSize = 15.sp, lineHeight = 24.sp); Spacer(Modifier.height(10.dp)) }
                          else -> Unit
                      }
                  }
            } else {
                // 走到这个分支时 revision 一定非空（上面的两个分支已排除 null）。
                val rev = revision!!
                val content = rev.content
                if (content == null) {
                    Text(rev.draftText, fontSize = 15.sp, lineHeight = 24.sp)
                } else {
                    content.blocks.forEach { block ->
                        when (block) {
                            is ContentBlock.Heading -> {
                                Text(block.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Spacer(Modifier.height(6.dp))
                            }
                            is ContentBlock.Dialogue -> {
                                Text(block.text, fontSize = 15.sp, lineHeight = 24.sp)
                                Spacer(Modifier.height(6.dp))
                            }
                            is ContentBlock.Choice -> {
                                val accent = MaterialTheme.colorScheme.primary
                                val shape = RoundedCornerShape(14.dp)
                                Text(block.prompt, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(Modifier.height(8.dp))
                                val chosen = galSave?.choiceHistory.orEmpty()
                                block.options.forEach { opt ->
                                    val picked = opt.id in chosen
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(shape)
                                            // 半透明渐变：主色由上往下淡出，像一层薄玻璃压在纸上。
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        accent.copy(alpha = if (picked) 0.26f else 0.14f),
                                                        accent.copy(alpha = if (picked) 0.10f else 0.04f),
                                                    ),
                                                ),
                                            )
                                            .border(
                                                1.dp,
                                                accent.copy(alpha = if (picked) 0.42f else 0.18f),
                                                shape,
                                            )
                                            .clickable(enabled = !picked) {
                                                recordChoice(opt.id)
                                                // 选项的目标是「某一节的 unitId」：真的跳过去，
                                                // 否则 GAL 就只是几段带按钮的静态文字。
                                                val target = units.firstOrNull {
                                                    it.id.value == opt.targetNodeId
                                                }
                                                if (target != null) onUnitChange(target.id)
                                            }
                                            .padding(horizontal = 14.dp, vertical = 11.dp),
                                    ) {
                                        Text(
                                            (if (picked) "· 已选 · " else "") + opt.text,
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                            is ContentBlock.Exercise -> {
                                Text("练习：" + block.prompt, fontSize = 15.sp)
                                // 答案默认折叠：学习者作答前不该看到标准答案。
                                var showAnswer by remember(block.id) { mutableStateOf(false) }
                                TextButton(onClick = { showAnswer = !showAnswer }) {
                                    Text(if (showAnswer) "收起答案" else "显示答案")
                                }
                                if (showAnswer) Text(block.answer, fontSize = 14.sp)
                                Spacer(Modifier.height(6.dp))
                            }
                            else -> {
                                Text(blockText(block), fontSize = 15.sp, lineHeight = 24.sp)
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                onClick = { onUnitChange(units[index - 1].id) },
                enabled = index > 0,
            ) { Text("上一节") }
            TextButton(
                onClick = { onUnitChange(units[index + 1].id) },
                enabled = index >= 0 && index < units.size - 1,
            ) { Text("下一节") }
        }
    }
}

private fun blockText(block: ContentBlock): String = when (block) {
    is ContentBlock.Narration -> block.text
    is ContentBlock.Dialogue -> block.text
    is ContentBlock.Heading -> block.text
    is ContentBlock.Explanation -> block.text
    is ContentBlock.Example -> block.text
    is ContentBlock.Exercise -> block.prompt
    is ContentBlock.Choice -> block.prompt
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, ProjectType, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ProjectType.TEACHING) }
    var request by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建课堂") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                // 可换行：中文标签比英文宽，固定一行会把最后一个选项裁成一条边。
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ProjectType.values().forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.label, fontSize = 12.sp) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = request,
                    onValueChange = { request = it },
                    label = { Text("想要什么（原始需求）") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.ifBlank { "未命名课堂" }, type, request) },
                enabled = request.isNotBlank(),
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 调整单个单元的方向：可以改写目标，也可以加注释。
 *
 * 这里刻意只给「目标和注释」两个输入框 —— 规格、硬约束、全局主线不在这里改，
 * 那属于「改设定并重规划」，是另一件事。
 */
@Composable
private fun UnitDirectiveDialog(
    unit: ClassroomUnit,
    onDismiss: () -> Unit,
    onConfirm: (String?, String) -> Unit,
) {
    var goal by remember(unit.id) { mutableStateOf(unit.directive?.goalOverride.orEmpty()) }
    var notes by remember(unit.id) { mutableStateOf(unit.directive?.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调整这一节的方向") },
        text = {
            Column {
                Text(
                    "原本的目标：" + unit.goal,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    label = { Text("改写目标（留空则沿用原目标）") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("补充要求（重点、口吻、不要写什么）") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "只影响之后的生成；已写好的正文不会自动改写，但这一节会被标为待复查。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(goal.ifBlank { null }, notes) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
