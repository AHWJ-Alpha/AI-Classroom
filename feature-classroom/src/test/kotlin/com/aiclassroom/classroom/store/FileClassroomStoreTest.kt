package com.aiclassroom.classroom.store

import com.aiclassroom.classroom.domain.*
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileClassroomStoreTest {

    private val now = 1_700_000_000_000L

    private fun tempDir(): File = Files.createTempDirectory("classroom-store").toFile()

    private fun sampleProject(id: String = "p1") = ClassroomProject(
        id = ProjectId(id), title = "雨夜课堂", type = ProjectType.NOVEL,
        lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
    )

    private fun sampleUnit(projectId: ProjectId = ProjectId("p1")) = ClassroomUnit(
        id = UnitId("u1"), projectId = projectId, index = 1, type = ProjectType.NOVEL,
        chapter = "第一章", goal = "开场", dependsOn = emptyList(),
        requiredCharacters = emptyList(), completionCriteria = listOf("主角登场"),
        inputStateVersion = 0, status = UnitStatus.PLANNED,
    )

    @Test
    fun `写入后重新打开仍能读到项目与单元`() {
        val dir = tempDir()
        val first = FileClassroomStore(dir)
        first.createProject(sampleProject())
        first.upsertUnit(sampleUnit())

        val reopened = FileClassroomStore(dir)
        assertEquals("雨夜课堂", reopened.project(ProjectId("p1"))!!.title)
        assertEquals(UnitStatus.PLANNED, reopened.unit(UnitId("u1"))!!.status)
    }

    @Test
    fun `事务内成功提交会一起落盘`() {
        val dir = tempDir()
        val store = FileClassroomStore(dir)
        store.transaction {
            store.createProject(sampleProject())
            store.upsertUnit(sampleUnit())
        }
        val reopened = FileClassroomStore(dir)
        assertNotNull(reopened.project(ProjectId("p1")))
        assertNotNull(reopened.unit(UnitId("u1")))
    }

    @Test
    fun `事务内抛异常时内存回滚且磁盘不落半截数据`() {
        val dir = tempDir()
        val store = FileClassroomStore(dir)
        runCatching {
            store.transaction {
                store.createProject(sampleProject())
                throw IllegalStateException("模拟崩溃")
            }
        }
        assertEquals(1, store.rollbackCount)
        assertNull(store.project(ProjectId("p1")))

        val reopened = FileClassroomStore(dir)
        assertNull(reopened.project(ProjectId("p1")))
    }

    @Test
    fun `落盘后不留下临时文件`() {
        val dir = tempDir()
        val store = FileClassroomStore(dir)
        store.createProject(sampleProject())
        val leftovers = dir.listFiles()?.filter { it.name.endsWith(".tmp") }.orEmpty()
        assertTrue(leftovers.isEmpty(), "不应残留 tmp")
    }

    @Test
    fun `只有临时文件时启动会把它当正式数据恢复`() {
        val dir = tempDir()
        val store = FileClassroomStore(dir)
        store.createProject(sampleProject())

        val main = File(dir, "classroom_store.json")
        val tmp = File(dir, "classroom_store.json.tmp")
        main.renameTo(tmp)
        assertTrue(tmp.exists() && !main.exists())

        val reopened = FileClassroomStore(dir)
        assertEquals("雨夜课堂", reopened.project(ProjectId("p1"))!!.title)
        assertTrue(main.exists())
        assertTrue(!tmp.exists())
    }

    @Test
    fun `损坏的存档不会让应用打不开而是当空仓库`() {
        val dir = tempDir()
        File(dir, "classroom_store.json").writeText("{ 这不是 JSON")
        val store = FileClassroomStore(dir)
        assertTrue(store.projects().isEmpty())

        val writer = FileClassroomStore(dir)
        writer.createProject(sampleProject())
        assertEquals(1, FileClassroomStore(dir).projects().size)
    }

    @Test
    fun `项目的来源绑定要能往返`() {
        // 归属靠 sourceConversationId，显示名只是快照，不能当键。
        val dir = tempDir()
        val store = FileClassroomStore(dir)
        store.createProject(
            ClassroomProject(
                id = ProjectId("pb1"), title = "绑定测试", type = ProjectType.NOVEL,
                lifecycle = ProjectLifecycle.DRAFT, createdAt = now, updatedAt = now,
                sourceConversationId = "conv-a",
                sourceDisplayNameSnapshot = "数学课",
                binding = BindingStatus.BOUND,
            ),
        )
        store.createProject(
            ClassroomProject(
                id = ProjectId("pb2"), title = "旧项目", type = ProjectType.NOVEL,
                lifecycle = ProjectLifecycle.DRAFT, createdAt = now, updatedAt = now,
            ),
        )

        val reopened = FileClassroomStore(dir)
        val bound = reopened.project(ProjectId("pb1"))!!
        assertEquals("conv-a", bound.sourceConversationId)
        assertEquals("数学课", bound.sourceDisplayNameSnapshot)
        assertEquals(BindingStatus.BOUND, bound.binding)

        val legacy = reopened.project(ProjectId("pb2"))!!
        assertEquals(null, legacy.sourceConversationId)
        assertEquals(BindingStatus.UNBOUND, legacy.binding)
    }

    @Test
    fun `两个项目里同名角色互不覆盖`() {
        // 角色 ID 不是全局唯一的：不同项目里同名的「讲师」是两个人。
        val dir = tempDir()
        val store = FileClassroomStore(dir)
        val a = ProjectId("pa")
        val b = ProjectId("pb")
        val cid = CharacterId("c1")

        store.saveCharacter(
            CharacterProfile(id = cid, projectId = a, name = "讲师", identity = "数学",
                corePersonality = "克制", values = "", thinkingStyle = "", speechStyle = "",
                behaviorRules = emptyList()),
        )
        store.saveCharacter(
            CharacterProfile(id = cid, projectId = b, name = "讲师", identity = "语文",
                corePersonality = "热情", values = "", thinkingStyle = "", speechStyle = "",
                behaviorRules = emptyList()),
        )
        store.saveCharacterState(CharacterState(cid, a, version = 1, emotion = "紧张"))
        store.saveCharacterState(CharacterState(cid, b, version = 7, emotion = "放松"))

        val reopened = FileClassroomStore(dir)
        assertEquals("数学", reopened.characters(a).first().identity)
        assertEquals("语文", reopened.characters(b).first().identity)
        assertEquals(1, reopened.characterState(a, cid)!!.version)
        assertEquals(7, reopened.characterState(b, cid)!!.version)
        assertEquals("紧张", reopened.characterState(a, cid)!!.emotion)
        assertEquals("放松", reopened.characterState(b, cid)!!.emotion)
        assertEquals(1, reopened.characters(a).size)
        assertEquals(1, reopened.characters(b).size)
    }

    @Test
    fun `人物档案 事实 任务 尝试 也要落盘`() {
        // 之前 persist 漏了这四张表：正文和检查点还在，人物设定与诊断全丢。
        val dir = tempDir()
        val store = FileClassroomStore(dir)
        val pid = ProjectId("p1")
        store.saveCharacter(
            CharacterProfile(
                id = CharacterId("c1"),
                projectId = ProjectId("p1"), name = "讲师", identity = "数学老师",
                corePersonality = "克制", values = "诚实", thinkingStyle = "先问再答",
                speechStyle = "短句", behaviorRules = listOf("不替学生作答"),
                authorPrivate = "作者私密",
            ),
        )
        store.saveFact(
            FactRevision(
                factId = FactId("f1"), projectId = pid, version = 1,
                content = "主角怕水", kind = FactKind.FACT,
                sourceUnitId = null, sourceRevisionId = null,
                visibility = Visibility.RESTRICTED,
                knownBy = setOf(CharacterId("c1")), evidence = "第二章",
            ),
        )
        store.saveRun(
            GenerationRun(
                id = RunId("r1"), projectId = pid, specVersion = 1, outlineVersion = 1,
                phase = RunPhase.GENERATING, status = RunStatus.PAUSED,
                completedUnits = 3, totalUnits = 8, startedAt = now, updatedAt = now,
            ),
        )
        store.saveAttempt(
            GenerationAttempt(
                id = AttemptId("a1"), runId = RunId("r1"), unitId = null,
                phase = RunPhase.GENERATING, inputHash = "h", requestedModel = "m1",
                actualModel = "m2", finishReason = "length", transportCompleted = false,
                createdAt = now,
            ),
        )

        val reopened = FileClassroomStore(dir)
        assertEquals("数学老师", reopened.characters(pid).first().identity)
        assertEquals("作者私密", reopened.characters(pid).first().authorPrivate)
        assertEquals(1, reopened.facts(pid).size)
        assertEquals(RunStatus.PAUSED, reopened.run(RunId("r1"))!!.status)
        assertEquals("m2", reopened.attempts(RunId("r1")).first().actualModel)
        assertEquals("length", reopened.attempts(RunId("r1")).first().finishReason)
    }

    @Test
    fun `读者存档要真正落盘，重启后还在`() {
        // 实测事故：savePlaythrough 只改内存，persist 没写进去，选项一重启就没了。
        val dir = tempDir()
        val store = FileClassroomStore(dir)
        store.savePlaythrough(
            PlaythroughSave(
                id = SaveId("s1"), projectId = ProjectId("p1"), workVersion = 1,
                currentNodeId = "n2",
                variables = mapOf("courage" to GalValue.Int(2)),
                choiceHistory = listOf("o1"),
                seenEndingIds = listOf("e1"),
                updatedAt = now,
            ),
        )

        val reopened = FileClassroomStore(dir)
        val save = reopened.playthrough(SaveId("s1"))!!
        assertEquals("n2", save.currentNodeId)
        assertEquals(GalValue.Int(2), save.variables["courage"])
        assertEquals(listOf("o1"), save.choiceHistory)
        assertEquals(listOf("e1"), save.seenEndingIds)
    }
    @Test
    fun `检查点与角色状态可以往返`() {
        val dir = tempDir()
        val writer = FileClassroomStore(dir)
        writer.saveCharacterState(
            CharacterState(CharacterId("c1"), ProjectId("p1"), version = 3, emotion = "紧张", stress = 2),
        )
        writer.saveCheckpoint(
            Checkpoint(
                runId = RunId("r1"), unitId = UnitId("u1"), revisionId = RevisionId("rev1"),
                workVersion = 1, stateVersion = 4, outlineVersion = 1,
                committedAt = now, checksum = "abc", idempotencyKey = "k1",
            ),
        )

        val reopened = FileClassroomStore(dir)
        assertEquals("紧张", reopened.characterState(ProjectId("p1"), CharacterId("c1"))!!.emotion)
        assertEquals("k1", reopened.checkpoint(RunId("r1"))!!.idempotencyKey)
    }
}
