package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegenerationTest {

    private val now = 1_700_000_000_000L
    private val projectId = ProjectId("p1")
    private val directive = UnitDirective(notes = "换个讲法", updatedAt = now)

    private fun store(vararg units: ClassroomUnit): InMemoryStore {
        val s = InMemoryStore()
        s.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.TEACHING,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
            ),
        )
        units.forEach { s.upsertUnit(it) }
        return s
    }

    private fun unit(id: String, index: Int, status: UnitStatus, dir: UnitDirective? = null) =
        ClassroomUnit(
            id = UnitId(id), projectId = projectId, index = index, type = ProjectType.TEACHING,
            chapter = "c", goal = "g", dependsOn = emptyList(), requiredCharacters = emptyList(),
            completionCriteria = listOf("done"), inputStateVersion = 0, status = status,
            directive = dir,
        )

    @Test
    fun `STALE 单元被放回 PLANNED 且保留方向指令`() {
        val s = store(unit("u1", 1, UnitStatus.STALE, directive))
        val reset = Regeneration.resetStale(s, projectId)

        assertEquals(1, reset.count)
        val after = s.unit(UnitId("u1"))!!
        assertEquals(UnitStatus.PLANNED, after.status)
        assertEquals("换个讲法", after.directive!!.notes)
    }

    @Test
    fun `READY 与生成中的单元不会被重置`() {
        val s = store(
            unit("u1", 1, UnitStatus.READY),
            unit("u2", 2, UnitStatus.GENERATING),
            unit("u3", 3, UnitStatus.STALE),
        )
        val reset = Regeneration.resetStale(s, projectId)

        assertEquals(1, reset.count)
        assertEquals(UnitStatus.READY, s.unit(UnitId("u1"))!!.status)
        assertEquals(UnitStatus.GENERATING, s.unit(UnitId("u2"))!!.status)
        assertEquals(UnitStatus.PLANNED, s.unit(UnitId("u3"))!!.status)
    }

    @Test
    fun `没有 STALE 时报告为空`() {
        val s = store(unit("u1", 1, UnitStatus.READY))
        assertFalse(Regeneration.resetStale(s, projectId).any)
    }

    @Test
    fun `单个单元重置只影响自己`() {
        val s = store(
            unit("u1", 1, UnitStatus.STALE),
            unit("u2", 2, UnitStatus.STALE),
        )
        assertTrue(Regeneration.resetUnit(s, UnitId("u1")))
        assertEquals(UnitStatus.PLANNED, s.unit(UnitId("u1"))!!.status)
        assertEquals(UnitStatus.STALE, s.unit(UnitId("u2"))!!.status)
    }

    @Test
    fun `非 STALE 的单元拒绝重置`() {
        val s = store(unit("u1", 1, UnitStatus.READY))
        assertFalse(Regeneration.resetUnit(s, UnitId("u1")))
    }
}
