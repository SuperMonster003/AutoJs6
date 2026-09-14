package org.autojs.autojs.core.terminal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.autojs.autojs.ui.terminal.TerminalActivity
import org.autojs.autojs6.R

/**
 * Foreground lifetime for terminal sessions: while at least one shell is registered in
 * [TerminalSessionManager] the app process stays in the foreground, so `npm install` or a
 * long-running script survives leaving the terminal screen. The notification shows the newest
 * session's working directory, opens the terminal on tap and offers "Close sessions".
 *
 * zh-CN: 终端会话的前台生命周期: 只要 [TerminalSessionManager] 中仍有 shell, 应用进程就保持前台,
 * 使 `npm install` 或长时间运行的脚本在离开终端界面后继续运行. 通知显示最新会话的工作目录,
 * 点击可打开终端, 并提供 "关闭会话" 操作.
 */
class TerminalSessionService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private var destroyed = false
    private val sessionListener: () -> Unit = { handler.post { refresh() } }
    private val pulse = object : Runnable {
        override fun run() {
            if (destroyed) return
            refresh()
            handler.postDelayed(this, PULSE_INTERVAL_MILLIS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startForegroundCompat(buildNotification(TerminalSessionManager.activeSessions))
        TerminalSessionManager.addListener(sessionListener)
        handler.postDelayed(pulse, PULSE_INTERVAL_MILLIS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CLOSE_ALL) {
            TerminalSessionManager.closeAll()
        }
        refresh()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacks(pulse)
        TerminalSessionManager.removeListener(sessionListener)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /**
     * Re-renders the notification (the shell's cwd changes over time) and stops once no session is left.
     * zh-CN: 重新渲染通知 (shell 的工作目录会变化), 没有会话时停止服务.
     */
    private fun refresh() {
        if (destroyed) return
        val sessions = TerminalSessionManager.activeSessions
        if (sessions.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        runCatching { notificationManager.notify(NOTIFICATION_ID, buildNotification(sessions)) }
            .onFailure { Log.w(TAG, "Could not update the terminal notification: ${it.message}") }
    }

    private fun buildNotification(sessions: List<TerminalSessionManager.Session>): Notification {
        val title = if (sessions.size > 1) {
            getString(R.string.text_terminal_notification_title_multiple, sessions.size)
        } else {
            getString(R.string.text_terminal_notification_title)
        }
        val newest = sessions.lastOrNull()
        val content = newest?.let { session ->
            val directory = session.pty.currentDirectory()?.path ?: session.initialDirectory
            session.paths.toTildePath(directory)
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            TerminalActivity.intent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val closeIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TerminalSessionService::class.java).setAction(ACTION_CLOSE_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.autojs6_status_bar_icon)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .addAction(
                R.drawable.ic_close_black_24dp,
                getString(R.string.text_terminal_notification_close_sessions),
                closeIntent,
            )
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.text_terminal_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.text_terminal_notification_channel_description)
                setShowBadge(false)
            },
        )
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {

        private const val TAG = "TerminalSessionService"
        private const val CHANNEL_ID = "autojs6.terminal.sessions"
        private const val NOTIFICATION_ID = 0x5445_524D
        private const val PULSE_INTERVAL_MILLIS = 5_000L
        private const val ACTION_CLOSE_ALL = "org.autojs.autojs.terminal.CLOSE_ALL_SESSIONS"

        /**
         * Starts the foreground service; a refusal (background start restrictions) is only logged
         * because the session itself keeps working, just without foreground protection.
         * zh-CN: 启动前台服务; 被拒绝 (后台启动限制) 时仅记录日志, 会话本身照常工作, 只是没有前台保护.
         */
        fun ensureStarted(context: Context) {
            val app = context.applicationContext
            runCatching { ContextCompat.startForegroundService(app, Intent(app, TerminalSessionService::class.java)) }
                .onFailure { Log.w(TAG, "Could not start the terminal session service: ${it.message}") }
        }

    }

}
