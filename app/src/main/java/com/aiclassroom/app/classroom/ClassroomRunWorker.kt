package com.aiclassroom.app.classroom

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aiclassroom.app.readConversationsForBackground
import com.aiclassroom.classroom.domain.ProjectId
import com.aiclassroom.classroom.exec.RunControl
import com.aiclassroom.classroom.exec.RunOutcome
import com.aiclassroom.classroom.exec.RunRecovery
import kotlinx.coroutines.CancellationException

/**
 * 课堂生成的持久执行入口。
 *
 * 只携带稳定 ID（项目 + 是否只重写待复查），**不携带任何密钥**：
 * 执行时经来源解析器取配置，密钥永远不落到 WorkManager 的 Data 里。
 */
class ClassroomRunWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val projectId = inputData.getString(KEY_PROJECT) ?: return Result.failure()
        val staleOnly = inputData.getBoolean(KEY_STALE_ONLY, false)
        val context = applicationContext
        val service = ClassroomServiceProvider.service(context)
        setForeground(
            ClassroomNotify.foregroundInfo(
                context,
                ProjectId(projectId),
                if (staleOnly) "正在重写待复查的单元…" else "正在生成…",
            ),
        )

        // 来源解析：配置来自项目**自己的**来源对话，不是当前打开的对话。
        val resolver = ClassroomSourceResolver(
            conversations = { readConversationsForBackground(context) },
            store = service.store,
        )
        val source = resolver.resolve(ProjectId(projectId))
        if (source !is SourceResolution.Ready) {
            // 解析不到就停在这里，把原因留给界面显示，绝不借用别的对话。
            return Result.failure(workDataOf(KEY_ERROR to source.describe()))
        }

        val control = object : RunControl {
            override fun shouldContinue() = !isStopped
            override fun isCancelled() = isStopped
        }

        // 生成期间给一条常驻通知：Worker 没有界面，用户看不到进度会以为卡死。
        ClassroomNotify.show(
            context, ProjectId(projectId), "课堂生成中",
            if (staleOnly) "正在重写待复查的单元…" else "正在生成…",
            indeterminate = true, showCancel = true,
        )

        return try {
            val outcome = if (staleOnly) {
                service.regenerateStale(
                    ProjectId(projectId), source.connection.policy, source.connection.apiKey, control,
                    forceLease = true,
                )
            } else {
                service.run(
                    ProjectId(projectId), source.connection.policy, source.connection.apiKey, control,
                    forceLease = true,
                )
            }
            val message = when (outcome) {
                null -> "没有待处理的单元"
                is RunOutcome.Completed -> "已完成，共 ${outcome.committedUnits} 节"
                is RunOutcome.Paused -> "已暂停，已完成 ${outcome.committedUnits} 节"
                is RunOutcome.Cancelled -> "已取消，已完成内容保留"
                is RunOutcome.NeedsAttention -> "需要处理：${outcome.reason}"
                is RunOutcome.Stalled -> "无法继续：${outcome.reason}"
            }
            ClassroomNotify.show(
                context, ProjectId(projectId), "课堂任务结束", message,
                indeterminate = false,
            )
            when (outcome) {
                is RunOutcome.NeedsAttention, is RunOutcome.Stalled ->
                    Result.failure(workDataOf(KEY_ERROR to message))
                else -> Result.success()
            }
        } catch (e: CancellationException) {
            // 被取消时协程直接结束，引擎来不及写状态：这里补上，避免重启后一直显示运行中。
            runCatching { RunRecovery.markCancelled(service.store, ProjectId(projectId), service.clock) }
            throw e
        } catch (e: Throwable) {
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: e::class.simpleName ?: "未知错误")))
        }
    }

    companion object {
        const val KEY_PROJECT = "project_id"
        const val KEY_STALE_ONLY = "stale_only"
        const val KEY_ERROR = "error"

        /** 每个项目只允许一个未完成的执行：重复点击不会起第二个执行者。 */
        fun workName(projectId: ProjectId) = "classroom-run-" + projectId.value

        fun schedule(context: Context, projectId: ProjectId, staleOnly: Boolean) {
            val request = OneTimeWorkRequestBuilder<ClassroomRunWorker>()
                .setInputData(
                    workDataOf(
                        KEY_PROJECT to projectId.value,
                        KEY_STALE_ONLY to staleOnly,
                    ),
                )
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(workName(projectId), ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context, projectId: ProjectId) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(workName(projectId))
        }
    }
}
