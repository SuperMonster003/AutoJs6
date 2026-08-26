package org.autojs.autojs.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AiPluginSettingsPolicyTest {

    @Test
    fun availabilityUsesOneDeterministicRecoveryPath() {
        assertState(AiPluginSettingsAvailability.INSTALL_REQUIRED, installed = false)
        assertState(
            AiPluginSettingsAvailability.APPLICATION_ENABLE_REQUIRED,
            applicationEnabled = false,
        )
        assertState(AiPluginSettingsAvailability.PLUGIN_ENABLE_REQUIRED, pluginEnabled = false)
        assertState(AiPluginSettingsAvailability.UPDATE_REQUIRED, official = false)
        assertState(
            AiPluginSettingsAvailability.UPDATE_REQUIRED,
            settingsEntryAvailable = false,
        )
        assertState(AiPluginSettingsAvailability.AVAILABLE)
    }

    private fun assertState(
        expected: AiPluginSettingsAvailability,
        installed: Boolean = true,
        applicationEnabled: Boolean = true,
        pluginEnabled: Boolean = true,
        official: Boolean = true,
        settingsEntryAvailable: Boolean = true,
    ) {
        assertEquals(
            expected,
            AiPluginSettingsPolicy.availability(
                AiPluginSettingsFacts(
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
