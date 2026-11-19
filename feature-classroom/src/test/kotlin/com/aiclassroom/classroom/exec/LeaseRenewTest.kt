package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.commit.UnitCommitter
import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.gateway.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/** 长任务不续租的话，中途会被判过期，之后所有提交都会被拒。 */
class LeaseRenewTest {

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

    private fun ok(text: String) = ModelCallResult(
        content = text, actualModel = "m", finishReason = FinishReason.Stop,
        transportCompleted = true, structuredPayload = null, usage = null,
        error = null, diagnosticId = null,
    )

    @Test
    fun `执行一章会把租约续到未来`() = runBlocking {
        val store = InMemoryStore()
        store.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.NOVEL,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
            ),
        )
        store.saveSpec(
            ProjectSpec(
                projectId = projectId, version = 1, rawRequest = "写故事", topic = "校园",
                goals = listOf("讲完"), audience = "读者", language = "zh", style = "平实",
                expectedLength = "短", unitCount = 1, runMode = RunMode.CONFIRM_OUTLINE,
                reviewPolicy = ReviewPolicy.STANDARD, hardConstraints = emptyList(),
                authorRules = emptyList(), publicWorld = "", createdAt = now,
            ),
        )
        store.saveOutline(
            ProjectOutline(
                projectId = projectId, version = 1,
                globalGoal = "讲完", mainLine = "主线", chapters = listOf("第一章"),
                units = listOf(
                    PlannedUnit(unitId, 1, "第一章", "开场", completionCriteria = listOf("完成")),
                ),
                createdAt = now,
            ),
        )
        store.upsertUnit(
            ClassroomUnit(
                unitId, projectId, 1, ProjectType.NOVEL, "第一章", "开场",
                emptyList(), emptyList(), listOf("完成"), 0, UnitStatus.PLANNED,
            ),
        )
        store.saveRun(
            GenerationRun(
                id = runId, projectId = projectId, specVersion = 1, outlineVersion = 1,
                phase = RunPhase.GENERATING, status = RunStatus.QUEUED,
                totalUnits = 1, startedAt = now, updatedAt = now,
            ),
        )
        val granted = RunLease.acquire(store, runId, "A", 100) { now }
        val token = (granted as RunLease.Acquired.Granted).lease.token
        val before = store.run(runId)!!.leaseUntil

        val content = "{\"unitId\":\"u1\",\"title\":\"第一章\",\"planVersion\":1," +
            "\"blocks\":[{\"id\":\"b1\",\"type\":\"narration\",\"text\":\"雨夜。\"}]," +
            "\"events\":[{\"id\":\"e1\",\"sourceBlockIds\":[\"b1\"],\"description\":\"开场\"}]}"
        val gateway = FakeGateway(ok(content), ok("{\"issues\":[]}"), ok("{\"changes\":[],\"reason\":\"\",\"evidenceEventIds\":[]}"))
        val executor = UnitExecutor(
            gateway, store, UnitCommitter(store) { now }, { now },
            runOwner = "A", fencingToken = token,
        )

        executor.execute(projectId, unitId, runId)

        assertTrue(store.run(runId)!!.leaseUntil > before, "执行期间必须续租")
    }
}
