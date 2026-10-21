package org.autojs.autojs.core.plugin.center.wizard

import android.content.Context
import org.autojs.autojs.core.pref.keys.WizardKeys
import org.autojs.autojs.ui.wizard.WizardProfileStore

/**
 * Profiles and options of the plugin install wizard. The preference file and keys
 * match the documents written since the wizard was introduced; they are declared in
 * [WizardKeys] (docs/settings/ROADMAP.md P3.3).
 *
 * zh-CN: 插件安装向导的配置及选项. 偏好文件与键名与向导引入以来写入的文档保持一致, 声明在 [WizardKeys] (ROADMAP P3.3).
 */
object PluginInstallWizardProfileStore {

    @Suppress("UNUSED_PARAMETER")
    fun showLauncherIcons(context: Context): Boolean = WizardKeys.PLUGIN_INSTALL_WIZARD_SHOW_LAUNCHER_ICONS.get()

    @Suppress("UNUSED_PARAMETER")
    fun setShowLauncherIcons(context: Context, show: Boolean) {
        WizardKeys.PLUGIN_INSTALL_WIZARD_SHOW_LAUNCHER_ICONS.set(show)
    }

    val store = WizardProfileStore(
        keys = WizardKeys.PLUGIN_INSTALL_WIZARD,
        membersKey = "packages",
    )

}
