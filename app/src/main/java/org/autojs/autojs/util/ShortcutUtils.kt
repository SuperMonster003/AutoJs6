package org.autojs.autojs.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Created by SuperMonster003 on Oct 11, 2022.
 */
object ShortcutUtils {

    @JvmStatic
    fun requestPinShortcut(context: Context, id: Int, className: String, longLabelResId: Int, shortLabelResId: Int, iconResId: Int) {
        requestPinShortcut(context, context.getString(id), className, context.getString(longLabelResId), context.getString(shortLabelResId), Icon.createWithResource(context, iconResId))
    }

    @JvmStatic
    fun requestPinShortcut(context: Context, id: String, className: String, longLabel: CharSequence, shortLabel: CharSequence, icon: Icon) {
        requestPinShortcut(context, id, Intent("android.intent.action.VIEW").setClassName(context, className), longLabel, shortLabel, icon)
    }

    @JvmStatic
    fun requestPinShortcut(context: Context, id: String, intent: Intent, longLabel: CharSequence, shortLabel: CharSequence, icon: Icon) {
        // The shortcut must be enabled.
        // The "id" for ShortcutInfoCompat.Builder must contain stable, constant strings.
        val pinShortcutInfo = ShortcutInfoCompat.Builder(context, id)
            .setIntent(intent)
            .setLongLabel(longLabel)
            .setShortLabel(shortLabel)
            .setIcon(IconCompat.createFromIcon(context, icon))
            .setAlwaysBadged()
            .build()

        // Create the PendingIntent object only if your app needs to be notified
        // that the user allowed the shortcut to be pinned. Note that, if the
        // pinning operation fails, your app isn't notified. We assume here that the
        // app has implemented a method called createShortcutResultIntent() that
        // returns a broadcast intent.
        val pinnedShortcutCallbackIntent = ShortcutManagerCompat.createShortcutResultIntent(context, pinShortcutInfo)

        // Configure the intent so that your app's broadcast receiver gets
        // the callback successfully.For details, see PendingIntent.getBroadcast().
        val successCallback = PendingIntent.getBroadcast(context, 0, pinnedShortcutCallbackIntent, PendingIntent.FLAG_IMMUTABLE)

        ShortcutManagerCompat.requestPinShortcut(context, pinShortcutInfo, successCallback.intentSender)
    }

}