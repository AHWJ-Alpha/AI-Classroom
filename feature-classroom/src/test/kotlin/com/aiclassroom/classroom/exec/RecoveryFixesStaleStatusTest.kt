package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 状态与草稿不一致时，恢复要如实修正。
 *
 * 真机现场：单元 #2 状态是 PLANNED，草稿里已经有 2 段 1188 字。
 * 界面显示「待生成」，用户以为进度没了 —— 其实正文都在。
 */
class RecoveryFixesStaleStatusTest {

    private val projectId = ProjectId("p1")
    private val unitId = UnitId("u1")
    private val now = 1_000_000L

    private fun store(status: UnitStatus): InMemoryStore {
        val s = InMemoryStore()
        s.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.SCENE,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = 1L, updatedAt = 1L,
            ),
        )
        s.upsertUnit(
            ClassroomUnit(
                unitId, projectId, 1, ProjectType.SCENE, "第 1 天", "g",
                emptyList(), emptyList(), listOf("完成"), 0, status,
            ),
        )
        return s
    }

    private fun draft() = UnitDraft(
        projectId = projectId, unitId = unitId,
        candidateId = "c1", inputVersion = "v1",
        segments = listOf(DraftSegment(index = 1, text = "已经写好的正文", chars = 8)),
        totalChars = 8, fencingToken = 1, updatedAt = 1L,
    )

    @Test
    fun `PLANNED 但有草稿 恢复后改成 DRAFT_READY`() {
        val s = store(UnitStatus.PLANNED)
        s.saveDraft(draft())
        RunRecovery.recover(s, now)
        assertEquals(UnitStatus.DRAFT_READY, s.unit(unitId)!!.status, "有草稿就不该显示待生成")
    }

    @Test
    fun `STALE 但有草稿 恢复后也改成 DRAFT_READY`() {
        val s = store(UnitStatus.STALE)
        s.saveDraft(draft())
        RunRecovery.recover(s, now)
        assertEquals(UnitStatus.DRAFT_READY, s.unit(unitId)!!.status)
    }

    @Test
    fun `已提交的草稿不算进度 不该改成 DRAFT_READY`() {
        val s = store(UnitStatus.READY)
        s.saveDraft(draft().copy(phase = DraftPhase.COMMITTED))
        RunRecovery.recover(s, now)
        assertEquals(UnitStatus.READY, s.unit(unitId)!!.status, "已提交的节不能被改回去")
    }
}
