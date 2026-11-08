package com.aiclassroom.feature.plugin

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 插件包解析失败（不是 ZIP、缺文件、路径穿越、体积超限）。
 * 与 [ManifestValidation] 的错误不同：这一层是「包本身打不开」，还没有进到 schema 校验。
 */
class PluginPackageException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 一个已解包的插件：包内相对路径 -> 原始字节。
 *
 * 包就是一个 ZIP（后缀 .aip 或 .zip），约定结构：
 * manifest.json（必需） / logic.js（可选） / ui.json（可选） / assets 目录（可选）。
 */
class PluginPackage private constructor(val entries: Map<String, ByteArray>) {

    val manifestText: String? get() = text(PluginPackage.MANIFEST_FILE)
    val logicText: String? get() = text(PluginPackage.LOGIC_FILE)
    val uiText: String? get() = text(PluginPackage.UI_FILE)

    val fileNames: Set<String> get() = entries.keys

    fun text(path: String): String? = entries[path]?.toString(Charsets.UTF_8)

    fun bytes(path: String): ByteArray? = entries[path]

    fun has(path: String): Boolean = entries.containsKey(path)

    /** 校验 manifest 与包内文件的一致性。 */
    fun validate(): ManifestValidation = ManifestValidator.validate(manifestText, logicText, fileNames)

    companion object {
        const val MANIFEST_FILE = "manifest.json"
        const val LOGIC_FILE = "logic.js"
        const val UI_FILE = "ui.json"
        const val EXTENSION_AIP = "aip"
        const val EXTENSION_ZIP = "zip"

        /** 单个文件上限 8MB、整包上限 16MB、文件数上限 256，防 zip bomb。 */
        const val MAX_ENTRY_BYTES = 8 * 1024 * 1024
        const val MAX_TOTAL_BYTES = 16 * 1024 * 1024
        const val MAX_ENTRY_COUNT = 256

        val ALLOWED_SUFFIXES: List<String> = listOf(EXTENSION_AIP, EXTENSION_ZIP)

        fun isPluginFileName(fileName: String): Boolean {
            val lower = fileName.lowercase()
            return ALLOWED_SUFFIXES.any { lower.endsWith(".$it") }
        }

        /** 从磁盘读插件包；后缀必须是 .aip / .zip。 */
        fun read(file: File): PluginPackage {
            if (!isPluginFileName(file.name)) {
                throw PluginPackageException("插件包后缀必须是 .aip 或 .zip：${file.name}")
            }
            if (!file.isFile) throw PluginPackageException("插件包不存在：${file.absolutePath}")
            return read(file.readBytes(), file.name)
        }

        /** 从 ZIP 字节读插件包。 */
        fun read(bytes: ByteArray, sourceName: String = "plugin.aip"): PluginPackage {
            val collected = LinkedHashMap<String, ByteArray>()
            var total = 0L
            try {
                ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) {
                            zip.closeEntry()
                            continue
                        }
                        val path = normalizeEntryPath(entry.name, sourceName)
                        if (collected.size >= MAX_ENTRY_COUNT) {
                            throw PluginPackageException("插件包内文件过多（上限 $MAX_ENTRY_COUNT）：$sourceName")
                        }
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            total += read
                            if (out.size() > MAX_ENTRY_BYTES) {
                                throw PluginPackageException("插件包内文件过大（单文件上限 $MAX_ENTRY_BYTES 字节）：$path")
                            }
                            if (total > MAX_TOTAL_BYTES) {
                                throw PluginPackageException("插件包解压后过大（上限 $MAX_TOTAL_BYTES 字节）：$sourceName")
                            }
                        }
                        collected[path] = out.toByteArray()
                        zip.closeEntry()
                    }
                }
            } catch (e: PluginPackageException) {
                throw e
            } catch (e: Exception) {
                throw PluginPackageException("插件包不是合法的 ZIP：$sourceName（${e.message}）", e)
            }
            if (collected.isEmpty()) throw PluginPackageException("插件包是空的：$sourceName")
            return PluginPackage(collected)
        }

        /** 直接由「文件名 -> 内容」构造，方便宿主构建工具与测试使用。 */
        fun of(entries: Map<String, ByteArray>): PluginPackage {
            val collected = LinkedHashMap<String, ByteArray>()
            for ((rawPath, content) in entries) {
                collected[normalizeEntryPath(rawPath, "memory")] = content
            }
            return PluginPackage(collected)
        }

        fun ofText(entries: Map<String, String>): PluginPackage =
            of(entries.mapValues { (_, value) -> value.toByteArray(Charsets.UTF_8) })

        /** 拒绝绝对路径、盘符、反斜杠与 .. 穿越。 */
        private fun normalizeEntryPath(raw: String, sourceName: String): String {
            val path = raw.trim()
            if (path.isEmpty()) throw PluginPackageException("插件包里有空文件名：$sourceName")
            if (path.startsWith("/") || path.contains("\\") || path.contains(":")) {
                throw PluginPackageException("插件包里有非法路径：$raw")
            }
            if (!isSafeRelativePath(path)) throw PluginPackageException("插件包里有非法路径：$raw")
            return path
        }

        internal fun isSafeRelativePath(path: String): Boolean {
            if (path.isBlank() || path.startsWith("/") || path.contains("\\") || path.contains(":")) return false
            return path.split('/').none { it.isEmpty() || it == "." || it == ".." }
        }
    }
}
