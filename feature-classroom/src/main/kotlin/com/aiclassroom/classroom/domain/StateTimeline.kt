package com.aiclassroom.classroom.domain

import com.aiclassroom.classroom.repository.ClassroomStore

/**
 * 某一章提交时的角色状态快照。
 *
 * 没有它，重写第 2 章时模型看到的是第 5 章结束时的状态（"未来"），
 * 于是第 2 章里的人物会用后面的关系、情绪和目标说话。
 */
data class UnitStateSnapshot(
    val projectId: ProjectId,
    val unitId: UnitId,
    val unitIndex: Int,
    val states: Map<CharacterId, CharacterState>,
    val createdAt: EpochMillis,
)

object StateTimeline {

    /**
     * 第 [unitIndex] 章**之前**的状态：取序号小于它的最后一个快照。
     *
     * 没有任何更早的快照时返回空 map —— 说明这是第一章，
     * 调用方应当把它当作"没有前置状态"，而不是回退到当前状态。
     */
    fun statesBefore(
        store: ClassroomStore,
        projectId: ProjectId,
        unitIndex: Int,
    ): Map<CharacterId, CharacterState> {
        val snapshot = store.stateSnapshots(projectId)
            .filter { it.unitIndex < unitIndex }
            .maxByOrNull { it.unitIndex }
        return snapshot?.states ?: emptyMap()
    }

    /** 该项目已提交到的最大章节序号；没有提交过返回 0。 */
    fun maxCommittedIndex(store: ClassroomStore, projectId: ProjectId): Int =
        store.stateSnapshots(projectId).maxOfOrNull { it.unitIndex } ?: 0

    /**
     * 这一章是不是"回头重写"：它前面已经有提交过且序号更大的章节。
     */
    fun isRewrite(store: ClassroomStore, projectId: ProjectId, unitIndex: Int): Boolean {
        val snapshots = store.stateSnapshots(projectId)
        val own = snapshots.any { it.unitIndex == unitIndex }
        val hasLater = snapshots.any { it.unitIndex > unitIndex }
        return own || hasLater
    }
}
