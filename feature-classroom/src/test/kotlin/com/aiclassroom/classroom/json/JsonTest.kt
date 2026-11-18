package com.aiclassroom.classroom.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonTest {

    @Test
    fun `对象往返序列化保持结构与顺序`() {
        val json = Json.obj(
            "b" to Json.of(1),
            "a" to Json.of("x"),
            "n" to Json.arr(listOf(Json.of(true), Json.Null)),
        )
        val text = JsonWriter.write(json)
        assertEquals("{\"a\":\"x\"}", JsonWriter.write(Json.obj("a" to Json.of("x"))))
        assertEquals(json, JsonParser.parse(text))
    }

    @Test
    fun `整数不会退化成 3点0`() {
        assertEquals("{\"v\":3}", JsonWriter.write(Json.obj("v" to Json.of(3))))
        assertEquals("{\"v\":3}", JsonWriter.write(Json.obj("v" to Json.of(3L))))
        assertEquals("{\"v\":3.5}", JsonWriter.write(Json.obj("v" to Json.of(3.5))))
    }

    @Test
    fun `字符串转义往返`() {
        val tricky = "引号\" 反斜杠\\ 换行\n 制表\t 结束"
        val text = JsonWriter.write(Json.of(tricky))
        assertEquals(tricky, JsonParser.parse(text).asString())
    }

    @Test
    fun `中文与 emoji 原样保留`() {
        val s = "第一章 · 雨夜 🌧"
        assertEquals(s, JsonParser.parse(JsonWriter.write(Json.of(s))).asString())
    }

    @Test
    fun `unicode 转义可以解析`() {
        assertEquals("A", JsonParser.parse("\"\\u0041\"").asString())
    }

    @Test
    fun `拒绝尾随内容而不是只解析前半截`() {
        assertFailsWith<JsonParseException> { JsonParser.parse("{\"a\":1} 垃圾") }
    }

    @Test
    fun `拒绝未闭合的对象`() {
        assertFailsWith<JsonParseException> { JsonParser.parse("{\"a\":1") }
    }

    @Test
    fun `拒绝非法字面量`() {
        assertFailsWith<JsonParseException> { JsonParser.parse("tru") }
    }

    @Test
    fun `嵌套结构与数组下标取值`() {
        val parsed = JsonParser.parse("{\"units\":[{\"id\":\"u1\"},{\"id\":\"u2\"}]}")
        val units = parsed.asObject()["units"].asArray()
        assertEquals(2, units.size)
        assertEquals("u2", units[1].asObject()["id"].asString())
    }

    @Test
    fun `缺失字段与类型不符都返回 null 而不是抛异常`() {
        val parsed = JsonParser.parse("{\"n\":\"text\"}")
        assertNull(parsed.asObject()["missing"].asString())
        assertNull(parsed.asObject()["n"].asInt())
        assertTrue(parsed.asObject()["n"].asString() == "text")
    }
}
