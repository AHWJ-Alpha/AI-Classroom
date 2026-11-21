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
import kotlin.test.assertNotEquals
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
                // 单元在总纲里声明了这个角色：状态补丁引用它才是合法的。
                requiredCharacters = listOf(charId), completionCriteria = listOf("见面"),
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
    fun `网络重试成功保留重试正文 不再被首次失败内容覆盖`() = runBlocking {
        val store = storeWithProject()
        val gateway = FakeGateway(failed(ModelCallError.Network("offline")), ok(validContent), ok(cleanReview), ok(validPatch))
        val outcome = executor(store, gateway).execute(projectId, unitId, runId)
        assertIs<UnitOutcome.Committed>(outcome)
        assertNotNull(store.officialRevision(unitId))
        assertEquals(4, gateway.prompts.size)
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
    fun `网络错误返回可继续的中断结果`() = runBlocking {
        // 网络断了不是任务出错：保留进度、稍后自动继续，不能变成「需要处理」。
        val store = storeWithProject()
        val gateway = FakeGateway(
            failed(ModelCallError.Network("断网")),
            failed(ModelCallError.Network("还是断网")),   // 就地重试那一次
        )

        val outcome = executor(store, gateway).execute(projectId, unitId, runId)

        assertIs<UnitOutcome.NetworkInterrupted>(outcome)
        assertNull(store.officialRevision(unitId), "没生成成功就不该有正式版本")
    }

    @Test
    fun `认证失败直接返回 Failed`() = runBlocking {
        // 认证错误重试也没用，不该消耗额外请求。
        val store = storeWithProject()
        val gateway = FakeGateway(failed(ModelCallError.Unauthorized(401)))

        val outcome = executor(store, gateway).execute(projectId, unitId, runId)

        assertIs<UnitOutcome.Failed>(outcome)
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
    fun `状态提取失败时进入待处理而不是当成没有变化`() = runBlocking {
        val store = storeWithProject()
        val gateway = FakeGateway(ok(validContent), ok(cleanReview), ok("我无法判断"))

        val outcome = executor(store, gateway).execute(projectId, unitId, runId)

        // 以前这条断言的是「正文提交了、状态没提出来也算成功」，
        // 正是要修掉的行为：两边长期不一致却显示为已完成。
        assertIs<UnitOutcome.NeedsAttention>(outcome)
        assertTrue(outcome.reason.contains("状态提取失败"))
        assertNull(store.officialRevision(unitId))
        assertNotEquals(UnitStatus.READY, store.unit(unitId)!!.status)
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

    @Test
    fun `正式版本保留审查报告与模型名`() = runBlocking {
        // 之前 revision.review 写死为 null：审完就丢，用户看不到判据。
        val store = storeWithProject()
        val gateway = FakeGateway(ok(validContent), ok(cleanReview), ok(validPatch))

        executor(store, gateway).execute(projectId, unitId, runId)

        val official = store.officialRevision(unitId)!!
        // 注意：assertNotNull 返回的是值本身，最后一句会让方法返回非 void，
        // JUnit 4 会直接忽略这种测试（这里踩过一次）。所以断言后显式收尾。
        val review = assertNotNull(official.review, "审查报告应随正式版本保存")
        assertEquals(false, review.hasBlocking)
        val modelName = assertNotNull(official.modelName)
        assertTrue(modelName.isNotBlank())
    }

    @Test
    fun `每次模型调用都留下尝试记录`() = runBlocking {
        val store = storeWithProject()
        val gateway = FakeGateway(ok(validContent), ok(cleanReview), ok(validPatch))

        executor(store, gateway).execute(projectId, unitId, runId)

        val attempts = store.attempts(runId)
        assertTrue(attempts.size >= 2, "生成与审查都应留痕，实际 " + attempts.size)
        assertTrue(attempts.any { it.phase == RunPhase.GENERATING })
        assertTrue(attempts.any { it.phase == RunPhase.REVIEWING })
    }

    @Test
    fun `本单元没有事件时属于合法的无状态变化`() = runBlocking {
        val store = storeWithProject()
        val noEvents = """{"unitId":"u1","title":"开场","planVersion":1,"blocks":[{"id":"b1","type":"narration","text":"雨夜。"}],"events":[]}""".trimIndent()
        // 即使没有事件也要问一次状态提取：模型可能漏写事件，静默跳过会丢变化。
        val emptyPatch = "{\"changes\":[],\"reason\":\"本单元没有状态变化\",\"evidenceEventIds\":[]}"
        val gateway = FakeGateway(ok(noEvents), ok(cleanReview), ok(emptyPatch))

        val outcome = executor(store, gateway).execute(projectId, unitId, runId)

        assertIs<UnitOutcome.Committed>(outcome)
        assertEquals(UnitStatus.READY, store.unit(unitId)!!.status)
    }

    @Test
    fun `部分变化未通过校验时整体停下`() = runBlocking {
        // 一条合法 + 一条非法：只提交合法那条会造成「正文已生效、状态只变一半」，
        // 比整体失败更难排查。
        val store = storeWithProject()
        val mixed = """{"changes":[" + 
            "{\"op\":\"SetEmotion\",\"characterId\":\"c1\",\"value\":\"紧张\"}," + 
            "{\"op\":\"SetEmotion\",\"characterId\":\"c404\",\"value\":\"x\"}]," + 
            "\"reason\":\"混合\",\"evidenceEventIds\":[\"e1\"]}""".trimIndent()
        val gateway = FakeGateway(ok(validContent), ok(cleanReview), ok(mixed))

        val outcome = executor(store, gateway).execute(projectId, unitId, runId)

        assertIs<UnitOutcome.NeedsAttention>(outcome)
        assertNull(store.officialRevision(unitId), "有被丢弃的变化就不该提交")
    }

    @Test
    fun `第 2 章请求里带第 1 章的摘要`() = runBlocking {
        // 跨章上下文必须真的进请求：只保存摘要不算接通。
        val store = storeWithProject()
        val rev1 = RevisionId("rev1")
        store.upsertUnit(
            store.unit(unitId)!!.copy(
                status = UnitStatus.READY, currentRevisionId = rev1, officialVersion = 1,
            ),
        )
        store.saveRevision(
            UnitRevision(
                id = rev1, unitId = unitId, projectId = projectId, version = 1,
                draftText = "雨夜，门外塞进一封信。",
                content = UnitContent(
                    schemaVersion = 1, unitId = unitId, planVersion = 1, title = "第一章",
                    objectivesCovered = emptyList(), unresolvedItems = listOf("谁是送信人"),
                    events = listOf(UnitEvent("e1", listOf("b1"), "收到信")),
                    blocks = listOf(ContentBlock.Narration("b1", "雨夜来信")),
                ),
                createdAt = now, committedAt = now, sourceHash = "h",
            ),
        )
        store.saveSummary(
            ChapterSummary(
                projectId = projectId, unitId = unitId, revisionId = rev1, index = 1,
                title = "第一章", keyEvents = listOf("主角收到一封信"),
                unresolvedItems = listOf("谁是送信人"), gist = "雨夜来信", createdAt = now,
            ),
        )

        val u2 = UnitId("u2")
        store.upsertUnit(
            ClassroomUnit(
                u2, projectId, 2, ProjectType.NOVEL, "第二章", "接着讲", listOf(unitId),
                emptyList(), listOf("完成"), 0, UnitStatus.PLANNED,
            ),
        )

        // 总纲里必须有第 2 章，否则执行器在发请求前就停了。
        val outline = store.latestOutline(projectId)!!
        store.saveOutline(
            outline.copy(
                version = outline.version + 1,
                units = outline.units + PlannedUnit(
                    unitId = u2, index = 2, title = "第二章", goal = "接着讲",
                    completionCriteria = listOf("完成"),
                ),
            ),
        )

        val emptyPatch = "{\"changes\":[],\"reason\":\"无变化\",\"evidenceEventIds\":[]}"
        val gateway = FakeGateway(ok(validContent), ok(cleanReview), ok(emptyPatch))
        executor(store, gateway).execute(projectId, u2, runId)

        val first = gateway.prompts.first()
        assertTrue(first.contains("谁是送信人"), "第 1 章的未解决事项应进入第 2 章请求")
        assertTrue(first.contains("第 1 章"), "摘要要带章号")
    }
}
