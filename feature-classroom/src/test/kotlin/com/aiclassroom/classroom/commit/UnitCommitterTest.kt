package com.aiclassroom.classroom.commit

import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.repository.CommitRejection
import com.aiclassroom.classroom.repository.CommitResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnitCommitterTest {

    private val now = 1_700_000_000_000L
    private val projectId = ProjectId("p1")
    private val unitId = UnitId("u1")
    private val runId = RunId("r1")
    private val charId = CharacterId("c1")

    private fun storeWithUnit(status: UnitStatus = UnitStatus.DRAFT_READY): InMemoryStore {
        val store = InMemoryStore()
        store.createProject(
            ClassroomProject(
                id = projectId, title = "测试项目", type = ProjectType.NOVEL,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now, workVersion = 0,
            ),
        )
        store.upsertUnit(
            ClassroomUnit(
                id = unitId, projectId = projectId, index = 1, type = ProjectType.NOVEL,
                chapter = "第一章", goal = "开场", dependsOn = emptyList(),
                requiredCharacters = listOf(charId), completionCriteria = listOf("主角登场"),
                inputStateVersion = 0, status = status,
            ),
        )
        return store
    }

    private fun revision(
        id: String = "rev1",
        review: ReviewReport? = null,
    ) = UnitRevision(
        id = RevisionId(id), unitId = unitId, projectId = projectId, version = 0,
        draftText = "正文",
        content = UnitContent(
            schemaVersion = 1, unitId = unitId, planVersion = 1, title = "第一章",
            objectivesCovered = emptyList(), unresolvedItems = emptyList(),
            events = listOf(UnitEvent(id = "e1", sourceBlockIds = listOf("b1"), description = "开场")),
            blocks = listOf(ContentBlock.Narration("b1", "正文")),
        ),
        review = review, createdAt = now, sourceHash = "h-" + id,
    )

    private fun request(
        rev: UnitRevision = revision(),
        patch: StatePatch? = null,
        key: String = "k1",
    ) = CommitRequest(projectId, unitId, rev, patch, runId, key)

    @Test
    fun `提交成功后正文-检查点-单元状态-项目版本一起生效`() {
        val store = storeWithUnit()
        val committer = UnitCommitter(store) { now }

        val result = committer.commit(request())

        val committed = assertIs<CommitResult.Committed>(result)
        assertEquals(1, committed.revision.version)
        assertNotNull(committed.revision.committedAt)

        assertNotNull(store.officialRevision(unitId))
        assertEquals(UnitStatus.READY, store.unit(unitId)!!.status)
        assertEquals(1, store.unit(unitId)!!.officialVersion)
        assertEquals(1, store.project(projectId)!!.workVersion)
        assertNotNull(store.checkpoint(runId))
    }

    @Test
    fun `同一幂等键重复提交不会再造新版本`() {
        val store = storeWithUnit()
        val committer = UnitCommitter(store) { now }

        val first = assertIs<CommitResult.Committed>(committer.commit(request()))
        val second = assertIs<CommitResult.Committed>(committer.commit(request()))

        assertEquals(first.checkpoint.revisionId, second.checkpoint.revisionId)
        assertEquals(1, store.project(projectId)!!.workVersion)
        assertEquals(1, store.revisions(unitId).size)
    }

    @Test
    fun `审查已移除 旧的审查报告不再拦截提交`() {
        // 审查调用真机上返回空内容，把写够的正文卡在「审查未通过」的死循环里。
        // 现在提交器完全不看 review 字段：即使版本里带着 blocking 报告也照常提交。
        val store = storeWithUnit()
        val committer = UnitCommitter(store) { now }
        val review = ReviewReport(
            issues = listOf(
                ReviewIssue("i1", ReviewIssue.Severity.ERROR, RevisionId("rev1"), null, "因果", "证据", null, true),
            ),
            reviewedAt = now,
        )

        val result = committer.commit(request(revision(review = review)))

        assertTrue(result is CommitResult.Committed, "带 blocking 报告也必须能提交，实际：" + result)
        assertNotNull(store.officialRevision(unitId), "提交成功就该有正式版本")
        // 提交成功意味着工作版本 +1，不再是原来的 0。
        assertEquals(1, store.project(projectId)!!.workVersion)
    }

    @Test
    fun `非阻断问题允许提交`() {
        val store = storeWithUnit()
        val committer = UnitCommitter(store) { now }
        val review = ReviewReport(
            issues = listOf(
                ReviewIssue("i1", ReviewIssue.Severity.INFO, RevisionId("rev1"), null, "风格", "提示", null, false),
            ),
            reviewedAt = now,
        )

        assertIs<CommitResult.Committed>(committer.commit(request(revision(review = review))))
    }

    @Test
    fun `状态补丁的基础版本过期时拒绝提交`() {
        val store = storeWithUnit()
        store.saveCharacterState(CharacterState(characterId = charId, projectId = projectId, version = 5))
        val committer = UnitCommitter(store) { now }
        val patch = StatePatch(
            id = "patch1", projectId = projectId, unitId = unitId, revisionId = RevisionId("rev1"),
            baseVersion = 3,
            changes = listOf(StateChange.SetEmotion(charId, "紧张")),
            reason = "事件推进", evidenceEventIds = listOf("e1"), idempotencyKey = "p-k1",
        )

        val result = committer.commit(request(patch = patch))

        val rejected = assertIs<CommitResult.Rejected>(result)
        val stale = assertIs<CommitRejection.StaleBaseVersion>(rejected.rejection)
        assertEquals(3, stale.expected)
        assertEquals(5, stale.actual)
        assertNull(store.checkpoint(runId))
        assertEquals(5, store.characterState(projectId, charId)!!.version)
    }

    @Test
    fun `状态补丁版本匹配时提交并推进角色状态版本`() {
        val store = storeWithUnit()
        store.saveCharacterState(CharacterState(characterId = charId, projectId = projectId, version = 2))
        val committer = UnitCommitter(store) { now }
        val patch = StatePatch(
            id = "patch1", projectId = projectId, unitId = unitId, revisionId = RevisionId("rev1"),
            baseVersion = 2,
            changes = listOf(
                StateChange.SetEmotion(charId, "紧张"),
                StateChange.AdjustStress(charId, 3),
                StateChange.LearnFact(charId, FactId("f1")),
            ),
            reason = "事件推进", evidenceEventIds = listOf("e1"), idempotencyKey = "p-k1",
        )

        assertIs<CommitResult.Committed>(committer.commit(request(patch = patch)))

        val state = store.characterState(projectId, charId)!!
        assertEquals(3, state.version)
        assertEquals("紧张", state.emotion)
        assertEquals(3, state.stress)
        assertTrue(state.knownFactIds.contains(FactId("f1")))
    }

    @Test
    fun `已经 READY 的单元再次提交被拒绝`() {
        val store = storeWithUnit(status = UnitStatus.READY)
        val committer = UnitCommitter(store) { now }

        val result = committer.commit(request(key = "k2"))

        val rejected = assertIs<CommitResult.Rejected>(result)
        assertIs<CommitRejection.WrongStatus>(rejected.rejection)
    }

    @Test
    fun `提交前一个单元把旧版本标记为被替代而不是删除`() {
        val store = storeWithUnit()
        var clock = now
        val committer = UnitCommitter(store) { clock }

        committer.commit(request(revision("rev1"), key = "k1"))
        clock += 1000
        store.upsertUnit(store.unit(unitId)!!.copy(status = UnitStatus.DRAFT_READY))
        committer.commit(request(revision("rev2"), key = "k2"))

        val all = store.revisions(unitId)
        assertEquals(2, all.size)
        assertEquals(RevisionId("rev2"), all.first { it.id == RevisionId("rev1") }.supersededBy)
        assertEquals(2, store.officialRevision(unitId)!!.version)
    }

    @Test
    fun `事务中抛异常时全部回滚且留下回滚记录`() {
        val store = storeWithUnit()
        val thrown = runCatching {
            store.transaction {
                store.updateProject(store.project(projectId)!!.copy(title = "临时改动"))
                throw IllegalStateException("模拟崩溃")
            }
        }.exceptionOrNull()
        assertIs<IllegalStateException>(thrown)
        assertEquals("测试项目", store.project(projectId)!!.title)
        assertEquals(1, store.rollbackCount)
    }

    @Test
    fun `补丁引用不存在的事件时拒绝提交`() {
        // 没有证据的状态变化不能生效：否则事后无法核对这条状态从哪来。
        val store = storeWithUnit()
        val patch = StatePatch(
            id = "p1", projectId = projectId, unitId = unitId, revisionId = RevisionId("r1"),
            baseVersion = 0, changes = listOf(StateChange.SetEmotion(CharacterId("c1"), "紧张")),
            reason = "事件推进", evidenceEventIds = listOf("e404"), idempotencyKey = "p1",
        )

        val result = UnitCommitter(store) { now }.commit(request(patch = patch))

        val rejected = assertIs<CommitResult.Rejected>(result)
        assertIs<CommitRejection.UnknownEvent>(rejected.rejection)
    }
}
