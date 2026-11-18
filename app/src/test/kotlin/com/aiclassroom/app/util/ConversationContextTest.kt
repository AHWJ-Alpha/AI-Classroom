package com.aiclassroom.app.util

import com.aiclassroom.app.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConversationContextTest {
    private fun card(name: String, text: String = "# $name\n## 性格\n谨慎\n## 语言风格\n简短") =
        KnowledgeFile("$name.md", "md", text.length, text, text, "character_card")

    private fun room(config: ClassroomConfig = ClassroomConfig(), files: List<KnowledgeFile> = emptyList()) = Classroom(
        "课堂", "故事", messages = mutableListOf(), branches = mutableListOf(), memories = mutableListOf(),
        corePremises = mutableListOf(), memoryCards = mutableListOf(), chapters = mutableListOf(),
        files = files.toMutableList(), config = config
    )

    private fun memory(id: String, text: String, end: Int = 1, visibility: String = "public",
                       known: List<String> = emptyList(), unknown: List<String> = emptyList()) = MemoryCard(
        id, text, listOf("钥匙"), "narrative", 2, end, end, visibility, known, unknown, "事件证据", id
    )

    @Test fun privateFactsAreFilteredBeforeRetrieval() {
        val records = listOf(
            memory("secret", "钥匙藏在阁楼", visibility = "character_private", known = listOf("A.md")),
            memory("author", "钥匙幕后秘密", visibility = "author_private", known = listOf("A.md")),
            memory("public", "钥匙已公开"),
            memory("denied", "钥匙尚未告知", known = listOf("B.md"), unknown = listOf("B.md"))
        )
        assertEquals(listOf("public"), recallMemoryCards(records, "钥匙", 8, setOf("B.md")).map { it.factId })
        assertEquals(setOf("secret", "public", "denied"), recallMemoryCards(records, "钥匙", 8, setOf("A.md")).map { it.factId }.toSet())
        assertEquals(4, recallMemoryCards(records, "钥匙", 8).size)
    }

    @Test fun unrelatedPriorityCannotForceRecall() {
        assertTrue(recallMemoryCards(listOf(memory("x", "钥匙")), "quantum", 8).isEmpty())
    }

    @Test fun chineseQuestionRecallsFactWithDifferentSentenceWording() {
        assertEquals(1, recallMemoryCards(listOf(memory("key", "钥匙藏在阁楼")), "钥匙在哪里", 8).size)
    }

    @Test fun duplicateDisplayNamesDoNotGrantAccessToEachOthersMemory() {
        val source = room(ClassroomConfig(activeCharacterId = "B.md"), listOf(
            card("A", "# 同名\nA_SECRET"), card("B", "# 同名\nB_PROFILE")))
        source.memoryCards += memory("a", "钥匙 NAME_SECRET", visibility = "character_private", known = listOf("同名"))
        assertFalse(promptMessagesForRoom(source, listOf(scopedMessage(source.config, "user", "钥匙"))).joinToString { it.text }.contains("NAME_SECRET"))
    }

    @Test fun updatesKeepHistoryButDoNotResurrectOldPublicVersion() {
        val old = memory("key", "钥匙公开", 1)
        val update = memory("key", "钥匙私密", 8, "character_private", listOf("A.md"), listOf("B.md"))
        val merged = mergeMemoryCards(listOf(old, update, update))
        assertEquals(2, merged.size)
        assertTrue(recallMemoryCards(merged, "钥匙", 8, setOf("B.md")).isEmpty())
        assertEquals(listOf("B.md"), latestMemoryCards(merged).single().unknownBy)
        assertEquals(listOf(old), merged.filter { it.endIndex < 5 })
    }

    @Test fun characterRequestExcludesOtherSecretsAndUnscopedHistory() {
        val source = room(ClassroomConfig(activeCharacterId = "B.md", worldBook = "AUTHOR_SECRET",
            publicWorldBook = "公开校园"), listOf(card("A", "# A\nA_SECRET"), card("B")))
        source.corePremises += "PREMISE_SECRET"
        source.chapters += ConversationChapter("CHAPTER_SECRET", "SUMMARY_SECRET", 0, 1)
        source.memoryCards += memory("a", "钥匙 MEMORY_SECRET", visibility = "character_private", known = listOf("A.md"))
        val history = listOf(ChatMessage("user", "HISTORY_SECRET"),
            ChatMessage("user", "A_HISTORY_SECRET", audience = listOf("A.md")),
            scopedMessage(source.config, "user", "钥匙在哪"))
        val request = conversationSystemPrompt(source, history) + promptMessagesForRoom(source, history).joinToString { it.text }
        listOf("AUTHOR_SECRET", "A_SECRET", "PREMISE_SECRET", "CHAPTER_SECRET", "SUMMARY_SECRET", "MEMORY_SECRET", "HISTORY_SECRET")
            .forEach { assertFalse(it, request.contains(it)) }
        assertTrue(request.contains("公开校园"))
        assertTrue(request.contains("钥匙在哪"))
    }

    @Test fun ownCardRemovesExplicitAuthorOnlySections() {
        val content = "# B\n## 性格\n谨慎\n## 作者私密\nSECRET\n### 子节\nNESTED_SECRET\n## 口吻\n简短"
        val result = characterOwnedProfile(content)
        assertFalse(result.contains("SECRET"))
        assertTrue(result.contains("简短"))
    }

    @Test fun lastRelevantCharacterSurvivesLargeRosterAndKeepsTailSections() {
        val files = (1..12).map { card("角色$it", "# 角色$it\n## 性格\n" + "性格".repeat(3000) + "\n## 行为边界\nBOUNDARY_$it") }
        val result = formatCharacterCards(files, "角色12")
        assertTrue(result.contains("BOUNDARY_12"))
        assertFalse(result.contains("[角色：角色1]"))
        assertTrue(result.length <= CHARACTER_CARD_CONTEXT_LIMIT)
    }

    @Test fun branchDoesNotReceiveMainlineFutureStateAndSurvivesSerialization() {
        val source = room(files = listOf(card("A")))
        source.messages += ChatMessage("user", "起点")
        source.memoryCards += memory("key", "钥匙原状态", 0)
        val branch = BranchClass("支线", "起点", mutableListOf(), "", source.messages.toMutableList(), snapshotBranch(source))
        source.memoryCards += memory("key", "钥匙 FUTURE_SECRET", 5)
        source.files += card("C", "FUTURE_CARD")
        val restored = branch.copy(snapshot = branchSnapshotFromJson(branch.snapshot!!.toSnapshotJson()))
        val result = branchConversationSystemPrompt(source, restored) +
            promptMessagesForBranch(source, restored, listOf(ChatMessage("user", "钥匙"))).joinToString { it.text }
        assertFalse(result.contains("FUTURE_SECRET"))
        assertFalse(result.contains("FUTURE_CARD"))
        assertTrue(result.contains("钥匙原状态"))
        assertFalse(restored.snapshot!!.toSnapshotJson().toString().contains("apiKey"))
    }

    @Test fun oldBranchDoesNotInheritCurrentRoomState() {
        val source = room(ClassroomConfig(worldBook = "FUTURE_SECRET"), listOf(card("A", "FUTURE_CARD")))
        source.memoryCards += memory("future", "FUTURE_MEMORY")
        val branch = BranchClass("旧分支", "旧起点", mutableListOf(), "", mutableListOf(ChatMessage("user", "原锚点")))
        val request = branchConversationSystemPrompt(source, branch) + promptMessagesForBranch(source, branch, emptyList()).joinToString { it.text }
        assertFalse(request.contains("FUTURE_"))
        assertTrue(request.contains("原锚点"))
    }

    @Test fun branchLocalFactOverridesSnapshotEvenWithSmallerMessageIndex() {
        val source = room()
        source.memoryCards += memory("key", "钥匙旧状态", 99)
        val branch = BranchClass("支线", "起点", mutableListOf(), "", snapshot = snapshotBranch(source, 100))
        branch.memoryCards += memory("key", "钥匙新状态", 2)
        val request = promptMessagesForBranch(source, branch, listOf(ChatMessage("user", "钥匙"))).joinToString { it.text }
        assertTrue(request.contains("钥匙新状态"))
        assertFalse(request.contains("钥匙旧状态"))
        assertTrue(source.memoryCards.single().summary.contains("旧状态"))
    }

    @Test fun longSourcesKeepNewestTextAndSeparateAudiences() {
        val messages = listOf(ChatMessage("user", "x".repeat(20000) + "LATEST_MARKER"),
            ChatMessage("user", "B_ONLY", audience = listOf("B.md")))
        val batches = memorySourceBatches(messages, 0)
        assertTrue(batches.joinToString { it.text }.contains("LATEST_MARKER"))
        assertTrue(batches.last().text.contains("B_ONLY"))
        assertEquals(listOf("B.md"), batches.last().audience)
        assertTrue(batches.all { it.text.length <= 7000 })
        assertEquals(1, memorySourceBatches(messages, 1).size)
    }

    @Test fun fallbackDoesNotInventPublicKnowledgeOrRememberAssistantClaims() {
        val pack = localLearningMemoryPack(listOf(ChatMessage("assistant", "假设的秘密"),
            ChatMessage("user", "我的学习计划", audience = listOf("B.md"))))
        assertEquals(1, pack.cards.size)
        assertEquals("character_private", pack.cards.single().visibility)
        assertEquals(listOf("B.md"), pack.cards.single().knownBy)
        assertEquals("public", inferVisibility("我的学习计划"))
        assertEquals("author_private", normalizeVisibility("typo"))
    }

    @Test fun audienceAndLegacyMessagesRoundTrip() {
        val message = ChatMessage("user", "你好", images = listOf("image.png"), audience = listOf("B.md"))
        assertEquals(message, JSONArray().put(message.toMessageJson()).toMessages().single())
        assertTrue(JSONArray().put(JSONObject().put("role", "user").put("text", "旧消息")).toMessages().single().audience.isEmpty())
    }

    @Test fun visionIncludesFilteredStateAndPersonalityRulesAppearOnce() {
        val source = room()
        val system = conversationSystemPrompt(source)
        assertEquals(1, Regex("固定人格、价值观和口吻保持稳定").findAll(system).count())
        val request = JSONObject(buildVisionJson("model", system, "看图", listOf("data:image/png;base64,AA"),
            listOf(ChatMessage("system", "VISIBLE_MEMORY"))))
        assertTrue(request.getJSONArray("messages").getJSONObject(0).getString("content").contains("VISIBLE_MEMORY"))
    }
}
