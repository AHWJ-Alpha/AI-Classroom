package com.aiclassroom.classroom.domain

import com.aiclassroom.classroom.commit.InMemoryStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 租约必须保护**项目**，而不是某个 run。
 *
 * 之前每次执行都新建 runId 再加租约：两个 run 之间根本不互斥，
 * 只靠 WorkManager 的 uniqueWork 兜着，页面入口或恢复路径都不受保护。
 */
class ProjectLeaseTest {

    private var now = 1_000_000L
    private val projectId = ProjectId("p1")

    private fun store(): InMemoryStore {
        val s = InMemoryStore()
        s.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.NOVEL,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
            ),
        )
        listOf("r1", "r2").forEach { id ->
            s.saveRun(
                GenerationRun(
                    id = RunId(id), projectId = projectId, specVersion = 1, outlineVersion = 1,
                    phase = RunPhase.GENERATING, status = RunStatus.QUEUED,
                    totalUnits = 1, startedAt = now, updatedAt = now,
                ),
            )
        }
        return s
    }

    @Test
    fun `另一个 run 抢不到同一项目的执行权`() {
        val s = store()
        val first = assertIs<RunLease.Acquired.Granted>(
            RunLease.acquireForProject(s, projectId, RunId("r1"), "A", 10_000, { now }),
        )

        val second = RunLease.acquireForProject(s, projectId, RunId("r2"), "B", 10_000, { now })
        val busy = assertIs<RunLease.Acquired.Busy>(second)
        assertEquals("A", busy.owner)
        assertTrue(first.lease.token >= 0)
    }

    @Test
    fun `换执行者时令牌在项目内递增 so 旧令牌失效`() {
        val s = store()
        val first = assertIs<RunLease.Acquired.Granted>(
            RunLease.acquireForProject(s, projectId, RunId("r1"), "A", 100, { now }),
        )
        now += 500   // 过期

        val second = assertIs<RunLease.Acquired.Granted>(
            RunLease.acquireForProject(s, projectId, RunId("r2"), "B", 10_000, { now }),
        )
        assertTrue(second.lease.token > first.lease.token)

        val stale = RunLease.validate(s, RunId("r1"), "A", first.lease.token, { now })
        assertIs<RunLease.Validation.Invalid>(stale)
    }

    @Test
    fun `force 可以接管（Worker 恢复场景）`() {
        val s = store()
        RunLease.acquireForProject(s, projectId, RunId("r1"), "A", 10_000, { now })

        val taken = RunLease.acquireForProject(
            s, projectId, RunId("r2"), "worker", 10_000, { now }, force = true,
        )
        assertIs<RunLease.Acquired.Granted>(taken)
    }

    @Test
    fun `被接管后旧执行者的令牌必须失效`() {
        // 关键回归：接管只给新 run 递增令牌，旧 run 的令牌没变。
        // 如果校验只看旧 run 自己的令牌，旧执行者永远「有效」—— 租约等于没做。
        val s = store()
        val first = assertIs<RunLease.Acquired.Granted>(
            RunLease.acquireForProject(s, projectId, RunId("r1"), "A", 10_000, { now }),
        )
        RunLease.acquireForProject(s, projectId, RunId("r2"), "B", 10_000, { now }, force = true)

        val stale = RunLease.validateForProject(s, projectId, RunId("r1"), "A", first.lease.token, { now })
        assertIs<RunLease.Validation.Invalid>(stale)

        // 新执行者自己仍然有效
        val latest = s.run(RunId("r2"))!!
        assertIs<RunLease.Validation.Valid>(
            RunLease.validateForProject(s, projectId, RunId("r2"), "B", latest.fencingToken, { now }),
        )
    }
}
