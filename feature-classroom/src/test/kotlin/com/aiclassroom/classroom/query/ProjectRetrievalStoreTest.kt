package com.aiclassroom.classroom.query

import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.store.FileClassroomStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectRetrievalStoreTest {

    private val now = 1_700_000_000_000L
    private val pid = ProjectId("p1")

    private fun store() = FileClassroomStore(Files.createTempDirectory("retr").toFile())

    private fun seed(store: FileClassroomStore, commitRevision: Boolean = true) {
        store.createProject(
            ClassroomProject(
                id = pid, title = "Python 面向对象", type = ProjectType.TEACHING,
                lifecycle = ProjectLifecycle.READY, workVersion = 2, createdAt = now, updatedAt = now,
            ),
        )
        for (i in 1..3) {
            val uid = UnitId("u" + i)
            val rid = RevisionId("r" + i)
            store.upsertUnit(
                ClassroomUnit(
                    uid, pid, i, ProjectType.TEACHING, "第" + i + "节", "目标" + i,
                    emptyList(), emptyList(), listOf("完成"), 0, UnitStatus.READY,
                    currentRevisionId = if (commitRevision) rid else null,
                ),
            )
            store.saveRevision(
                UnitRevision(
                    id = rid, unitId = uid, projectId = pid, version = 1,
                    draftText = "", attemptId = null, modelName = "m",
                    promptTemplateVersion = 1, createdAt = now, committedAt = now,
                    sourceHash = "h",
                    content = UnitContent(
                        schemaVersion = 1, unitId = uid, planVersion = 1, title = "第" + i + "节",
                        objectivesCovered = emptyList(), unresolvedItems = emptyList(), events = emptyList(),
                        blocks = listOf(
                            ContentBlock.Narration("b1", "这是第" + i + "节的正文，讲多态与继承"),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `从仓库取第三节的正式内容`() {
        val s = store()
        seed(s)
        val ctx = ProjectRetrieval.forProject(s, pid, "解释第三节")
        assertEquals(3, ctx!!.excerpts.first().unitIndex)
        assertTrue(ctx.excerpts.first().text.contains("多态"))
        assertEquals("Python 面向对象", ctx.projectTitle)
    }

    @Test
    fun `没有正式版本时取不到内容`() {
        val s = store()
        seed(s, commitRevision = false)
        val ctx = ProjectRetrieval.forProject(s, pid, "解释第三节")
        assertTrue(ctx!!.isEmpty)
    }

    @Test
    fun `项目不存在返回 null`() {
        assertNull(ProjectRetrieval.forProject(store(), ProjectId("nope"), "随便问"))
    }

    @Test
    fun `角色视角不检索全文 并说明原因`() {
        val s = store()
        seed(s)
        val ctx = ProjectRetrieval.forProject(s, pid, "解释第三节", authorView = false)!!
        assertTrue(ctx.isEmpty, "角色视角不该拿到正文")
        assertTrue(ctx.refusedReason.contains("角色视角"), "要说明为什么没给")
    }
}
