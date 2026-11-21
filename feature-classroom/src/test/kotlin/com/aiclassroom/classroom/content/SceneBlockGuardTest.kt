package com.aiclassroom.classroom.content

import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 场景类型的内容块约束。
 *
 * 提示词只能劝，这里才是拦：模型返回讲解块时，正文进不了审查。
 */
class SceneBlockGuardTest {

    private val unitId = UnitId("u1")

    private fun json(vararg types: String): String {
        val blocks = types.mapIndexed { i, t ->
            "{\"id\":\"b" + (i + 1) + "\",\"type\":\"" + t + "\",\"text\":\"内容\"}"
        }.joinToString(",")
        return "{\"title\":\"第 1-3 天\",\"blocks\":[" + blocks + "],\"events\":[]}"
    }

    @Test
    fun `场景允许叙述对白标题`() {
        val parsed = UnitContentParser.parse(
            json("narration", "heading"), unitId, 1,
            projectType = ProjectType.SCENE,
        )
        assertTrue(parsed.problems.isEmpty(), "叙述类块不该被拦")
    }

    @Test
    fun `场景拒绝讲解例题练习与选项`() {
        for (bad in listOf("explanation", "example", "exercise", "choice")) {
            val parsed = UnitContentParser.parse(
                json("narration", bad), unitId, 1,
                projectType = ProjectType.SCENE,
            )
            assertTrue(
                parsed.problems.any { it.contains("不允许的内容块类型") },
                bad + " 必须被拒绝",
            )
        }
    }

    @Test
    fun `教学类型仍然接受讲解块`() {
        val parsed = UnitContentParser.parse(
            json("narration", "explanation"), unitId, 1,
            projectType = ProjectType.TEACHING,
        )
        assertTrue(parsed.problems.isEmpty(), "教学类型不该被拦")
    }

    @Test
    fun `白名单本身按类型区分`() {
        assertTrue(allowedBlockTypes(ProjectType.SCENE) == setOf("narration", "dialogue", "heading"))
        assertTrue("choice" in allowedBlockTypes(ProjectType.TEXT_GAL))
        assertTrue("explanation" !in allowedBlockTypes(ProjectType.SCENE))
    }
}
