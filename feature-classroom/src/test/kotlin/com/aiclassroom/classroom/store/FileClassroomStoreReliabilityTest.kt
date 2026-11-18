package com.aiclassroom.classroom.store

import com.aiclassroom.classroom.domain.*
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 仓库的可靠性契约：写盘失败不能污染状态，损坏存档不能被覆盖，并发不能丢数据。
 *
 * 这些用例针对的正是之前的两处真实缺陷：
 * - transaction 在 persist 失败时把 depth 递减两次，之后所有写入都不再落盘；
 * - 完全没有同步，后台提交与前台编辑会互相覆盖。
 */
class FileClassroomStoreReliabilityTest {

    private val now = 1_700_000_000_000L

    private fun dir() = Files.createTempDirectory("store").toFile()

    private fun project(id: String) = ClassroomProject(
        id = ProjectId(id), title = "项目" + id, type = ProjectType.TEACHING,
        lifecycle = ProjectLifecycle.DRAFT, createdAt = now, updatedAt = now,
    )

    @Test
    fun `写盘失败回滚后 下一次仍能正常落盘`() {
        val root = dir()
        var failOnce = true
        val store = FileClassroomStore(root) { file, text ->
            if (failOnce) {
                failOnce = false
                throw IOException("disk full")
            }
            file.writeText(text)
        }

        assertFailsWith<IOException> { store.createProject(project("bad")) }
        assertEquals(1, store.rollbackCount, "失败应记一次回滚")
        // 失败的项目不该留在内存里（否则界面看到成功、磁盘没有）
        assertTrue(store.projects().isEmpty())

        // 关键：深度没有被多减一次，所以这一笔必须真的落盘
        store.createProject(project("good"))

        val reopened = FileClassroomStore(root)
        assertEquals(listOf("good"), reopened.projects().map { it.id.value })
    }

    @Test
    fun `事务里写盘失败同样只回滚一次并可继续使用`() {
        val root = dir()
        var failOnce = true
        val store = FileClassroomStore(root) { file, text ->
            if (failOnce) {
                failOnce = false
                throw IOException("disk full")
            }
            file.writeText(text)
        }

        assertFailsWith<IOException> {
            store.transaction {
                store.createProject(project("a"))
                store.createProject(project("b"))
            }
        }
        assertTrue(store.projects().isEmpty(), "事务失败后内存应回滚")

        store.transaction { store.createProject(project("c")) }
        val reopened = FileClassroomStore(root)
        assertEquals(listOf("c"), reopened.projects().map { it.id.value })
    }

    @Test
    fun `损坏的存档会被保留而不是被覆盖`() {
        val root = dir()
        val main = java.io.File(root, "classroom_store.json")
        main.writeText("{ 这不是合法 JSON")

        val store = FileClassroomStore(root)
        assertTrue(store.projects().isEmpty())

        // 原文件必须还在（改名留档），内容不变
        val preserved = root.listFiles()!!.filter { it.name.contains(".corrupt-") }
        assertEquals(1, preserved.size, "损坏文件应留档一份")
        assertTrue(preserved.first().readText().contains("这不是合法 JSON"))

        // 之后正常写入不受影响
        store.createProject(project("after"))
        assertEquals(1, FileClassroomStore(root).projects().size)
    }

    @Test
    fun `并发写入不丢数据也不抛异常`() {
        val root = dir()
        val store = FileClassroomStore(root)
        val threads = 4
        val perThread = 25
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        val workers = (0 until threads).map { t ->
            Thread {
                ready.countDown()
                go.await()
                repeat(perThread) { i ->
                    store.createProject(project("t" + t + "-" + i))
                    // 同时读，检验读不会被写打断
                    store.projects().size
                }
            }
        }
        workers.forEach { it.start() }
        ready.await(5, TimeUnit.SECONDS)
        go.countDown()
        workers.forEach { it.join(20_000) }

        val reopened = FileClassroomStore(root)
        assertEquals(threads * perThread, reopened.projects().size, "并发写入丢了条目")
    }

    @Test
    fun `最新事实版本按项目隔离`() {
        val root = dir()
        val store = FileClassroomStore(root)
        val fid = FactId("f1")
        store.saveFact(
            FactRevision(
                factId = fid, projectId = ProjectId("pa"), version = 1,
                content = "A 的事实", kind = FactKind.FACT,
                sourceUnitId = null, sourceRevisionId = null,
                visibility = Visibility.AUTHOR_ONLY, evidence = "第1节",
            ),
        )
        store.saveFact(
            FactRevision(
                factId = fid, projectId = ProjectId("pb"), version = 7,
                content = "B 的事实", kind = FactKind.FACT,
                sourceUnitId = null, sourceRevisionId = null,
                visibility = Visibility.AUTHOR_ONLY, evidence = "第2节",
            ),
        )

        assertEquals(1, store.latestFactVersion(fid, ProjectId("pa"))!!.version)
        assertEquals(7, store.latestFactVersion(fid, ProjectId("pb"))!!.version)
        assertNotNull(store.facts(ProjectId("pa")).firstOrNull())
    }
}
