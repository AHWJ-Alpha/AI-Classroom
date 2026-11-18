package com.aiclassroom.classroom.domain

import com.aiclassroom.classroom.repository.ClassroomStore

/**
 * 把快照里的人物注册进项目。
 *
 * 之前提示词里有角色、但 store.characters(projectId) 是空的：
 * 于是状态校验那一侧根本不认识这些人，人物边界只剩一句“请勿泄露”。
 */
object CharacterRegistration {

    fun register(
        store: ClassroomStore,
        projectId: ProjectId,
        characters: List<SnapshotCharacter>,
    ): Int {
        if (characters.isEmpty()) return 0
        val existing = store.characters(projectId).associateBy { it.id }
        var changed = 0
        for (c in characters) {
            val before = existing[c.projectCharacterId]
            val profile = CharacterProfile(
                id = c.projectCharacterId,
                projectId = projectId,
                name = c.name,
                identity = before?.identity.orEmpty(),
                corePersonality = c.persona,
                values = before?.values.orEmpty(),
                thinkingStyle = before?.thinkingStyle.orEmpty(),
                speechStyle = c.speechStyle,
                behaviorRules = before?.behaviorRules ?: emptyList(),
                positiveExamples = before?.positiveExamples ?: emptyList(),
                negativeExamples = before?.negativeExamples ?: emptyList(),
                authorPrivate = c.authorPrivate,
                version = (before?.version ?: 0) + 1,
            )
            if (before == profile) continue
            store.saveCharacter(profile)
            changed++
        }
        return changed
    }
}
