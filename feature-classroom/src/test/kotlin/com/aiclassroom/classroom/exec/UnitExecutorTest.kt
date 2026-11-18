package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.commit.UnitCommitter
import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.gateway.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnitExecutorTest {

    private val now = 1_700_000_000_000L
    private val projectId = ProjectId("p1")
    private val unitId = UnitId("u1")
    private val runId = RunId("r1")

    /** 按脚本回答的假网关，并记录收到的请求类型。 */
    private class FakeGateway(vararg responses: ModelCallResult) : ModelGateway {
        private val queue = ArrayDeque(responses.toList())
        val prompts = mutableListOf<String>()
        override suspend fun complete(request: ModelRequest): ModelCallResult {
            prompts += request.messages.joinToString("\n") { it.content }
            return queue.removeFirstOrNull() ?: error("脚本用完了")
        }
    }

    private fun ok(text: String) = ModelCallResult(
        content = text, actualModel = "m1", finishReason = FinishReason.Stop,
        transportCompleted = true, structuredPayload = null, usage = null,
        error = null, diagnosticId = null,
    )

    private fun failed(error: ModelCallError) = ModelCallResult(
        content = "", actualModel = null, finishReason = null,
        transportCompleted = false, structuredPayload = null, usage = null,
        error = error, diagnosticId = null,
    )

    private val validContent = """
        {"unitId":"u1","title":"开场","planVersion":1,
         "blocks":[{"id":"b1","type":"narration","text":"雨夜。"}],
         "events":[{"id":"e1","sourceBlockIds":["b1"],"description":"开场"}]}
    """.trimIndent()

    private val cleanReview = "{\"issues\":[]}"

    private val charId = CharacterId("c1")

    private val validPatch = """
        {"changes":[{"op":"SetEmotion","characterId":"c1","value":"紧张"}],
         "reason":"雨夜开场","evidenceEventIds":["e1"]}
    """.trimIndent()

    private val blockingReview =
        "{\"issues\":[{\"id\":\"i1\",\"severity\":\"ERROR\",\"rule\":\"因果\",\"evidence\":\"跳了\",\"blocking\":true}]}"

    private fun storeWithProject(): InMemoryStore {
        val store = InMemoryStore()
        store.createProject(
            ClassroomProject(
                id = projectId, title = "测试", type = ProjectType.NOVEL,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
            ),
        )
        store.saveSpec(
            ProjectSpec(
                projectId = projectId, version = 1, rawRequest = "写个故事", topic = "校园",
                goals = listOf("讲完第一章"), audience = "读者", language = "zh", style = "平实",
                expectedLength = "短", unitCount = 1, runMode = RunMode.CONFIRM_OUTLINE,
                reviewPolicy = ReviewPolicy.STANDARD, hardConstraints = emptyList(),
                authorRules = emptyList(), publicWorld = "", createdAt = now,
            ),
        )
        store.saveOutline(
            ProjectOutline(
                projectId = projectId, version = 1, globalGoal = "讲完", mainLine = "主线",
                chapters = listOf("第一章"), createdAt = now,
                units = listOf(
                    PlannedUnit(
                        unitId = unitId, index = 1, title = "开场", goal = "两人见面",
                        completionCriteria = listOf("见面"),
                    ),
                ),
            ),
        )
        store.upsertUnit(
            ClassroomUnit(
                id = unitId, projectId = projectId, index = 1, type = ProjectType.NOVEL,
                chapter = "第一章", goal = "两人见面", dependsOn = emptyList(),
                requiredCharacters = emptyList(), completionCriteria = listOf("见面"),
                inputStateVersion = 0, status = UnitStatus.PLANNED,
            ),
        )
        return store
    }

    private fun executor(store: InMemoryStore, gateway: ModelGateway) =
        UnitExecutor(gateway, store, UnitCommitter(store) { now }, { now })

    @Test
    fun `顺利生成并提交后单元变 READY 且有检查点`() = runBlocking {
        val store = storeWithProject()
        val gateway = FakeGateway(ok(validContent), ok(cleanReview), ok(validPatch))

        val outcome = executor(store, gateway).execute(projectId, unitId, runId)

        assertIs<UnitOutcome.Committed>(outcome)
        assertEquals(UnitStatus.READY, store.unit(unitId)!!.status)
        assertNotNull(store.checkpoint(runId))
        assertNotNull(store.officialRevision(unitId))
        // 状态补丁随正文一起提交
        assertEquals("紧张", store.characterState(projectId, charId)!!.emotion)
    }

    @Test
    fun `审查与生成是两次独立调用`() = runBlocking {
        val store = storeWithProject()
        val gateway = FakeGateway(ok(validContent), ok(cleanReview), ok(validPatch))
        executor(store, gateway).execute(projectId, unitId, runId)
        assertEquals(3, gateway.prompts.size)
    }

    @Test
    fun `审查一直阻断时进入待处理且不提交`() = runBlocking {
        val store = storeWithProject()
        val gateway = FakeGateway(
            ok(validContent), ok(blockingReview),
            ok(validContent), ok(blockingReview),
            ok(validContent), ok(blockingReview),
        )

        val outcome = executor(store, gateway).execute(projectId, unitId, runId)

        assertIs<UnitOutcome.NeedsAttention>(outcome)
        assertNull(store.officialRevision(unitId))
        assertNull(store.checkpoint(runId))
        assertEquals(UnitStatus.PLANNED, store.unit(unitId)!!.status)
    }

    @Test
    fun `正文结构不合法时重试后仍失败进入待处理`() = runBlocking {
        val store = storeWithProject()
        val bad = "{\"unitId\":\"u1\",\"blocks\":[{\"id\":\"b1\",\"type\":\"video\"}]}"
        val gateway = FakeGateway(ok(bad), ok(bad), ok(bad))

        val outcome = executor(store, gateway).execute(projectId, unitId, runId)

        assertIs<UnitOutcome.NeedsAttention>(outcome)
        assertNull(store.checkpoint(runId))
    }

    @Test
    fun `调用失败直接返回 Failed 且不写任何内容`() = runBlocking {
        val store = storeWithProject()
        val gateway = FakeGateway(failed(ModelCallError.Network("断网")))

        val outcome = executor(store, gateway).execute(projectId, unitId, runId)

        assertIs<UnitOutcome.Failed>(outcome)
        assertNull(store.officialRevision(unitId))
    }

    @Test
    fun `输出被 max_tokens 截断不能当作完成`() = runBlocking {
        val store = storeWithProject()
        val truncated = ok(validContent).copy(finishReason = FinishReason.Length(100))
        val gateway = FakeGateway(truncated)

        val outcome = executor(store, gateway).execute(projectId, unitId, runId)

        assertIs<UnitOutcome.Failed>(outcome)
        assertNull(store.officialRevision(unitId))
    }

    @Test
    fun `状态提取失败不影响正文提交`() = runBlocking {
        val store = storeWithProject()
        val gateway = FakeGateway(ok(validContent), ok(cleanReview), ok("我无法判断"))

        val outcome = executor(store, gateway).execute(projectId, unitId, runId)

        assertIs<UnitOutcome.Committed>(outcome)
        assertEquals(UnitStatus.READY, store.unit(unitId)!!.status)
        assertNull(store.characterState(projectId, charId))
    }

    @Test
    fun `已经 READY 的单元不会重复生成`() = runBlocking {
        val store = storeWithProject()
        store.upsertUnit(store.unit(unitId)!!.copy(status = UnitStatus.READY))
        val gateway = FakeGateway()

        val outcome = executor(store, gateway).execute(projectId, unitId, runId)

        assertIs<UnitOutcome.AlreadyDone>(outcome)
        assertEquals(0, gateway.prompts.size)
    }

    @Test
    fun `nextReadyUnit 只选依赖已就绪的最早单元`() {
        val u1 = ClassroomUnit(UnitId("a"), projectId, 1, ProjectType.NOVEL, "c", "g", emptyList(), emptyList(), emptyList(), 0, UnitStatus.PLANNED)
        val u2 = ClassroomUnit(UnitId("b"), projectId, 2, ProjectType.NOVEL, "c", "g", listOf(UnitId("a")), emptyList(), emptyList(), 0, UnitStatus.PLANNED)
        val u3 = ClassroomUnit(UnitId("c"), projectId, 3, ProjectType.NOVEL, "c", "g", emptyList(), emptyList(), emptyList(), 0, UnitStatus.PLANNED)

        assertEquals(UnitId("a"), UnitExecutor.nextReadyUnit(listOf(u1, u2, u3))!!.id)
        // a 完成后，第 3 个和第 2 个都满足依赖，取序号小的那个
        assertEquals(UnitId("b"), UnitExecutor.nextReadyUnit(listOf(u1.copy(status = UnitStatus.READY), u2, u3))!!.id)
    }
}
