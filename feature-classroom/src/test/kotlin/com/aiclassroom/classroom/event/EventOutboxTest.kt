package com.aiclassroom.classroom.event

import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventOutboxTest {

    private val now = 1_700_000_000_000L
    private val projectId = ProjectId("p1")
    private val runId = RunId("r1")

    private fun event(
        type: EventType = EventType.COMPLETED,
        conv: String = "conv-a",
        id: String = EventOutbox.eventIdOf(runId, projectId, type),
    ) = ClassroomEvent(
        eventId = id, sourceConversationId = conv, projectId = projectId,
        projectTitle = "Python 面向对象", runId = runId, workVersion = 3,
        type = type, summary = "共 8 节", createdAt = now,
    )

    @Test
    fun `同一个事件入队两次只有一条`() {
        val store = InMemoryStore()
        assertTrue(EventOutbox.enqueue(store, event()))
        assertFalse(EventOutbox.enqueue(store, event()))
        assertEquals(1, EventOutbox.pending(store, "conv-a").size)
    }

    @Test
    fun `只取该对话未投递的事件`() {
        val store = InMemoryStore()
        EventOutbox.enqueue(store, event(conv = "conv-a"))
        EventOutbox.enqueue(store, event(type = EventType.FAILED, conv = "conv-b"))
        assertEquals(1, EventOutbox.pending(store, "conv-a").size)
        assertEquals(EventType.COMPLETED, EventOutbox.pending(store, "conv-a").first().type)
    }

    @Test
    fun `标记投递后不再出现在待投递里`() {
        val store = InMemoryStore()
        val e = event()
        EventOutbox.enqueue(store, e)
        EventOutbox.markDelivered(store, e.eventId)
        assertTrue(EventOutbox.pending(store, "conv-a").isEmpty())
        assertEquals(DeliveryState.DELIVERED, store.event(e.eventId)!!.delivery)
    }

    @Test
    fun `不同 run 的完成事件各自保留`() {
        val store = InMemoryStore()
        EventOutbox.enqueue(store, event())
        EventOutbox.enqueue(store, event(id = EventOutbox.eventIdOf(RunId("r2"), projectId, EventType.COMPLETED)))
        assertEquals(2, EventOutbox.pending(store, "conv-a").size)
    }

    @Test
    fun `文本里带可解析的项目与版本标记`() {
        val text = EventText.render(event())
        assertTrue(EventText.isEventMessage(text))
        assertTrue(text.contains("project=p1"))
        assertTrue(text.contains("version=3"))
        assertTrue(text.contains("共 8 节"))
    }

    @Test
    fun `只有终态事件值得留卡`() {
        assertTrue(EventType.COMPLETED.isTerminal)
        assertFalse(EventType.STARTED.isTerminal)
    }
}
