package org.autojs.autojs.engine

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
import org.autojs.autojs6.R
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Explicit camera/microphone foreground leases for media owned by visible-started web pages. */
class RuntimeWebMediaService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        if (intent?.action == STOP) {
            val active = entries.values.toList(); entries.clear()
            active.forEach { it.stopped() }; stopIfIdle()
            return START_NOT_STICKY
        }
        val id = intent?.getStringExtra("id"); val entry = entries[id]
        if (entry == null) { stopIfIdle(); return START_NOT_STICKY }
        entry.owner = this
        try {
            val manager = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL,
                getString(R.string.text_runtime_web_media_channel), NotificationManager.IMPORTANCE_LOW))
            val stop = PendingIntent.getService(this, 0, Intent(this, RuntimeWebMediaService::class.java).setAction(STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(getString(R.string.text_runtime_web_media_channel)).setContentText(getString(R.string.text_runtime_web_media_capturing))
                .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(android.R.drawable.ic_media_pause, getString(R.string.text_stop), stop).build()
            if (Build.VERSION.SDK_INT >= 30) startForeground(ID, notification, entries.values.fold(0) { types, item -> types or item.types })
            else startForeground(ID, notification)
            entry.ready(Result.success(Unit))
        } catch (error: RuntimeException) { entries.remove(id); entry.ready(Result.failure(error)); stopIfIdle() }
        return START_NOT_STICKY
    }
    private fun stopIfIdle() { if (entries.isEmpty()) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() } }
    override fun onDestroy() {
        if (instance === this) instance = null
        // A new acquire may already be queued while the previous idle instance is being destroyed.
        val active = entries.filterValues { it.owner === this }.toList()
        active.forEach { (id, entry) -> if (entries.remove(id, entry)) entry.stopped() }
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "runtime_web_media"
        private const val ID = 0xD3
        private const val STOP = "org.autojs.autojs6.STOP_WEB_MEDIA"
        private data class Entry(val types: Int, val ready: (Result<Unit>) -> Unit, val stopped: () -> Unit, var owner: RuntimeWebMediaService? = null)
        private val entries = ConcurrentHashMap<String, Entry>()
        private val main = Handler(Looper.getMainLooper())
        private var instance: RuntimeWebMediaService? = null
        internal fun acquire(context: Context, camera: Boolean, microphone: Boolean, ready: (Result<Unit>) -> Unit, stopped: () -> Unit): Closeable {
            val id = UUID.randomUUID().toString()
            val types = (if (camera) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0) or
                (if (microphone) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
            require(types != 0) { "Background capture needs camera or microphone" }
            entries[id] = Entry(types, ready, stopped)
            try {
                val intent = Intent(context, RuntimeWebMediaService::class.java).putExtra("id", id)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            } catch (error: RuntimeException) { entries.remove(id); ready(Result.failure(error)) }
            return Closeable { entries.remove(id); main.post { instance?.stopIfIdle() } }
        }
    }
}
