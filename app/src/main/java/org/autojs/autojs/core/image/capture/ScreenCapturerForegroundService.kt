package org.autojs.autojs.core.image.capture

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import org.autojs.autojs.tool.ForegroundServiceCreator
import org.autojs.autojs.util.ForegroundServiceUtils.FOREGROUND_SERVICE_TYPE_UNKNOWN
import org.autojs.autojs6.R

/**
 * Created by SuperMonster003 on Apr 10, 2022.
 * Transformed by SuperMonster003 on Dec 6, 2023.
 */
class ScreenCapturerForegroundService : Service() {

    private var mForegroundServiceCreator: ForegroundServiceCreator? = null
    private var mForegroundStarted = false

    private val mForegroundServiceType = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        }
        else -> FOREGROUND_SERVICE_TYPE_UNKNOWN
    }

    override fun onBind(intent: Intent): IBinder = Binder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        clearForegroundStartFailure()
        try {
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
            mForegroundStarted = true
        } catch (e: RuntimeException) {
            mForegroundStarted = false
            sForegroundStartFailure = e
            Log.w(TAG, "Failed to start screen capture foreground service", e)
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (mForegroundStarted) {
            mForegroundServiceCreator?.stopForeground(STOP_FOREGROUND_REMOVE)
        }
        mForegroundStarted = false
        instance = null
        super.onDestroy()
    }

    companion object {

        private const val TAG = "ScreenCapturerFgService"

        const val NOTIFICATION_ID = 0xCF

        @Volatile
        var instance: ScreenCapturerForegroundService? = null

        @Volatile
        private var sForegroundStartFailure: RuntimeException? = null

        @JvmStatic
        fun clearForegroundStartFailure() {
            sForegroundStartFailure = null
        }

        @JvmStatic
        fun getForegroundStartFailure(): RuntimeException? = sForegroundStartFailure

        @JvmStatic
        fun isForegroundStarted() = instance?.mForegroundStarted == true

        fun stopService() {
            instance?.stopSelf()
        }

    }

}
