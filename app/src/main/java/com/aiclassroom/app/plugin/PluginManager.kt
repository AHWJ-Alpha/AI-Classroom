package com.aiclassroom.app.plugin

import android.content.Context
import android.util.Log
import com.aiclassroom.feature.plugin.PluginClock
import com.aiclassroom.feature.plugin.PluginExtensionType
import com.aiclassroom.feature.plugin.PluginHostApi
import com.aiclassroom.feature.plugin.PluginPackage
import com.aiclassroom.feature.plugin.PluginRuntime
import com.aiclassroom.feature.plugin.PluginSetting
import com.aiclassroom.feature.plugin.PluginSettingType
import com.aiclassroom.feature.plugin.PluginSettingsCodec
import com.aiclassroom.feature.plugin.PluginSlots
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
    /** manifest.settings 声明的配置字段（设置页据此生成表单）；没声明时为空。 */
    val settings: List<PluginSetting> = emptyList(),
    /** 插件包总开关的当前状态（prefs: plugin_enabled_<id>，默认开）。 */
    val enabled: Boolean = true,
    /** 当前生效的配置值（已合并 default），设置页表单的初始值。 */
    val settingValues: Map<String, String> = emptyMap(),
) {
    /** 一句话能力的摘要（设置页一行显示得下）。 */
    val summary: String
        get() {
            val parts = mutableListOf<String>()
            if (promptHook) parts += "改写发送前的提示词"
            if (permissions.isNotEmpty()) parts += "权限：" + permissions.joinToString(" / ")
            return parts.joinToString(" · ").ifBlank { "未声明权限与扩展点" }
        }

    /** 必填但没填好的配置项数量（折叠标题里的「未完成 M 项」）。 */
    val unfinishedSettings: Int
        get() = PluginSettingsCodec.missingRequired(settings, settingValues).size
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
    /** ui_overlay 的取数函数：非空时宿主每次渲染前调它拿最新 state（见 PluginManager.uiStates）。 */
    val provider: String? = null,
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
    /** 插件配置的当前值（已存的那部分，未经 default 合并）；主 app 用 prefs 实现。 */
    private val pluginSettings: (String) -> Map<String, String> = { emptyMap() },
    /** 插件包总开关；关闭的插件照常装载，但不出现在任何能力面上。 */
    private val pluginEnabled: (String) -> Boolean = { true },
) {
    private val root = File(context.filesDir, "plugins")
    private val clock = PluginClock.SYSTEM
    private val lock = Any()
    private val runtimes = LinkedHashMap<String, PluginRuntime>()

    /** 界面扩展点的解析缓存：装载 / 卸载 / 覆盖时清空，平时每次重组都复用同一份。 */
    @Volatile
    private var uiCache: List<PluginUiBinding>? = null

    /**
     * 当前**生效**的运行时快照（钩子按这个顺序串行执行）。
     *
     * 开关选择的是「照常装载、分发时短路」而不是「跳过装载」：关掉的插件必须继续出现在
     * 设置页列表里，否则用户没有任何入口把它重新打开。所有能力面（prompt 钩子、事件、
     * 定时器、界面插槽、界面回调）都从这一个快照取数，因此一处过滤就全部失效。
     */
    fun snapshot(): List<PluginRuntime> = synchronized(lock) {
        runtimes.values.filter { pluginEnabled(it.manifest.id) }
    }

    /** 所有已装载的运行时，**不过**开关过滤：设置页列表与卸载用它。 */
    fun allRuntimes(): List<PluginRuntime> = synchronized(lock) { runtimes.values.toList() }

    fun infos(): List<LoadedPluginInfo> = allRuntimes().map { runtime ->
        val info = runtime.info()
        val resolved = PluginSettingsCodec.resolve(info.settings, pluginSettings(info.id))
        info.copy(enabled = pluginEnabled(info.id), settingValues = resolved)
    }

    /** 开关 / 配置变化后丢弃界面解析缓存，让下一次重组按新的生效集合重新解析。 */
    fun invalidate() {
        synchronized(lock) { uiCache = null }
    }

    /** 按当前 prefs 里的配置值重新装载一个插件（设置页保存后调用，新值立刻进 ctx.settings）。 */
    fun reload(pluginId: String): Boolean {
        val dir = File(root, pluginId)
        if (!dir.isDirectory) return false
        return try {
            loadDirectory(dir)
            true
        } catch (t: Throwable) {
            logger("插件 $pluginId 重新装载失败：" + (t.message ?: t.javaClass.simpleName))
            false
        }
    }

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

    /**
     * 插件界面此刻要画的 state，供 ui.json 的 `state.xxx` 取值表达式使用。
     *
     * 声明了 `provider` 的插件走 provider（宿主每次渲染前现问一次，失败沿用上一次的值）；
     * 没声明的沿用旧路径：读脚本顶层 `state` 对象的 JSON 快照。
     */
    fun uiStates(): Map<String, JSONObject?> = snapshot().associate { runtime ->
        runtime.manifest.id to stateOf(runtime)
    }

    /** 声明了 FULLSCREEN 插槽的全部 binding（正常情况下不会用到，只有仲裁时才需要）。 */
    fun fullscreenBindings(): List<PluginUiBinding> = uiBindings(PluginSlots.FULLSCREEN)

    private fun stateOf(runtime: PluginRuntime): JSONObject? {
        val id = runtime.manifest.id
        val providerEntry = runtime.uiProviderEntry
        val json = if (providerEntry == null) {
            runCatching { runtime.stateJson() }
                .onFailure { logger("读取插件 " + id + " 的 state 失败：" + describe(it)) }
                .getOrNull()
        } else {
            val provided = runtime.provideUiState()
            if (!provided.ok) {
                logger("插件 " + id + " 的 provider " + providerEntry + " 调用失败（沿用上一次的值）：" + (provided.error ?: ""))
            }
            provided.stateJson()
        }
        return json?.let { text -> runCatching { JSONObject(text) }.getOrNull() }
    }

    /** 只取一个插件的 state（全屏层 / 单插槽刷新时用，避免为了一帧去问所有插件）。 */
    fun uiStateOf(pluginId: String): JSONObject? {
        val runtime = snapshot().firstOrNull { it.manifest.id == pluginId } ?: return null
        return stateOf(runtime)
    }

    /**
     * 当前占用全屏层的插件 binding。谁被选中由 FullscreenArbiter 决定（宿主侧仲裁），
     * 这里只按 id 把界面描述取出来；找不到（插件被卸载 / 关掉）返回 null，全屏层随之消失。
     */
    fun fullscreenBinding(pluginId: String): PluginUiBinding? =
        uiBindings().firstOrNull { it.pluginId == pluginId && it.slot == PluginSlots.FULLSCREEN }

    /** 声明了 FULLSCREEN 插槽的插件 id，按启用顺序（装载顺序）。 */
    fun fullscreenPluginIds(): List<String> = snapshot()
        .filter { it.manifest.fullscreenExtension != null }
        .map { it.manifest.id }

    /** 声明了界面插槽的插件 id（全屏与 ticker 的可见性判定都用它）。 */
    fun uiPluginIds(): List<String> {
        val ids = LinkedHashSet<String>()
        for (binding in uiBindings()) ids += binding.pluginId
        return ids.toList()
    }

    /** 插件设置里 type=image 的字段值（key -> 私有目录下的绝对路径）。 */
    fun pluginImagePaths(pluginId: String): Map<String, File> {
        val runtime = allRuntimes().firstOrNull { it.manifest.id == pluginId } ?: return emptyMap()
        val dir = File(root, pluginId)
        val result = LinkedHashMap<String, File>()
        for (setting in runtime.manifest.settings) {
            if (setting.type != PluginSettingType.IMAGE) continue
            val raw = pluginSettings(pluginId)[setting.key]
            if (raw.isNullOrBlank()) continue
            val file = File(raw)
            // 只认插件自己目录里的文件：设置页理论上不会写别处的路径，这里也不允许。
            if (!file.isAbsolute || !file.absolutePath.startsWith(dir.absolutePath)) continue
            if (!file.isFile) continue
            result[setting.key] = file
        }
        return result
    }

    /** 用户为图片字段选的裁切方式（键名 <field>Fit，取值为 crop / fit / fill）。 */
    fun pluginImageFits(pluginId: String): Map<String, String> {
        val runtime = allRuntimes().firstOrNull { it.manifest.id == pluginId } ?: return emptyMap()
        val stored = pluginSettings(pluginId)
        val result = LinkedHashMap<String, String>()
        for (setting in runtime.manifest.settings) {
            if (setting.type != PluginSettingType.IMAGE) continue
            val value = stored[setting.key + FIT_SUFFIX]
            if (value.isNullOrBlank()) continue
            // 无级裁切：0..100 的数字（0=保留画面顶部，100=保留底部）。
            // 旧的 top/center/bottom 也认，方便老配置继续用。
            val ok = value in LEGACY_IMAGE_FITS || value.toIntOrNull()?.let { it in 0..100 } == true
            if (!ok) continue
            result[setting.key] = value
        }
        return result
    }

    /** 设置页保存图片后调用：把用户选的图片复制进插件私有目录，返回落盘路径。 */
    fun importPluginImage(pluginId: String, key: String, source: java.io.InputStream, extension: String?): File {
        val dir = File(File(root, pluginId), IMAGE_DIR)
        if (!dir.mkdirs() && !dir.isDirectory) throw IllegalStateException("无法创建图片目录 " + dir.absolutePath)
        // 只保留白名单后缀：图片文件的扩展名会影响系统解码器选择，不能直接信 uri 里那段。
        val suffix = extension?.lowercase()?.takeIf { it in IMAGE_EXTENSIONS } ?: "jpg"
        val target = File(dir, key + "." + suffix)
        // 同名旧文件先删掉：换图后不应在目录里留下两张图。
        dir.listFiles()?.filter { it.name.substringBeforeLast('.') == key }?.forEach { it.delete() }
        source.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        return target
    }

    /** 清除某个图片字段的文件（设置页点「清除」）。 */
    fun clearPluginImage(pluginId: String, key: String) {
        val dir = File(File(root, pluginId), IMAGE_DIR)
        dir.listFiles()?.filter { it.name.substringBeforeLast('.') == key }?.forEach { it.delete() }
    }

    /** 声明了 ui_ticker 的插件 id（宿主据此判断要不要给它起界面时钟）。 */
    fun tickerPluginIds(): List<String> = snapshot()
        .filter { it.manifest.uiTickerExtension != null }
        .map { it.manifest.id }

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
                provider = extension.provider,
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
        val runtime = PluginRuntime.create(
            pkg, host, stateStore, clock, logger,
            settingValues = pluginSettings(pkg.validate().manifest?.id ?: dir.name),
        )
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
            PluginRuntime.create(
                pkg, host, stateStore, clock, logger,
                settingValues = pluginSettings(manifest.id),
            )
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

    private fun describe(t: Throwable): String = t.message ?: t.javaClass.simpleName

    companion object {
        /** 图片字段的裁切方式存在 <key>Fit 这个辅助键里，plugin manifest 不用声明它。 */
        const val FIT_SUFFIX = "Fit"

        /** 允许的裁切方式：居中裁切（铺满）/ 适应（留边）/ 填充（拉伸）。 */
        /** 旧版的三档裁切，保留兼容。 */
        val LEGACY_IMAGE_FITS: Set<String> = linkedSetOf("top", "center", "bottom")

        /** 用户图片落在插件私有目录的这个子目录里（包内 assets/ 是只读的插件包内容）。 */
        const val IMAGE_DIR = "user-images"

        /** 允许的图片后缀；其余一律按 jpg 存。 */
        val IMAGE_EXTENSIONS: Set<String> = linkedSetOf("jpg", "jpeg", "png", "webp", "gif")
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
    settings = manifest.settings,
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
