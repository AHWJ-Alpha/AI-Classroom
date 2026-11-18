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
                store.saveRun(
                    run.copy(
                        status = RunStatus.PAUSED,
                        error = "应用被关闭后已暂停，可手动继续",
                        updatedAt = now,
                    ),
                )
                paused++
            }

            for (unit in store.units(project.id)) {
                if (unit.status in STUCK_UNIT) {
                    store.upsertUnit(unit.copy(status = UnitStatus.PLANNED))
                    reset++
                }
            }
        }
        return Report(paused, reset)
    }
}
