package com.aiclassroom.app.plugin

import android.content.Context
import android.util.Log
import com.aiclassroom.feature.plugin.PluginClock
import com.aiclassroom.feature.plugin.PluginExtensionType
import com.aiclassroom.feature.plugin.PluginHostApi
import com.aiclassroom.feature.plugin.PluginPackage
import com.aiclassroom.feature.plugin.PluginRuntime
import com.aiclassroom.feature.plugin.PluginStateStore
import com.aiclassroom.feature.plugin.ui.PluginUiParser
import com.aiclassroom.feature.plugin.ui.UiNode
import java.io.File
import org.json.JSONObject

/** 插件相关日志统一走这个 tag，方便 logcat 里过滤钩子与装载结果。 */
const val PLUGIN_LOG_TAG = "PluginHost"

/**
 * prefs 实现的插件状态存储：一个插件一条 JSON，键名 plugin_state_<id>。
 * feature-plugin 的 ctx.storage / host.storageGet / host.storagePut 都建在它上面。
 */
class PrefsPluginStateStore(context: Context) : PluginStateStore {
    private val prefs = context.getSharedPreferences("ai_classroom_14", Context.MODE_PRIVATE)

    override fun read(pluginId: String): String? = prefs.getString(key(pluginId), null)

    override fun write(pluginId: String, json: String) {
        prefs.edit().putString(key(pluginId), json).apply()
    }

    private fun key(pluginId: String): String = "plugin_state_$pluginId"
}

/** 会渲染进界面的扩展点类型（ui_overlay / message_decorator / bar_button）。 */
private val UI_EXTENSION_TYPES: Set<PluginExtensionType> = linkedSetOf(
    PluginExtensionType.UI_OVERLAY,
    PluginExtensionType.MESSAGE_DECORATOR,
    PluginExtensionType.BAR_BUTTON,
)

/** 已装载插件在设置页要展示的那点信息。 */
data class LoadedPluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val permissions: List<String>,
    val promptHook: Boolean,
) {
    /** 一句话能力的摘要（设置页一行显示得下）。 */
    val summary: String
        get() {
            val parts = mutableListOf<String>()
            if (promptHook) parts += "改写发送前的提示词"
            if (permissions.isNotEmpty()) parts += "权限：" + permissions.joinToString(" / ")
            return parts.joinToString(" · ").ifBlank { "未声明权限与扩展点" }
        }
}

/**
 * 一个界面扩展点（ui_overlay / message_decorator / bar_button）在宿主侧的解析结果。
 *
 * [root] 为 null 表示没有 ui.json（旧写法：button 走 entry、decorator 走 entry）或解析失败，
 * 解析失败时 [errors] 里是逐条可读的错误，由 [PluginManager] 写进日志，绝不静默。
 */
data class PluginUiBinding(
    val pluginId: String,
    val type: PluginExtensionType,
    val slot: String,
    val label: String?,
    val entry: String?,
    val configFile: String,
    val root: UiNode?,
    val errors: List<String>,
    val assets: File,
)

/** 一次导入/安装的结果。Conflict 只表示「需要用户确认覆盖」，还没有落盘。 */
sealed class PluginImportResult {
    data class Installed(val info: LoadedPluginInfo, val replaced: Boolean) : PluginImportResult()

    /** 已有同 id 插件：把包留在内存里，等用户点「覆盖」再调 [installPluginPackage]。 */
    data class Conflict(
        val pluginId: String,
        val name: String,
        val bytes: ByteArray,
        val sourceName: String,
    ) : PluginImportResult()

    data class Failed(val message: String) : PluginImportResult()
}

/**
 * 插件装载中心：扫描 / 导入 / 卸载，全部落盘到 filesDir/plugins/<pluginId>/。
 *
 * 目录约定：每个插件一个子目录，包内的 manifest.json / logic.js / ui.json / assets 按原相对路径存下来。
 * 装载失败只记录日志，绝不向上抛——插件坏掉不能影响 app 启动与发消息。
 */
class PluginManager(
    context: Context,
    private val host: PluginHostApi,
    private val stateStore: PluginStateStore,
    private val logger: (String) -> Unit = { line -> Log.i(PLUGIN_LOG_TAG, line) },
) {
    private val root = File(context.filesDir, "plugins")
    private val clock = PluginClock.SYSTEM
    private val lock = Any()
    private val runtimes = LinkedHashMap<String, PluginRuntime>()

    /** 界面扩展点的解析缓存：装载 / 卸载 / 覆盖时清空，平时每次重组都复用同一份。 */
    @Volatile
    private var uiCache: List<PluginUiBinding>? = null

    /** 当前装载的运行时快照（钩子按这个顺序串行执行）。 */
    fun snapshot(): List<PluginRuntime> = synchronized(lock) { runtimes.values.toList() }

    fun infos(): List<LoadedPluginInfo> = snapshot().map { it.info() }

    // ---------------------------------------------------------------- 界面插槽

    /** 所有已装载插件的界面扩展点（解析结果已缓存）。 */
    fun uiBindings(): List<PluginUiBinding> {
        uiCache?.let { return it }
        val built = snapshot().flatMap { runtime -> bindingsOf(runtime) }
        uiCache = built
        return built
    }

    /** 指定插槽上的界面扩展点；没有插件声明时返回空列表，渲染点据此直接不渲染。 */
    fun uiBindings(slot: String): List<PluginUiBinding> = uiBindings().filter { it.slot == slot }

    /** 插件脚本里顶层 state 的 JSON 快照，供 state.xxx 取值表达式使用。 */
    fun uiStates(): Map<String, JSONObject?> = snapshot().associate { runtime ->
        runtime.manifest.id to runCatching {
            runtime.stateJson()?.let { text -> JSONObject(text) }
        }.onFailure {
            logger("读取插件 " + runtime.manifest.id + " 的 state 失败：" + (it.message ?: it.javaClass.simpleName))
        }.getOrNull()
    }

    /** 宿主调插件界面回调：由调用方决定线程（主 app 放在 IO），异常只记日志。 */
    fun invokeUiEntry(pluginId: String, entry: String, event: String, value: String?) {
        val runtime = snapshot().firstOrNull { it.manifest.id == pluginId } ?: return
        try {
            runtime.runUiEvent(entry, event, value)
        } catch (t: Throwable) {
            logger("插件 " + pluginId + " 的界面回调 " + entry + " 执行失败：" + (t.message ?: t.javaClass.simpleName))
        }
    }

    private fun bindingsOf(runtime: PluginRuntime): List<PluginUiBinding> {
        val assets = File(root, runtime.manifest.id)
        return runtime.manifest.extensions.mapNotNull { extension ->
            val slot = extension.slot ?: return@mapNotNull null
            if (extension.type !in UI_EXTENSION_TYPES) return@mapNotNull null
            val configFile = extension.configFile
            val parsed = if (configFile.endsWith(".json")) {
                val text = runtime.textFile(configFile)
                if (text == null) {
                    logger("插件 " + runtime.manifest.id + " 缺少界面文件 " + configFile + "（manifest 声明了它）")
                    null
                } else {
                    PluginUiParser.parse(text).also { result ->
                        if (!result.ok) {
                            logger(
                                "插件 " + runtime.manifest.id + " 的 " + configFile +
                                    " 解析失败：" + result.errors.joinToString("；")
                            )
                        }
                    }
                }
            } else {
                null
            }
            PluginUiBinding(
                pluginId = runtime.manifest.id,
                type = extension.type,
                slot = slot,
                label = extension.label,
                entry = extension.entry,
                configFile = configFile,
                root = parsed?.root,
                errors = parsed?.errors.orEmpty(),
                assets = assets,
            )
        }
    }

    fun exists(pluginId: String): Boolean = synchronized(lock) { runtimes.containsKey(pluginId) }

    /** 启动时扫描插件目录；返回成功装载的数量。 */
    fun loadAll(): Int {
        if (!root.isDirectory) return 0
        var loaded = 0
        root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { dir ->
            try {
                loadDirectory(dir)
                loaded++
            } catch (t: Throwable) {
                logger("插件 ${dir.name} 装载失败（已跳过）：${t.message ?: t.javaClass.simpleName}")
            }
        }
        if (loaded > 0) logger("已装载 $loaded 个插件")
        return loaded
    }

    /** 单目录装载：目录里的文件重新拼成一个包再交给 PluginRuntime 校验。 */
    private fun loadDirectory(dir: File) {
        val entries = LinkedHashMap<String, ByteArray>()
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.relativeTo(dir).path.replace(File.separatorChar, '/')
            entries[relative] = file.readBytes()
        }
        if (entries.isEmpty()) throw IllegalStateException("目录里没有插件文件")
        val pkg = PluginPackage.of(entries)
        val runtime = PluginRuntime.create(pkg, host, stateStore, clock, logger)
        synchronized(lock) {
            runtimes.remove(runtime.manifest.id)?.close()
            runtimes[runtime.manifest.id] = runtime
            uiCache = null
        }
        logger("已装载插件 ${runtime.manifest.id}（${runtime.manifest.name} v${runtime.manifest.version}）")
    }

    /**
     * 落盘 + 注册一个已经解析好的插件包。
     * 先建 runtime 再写盘：语法错误 / 入口函数缺失的包不会在磁盘上留下半成品。
     */
    fun install(pkg: PluginPackage, replace: Boolean): PluginImportResult {
        val validation = pkg.validate()
        val manifest = validation.manifest
        if (!validation.ok || manifest == null) {
            return PluginImportResult.Failed("插件包校验未通过：" + validation.errors.joinToString("；"))
        }
        if (!replace && exists(manifest.id)) {
            return PluginImportResult.Failed("已存在同 id 的插件「${manifest.name}」（${manifest.id}）")
        }
        val runtime = try {
            PluginRuntime.create(pkg, host, stateStore, clock, logger)
        } catch (t: Throwable) {
            return PluginImportResult.Failed("插件装载失败：" + (t.message ?: t.javaClass.simpleName))
        }
        try {
            writeDirectory(File(root, manifest.id), pkg)
        } catch (t: Throwable) {
            runtime.close()
            return PluginImportResult.Failed("插件写入失败：" + (t.message ?: t.javaClass.simpleName))
        }
        val replaced = synchronized(lock) {
            uiCache = null
            runtimes.put(manifest.id, runtime) != null
        }
        logger((if (replaced) "已覆盖插件 " else "已导入插件 ") + "${manifest.id}（${manifest.name} v${manifest.version}）")
        return PluginImportResult.Installed(runtime.info(), replaced)
    }

    fun uninstall(pluginId: String): Boolean {
        val removed = synchronized(lock) {
            uiCache = null
            runtimes.remove(pluginId)
        }
        runCatching { File(root, pluginId).deleteRecursively() }
        removed?.close()
        stateStore.write(pluginId, "")
        logger("已卸载插件 $pluginId")
        return removed != null
    }

    fun closeAll() {
        synchronized(lock) {
            uiCache = null
            runtimes.values.forEach { runCatching { it.close() } }
            runtimes.clear()
        }
    }

    private fun writeDirectory(dir: File, pkg: PluginPackage) {
        if (dir.exists()) dir.deleteRecursively()
        if (!dir.mkdirs() && !dir.isDirectory) throw IllegalStateException("无法创建插件目录 ${dir.absolutePath}")
        for ((path, bytes) in pkg.entries) {
            val target = File(dir, path)
            val parent = target.parentFile
            if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
                throw IllegalStateException("无法创建目录 ${parent.absolutePath}")
            }
            target.writeBytes(bytes)
        }
    }
}

private fun PluginRuntime.info(): LoadedPluginInfo = LoadedPluginInfo(
    id = manifest.id,
    name = manifest.name,
    version = manifest.version,
    description = manifest.description,
    permissions = manifest.permissions,
    promptHook = promptHookEntry != null,
)

/** 从 .aip / .zip 字节导入：解析 → 校验 → （同 id 时）要用户确认 → 落盘注册。 */
fun importPluginArchive(
    manager: PluginManager,
    bytes: ByteArray,
    sourceName: String,
    replace: Boolean,
): PluginImportResult {
    val pkg = try {
        PluginPackage.read(bytes, sourceName)
    } catch (t: Throwable) {
        return PluginImportResult.Failed("插件包解析失败：" + (t.message ?: t.javaClass.simpleName))
    }
    val validation = pkg.validate()
    val manifest = validation.manifest
    if (!validation.ok || manifest == null) {
        return PluginImportResult.Failed("插件包校验未通过：" + validation.errors.joinToString("；"))
    }
    if (!replace && manager.exists(manifest.id)) {
        return PluginImportResult.Conflict(manifest.id, manifest.name, bytes, sourceName)
    }
    return manager.install(pkg, replace)
}
