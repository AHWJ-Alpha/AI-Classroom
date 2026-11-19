package com.aiclassroom.classroom.domain

import com.aiclassroom.classroom.repository.ClassroomStore

/**
 * 按版本读作品。
 *
 * 清单写下来之后必须真的被用上，否则「第 N 版」仍是空话：
 * 阅读、讨论、旧卡片都应该走这里，而不是直接拿 unit.currentRevisionId。
 */
object ManifestResolver {

    /** 该项目已发布的所有版本号，从新到旧。 */
    fun versions(store: ClassroomStore, projectId: ProjectId): List<Int> {
        val latest = store.latestManifest(projectId)?.workVersion ?: 0
        if (latest <= 0) return emptyList()
        return (latest downTo 1).filter { store.manifest(projectId, it) != null }
    }

    fun latestVersion(store: ClassroomStore, projectId: ProjectId): Int =
        store.latestManifest(projectId)?.workVersion ?: 0

    /**
     * 取某一版某一节的 revision。
     *
     * 有清单：严格按清单取，取不到就返回 null（不拿当前正文冒充历史版本）。
     * 没有清单（旧项目）：退回当前正式版本，并在结果里标注 legacy。
     */
    fun revisionAt(
        store: ClassroomStore,
        projectId: ProjectId,
        unitId: UnitId,
        workVersion: Int?,
    ): Resolution {
        val target = workVersion ?: latestVersion(store, projectId)
        if (target <= 0) {
            val current = store.officialRevision(unitId)
            return if (current == null) Resolution.Missing("还没有正式版本")
            else Resolution.Legacy(current)
        }

        val manifest = store.manifest(projectId, target)
            ?: return Resolution.Missing("第 " + target + " 版没有版本清单")
        val revisionId = manifest.revisionOf(unitId)
            ?: return Resolution.Missing("第 " + target + " 版没有这一节")
        val revision = store.revision(revisionId)
            ?: return Resolution.Missing("第 " + target + " 版的正文已不可用")
        return Resolution.Found(revision, manifest)
    }

    sealed interface Resolution {
        data class Found(val revision: UnitRevision, val manifest: WorkManifest) : Resolution
        /** 旧项目没有清单：退回当前正式版本，界面需要说明。 */
        data class Legacy(val revision: UnitRevision) : Resolution
        data class Missing(val reason: String) : Resolution
    }
}
