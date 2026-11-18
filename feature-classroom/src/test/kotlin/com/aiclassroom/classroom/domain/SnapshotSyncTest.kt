package com.aiclassroom.classroom.domain

import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.exec.Regeneration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SnapshotSyncTest {

    private val now = 1_700_000_000_000L
    private val projectId = ProjectId("p1")

    private fun mat(name: String, content: String) = SnapshotMaterial(name = name, content = content)

    @Test
    fun `内容没变时差异为空`() {
        val a = listOf(mat("讲义", "第一章"))
        assertTrue(SnapshotSync.diff(a, listOf(mat("讲义", "第一章"))).empty)
    }

    @Test
    fun `新增 改动 移除 都能识别`() {
        val old = listOf(mat("讲义", "第一章"), mat("角色卡", "旧设定"), mat("旧资料", "x"))
        val now = listOf(mat("讲义", "第一章改过"), mat("角色卡", "旧设定"), mat("新资料", "y"))
        val diff = SnapshotSync.diff(old, now)
        assertEquals(3, diff.changes.size)
        assertTrue(diff.changes.any { it.kind == "changed" && it.name == "讲义" })
        assertTrue(diff.changes.any { it.kind == "removed" && it.name == "旧资料" })
        assertTrue(diff.changes.any { it.kind == "added" && it.name == "新资料" })
    }

    @Test
    fun `指纹优先用已有的 checksum`() {
        val m = SnapshotMaterial(name = "a", content = "很长很长的内容", checksum = "abc")
        assertEquals("abc", SnapshotSync.checksumOf(m))
    }

    @Test
    fun `同步后已完成的单元被保守标记为待复查`() {
        val store = InMemoryStore()
        store.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.NOVEL,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
            ),
        )
        fun unit(id: String, index: Int, status: UnitStatus) = ClassroomUnit(
            UnitId(id), projectId, index, ProjectType.NOVEL, "c", "g",
            emptyList(), emptyList(), listOf("done"), 0, status,
        )
        store.upsertUnit(unit("u1", 1, UnitStatus.READY))
        store.upsertUnit(unit("u2", 2, UnitStatus.READY))
        store.upsertUnit(unit("u3", 3, UnitStatus.PLANNED))

        val n = Regeneration.markReadyStale(store, projectId)

        assertEquals(2, n)
        assertEquals(UnitStatus.STALE, store.unit(UnitId("u1"))!!.status)
        assertEquals(UnitStatus.STALE, store.unit(UnitId("u2"))!!.status)
        assertEquals(UnitStatus.PLANNED, store.unit(UnitId("u3"))!!.status)
    }
}
