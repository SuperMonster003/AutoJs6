package org.autojs.autojs.core.image.capture

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import org.autojs.autojs.tool.ForegroundServiceCreator
import org.autojs.autojs.util.ForegroundServiceUtils.FOREGROUND_SERVICE_TYPE_UNKNOWN
import org.autojs.autojs6.R

/**
 * Created by SuperMonster003 on Apr 10, 2022.
 * Transformed by SuperMonster003 on Dec 6, 2023.
 */
class ScreenCapturerForegroundService : Service() {

    private var mForegroundServiceCreator: ForegroundServiceCreator? = null

    private val mForegroundServiceType = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        }
        else -> FOREGROUND_SERVICE_TYPE_UNKNOWN
    }

    override fun onBind(intent: Intent): IBinder = Binder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mForegroundServiceCreator = ForegroundServiceCreator.Builder(this)
            .setClassName(ScreenCapturerForegroundService::class.java)
            .setIntent(
                // @Reference to TonyJiangWJ/Auto.js (https://github.com/TonyJiangWJ/Auto.js) by SuperMonster003 on Apr 10, 2022.
                // Intent(this, ScreenCaptureRequestActivity::class.java)

                packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    setPackage(null)
                }
            )
            .setNotificationId(NOTIFICATION_ID)
            .setServiceName(R.string.screen_capturer_foreground_notification_channel_name)
            .setServiceDescription(R.string.screen_capturer_foreground_notification_channel_name)
            .setNotificationTitle(R.string.screen_capturer_foreground_notification_title)
            .setNotificationContent(R.string.screen_capturer_foreground_notification_text)
            .create()
            .apply { startForeground(mForegroundServiceType) }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        mForegroundServiceCreator?.stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 0xCF
    }

}
