package org.autojs.autojs.external.foreground

import android.app.Service
import android.content.Intent
import org.autojs.autojs.tool.ForegroundServiceCreator
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.util.ForegroundServiceUtils
import org.autojs.autojs6.R

/**
 * Modified by SuperMonster003 as of Apr 10, 2022.
 * Transformed by SuperMonster003 on May 13, 2023.
 */
class MainActivityForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        startForeground()
    }

    override fun onBind(intent: Intent) = null

    private fun startForeground() {
        ForegroundServiceUtils.startForeground(
            ForegroundServiceCreator.Builder(this)
                .setClassName(sClassName)
                .setIntent(MainActivity.getIntent(this))
                .setNotificationId(NOTIFICATION_ID)
                .setServiceName(R.string.foreground_notification_channel_name)
                .setServiceDescription(R.string.foreground_notification_channel_name)
                .setNotificationTitle(R.string.foreground_notification_title)
                .setNotificationContent(R.string.foreground_notification_text)
                .create()
        )
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {

        private const val NOTIFICATION_ID = 1
        private val sClassName = MainActivityForegroundService::class.java

    }

}
