package com.aiclassroom.classroom.domain

import com.aiclassroom.classroom.commit.CommitRequest
import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.commit.UnitCommitter
import com.aiclassroom.classroom.repository.CommitResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 早期重写要用「当时」的状态，不能读全书最新状态。
 */
class StateTimelineTest {

    private val now = 1_700_000_000_000L
    private val projectId = ProjectId("p1")
    private val hero = CharacterId("hero")

    private fun store(): InMemoryStore {
        val s = InMemoryStore()
        s.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.NOVEL,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
            ),
        )
        return s
    }

    private fun unit(id: String, index: Int) = ClassroomUnit(
        UnitId(id), projectId, index, ProjectType.NOVEL, "第" + index + "章", "目标",
        emptyList(), listOf(hero), listOf("完成"), 0, UnitStatus.DRAFT_READY,
    )

    private fun revision(unitId: String, emotion: String) = UnitRevision(
        id = RevisionId("rev-" + unitId + "-" + emotion), unitId = UnitId(unitId),
        projectId = projectId, version = 0, draftText = "正文",
        content = UnitContent(
            schemaVersion = 1, unitId = UnitId(unitId), planVersion = 1, title = "章",
            objectivesCovered = emptyList(), unresolvedItems = emptyList(),
            events = listOf(UnitEvent("e1", listOf("b1"), "发生了什么")),
            blocks = listOf(ContentBlock.Narration("b1", "正文")),
        ),
        createdAt = now, sourceHash = "h",
    )

    private fun patch(unitId: String, base: Int) = StatePatch(
        id = "sp", projectId = projectId, unitId = UnitId(unitId), revisionId = RevisionId("r"),
        baseVersion = base, baseVersions = mapOf(hero to base),
        changes = listOf(StateChange.SetEmotion(hero, "紧张")),
        reason = "推进", evidenceEventIds = listOf("e1"), idempotencyKey = "sp-" + unitId + base,
    )

    private fun commit(s: InMemoryStore, unitId: String, key: String, patch: StatePatch?) =
        UnitCommitter(s) { now }.commit(
            CommitRequest(
                projectId = projectId, unitId = UnitId(unitId),
                revision = revision(unitId, key), statePatch = patch,
                runId = RunId("run"), idempotencyKey = key,
            ),
        )

    @Test
    fun `每章提交后留下状态快照`() {
        val s = store()
        s.upsertUnit(unit("u1", 1))
        commit(s, "u1", "k1", null)

        val snaps = s.stateSnapshots(projectId)
        assertEquals(1, snaps.size)
        assertEquals(1, snaps.first().unitIndex)
    }

    @Test
    fun `重写第 2 章取的是第 1 章之后的状态`() {
        val s = store()
        s.upsertUnit(unit("u1", 1))
        s.saveCharacterState(CharacterState(hero, projectId, version = 0, emotion = "平静"))
        commit(s, "u1", "k1", null)   // 快照：v0 平静

        s.upsertUnit(unit("u2", 2))
        s.saveCharacterState(CharacterState(hero, projectId, version = 1, emotion = "紧张"))
        commit(s, "u2", "k2", null)   // 快照：v1 紧张

        s.upsertUnit(unit("u3", 3))
        s.saveCharacterState(CharacterState(hero, projectId, version = 5, emotion = "崩溃"))

        val before = StateTimeline.statesBefore(s, projectId, 3)
        assertEquals("紧张", before[hero]!!.emotion, "第 3 章之前应看到第 2 章结束时的状态")
        assertTrue(StateTimeline.isRewrite(s, projectId, 1))
    }

    @Test
    fun `重写时按历史基础版本提交会被接受`() {
        val s = store()
        s.upsertUnit(unit("u1", 1))
        s.saveCharacterState(CharacterState(hero, projectId, version = 0))
        commit(s, "u1", "k1", null)

        // 之后角色状态已经推进到 v7
        s.saveCharacterState(CharacterState(hero, projectId, version = 7))
        s.upsertUnit(unit("u1", 1))

        // 重写第 1 章：补丁基于「当时」的 v0
        val result = commit(s, "u1", "k2", patch("u1", 0))
        assertIs<CommitResult.Committed>(result)
    }

    @Test
    fun `既不是当前也不是历史的基础版本仍被拒绝`() {
        val s = store()
        s.upsertUnit(unit("u1", 1))
        s.saveCharacterState(CharacterState(hero, projectId, version = 3))

        val result = commit(s, "u1", "k3", patch("u1", 99))
        assertIs<CommitResult.Rejected>(result)
    }
}
