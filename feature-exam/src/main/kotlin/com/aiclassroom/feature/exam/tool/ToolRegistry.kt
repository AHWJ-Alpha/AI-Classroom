package com.aiclassroom.feature.exam.tool

import java.util.concurrent.CancellationException

/**
 * 工具注册表：同一个实例里可以同时注册考试 / 文件 / 网络工具，
 * 按注册顺序保留，便于稳定地生成 function calling 的 tools 数组。
 */
class ToolRegistry {
    private val handlers = LinkedHashMap<String, ToolHandler>()

    fun register(handler: ToolHandler) {
        synchronized(handlers) { handlers[handler.name] = handler }
    }

    fun all(): List<ToolHandler> = synchronized(handlers) { handlers.values.toList() }

    fun find(name: String): ToolHandler? = synchronized(handlers) { handlers[name] }

    /**
     * 统一调度入口：名字未注册与实现抛出的异常都在这里收口，
     * 调用方永远只会拿到结构化的 [ToolResult]，协程取消照常向上传递。
     */
    suspend fun dispatch(name: String, argumentsJson: String): ToolResult {
        val handler = find(name) ?: return ToolResult.Error("unknown tool: $name")
        return try {
            handler.execute(argumentsJson)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ToolResult.Error("tool $name failed: ${throwable.message ?: throwable::class.java.simpleName}")
        }
    }
}
