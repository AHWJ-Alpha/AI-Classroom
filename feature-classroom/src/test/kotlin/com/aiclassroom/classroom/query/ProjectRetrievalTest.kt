package com.aiclassroom.classroom.query

import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectRetrievalTest {

    private val projectId = ProjectId("p1")

    private fun unit(index: Int, title: String, vararg lines: String): Pair<ClassroomUnit, UnitContent?> {
        val uid = UnitId("u" + index)
        val u = ClassroomUnit(
            uid, projectId, index, ProjectType.TEACHING, title, "讲清楚" + title,
            emptyList(), emptyList(), listOf("完成"), 0, UnitStatus.READY,
        )
        val content = UnitContent(
            schemaVersion = 1, unitId = uid, planVersion = 1, title = title,
            objectivesCovered = emptyList(), unresolvedItems = emptyList(), events = emptyList(),
            blocks = lines.mapIndexed { i, t -> ContentBlock.Narration("b" + i, t) },
        )
        return u to content
    }

    private fun units() = listOf(
        unit(1, "面向对象入门", "对象是数据的集合"),
        unit(2, "封装", "封装把状态藏起来"),
        unit(3, "继承与多态", "多态让子类替换父类"),
    )

    @Test
    fun `显式节号能解析`() {
        assertEquals(setOf(3), ProjectRetrieval.parseSectionNumbers("解释一下第三节"))
        assertEquals(setOf(2), ProjectRetrieval.parseSectionNumbers("第二章讲了什么"))
        assertEquals(emptySet(), ProjectRetrieval.parseSectionNumbers("这个作品讲了什么"))
    }

    @Test
    fun `问第三节就取第三节`() {
        val ctx = ProjectRetrieval.search(projectId, "Python 面向对象", 4, units(), "解释第三节")
        assertEquals(1, ctx.excerpts.size)
        assertEquals(3, ctx.excerpts.first().unitIndex)
        assertTrue(ctx.excerpts.first().text.contains("多态"))
    }

    @Test
    fun `没命中就返回空 不编造`() {
        val ctx = ProjectRetrieval.search(projectId, "Python 面向对象", 4, units(), "今天天气怎么样")
        assertTrue(ctx.isEmpty)
    }

    @Test
    fun `关键词能命中标题`() {
        val ctx = ProjectRetrieval.search(projectId, "Python 面向对象", 4, units(), "封装是什么意思")
        assertTrue(ctx.excerpts.any { it.unitIndex == 2 })
    }

    @Test
    fun `片段数量受上限约束`() {
        val ctx = ProjectRetrieval.search(projectId, "书", 1, units(), "第一节 第二节 第三节", maxExcerpts = 2)
        assertEquals(2, ctx.excerpts.size)
    }

    @Test
    fun `渲染会把超长正文截断`() {
        val long = "字".repeat(3000)
        val ctx = ProjectContext(
            projectId, "书", 2,
            listOf(ProjectExcerpt(1, UnitId("u1"), "第一节", long, "第 1 节")),
        )
        val text = ctx.render(limitPerExcerpt = 100)
        assertTrue(text.length < long.length)
        assertTrue(text.contains("已截断"))
        assertTrue(text.contains("正式版本 2"))
    }
}
