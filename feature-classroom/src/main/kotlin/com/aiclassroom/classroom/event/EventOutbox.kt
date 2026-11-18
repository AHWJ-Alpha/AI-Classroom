package com.aiclassroom.classroom.event

import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.repository.ClassroomStore

/**
 * 事件 outbox。
 *
 * 唯一的写入约定：**先落库，再投递**。
 * 投递由宿主（打开着的那个对话）完成，成功后把事件标成 DELIVERED；
 * 中途崩溃时事件仍是 PENDING，下次启动继续投，且因 eventId 固定不会重复。
 */
object EventOutbox {

    /** 入队。同一 eventId 只保留一条 —— 这是幂等的第一道闸。 */
    fun enqueue(store: ClassroomStore, event: ClassroomEvent): Boolean {
        if (store.event(event.eventId) != null) return false
        store.saveEvent(event)
        return true
    }

    fun markDelivered(store: ClassroomStore, eventId: String) {
        val event = store.event(eventId) ?: return
        if (event.delivery == DeliveryState.DELIVERED) return
        store.saveEvent(event.copy(delivery = DeliveryState.DELIVERED))
    }

    /** 该对话里还没投递的事件，按时间先后。 */
    fun pending(store: ClassroomStore, sourceConversationId: String): List<ClassroomEvent> =
        store.pendingEvents(sourceConversationId)

    /**
     * 稳定的 eventId：同一个 run 的同类终态只对应一张卡。
     *
     * 用它而不是随机 id，是为了「重复回调 / 重启重放」不会多出第二条结果。
     */
    fun eventIdOf(runId: RunId?, projectId: ProjectId, type: EventType): String =
        (runId?.value ?: "no-run") + "|" + projectId.value + "|" + type.name
}
