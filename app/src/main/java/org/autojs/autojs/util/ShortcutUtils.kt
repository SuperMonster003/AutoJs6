package org.autojs.autojs.util

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.IconCompat
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.external.shortcut.ShortcutBackupRepository
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
    @JvmOverloads
    fun requestPinShortcut(context: Context, id: String, intent: Intent, longLabel: CharSequence, shortLabel: CharSequence, icon: IconCompat?, onRequested: Runnable? = null) {
        ShortcutBackupRepository.scope.launch {
            val failure = runCatching {
                val repository = ShortcutBackupRepository.get(context)
                repository.capture(id, intent, shortLabel, longLabel, icon)
                check(repository.requestPin(id)) { context.getString(R.string.text_pin_shortcut_not_unsupported) }
            }.exceptionOrNull()
            withContext(Dispatchers.Main) {
                if (failure != null) {
                    ViewUtils.showToast(context, context.getString(R.string.shortcut_backup_error, failure.message.orEmpty()), true)
                }
                onRequested?.run()
            }
        }
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
