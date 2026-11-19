package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.commit.UnitCommitter
import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.gateway.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 端到端：快照入库 → 执行器 → ModelRequest → **最终 HTTP body**。
 *
 * 这条测的是一次真实链路，而不是某个函数返回值：
 * 目标是证明「4000 字之后的资料内容」和「跨章摘要」真的出现在请求体里。
 */
class EndToEndRequestTest {

    private val now = 1_700_000_000_000L
    private val projectId = ProjectId("p1")
    private val unitId = UnitId("u1")
    private val runId = RunId("r1")

    private class CapturingTransport(
        private val replies: MutableList<suspend (HttpRequest, (String) -> Unit) -> HttpExchange>,
    ) : HttpTransport {
        val bodies = mutableListOf<String>()
        override suspend fun post(request: HttpRequest, onLine: (String) -> Unit): HttpExchange {
            bodies += request.body
            val step = replies.removeFirstOrNull() ?: error("脚本用完了")
            return step(request, onLine)
        }
    }

    private class NoopSleeper : Sleeper {
        override suspend fun sleep(ms: Long) = Unit
    }

    private fun ok(text: String): suspend (HttpRequest, (String) -> Unit) -> HttpExchange = { _, onLine ->
        // 内容里有换行，SSE 用 \n 转义送回来。
        val escaped = text.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"")
        onLine("data: {\"model\":\"m\",\"choices\":[{\"delta\":{\"content\":\"$escaped\"},\"finish_reason\":\"stop\"}]}")
        onLine("data: [DONE]")
        HttpExchange(status = 200, completed = true, actualModel = "m")
    }

    @Test
    fun `长资料末尾与跨章摘要都进入最终请求体`() = runBlocking {
        val store = InMemoryStore()
        store.createProject(
            ClassroomProject(
                id = projectId, title = "Python 面向对象", type = ProjectType.TEACHING,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
            ),
        )
        store.saveSpec(
            ProjectSpec(
                projectId = projectId, version = 1, rawRequest = "讲多态", topic = "Python 面向对象",
                goals = listOf("讲透多态"), audience = "学生", language = "zh", style = "平实",
                expectedLength = "2 节", unitCount = 2, runMode = RunMode.CONFIRM_OUTLINE,
                reviewPolicy = ReviewPolicy.STANDARD, hardConstraints = emptyList(),
                authorRules = emptyList(), publicWorld = "", createdAt = now,
            ),
        )
        store.saveOutline(
            ProjectOutline(
                projectId = projectId, version = 1, globalGoal = "讲完", mainLine = "主线",
                chapters = listOf("基础", "多态"),
                units = listOf(
                    PlannedUnit(UnitId("u0"), 1, "基础", "讲对象", completionCriteria = listOf("完成")),
                    PlannedUnit(unitId, 2, "多态", "讲多态", completionCriteria = listOf("完成")),
                ),
                createdAt = now,
            ),
        )
        // 第 1 章已完成，留下摘要
        store.upsertUnit(
            ClassroomUnit(
                UnitId("u0"), projectId, 1, ProjectType.TEACHING, "基础", "讲对象",
                emptyList(), emptyList(), listOf("完成"), 0, UnitStatus.READY,
                currentRevisionId = RevisionId("rev0"), officialVersion = 1,
            ),
        )
        store.saveRevision(
            UnitRevision(
                id = RevisionId("rev0"), unitId = UnitId("u0"), projectId = projectId, version = 1,
                draftText = "第一章讲对象", content = null, createdAt = now, committedAt = now, sourceHash = "h",
            ),
        )
        store.saveSummary(
            ChapterSummary(
                projectId = projectId, unitId = UnitId("u0"), revisionId = RevisionId("rev0"),
                index = 1, title = "基础",
                keyEvents = listOf("讲清了对象是数据的集合"),
                unresolvedItems = listOf("还没讲继承"), gist = "对象基础", createdAt = now,
            ),
        )
        // 超长资料：关键内容在 4000 字之后
        val padding = (1..120).joinToString("\n\n") { "第" + it + "段：与多态无关的铺垫内容。" }
        val tail = "最后一段：多态让子类对象可以替换父类对象，这是本节的核心。"
        store.saveSnapshot(
            ContextSnapshot(
                id = SnapshotId("s1"), projectId = projectId, version = 1, createdAt = now,
                viewMode = "author",
                materials = listOf(SnapshotMaterial(name = "讲义.md", content = padding + "\n\n" + tail)),
            ),
        )
        store.upsertUnit(
            ClassroomUnit(
                unitId, projectId, 2, ProjectType.TEACHING, "多态", "讲多态",
                emptyList(), emptyList(), listOf("完成"), 0, UnitStatus.PLANNED,
            ),
        )

        val content = "{\"unitId\":\"u1\",\"title\":\"多态\",\"planVersion\":1," +
            "\"blocks\":[{\"id\":\"b1\",\"type\":\"narration\",\"text\":\"多态讲完了。\"}]," +
            "\"events\":[{\"id\":\"e1\",\"sourceBlockIds\":[\"b1\"],\"description\":\"讲多态\"}]}"
        val transport = CapturingTransport(
            mutableListOf(ok(content), ok("{\"issues\":[]}"), ok("{\"changes\":[],\"reason\":\"\",\"evidenceEventIds\":[]}")),
        )
        val gateway = HttpModelGateway(
            transport = transport,
            sleeper = NoopSleeper(),
            endpointOf = { "https://example.invalid/chat" },
            headersOf = { mapOf("Authorization" to "Bearer fake") },
            bodyBuilder = { req -> ChatRequestJson.build(req) },
            maxAttemptsPerModel = 1,
        )
        val executor = UnitExecutor(
            gateway, store, UnitCommitter(store) { now }, { now },
            policy = ModelPolicyResolver.resolve(
                provider = "DeepSeek", baseUrl = "https://example.invalid/v1",
                orderedModels = listOf("m"), temperature = 0.3,
            ),
        )

        executor.execute(projectId, unitId, runId)

        val firstBody = transport.bodies.first()
        assertTrue(firstBody.contains("多态让子类对象可以替换父类对象"), "4000 字之后的资料必须进入请求体")
        assertTrue(firstBody.contains("还没讲继承"), "第 1 章的未解决事项必须进入请求体")
        assertTrue(firstBody.contains("0.3"), "策略参数必须进入请求体")
        assertTrue(!firstBody.contains("fake"), "密钥不能出现在请求体")
    }
}
