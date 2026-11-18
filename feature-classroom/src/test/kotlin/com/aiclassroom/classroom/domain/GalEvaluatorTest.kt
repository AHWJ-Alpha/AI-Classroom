package com.aiclassroom.classroom.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GalEvaluatorTest {

    private val vars = mapOf(
        "visited_library" to GalValue.Bool(true),
        "courage" to GalValue.Int(3),
        "mood" to GalValue.Enum("calm"),
    )

    @Test
    fun `equals 比较布尔变量`() {
        val c = Condition.Equals("visited_library", GalValue.Bool(true))
        assertTrue(GalEvaluator.evaluate(c, vars))
    }

    @Test
    fun `未声明的变量求值为 false 而不是抛异常`() {
        val c = Condition.Equals("not_exists", GalValue.Bool(true))
        assertFalse(GalEvaluator.evaluate(c, vars))
    }

    @Test
    fun `greaterOrEqual 对整数生效`() {
        assertTrue(GalEvaluator.evaluate(Condition.GreaterOrEqual("courage", GalValue.Int(3)), vars))
        assertFalse(GalEvaluator.evaluate(Condition.GreaterOrEqual("courage", GalValue.Int(4)), vars))
    }

    @Test
    fun `greaterOrEqual 用在非整数上返回 false`() {
        val c = Condition.GreaterOrEqual("mood", GalValue.Int(1))
        assertFalse(GalEvaluator.evaluate(c, vars))
    }

    @Test
    fun `and 与 or 与 not 的组合`() {
        val c = Condition.And(
            listOf(
                Condition.Equals("visited_library", GalValue.Bool(true)),
                Condition.Not(Condition.Equals("mood", GalValue.Enum("angry"))),
            ),
        )
        assertTrue(GalEvaluator.evaluate(c, vars))

        val c2 = Condition.Or(
            listOf(
                Condition.Equals("mood", GalValue.Enum("angry")),
                Condition.GreaterOrEqual("courage", GalValue.Int(2)),
            ),
        )
        assertTrue(GalEvaluator.evaluate(c2, vars))
    }

    @Test
    fun `increment 只对整数变量生效`() {
        val after = GalEvaluator.apply(Effect.Increment("courage", 2), vars)
        assertEquals(GalValue.Int(5), after["courage"])

        val unchanged = GalEvaluator.apply(Effect.Increment("mood", 2), vars)
        assertEquals(GalValue.Enum("calm"), unchanged["mood"])
    }

    @Test
    fun `set 覆盖已有值并保留其他变量`() {
        val after = GalEvaluator.apply(Effect.Set("mood", GalValue.Enum("happy")), vars)
        assertEquals(GalValue.Enum("happy"), after["mood"])
        assertEquals(GalValue.Int(3), after["courage"])
    }

    @Test
    fun `validate 发现未声明变量`() {
        val decls = mapOf("courage" to GalEvaluator.VarKind.INT)
        val problems = GalEvaluator.validate(Condition.Equals("ghost", GalValue.Bool(true)), decls)
        assertEquals(1, problems.size)
        assertTrue(problems[0].contains("ghost"))
    }

    @Test
    fun `validate 拒绝在非整数上做大小比较`() {
        val decls = mapOf("mood" to GalEvaluator.VarKind.ENUM)
        val problems = GalEvaluator.validate(Condition.GreaterOrEqual("mood", GalValue.Int(1)), decls)
        assertEquals(1, problems.size)
    }

    @Test
    fun `validate 通过时返回空列表`() {
        val decls = mapOf(
            "courage" to GalEvaluator.VarKind.INT,
            "mood" to GalEvaluator.VarKind.ENUM,
        )
        val c = Condition.And(
            listOf(
                Condition.GreaterOrEqual("courage", GalValue.Int(2)),
                Condition.Equals("mood", GalValue.Enum("calm")),
            ),
        )
        assertTrue(GalEvaluator.validate(c, decls).isEmpty())
    }
}
