package org.autojs.autojs.core.plugin.center.wizard

import androidx.annotation.StringRes
import org.autojs.autojs6.R

/**
 * Display groups of the plugin install wizard, in the order they appear in the list.
 *
 * zh-CN: 插件安装向导的展示分组, 按列表中的出现顺序排列.
 */
enum class PluginInstallWizardCategory(@StringRes val titleRes: Int) {
    ACCESSIBILITY(R.string.text_plugin_install_wizard_category_accessibility),
    EDITOR(R.string.text_plugin_install_wizard_category_editor),
    UI(R.string.text_plugin_install_wizard_category_ui),
    APK_BUILDER(R.string.text_plugin_install_wizard_category_apk_builder),
    FILE_MANAGER(R.string.text_plugin_install_wizard_category_file_manager),
    RUNTIME(R.string.text_plugin_install_wizard_category_runtime),
    IMAGE(R.string.text_plugin_install_wizard_category_image),
    COMPILER(R.string.text_plugin_install_wizard_category_compiler),
    TEXT_CONVERSION(R.string.text_plugin_install_wizard_category_text_conversion),
    DOCS(R.string.text_plugin_install_wizard_category_docs),
    PLAYER(R.string.text_plugin_install_wizard_category_player),
    TOOLS(R.string.text_plugin_install_wizard_category_tools),

    /** Featured official index entries that the built-in catalog does not know yet. */
    OTHERS(R.string.text_others),
}
