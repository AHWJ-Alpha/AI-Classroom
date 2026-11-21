package com.aiclassroom.app.classroom

import android.app.Instrumentation
import android.app.Activity
import android.os.Bundle
import com.aiclassroom.classroom.content.TextCount
import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.exec.*
import com.aiclassroom.classroom.gateway.*
import com.aiclassroom.classroom.json.*
import com.aiclassroom.classroom.store.FileClassroomStore
import kotlinx.coroutines.*
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 实机上走 ClassroomService 的正式装配。独立临时项目、模拟模型，不读取用户对话。 */
class SceneHostDeviceTest(private val instrumentation: Instrumentation) {
    private val pid = ProjectId("device-scene")
    private val uid = UnitId("device-unit")
    private val policy = ModelPolicy("fake", "https://example.invalid", "fake")
    private fun response(body: String) = ModelCallResult(body, "fake", FinishReason.Stop, true, null, null, null, null)
    private fun seed(s: FileClassroomStore, target: Int) {
        val now = System.currentTimeMillis()
        s.createProject(ClassroomProject(pid, "隔离测试", ProjectType.SCENE, ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now))
        s.saveSpec(ProjectSpec(pid, 1, "每节 ${target} 字，共同生活三天", "借住", listOf("建立习惯"), "自己", "zh", "自然", "", 1,
            RunMode.CONFIRM_OUTLINE, ReviewPolicy.STANDARD, emptyList(), emptyList(), "", createdAt = now))
        s.saveOutline(ProjectOutline(pid, 1, "建立边界", "慢慢熟悉", emptyList(),
            listOf(PlannedUnit(uid, 1, "第 1-3 天", "相处习惯")), createdAt = now))
        s.upsertUnit(ClassroomUnit(uid, pid, 1, ProjectType.SCENE, "第 1-3 天", "相处习惯", emptyList(), emptyList(), emptyList(), 0, UnitStatus.PLANNED))
    }
    private fun segment(text: String, finished: Boolean) = response(JsonWriter.write(Json.obj(
        "blocks" to Json.arr(listOf(Json.obj("id" to Json.of("b1"), "type" to Json.of("narration"), "text" to Json.of(text)))),
        "events" to Json.arr(emptyList()), "summary" to Json.of(text.take(80)), "finished" to Json.of(finished))))

    fun testHostResumesFileDraftAndPublishesVersion() = runBlocking {
        val root = File(instrumentation.targetContext.cacheDir, "scene-instrumentation-${System.nanoTime()}")
        try {
            val first = "她把钥匙放到门口的木碗里，轻轻关门，然后坐在窗边听雨。".repeat(4)
            val last = "清晨他把杯子推到她手边，两人一起收拾桌面，约好晚上留一盏灯。".repeat(4)
            val original = FileClassroomStore(root); seed(original, 200)
            var firstCalls = 0
            val interrupted = object : ModelGateway {
                override suspend fun complete(request: ModelRequest): ModelCallResult = if (firstCalls++ == 0) segment(first, false)
                else ModelCallResult("", null, null, false, null, null, ModelCallError.Network("isolated-offline"), null)
            }
            val control = object : RunControl { override fun shouldContinue() = true }
            val service = ClassroomService(instrumentation.targetContext, store = original, gatewayFactory = { _, _ -> interrupted })
            val interruptedOutcome = service.run(pid, policy, "fake", control)
            check(interruptedOutcome is RunOutcome.Paused) { "unexpected first outcome=$interruptedOutcome calls=$firstCalls draft=${original.draft(pid, uid)}" }
            val before = requireNotNull(original.draft(pid, uid))
            check(before.segments.size == 1)
            val reopened = FileClassroomStore(root)
            var calls = 0
            val resumed = object : ModelGateway {
                override suspend fun complete(request: ModelRequest): ModelCallResult {
                    val prompt = request.messages.joinToString { it.content }
                    return when (calls++) {
                        0 -> { check(prompt.contains(first.takeLast(50))); segment(last, true) }
                        1 -> { check(prompt.contains(first)); check(prompt.contains(last)); response("{\"issues\":[]}") }
                        else -> response("{\"changes\":[],\"evidenceEventIds\":[]}")
                    }
                }
            }
            val again = ClassroomService(instrumentation.targetContext, store = reopened, gatewayFactory = { _, _ -> resumed })
            check(again.run(pid, policy, "fake", control) is RunOutcome.Completed)
            val saved = FileClassroomStore(root)
            val draft = requireNotNull(saved.draft(pid, uid))
            check(draft.candidateId == before.candidateId)
            check(draft.segments.first().checksum == before.segments.first().checksum)
            check(draft.phase == DraftPhase.COMMITTED)
            check(draft.fencingToken > before.fencingToken)
            check(calls == 3)
            val revision = requireNotNull(saved.officialRevision(uid))
            check(TextCount.units(revision.content!!.blocks) >= 180)
            check(saved.latestManifest(pid)!!.revisionOf(uid) == revision.id)
            android.util.Log.i("SceneDeviceTest", "PASS resumed segments=${draft.segments.size} chars=${draft.totalChars} calls=${draft.callCount} phase=${draft.phase}")
        } finally { root.deleteRecursively() }
    }

    fun testBlockedTransportCancellationReturnsBeforeReadTimeout() = runBlocking {
        val server = ServerSocket(0)
        val accepted = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val release = CountDownLatch(1)
        val thread = Thread {
            runCatching {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    // 消费完整请求，再发送 SSE 响应头但一直不发 data，确保客户端阻塞在读正文。
                    val input = socket.getInputStream()
                    var ending = 0
                    val pattern = byteArrayOf(13, 10, 13, 10)
                    while (ending < 4) {
                        val value = input.read()
                        check(value >= 0)
                        ending = if (value == pattern[ending].toInt()) ending + 1 else 0
                    }
                    check(input.read() == '{'.code); check(input.read() == '}'.code)
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n".toByteArray())
                        flush()
                    }
                    accepted.countDown()
                    if (input.read() == -1) closed.countDown()
                    release.await(1, TimeUnit.SECONDS)
                }
            }
        }.apply { isDaemon = true; start() }
        try {
            var callbacks = 0
            val job = launch(Dispatchers.Default) {
                AndroidHttpTransport(readTimeoutMs = 30_000).post(
                    HttpRequest("http://127.0.0.1:${server.localPort}", emptyMap(), "{}")) { callbacks++ }
            }
            check(accepted.await(5, TimeUnit.SECONDS))
            withTimeout(2500) { job.cancelAndJoin() }
            check(callbacks == 0)
            check(closed.await(3, TimeUnit.SECONDS)) { "cancel returned but socket remained open" }
            android.util.Log.i("SceneDeviceTest", "PASS blocked transport cancelled before timeout")
        } finally { release.countDown(); server.close(); thread.join(1500) }
    }
}

/** 自包含的实机回归入口，无调试代码进入 release。 */
class SceneTestInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val results = Bundle()
        try {
            val tests = SceneHostDeviceTest(this)
            tests.testHostResumesFileDraftAndPublishesVersion()
            sendStatus(0, Bundle().apply { putString("stream", "PASS host file-draft resume / full review / version commit\n") })
            tests.testBlockedTransportCancellationReturnsBeforeReadTimeout()
            results.putString("stream", "PASS 2 device tests: host resume and blocked transport cancellation\n")
            finish(Activity.RESULT_OK, results)
        } catch (t: Throwable) {
            results.putString("stream", t.stackTraceToString())
            finish(Activity.RESULT_CANCELED, results)
        }
    }
}
