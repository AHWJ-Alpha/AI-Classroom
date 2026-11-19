package com.aiclassroom.classroom.domain

import com.aiclassroom.classroom.commit.CommitRequest
import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.commit.UnitCommitter
import com.aiclassroom.classroom.repository.CommitRejection
import com.aiclassroom.classroom.repository.CommitResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 同一任务同时只能有一个有效提交者；换人后旧令牌立刻失效。 */
class RunLeaseTest {

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
        s.saveRun(
            GenerationRun(
                id = runId, projectId = projectId, specVersion = 1, outlineVersion = 1,
                phase = RunPhase.GENERATING, status = RunStatus.QUEUED,
                totalUnits = 1, startedAt = now, updatedAt = now,
            ),
        )
        return s
    }

    private fun acquire(s: InMemoryStore, owner: String, ttl: Long = 1000) =
        RunLease.acquire(s, runId, owner, ttl, { now })

    @Test
    fun `第一个执行者拿到租约并把任务置为运行中`() {
        val s = store()
        val granted = assertIs<RunLease.Acquired.Granted>(acquire(s, "A"))
        assertEquals("A", s.run(runId)!!.owner)
        assertEquals(RunStatus.RUNNING, s.run(runId)!!.status)
        assertEquals(granted.lease.token, s.run(runId)!!.fencingToken)
    }

    @Test
    fun `租约有效期内别人抢不到`() {
        val s = store()
        acquire(s, "A")
        val busy = assertIs<RunLease.Acquired.Busy>(acquire(s, "B"))
        assertEquals("A", busy.owner)
    }

    @Test
    fun `租约过期后换人 令牌递增 so 旧执行者失效`() {
        val s = store()
        val first = assertIs<RunLease.Acquired.Granted>(acquire(s, "A", ttl = 100))
        now += 500  // 过期

        val second = assertIs<RunLease.Acquired.Granted>(acquire(s, "B", ttl = 100))
        assertTrue(second.lease.token > first.lease.token, "换人必须递增令牌")

        val stale = RunLease.validate(s, runId, "A", first.lease.token) { now }
        assertIs<RunLease.Validation.Invalid>(stale)
    }

    @Test
    fun `续租只对自己有效`() {
        val s = store()
        acquire(s, "A")
        assertTrue(RunLease.renew(s, runId, "A", 1000) { now })
        assertTrue(!RunLease.renew(s, runId, "B", 1000) { now })
    }

    @Test
    fun `释放后别人可以接手`() {
        val s = store()
        acquire(s, "A")
        RunLease.release(s, runId, "A") { now }
        assertIs<RunLease.Acquired.Granted>(acquire(s, "B"))
    }

    @Test
    fun `令牌过期的执行者提交会被拒绝`() {
        val s = store()
        s.upsertUnit(
            ClassroomUnit(
                UnitId("u1"), projectId, 1, ProjectType.NOVEL, "第一章", "目标",
                emptyList(), emptyList(), listOf("完成"), 0, UnitStatus.DRAFT_READY,
            ),
        )
        val first = assertIs<RunLease.Acquired.Granted>(acquire(s, "A", ttl = 100))
        now += 500
        acquire(s, "B", ttl = 100)   // 换人，令牌递增

        val result = UnitCommitter(s) { now }.commit(
            CommitRequest(
                projectId = projectId, unitId = UnitId("u1"),
                revision = UnitRevision(
                    id = RevisionId("rev"), unitId = UnitId("u1"), projectId = projectId,
                    version = 0, draftText = "正文", content = null, createdAt = now, sourceHash = "h",
                ),
                statePatch = null, runId = runId, idempotencyKey = "k1",
                owner = "A", fencingToken = first.lease.token,
            ),
        )
        val rejected = assertIs<CommitResult.Rejected>(result)
        assertIs<CommitRejection.StaleExecutor>(rejected.rejection)
    }
}
