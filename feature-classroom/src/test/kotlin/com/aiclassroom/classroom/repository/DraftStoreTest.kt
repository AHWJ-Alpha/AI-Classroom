package com.aiclassroom.classroom.repository

import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.content.TextCount
import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 草稿守卫：身份、输入版本、序号、重复响应都要在事务内判准。 */
class DraftStoreTest {

    private var now = 1_000L
    private val projectId = ProjectId("p1")
    private val unitId = UnitId("u1")
    private val runId = RunId("r1")
    private val owner = "worker-a"

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
                emptyList(), emptyList(), listOf("完成"), 0, UnitStatus.PLANNED,
            ),
        )
        s.saveRun(
            GenerationRun(
                id = runId, projectId = projectId, specVersion = 1, outlineVersion = 1,
                phase = RunPhase.GENERATING, status = RunStatus.QUEUED,
                totalUnits = 1, startedAt = now, updatedAt = now,
            ),
        )
        // 正常持有执行权：owner + 令牌 + 未过期租约。
        RunLease.acquireForProject(s, projectId, runId, owner, 10_000, { now })
        return s
    }

    private fun guard(store: InMemoryStore, token: Long? = null, candidate: String = "c1"): DraftWriteGuard {
        val run = store.run(runId)!!
        return DraftWriteGuard(
            projectId = projectId,
            unitId = unitId,
            candidateId = candidate,
            expectedInputVersion = "v1",
            expectedCandidateVersion = 0,
            runId = runId,
            owner = run.owner ?: owner,
            fencingToken = token ?: run.fencingToken,
        )
    }

    private fun seg(index: Int, text: String) = DraftSegment(
        index = index,
        text = text,
        blocks = listOf(ContentBlock.Narration("c1:s" + index, text)),
        chars = 0,
    )

    private fun begin(s: InMemoryStore): DraftStore {
        val d = DraftStore(s) { now }
        assertIs<DraftOutcome.Applied>(d.begin(projectId, unitId, "c1", "v1", null))
        return d
    }

    @Test
    fun `分段按序号累计 字数由程序统计`() {
        val s = store()
        val d = begin(s)
        assertIs<DraftOutcome.Applied>(d.append(guard(s), seg(1, "她推门进来"), "v1", { seg -> TextCount.units(seg.blocks) }))
        assertIs<DraftOutcome.Applied>(d.append(guard(s), seg(2, "水烧开了"), "v1", { seg -> TextCount.units(seg.blocks) }))

        val draft = d.current(projectId, unitId)!!
        assertEquals(2, draft.segments.size)
        assertEquals(9, draft.totalChars, "5 + 4，标点不算")
        assertEquals(3, draft.nextIndex)
    }

    @Test
  fun `同一段重复送达不重复累加`() {
        val s = store()
        val d = begin(s)
        d.append(guard(s), seg(1, "她推门进来"), "v1", { seg -> TextCount.units(seg.blocks) })
        val again = d.append(guard(s), seg(1, "她推门进来"), "v1", { seg -> TextCount.units(seg.blocks) })

        assertIs<DraftOutcome.AlreadyApplied>(again)
        assertEquals(5, d.current(projectId, unitId)!!.totalChars)
    }

    @Test
    fun `同序号不同内容与跳号都被拒绝`() {
        val s = store()
        val d = begin(s)
        d.append(guard(s), seg(1, "她推门进来"), "v1", { seg -> TextCount.units(seg.blocks) })

        assertIs<DraftOutcome.Conflict>(d.append(guard(s), seg(1, "换了一句"), "v1", { seg -> TextCount.units(seg.blocks) }))
        assertIs<DraftOutcome.Conflict>(d.append(guard(s), seg(3, "跳过第二段"), "v1", { seg -> TextCount.units(seg.blocks) }))
    }

    @Test
    fun `换了候选后旧候选写入被拒`() {
        val s = store()
        val d = begin(s)
        val old = guard(s, candidate = "c1")
        d.begin(projectId, unitId, "c2", "v1", expectedOldCandidateId = "c1")

        val stale = d.append(old, seg(1, "旧候选的响应"), "v1", { seg -> TextCount.units(seg.blocks) })
        assertIs<DraftOutcome.StaleCandidate>(stale)
    }

    @Test
    fun `令牌为零或执行者不符都不放行`() {
        val s = store()
        val d = begin(s)
        assertIs<DraftOutcome.StaleExecutor>(d.append(guard(s, token = 0L), seg(1, "x"), "v1", { seg -> TextCount.units(seg.blocks) }))

        val wrongOwner = guard(s).copy(owner = "someone-else")
        assertIs<DraftOutcome.StaleExecutor>(d.append(wrongOwner, seg(1, "x"), "v1", { seg -> TextCount.units(seg.blocks) }))
    }

    @Test
    fun `租约过期后不能再写`() {
        val s = store()
        val d = begin(s)
        now += 20_000 // 租约到期
        assertIs<DraftOutcome.StaleExecutor>(d.append(guard(s), seg(1, "x"), "v1", { seg -> TextCount.units(seg.blocks) }))
    }

    @Test
    fun `输入版本变化后旧候选不能继续`() {
        val s = store()
        val d = begin(s)
        val stale = d.append(guard(s), seg(1, "x"), "v2", { seg -> TextCount.units(seg.blocks) })
        assertIs<DraftOutcome.StaleInput>(stale)
    }
}
