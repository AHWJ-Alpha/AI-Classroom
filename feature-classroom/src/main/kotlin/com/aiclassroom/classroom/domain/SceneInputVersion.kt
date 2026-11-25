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
            "scene-continuation-v3", store.latestSpec(projectId).toString(), store.latestOutline(projectId).toString(),
            unit.copy(status = UnitStatus.PLANNED, directive = unit.directive?.takeUnless { it.isEmpty }).toString(),
            store.latestSnapshot(projectId)?.let { canonical(StoreCodec.snapshot(it)) }.orEmpty(),
            store.characters(projectId).sortedBy { it.id.value }.joinToString { canonical(StoreCodec.character(it)) },
            // 上游只取「真正影响正文身份」的三项：是哪一节、它的正式版本、它的正文 id。
            // **不取 status** —— 状态会因为一次失败、一次恢复、一次标 STALE 就变，
            // 而正文并没有变。把 status 算进指纹会让下游草稿集体失配：
            // 上一节刚报错，下一节已经写好的草稿就被判成「输入变了」，从头重写。
            // 用户看到的就是「一报错/一中断，进度就没了」。
            store.units(projectId).filter { it.index < unit.index }.sortedBy { it.index }
                .joinToString { "${it.id.value}:${it.currentRevisionId?.value}:${it.officialVersion}" },
            // 正式状态只在单元提交时改变；本候选的生成阶段不改变这些值。
            store.characterStates(projectId).sortedBy { it.characterId.value }
                .joinToString { canonical(StoreCodec.characterState(it)) },
            store.facts(projectId).sortedWith(compareBy({ it.factId.value }, { it.version }))
                .joinToString { canonical(StoreCodec.fact(it)) },
            // 只把「会改变正文身份」的项算进指纹：模型、服务商、温度、推理强度。
            // maxOutputTokens 是工程参数——调大它是为了不被截断，不是换了内容来源；
            // 把它算进来会让「调过一次上限」之后所有旧草稿失配，用户一继续就从头写。
            "${policy.provider}|${policy.baseUrl}|${policy.models}|${policy.temperature}|${policy.reasoningEffort}",
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
