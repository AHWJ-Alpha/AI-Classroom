package com.aiclassroom.classroom.prompt

import com.aiclassroom.classroom.domain.ProjectType
import com.aiclassroom.classroom.gateway.StructuredPayload
import com.aiclassroom.classroom.json.JsonParser
import com.aiclassroom.classroom.json.asArray
import com.aiclassroom.classroom.json.asInt
import com.aiclassroom.classroom.json.asObject
import com.aiclassroom.classroom.json.asString

/**
 * 分批细化。
 *
 * 规划一次写八个单元的目标与完成条件，单元一多就会被输出上限截断。
 * 正确做法不是让模型少写细节，而是**分几次写**，由程序合并：
 * 第一次只要骨架（标题），之后每次只补一批单元的 goal 与完成条件。
 */
object PlannerBatch {

    const val BATCH = 4

    data class UnitDetail(val index: Int, val goal: String, val criteria: List<String>)

    fun request(
        rawRequest: String,
        type: ProjectType,
        titles: List<String>,
        from: Int,
        to: Int,
    ): String = buildString {
        appendLine("[TASK_ROLE]")
        appendLine("你正在为一段长内容补写单元细节。只写指定范围内的单元，不要写范围内的别的单元。")
        appendLine()
        appendLine("[DATA:REQUEST]")
        appendLine(rawRequest)
        appendLine("[/DATA:REQUEST]")
        appendLine()
        appendLine("[TARGET_TYPE]")
        appendLine(type.label)
        appendLine()
        appendLine("[OUTLINE_TITLES]")
        titles.forEachIndexed { i, t -> appendLine((i + 1).toString() + ". " + t) }
        appendLine()
        appendLine("[RANGE]")
        appendLine("只输出第 " + from + " 到第 " + to + " 个单元。")
        appendLine("每个单元：goal 不超过 40 字，写清这一段要走到哪；completionCriteria 最多 2 条、每条不超过 20 字。")
        appendLine("如果写不下就写得更短，不要截断 JSON。")
        appendLine()
        appendLine("[OUTPUT_SCHEMA]")
        appendLine(BATCH_SCHEMA)
    }

    private val BATCH_SCHEMA = """
{"units":[{"index":1,"goal":"这一段要走到哪","completionCriteria":["可判断的标志"]}]}
"""

    /** 解析一批细节；范围外的行忽略，范围缺失则报错。 */
    fun parse(raw: String, from: Int, to: Int): List<UnitDetail> {
        val jsonText = StructuredPayload.extract(raw) ?: return emptyList()
        val root = runCatching { JsonParser.parse(jsonText) }.getOrNull() ?: return emptyList()
        val rows = root.asObject()["units"].asArray()
        return rows.mapNotNull { item ->
            val o = item.asObject()
            val index = o["index"].asInt() ?: return@mapNotNull null
            if (index < from || index > to) return@mapNotNull null
            val goal = o["goal"].asString()?.trim().orEmpty()
            if (goal.isBlank()) return@mapNotNull null
            val criteria = o["completionCriteria"].asArray().mapNotNull { it.asString()?.takeIf { s -> s.isNotBlank() } }
            UnitDetail(index = index, goal = goal, criteria = criteria)
        }
    }
}
