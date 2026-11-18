package com.aiclassroom.classroom.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardSectionsTest {

    @Test
    fun `按标题把作者私密小节拆出来`() {
        val card = """
            ## 基本设定
            林夏，十七岁，嘴硬心软。

            ## 作者私密
            她其实是卧底，第三章会暴露。
        """.trimIndent()

        val split = CardSections.split(card)
        assertTrue(split.persona.contains("嘴硬心软"))
        assertTrue(split.hasPrivate)
        assertTrue(split.authorPrivate.contains("卧底"))
        assertFalse(split.persona.contains("卧底"))
    }

    @Test
    fun `没有分节的卡整段都是公开人格`() {
        val card = "他就是个普通的图书管理员，说话很慢。"
        val split = CardSections.split(card)
        assertEquals(card, split.persona)
        assertEquals("", split.authorPrivate)
    }

    @Test
    fun `三级标题与幕后字样同样算私密`() {
        val card = """
            ## 公开
            喜欢下雨天。

            ### 幕后设定
            市长是他父亲。
        """.trimIndent()
        val split = CardSections.split(card)
        assertTrue(split.authorPrivate.contains("市长"))
        assertFalse(split.persona.contains("市长"))
    }

    @Test
    fun `空卡不炸`() {
        val split = CardSections.split("   ")
        assertEquals("", split.persona)
        assertEquals("", split.authorPrivate)
    }

    @Test
    fun `正文里的普通加粗不当成分节`() {
        val newline = 10.toChar().toString()
        val card = "**重点**：他怕水。" + newline + "**注意**：别写他游泳。"
        val split = CardSections.split(card)
        assertEquals("", split.authorPrivate)
        assertTrue(split.persona.contains("怕水"))
    }
}
