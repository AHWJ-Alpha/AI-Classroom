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
        // 执行交给持久 Worker：进程被杀后任务还能继续。
        // 这里只负责入队与状态投影，不再自己跑协程 —— 避免双执行。
        _notice.value = if (staleOnly) "正在重写待复查的单元…" else "正在生成…"
        _running.value = true
        ClassroomRunWorker.schedule(context, projectId, staleOnly)

        // 状态以仓库为准：Worker 结束后自然收敛，不依赖 Activity 存活。
        cancelJob = scope.launch {
            while (true) {
                delay(1500)
                val active = service.store.runs(projectId).any { it.status == RunStatus.RUNNING }
                if (!active) {
                    _running.value = false
                    _notice.value = null
                    return@launch
                }
            }
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