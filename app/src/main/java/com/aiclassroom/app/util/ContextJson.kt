package com.aiclassroom.app.util

import com.aiclassroom.app.model.BranchSnapshot
import com.aiclassroom.app.model.KnowledgeFile
import com.aiclassroom.app.model.MemoryCard
import com.aiclassroom.app.model.ChatMessage
import com.aiclassroom.app.model.ClassroomEventRef
import org.json.JSONArray
import org.json.JSONObject

internal fun ChatMessage.toMessageJson(): JSONObject = JSONObject().put("role", role).put("text", text).apply {
    if (images.isNotEmpty()) put("images", JSONArray(images))
    if (audience.isNotEmpty()) put("audience", JSONArray(audience))
    // 结构化任务卡：旧存档没有这个键，读成 null 即可。
    classroomEvent?.let { e ->
        put("classroomEvent", JSONObject().apply {
            put("eventId", e.eventId)
            put("projectId", e.projectId)
            put("runId", e.runId ?: "")
            put("workVersion", e.workVersion)
            put("kind", e.kind)
        })
    }
}

internal fun JSONArray?.toMessages(): List<ChatMessage> = if (this == null) emptyList() else List(length()) { index ->
    getJSONObject(index).let { item ->
        ChatMessage(
            item.optString("role"), item.optString("text"),
            images = item.optJSONArray("images").toStrings().filter { it.isNotBlank() },
            audience = item.optJSONArray("audience").toStrings().filter { it.isNotBlank() },
            classroomEvent = item.optJSONObject("classroomEvent")?.let { e ->
                ClassroomEventRef(
                    eventId = e.optString("eventId"),
                    projectId = e.optString("projectId"),
                    runId = e.optString("runId").ifBlank { null },
                    workVersion = e.optInt("workVersion", 0),
                    kind = e.optString("kind"),
                )
            }
        )
    }
}

internal fun MemoryCard.toMemoryCardJson(): JSONObject = JSONObject()
    .put("id", factId).put("title", title).put("summary", summary)
    .put("keywords", JSONArray(keywords)).put("type", type).put("priority", priority)
    .put("startIndex", startIndex).put("endIndex", endIndex)
    .put("visibility", visibility).put("knownBy", JSONArray(knownBy))
    .put("unknownBy", JSONArray(unknownBy)).put("evidence", evidence)

private fun JSONObject.memoryCard(): MemoryCard = MemoryCard(
    optString("title"), optString("summary"), optJSONArray("keywords").toStrings(),
    optString("type", "concept"), optInt("priority", 1), optInt("startIndex"), optInt("endIndex"),
    normalizeVisibility(optString("visibility", "author_private")),
    optJSONArray("knownBy").toStrings(), optJSONArray("unknownBy").toStrings(),
    optString("evidence"), optString("id")
)

/** 内容快照不包含模型连接和密钥。 */
internal fun BranchSnapshot.toSnapshotJson(): JSONObject = JSONObject()
    .put("mentorName", mentorName).put("userAlias", userAlias).put("mentorPrompt", mentorPrompt)
    .put("worldBook", worldBook).put("publicWorldBook", publicWorldBook)
    .put("activeCharacterId", activeCharacterId).put("sourceMessageCount", sourceMessageCount)
    .put("files", JSONArray(files.map { file ->
        JSONObject().put("name", file.name).put("type", file.type).put("chars", file.chars)
            .put("preview", file.preview).put("content", file.content).put("kind", file.kind)
    }))
    .put("memoryCards", JSONArray(memoryCards.map { it.toMemoryCardJson() }))

internal fun branchSnapshotFromJson(json: JSONObject?): BranchSnapshot? {
    if (json == null) return null
    return runCatching {
        val files = json.optJSONArray("files") ?: JSONArray()
        val cards = json.optJSONArray("memoryCards") ?: JSONArray()
        BranchSnapshot(
            json.optString("mentorName"), json.optString("userAlias"), json.optString("mentorPrompt"),
            json.optString("worldBook"), json.optString("publicWorldBook"), json.optString("activeCharacterId"),
            List(files.length()) { i -> files.getJSONObject(i).let {
                KnowledgeFile(it.getString("name"), it.optString("type"), it.optInt("chars"),
                    it.optString("preview"), it.optString("content"), it.optString("kind", "reference"))
            } },
            List(cards.length()) { cards.getJSONObject(it).memoryCard() }, json.getInt("sourceMessageCount")
        )
    }.getOrNull()
}
