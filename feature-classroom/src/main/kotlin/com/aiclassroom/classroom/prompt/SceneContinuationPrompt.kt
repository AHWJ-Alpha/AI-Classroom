package com.aiclassroom.classroom.prompt

import com.aiclassroom.classroom.domain.*

/**
 * 场景续写提示词。
 *
 * 每次调用都同时给出：整节目标、当前累计、本次建议新增，
 * 以及本节已发生的事实 —— 只给末尾原文会让模型反复复述开头。
 */
object SceneContinuationPrompt {

    private val SCHEMA = """
{"blocks":[{"id":"b1","type":"narration","text":"……"}],
 "events":[{"id":"e1","sourceBlockIds":["b1"],"description":"发生了什么"}],
 "unresolvedItems":["还没说完的事"],"finished":false,
 "summary":"本段发生的事，供下一段参考"}
"""

    data class Input(
        val rawRequest: String,
        val unitGoal: String,
        val timeRange: String,
        val characters: List<CharacterProfile>,
        val totalTarget: Int,
        val currentChars: Int,
        val perCallTarget: Int,
        val isFirst: Boolean,
        val isLast: Boolean,
        val summary: String,
        val events: List<UnitEvent>,
        val usedScenes: List<String>,
        val tailText: String,
        val mustFinish: Boolean,
        val frozenContext: String = "",
        val correction: String = "",
    )

    fun build(input: Input): String = buildString {
        appendLine("[TASK_ROLE]")
        appendLine("你在写一段共同生活的时间线，以具体动作和对白展开。")
        appendLine("只输出新增内容，不复述已写部分，不写本节总结。")
        if (input.frozenContext.isNotBlank()) {
            appendLine("[FROZEN_CONTEXT]")
            appendLine(input.frozenContext)
            appendLine("[/FROZEN_CONTEXT]")
        }
        appendLine()
        appendLine("[DATA:REQUEST]")
        appendLine(input.rawRequest)
        appendLine("[/DATA:REQUEST]")
        appendLine()
        appendLine("[SECTION]")
        appendLine("时间范围：" + input.timeRange)
        appendLine("本节要写到的：" + input.unitGoal)
        appendLine("整节目标：" + input.totalTarget + " 字")
        appendLine("当前累计：" + input.currentChars + " 字")
        appendLine("本次建议新增：约 " + input.perCallTarget + " 字")
        if (input.isFirst) appendLine("这是本节的第 1 段：可以用一个标题交代时间范围，之后不再出现标题。")
        else appendLine("这是续写段：不要新增标题。")
        if (input.mustFinish) appendLine("这一段要把当前阶段自然收束，并声明 finished=true。")
        appendLine()
        if (input.characters.isNotEmpty()) {
            appendLine("[DATA:CHARACTERS]")
            input.characters.forEach {
                appendLine("- id=" + it.id.value + "；姓名=" + it.name)
                appendLine("  人格=" + it.corePersonality + "；语言=" + it.speechStyle)
            }
            appendLine("[/DATA:CHARACTERS]")
            appendLine()
        }
        if (input.summary.isNotBlank()) {
            appendLine("[DATA:PRIOR_SUMMARY]")
            appendLine(input.summary)
            appendLine("[/DATA:PRIOR_SUMMARY]")
            appendLine()
        }
        if (input.events.isNotEmpty()) {
            appendLine("[DATA:PRIOR_EVENTS]")
            input.events.forEach { appendLine("- " + it.description) }
            appendLine("[/DATA:PRIOR_EVENTS]")
            appendLine()
        }
        if (input.usedScenes.isNotEmpty()) {
            appendLine("[AVOID_REPEAT]")
            appendLine("已经用过的桥段，不要再写一遍：" + input.usedScenes.joinToString("、"))
            appendLine()
        }
        if (input.tailText.isNotBlank()) {
            appendLine("[DATA:TAIL]")
            appendLine(input.tailText)
            appendLine("[/DATA:TAIL]")
            appendLine("上面是最近的原文，只用来衔接，不要重复它。")
            appendLine()
        }
        // 叙述视角：以在场第一位角色（讲师）的第一人称来写，把对方称作「你」。
        // 不指定的话模型默认用第三人称，读起来像旁观者的报告，而不是当事人的经历。
        if (input.characters.size >= 2) {
            appendLine("[VIEWPOINT]")
            appendLine("以「" + input.characters[0].name + "」的第一人称叙述：写「我」怎么想、怎么做。")
            appendLine("把「" + input.characters[1].name + "」称作「你」，不要用第三人称称呼。")
            appendLine("叙述里不要出现「" + input.characters[0].name + "说」这种转述，直接写动作与对白。")
            appendLine()
        }
        appendLine("[WRITING_RULES]")
        appendLine("- 通过动作、对白、物件、空间与停顿表现相处方式；")
        appendLine("- 允许安静与稳定，不要求每段关系升级或每段发生冲突；")
        appendLine("- 允许有交代的自然转场，不得用概括句替代应展开的过程；")
        appendLine("- 不要旁白解释心情，让人物用行为说话；")
        appendLine("- 结尾不要每次都写「分开/回头/挥手/看背影」：")
        appendLine("  可以停在一句话、一个动作、一件没做完的事，或直接切断。")
        appendLine("- 也不要每段都用「先冲突再和解」推进。")
        appendLine("- 块类型只能是 narration / dialogue / heading；")
        appendLine("- dialogue 必须带 speakerId，且只能用上面列出的角色 id。")
        appendLine("- 有角色在场时，人物说出口的话必须用 dialogue 块，")
        appendLine("  不要把台词写进 narration：转述会让人物失去声音。")
        appendLine("- 每一段至少 3 处 dialogue；整段没有一句台词就算不合格。")
        appendLine("- 引号里的内容一律拆成独立 dialogue 块，不要留在 narration 里。")
        if (input.characters.isEmpty()) appendLine("没有已注册角色，不输出 dialogue；用 narration 描写动作、内心和引号中的对白。")
        appendLine()
        appendLine("[OUTPUT_SCHEMA]")
        appendLine("只输出 valid json object，不要输出 JSON 以外的解释。")
        appendLine("只输出一个 JSON 对象：")
        appendLine(SCHEMA)
        appendLine("没有事件就返回空 events，不要为凑数编造。")
        if (input.correction.isNotBlank()) appendLine("上一轮未被接收，请纠正：" + input.correction)
    }
}
