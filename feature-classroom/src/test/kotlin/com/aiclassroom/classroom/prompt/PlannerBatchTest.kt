package com.aiclassroom.classroom.prompt

import com.aiclassroom.classroom.domain.ProjectType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 分批细化：范围外的行要忽略，缺 goal 的行要丢掉。 */
class PlannerBatchTest {

    private val titles = listOf("第1-3天", "第4-7天", "第2周", "第3周", "第4周")

    @Test
    fun `分批请求只要求指定范围并说明上限`() {
        val p = PlannerBatch.request("一起住一个月", ProjectType.SCENE, titles, 1, 4)
        assertTrue(p.contains("只输出第 1 到第 4 个单元"))
        assertTrue(p.contains("goal 不超过 40 字"))
        assertTrue(p.contains("不要截断 JSON"))
    }

    @Test
    fun `解析时忽略范围外的单元`() {
        val raw = """{"units":[{"index":1,"goal":"建立边界","completionCriteria":["钥匙交接"]},{"index":9,"goal":"范围外"}]}"""
        val parsed = PlannerBatch.parse(raw, 1, 4)
        assertEquals(1, parsed.size)
        assertEquals(1, parsed[0].index)
    }

    @Test
    fun `没有 goal 的行被丢弃`() {
        val raw = """{"units":[{"index":2,"goal":"  "}]}"""
        assertTrue(PlannerBatch.parse(raw, 1, 4).isEmpty())
    }
}
