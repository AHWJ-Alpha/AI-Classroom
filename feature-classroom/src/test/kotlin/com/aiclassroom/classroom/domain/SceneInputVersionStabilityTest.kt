package com.aiclassroom.classroom.domain

import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.gateway.ModelPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * 输入指纹不能因为「上游状态变了」就失配。
 *
 * 真机症状：上一节报错或中断后，下一节已经写好的草稿被判成「输入变了」，
 * 从头重写 —— 用户看到的就是「一报错/一中断，进度就没了」。
 * 而正文其实没有任何变化，变的只是 status。
 */
class SceneInputVersionStabilityTest {

    private val projectId = ProjectId("p1")
    private val upId = UnitId("u1")
    private val downId = UnitId("u2")
    private val policy = ModelPolicy("prov", "https://x.invalid", "m")

    private fun store(): InMemoryStore {
        val s = InMemoryStore()
        s.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.SCENE,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = 1L, updatedAt = 1L,
            ),
        )
        s.saveSpec(
            ProjectSpec(
                projectId = projectId, version = 1, rawRequest = "一起住", topic = "同居",
                goals = listOf("相处"), audience = "自己", language = "zh", style = "细腻",
                expectedLength = "", unitCount = 2, runMode = RunMode.CONFIRM_OUTLINE,
                reviewPolicy = ReviewPolicy.STANDARD, hardConstraints = emptyList(),
                authorRules = emptyList(), publicWorld = "", createdAt = 1L,
            ),
        )
        s.saveOutline(
            ProjectOutline(
                projectId = projectId, version = 1, globalGoal = "熟", mainLine = "同居",
                chapters = listOf("第 1-3 天"),
                units = listOf(
                    PlannedUnit(upId, 1, "第 1-3 天", "建立边界", completionCriteria = listOf("完成")),
                    PlannedUnit(downId, 2, "第 4-7 天", "继续", completionCriteria = listOf("完成")),
                ),
                createdAt = 1L,
            ),
        )
        s.upsertUnit(unit(upId, 1, emptyList(), UnitStatus.READY))
        s.upsertUnit(unit(downId, 2, listOf("u1"), UnitStatus.PLANNED))
        return s
    }

    private fun unit(id: UnitId, index: Int, deps: List<String>, status: UnitStatus) =
        ClassroomUnit(
            id = id, projectId = projectId, index = index, type = ProjectType.SCENE,
            chapter = "第" + index + "节", goal = "g",
            dependsOn = deps.map(::UnitId),
            requiredCharacters = emptyList(), completionCriteria = listOf("完成"),
            inputStateVersion = 0, status = status,
        )

    @Test
    fun `上游从 READY 变成 STALE 不影响下游指纹`() {
        val s = store()
        val before = SceneInputVersion.compute(s, projectId, downId, policy)
        // 模拟：上游被标成待重写（比如用户改了方向，或上一节刚报错）。
        s.upsertUnit(s.unit(upId)!!.copy(status = UnitStatus.STALE))
        val after = SceneInputVersion.compute(s, projectId, downId, policy)
        assertEquals(before, after, "上游状态变化不该让下游草稿失效")
    }

    @Test
    fun `上游从 READY 变成 FAILED 不影响下游指纹`() {
        val s = store()
        val before = SceneInputVersion.compute(s, projectId, downId, policy)
        s.upsertUnit(s.unit(upId)!!.copy(status = UnitStatus.FAILED))
        val after = SceneInputVersion.compute(s, projectId, downId, policy)
        assertEquals(before, after, "一次失败不该让下游从头写")
    }

    @Test
    fun `上游换了正式版本才应该让下游失效`() {
        val s = store()
        val before = SceneInputVersion.compute(s, projectId, downId, policy)
        // 正式版本变了 = 正文真的变了，这时下游必须重写。
        s.upsertUnit(s.unit(upId)!!.copy(officialVersion = 2, currentRevisionId = RevisionId("new")))
        val after = SceneInputVersion.compute(s, projectId, downId, policy)
        assertNotEquals(before, after, "上游正文变了，下游就该失效")
    }
}
