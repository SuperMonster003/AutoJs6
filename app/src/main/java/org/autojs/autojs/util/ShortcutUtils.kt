package org.autojs.autojs.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.content.getSystemService
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs6.R

/**
 * Created by SuperMonster003 on Oct 11, 2022.
 */
object ShortcutUtils {

    @JvmStatic
    fun requestPinShortcut(context: Context, id: Int, className: String, longLabelResId: Int, shortLabelResId: Int, iconResId: Int) {
        requestPinShortcut(context, context.getString(id), className, context.getString(longLabelResId), context.getString(shortLabelResId), IconCompat.createWithResource(context, iconResId))
    }

    @JvmStatic
    fun requestPinShortcut(context: Context, id: String, intent: Intent, longLabel: CharSequence, shortLabel: CharSequence, iconResId: Int) {
        requestPinShortcut(context, id, intent, longLabel, shortLabel, IconCompat.createWithResource(context, iconResId))
    }

    @JvmStatic
    fun requestPinShortcut(context: Context, id: String, intent: Intent, longLabel: CharSequence, shortLabel: CharSequence, drawable: Drawable) {
        requestPinShortcut(context, id, intent, longLabel, shortLabel, BitmapUtils.drawableToBitmap(drawable))
    }

    @JvmStatic
    fun requestPinShortcut(context: Context, id: String, className: String, longLabel: CharSequence, shortLabel: CharSequence, icon: IconCompat) {
        requestPinShortcut(context, id, Intent("android.intent.action.VIEW").setClassName(context, className), longLabel, shortLabel, icon)
    }

    @JvmStatic
    fun requestPinShortcut(context: Context, id: String, intent: Intent, longLabel: CharSequence, shortLabel: CharSequence, icon: Icon) {
        requestPinShortcut(context, id, intent, longLabel, shortLabel, IconCompat.createFromIcon(context, icon))
    }

    @JvmStatic
    fun requestPinShortcut(context: Context, id: String, intent: Intent, longLabel: CharSequence, shortLabel: CharSequence, iconBitmap: Bitmap) {
        requestPinShortcut(context, id, intent, longLabel, shortLabel, IconCompat.createWithBitmap(iconBitmap))
    }

    @JvmStatic
    fun requestPinShortcut(context: Context, id: String, intent: Intent, longLabel: CharSequence, shortLabel: CharSequence, icon: IconCompat?) {
        // The shortcut must be enabled.
        // The "id" for ShortcutInfoCompat.Builder must contain stable, constant strings.
        val pinShortcutInfo = ShortcutInfoCompat.Builder(context, id)
            .setIntent(intent.apply { action = action ?: Intent.ACTION_VIEW })
            .setLongLabel(longLabel)
            .setShortLabel(shortLabel)
            .setIcon(icon)
            .setAlwaysBadged()
            .build()

        // Create the PendingIntent object only if your app needs to be notified
        // that the user allowed the shortcut to be pinned.
        // Note that if the pinning operation fails, your app isn't notified.
        // We assume here that the app has implemented a method called createShortcutResultIntent()
        // that returns a broadcast intent.
        val pinnedShortcutCallbackIntent = ShortcutManagerCompat.createShortcutResultIntent(context, pinShortcutInfo)

        // Configure the intent so that your app's broadcast receiver gets
        // the callback successfully.For details, see PendingIntent.getBroadcast().
        val successCallback = PendingIntent.getBroadcast(context, 0, pinnedShortcutCallbackIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        ShortcutManagerCompat.requestPinShortcut(context, pinShortcutInfo, successCallback.intentSender)
    }

    @JvmStatic
    fun showPinShortcutNotSupportedDialog(context: Context): MaterialDialog {
        return MaterialDialog.Builder(context)
            .title(R.string.text_prompt)
            .content(R.string.text_pin_shortcut_not_unsupported)
            .positiveText(R.string.dialog_button_dismiss)
            .positiveColorRes(R.color.dialog_button_failure)
            .build().also { it.show() }
    }

    @JvmStatic
    @JvmOverloads
    fun getAllShortcuts(context: Context, componentName: String? = null): List<ShortcutInfo> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 -> {
            context.getSystemService<ShortcutManager>()?.let { manager ->
                manager.pinnedShortcuts.filter { shortcut ->
                    componentName == null || shortcut.intent?.component?.className == componentName
                }
            }
        }
        else -> null
    } ?: emptyList()

    @JvmStatic
    @JvmOverloads
    fun getAllShortcutScriptPaths(context: Context, componentName: String? = null): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 -> {
            getAllShortcuts(context, componentName).mapNotNull { shortcut ->
                shortcut.intent?.getStringExtra(ScriptIntents.EXTRA_KEY_PATH)
            }
        }
        else -> emptyList()
    }

}