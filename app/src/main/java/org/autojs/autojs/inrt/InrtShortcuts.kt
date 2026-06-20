package org.autojs.autojs.inrt

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.project.ProjectConfig
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
        val launchConfig = ProjectConfig.fromAssets(context, ProjectConfig.configFileOfDir("project"))?.launchConfig

        val allShortcutIds = listOf(ID_LOG, ID_SETTINGS)
        val enabledShortcutIds = mutableListOf<String>()
        val shortcuts = mutableListOf<ShortcutInfo>()

        if (launchConfig?.isLogShortcutVisible == true) {
            enabledShortcutIds += ID_LOG
            shortcuts += ShortcutInfo.Builder(context, ID_LOG)
                .setShortLabel(context.getString(R.string.text_app_shortcut_log_short_label))
                .setLongLabel(context.getString(R.string.text_app_shortcut_log_short_label))
                .setIntent(
                    Intent(context, LogActivity::class.java)
                        .setAction("$packageName.action.INRT_SHORTCUT_LOG")
                        .setClass(context, LogActivity::class.java)
                        .setPackage(packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                .build()
        }

        if (launchConfig?.isSettingsShortcutVisible == true) {
            enabledShortcutIds += ID_SETTINGS
            shortcuts += ShortcutInfo.Builder(context, ID_SETTINGS)
                .setShortLabel(context.getString(R.string.text_app_shortcut_settings_short_label))
                .setLongLabel(context.getString(R.string.text_app_shortcut_settings_short_label))
                .setIntent(
                    Intent(context, SettingsActivity::class.java)
                        .setAction("$packageName.action.INRT_SHORTCUT_SETTINGS")
                        .setClass(context, SettingsActivity::class.java)
                        .setPackage(packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                .build()
        }

        val disabledShortcutIds = allShortcutIds - enabledShortcutIds.toSet()

        runCatching {
            if (disabledShortcutIds.isNotEmpty()) {
                shortcutManager.removeDynamicShortcuts(disabledShortcutIds)
            }
            if (shortcuts.isEmpty()) {
                return@runCatching
            }
            if (!shortcutManager.setDynamicShortcuts(shortcuts)) {
                shortcutManager.removeDynamicShortcuts(allShortcutIds)
                shortcutManager.addDynamicShortcuts(shortcuts)
            }
        }
    }
}
