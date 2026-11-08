package com.aiclassroom.feature.exam.tool.exam

import com.aiclassroom.feature.exam.ExamQuestion
import com.aiclassroom.feature.exam.ExamSession
import com.aiclassroom.feature.exam.tool.ToolHandler
import com.aiclassroom.feature.exam.tool.ToolRegistry
import com.aiclassroom.feature.exam.tool.ToolResult

private const val DEFAULT_EXAM_TITLE = "AI 随堂考试"

/**
 * 考试工具集（exam_create / exam_get / exam_add_question / exam_edit_question /
 * exam_delete_question / exam_set_title / exam_mark_unknown）。
 *
 * 不持有全局单例：当前考试由 [current] 读取，每次修改后通过 [update] 回写一个全新实例
 * （[ExamSession] 与题目列表都是复制后重建，不原地改调用方持有的对象）。
 *
 * execute 永不抛异常：所有失败都转成 [ToolResult.Error]。
 */
class ExamTools(
    private val current: () -> ExamSession?,
    private val update: (ExamSession) -> Unit,
) {
    /** 按注册顺序暴露给模型的工具。 */
    val handlers: List<ToolHandler> = listOf(
        CreateTool(),
        GetTool(),
        AddQuestionTool(),
        EditQuestionTool(),
        DeleteQuestionTool(),
        SetTitleTool(),
        MarkUnknownTool(),
    )

    /** 阶段 C 一行接入：[registry] 里已有同名工具会被覆盖。 */
    fun register(registry: ToolRegistry): ExamTools {
        handlers.forEach(registry::register)
        return this
    }

    // ---------- 工具实现 ----------

    private inner class CreateTool : ToolHandler {
        override val name: String = "exam_create"
        override val description: String =
            "创建或整体替换当前考试：一次给出完整题目列表，原有题目、草稿和提交状态都会被丢弃。题目按数组顺序编号为第 1 题起。每题的 referenceAnswer 是给考生看的参考解析，不会填进考生作答框。"
        override val parametersJson: String = ExamToolSchemas.CREATE

        override suspend fun execute(argumentsJson: String): ToolResult = guarded {
            val arguments = parseArguments(argumentsJson)
            val title = arguments.string("title")?.trim().orEmpty().ifBlank { DEFAULT_EXAM_TITLE }
            val items = arguments.requiredArray("questions")
            if (items.isEmpty()) throw ToolFailure("questions 至少要有 1 题")
            val questions = items.mapIndexed { position, item -> questionFrom(item, position + 1) }
            val session = ExamSession(title = title, questions = questions.toMutableList())
            update(session)
            success("已创建考试「$title」，共 ${questions.size} 题", session)
        }
    }

    private inner class GetTool : ToolHandler {
        override val name: String = "exam_get"
        override val description: String =
            "读回当前考试的完整内容：标题、每题（第几题 / 前提 / 问题 / 考生作答 / 参考解析 / 是否标记不会）以及草稿与提交状态。考生作答是 answer，参考解析是 referenceAnswer，两者互不影响。"
        override val parametersJson: String = ExamToolSchemas.GET

        override suspend fun execute(argumentsJson: String): ToolResult = guarded {
            parseArguments(argumentsJson)
            val session = currentOrFail()
            success("当前考试「${session.title}」共 ${session.questions.size} 题", session)
        }
    }

    private inner class AddQuestionTool : ToolHandler {
        override val name: String = "exam_add_question"
        override val description: String =
            "在当前考试中追加或插入一道题。index 从 1 开始：1 = 插到最前面并成为第 1 题，当前题数 + 1 = 追加到末尾；省略 index 即追加到末尾。referenceAnswer 是参考解析，不会填入考生作答框。"
        override val parametersJson: String = ExamToolSchemas.ADD_QUESTION

        override suspend fun execute(argumentsJson: String): ToolResult = guarded {
            val arguments = parseArguments(argumentsJson)
            val session = currentOrFail()
            val premise = requiredText(arguments, "premise")
            val question = requiredText(arguments, "question")
            val referenceAnswer = arguments.referenceAnswerOrEmpty()
            val size = session.questions.size
            val position = arguments.indexOrNull("index") ?: (size + 1)
            if (position < 1 || position > size + 1) {
                throw ToolFailure("index 越界：当前共 $size 题，可用范围 1..${size + 1}")
            }
            val questions = session.questions.toMutableList()
            questions.add(position - 1, ExamQuestion(premise = premise, question = question, referenceAnswer = referenceAnswer))
            val updated = session.copy(questions = questions)
            update(updated)
            val action = if (position == size + 1) "已追加第 $position 题" else "已插入第 $position 题，原第 $position 题起顺延"
            success("$action，当前共 ${questions.size} 题", updated)
        }
    }

    private inner class EditQuestionTool : ToolHandler {
        override val name: String = "exam_edit_question"
        override val description: String =
            "修改当前考试中指定题目的前提 / 问题 / 参考解析。index 是题号，从 1 开始（第 1 题 = 1）。只传要改的字段，未传的字段保持原值；referenceAnswer 传空字符串表示清空解析。注意：本工具不会改写考生已经写下的作答（answer）。"
        override val parametersJson: String = ExamToolSchemas.EDIT_QUESTION

        override suspend fun execute(argumentsJson: String): ToolResult = guarded {
            val arguments = parseArguments(argumentsJson)
            val session = currentOrFail()
            val index = arguments.indexOrNull("index") ?: throw ToolFailure("缺少参数 index（题号，从 1 开始）")
            val size = session.questions.size
            if (index < 1 || index > size) {
                throw ToolFailure("index 越界，当前共 $size 题（可用范围 1..$size）")
            }
            // 参考解析：新参数名 referenceAnswer 优先，旧参数名 answer 兼容映射到同一个字段——
            // 绝不写入考生作答（answer），否则又会出现「AI 出题即算已答」的老问题。
            val referenceAnswerKey = when {
                arguments.provided("referenceAnswer") -> "referenceAnswer"
                arguments.provided("answer") -> "answer"
                else -> null
            }
            val changed = buildList {
                if (arguments.provided("premise")) add("premise")
                if (arguments.provided("question")) add("question")
                if (referenceAnswerKey != null) add("referenceAnswer")
            }
            if (changed.isEmpty()) throw ToolFailure("至少提供 premise / question / referenceAnswer 中的一项")
            val original = session.questions[index - 1]
            val question = original.copy(
                premise = if (arguments.provided("premise")) requiredText(arguments, "premise") else original.premise,
                question = if (arguments.provided("question")) requiredText(arguments, "question") else original.question,
                referenceAnswer = if (referenceAnswerKey != null) arguments.string(referenceAnswerKey)?.trim().orEmpty() else original.referenceAnswer,
            )
            val questions = session.questions.toMutableList()
            questions[index - 1] = question
            val updated = session.copy(questions = questions)
            update(updated)
            success("已修改第 $index 题的 ${changed.joinToString("、")}", updated)
        }
    }

    private inner class DeleteQuestionTool : ToolHandler {
        override val name: String = "exam_delete_question"
        override val description: String =
            "删除当前考试中的一道题，index 是题号，从 1 开始（第 1 题 = 1）。删除后其后的题号依次前移。"
        override val parametersJson: String = ExamToolSchemas.DELETE_QUESTION

        override suspend fun execute(argumentsJson: String): ToolResult = guarded {
            val arguments = parseArguments(argumentsJson)
            val session = currentOrFail()
            val index = arguments.indexOrNull("index") ?: throw ToolFailure("缺少参数 index（题号，从 1 开始）")
            val size = session.questions.size
            if (index < 1 || index > size) {
                throw ToolFailure("index 越界，当前共 $size 题（可用范围 1..$size）")
            }
            val questions = session.questions.toMutableList()
            questions.removeAt(index - 1)
            val updated = session.copy(questions = questions)
            update(updated)
            success("已删除第 $index 题，剩余 ${questions.size} 题", updated)
        }
    }

    private inner class SetTitleTool : ToolHandler {
        override val name: String = "exam_set_title"
        override val description: String = "修改当前考试的标题，题目保持不变。"
        override val parametersJson: String = ExamToolSchemas.SET_TITLE

        override suspend fun execute(argumentsJson: String): ToolResult = guarded {
            val arguments = parseArguments(argumentsJson)
            val session = currentOrFail()
            val title = requiredText(arguments, "title")
            val updated = session.copy(title = title)
            update(updated)
            success("标题已改为「$title」", updated)
        }
    }

    private inner class MarkUnknownTool : ToolHandler {
        override val name: String = "exam_mark_unknown"
        override val description: String =
            "把某题标记为「不会」（或取消标记）。index 是题号，从 1 开始（第 1 题 = 1）。用于考生跳过这题时保留其状态。"
        override val parametersJson: String = ExamToolSchemas.MARK_UNKNOWN

        override suspend fun execute(argumentsJson: String): ToolResult = guarded {
            val arguments = parseArguments(argumentsJson)
            val session = currentOrFail()
            val index = arguments.indexOrNull("index") ?: throw ToolFailure("缺少参数 index（题号，从 1 开始）")
            val flag = arguments["unknown"] ?: throw ToolFailure("缺少参数 unknown（true = 标记不会，false = 取消标记）")
            val unknown = flag.asBooleanOrNull() ?: throw ToolFailure("参数 unknown 必须是布尔值 true / false")
            val size = session.questions.size
            if (index < 1 || index > size) {
                throw ToolFailure("index 越界，当前共 $size 题（可用范围 1..$size）")
            }
            val questions = session.questions.toMutableList()
            questions[index - 1] = questions[index - 1].copy(unknown = unknown)
            val updated = session.copy(questions = questions)
            update(updated)
            val action = if (unknown) "已把第 $index 题标记为「不会」" else "已取消第 $index 题的「不会」标记"
            success(action, updated)
        }
    }

    // ---------- 参数与结果处理 ----------

    private fun parseArguments(argumentsJson: String): Map<String, JsonValue> {
        val trimmed = argumentsJson.trim()
        if (trimmed.isEmpty()) return emptyMap()
        val parsed = try {
            JsonValue.parse(trimmed)
        } catch (failure: JsonFormatException) {
            throw ToolFailure("参数不是合法 JSON：${failure.message}")
        } catch (throwable: Throwable) {
            throw ToolFailure("参数不是合法 JSON")
        }
        return parsed.asObjectOrNull() ?: throw ToolFailure("参数必须是 JSON 对象")
    }

    /** 键存在且不是 null。用于区分「没传」和「传了空字符串」。 */
    private fun Map<String, JsonValue>.provided(key: String): Boolean =
        containsKey(key) && this[key] !is JsonValue.Null

    private fun Map<String, JsonValue>.string(key: String): String? {
        if (!provided(key)) return null
        return this[key]?.asStringOrNull() ?: throw ToolFailure("参数 $key 必须是字符串")
    }

    /** referenceAnswer 优先，兼容旧的 answer 参数名；两者都只表示参考解析。 */
    private fun Map<String, JsonValue>.referenceAnswerOrEmpty(): String =
        string("referenceAnswer")?.trim().orEmpty().ifBlank { string("answer")?.trim().orEmpty() }

    private fun Map<String, JsonValue>.requiredArray(key: String): List<JsonValue> {
        val value = this[key] ?: throw ToolFailure("缺少参数 $key")
        return (value as? JsonValue.Arr)?.items ?: throw ToolFailure("参数 $key 必须是数组")
    }

    private fun Map<String, JsonValue>.indexOrNull(key: String): Int? {
        if (!provided(key)) return null
        return this[key]?.asIntOrNull() ?: throw ToolFailure("参数 $key 必须是整数（题号从 1 开始）")
    }

    private fun requiredText(arguments: Map<String, JsonValue>, key: String): String =
        arguments.string(key)?.trim().orEmpty().ifBlank { throw ToolFailure("参数 $key 不能为空") }

    private fun questionFrom(item: JsonValue, position: Int): ExamQuestion {
        val fields = item.asObjectOrNull()
            ?: throw ToolFailure("第 $position 题必须是对象：{\"premise\": \"...\", \"question\": \"...\"}")
        val premise = fields.string("premise")?.trim().orEmpty()
        if (premise.isEmpty()) throw ToolFailure("第 $position 题缺少 premise（题干前提）")
        val question = fields.string("question")?.trim().orEmpty()
        if (question.isEmpty()) throw ToolFailure("第 $position 题缺少 question（具体问题）")
        return ExamQuestion(
            premise = premise,
            question = question,
            // 模型给的标准答案只进 referenceAnswer；answer 始终留给考生自己写。
            referenceAnswer = fields.referenceAnswerOrEmpty(),
        )
    }

    // ---------- 回调与结果 ----------

    private fun currentOrFail(): ExamSession = current() ?: throw ToolFailure("当前没有进行中的考试")

    private fun success(message: String, session: ExamSession): ToolResult = ToolResult.Ok(
        JsonValue.write(
            jsonObject(
                "ok" to JsonValue.Bool(true),
                "message" to JsonValue.Str(message),
                "exam" to examJson(session),
            )
        )
    )

    private fun examJson(session: ExamSession): JsonValue = jsonObject(
        "title" to JsonValue.Str(session.title),
        "count" to JsonValue.Num(session.questions.size.toDouble()),
        "submitted" to JsonValue.Bool(session.submitted),
        "draft" to JsonValue.Str(session.draft),
        "questions" to jsonArray(
            session.questions.mapIndexed { position, question ->
                jsonObject(
                    "index" to JsonValue.Num((position + 1).toDouble()),
                    "premise" to JsonValue.Str(question.premise),
                    "question" to JsonValue.Str(question.question),
                    "answer" to JsonValue.Str(question.answer),
                    "referenceAnswer" to JsonValue.Str(question.referenceAnswer),
                    "unknown" to JsonValue.Bool(question.unknown),
                )
            }
        ),
    )

    private fun guarded(block: () -> ToolResult): ToolResult = try {
        block()
    } catch (failure: ToolFailure) {
        ToolResult.Error(failure.message ?: "考试工具调用失败")
    } catch (throwable: Throwable) {
        ToolResult.Error("考试工具内部错误：${throwable.message ?: throwable::class.java.simpleName}")
    }
}

private class ToolFailure(message: String) : Exception(message)

/**
 * 阶段 C 一行接入：
 * ```
 * val examTools = registerExamTools(registry, { uiState.examSession }, { uiState.examSession = it })
 * ```
 * [current] 返回 null 表示当前没有进行中的考试；[update] 收到的一定是全新实例。
 */
fun registerExamTools(
    registry: ToolRegistry,
    current: () -> ExamSession?,
    update: (ExamSession) -> Unit,
): ExamTools = ExamTools(current, update).register(registry)
