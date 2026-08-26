package org.autojs.plugin.ai.provider.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderSettingsContractTest {

    @Test
    fun settingsEntryIsParameterlessAndUsesThePluginPermission() {
        assertEquals(
            "org.autojs.plugin.AI_PROVIDER_SETTINGS",
            AiProviderSettingsContract.ACTION_OPEN_SETTINGS,
        )
        assertEquals(
            AiProviderProtocol.PLUGIN_PERMISSION,
            AiProviderSettingsContract.PLUGIN_PERMISSION,
        )
        assertTrue(
            AiProviderSettingsContract::class.java.declaredFields.none { field ->
                field.name.startsWith("EXTRA_")
            },
        )
    }
}
