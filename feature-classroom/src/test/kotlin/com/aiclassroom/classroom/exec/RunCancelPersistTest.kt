package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.store.FileClassroomStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 用户在网络请求途中取消时，协程被直接取消，引擎来不及写状态。
 * 这里验证「补写 CANCELLED 之后，重建仓库仍然是 CANCELLED」——
 * 否则重启后任务一直停在 RUNNING，看起来像还在跑。
 */
class RunCancelPersistTest {

    private val now = 1_700_000_000_000L
    private val projectId = ProjectId("p1")
    private val runId = RunId("r1")

    @Test
    fun `取消落盘后重建仓库仍是 CANCELLED`() {
        val dir = Files.createTempDirectory("cancel").toFile()
        val store = FileClassroomStore(dir)
        store.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.TEACHING,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
            ),
        )
        store.saveRun(
            GenerationRun(
                id = runId, projectId = projectId, specVersion = 1, outlineVersion = 1,
                phase = RunPhase.GENERATING, status = RunStatus.RUNNING,
                totalUnits = 3, startedAt = now, updatedAt = now,
            ),
        )

        val marked = RunRecovery.markCancelled(store, projectId) { now }
        assertEquals(1, marked)

        val reopened = FileClassroomStore(dir)
        assertEquals(RunStatus.CANCELLED, reopened.run(runId)!!.status)
    }

    @Test
    fun `已经结束的任务不会被改写成取消`() {
        val dir = Files.createTempDirectory("cancel2").toFile()
        val store = FileClassroomStore(dir)
        store.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.TEACHING,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
            ),
        )
        store.saveRun(
            GenerationRun(
                id = runId, projectId = projectId, specVersion = 1, outlineVersion = 1,
                phase = RunPhase.COMMITTING, status = RunStatus.COMPLETED,
                totalUnits = 3, startedAt = now, updatedAt = now,
            ),
        )

        assertEquals(0, RunRecovery.markCancelled(store, projectId) { now })
        assertEquals(RunStatus.COMPLETED, store.run(runId)!!.status)
    }
}
