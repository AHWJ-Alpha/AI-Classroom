package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RunRecoveryTest {

    private val now = 1_700_000_000_000L
    private val projectId = ProjectId("p1")

    private fun run(status: RunStatus, id: String = "r1") = GenerationRun(
        id = RunId(id), projectId = projectId, specVersion = 1, outlineVersion = 1,
        phase = RunPhase.GENERATING, status = status, startedAt = now, updatedAt = now,
    )

    private fun unit(status: UnitStatus, id: String = "u1", index: Int = 1) = ClassroomUnit(
        id = UnitId(id), projectId = projectId, index = index, type = ProjectType.NOVEL,
        chapter = "c", goal = "g", dependsOn = emptyList(), requiredCharacters = emptyList(),
        completionCriteria = listOf("done"), inputStateVersion = 0, status = status,
    )

    private fun storeWith(vararg units: ClassroomUnit): InMemoryStore {
        val store = InMemoryStore()
        store.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.NOVEL,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
            ),
        )
        units.forEach { store.upsertUnit(it) }
        return store
    }

    @Test
    fun `RUNNING 的任务在重建后变成 PAUSED 并留下说明`() {
        val store = storeWith()
        store.saveRun(run(RunStatus.RUNNING))

        val report = RunRecovery.recover(store, now)

        assertEquals(1, report.pausedRuns)
        val recovered = store.run(RunId("r1"))!!
        assertEquals(RunStatus.PAUSED, recovered.status)
        assertTrue(recovered.error!!.contains("已暂停"))
    }

    @Test
    fun `PAUSE_REQUESTED 的任务也会被恢复`() {
        val store = storeWith()
        store.saveRun(run(RunStatus.PAUSE_REQUESTED))
        RunRecovery.recover(store, now)
        assertEquals(RunStatus.PAUSED, store.run(RunId("r1"))!!.status)
    }

    @Test
    fun `已完成的任务不会被改写`() {
        val store = storeWith()
        store.saveRun(run(RunStatus.COMPLETED))
        val report = RunRecovery.recover(store, now)
        assertFalse(report.changed)
        assertEquals(RunStatus.COMPLETED, store.run(RunId("r1"))!!.status)
    }

    @Test
    fun `生成中的单元回到 PLANNED，已 READY 的不动`() {
        val store = storeWith(
            unit(UnitStatus.GENERATING, "u1", 1),
            unit(UnitStatus.REVIEWING, "u2", 2),
            unit(UnitStatus.READY, "u3", 3),
        )

        val report = RunRecovery.recover(store, now)

        assertEquals(2, report.resetUnits)
        assertEquals(UnitStatus.PLANNED, store.unit(UnitId("u1"))!!.status)
        assertEquals(UnitStatus.PLANNED, store.unit(UnitId("u2"))!!.status)
        assertEquals(UnitStatus.READY, store.unit(UnitId("u3"))!!.status)
    }

    @Test
    fun `DRAFT_READY 也要放回 PLANNED，否则依赖链会卡死`() {
        // 实测事故：草稿状态遗留后 nextReadyUnit 永远不选它，后面所有单元跟着停摆。
        val store = storeWith(
            unit(UnitStatus.DRAFT_READY, "u1", 1),
            unit(UnitStatus.PLANNED, "u2", 2),
        )
        val report = RunRecovery.recover(store, now)
        assertEquals(1, report.resetUnits)
        assertEquals(UnitStatus.PLANNED, store.unit(UnitId("u1"))!!.status)
    }
    @Test
    fun `没有卡住状态时报告未变更`() {
        val store = storeWith(unit(UnitStatus.READY))
        assertFalse(RunRecovery.recover(store, now).changed)
    }

    @Test
    fun `暂停同时清理排队任务与有效租约但保留令牌`() {
        val store = storeWith()
        store.saveRun(run(RunStatus.RUNNING).copy(owner = "worker", leaseUntil = now + 60000, fencingToken = 7))
        store.saveRun(run(RunStatus.QUEUED, "queued"))
        assertEquals(2, RunRecovery.markPaused(store, projectId) { now })
        val stopped = store.run(RunId("r1"))!!
        assertEquals(RunStatus.PAUSED, stopped.status)
        assertEquals(null, stopped.owner)
        assertEquals(0L, stopped.leaseUntil)
        assertEquals(7L, stopped.fencingToken)
        assertEquals(RunStatus.PAUSED, store.run(RunId("queued"))!!.status)
    }
}
