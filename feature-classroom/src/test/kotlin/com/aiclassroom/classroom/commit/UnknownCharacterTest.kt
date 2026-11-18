package com.aiclassroom.classroom.commit

import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.repository.CommitRejection
import com.aiclassroom.classroom.repository.CommitResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 角色边界：没有登记的 id 不能产生状态。 */
class UnknownCharacterTest {

    private val now = 1_700_000_000_000L
    private val projectId = ProjectId("p1")
    private val unitId = UnitId("u1")
    private val runId = RunId("r1")
    private val declared = CharacterId("c1")

    private fun store(declareCharacter: Boolean): InMemoryStore {
        val s = InMemoryStore()
        s.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.NOVEL,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
            ),
        )
        s.upsertUnit(
            ClassroomUnit(
                unitId, projectId, 1, ProjectType.NOVEL, "第一章", "见面",
                emptyList(),
                if (declareCharacter) listOf(declared) else emptyList(),
                listOf("完成"), 0, UnitStatus.DRAFT_READY,
            ),
        )
        return s
    }

    private fun revision() = UnitRevision(
        id = RevisionId("r1"), unitId = unitId, projectId = projectId, version = 1,
        draftText = "正文", content = null, review = null, attemptId = null, modelName = "m",
        promptTemplateVersion = 1, createdAt = now, committedAt = now, sourceHash = "h",
    )

    private fun patch(vararg changes: StateChange) = StatePatch(
        id = "sp1", projectId = projectId, unitId = unitId, revisionId = RevisionId("r1"),
        baseVersion = 0, changes = changes.toList(), reason = "正文里的情绪变化",
        evidenceEventIds = emptyList(), idempotencyKey = "sp1",
    )

    @Test
    fun `未声明的角色产生状态时拒绝提交`() {
        val s = store(declareCharacter = false)
        val result = UnitCommitter(s) { now }.commit(
            CommitRequest(
                projectId = projectId, unitId = unitId,
                revision = revision(),
                statePatch = patch(StateChange.SetEmotion(CharacterId("陌生人"), "紧张")),
                runId = runId, idempotencyKey = "k1",
            ),
        )
        val rejected = assertIs<CommitResult.Rejected>(result)
        val reason = assertIs<CommitRejection.UnknownCharacter>(rejected.rejection)
        assertTrue(reason.ids.contains("陌生人"))
        assertNull(s.characterState(projectId, CharacterId("陌生人")), "被拒绝就不该写状态")
    }

    @Test
    fun `单元声明的角色可以产生状态`() {
        val s = store(declareCharacter = true)
        val result = UnitCommitter(s) { now }.commit(
            CommitRequest(
                projectId = projectId, unitId = unitId,
                revision = revision(),
                statePatch = patch(StateChange.SetEmotion(declared, "紧张")),
                runId = runId, idempotencyKey = "k2",
            ),
        )
        assertIs<CommitResult.Committed>(result)
        assertEquals("紧张", s.characterState(projectId, declared)!!.emotion)
    }
}
