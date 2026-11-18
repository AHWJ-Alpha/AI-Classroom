package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.domain.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 取消不是暂停：状态、返回值、后续可恢复性都必须不同。 */
class RunCancelTest {

    private val projectId = ProjectId("p1")
    private val runId = RunId("r1")
    private val now = 1_700_000_000_000L

    private fun store(): InMemoryStore {
        val s = InMemoryStore()
        s.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.TEACHING,
                lifecycle = ProjectLifecycle.ACTIVE, workVersion = 0,
                createdAt = now, updatedAt = now,
            ),
        )
        s.upsertUnit(
            ClassroomUnit(
                UnitId("u1"), projectId, 1, ProjectType.TEACHING, "第1节", "目标",
                emptyList(), emptyList(), listOf("完成"), 0, UnitStatus.PLANNED,
            ),
        )
        s.saveRun(
            GenerationRun(
                id = runId, projectId = projectId, specVersion = 1, outlineVersion = 1,
                phase = RunPhase.GENERATING, status = RunStatus.RUNNING,
                totalUnits = 1, startedAt = now, updatedAt = now,
            ),
        )
        return s
    }

    private val neverRun = object : UnitRunner {
        override suspend fun execute(projectId: ProjectId, unitId: UnitId, runId: RunId): UnitOutcome =
            UnitOutcome.NeedsAttention("不该被执行")
    }

    @Test
    fun `取消返回 Cancelled 并写入 CANCELLED 状态`() = runBlocking {
        val s = store()
        val control = object : RunControl {
            override fun shouldContinue() = false
            override fun isCancelled() = true
        }
        val outcome = RunEngine(s, neverRun, control, clock = { now }).run(projectId, runId)
        assertIs<RunOutcome.Cancelled>(outcome)
        assertEquals(RunStatus.CANCELLED, s.run(runId)!!.status)
    }

    @Test
    fun `暂停仍然写 PAUSED 且不等于取消`() = runBlocking {
        val s = store()
        val control = object : RunControl {
            override fun shouldContinue() = false
        }
        val outcome = RunEngine(s, neverRun, control, clock = { now }).run(projectId, runId)
        assertIs<RunOutcome.Paused>(outcome)
        assertEquals(RunStatus.PAUSED, s.run(runId)!!.status)
    }
}
