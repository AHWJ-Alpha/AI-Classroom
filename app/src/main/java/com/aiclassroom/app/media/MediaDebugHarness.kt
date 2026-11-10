package com.aiclassroom.app.media

import android.content.Context
import android.util.Log
import com.aiclassroom.app.BuildConfig
import java.io.File

/**
 * 仅 debug 版的命令行自测钩子（无 UI 依赖，便于 adb 验证）。
 *
 * 用法：先 `adb shell run-as com.aiclassroom.app` 往 files/media_debug_cmd.txt 写一行命令，
 * 再让监听服务收到任一通知（播放/暂停音乐本身就会触发），命令即执行并把结果打到 logcat：
 *   adb logcat -s NowPlayingTest
 *
 * 支持：dump / sessions / play / pause / next / prev / seek <ms> / toggle / lastpkg / oneshot
 */
internal object MediaDebugHarness {

    private const val TAG = "NowPlayingTest"
    private const val FILE_NAME = "media_debug_cmd.txt"

    private var watch: ((NowPlaying.Track?) -> Unit)? = null

    fun runIfRequested(context: Context, source: String) {
        if (!BuildConfig.DEBUG) return
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.isFile) return
            val command = file.readText().trim()
            file.delete()
            if (command.isEmpty()) return
            Log.i(TAG, "[$source] cmd=$command => ${execute(context, command)}")
        } catch (t: Throwable) {
            Log.w(TAG, "[$source] harness failed: ${t.message}")
        }
    }

    private fun execute(context: Context, command: String): String {
        val name = command.substringBefore(' ').lowercase()
        val argument = command.substringAfter(' ', "").trim()
        return when (name) {
            "dump" -> {
                NowPlaying.refresh()
                describe(NowPlaying.current())
            }
            "sessions" -> {
                NowPlaying.refresh()
                "active=${NowPlaying.currentPackage()} track=${describe(NowPlaying.current())}"
            }
            "play" -> "play()=${NowPlaying.play()}"
            "pause" -> "pause()=${NowPlaying.pause()}"
            "toggle" -> "toggle()=${NowPlaying.togglePlayPause()}"
            "next" -> "next()=${NowPlaying.next()}"
            "prev" -> "previous()=${NowPlaying.previous()}"
            "seek" -> "seekTo($argument)=${NowPlaying.seekTo(argument.toLongOrNull() ?: 0L)}"
            "listen" -> {
                if (watch == null) {
                    watch = { track -> Log.i(TAG, "onChange => ${describe(track)}") }
                    NowPlaying.registerListener(watch!!)
                    "listener registered"
                } else {
                    NowPlaying.unregisterListener(watch!!)
                    watch = null
                    "listener unregistered"
                }
            }
            "perm" -> "isGranted=${MediaAccess.isGranted(context)} " +
                "settingFlag=${MediaAccess.listenerEnabled(context)} " +
                "connected=${MediaNotificationListener.isConnected()} " +
                "canRead=${MediaAccess.canReadSessions(context)}"
            "list" -> {
                NowPlaying.refresh()
                "activePkg=${NowPlaying.currentPackage()} track=${describe(NowPlaying.current())}"
            }
            "lastpkg" -> "lastPlayerPackage=" + NowPlaying.lastPlayerPackage()
            "oneshot" -> {
                // 一键播放的完整路径：有会话就继续播，没有就拉起上次的播放器再播。
                val outcome = kotlinx.coroutines.runBlocking { MusicLauncher.playOrLaunch(context) }
                "playOrLaunch state=" + outcome.state +
                    " pkg=" + outcome.packageName +
                    " playing=" + outcome.playing +
                    " message=" + (outcome.message ?: "")
            }
            "wait" -> {
                Thread.sleep(argument.toLongOrNull() ?: 1000L)
                describe(NowPlaying.current())
            }
            else -> "unknown command"
        }
    }

    private fun describe(track: NowPlaying.Track?): String {
        if (track == null) return "Track=null activePkg=${NowPlaying.currentPackage()}"
        return "Track(title=${track.title}, artist=${track.artist}, album=${track.album}, " +
            "artwork=${track.artwork?.let { "${it.width}x${it.height}" } ?: "null"}, " +
            "isPlaying=${track.isPlaying}, durationMs=${track.durationMs}, " +
            "positionMs=${track.positionMs}, packageName=${track.packageName})"
    }
}
