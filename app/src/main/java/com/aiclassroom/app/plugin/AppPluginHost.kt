package com.aiclassroom.app.plugin

import android.graphics.Bitmap
import com.aiclassroom.feature.plugin.PluginHostApi
import com.aiclassroom.feature.plugin.PluginResult
import com.aiclassroom.feature.plugin.PluginStateStore
import com.aiclassroom.feature.plugin.host.PluginKvStorage
import java.io.File
import java.io.FileOutputStream

/**
 * :app 侧的 [PluginHostApi] 实现。
 *
 * 具体能力（生图 / TTS / 网络 / 对话 / 课堂 / 考试 / 记忆 / 知识库）都由主 app 用 lambda 注入，
 * 这里只做三件事：
 * 1. 统一兜异常：任何实现抛出来都转成 PluginResult.Failure，绝不让脚本看到 Kotlin 异常；
 * 2. storageGet / storagePut 直接建在 PluginStateStore 上（与 ctx.storage 共用同一份 key-value）；
 * 3. requestUiRefresh 转给主 app 的重组回调。
 *
 * 说明：按插件的权限校验发生在 feature-plugin 的 HostFacade（那里才知道 pluginId）；
 * 宿主这一层再按「功能总开关」兜一层（网络关闭时任何插件都拿不到网络能力）。
 *
 * 第二批能力默认实现为 not_available：没有注入实现的宿主（例如单测）不会静默返回空数据，
 * 而是让插件看到一个明确的错误码。
 */
class AppPluginHost(
    private val stateStore: PluginStateStore,
    private val generateImageImpl: suspend (prompt: String, width: Int?, height: Int?) -> PluginResult,
    private val speakImpl: suspend (text: String) -> PluginResult,
    private val httpImpl: suspend (url: String, method: String, body: String?) -> PluginResult,
    private val readDialogImpl: suspend (limit: Int) -> PluginResult,
    private val insertDialogImpl: suspend (role: String, text: String) -> PluginResult,
    private val uiRefreshImpl: (String) -> Unit = {},
    private val logger: (String) -> Unit = { line -> android.util.Log.i(PLUGIN_LOG_TAG, line) },
    private val listClassroomsImpl: suspend () -> PluginResult = { notAvailable("classroom.read") },
    private val writeClassroomImpl: suspend (String) -> PluginResult = { notAvailable("classroom.write") },
    private val readExamImpl: suspend () -> PluginResult = { notAvailable("exam.read") },
    private val writeExamImpl: suspend (String) -> PluginResult = { notAvailable("exam.write") },
    private val readMemoryImpl: suspend () -> PluginResult = { notAvailable("memory.read") },
    private val writeMemoryImpl: suspend (String) -> PluginResult = { notAvailable("memory.write") },
    private val readKnowledgeImpl: suspend () -> PluginResult = { notAvailable("knowledge.read") },
    private val readAllDialogsImpl: suspend (String?, Int) -> PluginResult =
        { _, _ -> notAvailable("dialogs.readAll") },
    private val enterFullscreenImpl: (String) -> Unit = {},
    private val exitFullscreenImpl: (String) -> Unit = {},
    private val pickImageImpl: suspend (String) -> PluginResult =
        { _ -> notAvailable("storage.readImage") },
    private val mediaCurrentImpl: suspend () -> PluginResult = { notAvailable("media.read") },
    private val mediaControlImpl: suspend (String) -> PluginResult = { notAvailable("media.control") },
    private val mediaArtworkImpl: suspend () -> PluginResult = { notAvailable("media.read") },
) : PluginHostApi {

    override suspend fun generateImage(prompt: String, width: Int?, height: Int?): PluginResult =
        guard("image.generate") { generateImageImpl(prompt, width, height) }

    override suspend fun speak(text: String): PluginResult = guard("tts.speak") { speakImpl(text) }

    override suspend fun httpRequest(url: String, method: String, body: String?): PluginResult =
        guard("http.request") { httpImpl(url, method, body) }

    override suspend fun readDialog(limit: Int): PluginResult = guard("dialog.read") { readDialogImpl(limit) }

    override suspend fun insertDialog(role: String, text: String): PluginResult =
        guard("dialog.insert") { insertDialogImpl(role, text) }

    override suspend fun listClassrooms(): PluginResult = guard("classroom.read") { listClassroomsImpl() }

    override suspend fun writeClassroom(patchJson: String): PluginResult =
        guard("classroom.write") { writeClassroomImpl(patchJson) }

    override suspend fun readExam(): PluginResult = guard("exam.read") { readExamImpl() }

    override suspend fun writeExam(patchJson: String): PluginResult = guard("exam.write") { writeExamImpl(patchJson) }

    override suspend fun readMemory(): PluginResult = guard("memory.read") { readMemoryImpl() }

    override suspend fun writeMemory(text: String): PluginResult = guard("memory.write") { writeMemoryImpl(text) }

    override suspend fun readKnowledge(): PluginResult = guard("knowledge.read") { readKnowledgeImpl() }

    override suspend fun readAllDialogs(classroomId: String?, limitPerClassroom: Int): PluginResult =
        guard("dialogs.readAll") { readAllDialogsImpl(classroomId, limitPerClassroom) }

    override fun storageGet(pluginId: String, key: String): String? = try {
        PluginKvStorage(stateStore, pluginId).get(key)
    } catch (t: Throwable) {
        logger("storageGet 失败（" + pluginId + "/" + key + "）：" + (t.message ?: t.javaClass.simpleName))
        null
    }

    override fun storagePut(pluginId: String, key: String, value: String) {
        try {
            PluginKvStorage(stateStore, pluginId).set(key, value)
        } catch (t: Throwable) {
            logger("storagePut 失败（" + pluginId + "/" + key + "）：" + (t.message ?: t.javaClass.simpleName))
        }
    }

    override fun requestUiRefresh(pluginId: String) {
        try {
            uiRefreshImpl(pluginId)
        } catch (t: Throwable) {
            logger("requestUiRefresh 失败（" + pluginId + "）：" + (t.message ?: t.javaClass.simpleName))
        }
    }

    override fun enterFullscreen(pluginId: String) {
        try {
            enterFullscreenImpl(pluginId)
        } catch (t: Throwable) {
            logger("enterFullscreen 失败（" + pluginId + "）：" + (t.message ?: t.javaClass.simpleName))
        }
    }

    override fun exitFullscreen(pluginId: String) {
        try {
            exitFullscreenImpl(pluginId)
        } catch (t: Throwable) {
            logger("exitFullscreen 失败（" + pluginId + "）：" + (t.message ?: t.javaClass.simpleName))
        }
    }

    override suspend fun pickImage(pluginId: String): PluginResult =
        guard("storage.readImage") { pickImageImpl(pluginId) }

    override suspend fun mediaCurrent(): PluginResult = guard("media.read") { mediaCurrentImpl() }

    override suspend fun mediaControl(action: String): PluginResult =
        guard("media.control") { mediaControlImpl(action) }

    override suspend fun mediaArtwork(): PluginResult = guard("media.read") { mediaArtworkImpl() }

    private suspend fun guard(capability: String, block: suspend () -> PluginResult): PluginResult = try {
        block()
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (t: Throwable) {
        logger("宿主能力 " + capability + " 失败：" + (t.message ?: t.javaClass.simpleName))
        PluginResult.Failure("host_error", t.message ?: t.javaClass.simpleName)
    }

    companion object {
        fun notAvailable(capability: String): PluginResult =
            PluginResult.Failure("not_available", "宿主未提供 " + capability + " 的实现")
    }
}

/**
 * 封面落盘的缓存：只有「换歌了」才重新编码 PNG。
 *
 * 缓存键是 packageName + title —— 播放进度每秒都在变，用它做键等于每秒写一次文件，
 * 那是插件轮询 mediaArtwork() 时最贵的开销。键随文件一起落在同一目录里，
 * 进程重启后仍然有效，不会因为重装 App 就多写一次。
 */
internal class MediaArtworkCache(
    private val dir: File,
    private val logger: (String) -> Unit = {},
) {
    private val keyFile = File(dir, "media_cover.key")
    private var lastPath: String? = null

    /**
     * 返回封面路径；[key] 没变且文件还在时什么都不做。
     *
     * **文件名带 key 哈希**，不是固定的 media_cover.png —— 这是必须的：
     * 插件 ui.json 里图片的 src 就是这个绝对路径，而渲染器用 `remember(path)` 缓存解码结果。
     * 路径固定的话，换歌后 remember 命中旧值，界面上永远显示上一首的封面（实测踩过）。
     * 路径随歌变，缓存自然失效，不需要改渲染器或协议。
     */
    fun store(key: String, bitmap: Bitmap): String {
        dir.mkdirs()
        val target = File(dir, fileNameFor(key))
        if (target.isFile && target.length() > 0 && readKey() == key) {
            lastPath = target.absolutePath
            return target.absolutePath
        }
        val temporary = File(dir, "media_cover.tmp")
        try {
            // 先写临时文件再改名：写到一半被下一首歌打断时，读到的永远是完整的上一个文件。
            FileOutputStream(temporary).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            keyFile.writeText(key)
            // 旧封面立刻删掉，否则每换一首歌就在 filesDir/images 里留一张图。
            dir.listFiles()?.forEach { f ->
                if (f != target && f.name.startsWith(COVER_PREFIX) && f.name.endsWith(".png")) {
                    runCatching { f.delete() }
                }
            }
            lastPath = target.absolutePath
        } catch (t: Throwable) {
            logger("封面落盘失败：" + (t.message ?: t.javaClass.simpleName))
            temporary.delete()
            throw t
        }
        return target.absolutePath
    }

    /** 已有的封面路径（没有就 null）：mediaCurrent 用它回填 artworkPath，不必再编码一次。 */
    fun existingPath(): String? {
        lastPath?.let { p -> File(p).takeIf { it.isFile && it.length() > 0 }?.let { return it.absolutePath } }
        val found = dir.listFiles()?.firstOrNull { it.name.startsWith(COVER_PREFIX) && it.name.endsWith(".png") && it.length() > 0 }
        lastPath = found?.absolutePath
        return found?.absolutePath
    }

    /** 盘上那张封面是不是 [key] 这首歌的。 */
    fun matches(key: String): Boolean = readKey() == key

    private fun readKey(): String? = runCatching { keyFile.readText() }.getOrNull()

    private fun fileNameFor(key: String): String = COVER_PREFIX + Integer.toHexString(key.hashCode()) + ".png"

    companion object {
        /** 缓存键：换了歌才换键，进度变化不进键。 */
        /** 封面文件名前缀：换歌换文件名，让渲染器的 remember(path) 自然失效。 */
        private const val COVER_PREFIX = "media_cover_"

        fun keyOf(packageName: String, title: String, album: String): String =
            packageName + "|" + title + "|" + album
    }
}
