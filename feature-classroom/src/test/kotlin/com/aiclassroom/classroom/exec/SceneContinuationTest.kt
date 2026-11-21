package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.content.TextCount
import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.gateway.*
import com.aiclassroom.classroom.repository.DraftStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 多段续写：分段落盘、中断可续、只追加不重写。 */
class SceneContinuationTest {

    private var now = 1_000L
    private val projectId = ProjectId("p1")
    private val unitId = UnitId("u1")
    private val runId = RunId("r1")
    private val owner = "worker-a"

    private class FakeGateway(vararg bodies: Any) : ModelGateway {
        private val queue = ArrayDeque(bodies.toList())
        val prompts = mutableListOf<String>()

        override suspend fun complete(request: ModelRequest): ModelCallResult {
            prompts += request.messages.joinToString("\n") { it.content }
            return when (val next = queue.removeFirstOrNull()) {
                null -> error("脚本用完了")
                is ModelCallError -> ModelCallResult(
                    content = "", actualModel = null, finishReason = null,
                    transportCompleted = false, structuredPayload = null, usage = null,
                    error = next, diagnosticId = null,
                )
                else -> ModelCallResult(
                    content = next as String, actualModel = "m", finishReason = FinishReason.Stop,
                    transportCompleted = true, structuredPayload = null, usage = null,
                    error = null, diagnosticId = null,
                )
            }
        }
    }

    private fun segmentJson(text: String): String =
        "{\"blocks\":[" +
            "{\"id\":\"b1\",\"type\":\"narration\",\"text\":\"" + text + "\"}," +
            "{\"id\":\"b2\",\"type\":\"dialogue\",\"speakerId\":\"pc-a\",\"text\":\"她说了一句话\"}]," +
            "\"events\":[{\"id\":\"e1\",\"sourceBlockIds\":[\"b1\"],\"description\":\"发生了一件事\"}]," +
            "\"unresolvedItems\":[],\"finished\":false}"

    private fun setup(): Pair<InMemoryStore, DraftStore> {
        val s = InMemoryStore()
        s.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.SCENE,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
            ),
        )
        s.upsertUnit(
            ClassroomUnit(
                unitId, projectId, 1, ProjectType.SCENE, "第 1-3 天", "建立借住边界",
                emptyList(), emptyList(), listOf("完成"), 0, UnitStatus.PLANNED,
            ),
        )
        s.saveRun(
            GenerationRun(
                id = runId, projectId = projectId, specVersion = 1, outlineVersion = 1,
                phase = RunPhase.GENERATING, status = RunStatus.QUEUED,
                totalUnits = 1, startedAt = now, updatedAt = now,
            ),
        )
        RunLease.acquireForProject(s, projectId, runId, owner, 60_000, { now })
        return s to DraftStore(s) { now }
    }

    private fun request(plan: LengthPlan.Ok, store: InMemoryStore) = SceneContinuation.Request(
        projectId = projectId,
        unit = store.unit(unitId)!!,
        spec = ProjectSpec(
            projectId = projectId, version = 1,
            rawRequest = "接下来在你家过一个月", topic = "同居", goals = listOf("建立边界"),
            audience = "自己", language = "zh", style = "细腻", expectedLength = "", unitCount = 1,
            runMode = RunMode.CONFIRM_OUTLINE, reviewPolicy = ReviewPolicy.STANDARD,
            hardConstraints = emptyList(), authorRules = emptyList(), publicWorld = "", createdAt = now,
        ),
        outlineSummary = "一个月同居",
        timeRange = "第 1-3 天",
        characters = emptyList(),
        plan = plan,
        inputVersion = "v1",
        candidateId = "c1",
        expectedCandidateVersion = 0,
        runId = runId,
        owner = owner,
        fencingToken = store.run(runId)!!.fencingToken,
        policy = ModelPolicyResolver.resolve(
            provider = "p", baseUrl = "https://example.invalid", orderedModels = listOf("m"), temperature = 0.3,
        ),
    )

    @Test
    fun `多段生成写够目标后组装成完整单元`() = runBlocking {
        val (store, drafts) = setup()
        val plan = assertIs<LengthPlan.Ok>(LengthPlan.parse("每节 60 字", ProjectType.SCENE))
        val gateway = FakeGateway(
            segmentJson("她推门进来，把伞靠在门边，水顺着伞骨一路淌到鞋柜下面"),
            segmentJson("第二天早上，咖啡机比人先醒，她披着外套出来看了一眼水池"),
            segmentJson("第三天他们没有多说话，只是把碗一起洗了"),
        )
        val cont = SceneContinuation(gateway, store, drafts) { now }

        val result = assertIs<SceneContinuation.Result.Assembled>(cont.run(request(plan, store)))

        assertTrue(result.segments >= 2, "至少两段才够下限")
        assertTrue(result.chars >= 54, "60 字的九成")
        assertEquals(result.segments * 2, result.content.blocks.size, "每段两个块")
        val ids = result.content.blocks.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "块 id 必须唯一")
        val blockIds = ids.toSet()
        for (event in result.content.events) {
            assertTrue(event.sourceBlockIds.all { it in blockIds }, "事件引用必须指向真实块")
        }
        assertEquals(TextCount.units(result.content.blocks), result.chars)
    }

    @Test
    fun `第二段网络中断时保留第一段 换执行者只补后面的段`() = runBlocking {
        val (store, drafts) = setup()
        val plan = assertIs<LengthPlan.Ok>(LengthPlan.parse("每节 60 字", ProjectType.SCENE))

        val first = FakeGateway(
            segmentJson("她推门进来，把伞靠在门边，水顺着伞骨一路淌到鞋柜下面"),
            ModelCallError.Network("断网"),
        )
        val paused = assertIs<SceneContinuation.Result.Paused>(
            SceneContinuation(first, store, drafts) { now }.run(request(plan, store)),
        )
        assertEquals(1, paused.segments, "第一段必须已经落盘")

        val savedFirst = drafts.current(projectId, unitId)!!.segments.first()

        val second = FakeGateway(segmentJson("第二天早上，咖啡机比人先醒，她披着外套出来看了一眼水池"))
        val done = assertIs<SceneContinuation.Result.Assembled>(
            SceneContinuation(second, store, drafts) { now }.run(request(plan, store)),
        )

        val savedAfter = drafts.current(projectId, unitId)!!.segments
        assertEquals(savedFirst.checksum, savedAfter.first().checksum, "已保存的段落不能被重写")
        assertTrue(done.segments >= 2)
    }
}
