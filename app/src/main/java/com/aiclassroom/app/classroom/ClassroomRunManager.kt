package com.aiclassroom.app.classroom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.exec.RunControl
import com.aiclassroom.classroom.exec.RunOutcome
import com.aiclassroom.classroom.domain.ClassroomEvent
import com.aiclassroom.classroom.domain.EventType
import com.aiclassroom.classroom.event.EventOutbox
import com.aiclassroom.classroom.gateway.ModelPolicy

/**
 * 课堂生成任务的进程级持有者。
 *
 * 为什么不能放在界面的 coroutineScope 里：那个作用域跟着 Compose 生命周期走，
 * 一旦退出课堂界面就会被取消 —— 生成任务会静默中断。
 * 这里用一个进程级作用域，界面来去都不影响任务。
 *
 * 同时负责发通知：用户切到别的应用时能看到进度，也能直接暂停/取消。
 */
object ClassroomRunManager {

    private const val CHANNEL_ID = "classroom_runs"
    private const val NOTIFICATION_ID = 7001

    /** 通知点击时带给宿主的项目 id。 */
    const val EXTRA_OPEN_PROJECT = "classroom_open_project"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO) // 进程级，不随界面销毁
    private val paused = AtomicBoolean(false)
    /** 用户明确取消（区别于暂停）：引擎据此写入 CANCELLED 而不是 PAUSED。 */
    private val cancelRequested = AtomicBoolean(false)
    private var cancelJob: Job? = null

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    val control = object : RunControl {
        override fun shouldContinue() = !paused.get()
        override fun isCancelled() = cancelRequested.get()
    }

    fun requestPause() { paused.set(true) }

    /** 取消：只停后续，已提交内容一律保留。 */
    fun cancel() {
        paused.set(true)
        cancelRequested.set(true)
        // 不在这里清 running：旧协程可能还在提交当前单元。
        // 提前放行会让新任务与旧任务同时跑，而且旧任务的收尾会覆盖新任务状态。
        cancelJob?.cancel()
    }

    /**
     * 启动一次生成。调用后界面可以离开，任务继续跑。
     */
    fun start(
        context: Context,
        service: ClassroomService,
        projectId: ProjectId,
        policy: ModelPolicy,
        apiKey: String,
        staleOnly: Boolean = false,
    ) {
        if (_running.value) return
        paused.set(false)
        _running.value = true
        _notice.value = if (staleOnly) "正在重写待复查的单元…" else "正在生成…"
        ensureChannel(context)
        notify(
            context,
            "课堂生成中",
            "准备开始",
            indeterminate = true,
            openProjectId = projectId.value,
        )

        val appContext = context.applicationContext
        cancelJob = scope.launch {
            // 进度轮询：任务是别人在跑，这里只盯着存储里的完成数。
            val watcher = scope.launch {
                while (_running.value) {
                    val units = service.store.units(projectId)
                    val done = units.count { it.status == UnitStatus.READY }
                    notify(
                        appContext,
                        "课堂生成中",
                        "已完成 " + done + " / " + units.size + " 节",
                        indeterminate = false,
                    )
                    delay(2000)
                }
            }
            val committedNow = { service.store.units(projectId).count { it.status == UnitStatus.READY } }

            // 取消不是失败，异常也不是「没有待处理单元」 —— 这两种都必须留下真实原因。
            val outcome = try {
                if (staleOnly) service.regenerateStale(projectId, policy, apiKey, control)
                else service.run(projectId, policy, apiKey, control)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程被直接取消时引擎来不及写状态：这里补上，
                // 否则重建仓库后任务会一直是 RUNNING，看起来像还在跑。
                runCatching {
                    com.aiclassroom.classroom.exec.RunRecovery.markCancelled(
                        service.store, projectId, service.clock,
                    )
                }
                RunOutcome.Cancelled(committedNow())
            } catch (e: Throwable) {
                RunOutcome.NeedsAttention(
                    committedNow(),
                    e.message ?: e::class.simpleName ?: "未知错误",
                )
            }
            _running.value = false
            cancelRequested.set(false)
            paused.set(false)
            watcher.cancel()
            _notice.value = when (outcome) {
                null -> "没有待处理的单元"
                is RunOutcome.Completed -> "全部单元完成，共 " + outcome.committedUnits + " 个"
                is RunOutcome.Paused -> "已暂停，已完成 " + outcome.committedUnits + " 个单元"
                is RunOutcome.Cancelled -> "已取消，已完成内容保留"
                is RunOutcome.NeedsAttention -> "需要处理：" + outcome.reason
                is RunOutcome.Stalled -> "无法继续：" + outcome.reason
            }
            // 入队事件：真正的投递由宿主（那个对话自己）完成，这里只落库。
            runCatching {
                val project = service.store.project(projectId)
                val type = when (outcome) {
                    is RunOutcome.Completed -> EventType.COMPLETED
                    is RunOutcome.Paused -> EventType.PAUSED
                    is RunOutcome.Cancelled -> EventType.CANCELLED
                    is RunOutcome.NeedsAttention -> EventType.NEEDS_ATTENTION
                    is RunOutcome.Stalled -> EventType.FAILED
                    null -> EventType.NEEDS_ATTENTION
                }
                val conversationId = project?.sourceConversationId
                if (conversationId != null && type.isTerminal) {
                    val run = service.store.runs(projectId).maxByOrNull { it.startedAt }
                    EventOutbox.enqueue(
                        service.store,
                        ClassroomEvent(
                            eventId = EventOutbox.eventIdOf(run?.id, projectId, type),
                            sourceConversationId = conversationId,
                            projectId = projectId,
                            projectTitle = project.title,
                            runId = run?.id,
                            workVersion = project.workVersion,
                            type = type,
                            summary = _notice.value.orEmpty(),
                            detail = (outcome as? RunOutcome.NeedsAttention)?.reason
                                ?: (outcome as? RunOutcome.Stalled)?.reason.orEmpty(),
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }

            notify(
                appContext,
                "课堂任务结束",
                _notice.value.orEmpty(),
                indeterminate = false,
                ongoing = false,
                openProjectId = projectId.value,
            )
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "课堂生成", NotificationManager.IMPORTANCE_LOW).apply {
                description = "课堂内容生成进度"
            },
        )
    }

    private fun notify(
        context: Context,
        title: String,
        text: String,
        indeterminate: Boolean,
        ongoing: Boolean = true,
        openProjectId: String? = null,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, indeterminate)

        // 点通知回到对应项目：带上项目 id，宿主打开课堂时直接落到那一项。
        openProjectId?.let { id ->
            val open = Intent().apply {
                setClassName(context.packageName, "com.aiclassroom.app.MainActivity")
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(EXTRA_OPEN_PROJECT, id)
            }
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context,
                    NOTIFICATION_ID,
                    open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }

        if (ongoing) {
            builder.addAction(0, "暂停", actionIntent(context, RunActionReceiver.ACTION_PAUSE))
            builder.addAction(0, "取消", actionIntent(context, RunActionReceiver.ACTION_CANCEL))
        }

        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }

    private fun actionIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, RunActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}