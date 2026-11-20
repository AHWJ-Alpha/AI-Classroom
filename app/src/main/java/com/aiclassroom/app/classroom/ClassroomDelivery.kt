package com.aiclassroom.app.classroom

import com.aiclassroom.classroom.domain.ClassroomEvent
import com.aiclassroom.classroom.event.EventOutbox
import com.aiclassroom.classroom.repository.ClassroomStore

/**
 * 结果卡投递。
 *
 * 之前投递写在 MainActivity 的 LaunchedEffect 里：进程不在就不投，
 * 用户要等下次打开应用才看到完成卡。现在逻辑在这里，
 * Worker 结束时、应用启动时都调用同一个入口。
 */
object ClassroomDelivery {

    /**
     * 纯逻辑：幂等地把待投递事件写进各自对话。
     *
     * [writer] 负责「追加消息并持久化」，返回是否真的写成功；
     * 只有成功才标记 DELIVERED —— 反过来的话崩在中间会丢卡。
     *
     * @return 本次成功投递的条数。
     */
    fun deliver(
        store: ClassroomStore,
        conversations: () -> List<String>,
        writer: (conversationId: String, event: ClassroomEvent) -> Boolean,
    ): Int {
        var delivered = 0
        for (conversationId in conversations()) {
            for (event in EventOutbox.pending(store, conversationId)) {
                if (writer(conversationId, event)) {
                    EventOutbox.markDelivered(store, event.eventId)
                    delivered++
                }
            }
        }
        return delivered
    }

    /** Android 侧入口：读对话、写消息、确认投递。可重复调用。 */
    fun deliverPending(context: android.content.Context): Int =
        runCatching {
            val svc = ClassroomServiceProvider.service(context)
            deliver(
                store = svc.store,
                conversations = {
                    com.aiclassroom.app.readConversationsForBackground(context).map { it.id }
                },
                writer = { conversationId, event ->
                    com.aiclassroom.app.appendClassroomEventMessage(
                        context = context,
                        conversationId = conversationId,
                        eventId = event.eventId,
                        text = com.aiclassroom.classroom.domain.EventText.render(event),
                        // 必须带结构化附件：防覆盖合并只保留带附件的卡片，
                        // 不带的话后台结果会被界面保存抹掉（实测遗漏点）。
                        ref = com.aiclassroom.app.model.ClassroomEventRef(
                            eventId = event.eventId,
                            projectId = event.projectId.value,
                            runId = event.runId?.value,
                            workVersion = event.workVersion,
                            kind = event.type.name,
                        ),
                    )
                },
            )
        }.getOrDefault(0)
}
