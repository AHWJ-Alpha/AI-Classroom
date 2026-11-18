package com.aiclassroom.app.classroom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 通知栏上的「暂停 / 取消」。只发信号，真正的停止由任务自己检查。 */
class RunActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PAUSE -> ClassroomRunManager.requestPause()
            ACTION_CANCEL -> ClassroomRunManager.cancel()
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.aiclassroom.app.classroom.PAUSE"
        const val ACTION_CANCEL = "com.aiclassroom.app.classroom.CANCEL"
    }
}