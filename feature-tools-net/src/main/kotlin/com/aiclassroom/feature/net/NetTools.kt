package com.aiclassroom.feature.net

import com.aiclassroom.feature.exam.tool.ToolHandler
import com.aiclassroom.feature.exam.tool.ToolRegistry
import com.aiclassroom.feature.exam.tool.ToolResult

/**
 * 把网络工具注册进 [ToolRegistry]。
 *
 * @param enabled 总开关，每次调用前读一次；返回 false 时不发起任何网络请求，直接拒绝。
 * @param logger 调用日志回调（请求头已脱敏），由主 app 决定写文件还是打日志。
 */
fun registerNetTools(registry: ToolRegistry, enabled: () -> Boolean, logger: (String) -> Unit) {
    val tool = NetRequestTool(logger)
    registry.register(object : ToolHandler by tool {
        override suspend fun execute(argumentsJson: String): ToolResult {
            if (!enabled()) {
                logger("[" + timestamp() + "] net_request (disabled) -> REJECTED 网络工具总开关已关闭")
                return ToolResult.Error("网络工具已被用户关闭（总开关关闭中），请提示用户在设置里打开后再试。")
            }
            return tool.execute(argumentsJson)
        }
    })
}
