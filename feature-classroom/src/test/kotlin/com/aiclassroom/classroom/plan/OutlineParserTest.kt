package com.aiclassroom.classroom.plan

import com.aiclassroom.classroom.domain.ProjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutlineParserTest {

    private val projectId = ProjectId("p1")
    private val now = 1_700_000_000_000L

    private fun parse(text: String) = OutlineParser.parse(projectId, 1, text, now)

    @Test
    fun `合法 JSON 解析成总纲且单元 id 由程序生成`() {
        val text = """
        {
          "globalGoal": "教会面向对象",
          "mainLine": "从类到继承",
          "chapters": ["第一章"],
          "units": [
            {"index": 1, "title": "什么是类", "goal": "理解类", "completionCriteria": ["能说出类的作用"]},
            {"index": 2, "title": "实例化", "goal": "会创建对象", "dependsOn": [1], "completionCriteria": ["能写出实例化"]}
          ]
        }
        """.trimIndent()

        val parsed = parse(text)
        val outline = assertNotNull(parsed.outline)
        assertEquals(2, outline.units.size)
        assertTrue(outline.units[0].unitId != outline.units[1].unitId)

        // 依赖用序号表达时，换算成第一单元的 id
        assertEquals(listOf(outline.units[0].unitId), outline.units[1].dependsOn)
        assertTrue(parsed.problems.none { it.blocking }, parsed.problems.toString())
    }

    @Test
    fun `代码块包裹的输出也能解析`() {
        val fence = Char(96).toString().repeat(3)
        val text = "好的：\n" + fence + "json\n{\"units\":[{\"index\":1,\"title\":\"A\",\"goal\":\"g\",\"completionCriteria\":[\"c\"]}]}\n" + fence
        val parsed = parse(text)
        assertNotNull(parsed.outline)
        assertEquals(1, parsed.outline!!.units.size)
    }

    @Test
    fun `没有 JSON 时报告不可用而不是抛异常`() {
        val parsed = parse("抱歉，我无法完成这个请求。")
        assertNull(parsed.outline)
        assertTrue(parsed.problems.any { it.code == "no-json" && it.blocking })
    }

    @Test
    fun `半截 JSON 判定为不可用`() {
        val parsed = parse("{\"units\":[{\"index\":1,\"title\":\"A\"")
        assertNull(parsed.outline)
    }

    @Test
    fun `单元缺结束条件会把阻断问题带出来`() {
        val text = "{\"units\":[{\"index\":1,\"title\":\"A\",\"goal\":\"g\"}]}"
        val parsed = parse(text)
        assertNotNull(parsed.outline)
        assertTrue(parsed.problems.any { it.code == "no-criteria" })
    }

    @Test
    fun `units 为空判定不可用`() {
        val parsed = parse("{\"units\":[]}")
        assertNull(parsed.outline)
        assertTrue(parsed.problems.any { it.code == "no-units" })
    }
}
