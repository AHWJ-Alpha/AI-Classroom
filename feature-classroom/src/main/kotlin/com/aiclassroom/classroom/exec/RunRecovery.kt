package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.repository.ClassroomStore

/**
 * 进程重建后的恢复。
 *
 * 应用被杀掉时，数据库里会留下「正在生成」的任务和单元。如果不处理，
 * 界面会永远停在「生成中」，用户既不能继续也不能重来。
 *
 * 恢复策略刻意保守：
 * - 任务一律回到 PAUSED（不是自动继续跑）—— 用户开机时不该被偷偷计费；
 * - 未提交的单元回到 PLANNED，因为提交是事务性的，正式内容不受影响；
 * - 已 READY 的单元一律不碰。
 */
object RunRecovery {

    data class Report(val pausedRuns: Int, val resetUnits: Int) {
        val changed: Boolean get() = pausedRuns > 0 || resetUnits > 0
    }

    private val STUCK_RUN = setOf(RunStatus.RUNNING, RunStatus.PAUSE_REQUESTED)
    private val STUCK_UNIT = setOf(
        UnitStatus.GENERATING,
        UnitStatus.REVIEWING,
        UnitStatus.REVISING,
        // DRAFT_READY 也要算：它在「草稿已生成、还没提交」这个瞬间被写进库，
        // 进程一断草稿就没了，而 nextReadyUnit 只挑 PLANNED ——
        // 不清掉的话这一节永远不会被重新选中，后面的依赖链全卡死（实测发生过）。
        UnitStatus.DRAFT_READY,
    )

    fun recover(store: ClassroomStore, now: EpochMillis): Report {
        var paused = 0
        var reset = 0

        for (project in store.projects()) {
            // PAUSE_REQUESTED 的任务 activeRun 查不到，所以按项目取全部任务再筛。
            for (run in store.runs(project.id).filter { it.status in STUCK_RUN }) {
                // 有有效执行者（租约未过期）的任务正在别人手里跑：
                // 打开课堂页就把它标成暂停，会把后台正在生成的项目打断。
                if (run.owner != null && run.leaseUntil > now) continue
                store.saveRun(
                    run.copy(
                        status = RunStatus.PAUSED,
                        error = "应用被关闭后已暂停，可手动继续",
                        updatedAt = now,
                    ),
                )
                paused++
            }

            // 该项目还有活跃执行者时，连单元也不能重置 ——
            // 把它的 DRAFT_READY 打回 PLANNED，正在跑的 Worker 就白干了一节。
            val hasActiveExecutor = store.runs(project.id)
                .any { it.owner != null && it.leaseUntil > now }
            if (hasActiveExecutor) continue

            for (unit in store.units(project.id)) {
                val draft = store.draft(project.id, unit.id)
                val hasDraft = draft?.segments?.isNotEmpty() == true && draft.phase != DraftPhase.COMMITTED
                // 情况一：状态卡在「进行中」，必须清掉，否则 nextReadyUnit 永远选不中它。
                if (unit.status in STUCK_UNIT) {
                    // 有草稿就退回「草稿已组装」，不是 PLANNED。
                    // 草稿本身没丢（分段都在库里），标成 PLANNED 只是让界面显示「待生成」，
                    // 用户看到的就是「进度没了」；退回 DRAFT_READY 会如实显示已写多少字，
                    // 下次继续时也能从断点接着写。
                    store.upsertUnit(unit.copy(status = if (hasDraft) UnitStatus.DRAFT_READY else UnitStatus.PLANNED))
                    reset++
                    continue
                }
                // 情况二：状态是 PLANNED/STALE，但草稿里已经写了几段。
                // 这是历史遗留的「状态与草稿不一致」：界面显示「待生成」，
                // 用户以为进度没了。补一次修正，让它如实显示。
                if (hasDraft && unit.status in setOf(UnitStatus.PLANNED, UnitStatus.STALE)) {
                    store.upsertUnit(unit.copy(status = UnitStatus.DRAFT_READY))
                    reset++
                }
            }
        }
        return Report(paused, reset)
    }

    /**
     * 启动新任务前清理失效执行者。
     *
     * 与 recoverProject 的区别：这里会**清掉 owner 与 leaseUntil**。
     * 只标 PAUSED 而不清租约字段的话，那条 run 仍然「占着」项目，
     * 新任务 acquire 会一直拿到 Busy —— 实测出现过三个 RUNNING 互锁、
     * 界面点了没反应。
     *
     * 仍在有效期内的租约不动：那是真的有人在跑。
     */
    fun cleanupStale(
        store: ClassroomStore,
        projectId: ProjectId,
        now: EpochMillis,
    ): Report = store.transaction {
        var paused = 0
        var reset = 0
        val runs = store.runs(projectId)
        val hasLiveExecutor = runs.any { it.owner != null && it.leaseUntil > now }
        for (run in runs.filter { it.status in STUCK_RUN }) {
            if (run.owner != null && run.leaseUntil > now) continue
            store.saveRun(
                run.copy(
                    status = RunStatus.PAUSED,
                    error = "上次执行已中断，可继续",
                    owner = null,
                    leaseUntil = 0L,
                    // 令牌 +1：这次清理本身就是一次「接管」，
                    // 死执行者手里的旧令牌必须立刻失效，不能靠它继续提交。
                    fencingToken = run.fencingToken + 1,
                    updatedAt = now,
                ),
            )
            paused++
        }
        if (!hasLiveExecutor) {
            for (unit in store.units(projectId)) {
                if (unit.status in STUCK_UNIT) {
                    store.upsertUnit(unit.copy(status = UnitStatus.PLANNED))
                    reset++
                }
            }
        }
        Report(paused, reset)
    }

    /** 只恢复一个项目，供新的持久执行者启动前使用。 */
    fun recoverProject(store: ClassroomStore, projectId: ProjectId, now: EpochMillis): Report {
        var paused = 0
        var reset = 0
        for (run in store.runs(projectId).filter { it.status in STUCK_RUN }) {
            // 有尚未过期的执行租约时，说明 Worker 仍可能活着，不能抢占它。
            if (run.owner != null && run.leaseUntil > now) continue
            store.saveRun(run.copy(status = RunStatus.PAUSED, error = "上次执行已中断，可继续", updatedAt = now))
            paused++
        }
        for (unit in store.units(projectId)) {
            if (unit.status in STUCK_UNIT) {
                store.upsertUnit(unit.copy(status = UnitStatus.PLANNED))
                reset++
            }
        }
        return Report(paused, reset)
    }

    /**
     * 把仍然处于运行态的任务标记为已取消。
     *
     * 用在「用户在网络请求途中取消」这条路径上：协程被直接取消，
     * 引擎的循环来不及写状态，落盘的 run 会一直停在 RUNNING。
     */
    fun markCancelled(store: ClassroomStore, projectId: ProjectId, clock: () -> EpochMillis): Int {
        val active = store.runs(projectId).filter { it.status == RunStatus.RUNNING }
        for (run in active) {
            store.saveRun(run.copy(status = RunStatus.CANCELLED, updatedAt = clock()))
        }
        return active.size
    }

    /**
     * 把仍处于运行态的任务标成暂停。
     *
     * 暂停的实现是「停掉 Worker + 标记 PAUSED」：Worker 被 cancelUniqueWork 停掉后
     * 协程直接结束，引擎来不及写状态，这里补上，否则重启后一直显示运行中。
     */
    fun markPaused(store: ClassroomStore, projectId: ProjectId, clock: () -> EpochMillis): Int {
        return store.transaction {
            val active = store.runs(projectId).filter { it.status in setOf(RunStatus.RUNNING, RunStatus.QUEUED) }
            for (run in active) {
                store.saveRun(run.copy(status = RunStatus.PAUSED, owner = null, leaseUntil = 0, updatedAt = clock()))
            }
            active.size
        }
    }
}
