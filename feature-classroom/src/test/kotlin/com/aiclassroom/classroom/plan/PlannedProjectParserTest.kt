package com.aiclassroom.classroom.plan

import com.aiclassroom.classroom.domain.ProjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlannedProjectParserTest {

    private val projectId = ProjectId("p1")
    private val now = 1_700_000_000_000L

    private fun parse(text: String, units: Int = 2) =
        PlannedProjectParser.parse(text, projectId, "原始需求原文", units, now)

    private val good = """
        {
          "spec": {"topic":"Python 面向对象","goals":["理解类"],"audience":"初学者",
                   "language":"zh","style":"平实","expectedLength":"8 节","unitCount":2,
                   "hardConstraints":["不写框架"],"authorRules":[],"publicWorld":""},
          "globalGoal":"学会 OOP",
          "mainLine":"从类到继承",
          "chapters":["第一章"],
          "units": [
            {"index":1,"title":"什么是类","goal":"理解类","completionCriteria":["能举例"]},
            {"index":2,"title":"实例化","goal":"会建对象","dependsOn":[1],"completionCriteria":["能写代码"]}
          ]
        }
    """.trimIndent()

    @Test
    fun `合法输出解析出规格与总纲`() {
        val parsed = parse(good)
        assertTrue(parsed.ok, parsed.problems.toString())
        val spec = assertNotNull(parsed.spec)
        val outline = assertNotNull(parsed.outline)
        assertEquals("Python 面向对象", spec.topic)
        assertEquals(2, spec.unitCount)
        assertEquals(listOf("不写框架"), spec.hardConstraints)
        assertEquals(2, outline.units.size)
        assertEquals(listOf(outline.units[0].unitId), outline.units[1].dependsOn)
    }

    @Test
    fun `原始需求用调用方传入的原话而不是模型复述`() {
        val parsed = parse(good)
        assertEquals("原始需求原文", parsed.spec!!.rawRequest)
    }

    @Test
    fun `单元数与规格不一致会被标为阻断`() {
        val text = good.replace("\"unitCount\":2", "\"unitCount\":8")
        val parsed = parse(text, units = 8)
        assertTrue(!parsed.ok)
        assertTrue(parsed.problems.any { it.code == "unit-count-mismatch" })
    }

    @Test
    fun `主题为空判定不可用`() {
        val text = good.replace("\"topic\":\"Python 面向对象\"", "\"topic\":\"\"")
        val parsed = parse(text)
        assertTrue(parsed.problems.any { it.code == "blank-topic" })
    }

    @Test
    fun `没有 JSON 直接判定失败`() {
        val parsed = parse("我需要更多信息才能规划。")
        assertNull(parsed.spec)
        assertNull(parsed.outline)
        assertTrue(parsed.problems.any { it.code == "no-json" })
    }

    @Test
    fun `依赖成环时不算通过`() {
        val text = """
            {"spec":{"topic":"t","unitCount":2},"units":[
              {"index":1,"title":"A","goal":"g","dependsOn":[2],"completionCriteria":["c"]},
              {"index":2,"title":"B","goal":"g","dependsOn":[1],"completionCriteria":["c"]}]}
        """.trimIndent()
        val parsed = parse(text)
        assertTrue(parsed.problems.any { it.code == "dependency-cycle" })
        assertTrue(!parsed.ok)
    }
}
