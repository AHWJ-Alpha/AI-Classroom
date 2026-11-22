package com.aiclassroom.app.classroom

import android.content.Context
import com.aiclassroom.classroom.domain.ContextSnapshot
import com.aiclassroom.classroom.domain.ProjectId

/**
 * 自动同步来源设定。
 *
 * 以前靠界面上的「同步来源设定」按钮：用户不点就永远没有快照，
 * 于是人格是空的、资料检索也没有内容、角色只能靠兜底名字。
 * 现在每次开始生成前自动做一次，用户不需要知道这个动作存在。
 */
object ClassroomSourceSync {

    fun sync(context: Context, projectId: ProjectId) {
        val resolver = ClassroomSourceResolver(
            conversations = { com.aiclassroom.app.readConversationsForBackground(context) },
            store = ClassroomServiceProvider.store(context),
        )
        val source = resolver.conversationOf(projectId) ?: return
        runCatching {
            ClassroomServiceProvider.service(context).syncSource(
                projectId = projectId,
                mentorName = source.config.mentorName,
                mentorPrompt = source.config.mentorPrompt,
                userAlias = source.config.userAlias,
                viewMode = if (source.config.activeCharacterId.isBlank()) "author" else "character",
                activeCharacterId = source.config.activeCharacterId.takeIf { it.isNotBlank() },
                publicWorldBook = source.config.publicWorldBook,
                authorWorldBook = source.config.worldBook,
                characters = source.files
                    .filter { it.kind == "character_card" }
                    .map { f ->
                        val key = source.id + "|" + f.name
                        val id = java.util.UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).toString()
                        val split = com.aiclassroom.classroom.domain.CardSections.split(f.content)
                        com.aiclassroom.classroom.domain.SnapshotCharacter(
                            projectCharacterId = com.aiclassroom.classroom.domain.CharacterId(id),
                            sourceCharacterKey = f.name,
                            name = f.name.substringBeforeLast('.').ifBlank { f.name },
                            persona = split.persona,
                            authorPrivate = split.authorPrivate,
                        )
                    },
                materials = source.files.filter { it.kind != "character_card" }.map { f ->
                    com.aiclassroom.classroom.domain.SnapshotMaterial(
                        name = f.name,
                        type = f.type,
                        content = f.content,
                    )
                },
                facts = source.memoryCards.map { c ->
                    com.aiclassroom.classroom.domain.SnapshotFact(
                        content = c.title + "：" + c.summary,
                        visibility = com.aiclassroom.classroom.domain.VisibilityRules.fromLegacy(c.visibility),
                        knownBy = c.knownBy.toSet(),
                        unknownBy = c.unknownBy.toSet(),
                        evidence = c.evidence,
                    )
                }.take(40),
                historyThroughIndex = source.messages.lastIndex,
                sourceConversationId = source.id,
            )
        }
    }
}
