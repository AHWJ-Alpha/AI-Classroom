package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.gateway.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/** 来源对话的最近几轮必须进请求：否则课堂与对话里的剧情脱节。 */
class PriorTurnsTest {

    private val projectId = ProjectId("p1")
    private val unitId = UnitId("u1")
    private val runId = RunId("r1")

    private class CaptureGateway(private val body: String) : ModelGateway {
        val prompts = mutableListOf<String>()
        override suspend fun complete(request: ModelRequest): ModelCallResult {
            prompts += request.messages.joinToString("\n") { it.content }
            return ModelCallResult(
                content = body, actualModel = "m", finishReason = FinishReason.Stop,
                transportCompleted = true, structuredPayload = null, usage = null,
                error = null, diagnosticId = null,
            )
        }
    }

    private fun store(): InMemoryStore {
        val s = InMemoryStore()
        s.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.SCENE,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = 1L, updatedAt = 1L,
            ),
        )
        s.upsertUnit(
            ClassroomUnit(
                unitId, projectId, 1, ProjectType.SCENE, "第 1-3 天", "建立边界",
                emptyList(), emptyList(), listOf("完成"), 0, UnitStatus.PLANNED,
            ),
        )
        s.saveSpec(
            ProjectSpec(
                projectId = projectId, version = 1, rawRequest = "一起住一个月",
                topic = "同居", goals = listOf("建立边界"), audience = "自己",
                language = "zh", style = "细腻", expectedLength = "", unitCount = 1,
                runMode = RunMode.CONFIRM_OUTLINE, reviewPolicy = ReviewPolicy.STANDARD,
                hardConstraints = emptyList(), authorRules = emptyList(), publicWorld = "",
                createdAt = 1L,
            ),
        )
        s.saveRun(
            GenerationRun(
                id = runId, projectId = projectId, specVersion = 1, outlineVersion = 1,
                phase = RunPhase.GENERATING, status = RunStatus.QUEUED,
                totalUnits = 1, startedAt = 1L, updatedAt = 1L,
            ),
        )
        RunLease.acquireForProject(s, projectId, runId, "t", 60_000, { 1L })
        s.saveOutline(
            ProjectOutline(
                projectId = projectId, version = 1, globalGoal = "熟起来", mainLine = "同居",
                chapters = listOf("第 1-3 天"),
                units = listOf(
                    PlannedUnit(unitId, 1, "第 1-3 天", "建立边界", completionCriteria = listOf("完成")),
                ),
                createdAt = 1L,
            ),
        )
        return s
    }

    @Test
    fun `对话内容出现在生成请求里`() = runBlocking {
        val s = store()
        val gateway = CaptureGateway(
            "{\"blocks\":[{\"id\":\"b1\",\"type\":\"narration\",\"text\":\"我推门进来\"}],\"events\":[]}",
        )
        val executor = UnitExecutor(
            gateway = gateway, store = s,
            committer = com.aiclassroom.classroom.commit.UnitCommitter(s) { 1L },
            clock = { 1L },
            runOwner = "t",
            fencingToken = s.run(runId)!!.fencingToken,
            priorTurns = listOf("user" to "这周放假，不想出门", "assistant" to "那就别出门"),
        )

        val outcome = executor.execute(projectId, unitId, runId)
        println("outcome=" + outcome)

        assertTrue(gateway.prompts.isNotEmpty(), "应该至少发过一次请求，实际 outcome=" + outcome)
        val first = gateway.prompts.first()
        assertTrue(first.contains("[DATA:PRIOR_CONTEXT]"), "必须标明这是背景记忆")
        assertTrue(first.contains("这周放假，不想出门"), "对话内容要真的进请求")
    }
}
