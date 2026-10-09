package org.autojs.autojs.core.plugin.center.wizard

import android.content.Context
import androidx.core.content.edit
import org.autojs.autojs.ui.wizard.WizardProfileStore

/**
 * Profiles and options of the plugin install wizard. The preference file and keys
 * match the documents written since the wizard was introduced.
 *
 * zh-CN: 插件安装向导的配置及选项. 偏好文件与键名与向导引入以来写入的文档保持一致.
 */
object PluginInstallWizardProfileStore {

    private const val SHOW_LAUNCHER_ICONS = "show_launcher_icons_for_new_plugins"

    fun showLauncherIcons(context: Context): Boolean =
        context.applicationContext.getSharedPreferences("plugin_install_wizard", Context.MODE_PRIVATE)
            .getBoolean(SHOW_LAUNCHER_ICONS, true)

    fun setShowLauncherIcons(context: Context, show: Boolean) {
        context.applicationContext.getSharedPreferences("plugin_install_wizard", Context.MODE_PRIVATE)
            .edit { putBoolean(SHOW_LAUNCHER_ICONS, show) }
    }

    val store = WizardProfileStore(
        spName = "plugin_install_wizard",
        keyPrefix = "key_\$_plugin_install_wizard",
        membersKey = "packages",
    )

}
