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
    private const val VERSION = 12

    private const val ASSET_ROOT = "plugins"

    /**
     * **系统插件**：属于应用功能本身（沉浸模式是主界面的一整个形态，音乐是顶栏常驻入口），
     * 不是「用户装进来的扩展」。所以它们
     * - 不记入 [KEY_REMOVED]（删了也会在下次启动时回来）
     * - 设置页不显示卸载按钮
     * 但**仍然可以被停用**（plugin_enabled_<id>），用户不想用可以关掉，只是不能删。
     */
    val SYSTEM_IDS: Set<String> = setOf("immersive", "music")

    /**
     * 曾经随包发布的**测试件**，现已不再随包：
     * - `msg_badge` 回复徽章：只是「字数·用时」的展示演示
     * - `pet_cat` 宠物猫：纯装饰 sprite
     *
     * 注意 `nightmare`（夜梦画室）**不在**这个清单里 —— 它是给生图用的配置面板，属于功能。
     *
     * 光从 assets 删掉不够：老用户设备上已经释放出来的目录会一直留着，
     * 所以升级时要按这个清单把它们清掉。
     */
    val RETIRED_IDS: Set<String> = setOf("msg_badge", "pet_cat")

    /** 示例插件已全部下线；保留这个入口，免得调用方全要改。 */
    val SAMPLE_IDS: Set<String> = emptySet()

    /** 这个插件是不是系统自带（不可卸载）。 */
    fun isSystem(id: String): Boolean = id in SYSTEM_IDS

    /** 这个插件是不是演示用示例。 */
    fun isSample(id: String): Boolean = id in SAMPLE_IDS

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
        // 下线清理：这些测试件以前随包发布过，只从 assets 删掉不够 ——
        // 老用户设备上已经释放出来的目录会一直留着，插件列表里还是能看到。
        RETIRED_IDS.forEach { id ->
            val dir = File(pluginsDir, id)
            if (dir.exists() && dir.deleteRecursively()) {
                Log.i(TAG, "已清理下线插件：" + id)
            }
        }
        var installed = 0
        ids.forEach { id ->
            // 系统插件无视「用户删过」的记录：它们是应用功能的一部分，
            // 否则用户误删一次，沉浸模式和音乐就永久消失了。
            if (id in removed && !isSystem(id)) {
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
        // 系统插件不接受「已删除」标记：即使调用方漏判，也不会把它记进 removed 名单。
        if (isSystem(id)) {
            Log.w(TAG, "系统插件不允许标记删除：" + id)
            return
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val removed = prefs.getStringSet(KEY_REMOVED, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (removed.add(id)) prefs.edit().putStringSet(KEY_REMOVED, removed).apply()
    }
}
