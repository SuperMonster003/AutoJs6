package org.autojs.autojs.engine

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.service.notification.StatusBarNotification
import android.view.KeyEvent
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.accessibility.AccessibilityNotificationObserver
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.AccessibilityServiceCallback
import org.autojs.autojs.core.accessibility.OnKeyListener
import org.autojs.autojs.core.notification.NotificationListenerService
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.UUID

/** Independent, execution-owned subscriptions for main threads, Workers and Bun clients. */
internal class NodeEventObservers(private val context: Context) {
    private val sessions = linkedMapOf<String, Session>()
    private var destroyed = false

    @Synchronized
    fun observe(kind: String, stream: NodeBridgeEventStream?): Session {
        check(!destroyed) { "The script has ended." }
        sessions.entries.removeAll { it.value.closed }
        val session = Session(stream?.subscriptionId ?: "events-${UUID.randomUUID()}", kind, stream)
        try {
            when (kind) {
                "notification" -> attachNotifications(session)
                "toast" -> attachToasts(session)
                "key" -> session.detach = observeKeys(
                    { keyCode, event -> session.publish(JSONObject()
                        .put("type", "key").put("keyCode", keyCode)
                        .put("key", KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").lowercase())
                        .put("action", if (event.action == KeyEvent.ACTION_DOWN) "down" else "up")
                        .put("repeatCount", event.repeatCount).put("eventTime", event.eventTime)) },
                    { session.close("accessibility-disconnected") },
                )
                "broadcast" -> attachBroadcasts(session)
                else -> throw IllegalArgumentException("Unknown event source: $kind")
            }
            sessions[session.id] = session
            return session
        } catch (error: Throwable) {
            session.close("registration-failed")
            throw error
        }
    }

    @Synchronized fun drain(id: String): JSONArray = requireNotNull(sessions[id]) { "Event subscription is closed or belongs to another execution." }.drain()
    @Synchronized fun isDestroyed(): Boolean = destroyed
    @Synchronized fun activeCount(): Int = sessions.values.count { !it.closed }
    @Synchronized fun close(id: String, reason: String): Boolean = sessions.remove(id)?.let { it.close(reason); true } ?: false
    @Synchronized fun closeAll() { destroyed = true; sessions.values.forEach { it.close("script-ended") }; sessions.clear() }

    private fun attachNotifications(session: Session) {
        if (!NotificationListenerService.isNotificationListenerEnabled(context)) {
            throw SecurityException("Enable notification access for AutoJs6 in Android settings to observe notifications.")
        }
        val service = NotificationListenerService.instance?.takeIf { it.connected }
            ?: throw IllegalStateException("AutoJs6 notification listener is not connected yet.")
        val listener = object : NotificationListenerService.StatusBarListener {
            override fun onPosted(notification: StatusBarNotification) {
                val extras = notification.notification.extras
                session.publish(JSONObject().put("type", "notification")
                    .put("id", notification.id).put("key", notification.key)
                    .put("packageName", notification.packageName).put("tag", notification.tag ?: JSONObject.NULL)
                    .put("title", extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty())
                    .put("text", extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty())
                    .put("postTime", notification.postTime))
            }
            override fun onDisconnected() { session.close("notification-listener-disconnected") }
        }
        session.detach = { service.removeStatusBarListener(listener) }
        service.addStatusBarListener(listener)
    }

    private fun attachToasts(session: Session) {
        requireAccessibility()
        val observer = AutoJs.instance.notificationObserver
        val listener = object : AccessibilityNotificationObserver.ToastListener {
            override fun onToast(toast: AccessibilityNotificationObserver.Toast) {
                session.publish(JSONObject().put("type", "toast").put("packageName", toast.packageName).put("text", toast.text.orEmpty()))
            }
        }
        val disconnect = disconnectCallback { session.close("accessibility-disconnected") }
        session.detach = {
            observer.removeToastListener(listener)
            AccessibilityService.removeDisconnectCallback(disconnect)
        }
        observer.addToastListener(listener)
        AccessibilityService.addDisconnectCallback(disconnect)
    }

    private fun attachBroadcasts(session: Session) {
        var lastBattery: String? = null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val event = when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> JSONObject().put("type", "screen_on")
                    Intent.ACTION_SCREEN_OFF -> JSONObject().put("type", "screen_off")
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        JSONObject().put("type", "battery_changed")
                            .put("level", level).put("scale", scale)
                            .put("percent", if (level >= 0 && scale > 0) level * 100.0 / scale else -1.0)
                            .put("isCharging", status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
                    }
                    else -> return
                }
                if (event.optString("type") == "battery_changed") {
                    val snapshot = event.toString()
                    if (snapshot == lastBattery) return
                    lastBattery = snapshot
                }
                session.publish(event)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        val sticky = if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") context.registerReceiver(receiver, filter)
        session.detach = { context.unregisterReceiver(receiver) }
        // Some OEMs do not enqueue an initial callback for a background registration. Preserve the
        // actual sticky snapshot returned by Android and deduplicate the later identical callback.
        sticky?.let { receiver.onReceive(context, it) }
    }

    class Session(val id: String, val kind: String, private val stream: NodeBridgeEventStream?) {
        @Volatile var closed = false
            private set
        var detach: () -> Unit = {}
        private val queue = ArrayDeque<JSONObject>()
        private var dropped = 0
        @Synchronized fun publish(event: JSONObject) {
            if (closed) return
            event.put("time", System.currentTimeMillis()).put("source", kind)
            if (stream != null) stream.publish(event) else {
                if (queue.size == 128) { queue.removeFirst(); dropped++ }
                queue.addLast(event)
            }
        }
        @Synchronized fun drain(): JSONArray = JSONArray().also { result ->
            if (dropped > 0) {
                result.put(JSONObject().put("type", "overflow").put("source", kind).put("dropped", dropped).put("time", System.currentTimeMillis()))
                dropped = 0
            }
            while (queue.isNotEmpty() && result.length() < 32) result.put(queue.removeFirst())
        }
        @Synchronized fun close(reason: String) {
            if (closed) return
            queue.clear()
            publish(JSONObject().put("type", "close").put("reason", reason))
            closed = true
            detach()
            detach = {}
        }
        fun toJson(): JSONObject = JSONObject().put("id", id).put("source", kind).put("closed", closed)
    }

    companion object {
        private fun requireAccessibility(): AccessibilityService {
            if (!AccessibilityService.registry.isOperational) throw SecurityException("Enable AutoJs6 accessibility to observe keys or Toast messages.")
            return AccessibilityService.instance ?: throw SecurityException("AutoJs6 accessibility is not connected.")
        }
        private fun disconnectCallback(action: () -> Unit) = object : AccessibilityServiceCallback {
            override fun onConnected() = Unit
            override fun onDisconnected() = action()
        }
        fun observeKeys(onKey: (Int, KeyEvent) -> Unit, onDisconnect: () -> Unit): () -> Unit {
            val service = requireAccessibility()
            val listener = object : OnKeyListener {
                override fun onKeyEvent(keyCode: Int, event: KeyEvent) {
                    if (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) onKey(keyCode, event)
                }
            }
            val disconnect = disconnectCallback(onDisconnect)
            service.onKeyObserver.addListener(listener)
            AccessibilityService.addDisconnectCallback(disconnect)
            return {
                service.onKeyObserver.removeListener(listener)
                AccessibilityService.removeDisconnectCallback(disconnect)
            }
        }
    }
}
