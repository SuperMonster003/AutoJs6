package org.autojs.autojs.core.plugin.center.wizard

import org.autojs.autojs.ui.wizard.WizardProfileStore

/**
 * Profiles and options of the plugin install wizard. The preference file and keys
 * match the documents written since the wizard was introduced.
 *
 * zh-CN: 插件安装向导的配置及选项. 偏好文件与键名与向导引入以来写入的文档保持一致.
 */
object PluginInstallWizardProfileStore {

    val store = WizardProfileStore(
        spName = "plugin_install_wizard",
        keyPrefix = "key_\$_plugin_install_wizard",
        membersKey = "packages",
    )

}
