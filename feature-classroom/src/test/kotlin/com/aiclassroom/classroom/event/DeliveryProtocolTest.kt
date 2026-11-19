package com.aiclassroom.classroom.event

import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 投递协议：消息先落盘、再确认。
 *
 * 这里模拟四种崩溃窗口，验证「不丢卡、不重复、不错投」——
 * 这是纯领域逻辑，不需要 Android 就能复现。
 */
class DeliveryProtocolTest {

    private val now = 1_700_000_000_000L
    private val projectId = ProjectId("p1")
    private val runId = RunId("r1")

    private fun event(conv: String = "conv-a") = ClassroomEvent(
        eventId = EventOutbox.eventIdOf(runId, projectId, EventType.COMPLETED),
        sourceConversationId = conv, projectId = projectId, projectTitle = "Python 面向对象",
        runId = runId, workVersion = 3, type = EventType.COMPLETED,
        summary = "共 8 节", createdAt = now,
    )

    /** 模拟一次投递：消息先落盘，成功后才确认。 */
    private fun deliver(
        store: InMemoryStore,
        conv: String,
        chat: MutableList<String>,
        persistSucceeds: Boolean = true,
    ) {
        for (e in EventOutbox.pending(store, conv)) {
            if (chat.any { EventText.mentionsEvent(it, e.eventId) }) {
                EventOutbox.markDelivered(store, e.eventId)
                continue
            }
            chat += EventText.render(e)
            if (persistSucceeds) {
                EventOutbox.markDelivered(store, e.eventId)
            } else {
                chat.removeAt(chat.lastIndex)
            }
        }
    }

    @Test
    fun `正常投递只产生一张卡`() {
        val store = InMemoryStore()
        EventOutbox.enqueue(store, event())
        val chat = mutableListOf<String>()
        deliver(store, "conv-a", chat)
        assertEquals(1, chat.size)
        assertTrue(EventOutbox.pending(store, "conv-a").isEmpty())
    }

    @Test
    fun `聊天保存失败时不确认 重试后仍只有一张卡`() {
        val store = InMemoryStore()
        EventOutbox.enqueue(store, event())
        val chat = mutableListOf<String>()

        deliver(store, "conv-a", chat, persistSucceeds = false)
        assertTrue(chat.isEmpty(), "没存下去就不该留下卡片")
        assertEquals(1, EventOutbox.pending(store, "conv-a").size, "事件应保持待投递")

        deliver(store, "conv-a", chat)
        assertEquals(1, chat.size)
        assertTrue(EventOutbox.pending(store, "conv-a").isEmpty())
    }

    @Test
    fun `消息已写但确认未落时 重放只补确认不重复插卡`() {
        val store = InMemoryStore()
        val e = event()
        EventOutbox.enqueue(store, e)
        val chat = mutableListOf(EventText.render(e))  // 模拟：写完就崩了，没确认

        deliver(store, "conv-a", chat)

        assertEquals(1, chat.size, "不能出现第二张卡")
        assertTrue(EventOutbox.pending(store, "conv-a").isEmpty())
    }

    @Test
    fun `只投递到它自己的对话`() {
        val store = InMemoryStore()
        EventOutbox.enqueue(store, event(conv = "conv-a"))
        val chatB = mutableListOf<String>()
        deliver(store, "conv-b", chatB)
        assertTrue(chatB.isEmpty(), "A 的事件不该写进 B")
        assertEquals(1, EventOutbox.pending(store, "conv-a").size)
    }

    @Test
    fun `幂等判定只认自己的 eventId`() {
        val a = event()
        val b = event().copy(eventId = "other")
        val chat = EventText.render(a)
        assertTrue(EventText.mentionsEvent(chat, a.eventId))
        assertFalse(EventText.mentionsEvent(chat, b.eventId))
        assertFalse(EventText.mentionsEvent("普通消息", a.eventId))
    }
}
