package com.aiclassroom.feature.exam.tool

/**
 * 工具调用的统一入口。考试 / 文件 / 网络三条并行开发线都实现这个接口，
 * 由同一个 [ToolRegistry] 汇总后交给模型的 function calling 列表。
 */
interface ToolHandler {
    /** 工具名，如 exam_create / fs_read / net_request。 */
    val name: String

    /** 给模型看的说明。 */
    val description: String

    /** OpenAI function calling 的 parameters JSON Schema 字符串。 */
    val parametersJson: String

    /** 执行一次工具调用；实现方必须自行把失败转成 [ToolResult.Error]，不要让异常逃逸。 */
    suspend fun execute(argumentsJson: String): ToolResult
}

sealed class ToolResult {
    /** 成功：contentJson 是回填给模型的工具结果。 */
    data class Ok(val contentJson: String) : ToolResult()

    /** 失败：message 是回填给模型的错误说明。 */
    data class Error(val message: String) : ToolResult()
}
