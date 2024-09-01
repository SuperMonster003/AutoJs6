package org.autojs.autojs.util;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import org.autojs.autojs.tool.ForegroundServiceCreator;
import org.autojs.autojs6.R;

/**
 * Created by SuperMonster003 on Apr 10, 2022.
 */
public class ForegroundServiceUtils {

    public static void requestIfNeeded(Context context, Class<?> className) {
        if (!isRunning(context, className)) {
            request(context, className);
        }
    }

    public static void request(Context context, Class<?> className) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startService(context, className);
        }
    }

    public static void requestReadyIfNeeded(Context context, Class<?> className, Runnable runnable) {
        if (isRunning(context, className)) {
            runnable.run();
        } else {
            context.bindService(getService(context, className), new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    runnable.run();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                }
            }, Context.BIND_AUTO_CREATE);
        }
    }

    public static boolean startService(Context context, Class<?> className) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.startForegroundService(getService(context, className)) != null;
        } else {
            return context.startService(getService(context, className)) != null;
        }
    }

    public static boolean stopServiceIfNeeded(Context context, Class<?> className) {
        if (isRunning(context, className)) {
            return context.stopService(getService(context, className));
        }
        return true;
    }

    public static boolean isRunning(Context context, Class<?> className) {
        ActivityManager manager = (ActivityManager) context.getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (className.getName().equals(service.service.getClassName())) {
                return service.foreground;
            }
        }
        return false;
    }

    @NonNull
    private static Intent getService(Context context, Class<?> className) {
        return new Intent(context, className);
    }

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

        return new NotificationCompat.Builder(context, channelId)
                .setContentIntent(contentIntent)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.autojs6_material)
                .setChannelId(channelId)
                .setSilent(true)
                .build();
    }

    @NonNull
    private static String getChannelId(Class<?> className) {
        return className.getName() + ".foreground";
    }

    public static void startForeground(ForegroundServiceCreator creator) {
        Service service = creator.service;

        ForegroundServiceUtils.createNotificationChannelIfNeeded(service,
                creator.className,
                creator.serviceName,
                creator.serviceDescription);

        Notification notification = ForegroundServiceUtils.getNotification(service,
                creator.intent,
                creator.className,
                creator.notificationTitle,
                creator.notificationContent);

        service.startForeground(creator.notificationId, notification);
    }

    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static void startForeground(ForegroundServiceCreator creator, int foregroundServiceType) {
        Service service = creator.service;

        ForegroundServiceUtils.createNotificationChannelIfNeeded(service,
                creator.className,
                creator.serviceName,
                creator.serviceDescription);

        Notification notification = ForegroundServiceUtils.getNotification(service,
                creator.intent,
                creator.className,
                creator.notificationTitle,
                creator.notificationContent);

        service.startForeground(creator.notificationId, notification, foregroundServiceType);
    }

}
