package com.aiclassroom.classroom.domain

import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.gateway.ModelPolicyResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * 输入指纹只认「会改变正文身份」的东西。
 *
 * 曾经把 maxOutputTokens 也算进来：调过一次上限之后所有旧草稿失配，
 * 用户点「继续」就从头重写，等于白花额度。
 */
class SceneInputVersionTest {

    private val projectId = ProjectId("p1")
    private val unitId = UnitId("u1")

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
        return s
    }

    private fun policy(maxTokens: Int?) = ModelPolicyResolver.resolve(
        provider = "p", baseUrl = "https://example.invalid", orderedModels = listOf("m"),
        temperature = 0.3, maxOutputTokens = maxTokens,
    )

    @Test
    fun `只改输出上限不会让草稿失配`() {
        val s = store()
        val a = SceneInputVersion.compute(s, projectId, unitId, policy(4_096))
        val b = SceneInputVersion.compute(s, projectId, unitId, policy(8_192))
        assertEquals(a, b, "输出上限是工程参数，不是内容身份")
    }

    @Test
    fun `换模型会让草稿失配`() {
        val s = store()
        val a = SceneInputVersion.compute(s, projectId, unitId, policy(null))
        val other = ModelPolicyResolver.resolve(
            provider = "p", baseUrl = "https://example.invalid", orderedModels = listOf("m2"),
            temperature = 0.3,
        )
        assertNotEquals(a, SceneInputVersion.compute(s, projectId, unitId, other))
    }

    @Test
    fun `改单元目标会让草稿失配`() {
        val s = store()
        val before = SceneInputVersion.compute(s, projectId, unitId, policy(null))
        val unit = s.unit(unitId)!!
        s.upsertUnit(unit.copy(goal = "换一个目标"))
        assertNotEquals(before, SceneInputVersion.compute(s, projectId, unitId, policy(null)))
    }
}
