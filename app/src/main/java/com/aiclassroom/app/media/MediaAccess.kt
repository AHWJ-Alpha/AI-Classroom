package com.aiclassroom.app.media

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/**
 * 通知使用权的检测与引导。读取其他 App 的播放信息必须由用户在系统设置里手动授权，
 * 这里只做「查状态 / 跳设置 / 弹一句引导」，供音乐插件的首启流程调用。
 *
 * 授权状态有三条互相兜底的判据，任一成立即视为可用：
 * 1. NotificationManagerCompat.getEnabledListenerPackages —— 标准做法；
 * 2. 监听服务当前是否被系统绑定（onListenerConnected）—— MIUI 会把
 *    enabled_notification_listeners 里本 App 的条目过滤掉，这时 1 会漏判；
 * 3. 能否真的拿到 MediaSessionManager 的活跃会话 —— 能力的最终证据。
 */
object MediaAccess {

    private const val TAG = "MediaAccess"

    /** 已授予通知使用权（能读别的 App 的播放信息）？ */
    fun isGranted(context: Context): Boolean {
        if (listenerEnabled(context)) return true
        if (MediaNotificationListener.isConnected()) return true
        return canReadSessions(context)
    }

    /** 标准判据：系统的「通知使用权」里是否有我们。 */
    fun listenerEnabled(context: Context): Boolean {
        val enabled = try {
            NotificationManagerCompat.getEnabledListenerPackages(context)
        } catch (t: Throwable) {
            Log.w(TAG, "getEnabledListenerPackages failed: ${t.message}")
            return false
        }
        val packageName = context.packageName
        return enabled.any { it == packageName || it.startsWith("$packageName/") }
    }

    /** 能力判据：拿得到活跃媒体会话就说明授权确实生效了。 */
    fun canReadSessions(context: Context): Boolean {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            ?: return false
        val component = ComponentName(context, MediaNotificationListener::class.java)
        return try {
            manager.getActiveSessions(component)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "getActiveSessions denied: ${t.message}")
            false
        }
    }

    /** 跳到系统的「通知使用权」列表页；国产 ROM 可能改写入口，回退到系统设置首页。 */
    fun openSettings(context: Context): Boolean {
        try {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return true
        } catch (t: Throwable) {
            Log.w(TAG, "open listener settings failed: ${t.message}")
        }
        return try {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (t: Throwable) {
            Log.w(TAG, "open settings failed: ${t.message}")
            false
        }
    }

    /** 旧版 ROM 的入口叫「辅助功能 → 通知使用权」，两个都列出来方便用户自己找。 */
    fun settingsHint(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "设置 → 应用设置 → 特殊权限 → 通知使用权"
        } else {
            "设置 → 通知和状态栏 → 通知使用权"
        }

    /**
     * 未授权时弹引导框；已授权直接返回 true。调用方拿到 false 就不要继续读播放信息。
     */
    fun ensureGranted(activity: Activity): Boolean {
        if (isGranted(activity)) return true
        AlertDialog.Builder(activity)
            .setTitle("需要读取播放信息")
            .setMessage(
                "显示和控制当前播放的歌曲，需要允许「AI Classroom」读取通知。\n\n" + settingsHint()
            )
            .setPositiveButton("去授权") { _, _ -> openSettings(activity) }
            .setNegativeButton("取消", null)
            .show()
        return false
    }
}
