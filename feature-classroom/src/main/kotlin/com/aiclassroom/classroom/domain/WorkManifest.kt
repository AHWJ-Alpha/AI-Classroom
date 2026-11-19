package com.aiclassroom.classroom.domain

/**
 * 一个正式作品版本的固定清单。
 *
 * workVersion 之前只是个递增数字：用户三个月后打开「第 2 版」，
 * 看到的其实是当前正文 —— 因为没有任何地方记录那一版由哪些 revision 组成。
 *
 * 清单一旦写下就不再修改：后续重写产生**新的**清单，旧的仍可解析。
 */
data class WorkManifest(
    val projectId: ProjectId,
    val workVersion: Int,
    val specVersion: Int,
    val outlineVersion: Int,
    val snapshotId: String?,
    /** 按单元序号排列；只含当时已完成（READY）的单元。 */
    val entries: List<ManifestEntry>,
    val createdAt: EpochMillis,
) {

    val isValid: Boolean get() = workVersion > 0 && entries.isNotEmpty()

    fun revisionOf(unitId: UnitId): RevisionId? =
        entries.firstOrNull { it.unitId == unitId }?.revisionId

    /** 清单里的单元数；用于「这一版有几节」这类展示。 */
    val unitCount: Int get() = entries.size
}

data class ManifestEntry(
    val unitId: UnitId,
    val index: Int,
    val revisionId: RevisionId,
)

/** 从当前仓库状态拍一份清单。 */
object ManifestBuilder {

    fun build(
        project: ClassroomProject,
        specVersion: Int,
        outlineVersion: Int,
        snapshotId: String?,
        units: List<ClassroomUnit>,
        workVersion: Int,
        clock: () -> EpochMillis,
    ): WorkManifest = WorkManifest(
        projectId = project.id,
        workVersion = workVersion,
        specVersion = specVersion,
        outlineVersion = outlineVersion,
        snapshotId = snapshotId,
        entries = units
            .filter { it.status == UnitStatus.READY && it.currentRevisionId != null }
            .sortedBy { it.index }
            .map { ManifestEntry(it.id, it.index, it.currentRevisionId!!) },
        createdAt = clock(),
    )
}
