package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 中断恢复不能把「已经写了一些」的单元显示成「待生成」。
 *
 * 真机症状：应用被杀后重开，界面回到「待生成」，看起来进度全丢了。
 * 实际上草稿还在库里，只是单元状态被无条件打回 PLANNED。
 */
class RecoveryKeepsDraftTest {

    private val projectId = ProjectId("p1")
    private val unitId = UnitId("u1")
    private val now = 1_000_000L

    private fun store(): InMemoryStore {
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
                emptyList(), emptyList(), listOf("完成"), 0, UnitStatus.DRAFT_READY,
            ),
        )
        return s
    }

    @Test
    fun `有草稿的单元恢复后仍显示有进度`() {
        val s = store()
        s.saveDraft(
            UnitDraft(
                projectId = projectId, unitId = unitId,
                candidateId = "c1", inputVersion = "v1",
                segments = listOf(
                    DraftSegment(index = 1, text = "已经写好的一段正文", chars = 9),
                ),
                totalChars = 9, fencingToken = 1, updatedAt = 1L,
            ),
        )

        RunRecovery.recover(s, now)

        assertEquals(
            UnitStatus.DRAFT_READY, s.unit(unitId)!!.status,
            "草稿还在，就不该退回「待生成」",
        )
        assertTrue(s.draft(projectId, unitId)!!.segments.isNotEmpty(), "草稿必须还在")
    }

    @Test
    fun `没有草稿的单元恢复后退回待生成`() {
        val s = store()
        RunRecovery.recover(s, now)
        assertEquals(UnitStatus.PLANNED, s.unit(unitId)!!.status)
    }
}
