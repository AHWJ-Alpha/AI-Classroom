package com.aiclassroom.classroom.domain

/**
 * 来源资料与快照的差异。
 *
 * 用途很具体：用户改了资料或角色卡之后，要能**先看清楚变什么**，
 * 再决定是否同步；不能静默把正在跑的设定换掉。
 */
object SnapshotSync {

    data class MaterialChange(
        val name: String,
        /** added / changed / removed */
        val kind: String,
    ) {
        val label: String
            get() = when (kind) {
                "added" -> "新增：" + name
                "removed" -> "移除：" + name
                else -> "内容有变：" + name
            }
    }

    data class Diff(val changes: List<MaterialChange>) {
        val empty: Boolean get() = changes.isEmpty()
        val summary: String get() = changes.joinToString("；") { it.label }
    }

    /** 只比对资料；人格与世界书的变化也走同一条（用固定名字当作一个「资料项」）。 */
    fun diff(
        snapshotMaterials: List<SnapshotMaterial>,
        currentMaterials: List<SnapshotMaterial>,
    ): Diff {
        val old = snapshotMaterials.associateBy { it.name }
        val now = currentMaterials.associateBy { it.name }
        val changes = mutableListOf<MaterialChange>()

        for ((name, m) in now) {
            val before = old[name]
            when {
                before == null -> changes += MaterialChange(name, "added")
                checksumOf(before) != checksumOf(m) -> changes += MaterialChange(name, "changed")
            }
        }
        for (name in old.keys) {
            if (name !in now) changes += MaterialChange(name, "removed")
        }
        return Diff(changes)
    }

    /** 内容指纹：用长度 + 哈希，够判断「变没变」，不冒充密码学校验。 */
    fun checksumOf(material: SnapshotMaterial): String {
        if (material.checksum.isNotBlank()) return material.checksum
        val body = material.content
        return body.length.toString() + ":" + body.hashCode().toString()
    }
}
