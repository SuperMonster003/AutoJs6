package org.autojs.autojs.external.foreground;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.stardust.autojs.util.ForegroundServiceCreator;
import com.stardust.autojs.util.ForegroundServiceUtils;

import org.autojs.autojs6.R;
import org.autojs.autojs.ui.main.MainActivity_;

/**
 * Modified by SuperMonster003 as of Apr 10, 2022.
 */
public class MainActivityForegroundService extends Service {

    private static final int NOTIFICATION_ID = 1;
    private static final Class<MainActivityForegroundService> sClassName = MainActivityForegroundService.class;

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
                .setIntent(MainActivity_.intent(this).get())
                .setNotificationId(NOTIFICATION_ID)
                .setServiceName(R.string.foreground_notification_channel_name)
                .setServiceDescription(R.string.foreground_notification_channel_name)
                .setNotificationTitle(R.string.foreground_notification_title)
                .setNotificationContent(R.string.foreground_notification_text)
                .create());
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }

}
