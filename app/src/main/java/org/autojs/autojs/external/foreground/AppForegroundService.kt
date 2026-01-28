package org.autojs.autojs.external.foreground

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.os.Build
import android.os.IBinder
import org.autojs.autojs.AbstractAutoJs.Companion.isInrt
import org.autojs.autojs.tool.ForegroundServiceCreator
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.util.ForegroundServiceUtils.FOREGROUND_SERVICE_TYPE_UNKNOWN
import org.autojs.autojs6.R
import org.autojs.autojs.inrt.LogActivity as LogActivityInrt

/**
 * Transformed by SuperMonster003 on May 13, 2023.
 * Modified by SuperMonster003 as of Jan 28, 2026.
 */
class AppForegroundService : Service() {

    private lateinit var mForegroundServiceCreator: ForegroundServiceCreator

    private val mForegroundServiceType = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
            FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }
        else -> FOREGROUND_SERVICE_TYPE_UNKNOWN
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        val label = packageManager.getApplicationLabel(applicationInfo).toString()
        val intentClass = if (!isInrt) MainActivity::class.java else LogActivityInrt::class.java

        // @Hint by SuperMonster003 on Dec 4, 2023.
        //  ! service.startForeground() may need to be called within "onCreate" rather than a static method.
        //  ! Reference: https://stackoverflow.com/questions/44425584/context-startforegroundservice-did-not-then-call-service-startforeground
        //  ! zh-CN:
        //  ! service.startForeground() 可能需要在 "onCreate" 中调用, 而非在静态方法中.
        //  ! 参阅: https://stackoverflow.com/questions/44425584/context-startforegroundservice-did-not-then-call-service-startforeground
        mForegroundServiceCreator = ForegroundServiceCreator.Builder(this)
            .setClassName(AppForegroundService::class.java)
            .setIntent(Intent(this, intentClass))
            .setNotificationId(NOTIFICATION_ID)
            .setServiceName(getString(R.string.foreground_notification_channel_name, label))
            .setServiceDescription(getString(R.string.foreground_notification_channel_name, label))
            .setNotificationTitle(getString(R.string.foreground_notification_title, label))
            .setNotificationContent(getString(R.string.foreground_notification_text, label))
            .create()
            .apply { startForeground(mForegroundServiceType) }
    }

    override fun onDestroy() {
        mForegroundServiceCreator.stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 0xBF
    }

}
