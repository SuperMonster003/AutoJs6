package org.autojs.autojs.inrt

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs6.R

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 9, 2026.
 */
object InrtShortcuts {

    private const val ID_LOG = "id_inrt_launcher_shortcut_log"
    private const val ID_SETTINGS = "id_inrt_launcher_shortcut_settings"

    @JvmStatic
    fun syncToExplicitIntents() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return
        }
        val context = GlobalAppContext.get()
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
        val packageName = context.packageName

        val shortcuts = listOf(
            ShortcutInfo.Builder(context, ID_LOG)
                .setShortLabel(context.getString(R.string.text_app_shortcut_log_short_label))
                .setLongLabel(context.getString(R.string.text_app_shortcut_log_short_label))
                .setIntent(
                    Intent(context, LogActivity::class.java)
                        .setAction("$packageName.action.INRT_SHORTCUT_LOG")
                        .setClass(context, LogActivity::class.java)
                        .setPackage(packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                .build(),
            ShortcutInfo.Builder(context, ID_SETTINGS)
                .setShortLabel(context.getString(R.string.text_app_shortcut_settings_short_label))
                .setLongLabel(context.getString(R.string.text_app_shortcut_settings_short_label))
                .setIntent(
                    Intent(context, SettingsActivity::class.java)
                        .setAction("$packageName.action.INRT_SHORTCUT_SETTINGS")
                        .setClass(context, SettingsActivity::class.java)
                        .setPackage(packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                .build(),
        )

        runCatching {
            if (!shortcutManager.setDynamicShortcuts(shortcuts)) {
                shortcutManager.removeDynamicShortcuts(listOf(ID_LOG, ID_SETTINGS))
                shortcutManager.addDynamicShortcuts(shortcuts)
            }
        }
    }
}
