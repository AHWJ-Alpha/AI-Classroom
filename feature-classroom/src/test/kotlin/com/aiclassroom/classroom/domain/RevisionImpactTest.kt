package com.aiclassroom.classroom.domain

import com.aiclassroom.classroom.commit.InMemoryStore
import kotlin.test.Test
import kotlin.test.assertEquals

/** 改了一章，依赖它的下游章节必须变成待复查。 */
class RevisionImpactTest {

    private val now = 1_700_000_000_000L
    private val projectId = ProjectId("p1")

    private fun store(vararg units: ClassroomUnit): InMemoryStore {
        val s = InMemoryStore()
        s.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.NOVEL,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
            ),
        )
        units.forEach { s.upsertUnit(it) }
        return s
    }

    private fun unit(id: String, index: Int, dependsOn: List<String> = emptyList(), status: UnitStatus = UnitStatus.READY) =
        ClassroomUnit(
            UnitId(id), projectId, index, ProjectType.NOVEL, "第" + index + "章", "目标",
            dependsOn.map { UnitId(it) }, emptyList(), listOf("完成"), 0, status,
        )

    @Test
    fun `下游链式章节都被标记待复查`() {
        val s = store(
            unit("u1", 1),
            unit("u2", 2, listOf("u1")),
            unit("u3", 3, listOf("u2")),
        )
        val marked = RevisionImpact.markDownstreamStale(s, projectId, UnitId("u1"))
        assertEquals(2, marked)
        assertEquals(UnitStatus.READY, s.unit(UnitId("u1"))!!.status, "本节由重写流程自己处理")
        assertEquals(UnitStatus.STALE, s.unit(UnitId("u2"))!!.status)
        assertEquals(UnitStatus.STALE, s.unit(UnitId("u3"))!!.status)
    }

    @Test
    fun `无关章节不受影响`() {
        val s = store(
            unit("u1", 1),
            unit("u2", 2, listOf("u1")),
            unit("other", 3),
        )
        RevisionImpact.markDownstreamStale(s, projectId, UnitId("u1"))
        assertEquals(UnitStatus.READY, s.unit(UnitId("other"))!!.status)
    }

    @Test
    fun `还没生成的下游不算在内`() {
        val s = store(
            unit("u1", 1),
            unit("u2", 2, listOf("u1"), status = UnitStatus.PLANNED),
        )
        val marked = RevisionImpact.markDownstreamStale(s, projectId, UnitId("u1"))
        assertEquals(0, marked)
        assertEquals(UnitStatus.PLANNED, s.unit(UnitId("u2"))!!.status)
    }
}
