package com.stardust.autojs.core.image.capture;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.stardust.autojs.R;
import com.stardust.autojs.util.ForegroundServiceCreator;
import com.stardust.autojs.util.ForegroundServiceUtils;

/**
 * Created by SuperMonster003 on Apr 10, 2022.
 */
public class ScreenCapturerForegroundService extends Service {

    private static final int NOTIFICATION_ID = 2;
    private static final Class<ScreenCapturerForegroundService> sClassName = ScreenCapturerForegroundService.class;

    public static void start(Context context) {
        ForegroundServiceUtils.startService(context, sClassName);
    }

    public static void stop(Context context) {
        ForegroundServiceUtils.stopServiceIfNeeded(context, sClassName);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground();
    }

    private void startForeground() {
        ForegroundServiceUtils.startForeground(new ForegroundServiceCreator.Builder(this)
                .setClassName(sClassName)
                .setIntent(
                        // @Reference to TonyJiangWJ/Auto.js on Apr 10, 2022
                        ScreenCaptureRequestActivity.getIntent(this)
                )
                .setNotificationId(NOTIFICATION_ID)
                .setServiceName(R.string.screen_capturer_foreground_notification_channel_name)
                .setServiceDescription(R.string.screen_capturer_foreground_notification_channel_name)
                .setNotificationTitle(R.string.screen_capturer_foreground_notification_title)
                .setNotificationContent(R.string.screen_capturer_foreground_notification_text)
                .create());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
    }

}
