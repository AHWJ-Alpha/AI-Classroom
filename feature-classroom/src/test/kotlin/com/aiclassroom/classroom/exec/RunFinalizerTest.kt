package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.event.EventOutbox
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 任务完成时，状态与结果事件必须在同一次提交里产生。 */
class RunFinalizerTest {

    private val now = 1_700_000_000_000L
    private val projectId = ProjectId("p1")
    private val runId = RunId("r1")

    private fun store(source: String?): InMemoryStore {
        val s = InMemoryStore()
        s.createProject(
            ClassroomProject(
                id = projectId, title = "Python 面向对象", type = ProjectType.TEACHING,
                lifecycle = ProjectLifecycle.ACTIVE, workVersion = 3,
                createdAt = now, updatedAt = now, sourceConversationId = source,
            ),
        )
        s.upsertUnit(
            ClassroomUnit(
                UnitId("u1"), projectId, 1, ProjectType.TEACHING, "第1节", "目标",
                emptyList(), emptyList(), listOf("完成"), 0, UnitStatus.READY,
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

    private val noRunner = object : UnitRunner {
        override suspend fun execute(projectId: ProjectId, unitId: UnitId, runId: RunId): UnitOutcome =
            UnitOutcome.Failed("不该被执行")
    }

    @Test
    fun `全部完成时直接产生待投递的完成事件`() = runBlocking {
        val s = store(source = "conv-a")
        val outcome = RunEngine(s, noRunner, object : RunControl {
            override fun shouldContinue() = true
        }, clock = { now }).run(projectId, runId)

        assertTrue(outcome is RunOutcome.Completed)
        assertEquals(RunStatus.COMPLETED, s.run(runId)!!.status)

        val pending = EventOutbox.pending(s, "conv-a")
        assertEquals(1, pending.size, "完成事件应该已经入队")
        assertEquals(EventType.COMPLETED, pending.first().type)
        assertEquals(3, pending.first().workVersion)
    }

    @Test
    fun `来源缺失的项目不产生事件但仍然完成`() = runBlocking {
        val s = store(source = null)
        val outcome = RunEngine(s, noRunner, object : RunControl {
            override fun shouldContinue() = true
        }, clock = { now }).run(projectId, runId)

        assertTrue(outcome is RunOutcome.Completed)
        assertEquals(RunStatus.COMPLETED, s.run(runId)!!.status)
    }

    @Test
    fun `重复收尾不会产生第二个事件`() = runBlocking {
        val s = store(source = "conv-a")
        val engine = RunEngine(s, noRunner, object : RunControl {
            override fun shouldContinue() = true
        }, clock = { now })
        engine.run(projectId, runId)

        // 再跑一次同一个任务：事件按 id 幂等
        RunFinalizer.finish(
            store = s,
            project = s.project(projectId)!!,
            run = s.run(runId),
            runStatus = RunStatus.COMPLETED,
            projectLifecycle = ProjectLifecycle.READY,
            type = EventType.COMPLETED,
            summary = "共 1 节",
            clock = { now },
        )
        assertEquals(1, EventOutbox.pending(s, "conv-a").size)
    }
}
