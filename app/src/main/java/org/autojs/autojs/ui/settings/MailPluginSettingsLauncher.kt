package org.autojs.autojs.ui.settings

import android.content.Context
import org.autojs.autojs.core.plugin.mail.ThreeStampMailOfficialPlugin
import org.autojs.autojs6.R
import org.autojs.plugin.mail.api.MailActions

/**
 * Opens the official mail plugin's parameterless settings contract (its saved-accounts page,
 * `org.autojs.plugin.MAIL_SETTINGS`) or an exact recovery guide (mail plugin roadmap P4.4).
 */
internal object MailPluginSettingsLauncher {

    val target = OfficialPluginSettingsTarget(
        packageName = ThreeStampMailOfficialPlugin.PACKAGE_NAME,
        displayName = ThreeStampMailOfficialPlugin.DISPLAY_NAME,
        action = MailActions.OPEN_SETTINGS,
        permission = MailActions.PLUGIN_PERMISSION,
        title = R.string.text_mail_settings,
        installRequired = R.string.prompt_mail_plugin_install_required,
        pluginEnableRequired = R.string.prompt_mail_plugin_enable_required,
        applicationEnableRequired = R.string.prompt_mail_plugin_application_enable_required,
        settingsUnavailable = R.string.prompt_mail_plugin_settings_unavailable,
    )

    fun launch(context: Context) = OfficialPluginSettingsLauncher.launch(context, target)
}
