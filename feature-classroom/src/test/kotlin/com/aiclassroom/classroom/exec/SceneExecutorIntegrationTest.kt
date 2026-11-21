package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.commit.UnitCommitter
import com.aiclassroom.classroom.content.TextCount
import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.gateway.*
import com.aiclassroom.classroom.json.*
import com.aiclassroom.classroom.store.FileClassroomStore
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

/** 从产品 UnitExecutor 入口验证，而非直接调用续写器。真实文件重开、新 run 恢复。 */
class SceneExecutorIntegrationTest {
    private val project = ProjectId("scene")
    private val unit = UnitId("living")
    private val now = 1_800_000_000_000L
    private val policy = ModelPolicy("test", "https://example.invalid", "fake")
    private fun ok(body: String) = ModelCallResult(body, "fake", FinishReason.Stop, true, null, null, null, null)
    private val network = ModelCallResult("", null, null, false, null, null, ModelCallError.Network("offline"), null)
    private class Gateway(vararg val responses: ModelCallResult) : ModelGateway {
        var index = 0
        val prompts = mutableListOf<String>()
        var before: (() -> Unit)? = null
        override suspend fun complete(request: ModelRequest): ModelCallResult {
            before?.invoke()
            prompts += request.messages.joinToString("\n") { it.content }
            return responses.getOrNull(index++) ?: error("unexpected model request $index")
        }
    }
    private val first = "她把钥匙放在门边的木碗里进屋换鞋窗外的雨还在下厨房水壶刚刚烧开"
    private val second = "清晨她先醒来轻轻合上房门桌上多出一杯温水他们看了彼此一眼没有说话"
    private fun segment(text: String, finished: Boolean) = JsonWriter.write(Json.obj(
        "blocks" to Json.arr(listOf(Json.obj("id" to Json.of("b1"), "type" to Json.of("narration"), "text" to Json.of(text)))),
        "events" to Json.arr(emptyList()), "finished" to Json.of(finished), "summary" to Json.of(text),
    ))
    private fun seed(s: FileClassroomStore) {
        s.createProject(ClassroomProject(project, "借住", ProjectType.SCENE, ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now))
        s.saveSpec(ProjectSpec(project, 1, "每节 60 字，共同生活三天", "借住", listOf("相处"), "读者", "zh", "自然", "", 1,
            RunMode.CONFIRM_OUTLINE, ReviewPolicy.STANDARD, emptyList(), emptyList(), "", createdAt = now))
        s.saveOutline(ProjectOutline(project, 1, "熟悉边界", "彼此尊重", emptyList(),
            listOf(PlannedUnit(unit, 1, "第 1-3 天", "放好钥匙，形成一个小习惯")), createdAt = now))
        s.upsertUnit(ClassroomUnit(unit, project, 1, ProjectType.SCENE, "第 1-3 天", "相处", emptyList(), emptyList(), emptyList(), 0, UnitStatus.PLANNED))
    }
    private fun executor(s: FileClassroomStore, g: ModelGateway, id: String): Pair<UnitExecutor, RunId> {
        val run = RunId(id)
        s.saveRun(GenerationRun(run, project, 1, 1, RunPhase.GENERATING, RunStatus.QUEUED, startedAt = now, updatedAt = now))
        val lease = assertIs<RunLease.Acquired.Granted>(RunLease.acquireForProject(s, project, run, id, 60_000, { now }, force = true))
        return UnitExecutor(g, s, UnitCommitter(s) { now }, { now }, policy = policy, runOwner = id, fencingToken = lease.lease.token) to run
    }
    @Test fun `真实入口生成多段 全文审查 正式提交和版本可读`() = runBlocking {
        val root = Files.createTempDirectory("scene-executor").toFile()
        val s = FileClassroomStore(root); seed(s)
        val g = Gateway(ok(segment(first, false)), ok(segment(second, true)), ok("{\"issues\":[]}"),
            ok("{\"changes\":[],\"evidenceEventIds\":[],\"reason\":\"无持久变化\"}"))
        val (e, run) = executor(s, g, "a")
        assertIs<UnitOutcome.Committed>(e.execute(project, unit, run))
        assertEquals(3, g.index)
        assertContains(g.prompts[2], first)
        assertContains(g.prompts[2], second)
        val reopened = FileClassroomStore(root)
        val draft = assertNotNull(reopened.draft(project, unit))
        assertEquals(DraftPhase.COMMITTED, draft.phase)
        assertEquals(3, draft.callCount)
        assertEquals(now, draft.updatedAt)
        assertEquals("a", draft.owner)
        assertEquals(2, draft.segments.size)
        val official = assertNotNull(reopened.officialRevision(unit))
        assertEquals(draft.committedRevisionId, official.id)
        assertTrue(TextCount.units(official.content!!.blocks) >= 54)
        assertEquals(official.id, reopened.latestManifest(project)!!.revisionOf(unit))
        val unexpected = Gateway()
        val (again, secondRun) = executor(reopened, unexpected, "b")
        assertIs<UnitOutcome.AlreadyDone>(again.execute(project, unit, secondRun))
        assertEquals(0, unexpected.index)
    }
    @Test fun `文件重建换执行者只补第二段 已保存第一段不变`() = runBlocking {
        val root = Files.createTempDirectory("scene-resume").toFile()
        val s = FileClassroomStore(root); seed(s)
        val (e, run) = executor(s, Gateway(ok(segment(first, false)), network), "a")
        assertIs<UnitOutcome.NetworkInterrupted>(e.execute(project, unit, run))
        val draft = s.draft(project, unit)!!
        val checksum = draft.segments.single().checksum
        val reopened = FileClassroomStore(root)
        val g = Gateway(ok(segment(second, true)), ok("{\"issues\":[]}"), ok("{\"changes\":[],\"evidenceEventIds\":[]}"))
        val (continued, newRun) = executor(reopened, g, "b")
        assertIs<UnitOutcome.Committed>(continued.execute(project, unit, newRun))
        assertEquals(2, g.index)
        val done = reopened.draft(project, unit)!!
        assertEquals(draft.candidateId, done.candidateId)
        assertEquals(checksum, done.segments.first().checksum)
        assertTrue(done.fencingToken > draft.fencingToken)
        assertEquals(4, done.callCount)
        assertContains(g.prompts.first(), first)
    }
    @Test fun `响应返回前用户改目标 旧响应不能追加到草稿`() = runBlocking {
        val s = FileClassroomStore(Files.createTempDirectory("scene-stale").toFile()); seed(s)
        val g = Gateway(ok(segment(first, true)))
        g.before = { s.upsertUnit(s.unit(unit)!!.copy(directive = UnitDirective("新的方向"))) }
        val (e, run) = executor(s, g, "a")
        assertIs<UnitOutcome.NeedsAttention>(e.execute(project, unit, run))
        assertTrue(s.draft(project, unit)!!.segments.isEmpty())
        assertNull(s.officialRevision(unit))
    }

    @Test fun `四千字真实执行器路径 分三次写入并审查完整末尾`() = runBlocking {
        val s = FileClassroomStore(Files.createTempDirectory("scene-long").toFile()); seed(s)
        s.saveSpec(s.latestSpec(project)!!.copy(rawRequest = "每节 4000 字，共同生活三天"))
        val a = "甲".repeat(1500); val b = "乙".repeat(1500); val c = "丙".repeat(1000)
        val g = Gateway(ok(segment(a, false)), ok(segment(b, false)), ok(segment(c, true)),
            ok("{\"issues\":[]}"), ok("{\"changes\":[],\"evidenceEventIds\":[]}"))
        val (e, run) = executor(s, g, "a")
        assertIs<UnitOutcome.Committed>(e.execute(project, unit, run))
        assertEquals(4000, s.draft(project, unit)!!.totalChars)
        assertEquals(3, s.draft(project, unit)!!.segments.size)
        assertContains(g.prompts[3], c)
        assertContains(g.prompts[3], a)
        assertEquals(4000, TextCount.units(s.officialRevision(unit)!!.content!!.blocks))
    }

    @Test fun `被截断的完整JSON也不保存 只纠正当前段`() = runBlocking {
        val s = FileClassroomStore(Files.createTempDirectory("scene-length").toFile()); seed(s)
        val g = Gateway(ok(segment(first, false)), ok(segment("错误半段", true)).copy(finishReason = FinishReason.Length(null)),
            ok(segment(second, true)), ok("{\"issues\":[]}"), ok("{\"changes\":[],\"evidenceEventIds\":[]}"))
        val (e, run) = executor(s, g, "a")
        assertIs<UnitOutcome.Committed>(e.execute(project, unit, run))
        assertEquals(2, s.draft(project, unit)!!.segments.size)
        assertFalse(s.officialRevision(unit)!!.draftText.contains("错误半段"))
        assertContains(g.prompts[2], "输出上限")
    }

    @Test fun `定位问题段后局部修订 重新审查但不重写其他段`() = runBlocking {
        val s = FileClassroomStore(Files.createTempDirectory("scene-revise").toFile()); seed(s)
        val g = Gateway(ok(segment(first, false)), ok(segment(second, true)), ok("{\"issues\":[]}"),
            ok(segment(second + "她点头", true)), ok("{\"issues\":[]}"), ok("{\"changes\":[],\"evidenceEventIds\":[]}"))
        // 审查响应引用由程序分配的第二段块 id。
        val dynamic = object : ModelGateway {
            override suspend fun complete(request: ModelRequest): ModelCallResult {
                if (g.index == 2) {
                    val block = s.draft(project, unit)!!.segments[1].blocks.first().id
                    g.index++
                    return ok(JsonWriter.write(Json.obj("issues" to Json.arr(listOf(Json.obj(
                        "id" to Json.of("i1"), "blockId" to Json.of(block), "rule" to Json.of("语气"),
                        "evidence" to Json.of("稍硬"), "blocking" to Json.of(true), "severity" to Json.of("ERROR")))))))
                }
                return g.complete(request)
            }
        }
        val (e, run) = executor(s, dynamic, "a")
        assertIs<UnitOutcome.Committed>(e.execute(project, unit, run))
        val draft = s.draft(project, unit)!!
        assertEquals(first, draft.segments.first().text)
        assertEquals(1, draft.candidateVersion)
        assertContains(s.officialRevision(unit)!!.draftText, "她点头")
    }

    @Test fun `新执行者接管但未写下一段时旧执行者已不能续租和追加`() = runBlocking {
        val s = FileClassroomStore(Files.createTempDirectory("scene-fence").toFile()); seed(s)
        val (e, run) = executor(s, Gateway(ok(segment(first, false)), network), "a")
        assertIs<UnitOutcome.NetworkInterrupted>(e.execute(project, unit, run))
        val before = s.draft(project, unit)!!
        executor(s, Gateway(), "b")
        val drafts = com.aiclassroom.classroom.repository.DraftStore(s) { now }
        val old = com.aiclassroom.classroom.repository.DraftWriteGuard(project, unit, before.candidateId,
            before.inputVersion, before.candidateVersion, run, "a", before.fencingToken)
        assertIs<com.aiclassroom.classroom.repository.DraftOutcome.StaleExecutor>(drafts.append(old,
            before.segments.first().copy(index = 2), before.inputVersion, { it.chars }))
        assertFalse(RunLease.renew(s, run, "a", 60_000, { now }))
        assertEquals(1, s.draft(project, unit)!!.segments.size)
    }

    @Test fun `暂停且正式字段完整文件往返后仍继承同一候选`() = runBlocking {
        val root = Files.createTempDirectory("scene-real-roundtrip").toFile()
        val s = FileClassroomStore(root); seed(s)
        s.saveSpec(s.latestSpec(project)!!.copy(budget = Budget(40, 100000)))
        s.saveOutline(s.latestOutline(project)!!.copy(milestones = listOf("放下戒备"), arcs = mapOf(CharacterId("hero") to "熟悉"),
            foreshadows = listOf("灯"), ending = "亲近"))
        val (e, run) = executor(s, Gateway(ok(segment(first, false)), network), "a")
        assertIs<UnitOutcome.NetworkInterrupted>(e.execute(project, unit, run))
        val input = s.draft(project, unit)!!.inputVersion
        RunRecovery.markPaused(s, project) { now }
        RunLease.release(s, run, "a") { now }
        val reopened = FileClassroomStore(root)
        assertEquals(input, SceneInputVersion.compute(reopened, project, unit, policy))
        val g = Gateway(ok(segment(second, true)), ok("{\"issues\":[]}"), ok("{\"changes\":[],\"evidenceEventIds\":[]}"))
        val (continued, next) = executor(reopened, g, "b")
        assertIs<UnitOutcome.Committed>(continued.execute(project, unit, next))
        assertEquals(2, reopened.draft(project, unit)!!.segments.size)
        assertEquals(2, g.index)
    }

    @Test fun `一章完成后编排器自动进入第二章并保留两份正文`() = runBlocking {
        val s = FileClassroomStore(Files.createTempDirectory("scene-two-units").toFile()); seed(s)
        val secondUnit = UnitId("next")
        s.upsertUnit(s.unit(unit)!!.copy(id = secondUnit, index = 2, dependsOn = listOf(unit)))
        s.saveOutline(s.latestOutline(project)!!.copy(units = s.latestOutline(project)!!.units +
            PlannedUnit(secondUnit, 2, "第4-7天", "共同出门", dependsOn = listOf(unit))))
        val g = Gateway(ok(segment(first + second, true)), ok("{\"issues\":[]}"),
            ok(segment(second + first, true)), ok("{\"issues\":[]}"))
        val (e, run) = executor(s, g, "a")
        assertIs<RunOutcome.Completed>(RunEngine(s, e, object : RunControl { override fun shouldContinue() = true }, { now }, owner = "a",
            fenceToken = s.run(run)!!.fencingToken).run(project, run))
        assertNotNull(s.officialRevision(unit))
        assertNotNull(s.officialRevision(secondUnit))
        assertEquals(2, s.project(project)!!.workVersion)
        assertEquals(4, g.index, "无角色的场景只需正文与审查，不让模型编造状态")
    }

    @Test fun `审查撞上限且重试中断后只重做审查 已保存正文不重写`() = runBlocking {
        val root = Files.createTempDirectory("scene-review-resume").toFile()
        val s = FileClassroomStore(root); seed(s)
        val g = Gateway(ok(segment(first + second, true)),
            ok("{\"issues\":[").copy(finishReason = FinishReason.Length(null)), network)
        val (e, run) = executor(s, g, "first")
        assertIs<UnitOutcome.NetworkInterrupted>(e.execute(project, unit, run))
        val before = assertNotNull(s.draft(project, unit))
        val reopened = FileClassroomStore(root)
        val retry = Gateway(ok("{\"issues\":[]}"))
        val (continued, next) = executor(reopened, retry, "next")
        assertIs<UnitOutcome.Committed>(continued.execute(project, unit, next))
        assertEquals(1, retry.index)
        assertContains(retry.prompts.single(), "[REVIEW_CRITERIA]")
        assertEquals(before.candidateId, reopened.draft(project, unit)!!.candidateId)
        assertEquals(before.segments.single().checksum, reopened.draft(project, unit)!!.segments.single().checksum)
        assertEquals(2, reopened.attempts(run).count { it.phase == RunPhase.REVIEWING })
    }
}
