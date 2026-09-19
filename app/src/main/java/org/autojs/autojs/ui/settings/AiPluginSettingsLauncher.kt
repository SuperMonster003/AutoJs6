package org.autojs.autojs.ui.settings

import android.content.Context
import org.autojs.autojs.core.plugin.ai.ThreeStoneAiOfficialPlugin
import org.autojs.autojs6.R
import org.autojs.plugin.ai.provider.api.AiProviderSettingsContract

/** Opens the official AI plugin's parameterless settings contract or an exact recovery guide. */
internal object AiPluginSettingsLauncher {

    val target = OfficialPluginSettingsTarget(
        packageName = ThreeStoneAiOfficialPlugin.PACKAGE_NAME,
        displayName = ThreeStoneAiOfficialPlugin.DISPLAY_NAME,
        action = AiProviderSettingsContract.ACTION_OPEN_SETTINGS,
        permission = AiProviderSettingsContract.PLUGIN_PERMISSION,
        title = R.string.text_ai_settings,
        installRequired = R.string.prompt_ai_plugin_install_required,
        pluginEnableRequired = R.string.prompt_ai_plugin_enable_required,
        applicationEnableRequired = R.string.prompt_ai_plugin_application_enable_required,
        settingsUnavailable = R.string.prompt_ai_plugin_settings_unavailable,
    )

    fun launch(context: Context) = OfficialPluginSettingsLauncher.launch(context, target)
}
