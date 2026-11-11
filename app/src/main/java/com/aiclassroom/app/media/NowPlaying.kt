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

    // ---- 进度推算的锚点 ----
    //
    // 背景：酷狗这类播放器要么只报一次 position 就不再更新，要么偶尔回一个 0 / 旧值。
    // 所以「现在的进度」由我们自己按 elapsedRealtime 补足，播放器给的值只当**校准点**用。
    //
    // 之前反复出问题（进度归零 / 往回跳 / 卡住）的根因是「谁来当时间源」没有定死：
    // 原来的规则是「raw 与锚点差超过 1.5 秒就重新锚定」，于是播放器每推一次异常值
    // （0、或者比推算值小的旧位置）就被当成了一次 seek，进度条随之倒退或归零。
    //
    // 现在的三条规则：
    //  1. **单调**：同一个锚点内进度只增不减 —— 异常值最多让进度「停一下」，不会倒退。
    //  2. **连续两次才采信**：偏差很大的 raw 先记为 pending，下次读到同一个值才认作 seek。
    //     一次性异常值（0、旧位置）下一秒就变，因此会被自然丢掉。
    //  3. **换歌无条件重锚**，并清空单调水位。
    private var positionAnchorMs = 0L
    private var positionAnchorAt = 0L
    private var positionAnchorKey = ""
    /** 同一锚点内已返回过的最大进度，用于保证单调不倒退。-1 表示刚重锚、可从头开始。 */
    private var lastProjectedMs = -1L
    /** 上一次被判定为「偏差很大」的 raw 值，用于识别真正的 seek。 */
    private var pendingRawMs = -1L
    private const val KEY_LAST_PACKAGE = "last_player_package"

    /** 正常进度更新的容差：播放器报的值与推算值差在这个范围内，就不重锚。 */
    private const val DRIFT_TOLERANCE_MS = 1_500L

    /** 纯计算：按锚点推算此刻的理论位置（无副作用）。 */
    private fun expectedPosition(now: Long): Long =
        positionAnchorMs + (now - positionAnchorAt).coerceAtLeast(0L)

    /**
     * 对外展示用的进度：在推算值基础上保证**同一锚点内单调递增**。
     * 异常 raw 若被误采信，最多让进度停一帧，绝不会往回跳。
     */
    private fun projectPosition(now: Long, durationMs: Long): Long {
        var pos = expectedPosition(now)
        if (durationMs > 0L) pos = pos.coerceIn(0L, durationMs)
        // 单调保护：**只有算出来比上次小才回退到上次**。
        //
        // 早先写成 `if (lastProjectedMs in 0..pos) pos = lastProjectedMs` —— 方向反了：
        // 一旦 lastProjectedMs 变成 0，`0 in 0..pos` 恒成立，pos 就被永久钉死在 0，
        // 于是进度条永远是 0:00（实测连续 12 次采样全是 0:00 就是它造成的）。
        if (lastProjectedMs >= 0L && pos < lastProjectedMs) pos = lastProjectedMs
        lastProjectedMs = pos
        return pos
    }

    /** 换歌 / 确认 seek 后重新锚定，并清空单调水位。 */
    private fun reanchor(trackKey: String, raw: Long, now: Long) {
        positionAnchorKey = trackKey
        positionAnchorMs = raw.coerceAtLeast(0L)
        positionAnchorAt = now
        lastProjectedMs = -1L
        pendingRawMs = -1L
    }

    /**
     * 这个 raw 值该不该拿来重锚？
     *
     * - 换歌：直接重锚
     * - raw <= 0：只有「还没锚过」时才用（否则是播放器没准备好）
     * - 偏差在容差内：不重锚（锚点比 raw 更准，raw 可能滞后）
     * - 偏差很大：先记为 pending，**下次读到同一个值**才认作 seek
     */
    private fun shouldReanchor(trackKey: String, raw: Long, now: Long): Boolean {
        if (trackKey != positionAnchorKey) {
            pendingRawMs = -1L
            return true
        }
        if (raw <= 0L) {
            // 播放器没报有效位置（酷狗常见）。
            // **只有「这首歌还没锚过」时才用 0 起步** —— 早先写成 `positionAnchorMs <= 0L`，
            // 而锚点本身就是 0，于是每次扫描都判定「该重锚」，elapsed 被反复清零，
            // 进度永远停在 0:00（实测连续 12 次采样全是 0:00/3:07 就是这么来的）。
            return positionAnchorKey.isEmpty()
        }
        val drift = raw - expectedPosition(now)
        if (kotlin.math.abs(drift) <= DRIFT_TOLERANCE_MS) {
            pendingRawMs = -1L
            return false
        }
        val stable = raw == pendingRawMs
        pendingRawMs = raw
        return stable
    }

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
    /**
     * 当前曲目。**每次调用都按锚点重算进度**，不是返回冻结的快照。
     *
     * 为什么必须这样：currentTrack 只在「扫描会话」时重建（收到通知 / 主动 refresh），
     * 如果直接把缓存对象返回，positionMs 就冻结在上次扫描的那一刻 ——
     * 界面上的进度会永远停在 1:33，哪怕播放器早就在往前走。
     * 这是「进度条不动」的真正原因（之前一直在推算逻辑里找，方向错了）。
     */
    fun current(): Track? {
        val t = currentTrack ?: return null
        if (!t.isPlaying) return t
        return t.copy(positionMs = projectPosition(SystemClock.elapsedRealtime(), t.durationMs))
    }

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
        // 进度推算：不能依赖 state.lastPositionUpdateTime。
        //
        // 实测酷狗（com.kugou.android.lite）每帧都给一个新时间戳，但 position **始终是同一个值**
        // （收到过一次 4236 之后就不再变了）。拿时间戳做基准的话差值恒为 0，界面进度条永远停在 0:04。
        //
        // 改成自己维护锚点：记住「上次看到的 position」和「看到它的时刻」，
        // 播放中就用 elapsedRealtime 补足流逝量；position 出现跳变（换歌、用户拖进度条）时重新锚定。
        val rawPosition = state?.position ?: 0L
        val now = SystemClock.elapsedRealtime()
        val trackKey = session.packageName + "|" + title + "|" + album
        if (shouldReanchor(trackKey, rawPosition, now)) {
            reanchor(trackKey, rawPosition, now)
        }
        // 播放中用推算值；暂停时**先推算再把锚点冻结在这个位置** ——
        // 原来暂停分支直接用 rawPosition，而酷狗暂停时给的也是 0，
        // 于是一暂停进度就跳到 0:00（歌明明播到一半）。
        val positionMs = if (isPlaying) {
            projectPosition(now, durationMs)
        } else {
            val frozen = projectPosition(now, durationMs)
            positionAnchorMs = frozen
            positionAnchorAt = now
            frozen
        }


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
