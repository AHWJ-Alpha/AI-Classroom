package com.aiclassroom.classroom.domain

import com.aiclassroom.classroom.gateway.ModelPolicy
import com.aiclassroom.classroom.json.*
import com.aiclassroom.classroom.repository.ClassroomStore
import com.aiclassroom.classroom.store.StoreCodec
import java.security.MessageDigest

/** 不含执行者/阶段字段：换 run 可恢复，编辑设定和依赖则不能复用候选。 */
object SceneInputVersion {
    fun compute(store: ClassroomStore, projectId: ProjectId, unitId: UnitId, policy: ModelPolicy): String {
        val unit = requireNotNull(store.unit(unitId))
        val parts = listOf(
            "scene-continuation-v2", store.latestSpec(projectId).toString(), store.latestOutline(projectId).toString(),
            unit.copy(status = UnitStatus.PLANNED).toString(),
            store.latestSnapshot(projectId)?.let { canonical(StoreCodec.snapshot(it)) }.orEmpty(),
            store.characters(projectId).sortedBy { it.id.value }.joinToString { canonical(StoreCodec.character(it)) },
            store.units(projectId).filter { it.index < unit.index }.sortedBy { it.index }
                .joinToString { "${it.id.value}:${it.currentRevisionId?.value}:${it.officialVersion}:${it.status}" },
            // 正式状态只在单元提交时改变；本候选的生成阶段不改变这些值。
            store.characterStates(projectId).sortedBy { it.characterId.value }
                .joinToString { canonical(StoreCodec.characterState(it)) },
            store.facts(projectId).sortedWith(compareBy({ it.factId.value }, { it.version }))
                .joinToString { canonical(StoreCodec.fact(it)) },
            "${policy.provider}|${policy.baseUrl}|${policy.models}|${policy.temperature}|${policy.reasoningEffort}|${policy.maxOutputTokens}",
        )
        return MessageDigest.getInstance("SHA-256").digest(parts.joinToString("\u0000").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun canonical(value: Json): String = JsonWriter.write(sorted(value))
    private fun sorted(value: Json): Json = when (value) {
        is Json.Obj -> Json.Obj(value.fields.toSortedMap().mapValues { sorted(it.value) })
        is Json.Arr -> Json.Arr(value.items.map(::sorted))
        else -> value
    }
}
