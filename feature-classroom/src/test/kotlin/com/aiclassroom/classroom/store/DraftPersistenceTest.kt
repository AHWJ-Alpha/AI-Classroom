package com.aiclassroom.classroom.store

import com.aiclassroom.classroom.domain.*
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 草稿必须真的写进同一个存档文件，并且能被**新建的仓库**读回来。
 *
 * 只测内存 Map 证明不了重启后还在。
 */
class DraftPersistenceTest {

    private val projectId = ProjectId("p1")
    private val unitId = UnitId("u1")

    private fun segment(index: Int, text: String): DraftSegment = DraftSegment(
        index = index,
        text = text,
        blocks = listOf(
            ContentBlock.Narration("c1:s" + index + ":b1", text),
            ContentBlock.Dialogue("c1:s" + index + ":b2", CharacterId("pc-a"), "她在门口停了一下。"),
        ),
        events = listOf(
            UnitEvent("c1:s" + index + ":e1", listOf("c1:s" + index + ":b1"), "她把钥匙放在鞋柜上"),
        ),
        chars = text.length,
        createdAt = 1_000L * index,
    )

    @Test
    fun `草稿写入后换一个仓库实例仍能完整读回`() {
        val dir = Files.createTempDirectory("drafts").toFile()
        val first = FileClassroomStore(dir)
        first.saveDraft(
            UnitDraft(
                projectId = projectId,
                unitId = unitId,
                candidateId = "c1",
                inputVersion = "v1",
                segments = listOf(segment(1, "她推门进来"), segment(2, "水烧开了")),
                totalChars = 300,
                summary = "第一段：她进门；第二段：烧水",
                usedScenes = listOf("玄关"),
                runId = RunId("r1"),
                fencingToken = 7,
                updatedAt = 2_000L,
            ),
        )

        // 丢掉所有对象，重新开仓库：模拟进程重启。
        val reopened = FileClassroomStore(dir)
        val d = assertNotNull(reopened.draft(projectId, unitId))

        assertEquals("c1", d.candidateId)
        assertEquals("v1", d.inputVersion)
        assertEquals(2, d.segments.size, "两段都要在")
        assertEquals(3, d.nextIndex, "下一段序号要接得上")
        assertEquals(300, d.totalChars)
        assertEquals(7, d.fencingToken.toInt())
        assertEquals("玄关", d.usedScenes.single())
        assertEquals(2, d.segments[1].blocks.size, "块必须原样回来")
        assertEquals("她把钥匙放在鞋柜上", d.segments[0].events.single().description)
        assertEquals(
            listOf("c1:s1:b1"),
            d.segments[0].events.single().sourceBlockIds,
            "事件对块的引用不能丢",
        )
    }

    @Test
    fun `删除草稿后不再回读 且不影响正式内容`() {
        val dir = Files.createTempDirectory("drafts2").toFile()
        val store = FileClassroomStore(dir)
        store.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.SCENE,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = 1L, updatedAt = 1L,
            ),
        )
        store.saveDraft(
            UnitDraft(projectId, unitId, "c1", "v1", listOf(segment(1, "她推门进来")), 10, "", emptyList(), null, 1, 1L),
        )
        store.removeDraft(projectId, unitId)

        val reopened = FileClassroomStore(dir)
        assertNull(reopened.draft(projectId, unitId))
        assertNotNull(reopened.project(projectId), "删草稿不该动项目")
    }
}
