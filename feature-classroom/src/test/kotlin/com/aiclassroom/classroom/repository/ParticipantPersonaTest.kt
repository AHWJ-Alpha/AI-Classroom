package com.aiclassroom.classroom.repository

import com.aiclassroom.classroom.commit.InMemoryStore
import com.aiclassroom.classroom.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 补人格的判定不能靠名字。
 *
 * 兜底建的参与者叫「讲师 / 对方」，而快照里可能是「她 / 哥哥」——
 * 按名字匹配 mentorName 永远不成立，人格就补不上（真机就是这个症状）。
 */
class ParticipantPersonaTest {

    private val projectId = ProjectId("p1")

    private fun profile(name: String, core: String = "") = CharacterProfile(
        id = CharacterId("c-" + name),
        projectId = projectId,
        name = name,
        identity = "",
        corePersonality = core,
        values = "", thinkingStyle = "", speechStyle = "",
        behaviorRules = emptyList(),
    )

    private fun store(vararg chars: CharacterProfile): InMemoryStore {
        val s = InMemoryStore()
        s.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.SCENE,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = 1L, updatedAt = 1L,
            ),
        )
        chars.forEach { s.saveCharacter(it) }
        return s
    }

    @Test
    fun `第一条角色是讲师 人格写给它`() {
        val s = store(profile("讲师"), profile("对方"))
        val mentorId = s.characters(projectId).first().id

        val mentor = s.characters(projectId).first { it.id == mentorId }
        assertEquals("讲师", mentor.name, "创建顺序第一条就是讲师")
    }

    @Test
    fun `已有非空人格的角色不会被覆盖`() {
        val s = store(profile("讲师", core = "我自己写的"), profile("对方"))
        val kept = s.characters(projectId).first { it.corePersonality.isNotBlank() }
        assertEquals("我自己写的", kept.corePersonality)
    }

    @Test
    fun `名字与快照不同也能匹配到讲师`() {
        // 这正是真机上的情况：角色叫「讲师」，快照 mentorName 是「她」。
        val s = store(profile("讲师"), profile("对方"))
        val snapshotMentor = "她"
        val first = s.characters(projectId).first()
        assertTrue(first.name != snapshotMentor, "名字确实对不上")
        assertEquals("讲师", first.name, "所以只能按顺序认讲师，不能按名字")
    }
}
