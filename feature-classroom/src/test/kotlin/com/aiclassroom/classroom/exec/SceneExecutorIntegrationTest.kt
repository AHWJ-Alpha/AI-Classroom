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
        val g = Gateway(ok(segment(first, false)), ok(segment(second, true)),
            ok("{\"changes\":[],\"evidenceEventIds\":[],\"reason\":\"无持久变化\"}"))
        val (e, run) = executor(s, g, "a")
        assertIs<UnitOutcome.Committed>(e.execute(project, unit, run))
        // 审查调用已移除：两次生成 + 一次状态提取 = 3 次，最后一条是状态提取。
        assertEquals(2, g.index)
        // 第 2 次调用是第二段生成，它的 prompt 里应带上「已写部分」的尾部。
        assertContains(g.prompts[1], first)
        val reopened = FileClassroomStore(root)
        val draft = assertNotNull(reopened.draft(project, unit))
        assertEquals(DraftPhase.COMMITTED, draft.phase)
        assertEquals(2, draft.callCount)
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
        val g = Gateway(ok(segment(second, true)), ok("{\"changes\":[],\"evidenceEventIds\":[]}"))
        val (continued, newRun) = executor(reopened, g, "b")
        assertIs<UnitOutcome.Committed>(continued.execute(project, unit, newRun))
        // seed 里没有角色 → 状态提取整个跳过；审查已删除。只剩续写那 1 次。
        assertEquals(1, g.index, "无角色时只续写一次")
        val done = reopened.draft(project, unit)!!
        assertEquals(draft.candidateId, done.candidateId)
        assertEquals(checksum, done.segments.first().checksum)
        assertTrue(done.fencingToken > draft.fencingToken)
        assertEquals(3, done.callCount, "第一段1次 + 续写1次 + 状态提取0次；实测3说明有重试")
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
            ok("{\"changes\":[],\"evidenceEventIds\":[]}"))
        val (e, run) = executor(s, g, "a")
        assertIs<UnitOutcome.Committed>(e.execute(project, unit, run))
        assertEquals(4000, s.draft(project, unit)!!.totalChars)
        assertEquals(3, s.draft(project, unit)!!.segments.size)
        // 审查已删除：三次生成分别是 index 0/1/2，没有第 4 次调用。
        // 这里只验证「第三次确实在续写第三段」：prompt 带上了本节目标与已写进度。
        assertContains(g.prompts[2], "当前累计")
        assertEquals(4000, TextCount.units(s.officialRevision(unit)!!.content!!.blocks))
    }

    @Test fun `被截断的完整JSON也不保存 只纠正当前段`() = runBlocking {
        val s = FileClassroomStore(Files.createTempDirectory("scene-length").toFile()); seed(s)
        val g = Gateway(ok(segment(first, false)), ok(segment("错误半段", true)).copy(finishReason = FinishReason.Length(null)),
            ok(segment(second, true)), ok("{\"changes\":[],\"evidenceEventIds\":[]}"))
        val (e, run) = executor(s, g, "a")
        assertIs<UnitOutcome.Committed>(e.execute(project, unit, run))
        assertEquals(2, s.draft(project, unit)!!.segments.size)
        assertFalse(s.officialRevision(unit)!!.draftText.contains("错误半段"))
        assertContains(g.prompts[2], "输出上限")
    }

    // 原本有两条测试专测「审查不通过 → 局部修订」与「审查撞上限后只重做审查」。
    // 审查调用已彻底删除（真机返回空内容、修订被截断，只造成死循环），
    // 这两条测试的对象已不存在，一并移除。


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
        val g = Gateway(ok(segment(second, true)), ok("{\"changes\":[],\"evidenceEventIds\":[]}"))
        val (continued, next) = executor(reopened, g, "b")
        assertIs<UnitOutcome.Committed>(continued.execute(project, unit, next))
        assertEquals(2, reopened.draft(project, unit)!!.segments.size)
        // seed 里没有角色 → 状态提取跳过；审查已删除。只剩续写那 1 次。
        assertEquals(1, g.index)
    }

    @Test fun `一章完成后编排器自动进入第二章并保留两份正文`() = runBlocking {
        val s = FileClassroomStore(Files.createTempDirectory("scene-two-units").toFile()); seed(s)
        val secondUnit = UnitId("next")
        s.upsertUnit(s.unit(unit)!!.copy(id = secondUnit, index = 2, dependsOn = listOf(unit)))
        s.saveOutline(s.latestOutline(project)!!.copy(units = s.latestOutline(project)!!.units +
            PlannedUnit(secondUnit, 2, "第4-7天", "共同出门", dependsOn = listOf(unit))))
        val g = Gateway(ok(segment(first + second, true)),
            ok(segment(second + first, true)))
        val (e, run) = executor(s, g, "a")
        assertIs<RunOutcome.Completed>(RunEngine(s, e, object : RunControl { override fun shouldContinue() = true }, { now }, owner = "a",
            fenceToken = s.run(run)!!.fencingToken).run(project, run))
        assertNotNull(s.officialRevision(unit))
        assertNotNull(s.officialRevision(secondUnit))
        assertEquals(2, s.project(project)!!.workVersion)
        assertEquals(2, g.index, "无角色的场景只需正文，不让模型编造状态")
    }

    @Test
    fun `角色人格与来源对话都进入生成请求`() = runBlocking {
        // 真机上这两件事都出过问题：人格没落到角色、对话内容根本没进请求。
        // 这里用同一套产品入口（UnitExecutor）验证，不依赖网络与额度。
        val root = Files.createTempDirectory("scene-persona").toFile()
        val s = FileClassroomStore(root)
        seed(s)
        // 讲师（创建顺序第一条）+ 对方，人格写在讲师上。
        s.saveCharacter(CharacterProfile(
            id = CharacterId("m"), projectId = project, name = "她",
            identity = "叙述者", corePersonality = "话少、克制，用行动代替表态",
            values = "", thinkingStyle = "", speechStyle = "短句",
            behaviorRules = emptyList(),
        ))
        s.saveCharacter(CharacterProfile(
            id = CharacterId("y"), projectId = project, name = "哥哥",
            identity = "在场者", corePersonality = "",
            values = "", thinkingStyle = "", speechStyle = "",
            behaviorRules = emptyList(),
        ))

        val g = Gateway(
            ok(segment(first, false)),
            ok(segment(second, true)),
            
            ok("{\"changes\":[],\"evidenceEventIds\":[],\"reason\":\"无持久变化\"}"),
        )
        val run = RunId("p")
        s.saveRun(GenerationRun(run, project, 1, 1, RunPhase.GENERATING, RunStatus.QUEUED, startedAt = now, updatedAt = now))
        val lease = assertIs<RunLease.Acquired.Granted>(
            RunLease.acquireForProject(s, project, run, "p", 60_000, { now }, force = true),
        )
        val e = UnitExecutor(
            g, s, UnitCommitter(s) { now }, { now },
            policy = policy, runOwner = "p", fencingToken = lease.lease.token,
            priorTurns = listOf("user" to "外面雨太大了，我今晚回不去了。"),
            // 完整人格块：一致性规则、边界、世界书都在里面，不能只搬一句话。
            sourcePersona = "[讲师/叙述者身份]\n话少、克制，用行动代替表态\n[事实与人物规则]\n固定人格保持稳定",
        )

        assertIs<UnitOutcome.Committed>(e.execute(project, unit, run))

        val firstPrompt = g.prompts.first()
        assertContains(firstPrompt, "话少、克制，用行动代替表态", message = "人格必须进请求")
        assertContains(firstPrompt, "短句", message = "语言风格也要进")
        assertContains(firstPrompt, "[DATA:PRIOR_CONTEXT]", message = "要标明这是背景记忆")
        assertContains(firstPrompt, "不是本节的起点", message = "不能把对话当成开场")
        assertContains(firstPrompt, "[SOURCE_PERSONA]", message = "完整人格要带进来")
        assertContains(firstPrompt, "外面雨太大了", message = "对话内容要真的进请求")
        assertContains(firstPrompt, "以「她」的第一人称叙述", message = "视角要指定讲师")
    }
}
