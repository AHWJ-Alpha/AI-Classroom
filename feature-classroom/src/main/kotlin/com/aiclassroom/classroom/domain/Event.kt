package com.aiclassroom.classroom.domain

/**
 * 课堂任务事件：从项目回写到来源对话的那条链路。
 *
 * 为什么要有独立的 outbox 而不是「跑完直接往聊天里塞一条」：
 * - 写入聊天与写入项目是两处存储，不可能有同一个原子事务；
 * - 崩溃在「项目已提交、消息还没写」之间时，至少要能重试；
 * - 重试不能产生重复结果卡，所以事件必须带稳定 id 并记录投递状态。
 */
data class ClassroomEvent(
    /** 稳定 id：重放时靠它去重。 */
    val eventId: String,
    val sourceConversationId: String,
    val projectId: ProjectId,
    val projectTitle: String,
    val runId: RunId? = null,
    val workVersion: Int = 0,
    val type: EventType,
    /** 面向读者的简报。**不得包含作者私密内容**。 */
    val summary: String = "",
    /** 失败/需要处理时的原因，便于用户决定下一步。 */
    val detail: String = "",
    val createdAt: EpochMillis,
    val delivery: DeliveryState = DeliveryState.PENDING,
)

enum class EventType {
    STARTED,
    COMPLETED,
    PAUSED,
    CANCELLED,
    FAILED,
    NEEDS_ATTENTION,
    ;

    /** 只有这类事件值得在对话里留一张结果卡。 */
    val isTerminal: Boolean
        get() = this == COMPLETED || this == CANCELLED || this == FAILED || this == NEEDS_ATTENTION
}

enum class DeliveryState { PENDING, DELIVERED }

/**
 * 事件的文本形态。
 *
 * 现在先产出纯文本（带可解析标记）；点击跳转与卡片 UI 是后续一步，
 * 但 id/version 已经写进文本，不会因为以后换 UI 而失效。
 */
object EventText {
    const val MARKER = "[课堂任务]"

    fun render(event: ClassroomEvent): String = buildString {
        append(MARKER)
        append("《").append(event.projectTitle).append("》")
        when (event.type) {
            EventType.STARTED -> append("开始生成")
            EventType.COMPLETED -> append("已完成，正式版本已保存")
            EventType.PAUSED -> append("已暂停")
            EventType.CANCELLED -> append("已取消，已完成内容保留")
            EventType.FAILED -> append("生成失败")
            EventType.NEEDS_ATTENTION -> append("需要处理")
        }
        append("（正式版本 ").append(event.workVersion).append("）")
        if (event.summary.isNotBlank()) append("：").append(event.summary)
        if (event.detail.isNotBlank()) append(" — ").append(event.detail)
        // 可解析标记：以后换成真卡片时靠它定位项目与版本。
        append("  {project=").append(event.projectId.value)
        append(", version=").append(event.workVersion)
        append(", event=").append(event.eventId).append("}")
    }

    fun isEventMessage(text: String): Boolean = text.startsWith(MARKER)

    /** 从标记里取回项目 id；解析不出来返回 null，界面就退化成普通文本。 */
    fun parseProjectId(text: String): String? {
        if (!isEventMessage(text)) return null
        val match = Regex("""\{project=([^,]+),""").find(text) ?: return null
        return match.groupValues[1].trim().ifBlank { null }
    }

    fun parseVersion(text: String): Int? {
        if (!isEventMessage(text)) return null
        val match = Regex("""version=(\d+)""").find(text) ?: return null
        return match.groupValues[1].toIntOrNull()
    }
}
