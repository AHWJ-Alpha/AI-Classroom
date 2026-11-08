package com.aiclassroom.feature.fs

import com.aiclassroom.feature.exam.tool.ToolResult
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** fs_list：列出目录的直接子项。 */
internal class FsListTool(guard: FsPathGuard) : FsTool(guard) {

    override val name: String = "fs_list"

    override val description: String =
        "列出允许目录下某个目录的直接子项（名称 / 大小 / 是否为目录）。目录不存在或不是目录时返回错误。"

    override val parametersJson: String = """
        {
          "type": "object",
          "properties": {
            "path": {
              "type": "string",
              "description": "要列出的目录路径。可传相对于第一个允许根目录的相对路径。"
            }
          },
          "required": ["path"],
          "additionalProperties": false
        }
    """.trimIndent()

    override fun runTool(argumentsJson: String): ToolResult {
        val args = arguments(argumentsJson)
        val rawPath = args.requiredString("path")
        return withResolvedPath(rawPath) { directory ->
            if (!directory.exists()) return@withResolvedPath ToolResult.Error("目录不存在：" + directory.path)
            if (!directory.isDirectory) return@withResolvedPath ToolResult.Error(
                "不是目录：" + directory.path + "（读文件请用 fs_read）"
            )
            val children = directory.listFiles()
                ?: return@withResolvedPath ToolResult.Error("无法读取目录内容：" + directory.path)
            val sorted = children.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            val shown = sorted.take(MAX_ENTRIES)

            val json = StringBuilder()
            json.append('{')
            json.append("\"path\":").append(Json.quote(directory.path)).append(',')
            json.append("\"totalEntries\":").append(sorted.size).append(',')
            json.append("\"truncated\":").append(Json.bool(sorted.size > shown.size)).append(',')
            json.append("\"entries\":[")
            shown.forEachIndexed { index, child ->
                if (index > 0) json.append(',')
                json.append('{')
                json.append("\"name\":").append(Json.quote(child.name)).append(',')
                json.append("\"isDirectory\":").append(Json.bool(child.isDirectory)).append(',')
                json.append("\"size\":").append(if (child.isDirectory) 0L else child.length())
                json.append('}')
            }
            json.append("]}")
            return@withResolvedPath ToolResult.Ok(json.toString())
        }
    }

    private companion object {
        const val MAX_ENTRIES = 1000
    }
}

/** fs_read：按 UTF-8 读文本，默认上限 256KB。 */
internal class FsReadTool(guard: FsPathGuard) : FsTool(guard) {

    override val name: String = "fs_read"

    override val description: String =
        "读取一个 UTF-8 文本文件的内容。默认最多读取 256KB（262144 字节），超过上限会报错并给出实际大小，" +
            "可用 maxBytes 提高上限（硬上限 8MB）。"

    override val parametersJson: String = """
        {
          "type": "object",
          "properties": {
            "path": {
              "type": "string",
              "description": "要读取的文件路径。可传相对于第一个允许根目录的相对路径。"
            },
            "maxBytes": {
              "type": "integer",
              "minimum": 1,
              "maximum": 8388608,
              "description": "本次允许读取的最大字节数，默认 262144（256KB）。"
            }
          },
          "required": ["path"],
          "additionalProperties": false
        }
    """.trimIndent()

    override fun runTool(argumentsJson: String): ToolResult {
        val args = arguments(argumentsJson)
        val rawPath = args.requiredString("path")
        val requested = args.optionalLong("maxBytes", DEFAULT_MAX_BYTES)
        if (requested < 1L) return ToolResult.Error("maxBytes 必须大于 0，收到 " + requested)
        val limit = minOf(requested, HARD_MAX_BYTES)

        return withResolvedPath(rawPath) { file ->
            if (!file.exists()) return@withResolvedPath ToolResult.Error("文件不存在：" + file.path)
            if (file.isDirectory) return@withResolvedPath ToolResult.Error(
                "这是一个目录，请用 fs_list：" + file.path
            )
            val size = file.length()
            if (size > limit) {
                return@withResolvedPath ToolResult.Error(
                    "文件过大：" + file.path + " 共 " + size + " 字节，超过本次上限 " + limit +
                        " 字节。可提高 maxBytes（硬上限 " + HARD_MAX_BYTES + "）或换用更小的文件。"
                )
            }
            val bytes = file.readBytes()
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val text = try {
                decoder.decode(ByteBuffer.wrap(bytes)).toString()
            } catch (exception: CharacterCodingException) {
                return@withResolvedPath ToolResult.Error("不是合法的 UTF-8 文本文件：" + file.path)
            }
            return@withResolvedPath ToolResult.Ok(
                jsonObject(
                    "path" to Json.quote(file.path),
                    "bytes" to bytes.size.toString(),
                    "content" to Json.quote(text)
                )
            )
        }
    }

    private companion object {
        const val DEFAULT_MAX_BYTES = 256L * 1024L
        const val HARD_MAX_BYTES = 8L * 1024L * 1024L
    }
}

/** fs_write：写入（覆盖或追加）UTF-8 文本。 */
internal class FsWriteTool(guard: FsPathGuard) : FsTool(guard) {

    override val name: String = "fs_write"

    override val description: String =
        "把文本以 UTF-8 写入允许目录下的文件。父目录不存在会自动创建。" +
            "默认覆盖原文件，append=true 时追加到末尾。"

    override val parametersJson: String = """
        {
          "type": "object",
          "properties": {
            "path": {
              "type": "string",
              "description": "要写入的文件路径。可传相对于第一个允许根目录的相对路径。"
            },
            "content": {
              "type": "string",
              "description": "要写入的文本内容。"
            },
            "append": {
              "type": "boolean",
              "description": "true 表示追加到文件末尾，默认 false 表示覆盖。"
            }
          },
          "required": ["path", "content"],
          "additionalProperties": false
        }
    """.trimIndent()

    override fun runTool(argumentsJson: String): ToolResult {
        val args = arguments(argumentsJson)
        val rawPath = args.requiredString("path")
        val content = args.requiredString("content")
        val append = args.optionalBoolean("append", false)

        return withResolvedPath(rawPath) { file ->
            if (file.isDirectory) return@withResolvedPath ToolResult.Error(
                "目标是已存在的目录，不能写入：" + file.path
            )
            // 父目录一定也在白名单内（规范化后整条路径都在根目录下），可以安全创建。
            val parent = file.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
                return@withResolvedPath ToolResult.Error("无法创建父目录：" + parent.path)
            }
            val bytes = content.toByteArray(Charsets.UTF_8)
            FileOutputStream(file, append).use { it.write(bytes) }
            return@withResolvedPath ToolResult.Ok(
                jsonObject(
                    "path" to Json.quote(file.path),
                    "bytesWritten" to bytes.size.toString(),
                    "append" to Json.bool(append),
                    "totalBytes" to file.length().toString()
                )
            )
        }
    }
}

/** fs_delete：删除文件（不接受目录）。 */
internal class FsDeleteTool(guard: FsPathGuard) : FsTool(guard) {

    override val name: String = "fs_delete"

    override val description: String = "删除允许目录下的一个文件。只删文件，不删目录。"

    override val parametersJson: String = """
        {
          "type": "object",
          "properties": {
            "path": {
              "type": "string",
              "description": "要删除的文件路径。可传相对于第一个允许根目录的相对路径。"
            }
          },
          "required": ["path"],
          "additionalProperties": false
        }
    """.trimIndent()

    override fun runTool(argumentsJson: String): ToolResult {
        val args = arguments(argumentsJson)
        val rawPath = args.requiredString("path")
        return withResolvedPath(rawPath) { file ->
            if (!file.exists()) return@withResolvedPath ToolResult.Error("文件不存在：" + file.path)
            if (file.isDirectory) return@withResolvedPath ToolResult.Error(
                "是目录，fs_delete 只删文件：" + file.path
            )
            val size = file.length()
            if (!file.delete()) return@withResolvedPath ToolResult.Error("删除失败：" + file.path)
            return@withResolvedPath ToolResult.Ok(
                jsonObject(
                    "path" to Json.quote(file.path),
                    "deleted" to "true",
                    "bytesRemoved" to size.toString()
                )
            )
        }
    }
}
