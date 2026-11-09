package com.aiclassroom.feature.plugin

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 内存版 PluginStateStore，能看到宿主实际写进去的 JSON。 */
class InMemoryStateStore : PluginStateStore {
    private val data = LinkedHashMap<String, String>()

    override fun read(pluginId: String): String? = data[pluginId]

    override fun write(pluginId: String, json: String) {
        data[pluginId] = json
    }

    fun snapshot(pluginId: String): String? = data[pluginId]
}

/** 假的宿主实现：记录调用参数，返回可预期的结果，不真的联网 / 生图 / 发音。 */
class RecordingHostApi : PluginHostApi {
    val calls = mutableListOf<String>()
    val pluginStorage = LinkedHashMap<String, String>()
    var refreshCount = 0

    var imageResult: PluginResult = PluginResult.Success("ok", "/tmp/image.png")
    var speakResult: PluginResult = PluginResult.Success("ok", "/tmp/voice.mp3")
    var httpResult: PluginResult = PluginResult.Success("{}")
    var dialogReadResult: PluginResult = PluginResult.Success("[]")
    var insertResult: PluginResult = PluginResult.Success("ok")

    override suspend fun generateImage(prompt: String, width: Int?, height: Int?): PluginResult {
        calls += "generateImage:$prompt:$width:$height"
        return imageResult
    }

    override suspend fun speak(text: String): PluginResult {
        calls += "speak:$text"
        return speakResult
    }

    override suspend fun httpRequest(url: String, method: String, body: String?): PluginResult {
        calls += "httpRequest:$method:$url:$body"
        return httpResult
    }

    override suspend fun readDialog(limit: Int): PluginResult {
        calls += "readDialog:$limit"
        return dialogReadResult
    }

    override suspend fun insertDialog(role: String, text: String): PluginResult {
        calls += "insertDialog:$role:$text"
        return insertResult
    }

    var classroomsResult: PluginResult = PluginResult.Success("[]")
    var examResult: PluginResult = PluginResult.Success("{}")
    var writeExamResult: PluginResult = PluginResult.Success("ok")
    var writeClassroomResult: PluginResult = PluginResult.Success("ok")
    var memoryResult: PluginResult = PluginResult.Success("{}")
    var writeMemoryResult: PluginResult = PluginResult.Success("ok")
    var knowledgeResult: PluginResult = PluginResult.Success("[]")
    var allDialogsResult: PluginResult = PluginResult.Success("[]")

    override suspend fun listClassrooms(): PluginResult {
        calls += "listClassrooms"
        return classroomsResult
    }

    override suspend fun writeClassroom(patchJson: String): PluginResult {
        calls += "writeClassroom:$patchJson"
        return writeClassroomResult
    }

    override suspend fun readExam(): PluginResult {
        calls += "readExam"
        return examResult
    }

    override suspend fun writeExam(patchJson: String): PluginResult {
        calls += "writeExam:$patchJson"
        return writeExamResult
    }

    override suspend fun readMemory(): PluginResult {
        calls += "readMemory"
        return memoryResult
    }

    override suspend fun writeMemory(text: String): PluginResult {
        calls += "writeMemory:$text"
        return writeMemoryResult
    }

    override suspend fun readKnowledge(): PluginResult {
        calls += "readKnowledge"
        return knowledgeResult
    }

    override suspend fun readAllDialogs(classroomId: String?, limitPerClassroom: Int): PluginResult {
        calls += "readAllDialogs:$classroomId:$limitPerClassroom"
        return allDialogsResult
    }

    override fun storageGet(pluginId: String, key: String): String? = pluginStorage["$pluginId/$key"]

    override fun storagePut(pluginId: String, key: String, value: String) {
        pluginStorage["$pluginId/$key"] = value
    }

    override fun requestUiRefresh(pluginId: String) {
        refreshCount++
        calls += "requestUiRefresh:$pluginId"
    }

    /** 全屏层：记录谁进谁出，测试据此断言 JS 侧 host.enterFullscreen() 真的到了宿主。 */
    val fullscreenCalls = mutableListOf<String>()
    var pickImageResult: PluginResult = PluginResult.Success("ok", "/tmp/pick.png")

    override fun enterFullscreen(pluginId: String) {
        fullscreenCalls += "enter:$pluginId"
    }

    override fun exitFullscreen(pluginId: String) {
        fullscreenCalls += "exit:$pluginId"
    }

    override suspend fun pickImage(pluginId: String): PluginResult {
        calls += "pickImage:$pluginId"
        return pickImageResult
    }

    /** 媒体能力：默认返回一个「正在播」的假曲目，测试据此断言 JS 侧拿到的字段。 */
    var mediaCurrentResult: PluginResult =
        PluginResult.Success("""{"playing":true,"title":"测试曲","artist":"测试歌手"}""")
    var mediaControlResult: PluginResult = PluginResult.Success("play")
    var mediaArtworkResult: PluginResult = PluginResult.Success("ok", "/tmp/media_cover.png")

    override suspend fun mediaCurrent(): PluginResult {
        calls += "mediaCurrent"
        return mediaCurrentResult
    }

    override suspend fun mediaControl(action: String): PluginResult {
        calls += "mediaControl:$action"
        return mediaControlResult
    }

    override suspend fun mediaArtwork(): PluginResult {
        calls += "mediaArtwork"
        return mediaArtworkResult
    }
}

/** 把「文件名 -> 文本」打成真正的 ZIP，验证插件包格式解析走的是真实压缩包路径。 */
fun zipOf(entries: Map<String, String>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((path, content) in entries) {
            zip.putNextEntry(ZipEntry(path))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

fun readTestResource(path: String): String =
    checkNotNull(TestSupportMarker::class.java.classLoader?.getResourceAsStream(path)) {
        "找不到测试资源：$path"
    }.use { it.readBytes().toString(Charsets.UTF_8) }

private object TestSupportMarker
