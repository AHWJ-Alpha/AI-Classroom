package com.aiclassroom.feature.fs

import com.aiclassroom.feature.exam.tool.ToolHandler
import com.aiclassroom.feature.exam.tool.ToolResult
import java.io.File
import java.util.concurrent.CancellationException

/**
 * 文件工具的公共骨架：统一做参数解析、路径校验与异常收口。
 * execute 里绝不向外抛异常，一切失败都变成 [ToolResult.Error]（协程取消除外）。
 */
internal abstract class FsTool(protected val guard: FsPathGuard) : ToolHandler {

    final override suspend fun execute(argumentsJson: String): ToolResult =
        try {
            runTool(argumentsJson)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ToolResult.Error(name + " 执行失败：" + (throwable.message ?: throwable::class.java.simpleName))
        }

    protected abstract fun runTool(argumentsJson: String): ToolResult

    protected fun arguments(argumentsJson: String): Map<String, Any?> = Json.parseObject(argumentsJson)

    /** 路径校验通过后用规范化路径执行；不通过直接把拒绝原因回给模型。 */
    protected fun withResolvedPath(rawPath: String, block: (File) -> ToolResult): ToolResult =
        when (val resolution = guard.resolve(rawPath)) {
            is PathResolution.Allowed -> block(resolution.file)
            is PathResolution.Denied -> ToolResult.Error(resolution.reason)
        }
}

/** 顺序拼一个扁平 JSON 对象；值已经是 JSON 文本。 */
internal fun jsonObject(vararg fields: Pair<String, String>): String =
    fields.joinToString(separator = ",", prefix = "{", postfix = "}") { field ->
        Json.quote(field.first) + ":" + field.second
    }

internal fun Map<String, Any?>.requiredString(key: String): String {
    val value = this[key] ?: throw IllegalArgumentException("缺少必需参数 " + key)
    return value as? String ?: throw IllegalArgumentException("参数 " + key + " 必须是字符串")
}

internal fun Map<String, Any?>.optionalLong(key: String, default: Long): Long {
    val value = this[key] ?: return default
    return when (value) {
        is Number -> value.toLong()
        is String -> value.trim().toLongOrNull()
            ?: throw IllegalArgumentException("参数 " + key + " 必须是整数")
        else -> throw IllegalArgumentException("参数 " + key + " 必须是整数")
    }
}

internal fun Map<String, Any?>.optionalBoolean(key: String, default: Boolean): Boolean {
    val value = this[key] ?: return default
    return when (value) {
        is Boolean -> value
        is String -> when (value.trim().lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> throw IllegalArgumentException("参数 " + key + " 必须是布尔值")
        }
        else -> throw IllegalArgumentException("参数 " + key + " 必须是布尔值")
    }
}
