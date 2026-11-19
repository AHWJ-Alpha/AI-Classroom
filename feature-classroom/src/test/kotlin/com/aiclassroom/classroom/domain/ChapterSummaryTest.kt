package com.aiclassroom.classroom.domain

import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.commit.CommitRequest
import com.aiclassroom.classroom.commit.UnitCommitter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 章节摘要：提交时生成、绑定 revision、能被后续章节用到。 */
class ChapterSummaryTest {

    private val now = 1_700_000_000_000L
    private val projectId = ProjectId("p1")
    private val unitId = UnitId("u1")

    private fun unit() = ClassroomUnit(
        unitId, projectId, 1, ProjectType.NOVEL, "第一章 雨夜", "讲开场",
        emptyList(), emptyList(), listOf("完成"), 0, UnitStatus.READY, officialVersion = 1,
    )

    private fun revision(id: String = "r1") = UnitRevision(
        id = RevisionId(id), unitId = unitId, projectId = projectId, version = 1,
        draftText = "正文",
        content = UnitContent(
            schemaVersion = 1, unitId = unitId, planVersion = 1, title = "第一章",
            objectivesCovered = listOf("理解伏笔"),
            unresolvedItems = listOf("谁是送信人"),
            events = listOf(
                UnitEvent(id = "e1", sourceBlockIds = listOf("b1"), description = "主角收到一封信"),
            ),
            blocks = listOf(ContentBlock.Narration("b1", "雨夜，门外有人塞进一封信。")),
        ),
        createdAt = now, committedAt = now, sourceHash = "h",
    )

    @Test
    fun `摘要抓住事件 未解决事项与教学目标`() {
        val s = SummaryBuilder.from(unit(), revision()) { now }
        assertEquals(listOf("主角收到一封信"), s.keyEvents)
        assertEquals(listOf("谁是送信人"), s.unresolvedItems)
        assertEquals(listOf("理解伏笔"), s.objectivesCovered)
        assertTrue(s.gist.contains("信"))
    }

    @Test
    fun `提交时写入摘要并绑定 revision`() {
        val store = InMemoryStore()
        store.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.NOVEL,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
            ),
        )
        store.upsertUnit(unit().copy(status = UnitStatus.DRAFT_READY))

        UnitCommitter(store) { now }.commit(
            CommitRequest(
                projectId = projectId, unitId = unitId, revision = revision(),
                statePatch = null, runId = RunId("run1"), idempotencyKey = "k1",
            ),
        )

        val saved = store.summaries(projectId)
        assertEquals(1, saved.size)
        assertNotNull(store.summaryForRevision(saved.first().revisionId))
        assertEquals(listOf("谁是送信人"), saved.first().unresolvedItems)
    }

    @Test
    fun `摘要渲染包含章号与未解决事项`() {
        val s = SummaryBuilder.from(unit(), revision()) { now }
        val line = s.render()
        assertTrue(line.contains("第 1 章"))
        assertTrue(line.contains("谁是送信人"))
        val compact = s.render(compact = true)
        assertTrue(!compact.contains("谁是送信人"), "紧凑模式只留概览")
    }
}
