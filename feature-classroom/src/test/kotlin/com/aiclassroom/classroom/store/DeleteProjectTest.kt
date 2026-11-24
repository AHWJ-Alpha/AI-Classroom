package com.aiclassroom.classroom.store

import com.aiclassroom.classroom.domain.*
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 删除项目必须连关联数据一起清掉。
 *
 * 只删 projects 会留下孤儿：规格、总纲、单元、草稿、摘要都还挂在那个 projectId 下，
 * 下次按 projectId 查仍然能查出来。
 */
class DeleteProjectTest {

    private val projectId = ProjectId("p1")
    private val unitId = UnitId("u1")

    private fun seed(s: FileClassroomStore) {
        s.createProject(
            ClassroomProject(
                id = projectId, title = "要删的", type = ProjectType.SCENE,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = 1L, updatedAt = 1L,
            ),
        )
        s.saveSpec(
            ProjectSpec(
                projectId = projectId, version = 1, rawRequest = "一起住", topic = "同居",
                goals = listOf("相处"), audience = "自己", language = "zh", style = "细腻",
                expectedLength = "", unitCount = 1, runMode = RunMode.CONFIRM_OUTLINE,
                reviewPolicy = ReviewPolicy.STANDARD, hardConstraints = emptyList(),
                authorRules = emptyList(), publicWorld = "", createdAt = 1L,
            ),
        )
        s.saveOutline(
            ProjectOutline(
                projectId = projectId, version = 1, globalGoal = "熟", mainLine = "同居",
                chapters = listOf("第 1-3 天"),
                units = listOf(PlannedUnit(unitId, 1, "第 1-3 天", "建立边界", completionCriteria = listOf("完成"))),
                createdAt = 1L,
            ),
        )
        s.upsertUnit(
            ClassroomUnit(
                unitId, projectId, 1, ProjectType.SCENE, "第 1-3 天", "建立边界",
                emptyList(), emptyList(), listOf("完成"), 0, UnitStatus.PLANNED,
            ),
        )
        s.saveDraft(
            UnitDraft(
                projectId = projectId, unitId = unitId,
                candidateId = "c1", inputVersion = "v1",
                fencingToken = 1, updatedAt = 1L,
            ),
        )
    }

    @Test
    fun `删除项目后关联数据全部消失`() {
        val dir = Files.createTempDirectory("delete-project").toFile()
        val s = FileClassroomStore(dir)
        seed(s)
        assertNotNull(s.draft(projectId, unitId), "删除前草稿应该在")

        s.deleteProject(projectId)

        val reopened = FileClassroomStore(dir)
        assertNull(reopened.project(projectId))
        assertNull(reopened.unit(unitId))
        assertNull(reopened.draft(projectId, unitId))
        assertTrue(reopened.units(projectId).isEmpty())
        assertNull(reopened.latestSpec(projectId))
        assertNull(reopened.latestOutline(projectId))
    }

    @Test
    fun `删除一个项目不影响另一个`() {
        val dir = Files.createTempDirectory("delete-project-2").toFile()
        val s = FileClassroomStore(dir)
        seed(s)
        val other = ProjectId("p2")
        s.createProject(
            ClassroomProject(
                id = other, title = "留下的", type = ProjectType.NOVEL,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = 2L, updatedAt = 2L,
            ),
        )

        s.deleteProject(projectId)

        val reopened = FileClassroomStore(dir)
        assertNull(reopened.project(projectId))
        assertEquals("留下的", reopened.project(other)?.title)
    }
}
