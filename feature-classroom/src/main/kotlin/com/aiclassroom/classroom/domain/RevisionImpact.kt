package com.aiclassroom.classroom.domain

import com.aiclassroom.classroom.repository.ClassroomStore

/**
 * 修订的影响传播。
 *
 * 改了第 2 章，后面依赖它的第 3、4 章就已经跟新的第 2 章对不上了；
 * 只把第 2 章标成「待复查」等于默许后面几章继续错着。
 *
 * 这里沿 dependsOn 反向走一遍闭包（传递下游），把已完成的都标成 STALE。
 * 不做「精确判断哪几节受影响」—— 判断错会留下「看起来没问题、其实已经不一致」的内容，
 * 第一版宁可多标，让用户自己决定重写哪些。
 */
object RevisionImpact {

    /** 返回被标记为 STALE 的单元数。changedUnit 自身不标记。 */
    fun markDownstreamStale(
        store: ClassroomStore,
        projectId: ProjectId,
        changedUnitId: UnitId,
    ): Int {
        val units = store.units(projectId)
        if (units.isEmpty()) return 0

        // 起点：直接依赖 changedUnitId 的单元，然后逐层展开。
        val affected = linkedSetOf<UnitId>()
        var frontier = setOf(changedUnitId)
        while (frontier.isNotEmpty()) {
            val next = mutableSetOf<UnitId>()
            for (unit in units) {
                if (unit.id in affected || unit.id == changedUnitId) continue
                if (unit.dependsOn.any { it in frontier }) {
                    affected += unit.id
                    next += unit.id
                }
            }
            frontier = next
        }

        var marked = 0
        for (unit in units) {
            if (unit.id !in affected) continue
            // 只有已经完成的才需要复查；还没生成的本来就会按新前文生成。
            if (unit.status != UnitStatus.READY) continue
            store.upsertUnit(unit.copy(status = UnitStatus.STALE))
            marked++
        }
        return marked
    }
}
