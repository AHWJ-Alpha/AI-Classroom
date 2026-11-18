package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.repository.ClassroomStore

/**
 * 把「待复查」的单元放回生成队列。
 *
 * STALE 的含义是「约束变了，之前的内容不再对应当前设定」。
 * 重新生成时：
 * - 单元状态回到 PLANNED，交给执行器重新走一遍生成→审查→提交；
 * - 旧版本不会被删除，提交时会标成 supersededBy，历史仍可追溯；
 * - 用户改过的方向（directive）保留 —— 那正是要重写的原因。
 */
object Regeneration {

    data class Reset(val unitIds: List<UnitId>) {
        val count: Int get() = unitIds.size
        val any: Boolean get() = unitIds.isNotEmpty()
    }

    /** 把该项目所有 STALE 单元放回 PLANNED。已 READY / 生成中的一律不动。 */
    fun resetStale(store: ClassroomStore, projectId: ProjectId): Reset {
        val stale = store.units(projectId).filter { it.status == UnitStatus.STALE }
        for (unit in stale) {
            store.upsertUnit(unit.copy(status = UnitStatus.PLANNED))
        }
        return Reset(stale.map { it.id })
    }

    /**
     * 来源设定变化后，把已完成的单元保守标记为 STALE。
     *
     * 为什么不是精确到「哪几节受影响」：那需要逐单元比对依赖与事实，
     * 一旦判断错就会留下「看起来没问题、其实已经不一致」的内容。
     * 第一版宁可多标记，让用户自己决定重写哪些。
     */
    fun markReadyStale(store: ClassroomStore, projectId: ProjectId): Int {
        val ready = store.units(projectId).filter { it.status == UnitStatus.READY }
        for (unit in ready) {
            store.upsertUnit(unit.copy(status = UnitStatus.STALE))
        }
        return ready.size
    }

    /** 单个单元放回队列；不是 STALE 就返回 false。 */
    fun resetUnit(store: ClassroomStore, unitId: UnitId): Boolean {
        val unit = store.unit(unitId) ?: return false
        if (unit.status != UnitStatus.STALE) return false
        store.upsertUnit(unit.copy(status = UnitStatus.PLANNED))
        return true
    }
}
