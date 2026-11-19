package com.aiclassroom.classroom.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * store facts 必须按视角过滤。
 *
 * 之前它完全不过滤直接进提示词：快照事实过滤了，store 事实却全给了角色视角，
 * 等于从另一条路把作者私密内容漏出去。
 */
class FactsForViewTest {

    private val now = 1_700_000_000_000L
    private val pid = ProjectId("p1")
    private val hero = CharacterId("pc-hero")
    private val other = CharacterId("pc-other")

    private fun fact(content: String, visibility: Visibility, knownBy: Set<CharacterId> = emptySet()) =
        FactRevision(
            factId = FactId(content), projectId = pid, version = 1, content = content,
            kind = FactKind.FACT, sourceUnitId = null, sourceRevisionId = null,
            visibility = visibility, knownBy = knownBy, evidence = "第1节",
        )

    private fun snapshot(viewMode: String) = ContextSnapshot(
        id = SnapshotId("s"), projectId = pid, version = 1, createdAt = now,
        viewMode = viewMode, activeCharacterId = "主角.md",
        characters = listOf(
            SnapshotCharacter(
                projectCharacterId = hero, sourceCharacterKey = "主角.md", name = "主角",
            ),
        ),
    )

    @Test
    fun `作者视角拿到全部事实`() {
        val facts = listOf(
            fact("公开事实", Visibility.PUBLIC),
            fact("主角的秘密", Visibility.RESTRICTED, setOf(hero)),
            fact("作者的秘密", Visibility.AUTHOR_ONLY),
        )
        assertEquals(3, FactsForView.factsFor(snapshot("author"), facts).size)
    }

    @Test
    fun `角色视角只拿到自己知道的与公开的`() {
        val facts = listOf(
            fact("公开事实", Visibility.PUBLIC),
            fact("主角的秘密", Visibility.RESTRICTED, setOf(hero)),
            fact("别人的秘密", Visibility.RESTRICTED, setOf(other)),
            fact("作者的秘密", Visibility.AUTHOR_ONLY),
        )
        val visible = FactsForView.factsFor(snapshot("character"), facts).map { it.content }
        assertTrue(visible.contains("公开事实"))
        assertTrue(visible.contains("主角的秘密"), "自己知道的必须给")
        assertTrue(!visible.contains("别人的秘密"), "别人的秘密不能给")
        assertTrue(!visible.contains("作者的秘密"), "作者私密绝不能给角色")
    }

    @Test
    fun `没有快照时保持全量 不误伤旧项目`() {
        val facts = listOf(fact("作者的秘密", Visibility.AUTHOR_ONLY))
        assertEquals(1, FactsForView.factsFor(null, facts).size)
    }
}
