package com.aiclassroom.app.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 跨 App 的「正在播放」读取与播放控制。
 *
 * 数据来源有两条，互为兜底：
 * 1. [MediaNotificationListener] 从播放器通知的 EXTRA_MEDIA_SESSION 拿到 token（播放器普遍会发通知）；
 * 2. [MediaSessionManager.getActiveSessions]（需要通知使用权），覆盖只在播放时才发通知、甚至不发通知的播放器。
 *
 * 线程模型：会话表与当前曲目只在主线程改写；[current] 读的是 volatile 快照，任意线程可调。
 */
object NowPlaying {

    private const val TAG = "NowPlaying"

    /** 内嵌封面解码上限，避免个别播放器塞进超大位图。 */
    private const val ARTWORK_MAX_SIZE = 1024

    /** 通知里没有 token 时的兜底扫描间隔。 */
    private const val SCAN_INTERVAL_MS = 3_000L

    /** 「上次使用的播放器」落在 prefs：进程被杀、App 重启都还在。 */
    private const val PREFS = "ai_classroom_now_playing"
    private const val KEY_LAST_PACKAGE = "last_player_package"

    data class Track(
        val title: String,
        val artist: String,
        val album: String,
        val artwork: Bitmap?,
        val isPlaying: Boolean,
        val durationMs: Long,
        val positionMs: Long,
        val packageName: String,
    )

    private class Session(
        val packageName: String,
        val controller: MediaController,
        val callback: MediaController.Callback,
        /** 最后一次被判定为「活跃」的时刻（elapsedRealtime），用于多播放器仲裁。 */
        var lastActiveAt: Long,
    )

    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }
    private val sessions = LinkedHashMap<String, Session>()
    private val listeners = LinkedHashSet<(Track?) -> Unit>()

    @Volatile private var appContext: Context? = null
    @Volatile private var activePackage: String? = null
    @Volatile private var currentTrack: Track? = null
    private var lastLoggedSignature: String? = null
    @Volatile private var lastScanAt = 0L
    /** 已写进 prefs 的包名，避免每次 publish 都写一次磁盘。 */
    @Volatile private var rememberedPackage: String? = null

    // ---------------------------------------------------------------- 生命周期

    /** 幂等。必须给一个 application context（持有 MediaController 需要）。 */
    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    // ------------------------------------------------- 上次使用的播放器（prefs）

    /**
     * 「上次使用的播放器」：一键播放要拉起的就是它。
     *
     * 会话自己出现时（用户在别处放过歌）也会被记下来，用户不需要专门配置。
     * 只有包名，不含曲目 —— 曲目每次都现问系统。
     */
    fun lastPlayerPackage(): String? =
        appContext?.let { prefs(it).getString(KEY_LAST_PACKAGE, null)?.takeIf { pkg -> pkg.isNotBlank() } }

    fun rememberPlayer(pkg: String) {
        val name = pkg.trim()
        if (name.isEmpty()) return
        if (name == rememberedPackage) return
        rememberedPackage = name
        val context = appContext ?: return
        try {
            prefs(context).edit().putString(KEY_LAST_PACKAGE, name).apply()
        } catch (t: Throwable) {
            Log.w(TAG, "rememberPlayer($name) failed: ${t.message}")
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 当前曲目快照；没有可用会话时是 null。 */
    fun current(): Track? = currentTrack

    /** 当前曲目来自哪个包（没有会话时为 null）。 */
    fun currentPackage(): String? = activePackage

    /**
     * 立即向系统要一次活跃会话列表，补偿「播放器不发通知」的情况。
     * 主线程调用是同步的；后台线程会切到主线程并最多等 3 秒。
     */
    fun refresh() {
        val context = appContext ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            syncSessions(context)
            return
        }
        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                syncSessions(context)
            } finally {
                latch.countDown()
            }
        }
        latch.await(3, TimeUnit.SECONDS)
    }

    /** 会话全部失效（例如服务被解绑）时清干净，避免残留已死的控制器。 */
    fun releaseAll() {
        runOnMain {
            sessions.values.toList().forEach { detach(it) }
            sessions.clear()
            publish(null)
        }
    }

    // ------------------------------------------------------- 通知侧（token 更新）

    /**
     * 通知没有带 token 时顺手兜一次活跃会话扫描。
     * 通知回调非常频繁，所以这里限流：只有当前没有会话、且距上次扫描超过 [SCAN_INTERVAL_MS] 才真扫。
     */
    fun refreshIfIdle() {
        val now = SystemClock.elapsedRealtime()
        if (currentTrack != null) return
        if (now - lastScanAt < SCAN_INTERVAL_MS) return
        lastScanAt = now
        runOnMain {
            val context = appContext ?: return@runOnMain
            syncSessions(context)
        }
    }

    /** 只做 token 更新，真正的活都在主线程排队，避免拖慢通知回调。 */
    fun onMediaToken(packageName: String, token: android.media.session.MediaSession.Token) {
        runOnMain { attachToken(packageName, token) }
    }

    /** 通知被撤掉：只有当它确实是我们当前控制器时，才去确认会话是否还活着。 */
    fun onNotificationRemoved(packageName: String) {
        runOnMain {
            val session = sessions[packageName] ?: return@runOnMain
            if (isControllerAlive(session.controller)) {
                session.lastActiveAt = SystemClock.elapsedRealtime()
                promote()
            } else {
                Log.i(TAG, "session gone with notification: $packageName")
                detach(session)
                sessions.remove(packageName)
                promote()
            }
        }
    }

    private fun attachToken(packageName: String, token: android.media.session.MediaSession.Token) {
        val context = appContext ?: return
        val now = SystemClock.elapsedRealtime()
        val existing = sessions[packageName]
        if (existing != null && existing.controller.sessionToken == token) {
            existing.lastActiveAt = now
            promote()
            return
        }
        existing?.let { detach(it) }
        val controller = try {
            MediaController(context, token)
        } catch (t: Throwable) {
            Log.w(TAG, "MediaController($packageName) failed: ${t.message}")
            return
        }
        attach(controller, now)
    }

    // ------------------------------------------------------------- 系统会话扫描

    private fun syncSessions(context: Context) {
        val controllers = try {
            manager(context).getActiveSessions(listenerComponent(context))
        } catch (t: Throwable) {
            // 没有通知使用权时系统会抛 SecurityException，这时只能靠通知回调。
            Log.w(TAG, "getActiveSessions failed: ${t.message}")
            return
        }
        val alive = HashSet<String>()
        controllers.forEach { controller ->
            alive.add(controller.packageName)
            val known = sessions[controller.packageName]
            // 已在跟踪的会话不要刷时间戳，否则「最近活跃」的排序会被轮询抹平。
            attach(controller, known?.lastActiveAt ?: SystemClock.elapsedRealtime())
        }
        sessions.values.filter { it.packageName !in alive }.forEach { stale ->
            detach(stale)
            sessions.remove(stale.packageName)
        }
        promote()
    }

    private fun attach(controller: MediaController, activeAt: Long) {
        val packageName = controller.packageName
        val existing = sessions[packageName]
        if (existing != null && existing.controller.sessionToken == controller.sessionToken) {
            existing.lastActiveAt = maxOf(existing.lastActiveAt, activeAt)
            promote()
            return
        }
        existing?.let { detach(it) }
        val callback = sessionCallback(packageName)
        val session = Session(packageName, controller, callback, activeAt)
        try {
            controller.registerCallback(callback, mainHandler)
        } catch (t: Throwable) {
            Log.w(TAG, "registerCallback($packageName) failed: ${t.message}")
            return
        }
        sessions[packageName] = session
        Log.i(TAG, "attach session: $packageName state=${controller.playbackState?.state}")
        promote()
    }

    private fun detach(session: Session) {
        try {
            session.controller.unregisterCallback(session.callback)
        } catch (t: Throwable) {
            Log.w(TAG, "unregisterCallback(${session.packageName}) failed: ${t.message}")
        }
    }

    private fun sessionCallback(packageName: String): MediaController.Callback =
        object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) = onSessionChanged(packageName)
            override fun onPlaybackStateChanged(state: PlaybackState?) = onSessionChanged(packageName)
            override fun onSessionDestroyed() = onSessionDestroyed(packageName)
        }

    private fun onSessionChanged(packageName: String) {
        val session = sessions[packageName] ?: return
        session.lastActiveAt = SystemClock.elapsedRealtime()
        if (activePackage == packageName) {
            publish(session)
        } else {
            promote()
        }
    }

    private fun onSessionDestroyed(packageName: String) {
        val session = sessions.remove(packageName) ?: return
        Log.i(TAG, "onSessionDestroyed: $packageName")
        detach(session)
        promote()
    }

    // ------------------------------------------------------------------ 仲裁

    /** 多播放器仲裁：正在播的优先，其次取最后活跃的。 */
    private fun promote() {
        val best = sessions.values
            .sortedWith(
                compareByDescending<Session> { isPlaying(it) }
                    .thenByDescending { it.lastActiveAt }
            )
            .firstOrNull()
        if (best == null) {
            publish(null)
            return
        }
        publish(best)
    }

    private fun isPlaying(session: Session): Boolean =
        try {
            session.controller.playbackState?.state == PlaybackState.STATE_PLAYING
        } catch (t: Throwable) {
            false
        }

    // --------------------------------------------------------------- 数据装配

    private fun publish(session: Session?) {
        if (session == null) {
            val had = activePackage != null
            activePackage = null
            currentTrack = null
            if (had) log("<none>")
            listeners.forEach { it(null) }
            return
        }
        val track = buildTrack(session)
        activePackage = session.packageName
        currentTrack = track
        // 会话一出现就记住它：一键播放下次拉起的正是用户最近真正在用的那个播放器。
        rememberPlayer(session.packageName)
        // 位置每帧都在变，日志签名里刻意不含它，否则回调会被刷屏。
        log(
            if (track == null) {
                "<no metadata> ${session.packageName}"
            } else {
                "title=${track.title} artist=${track.artist} album=${track.album} " +
                    "art=${track.artwork?.let { "${it.width}x${it.height}" } ?: "none"} " +
                    "playing=${track.isPlaying} dur=${track.durationMs} " +
                    "pkg=${track.packageName}"
            }
        )
        listeners.forEach { it(track) }
    }

    private fun log(signature: String) {
        if (signature == lastLoggedSignature) return
        lastLoggedSignature = signature
        Log.i(TAG, "now playing: $signature")
    }

    private fun buildTrack(session: Session): Track? {
        val controller = session.controller
        val metadata = safeMetadata(controller)
        val state = safeState(controller)
        if (metadata == null && state == null) return null

        val title = firstText(
            metadata,
            MediaMetadata.METADATA_KEY_TITLE,
            MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
        ).orEmpty()
        val artist = firstText(
            metadata,
            MediaMetadata.METADATA_KEY_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
        ).orEmpty()
        val album = firstText(
            metadata,
            MediaMetadata.METADATA_KEY_ALBUM,
            MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION,
        ).orEmpty()
        if (title.isEmpty() && artist.isEmpty() && album.isEmpty()) return null

        val durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 } ?: 0L
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        var positionMs = state?.position ?: 0L
        if (state != null && isPlaying && state.lastPositionUpdateTime > 0L) {
            positionMs += SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
        }
        positionMs = if (durationMs > 0) positionMs.coerceIn(0L, durationMs) else positionMs.coerceAtLeast(0L)

        return Track(
            title = title,
            artist = artist,
            album = album,
            artwork = artwork(appContext, metadata),
            isPlaying = isPlaying,
            durationMs = durationMs,
            positionMs = positionMs,
            packageName = session.packageName,
        )
    }

    private fun safeMetadata(controller: MediaController): MediaMetadata? =
        try {
            controller.metadata
        } catch (t: Throwable) {
            null
        }

    private fun safeState(controller: MediaController): PlaybackState? =
        try {
            controller.playbackState
        } catch (t: Throwable) {
            null
        }

    private fun firstText(metadata: MediaMetadata?, vararg keys: String): String? {
        if (metadata == null) return null
        for (key in keys) {
            val value = metadata.getString(key)?.trim()
            if (!value.isNullOrEmpty()) return value
        }
        return null
    }

    /** 封面回退链：ALBUM_ART -> ART -> DISPLAY_ICON -> ALBUM_ART_URI / ART_URI。 */
    @Suppress("DEPRECATION")
    private fun artwork(context: Context?, metadata: MediaMetadata?): Bitmap? {
        if (metadata == null) return null
        metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.let { return it }
        metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)?.let { return it }
        metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)?.let { return it }
        val uri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
        return decodeArtwork(context, uri)
    }

    private fun decodeArtwork(context: Context?, uri: String?): Bitmap? {
        if (context == null || uri.isNullOrBlank()) return null
        return try {
            val parsed = Uri.parse(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return null
            var sample = 1
            while (bounds.outWidth / sample > ARTWORK_MAX_SIZE || bounds.outHeight / sample > ARTWORK_MAX_SIZE) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (t: Throwable) {
            // 其他 App 的 content:// 封面未必对我们开放，读不到就当没有。
            Log.w(TAG, "artwork uri failed: ${t.message}")
            null
        }
    }

    // ------------------------------------------------------------------ 控制

    fun play(): Boolean = withController { it.transportControls.play() }

    fun pause(): Boolean = withController { it.transportControls.pause() }

    fun next(): Boolean = withController { it.transportControls.skipToNext() }

    fun previous(): Boolean = withController { it.transportControls.skipToPrevious() }

    fun seekTo(ms: Long): Boolean = withController { it.transportControls.seekTo(ms.coerceAtLeast(0L)) }

    fun togglePlayPause(): Boolean =
        withController { if (isPlayingNow(it)) it.transportControls.pause() else it.transportControls.play() }

    fun stop(): Boolean = withController { it.transportControls.stop() }

    private fun isPlayingNow(controller: MediaController): Boolean =
        try {
            controller.playbackState?.state == PlaybackState.STATE_PLAYING
        } catch (t: Throwable) {
            false
        }

    private inline fun withController(block: (MediaController) -> Unit): Boolean {
        val packageName = activePackage ?: return false
        val controller = sessions[packageName]?.controller ?: return false
        return try {
            block(controller)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "control($packageName) failed: ${t.message}")
            false
        }
    }

    // ------------------------------------------------------------------ 监听

    /** 注册变更回调，并立刻回调一次当前值。 */
    fun registerListener(onChange: (Track?) -> Unit) {
        runOnMain {
            listeners.add(onChange)
            onChange(currentTrack)
        }
    }

    fun unregisterListener(onChange: (Track?) -> Unit) {
        runOnMain { listeners.remove(onChange) }
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 会话是否还活着：直接摸一下控制器。
     * 不用 getActiveSessions 是因为部分 ROM（含 MIUI）即使授权了也会对它抛 SecurityException，
     * 而 controller 的 binder 调用能如实反映对面是否已经销毁。
     */
    @Suppress("DEPRECATION")
    private fun isControllerAlive(controller: MediaController): Boolean =
        try {
            // 对面销毁后 binder 死掉，这两个读要么抛，要么都变成 null。
            controller.playbackState != null || controller.metadata != null
        } catch (t: Throwable) {
            Log.i(TAG, "controller dead: ${t.message}")
            false
        }

    private fun manager(context: Context): MediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private fun listenerComponent(context: Context): ComponentName =
        ComponentName(context, MediaNotificationListener::class.java)

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }
}
