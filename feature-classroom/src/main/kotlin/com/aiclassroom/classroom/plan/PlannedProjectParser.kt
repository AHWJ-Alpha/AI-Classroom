package com.aiclassroom.classroom.plan

import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.gateway.StructuredPayload
import com.aiclassroom.classroom.json.*

/**
 * 解析规划器的输出：规格 + 总纲。
 *
 * 规格里**用户原话永远保留**（[ProjectSpec.rawRequest] 用调用方传入的原值，
 * 不采用模型复述的版本）—— 模型总结过的需求不能反过来取代用户真实意图。
 */
object PlannedProjectParser {

    data class Parsed(
        val spec: ProjectSpec?,
        val outline: ProjectOutline?,
        val problems: List<PlanProblem>,
    ) {
        val ok: Boolean get() = spec != null && outline != null && problems.none { it.blocking }
    }

    fun parse(
        raw: String,
        projectId: ProjectId,
        rawRequest: String,
        fallbackUnitCount: Int,
        now: EpochMillis,
    ): Parsed {
        val jsonText = StructuredPayload.extract(raw)
            ?: return Parsed(null, null, listOf(PlanProblem("no-json", "规划输出里没有完整 JSON", blocking = true)))
        val root = runCatching { JsonParser.parse(jsonText) }.getOrNull()
            ?: return Parsed(null, null, listOf(PlanProblem("bad-json", "规划 JSON 无法解析", blocking = true)))

        val o = root.asObject()
        val specObj = o["spec"].asObject()

        val unitCount = specObj["unitCount"].asInt()
            ?: o["units"].asArray().size
            ?: fallbackUnitCount

        val spec = ProjectSpec(
            projectId = projectId,
            version = 2, // 1 是创建时保存的原始需求
            rawRequest = rawRequest,
            topic = specObj["topic"].asString() ?: "",
            goals = specObj["goals"].asArray().mapNotNull { it.asString() },
            audience = specObj["audience"].asString() ?: "",
            language = specObj["language"].asString() ?: "zh",
            style = specObj["style"].asString() ?: "",
            expectedLength = specObj["expectedLength"].asString() ?: "",
            unitCount = unitCount,
            runMode = RunMode.CONFIRM_OUTLINE,
            reviewPolicy = ReviewPolicy.STANDARD,
            hardConstraints = specObj["hardConstraints"].asArray().mapNotNull { it.asString() },
            authorRules = specObj["authorRules"].asArray().mapNotNull { it.asString() },
            publicWorld = specObj["publicWorld"].asString() ?: "",
            createdAt = now,
        )

        // 总纲复用 OutlineParser（它负责把序号依赖换算成 id 并跑结构校验）。
        val outlineParsed = OutlineParser.parse(
            rawText = jsonText,
            projectId = projectId,
            version = 1,
            now = now,
            spec = spec,
        )

        val problems = outlineParsed.problems.toMutableList()
        if (spec.topic.isBlank()) {
            problems += PlanProblem("blank-topic", "规格没有主题", blocking = true)
        }
        if (unitCount <= 0) {
            problems += PlanProblem("bad-unit-count", "规格的单元数不合法", blocking = true)
        }

        return Parsed(spec, outlineParsed.outline, problems)
    }
}
