package com.aiclassroom.app.plugin

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 内置插件：随 APK 一起发布，首次启动（或版本升级）时自动释放到插件目录。
 *
 * 为什么需要它：这些插件原来只躺在 `feature-plugin/src/test/resources` 里，
 * 是给单测用的测试资源，**从没打进过 APK** —— 所以每台新设备装完都得手动 adb push 一遍，
 * 用户侧看起来就是「装好了但插件列表是空的」。内置插件本来该是装完就能用。
 *
 * 释放策略：
 * - 用 [VERSION] 做闸门：版本没变就整个跳过，不会每次启动都重写磁盘；
 * - 用户在设置里删掉的内置插件会被记进 [KEY_REMOVED]，升级时不强行塞回来；
 * - 单个插件释放失败只记日志，不影响其它插件（一个坏包不该拖垮整批）。
 */
internal object BuiltinPlugins {

    private const val TAG = "BuiltinPlugins"
    private const val PREFS = "builtin_plugins"
    private const val KEY_VERSION = "version"
    private const val KEY_REMOVED = "removed"

    /** 改了内置插件的内容就把这个数字 +1，升级后会自动重新释放。 */
    private const val VERSION = 1

    private const val ASSET_ROOT = "plugins"

    fun install(context: Context, pluginsDir: File) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_VERSION, 0) == VERSION) return

        val removed = prefs.getStringSet(KEY_REMOVED, emptySet())?.toMutableSet() ?: mutableSetOf()
        val ids = runCatching { context.assets.list(ASSET_ROOT)?.toList().orEmpty() }.getOrElse {
            Log.w(TAG, "读取内置插件列表失败：" + it.message)
            emptyList()
        }
        if (ids.isEmpty()) {
            Log.w(TAG, "assets/" + ASSET_ROOT + " 下没有插件")
            return
        }

        pluginsDir.mkdirs()
        var installed = 0
        ids.forEach { id ->
            if (id in removed) {
                Log.i(TAG, "跳过已删除的内置插件：" + id)
                return@forEach
            }
            if (copyPlugin(context, pluginsDir, id)) installed++
        }
        Log.i(TAG, "内置插件释放完成：" + installed + " / " + ids.size)
        prefs.edit().putInt(KEY_VERSION, VERSION).apply()
    }

    /** 从 assets/plugins/<id>/ 递归拷到 <pluginsDir>/<id>/；已存在的文件直接覆盖（内置内容以 APK 为准）。 */
    private fun copyPlugin(context: Context, pluginsDir: File, id: String): Boolean = runCatching {
        val target = File(pluginsDir, id)
        target.mkdirs()
        copyDir(context, ASSET_ROOT + "/" + id, target)
        true
    }.getOrElse {
        Log.w(TAG, "释放插件失败 " + id + "：" + it.message)
        false
    }

    private fun copyDir(context: Context, assetPath: String, target: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            // 是文件：读出来写到目标
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        target.mkdirs()
        children.forEach { name -> copyDir(context, assetPath + "/" + name, File(target, name)) }
    }

    /** 用户卸载某个内置插件时调用，记下来，升级不再自动塞回。 */
    fun markRemoved(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val removed = prefs.getStringSet(KEY_REMOVED, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (removed.add(id)) prefs.edit().putStringSet(KEY_REMOVED, removed).apply()
    }
}
