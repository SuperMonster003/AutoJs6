package org.autojs.autojs.external.foreground

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
import androidx.core.app.NotificationCompat
import org.autojs.autojs.core.plugin.python.PythonLongRunningExecutionRegistry
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs6.R

/** Host-owned foreground lifetime for an explicitly admitted long-running Python project. */
class PythonLongRunningForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private var activeToken: String? = null
    private var destroyed = false
    private val pulse = object : Runnable {
        override fun run() {
            if (destroyed) return
            PythonLongRunningExecutionRegistry.pulseForegroundService()
            refreshNotification()
            handler.postDelayed(this, PULSE_INTERVAL_MILLIS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startForegroundCompat(buildNotification(PythonLongRunningExecutionRegistry.snapshot(null)))
        PythonLongRunningExecutionRegistry.pulseForegroundService()
        handler.postDelayed(pulse, PULSE_INTERVAL_MILLIS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val token = intent?.getStringExtra(EXTRA_TOKEN)
        if (token != null) activeToken = token
        if (intent?.action == ACTION_STOP && token != null) {
            PythonLongRunningExecutionRegistry.requestStop(token)
        }
        refreshNotification()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacks(pulse)
        stopForeground(STOP_FOREGROUND_REMOVE)
        PythonLongRunningExecutionRegistry.foregroundServiceStopped()
        super.onDestroy()
    }

    private fun refreshNotification() {
        val snapshot = PythonLongRunningExecutionRegistry.snapshot(activeToken)
        if (snapshot == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        activeToken = snapshot.token
        runCatching { notificationManager.notify(NOTIFICATION_ID, buildNotification(snapshot)) }
    }

    private fun buildNotification(
        snapshot: PythonLongRunningExecutionRegistry.Snapshot?,
    ): Notification {
        val title = getString(R.string.python_long_running_notification_title)
        val content = when (snapshot?.phase) {
            PythonLongRunningExecutionRegistry.Phase.STARTING -> getString(
                R.string.python_long_running_notification_starting,
                snapshot.label,
            )
            PythonLongRunningExecutionRegistry.Phase.RUNNING -> getString(
                R.string.python_long_running_notification_running,
                snapshot.label,
            )
            PythonLongRunningExecutionRegistry.Phase.STOPPING -> getString(
                R.string.python_long_running_notification_stopping,
                snapshot.label,
            )
            null -> getString(R.string.python_long_running_notification_preparing)
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.autojs6_status_bar_icon)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (snapshot != null && snapshot.phase != PythonLongRunningExecutionRegistry.Phase.STOPPING) {
            val stopIntent = intent(this, snapshot.token, stop = true)
            val stopPendingIntent = PendingIntent.getService(
                this,
                snapshot.token.hashCode(),
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(
                R.drawable.ic_stop_button_fill,
                getString(R.string.python_long_running_notification_stop),
                stopPendingIntent,
            )
        }
        if (
            snapshot != null &&
            snapshot.phase == PythonLongRunningExecutionRegistry.Phase.RUNNING &&
            snapshot.elapsedMillis > 0L
        ) {
            builder
                .setWhen(System.currentTimeMillis() - snapshot.elapsedMillis)
                .setUsesChronometer(true)
        } else {
            builder.setShowWhen(false)
        }
        return builder.build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.python_long_running_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.python_long_running_notification_channel_description)
                setShowBadge(false)
            },
        )
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "autojs6.python.long_running"
        private const val NOTIFICATION_ID = 0x5059_16
        private const val PULSE_INTERVAL_MILLIS = 2_000L
        private const val ACTION_START = "org.autojs.autojs.python.LONG_RUNNING_START"
        private const val ACTION_STOP = "org.autojs.autojs.python.LONG_RUNNING_STOP"
        private const val EXTRA_TOKEN = "pythonLongRunningToken"

        fun intent(context: Context, token: String, stop: Boolean): Intent =
            Intent(context, PythonLongRunningForegroundService::class.java)
                .setAction(if (stop) ACTION_STOP else ACTION_START)
                .putExtra(EXTRA_TOKEN, token)
    }
}
