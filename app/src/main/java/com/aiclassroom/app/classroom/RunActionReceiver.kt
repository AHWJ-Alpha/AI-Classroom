package com.aiclassroom.app.classroom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 通知栏上的「暂停 / 取消」。只发信号，真正的停止由任务自己检查。 */
class RunActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PAUSE -> {
                val pid = intent.getStringExtra(EXTRA_PROJECT)
                ClassroomRunManager.requestPause(
                    context = context.applicationContext,
                    projectId = pid?.let { com.aiclassroom.classroom.domain.ProjectId(it) },
                )
            }
            ACTION_CANCEL -> {
                // 必须带上项目 id：只发本地信号的话，后台 Worker 会继续跑。
                val pid = intent.getStringExtra(EXTRA_PROJECT)
                ClassroomRunManager.cancel(
                    context = context.applicationContext,
                    projectId = pid?.let { com.aiclassroom.classroom.domain.ProjectId(it) },
                )
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.aiclassroom.app.classroom.PAUSE"
        const val ACTION_CANCEL = "com.aiclassroom.app.classroom.CANCEL"
        const val EXTRA_PROJECT = "classroom_project_id"
    }
}