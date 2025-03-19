package org.autojs.autojs.util

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs6.R

/**
 * Created by SuperMonster003 on Mar 10, 2023.
 */
object NotificationUtils {

    private val globalAppContext = GlobalAppContext.get()

    private val DEFAULT_CHANNEL_NAME = globalAppContext.getString(R.string.default_script_notification_channel_name)
    private val DEFAULT_CHANNEL_DESCRIPTION = globalAppContext.getString(R.string.default_script_notification_channel_description)

    /**
     * For Android 7.1 and below only.
     */
    private const val DEFAULT_PRIORITY = NotificationCompat.PRIORITY_HIGH

    private const val DEFAULT_IMPORTANCE = NotificationManager.IMPORTANCE_HIGH
    private const val DEFAULT_LOCKSCREEN_VISIBILITY = Notification.VISIBILITY_PUBLIC

    @JvmField
    val defaultTitle = globalAppContext.getString(R.string.default_script_notification_title)

    @JvmField
    val defaultContent = globalAppContext.getString(R.string.default_script_notification_content)

    @JvmStatic
    val defaultNotificationId: Int
        get() = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

    @JvmStatic
    fun notice(
        channelId: String,
        title: String?,
        content: String?,
        bigContent: String?,
        notificationId: Int?,
        autoCancel: Boolean?,
        isSilent: Boolean?,
        intent: Intent?,
        priority: Int?,
    ) = notice(
        createBuilder(channelId, title, content, bigContent),
        notificationId,
        autoCancel,
        isSilent,
        intent,
        priority,
    )

    @JvmStatic
    fun notice(
        builder: NotificationCompat.Builder,
        notificationId: Int?,
        autoCancel: Boolean?,
        isSilent: Boolean?,
        intent: Intent?,
        priority: Int?,
    ) {
        if (ActivityCompat.checkSelfPermission(globalAppContext, POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            throw RuntimeException(globalAppContext.getString(R.string.error_no_post_notifications_permission))
        }
        NotificationManagerCompat.from(globalAppContext).notify(
            notificationId ?: defaultNotificationId,
            builder.apply {
                intent?.let { setContentIntent(PendingIntent.getActivity(globalAppContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)) }
                autoCancel?.let { setAutoCancel(it) }
                isSilent?.let { setSilent(isSilent) }
                /**
                 * For Android 7.1 and below only.
                 */
                this.priority = priority ?: DEFAULT_PRIORITY
            }.build(),
        )
    }

    private fun createBuilder(channelId: String, title: String?, content: String?, bigContent: String?): NotificationCompat.Builder {
        return NotificationCompat.Builder(globalAppContext, channelId)
            .apply {
                title?.let { setContentTitle(it) }
                content?.let { setContentText(it) }
                bigContent?.let { setStyle(NotificationCompat.BigTextStyle().bigText(it)) }
                setSmallIcon(R.drawable.autojs6_status_bar_icon)
            }
    }

    @JvmStatic
    fun getSimpleBuilder(): NotificationCompat.Builder {
        @Suppress("DEPRECATION")
        return NotificationCompat.Builder(globalAppContext)
            .apply {
                setSmallIcon(R.drawable.autojs6_status_bar_icon)
                /**
                 * For Android 7.1 and below only.
                 */
                priority = DEFAULT_PRIORITY
                // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                //     setLargeIcon(BitmapFactory.decodeResource(globalAppContext.resources, R.drawable.autojs6_material))
                // }
            }
    }

    @JvmStatic
    @JvmName("createChannel")
    fun createNotificationChannel(
        id: String,
        name: String?,
        description: String?,
        importance: Int?,
        enableVibration: Boolean?,
        vibrationPattern: LongArray?,
        enableLights: Boolean?,
        lightColor: Int?,
        lockscreenVisibility: Int?,
    ) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(id, name ?: DEFAULT_CHANNEL_NAME, importance ?: DEFAULT_IMPORTANCE).apply {
                this.description = description ?: DEFAULT_CHANNEL_DESCRIPTION
                this.lockscreenVisibility = lockscreenVisibility ?: DEFAULT_LOCKSCREEN_VISIBILITY
                enableVibration?.let { this.enableVibration(it) }
                vibrationPattern?.let { this.vibrationPattern = it }
                enableLights?.let { this.enableLights(it) }
                lightColor?.let { this.lightColor = it }
            }.also {
                // Register the channel with the system; you cannot change the importance
                // or other notification behaviors after this
                globalAppContext.getSystemService(NotificationManager::class.java).createNotificationChannel(it)
            }
        }
    }

    @JvmStatic
    fun isEnabled() = NotificationManagerCompat.from(globalAppContext).areNotificationsEnabled()

    @JvmStatic
    fun launchSettings() {
        val localIntent = Intent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            localIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            localIntent.action = "android.settings.APPLICATION_DETAILS_SETTINGS"
            localIntent.data = Uri.fromParts("package", globalAppContext.packageName, null)
        } else {
            localIntent.action = "android.settings.APP_NOTIFICATION_SETTINGS"
            localIntent.putExtra("app_package", globalAppContext.packageName)
            localIntent.putExtra("app_uid", globalAppContext.applicationInfo.uid)
        }
        globalAppContext.startActivity(localIntent)
    }

    @JvmStatic
    fun requestPermission(launcher: ActivityResultLauncher<Array<String>>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(arrayOf(POST_NOTIFICATIONS))
        } else {
            launchSettings()
        }
    }

    @JvmStatic
    fun ensureEnabled() {
        if (!isEnabled()) {
            launchSettings()
            throw Exception(globalAppContext.getString(R.string.error_no_post_notifications_permission))
        }
    }

}