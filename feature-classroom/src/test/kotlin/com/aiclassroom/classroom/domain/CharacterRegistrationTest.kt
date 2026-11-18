package com.aiclassroom.classroom.domain

import com.aiclassroom.classroom.commit.InMemoryStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 快照人物必须真正注册进项目，状态校验才认识他们。 */
class CharacterRegistrationTest {

    private val now = 1_700_000_000_000L
    private val projectId = ProjectId("p1")
    private val cid = CharacterId("pc1")

    private fun store() = InMemoryStore().apply {
        createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.NOVEL,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
            ),
        )
    }

    private fun character(persona: String = "嘴硬心软") = SnapshotCharacter(
        projectCharacterId = cid, sourceCharacterKey = "林夏.md", name = "林夏",
        persona = persona, authorPrivate = "其实是卧底",
    )

    @Test
    fun `注册后项目里查得到这个角色`() {
        val s = store()
        assertEquals(1, CharacterRegistration.register(s, projectId, listOf(character())))
        val profiles = s.characters(projectId)
        assertEquals(1, profiles.size)
        assertEquals("林夏", profiles.first().name)
        assertEquals("嘴硬心软", profiles.first().corePersonality)
        assertEquals("其实是卧底", profiles.first().authorPrivate)
    }

    @Test
    fun `重复注册不会产生第二个角色`() {
        val s = store()
        CharacterRegistration.register(s, projectId, listOf(character()))
        CharacterRegistration.register(s, projectId, listOf(character()))
        assertEquals(1, s.characters(projectId).size)
    }

    @Test
    fun `人格变化时更新同一角色而不是新建`() {
        val s = store()
        CharacterRegistration.register(s, projectId, listOf(character("嘴硬心软")))
        CharacterRegistration.register(s, projectId, listOf(character("沉默寡言")))
        val profiles = s.characters(projectId)
        assertEquals(1, profiles.size)
        assertEquals("沉默寡言", profiles.first().corePersonality)
        assertTrue(profiles.first().version >= 2)
    }
}
