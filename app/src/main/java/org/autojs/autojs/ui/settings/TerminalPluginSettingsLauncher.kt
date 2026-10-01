package org.autojs.autojs.ui.settings

import android.content.Context
import org.autojs.autojs.core.plugin.terminal.ThreeShellTerminalOfficialPlugin
import org.autojs.autojs6.R
import org.autojs.plugin.terminal.api.TerminalActions

/**
 * Opens the official terminal plugin's parameterless settings contract (`org.autojs.plugin.TERMINAL_SETTINGS`,
 * plugin roadmap D29) or an exact recovery guide; the Plugin Center "settings" button routes here.
 */
internal object TerminalPluginSettingsLauncher {

    val target = OfficialPluginSettingsTarget(
        packageName = ThreeShellTerminalOfficialPlugin.PACKAGE_NAME,
        displayName = ThreeShellTerminalOfficialPlugin.DISPLAY_NAME,
        action = TerminalActions.OPEN_SETTINGS,
        permission = TerminalActions.PLUGIN_PERMISSION,
        title = R.string.text_terminal,
        installRequired = R.string.prompt_terminal_plugin_install_required,
        pluginEnableRequired = R.string.prompt_terminal_plugin_enable_required,
        applicationEnableRequired = R.string.prompt_terminal_plugin_application_disabled,
        settingsUnavailable = R.string.prompt_terminal_plugin_update_required,
    )

    fun launch(context: Context) = OfficialPluginSettingsLauncher.launch(context, target)
}
