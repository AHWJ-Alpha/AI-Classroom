package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 全局恢复不能打断正在跑的任务。
 *
 * 打开课堂页会调用全局 recover：如果它把所有 RUNNING 都标成 PAUSED，
 * 后台正在生成的项目就被打断了 —— 而那个任务手里握着有效租约。
 */
class RecoverRespectsLeaseTest {

    private var now = 1_000_000L
    private val projectId = ProjectId("p1")
    private val runId = RunId("r1")

    private fun store(): InMemoryStore {
        val s = InMemoryStore()
        s.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.NOVEL,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
            ),
        )
        s.upsertUnit(
            ClassroomUnit(
                UnitId("u1"), projectId, 1, ProjectType.NOVEL, "第一章", "目标",
                emptyList(), emptyList(), listOf("完成"), 0, UnitStatus.DRAFT_READY,
            ),
        )
        s.saveRun(
            GenerationRun(
                id = runId, projectId = projectId, specVersion = 1, outlineVersion = 1,
                phase = RunPhase.GENERATING, status = RunStatus.QUEUED,
                totalUnits = 1, startedAt = now, updatedAt = now,
            ),
        )
        return s
    }

    @Test
    fun `有有效租约的任务不会被全局恢复打断`() {
        val s = store()
        RunLease.acquire(s, runId, "worker", 10_000, { now })
        assertEquals(RunStatus.RUNNING, s.run(runId)!!.status)

        val report = RunRecovery.recover(s, now)

        assertEquals(0, report.pausedRuns, "持有有效租约的任务不该被暂停")
        assertEquals(RunStatus.RUNNING, s.run(runId)!!.status)
        assertEquals(UnitStatus.DRAFT_READY, s.unit(UnitId("u1"))!!.status, "它的单元也不该被重置")
    }

    @Test
    fun `租约过期的任务仍会被恢复`() {
        val s = store()
        RunLease.acquire(s, runId, "worker", 100, { now })
        now += 500   // 租约过期：执行者已经死了

        val report = RunRecovery.recover(s, now)

        assertEquals(1, report.pausedRuns)
        assertEquals(RunStatus.PAUSED, s.run(runId)!!.status)
        assertEquals(UnitStatus.PLANNED, s.unit(UnitId("u1"))!!.status)
    }
}
