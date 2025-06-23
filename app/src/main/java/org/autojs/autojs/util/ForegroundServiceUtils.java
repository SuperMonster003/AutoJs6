package org.autojs.autojs.util;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.autojs.autojs6.R;

/**
 * Created by SuperMonster003 on Apr 10, 2022.
 */
public class ForegroundServiceUtils {

    public static int FOREGROUND_SERVICE_TYPE_UNKNOWN = -33127;

    public static void createNotificationChannelIfNeeded(Context context, Class<?> className, String name, @Nullable String description) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = getChannelId(className);

            NotificationChannel channel = new NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_MIN);

            if (description == null) {
                channel.setDescription(name);
            } else {
                channel.setDescription(description);
            }

            channel.enableLights(false);

            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    public static Notification getNotification(Context context, Intent intent, Class<?> className, String title, String content) {
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        String channelId = getChannelId(className);

        Notification notification = new NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.autojs6_status_bar_icon)
                .setAutoCancel(true)
                .setOngoing(false)
                .setSilent(true)
                .setWhen(System.currentTimeMillis())
                .setChannelId(channelId)
                .build();

        notification.flags |= Notification.FLAG_FOREGROUND_SERVICE;

        return notification;
    }

    @NonNull
    public static String getChannelId(Class<?> className) {
        return className.getName() + ".foreground";
    }


    /**
     * @noinspection deprecation
     */
    public static boolean isRunning(Context context, Class<?> clazz) {
        ActivityManager manager = (ActivityManager) context.getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (clazz.getName().equals(service.service.getClassName())) {
                return service.foreground;
            }
        }
        return false;
    }

}
