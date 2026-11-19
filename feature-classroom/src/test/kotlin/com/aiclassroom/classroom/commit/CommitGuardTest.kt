package com.aiclassroom.classroom.commit

import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.repository.CommitRejection
import com.aiclassroom.classroom.repository.CommitResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * 第二阶段：多人物补丁逐个校验基础版本；输入过期时拒绝覆盖。
 */
class CommitGuardTest {

    private val now = 1_700_000_000_000L
    private val projectId = ProjectId("p1")
    private val unitId = UnitId("u1")
    private val runId = RunId("r1")
    private val alice = CharacterId("alice")
    private val bob = CharacterId("bob")

    private fun store(specVersion: Int = 1): InMemoryStore {
        val s = InMemoryStore()
        s.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.NOVEL,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
            ),
        )
        s.saveSpec(
            ProjectSpec(
                projectId = projectId, version = specVersion, rawRequest = "写故事", topic = "校园",
                goals = listOf("讲完"), audience = "读者", language = "zh", style = "平实",
                expectedLength = "短", unitCount = 1, runMode = RunMode.CONFIRM_OUTLINE,
                reviewPolicy = ReviewPolicy.STANDARD, hardConstraints = emptyList(),
                authorRules = emptyList(), publicWorld = "", createdAt = now,
            ),
        )
        s.upsertUnit(
            ClassroomUnit(
                unitId, projectId, 1, ProjectType.NOVEL, "第一章", "见面",
                emptyList(), listOf(alice, bob), listOf("完成"), 0, UnitStatus.DRAFT_READY,
            ),
        )
        s.saveCharacterState(CharacterState(alice, projectId, version = 3))
        s.saveCharacterState(CharacterState(bob, projectId, version = 1))
        return s
    }

    private fun revision() = UnitRevision(
        id = RevisionId("r1"), unitId = unitId, projectId = projectId, version = 0,
        draftText = "正文",
        content = UnitContent(
            schemaVersion = 1, unitId = unitId, planVersion = 1, title = "第一章",
            objectivesCovered = emptyList(), unresolvedItems = emptyList(),
            events = listOf(UnitEvent(id = "e1", sourceBlockIds = listOf("b1"), description = "见面")),
            blocks = listOf(ContentBlock.Narration("b1", "正文")),
        ),
        createdAt = now, sourceHash = "h",
    )

    private fun patch(baseVersions: Map<CharacterId, Int>) = StatePatch(
        id = "sp", projectId = projectId, unitId = unitId, revisionId = RevisionId("r1"),
        baseVersion = 0, baseVersions = baseVersions,
        changes = listOf(
            StateChange.SetEmotion(alice, "紧张"),
            StateChange.SetEmotion(bob, "放松"),
        ),
        reason = "见面", evidenceEventIds = listOf("e1"), idempotencyKey = "sp",
    )

    @Test
    fun `多人物基础版本各自正确时提交成功`() {
        val s = store()
        val result = UnitCommitter(s) { now }.commit(
            CommitRequest(
                projectId = projectId, unitId = unitId, revision = revision(),
                statePatch = patch(mapOf(alice to 3, bob to 1)),
                runId = runId, idempotencyKey = "k1",
            ),
        )
        assertIs<CommitResult.Committed>(result)
        assertEquals("紧张", s.characterState(projectId, alice)!!.emotion)
        assertEquals("放松", s.characterState(projectId, bob)!!.emotion)
    }

    @Test
    fun `第二个人物版本已过期时会被拒绝`() {
        // 旧实现只看第一个人物的版本：Bob 已漂移也不会被发现。
        val s = store()
        s.saveCharacterState(CharacterState(bob, projectId, version = 2))
        val result = UnitCommitter(s) { now }.commit(
            CommitRequest(
                projectId = projectId, unitId = unitId, revision = revision(),
                statePatch = patch(mapOf(alice to 3, bob to 1)),
                runId = runId, idempotencyKey = "k2",
            ),
        )
        assertIs<CommitRejection.StaleBaseVersion>((result as CommitResult.Rejected).rejection)
        assertEquals(null, s.characterState(projectId, alice)!!.emotion.ifEmpty { null }, "被拒绝时不该写入")
    }

    @Test
    fun `规格版本变化时拒绝旧候选`() {
        val s = store(specVersion = 2)
        val result = UnitCommitter(s) { now }.commit(
            CommitRequest(
                projectId = projectId, unitId = unitId, revision = revision(),
                statePatch = null, runId = runId, idempotencyKey = "k3",
                expectedSpecVersion = 1,
            ),
        )
        assertIs<CommitRejection.StaleInput>((result as CommitResult.Rejected).rejection)
    }

    @Test
    fun `规格版本一致时正常提交`() {
        val s = store(specVersion = 2)
        val result = UnitCommitter(s) { now }.commit(
            CommitRequest(
                projectId = projectId, unitId = unitId, revision = revision(),
                statePatch = null, runId = runId, idempotencyKey = "k4",
                expectedSpecVersion = 2,
            ),
        )
        assertIs<CommitResult.Committed>(result)
    }
}
