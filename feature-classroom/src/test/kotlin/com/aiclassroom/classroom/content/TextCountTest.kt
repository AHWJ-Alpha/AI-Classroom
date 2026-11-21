package com.aiclassroom.classroom.content

import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals

/** 字数口径与接受区间。 */
class TextCountTest {

    private val speaker = CharacterId("pc-a")

    @Test
    fun `只数叙述与对白 标点空白不算`() {
        val blocks = listOf(
            ContentBlock.Narration("b1", "她推门进来。"),
            ContentBlock.Dialogue("b2", speaker, "你回来了？"),
            ContentBlock.Heading("b3", "第 1 天", 2),
            ContentBlock.Explanation("b4", "这里是讲解"),
        )
        // 「她推门进来」5 + 「你回来了」4
        assertEquals(9, TextCount.units(blocks))
    }

    @Test
    fun `英文与数字各算一个码点`() {
        assertEquals(6, TextCount.count("He is 25"), "空格不算")
    }

    @Test
    fun `接受下限默认九成并向上取整`() {
        assertEquals(180, TextCount.acceptMin(200))
        assertEquals(4500, TextCount.acceptMin(5000))
        assertEquals(1, TextCount.acceptMin(1))
    }

    @Test
    fun `用户写明至少 N 字时下限就是 N`() {
        assertEquals(200, TextCount.acceptMin(200, atLeast = 200))
    }

    @Test
    fun `上限是目标的一倍二`() {
        assertEquals(240, TextCount.acceptMax(200))
        assertEquals(6000, TextCount.acceptMax(5000))
    }
}
