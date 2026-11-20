package com.aiclassroom.app.classroom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.aiclassroom.classroom.domain.ProjectId

/**
 * 课堂任务的通知。
 *
 * Worker 执行时不依赖任何 Activity，所以通知必须自成一体：
 * 进度靠 Worker 自己轮询仓库，点击回到对应项目。
 */
object ClassroomNotify {

    private const val CHANNEL_ID = "classroom_runs"
    private const val CHANNEL_NAME = "课堂任务"
    const val NOTIFICATION_ID = 4201

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW),
        )
    }

    fun show(
        context: Context,
        projectId: ProjectId,
        title: String,
        text: String,
        indeterminate: Boolean,
        showCancel: Boolean = false,
    ) {
        runCatching {
            ensureChannel(context)
            val open = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                Intent(context, Class.forName("com.aiclassroom.app.MainActivity")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(ClassroomRunManager.EXTRA_OPEN_PROJECT, projectId.value)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(indeterminate)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
            if (indeterminate) {
                builder.setProgress(0, 0, true)
            } else {
                builder.setAutoCancel(true)
            }
            if (showCancel) {
                val cancel = PendingIntent.getBroadcast(
                    context,
                    1,
                    Intent(context, RunActionReceiver::class.java).apply {
                        action = RunActionReceiver.ACTION_CANCEL
                        putExtra(RunActionReceiver.EXTRA_PROJECT, projectId.value)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                builder.addAction(0, "取消", cancel)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, builder.build())
        }
    }

    fun foregroundInfo(context: Context, projectId: ProjectId, text: String): ForegroundInfo {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, Class.forName("com.aiclassroom.app.MainActivity")).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(ClassroomRunManager.EXTRA_OPEN_PROJECT, projectId.value)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("课堂生成中")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setProgress(0, 0, true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(idOf(projectId), notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(idOf(projectId), notification)
    }

    /** 前台服务用的通知对象（不可自动消失，否则 FGS 会被系统判为无通知）。 */
    fun buildForeground(context: Context, projectId: ProjectId, title: String, text: String): Notification {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID,
            Intent(context, Class.forName("com.aiclassroom.app.MainActivity")).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(ClassroomRunManager.EXTRA_OPEN_PROJECT, projectId.value)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setProgress(0, 0, true)
            .build()
    }

    fun clear(context: Context) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
    }

    private fun idOf(projectId: ProjectId): Int = 10000 + (projectId.value.hashCode() and 0x0fffffff)
}
