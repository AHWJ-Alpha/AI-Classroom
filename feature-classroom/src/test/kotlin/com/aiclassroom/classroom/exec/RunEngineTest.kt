package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.domain.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RunEngineTest {

    private val now = 1_700_000_000_000L
    private val projectId = ProjectId("p1")
    private val runId = RunId("r1")

    /** 按单元脚本回答；记录真正执行过哪些单元。 */
    private class FakeRunner(
        private val outcomes: Map<String, Queue<UnitOutcome>>,
    ) : UnitRunner {
        val executed = mutableListOf<String>()
        override suspend fun execute(projectId: ProjectId, unitId: UnitId, runId: RunId): UnitOutcome {
            executed += unitId.value
            val q = outcomes[unitId.value] ?: error("没有为 " + unitId.value + " 准备结果")
            return q.removeFirst()
        }
    }

    private class Queue<T>(private val items: MutableList<T>) {
        fun removeFirst(): T = if (items.isEmpty()) error("队列空了") else items.removeAt(0)
    }

    private fun queue(vararg o: UnitOutcome) = Queue(o.toMutableList())

    private fun committed(id: String) = UnitOutcome.Committed(
        RevisionId("rev-" + id),
        Checkpoint(
            runId = runId, unitId = UnitId(id), revisionId = RevisionId("rev-" + id),
            workVersion = 1, stateVersion = 0, outlineVersion = 1,
            committedAt = now, checksum = "c", idempotencyKey = "k-" + id,
        ),
    )

    private fun unit(
        id: String,
        index: Int,
        dependsOn: List<String> = emptyList(),
        status: UnitStatus = UnitStatus.PLANNED,
    ) = ClassroomUnit(
        id = UnitId(id), projectId = projectId, index = index, type = ProjectType.NOVEL,
        chapter = "c", goal = "g", dependsOn = dependsOn.map(::UnitId),
        requiredCharacters = emptyList(), completionCriteria = listOf("done"),
        inputStateVersion = 0, status = status,
    )

    private fun storeWith(vararg units: ClassroomUnit): InMemoryStore {
        val store = InMemoryStore()
        store.createProject(
            ClassroomProject(
                id = projectId, title = "测试", type = ProjectType.NOVEL,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
            ),
        )
        store.saveRun(
            GenerationRun(
                id = runId, projectId = projectId, specVersion = 1, outlineVersion = 1,
                phase = RunPhase.GENERATING, status = RunStatus.RUNNING,
                totalUnits = units.size, startedAt = now, updatedAt = now,
            ),
        )
        units.forEach { store.upsertUnit(it) }
        return store
    }

    private fun engine(store: InMemoryStore, runner: UnitRunner, control: RunControl) =
        RunEngine(store, runner, control, { now })

    private val alwaysGo = object : RunControl {
        override fun shouldContinue() = true
    }

    @Test
    fun `全部单元完成后项目进入 READY`() = runBlocking {
        val store = storeWith(unit("u1", 1), unit("u2", 2, listOf("u1")))
        val runner = FakeRunner(mapOf("u1" to queue(committed("u1")), "u2" to queue(committed("u2"))))

        // 假执行器只返回结果，不真的改单元状态；这里手动模拟提交后的状态。
        val engine = RunEngine(store, runner, alwaysGo, { now })
        // 为了让循环推进，预先在假执行器里把状态改好：用包装类
        val wrapped = object : UnitRunner {
            override suspend fun execute(projectId: ProjectId, unitId: UnitId, runId: RunId): UnitOutcome {
                val o = runner.execute(projectId, unitId, runId)
                if (o is UnitOutcome.Committed) store.upsertUnit(store.unit(unitId)!!.copy(status = UnitStatus.READY))
                return o
            }
        }
        val outcome = RunEngine(store, wrapped, alwaysGo, { now }).run(projectId, runId)

        assertIs<RunOutcome.Completed>(outcome)
        assertEquals(2, outcome.committedUnits)
        assertEquals(ProjectLifecycle.READY, store.project(projectId)!!.lifecycle)
        assertEquals(RunStatus.COMPLETED, store.run(runId)!!.status)
    }

    @Test
    fun `暂停信号在开始前就生效时不执行任何单元`() = runBlocking {
        val store = storeWith(unit("u1", 1))
        val runner = FakeRunner(mapOf("u1" to queue(committed("u1"))))
        val stop = object : RunControl {
            override fun shouldContinue() = false
        }

        val outcome = engine(store, runner, stop).run(projectId, runId)

        assertIs<RunOutcome.Paused>(outcome)
        assertTrue(runner.executed.isEmpty())
        assertEquals(RunStatus.PAUSED, store.run(runId)!!.status)
    }

    @Test
    fun `单元需要人处理时整个任务停下且后续单元不执行`() = runBlocking {
        val store = storeWith(unit("u1", 1), unit("u2", 2, listOf("u1")))
        val runner = FakeRunner(
            mapOf("u1" to queue(UnitOutcome.NeedsAttention("审查一直不过"))),
        )

        val outcome = engine(store, runner, alwaysGo).run(projectId, runId)

        val attention = assertIs<RunOutcome.NeedsAttention>(outcome)
        assertEquals("审查一直不过", attention.reason)
        assertEquals(listOf("u1"), runner.executed, "依赖 u1 的 u2 不该被执行")
        assertEquals(ProjectLifecycle.NEEDS_ATTENTION, store.project(projectId)!!.lifecycle)
        assertEquals(RunStatus.WAITING_INPUT, store.run(runId)!!.status)
    }

    @Test
    fun `调用失败时记录 RETRY_WAIT 并停下`() = runBlocking {
        val store = storeWith(unit("u1", 1))
        val runner = FakeRunner(mapOf("u1" to queue(UnitOutcome.Failed("断网"))))

        val outcome = engine(store, runner, alwaysGo).run(projectId, runId)

        assertIs<RunOutcome.NeedsAttention>(outcome)
        assertEquals(RunStatus.RETRY_WAIT, store.run(runId)!!.status)
    }

    @Test
    fun `依赖永远不满足时报告停滞而不是宣称完成`() = runBlocking {
        // u2 依赖一个不存在的单元，永远无法开始
        val store = storeWith(unit("u2", 2, listOf("ghost")))
        val runner = FakeRunner(emptyMap())

        val outcome = engine(store, runner, alwaysGo).run(projectId, runId)

        val stalled = assertIs<RunOutcome.Stalled>(outcome)
        assertTrue(stalled.reason.contains("无法开始"))
        assertTrue(runner.executed.isEmpty())
    }
}
