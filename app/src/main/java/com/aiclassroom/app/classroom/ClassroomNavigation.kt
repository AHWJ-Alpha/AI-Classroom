package com.aiclassroom.app.classroom

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 「打开某个课堂项目」的导航意图。
 *
 * 为什么不用参数一层层往下传：消息卡在很深的消息列表里，而课堂覆盖层挂在根部，
 * 中间隔着好几个 composable。这里只承载一个意图，由根部消费，比穿透五层参数可靠。
 */
object ClassroomNavigation {

    /** 待打开的项目 id；null 表示没有待处理意图。 */
    var pendingProjectId by mutableStateOf<String?>(null)
        private set

    /** 自增序号：同一个项目重复点击也能触发一次消费。 */
    var requestSeq by mutableStateOf(0)
        private set

    fun openProject(projectId: String) {
        pendingProjectId = projectId
        requestSeq++
    }

    /** 根部消费后清空，避免重组时反复打开。 */
    fun consume() {
        pendingProjectId = null
    }
}
