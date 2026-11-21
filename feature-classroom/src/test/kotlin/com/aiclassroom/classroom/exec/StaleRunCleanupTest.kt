package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * 失效执行者必须被清理，否则项目被死任务占着，新任务永远启动不了。
 */
class StaleRunCleanupTest {

    private var now = 1_000_000L
    private val projectId = ProjectId("p1")

    private fun store(): InMemoryStore {
        val s = InMemoryStore()
        s.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.SCENE,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
            ),
        )
        s.upsertUnit(
            ClassroomUnit(
                UnitId("u1"), projectId, 1, ProjectType.SCENE, "第 1-3 天", "建立边界",
                emptyList(), emptyList(), listOf("完成"), 0, UnitStatus.DRAFT_READY,
            ),
        )
        s.saveRun(
            GenerationRun(
                id = RunId("r1"), projectId = projectId, specVersion = 1, outlineVersion = 1,
                phase = RunPhase.GENERATING, status = RunStatus.QUEUED,
                totalUnits = 3, startedAt = now, updatedAt = now,
            ),
        )
        return s
    }

    @Test
    fun `过期租约被清掉 项目不再被死任务占着`() {
        val s = store()
        RunLease.acquireForProject(s, projectId, RunId("r1"), "dead", 100, { now })
        now += 500 // 租约过期，执行者已死

        val report = RunRecovery.cleanupStale(s, projectId, now)

        assertEquals(1, report.pausedRuns)
        val dead = s.run(RunId("r1"))!!
        assertEquals(RunStatus.PAUSED, dead.status)
        assertNull(dead.owner, "不清 owner 的话新任务永远拿到 Busy")
        assertEquals(0L, dead.leaseUntil)
        assertEquals(UnitStatus.PLANNED, s.unit(UnitId("u1"))!!.status, "未提交的单元要能重新被选中")
    }

    @Test
    fun `清理后新执行者可以接管且令牌递增`() {
        val s = store()
        val first = assertIs<RunLease.Acquired.Granted>(
            RunLease.acquireForProject(s, projectId, RunId("r1"), "dead", 100, { now }),
        )
        now += 500
        RunRecovery.cleanupStale(s, projectId, now)

        val second = assertIs<RunLease.Acquired.Granted>(
            RunLease.acquireForProject(s, projectId, RunId("r1"), "worker", 10_000, { now }),
        )
        assert(second.lease.token > first.lease.token) { "接管后令牌必须递增" }
    }

    @Test
    fun `有效租约不会被清理`() {
        val s = store()
        RunLease.acquireForProject(s, projectId, RunId("r1"), "alive", 10_000, { now })

        val report = RunRecovery.cleanupStale(s, projectId, now)

        assertEquals(0, report.pausedRuns)
        assertEquals("alive", s.run(RunId("r1"))!!.owner)
        assertEquals(UnitStatus.DRAFT_READY, s.unit(UnitId("u1"))!!.status)
    }
}
