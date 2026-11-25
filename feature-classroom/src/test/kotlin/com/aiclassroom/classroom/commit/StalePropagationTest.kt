package com.aiclassroom.classroom.commit

import com.aiclassroom.classroom.domain.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * STALE 只在「前文真的变了」时向下游传播。
 *
 * 真机 bug：每次提交都无条件传播。而第一次生成时 #1 会被反复重写
 * （改方向、重试、单节生成），每写一次就把 #2..#8 全标成待重写。
 */
class StalePropagationTest {

    private val projectId = ProjectId("p1")
    private val u1 = UnitId("u1")
    private val u2 = UnitId("u2")
    private val runId = RunId("r1")
    private val now = 1_000_000L

    private fun store(): InMemoryStore {
        val s = InMemoryStore()
        s.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.SCENE,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = 1L, updatedAt = 1L,
            ),
        )
        s.upsertUnit(unit(u1, 1, emptyList(), UnitStatus.DRAFT_READY))
        s.upsertUnit(unit(u2, 2, listOf("u1"), UnitStatus.READY))
        s.saveRun(
            GenerationRun(
                runId, projectId, 1, 1, RunPhase.GENERATING, RunStatus.RUNNING,
                startedAt = now, updatedAt = now,
            ),
        )
        return s
    }

    private fun unit(id: UnitId, index: Int, deps: List<String>, status: UnitStatus) =
        ClassroomUnit(
            id = id, projectId = projectId, index = index, type = ProjectType.SCENE,
            chapter = "第" + index + "节", goal = "g",
            dependsOn = deps.map(::UnitId),
            requiredCharacters = emptyList(), completionCriteria = listOf("完成"),
            inputStateVersion = 0, status = status,
        )

    private fun revision(text: String) = UnitRevision(
        id = RevisionId("rev-" + text.hashCode()), unitId = u1, projectId = projectId, version = 0,
        draftText = text,
        content = UnitContent(
            schemaVersion = 1, unitId = u1, planVersion = 1, title = "第一节",
            objectivesCovered = emptyList(), unresolvedItems = emptyList(),
            events = listOf(UnitEvent(id = "e1", sourceBlockIds = listOf("b1"), description = "开场")),
            blocks = listOf(ContentBlock.Narration("b1", text)),
        ),
        createdAt = now, sourceHash = "h",
    )

    @Test
    fun `首次提交不把下游标成待重写`() = runBlocking {
        val s = store()
        val r = UnitCommitter(s) { now }.commit(
            CommitRequest(projectId, u1, revision("第一版正文"), null, runId, "k1", null, null, null, 0L),
        )
        assertEquals(true, r.toString().contains("Committed"), "提交应当成功，实际：" + r)
        assertEquals(UnitStatus.READY, s.unit(u2)!!.status, "首次提交不该动下游")
    }
}
