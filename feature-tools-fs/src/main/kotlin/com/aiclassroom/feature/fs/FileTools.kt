package com.aiclassroom.feature.fs

import com.aiclassroom.feature.exam.tool.ToolRegistry

/**
 * 文件工具的对外注册入口，阶段 C 一行接入：
 *
 *   FileTools.registerFileTools(registry, listOf(context.filesDir.absolutePath, extraDir))
 *
 * 白名单即安全边界：传空列表等于关闭文件工具，任何调用都会被拒绝。
 */
object FileTools {

    const val LIST = "fs_list"
    const val READ = "fs_read"
    const val WRITE = "fs_write"
    const val DELETE = "fs_delete"

    @JvmStatic
    fun registerFileTools(registry: ToolRegistry, allowedRoots: List<String>) {
        val guard = FsPathGuard(allowedRoots)
        listOf(FsListTool(guard), FsReadTool(guard), FsWriteTool(guard), FsDeleteTool(guard))
            .forEach { registry.register(it) }
    }
}
