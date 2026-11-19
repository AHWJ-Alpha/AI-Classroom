package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.commit.*
import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.gateway.*
import com.aiclassroom.classroom.repository.CommitRejection
import com.aiclassroom.classroom.repository.CommitResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 执行权被接管之后，旧执行者既不能继续干活，也不能写终态。
 */
class LeaseAuthorityTest {

    private var now = 1_000_000L
    private val projectId = ProjectId("p1")
    private val unitId = UnitId("u1")
    private val runId = RunId("r1")

    private class FakeGateway(vararg responses: ModelCallResult) : ModelGateway {
        private val queue = ArrayDeque(responses.toList())
        val prompts = mutableListOf<String>()
        override suspend fun complete(request: ModelRequest): ModelCallResult {
            prompts += request.messages.joinToString("\n") { it.content }
            return queue.removeFirstOrNull() ?: error("脚本用完了")
        }
    }

    private fun store(): InMemoryStore {
        val s = InMemoryStore()
        s.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.NOVEL,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
            ),
        )
        s.upsertUnit(
            ClassroomUnit(
                unitId, projectId, 1, ProjectType.NOVEL, "第一章", "开场",
                emptyList(), emptyList(), listOf("完成"), 0, UnitStatus.DRAFT_READY,
            ),
        )
        s.saveRun(
            GenerationRun(
                id = runId, projectId = projectId, specVersion = 1, outlineVersion = 1,
                phase = RunPhase.GENERATING, status = RunStatus.QUEUED,
                totalUnits = 1, startedAt = now, updatedAt = now,
            ),
        )
        return s
    }

    @Test
    fun `执行权被接管后旧执行者直接停下 不再烧模型`() = runBlocking {
        val s = store()
        val first = assertIs<RunLease.Acquired.Granted>(
            RunLease.acquire(s, runId, "A", 100, { now }),
        )
        now += 500
        RunLease.acquire(s, runId, "B", 100_000, { now })   // 换人

        val gateway = FakeGateway()   // 一个响应都没准备：一旦发请求就会抛错
        val executor = UnitExecutor(
            gateway, s, UnitCommitter(s) { now }, { now },
            runOwner = "A", fencingToken = first.lease.token,
        )

        val outcome = executor.execute(projectId, unitId, runId)

        assertIs<UnitOutcome.NeedsAttention>(outcome)
        assertTrue(outcome.reason.contains("执行权已被接管"))
        assertTrue(gateway.prompts.isEmpty(), "被接管后不该再发请求")
    }

    @Test
    fun `旧执行者不能写终态`() {
        val s = store()
        val first = assertIs<RunLease.Acquired.Granted>(
            RunLease.acquire(s, runId, "A", 100, { now }),
        )
        now += 500
        RunLease.acquire(s, runId, "B", 100_000, { now })

        val event = RunFinalizer.finish(
            store = s,
            project = s.project(projectId)!!,
            run = s.run(runId),
            runStatus = RunStatus.COMPLETED,
            projectLifecycle = ProjectLifecycle.READY,
            type = EventType.COMPLETED,
            summary = "旧执行者写的",
            clock = { now },
            owner = "A",
            fenceToken = first.lease.token,
        )

        assertNull(event, "旧执行者不该产生完成事件")
        assertTrue(
            s.run(runId)!!.status != RunStatus.COMPLETED,
            "旧执行者不该把任务的终态盖掉",
        )
    }

    @Test
    fun `早期重写能成功 但用户改过规格后同一请求会被拒`() {
        val s = store()
        s.saveSpec(
            ProjectSpec(
                projectId = projectId, version = 1, rawRequest = "写故事", topic = "校园",
                goals = listOf("讲完"), audience = "读者", language = "zh", style = "平实",
                expectedLength = "短", unitCount = 1, runMode = RunMode.CONFIRM_OUTLINE,
                reviewPolicy = ReviewPolicy.STANDARD, hardConstraints = emptyList(),
                authorRules = emptyList(), publicWorld = "", createdAt = now,
            ),
        )
        s.saveCharacterState(CharacterState(CharacterId("hero"), projectId, version = 5))

        fun revision(id: String) = UnitRevision(
            id = RevisionId(id), unitId = unitId, projectId = projectId,
            version = 0, draftText = "正文", content = null, createdAt = now, sourceHash = "h",
        )

        // 重写第 1 章：补丁基于「当时」的状态（v0），当前已经是 v5
        val rewritePatch = StatePatch(
            id = "sp", projectId = projectId, unitId = unitId, revisionId = RevisionId("rw"),
            baseVersion = 0, baseVersions = mapOf(CharacterId("hero") to 0),
            changes = listOf(StateChange.SetEmotion(CharacterId("hero"), "紧张")),
            reason = "推进", evidenceEventIds = emptyList(), idempotencyKey = "sp",
        )
        val ok = UnitCommitter(s) { now }.commit(
            CommitRequest(
                projectId = projectId, unitId = unitId, revision = revision("rw"),
                statePatch = null, runId = runId, idempotencyKey = "k-rewrite",
                expectedSpecVersion = 1,
            ),
        )
        assertIs<CommitResult.Committed>(ok)

        // 之后用户改了规格：同一个旧请求再提交必须被拒
        s.saveSpec(
            s.latestSpec(projectId)!!.copy(version = 2),
        )
        s.upsertUnit(s.unit(unitId)!!.copy(status = UnitStatus.DRAFT_READY))
        val stale = UnitCommitter(s) { now }.commit(
            CommitRequest(
                projectId = projectId, unitId = unitId, revision = revision("rw2"),
                statePatch = null, runId = runId, idempotencyKey = "k-stale",
                expectedSpecVersion = 1,
            ),
        )
        assertIs<CommitRejection.StaleInput>((stale as CommitResult.Rejected).rejection)
    }
}
