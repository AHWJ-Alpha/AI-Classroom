package com.aiclassroom.classroom.plan

import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.gateway.StructuredPayload
import com.aiclassroom.classroom.json.*

/**
 * 把模型返回的总纲 JSON 变成领域对象。
 *
 * 两个刻意的设计：
 * 1. **单元 id 由程序生成**，模型只给标题/目标/依赖。让模型编 id 迟早出现重复或伪造。
 * 2. 依赖既接受 id，也接受 1 起的序号（模型更习惯写「依赖第 2 节」），统一换算成 id。
 */
object OutlineParser {

    data class Parsed(val outline: ProjectOutline?, val problems: List<PlanProblem>)

    fun parse(
        projectId: ProjectId,
        version: Int,
        rawText: String,
        now: EpochMillis,
        spec: ProjectSpec? = null,
    ): Parsed {
        val jsonText = StructuredPayload.extract(rawText)
            ?: return Parsed(null, listOf(PlanProblem("no-json", "模型输出里没有完整的 JSON 总纲", blocking = true)))

        val root = runCatching { JsonParser.parse(jsonText) }.getOrNull()
            ?: return Parsed(null, listOf(PlanProblem("bad-json", "总纲 JSON 无法解析", blocking = true)))

        val o = root.asObject()
        val rawUnits = o["units"].asArray()
        if (rawUnits.isEmpty()) {
            return Parsed(null, listOf(PlanProblem("no-units", "总纲里没有 units", blocking = true)))
        }

        // 先按顺序生成 id，并记住「序号 -> id」，用于把 dependsOn 的序号换算成 id。
        val unitsRaw = rawUnits.mapIndexed { i, item ->
            val m = item.asObject()
            val index = m["index"].asInt() ?: (i + 1)
            val id = UnitId.new()
            Triple(index, id, m)
        }
        val byIndex = unitsRaw.associate { it.first to it.second }

        val units = unitsRaw.map { (index, id, m) ->
            val deps = m["dependsOn"].asArray().mapNotNull { dep ->
                val asIndex = dep.asInt()
                if (asIndex != null) byIndex[asIndex] else dep.asString()?.let { UnitId(it) }
            }
            PlannedUnit(
                unitId = id,
                index = index,
                title = m["title"].asString() ?: ("第 " + index + " 节"),
                goal = m["goal"].asString() ?: "",
                dependsOn = deps,
                completionCriteria = m["completionCriteria"].asArray().mapNotNull { it.asString() },
            )
        }

        val outline = ProjectOutline(
            projectId = projectId,
            version = version,
            globalGoal = o["globalGoal"].asString() ?: "",
            mainLine = o["mainLine"].asString() ?: "",
            chapters = o["chapters"].asArray().mapNotNull { it.asString() },
            units = units,
            milestones = o["milestones"].asArray().mapNotNull { it.asString() },
            foreshadows = o["foreshadows"].asArray().mapNotNull { it.asString() },
            ending = o["ending"].asString() ?: "",
            createdAt = now,
        )

        return Parsed(outline, OutlineValidator.validate(outline, spec).problems)
    }
}
