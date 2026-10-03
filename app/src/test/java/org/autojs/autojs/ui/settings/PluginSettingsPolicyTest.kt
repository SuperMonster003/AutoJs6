package org.autojs.autojs.ui.settings

import org.autojs.plugin.ai.provider.api.AiProviderSettingsContract
import org.autojs.plugin.mail.api.MailActions
import org.autojs.plugin.mail.api.MailIds
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginSettingsPolicyTest {

    @Test
    fun availabilityUsesOneDeterministicRecoveryPath() {
        assertState(PluginSettingsAvailability.INSTALL_REQUIRED, installed = false)
        assertState(
            PluginSettingsAvailability.APPLICATION_ENABLE_REQUIRED,
            applicationEnabled = false,
        )
        assertState(PluginSettingsAvailability.PLUGIN_ENABLE_REQUIRED, pluginEnabled = false)
        assertState(PluginSettingsAvailability.UPDATE_REQUIRED, official = false)
        assertState(
            PluginSettingsAvailability.UPDATE_REQUIRED,
            settingsEntryAvailable = false,
        )
        assertState(PluginSettingsAvailability.AVAILABLE)
    }

    @Test
    fun officialTargetsFollowTheirPluginContracts() {
        val ai = AiPluginSettingsLauncher.target
        assertEquals(AiProviderSettingsContract.ACTION_OPEN_SETTINGS, ai.action)
        assertEquals(AiProviderSettingsContract.PLUGIN_PERMISSION, ai.permission)

        val mail = MailPluginSettingsLauncher.target
        assertEquals(MailIds.DEFAULT_PACKAGE_NAME, mail.packageName)
        assertEquals("org.autojs.plugin.MAIL_SETTINGS", MailActions.OPEN_SETTINGS)
        assertEquals(MailActions.OPEN_SETTINGS, mail.action)
        assertEquals(MailActions.PLUGIN_PERMISSION, mail.permission)
        assertEquals("3-Stamp Mail", mail.displayName)
    }

    private fun assertState(
        expected: PluginSettingsAvailability,
        installed: Boolean = true,
        applicationEnabled: Boolean = true,
        pluginEnabled: Boolean = true,
        official: Boolean = true,
        settingsEntryAvailable: Boolean = true,
    ) {
        assertEquals(
            expected,
            PluginSettingsPolicy.availability(
                PluginSettingsFacts(
                    installed = installed,
                    applicationEnabled = applicationEnabled,
                    pluginEnabled = pluginEnabled,
                    official = official,
                    settingsEntryAvailable = settingsEntryAvailable,
                ),
            ),
        )
    }
}
