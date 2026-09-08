package org.autojs.autojs.core.notification

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.StatusBarNotification
import org.autojs.autojs.core.accessibility.NotificationListener
import java.util.concurrent.CopyOnWriteArrayList
import android.service.notification.NotificationListenerService as AndroidNotificationListenerService

/**
 * Created by Stardust on Oct 30, 2017.
 */
class NotificationListenerService : AndroidNotificationListenerService() {

    private val mNotificationListeners = CopyOnWriteArrayList<NotificationListener>()
    private val statusBarListeners = CopyOnWriteArrayList<StatusBarListener>()
    var connected = false
        private set

    interface StatusBarListener {
        fun onPosted(notification: StatusBarNotification)
        fun onDisconnected() = Unit
    }

    fun addStatusBarListener(listener: StatusBarListener) { statusBarListeners.add(listener) }
    fun removeStatusBarListener(listener: StatusBarListener) { statusBarListeners.remove(listener) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected = false
        statusBarListeners.forEach { it.onDisconnected() }
        statusBarListeners.clear()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        onNotificationPosted(sbn)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        statusBarListeners.forEach { it.onPosted(sbn) }
        for (listener in mNotificationListeners) {
            listener.onNotification(Notification.create(sbn.notification, sbn.packageName))
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) {}

    fun addListener(listener: NotificationListener) {
        mNotificationListeners.add(listener)
    }

    fun removeListener(listener: NotificationListener): Boolean {
        return mNotificationListeners.remove(listener)
    }

    override fun onDestroy() {
        connected = false
        statusBarListeners.forEach { it.onDisconnected() }
        statusBarListeners.clear()
        super.onDestroy()
        instance = null
    }

    companion object {

        @JvmStatic
        var instance: NotificationListenerService? = null
            private set

        @JvmStatic
        fun isNotificationListenerEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ) ?: return false
            val cn = ComponentName(context, NotificationListenerService::class.java).flattenToString()
            return enabled.contains(cn)
        }

        @JvmStatic
        fun requestRebindIfPossible(context: Context): Boolean {
            if (!isNotificationListenerEnabled(context)) return false
            return runCatching {
                val cn = ComponentName(context, NotificationListenerService::class.java)
                requestRebind(cn)
                true
            }.isSuccess
        }

    }

}
