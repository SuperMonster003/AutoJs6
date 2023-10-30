package org.autojs.autojs.service

import android.content.Context
import android.content.Intent
import android.provider.Settings
import org.autojs.autojs.core.notification.NotificationListenerService
import org.autojs.autojs.ui.main.drawer.ServiceItemHelper

class NotificationService(override val context: Context) : ServiceItemHelper {

    override val isRunning: Boolean
        get() = NotificationListenerService.instance != null

    override fun start() {
        config()
    }

    override fun stop() {
        config()
    }

    fun config() {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

}