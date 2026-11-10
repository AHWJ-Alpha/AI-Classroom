package com.aiclassroom.app.media

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.delay

/**
 * 顶栏「音乐」的一键播放：把用户本来要手动做的一串操作折成一次点击。
 *
 * 三种情形，顺序固定：
 * 1. 有会话且在播 -> 什么都不做（插件那边只切悬浮窗显隐）；
 * 2. 有会话但暂停 -> play()，不重新拉起 App；
 * 3. 没有会话 -> 用上次用过的播放器包名拉起它，等会话出现（最多 3 秒，每 500ms 问一次）再发 play()。
 *
 * 第 3 种必须等：播放器刚被拉起时还没建好媒体会话，立刻发 play() 只会打到空气上。
 */
object MusicLauncher {

    private const val TAG = "MusicLauncher"

    /** 拉起后等会话出现的总时长与轮询间隔。 */
    private const val LAUNCH_WAIT_MS = 3_000L
    private const val POLL_INTERVAL_MS = 500L

    /** 会话没等到时用媒体键兜一次 play，再等它真的开始播。 */
    private const val KEY_WAIT_MS = 2_000L

    /**
     * [state] 是给插件看的稳定枚举：
     * playing（本来就在播）/ resumed（暂停->继续）/ launched（拉起并播放）/
     * launched_pending（拉起了但还没准备好）/ no_player（还没有上次播放器）/ failed。
     */
    data class Outcome(
        val state: String,
        val packageName: String?,
        val playing: Boolean,
        val message: String?,
    )

    suspend fun playOrLaunch(context: Context): Outcome {
        NowPlaying.init(context)
        // 先问系统一次：可能已经有会话，只是宿主还没被动收到回调。
        NowPlaying.refresh()
        val track = NowPlaying.current()
        if (track != null && track.isPlaying) return Outcome("playing", track.packageName, true, null)
        if (track != null) {
            val resumed = NowPlaying.play()
            return Outcome(
                state = if (resumed) "resumed" else "failed",
                packageName = track.packageName,
                playing = resumed,
                message = if (resumed) null else "播放器没有接受 play 指令",
            )
        }
        val pkg = NowPlaying.lastPlayerPackage()
            ?: return Outcome(
                state = "no_player",
                packageName = null,
                playing = false,
                message = "还没有记录到播放器：先手动打开一个音乐 App 播一次，之后就能一键播放",
            )
        NowPlaying.rememberPlayer(pkg)
        if (!launch(context, pkg)) {
            return Outcome("failed", pkg, false, "找不到 " + pkg + " 的启动入口")
        }
        val deadline = SystemClock.elapsedRealtime() + LAUNCH_WAIT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            delay(POLL_INTERVAL_MS)
            NowPlaying.refresh()
            val found = NowPlaying.current() ?: continue
            if (found.isPlaying) return Outcome("launched", found.packageName, true, null)
            if (NowPlaying.play()) return Outcome("launched", found.packageName, true, null)
        }
        // 会话还没出现：播放器多半还停在启动页 / 首页。播放器普遍响应媒体键，
        // 让系统把 play 转给当前活跃的它，再等两秒确认真的开始播了。
        Log.i(TAG, "no session within " + LAUNCH_WAIT_MS + "ms, dispatch media PLAY key")
        dispatchPlayKey(context)
        val keyDeadline = SystemClock.elapsedRealtime() + KEY_WAIT_MS
        while (SystemClock.elapsedRealtime() < keyDeadline) {
            delay(POLL_INTERVAL_MS)
            NowPlaying.refresh()
            val found = NowPlaying.current() ?: continue
            if (found.isPlaying) return Outcome("launched", found.packageName, true, null)
        }
        // 到这儿 App 已经到前面了，浮窗照常显示，播放留给用户点一下播放键。
        Log.w(TAG, "player still not playing after media key: " + pkg)
        return Outcome("launched_pending", pkg, false, "已拉起播放器，但它还没准备好播放")
    }

    /** 媒体键 PLAY：系统会把它交给当前活跃的播放器，不需要会话、也不需要额外权限。 */
    private fun dispatchPlayKey(context: Context) {
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
        } catch (t: Throwable) {
            Log.w(TAG, "dispatchMediaKeyEvent failed: " + (t.message ?: t.javaClass.simpleName))
        }
    }

    private fun launch(context: Context, pkg: String): Boolean = try {
        // 优先用系统给的启动 intent；个别 ROM 上它为 null，再退回「MAIN/LAUNCHER + 包名」。
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(pkg)
                .takeIf { context.packageManager.resolveActivity(it, 0) != null }
        if (intent == null) {
            Log.w(TAG, "no launch intent for " + pkg)
            false
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.i(TAG, "launched " + pkg)
            true
        }
    } catch (t: Throwable) {
        Log.w(TAG, "launch " + pkg + " failed: " + (t.message ?: t.javaClass.simpleName))
        false
    }
}
