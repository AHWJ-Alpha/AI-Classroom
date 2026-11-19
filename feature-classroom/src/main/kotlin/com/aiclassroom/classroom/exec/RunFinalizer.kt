package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.event.EventOutbox
import com.aiclassroom.classroom.repository.ClassroomStore

/**
 * 任务收尾：把「写终态」和「入队结果事件」放进**同一次提交**。
 *
 * 之前是引擎先写 run 状态与项目状态，管理器在拿到结果后再单独入队事件。
 * 崩在这两步之间：run 已经是 COMPLETED，恢复逻辑看不到 RUNNING，
 * 事件永远不会被创建 —— 用户永远收不到那张结果卡。
 */
object RunFinalizer {

    /**
     * @return 新入队的事件；来源对话缺失或事件已存在时返回 null（幂等）。
     */
    fun finish(
        store: ClassroomStore,
        project: ClassroomProject,
        run: GenerationRun?,
        runStatus: RunStatus,
        projectLifecycle: ProjectLifecycle,
        type: EventType,
        summary: String,
        detail: String = "",
        committedUnits: Int? = null,
        clock: () -> EpochMillis,
        /** 执行者与令牌；给出时只有仍持有执行权才允许写终态。 */
        owner: String? = null,
        fenceToken: Long? = null,
    ): ClassroomEvent? = store.transaction {
        if (owner != null && fenceToken != null && run != null) {
            val check = RunLease.validate(store, run.id, owner, fenceToken, clock)
            if (check is RunLease.Validation.Invalid) {
                // 旧执行者不能把新任务的终态盖掉。
                return@transaction null
            }
        }
        run?.let {
            val patch = it.copy(
                status = runStatus,
                updatedAt = clock(),
                completedUnits = committedUnits ?: it.completedUnits,
            )
            store.saveRun(patch)
        }
        store.updateProject(project.copy(lifecycle = projectLifecycle, updatedAt = clock()))

        val conversationId = project.sourceConversationId
        if (conversationId.isNullOrBlank() || !type.isTerminal) {
            null
        } else {
            val event = ClassroomEvent(
                eventId = EventOutbox.eventIdOf(run?.id, project.id, type),
                sourceConversationId = conversationId,
                projectId = project.id,
                projectTitle = project.title,
                runId = run?.id,
                workVersion = project.workVersion,
                type = type,
                summary = summary,
                detail = detail,
                createdAt = clock(),
            )
            if (EventOutbox.enqueue(store, event)) event else null
        }
    }
}
