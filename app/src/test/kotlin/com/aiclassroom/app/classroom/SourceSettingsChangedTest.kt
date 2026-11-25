package com.aiclassroom.app.classroom

import com.aiclassroom.classroom.domain.ContextSnapshot
import com.aiclassroom.classroom.domain.ProjectId
import com.aiclassroom.classroom.domain.SnapshotId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自动同步不能把「只是聊了几句」当成「设定变了」。
 *
 * 真机 bug：run() 每次开始生成前自动同步快照，而 syncSource 无条件
 * markReadyStale —— 于是每按一次「生成」，所有写好的节全变成「待重写」。
 * 用户看到的就是「一生成进度就没了」。
 */
class SourceSettingsChangedTest {

    private val projectId = ProjectId("p1")

    private fun snapshot(
        mentorPrompt: String = "话少克制",
        userAlias: String = "哥哥",
        historyIndex: Int = 10,
    ) = ContextSnapshot(
        id = SnapshotId("s1"), projectId = projectId, version = 1,
        createdAt = 1L,
        mentorName = "她", mentorPrompt = mentorPrompt, userAlias = userAlias,
        historyThroughIndex = historyIndex,
    )

    @Test
    fun 对话多了一句不算设定变化() {
        val changed = ClassroomService.sourceSettingsChangedForTest(
            snapshot(historyIndex = 10), snapshot(historyIndex = 12),
        )
        assertFalse("只是多聊了几句，不该把已写好的节标成待重写", changed)
    }

    @Test
    fun 人格改了才算设定变化() {
        val changed = ClassroomService.sourceSettingsChangedForTest(
            snapshot(mentorPrompt = "话少克制"), snapshot(mentorPrompt = "活泼外向"),
        )
        assertTrue("人格变了，下游确实该复查", changed)
    }

    @Test
    fun 称呼改了也算设定变化() {
        val changed = ClassroomService.sourceSettingsChangedForTest(
            snapshot(userAlias = "哥哥"), snapshot(userAlias = "老师"),
        )
        assertTrue(changed)
    }
}
