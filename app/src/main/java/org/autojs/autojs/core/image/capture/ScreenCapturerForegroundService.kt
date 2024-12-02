package org.autojs.autojs.core.image.capture;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

import org.autojs.autojs6.R;
import org.autojs.autojs.tool.ForegroundServiceCreator;
import org.autojs.autojs.util.ForegroundServiceUtils;

/**
 * Created by SuperMonster003 on Apr 10, 2022.
 */
public class ScreenCapturerForegroundService extends Service {

    private static final int NOTIFICATION_ID = 2;
    private static final Class<ScreenCapturerForegroundService> sClassName = ScreenCapturerForegroundService.class;

    public static boolean start(Context context) {
        return ForegroundServiceUtils.startService(context, sClassName);
    }

    public static boolean stop(Context context) {
        return ForegroundServiceUtils.stopServiceIfNeeded(context, sClassName);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new Binder();
    }

    private void startForeground() {
        ForegroundServiceCreator foregroundServiceCreator = new ForegroundServiceCreator.Builder(this)
                .setClassName(sClassName)
                .setIntent(
                        // @Reference to TonyJiangWJ/Auto.js (https://github.com/TonyJiangWJ/Auto.js) on Apr 10, 2022.
                        ScreenCaptureRequestActivity.getIntent(this)
                )
                .setNotificationId(NOTIFICATION_ID)
                .setServiceName(R.string.screen_capturer_foreground_notification_channel_name)
                .setServiceDescription(R.string.screen_capturer_foreground_notification_channel_name)
                .setNotificationTitle(R.string.screen_capturer_foreground_notification_title)
                .setNotificationContent(R.string.screen_capturer_foreground_notification_text)
                .create();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundServiceUtils.startForeground(foregroundServiceCreator, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            ForegroundServiceUtils.startForeground(foregroundServiceCreator);
        }
    }

    @Override
    public void onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

}
