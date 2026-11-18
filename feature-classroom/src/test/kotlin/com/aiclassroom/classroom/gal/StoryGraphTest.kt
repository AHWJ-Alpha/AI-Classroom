package com.aiclassroom.classroom.gal

import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoryGraphTest {

    private val now = 1_700_000_000_000L
    private val projectId = ProjectId("p1")

    private fun text(t: String) = ContentBlock.Narration("b", t)

    private fun option(id: String, target: String, cond: Condition? = null, effects: List<Effect> = emptyList()) =
        ChoiceOption(id = id, text = id, condition = cond, effects = effects, targetNodeId = target)

    private fun graph() = StoryGraph(
        variables = listOf(VarSpec("courage", GalEvaluator.VarKind.INT)),
        startId = "n1",
        nodes = listOf(
            StoryNode(
                id = "n1", title = "开场", blocks = listOf(text("雨夜")),
                choices = listOf(
                    option("o1", "n2", effects = listOf(Effect.Increment("courage", 2))),
                    option("o2", "n3"),
                ),
            ),
            StoryNode(id = "n2", title = "勇敢线", blocks = listOf(text("往前走")), nextId = "end1"),
            StoryNode(id = "n3", title = "退缩线", blocks = listOf(text("回头")), endingId = "e_retreat"),
            StoryNode(id = "end1", title = "结局", blocks = listOf(text("天亮")), endingId = "e_brave"),
        ),
    )

    @Test
    fun `合法图通过校验`() {
        assertTrue(StoryGraphValidator.validate(graph()).isEmpty())
    }

    @Test
    fun `选项指向不存在的节点被发现`() {
        val broken = graph().let { g ->
            g.copy(nodes = g.nodes.map { if (it.id == "n1") it.copy(choices = listOf(option("o1", "ghost"))) else it })
        }
        val problems = StoryGraphValidator.validate(broken)
        assertTrue(problems.any { it.code == "dangling-choice" })
    }

    @Test
    fun `没有可达结局被发现`() {
        val broken = graph().let { g ->
            g.copy(nodes = g.nodes.map { it.copy(endingId = null) })
        }
        assertTrue(StoryGraphValidator.validate(broken).any { it.code == "no-ending" })
    }

    @Test
    fun `走不到的节点被发现`() {
        val g = graph().let { it.copy(nodes = it.nodes + StoryNode(id = "orphan", title = "孤岛", blocks = emptyList(), endingId = "e_x")) }
        assertTrue(StoryGraphValidator.validate(g).any { it.code == "unreachable" && it.nodeId == "orphan" })
    }

    @Test
    fun `死路被发现`() {
        val g = graph().let { it.copy(nodes = it.nodes + StoryNode(id = "dead", title = "死路", blocks = emptyList())) }
        assertTrue(StoryGraphValidator.validate(g).any { it.code == "dead-end" })
    }

    @Test
    fun `选择只改自己的存档并记录结局`() {
        val session = PlaySession(graph())
        val a = session.start(projectId, 1, now)
        val b = session.start(projectId, 1, now)

        val afterA = assertNotNull(session.choose(a, "o1", now))
        assertEquals("n2", afterA.currentNodeId)
        assertEquals(GalValue.Int(2), afterA.variables["courage"])
        assertEquals(listOf("o1"), afterA.choiceHistory)

        // b 没受影响
        assertEquals("n1", b.currentNodeId)
        assertEquals(GalValue.Int(0), b.variables["courage"])

        // 直线推进到结局并记录
        val ending = assertNotNull(session.advance(afterA, now))
        assertEquals("end1", ending.currentNodeId)
        assertEquals(listOf("e_brave"), ending.seenEndingIds)
    }

    @Test
    fun `结局节点不能再推进`() {
        val session = PlaySession(graph())
        val a = session.start(projectId, 1, now)
        val retreat = assertNotNull(session.choose(a, "o2", now))
        assertEquals(listOf("e_retreat"), retreat.seenEndingIds)
        assertNull(session.advance(retreat, now))
    }

    @Test
    fun `条件不满足的选项不会出现`() {
        val g = StoryGraph(
            variables = listOf(VarSpec("courage", GalEvaluator.VarKind.INT)),
            startId = "n1",
            nodes = listOf(
                StoryNode(
                    id = "n1", title = "开场", blocks = emptyList(),
                    choices = listOf(
                        option("brave", "n2", cond = Condition.GreaterOrEqual("courage", GalValue.Int(5))),
                        option("safe", "end1"),
                    ),
                ),
                StoryNode(id = "n2", title = "勇敢", blocks = emptyList(), endingId = "e_brave"),
                StoryNode(id = "end1", title = "普通", blocks = emptyList(), endingId = "e_plain"),
            ),
        )
        val session = PlaySession(g)
        val save = session.start(projectId, 1, now)
        val choices = session.availableChoices(save)
        assertEquals(1, choices.size)
        assertEquals("safe", choices[0].id)
        assertNull(session.choose(save, "brave", now))
    }
}
