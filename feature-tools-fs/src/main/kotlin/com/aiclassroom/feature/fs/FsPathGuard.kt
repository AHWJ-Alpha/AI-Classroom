package com.aiclassroom.feature.fs

import java.io.File
import java.io.IOException

/** 路径校验结果：要么给出规范化后的真实路径，要么给出拒绝原因。 */
internal sealed class PathResolution {
    data class Allowed(val file: File) : PathResolution()
    data class Denied(val reason: String) : PathResolution()
}

/**
 * 文件工具的安全边界。
 *
 * 规则：
 * 1. 允许根目录 = 应用私有根 + 调用方传入的额外白名单，构造时全部规范化；
 * 2. 任何访问路径都先 canonicalize（getCanonicalPath 会消解 .. 与符号链接），
 *    再判断是否落在某个白名单根目录内 —— 顺序不能颠倒，否则 ../ 和软链接能绕过；
 * 3. 白名单为空时拒绝一切操作。
 */
internal class FsPathGuard(allowedRoots: List<String>) {

    private val roots: List<File> = allowedRoots
        .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        .mapNotNull { raw -> runCatching { harden(File(raw).canonicalFile) }.getOrNull() }
        .distinctBy { comparablePath(it) }

    val isEmpty: Boolean get() = roots.isEmpty()

    fun describeRoots(): String = if (roots.isEmpty()) "（空）" else roots.joinToString("、") { it.path }

    fun resolve(rawPath: String): PathResolution {
        val path = rawPath.trim()
        if (path.isEmpty()) return PathResolution.Denied("path 不能为空")
        if (roots.isEmpty()) return PathResolution.Denied("没有配置任何允许访问的根目录，已拒绝全部文件操作")

        val primary = roots.first()
        val requested = File(path)
        // 相对路径按第一个白名单根目录展开，模型直接传 "notes/a.txt" 也能用。
        val absolute = if (requested.isAbsolute) requested else File(primary, path)
        val canonical = try {
            harden(absolute.canonicalFile)
        } catch (exception: IOException) {
            return PathResolution.Denied("无法规范化路径 " + path + "：" + (exception.message ?: ""))
        } catch (exception: SecurityException) {
            return PathResolution.Denied("路径不可访问 " + path + "：" + (exception.message ?: ""))
        }

        roots.firstOrNull { contains(it, canonical) }
            ?: return PathResolution.Denied(
                "路径越权：" + canonical.path + " 不在允许目录内（允许：" + describeRoots() + "）"
            )
        return PathResolution.Allowed(canonical)
    }

    /**
     * 补一层真实路径解析。
     *
     * getCanonicalPath 能消解 ".." 与（Linux/Android 上的）符号链接，但在 Windows 上
     * 不会解析目录联接（junction），实测 junction 可以整条路径绕过白名单。
     * toRealPath 走系统真实路径（GetFinalPathNameByHandle）能解出来，因此对已存在的那一段
     * 再取一次真实路径，不存在的那一段按名字拼回去。
     */
    private fun harden(file: File): File {
        var existing: File? = file
        val missing = ArrayList<String>()
        while (existing != null && !existing.exists()) {
            missing.add(0, existing.name)
            existing = existing.parentFile
        }
        if (existing == null) return file
        val real = runCatching { existing.toPath().toRealPath().toFile() }.getOrElse { return file }
        return missing.fold(real) { accumulated, name -> File(accumulated, name) }
    }

    /** 规范化后的真实路径是否位于白名单根目录之内（含根目录自身）。 */
    private fun contains(root: File, candidate: File): Boolean {
        val rootPath = comparablePath(root)
        val candidatePath = comparablePath(candidate)
        if (candidatePath == rootPath) return true
        val prefix = if (rootPath.endsWith(File.separator)) rootPath else rootPath + File.separator
        return candidatePath.startsWith(prefix)
    }

    /** Windows 文件系统大小写不敏感，比较前统一小写，避免同一目录被误判成越权。 */
    private fun comparablePath(file: File): String =
        if (File.separatorChar == '\\') file.path.replace('/', '\\').lowercase() else file.path
}
