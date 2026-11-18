package com.aiclassroom.classroom.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 这一组针对真实缺陷：快照收集曾用 visibility != "author" 过滤，
 * 而来源存的其实是 author_private —— 条件永远成立，作者秘密被当成公开事实。
 */
class SnapshotFactVisibilityTest {

    @Test
    fun `来源的真实取值被正确映射`() {
        assertEquals(Visibility.AUTHOR_ONLY, VisibilityRules.fromLegacy("author_private"))
        assertEquals(Visibility.PUBLIC, VisibilityRules.fromLegacy("public"))
        assertEquals(Visibility.RESTRICTED, VisibilityRules.fromLegacy("character_private"))
        // 未知取值一律保守当作者私密，不能当公开
        assertEquals(Visibility.AUTHOR_ONLY, VisibilityRules.fromLegacy("author"))
        assertEquals(Visibility.AUTHOR_ONLY, VisibilityRules.fromLegacy(null))
    }

    @Test
    fun `作者私密事实角色永远看不到`() {
        val f = SnapshotFact("校长是幕后黑手", Visibility.AUTHOR_ONLY)
        assertTrue(f.visibleTo(null), "作者视角应该看得到")
        assertFalse(f.visibleTo(emptySet()), "角色视角不该看到")
        assertFalse(f.visibleTo(setOf("林夏")))
    }

    @Test
    fun `限定事实只有知情人看得到`() {
        val f = SnapshotFact(
            "林夏是卧底", Visibility.RESTRICTED,
            knownBy = setOf("林夏"), unknownBy = emptySet(),
        )
        assertTrue(f.visibleTo(setOf("林夏")))
        assertFalse(f.visibleTo(setOf("周老师")))
    }

    @Test
    fun `unknownBy 优先于 public`() {
        val f = SnapshotFact(
            "下雨了", Visibility.PUBLIC,
            knownBy = emptySet(), unknownBy = setOf("林夏"),
        )
        assertFalse(f.visibleTo(setOf("林夏")), "显式不知道的人即使是公开事实也不该知道")
        assertTrue(f.visibleTo(setOf("周老师")))
    }

    @Test
    fun `快照往返保留可见性与知情人`() {
        val fact = SnapshotFact(
            "主角怕水", Visibility.RESTRICTED,
            knownBy = setOf("林夏"), unknownBy = setOf("周老师"), evidence = "第二章",
        )
        val encoded = com.aiclassroom.classroom.store.StoreCodec.snapshot(
            ContextSnapshot(
                id = SnapshotId("s1"), projectId = ProjectId("p1"), version = 1,
                createdAt = 0L, facts = listOf(fact),
            ),
        )
        val back = com.aiclassroom.classroom.store.StoreCodec.snapshot(
            com.aiclassroom.classroom.json.JsonParser.parse(
                com.aiclassroom.classroom.json.JsonWriter.write(encoded),
            ),
        )
        assertEquals(1, back.facts.size)
        assertEquals(Visibility.RESTRICTED, back.facts[0].visibility)
        assertEquals(setOf("林夏"), back.facts[0].knownBy)
        assertEquals(setOf("周老师"), back.facts[0].unknownBy)
        assertEquals("第二章", back.facts[0].evidence)
    }
}
