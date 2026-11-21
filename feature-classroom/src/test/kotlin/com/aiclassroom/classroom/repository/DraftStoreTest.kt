package com.aiclassroom.classroom.repository

import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 分段草稿的写入守卫：重复段不追加、旧令牌被拒、输入变更后旧候选作废。 */
class DraftStoreTest {

    private val projectId = ProjectId("p1")
    private val unitId = UnitId("u1")

    private fun seg(index: Int, chars: Int = 100) = DraftSegment(
        index = index,
        text = "第" + index + "段正文",
        chars = chars,
    )

    @Test
    fun `分段可以累计保存并回读`() {
        val store = InMemoryDraftStore()
        store.startCandidate(projectId, unitId, "c1", "v1", 1L)
        assertTrue(store.appendSegment(unitId, seg(1, 120), RunId("r1"), 1L, 2L).ok)
        assertTrue(store.appendSegment(unitId, seg(2, 80), RunId("r1"), 1L, 3L).ok)

        val d = store.draft(unitId)!!
        assertEquals(2, d.segments.size)
        assertEquals(200, d.totalChars, "累计长度只按正文算")
        assertEquals(3, d.nextIndex)
    }

    @Test
    fun `重复分段不会追加两次`() {
        val store = InMemoryDraftStore()
        store.startCandidate(projectId, unitId, "c1", "v1", 1L)
        store.appendSegment(unitId, seg(1), RunId("r1"), 1L, 2L)
        val again = store.appendSegment(unitId, seg(1), RunId("r1"), 1L, 3L)

        assertFalse(again.ok, "同一序号重复写入必须被拒")
        assertEquals(1, store.draft(unitId)!!.segments.size)
    }

    @Test
    fun `被接管后的旧令牌不能写入`() {
        val store = InMemoryDraftStore()
        store.startCandidate(projectId, unitId, "c1", "v1", 1L)
        store.appendSegment(unitId, seg(1), RunId("r1"), 5L, 2L)

        val stale = store.appendSegment(unitId, seg(2), RunId("r1"), 3L, 3L)

        assertFalse(stale.ok, "旧执行者的迟到响应不得追加")
        assertEquals(1, store.draft(unitId)!!.segments.size)
    }

    @Test
    fun `新候选替换旧候选 旧分段不残留`() {
        val store = InMemoryDraftStore()
        store.startCandidate(projectId, unitId, "c1", "v1", 1L)
        store.appendSegment(unitId, seg(1), RunId("r1"), 1L, 2L)

        store.startCandidate(projectId, unitId, "c2", "v2", 3L)

        val d = store.draft(unitId)!!
        assertEquals("c2", d.candidateId)
        assertEquals("v2", d.inputVersion)
        assertTrue(d.segments.isEmpty(), "换了输入版本就不能接着旧内容写")
    }

    @Test
    fun `没有候选时拒绝追加`() {
        val store = InMemoryDraftStore()
        assertFalse(store.appendSegment(unitId, seg(1), RunId("r1"), 1L, 2L).ok)
        assertNull(store.draft(unitId))
    }

    @Test
    fun `正式提交后可以清掉草稿`() {
        val store = InMemoryDraftStore()
        store.startCandidate(projectId, unitId, "c1", "v1", 1L)
        store.appendSegment(unitId, seg(1), RunId("r1"), 1L, 2L)
        store.clear(unitId)
        assertNull(store.draft(unitId))
    }
}
