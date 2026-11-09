package com.aiclassroom.app.media

import android.app.Notification
import android.content.ComponentName
import android.media.session.MediaSession
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * 通知使用权入口：只负责把通知里的 MediaSession.Token 交给 [NowPlaying]。
 *
 * 通知回调极其频繁，所以这里只做 token 提取 + 一次 post，不做任何组装/IO。
 */
class MediaNotificationListener : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        NowPlaying.init(applicationContext)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        Log.i(TAG, "listener connected")
        NowPlaying.init(applicationContext)
        NowPlaying.refresh()
        MediaDebugHarness.runIfRequested(this, "onListenerConnected")
    }

    override fun onListenerDisconnected() {
        connected = false
        Log.i(TAG, "listener disconnected")
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val token = mediaToken(sbn.notification?.extras)
        if (token != null) {
            NowPlaying.onMediaToken(sbn.packageName, token)
        } else {
            // 没有 token（很多播放器只在播放时带），限流地兜一次活跃会话扫描。
            NowPlaying.refreshIfIdle()
        }
        MediaDebugHarness.runIfRequested(this, "onNotificationPosted")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        NowPlaying.onNotificationRemoved(sbn.packageName)
    }

    private fun mediaToken(extras: Bundle?): MediaSession.Token? {
        if (extras == null) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "media token parse failed: ${t.message}")
            null
        }
    }

    companion object {
        private const val TAG = "MediaListener"

        @Volatile private var connected = false

        /** 系统当前是否把我们当作活的监听器（比读设置更硬的状态信号）。 */
        fun isConnected(): Boolean = connected

        /** 已授予通知使用权？ */
        fun isEnabled(context: android.content.Context): Boolean =
            MediaAccess.isGranted(context)

        /** 直接跳到系统的通知使用权页面。 */
        fun openSettings(context: android.content.Context): Boolean =
            MediaAccess.openSettings(context)

        fun component(context: android.content.Context): ComponentName =
            ComponentName(context, MediaNotificationListener::class.java)
    }
}
