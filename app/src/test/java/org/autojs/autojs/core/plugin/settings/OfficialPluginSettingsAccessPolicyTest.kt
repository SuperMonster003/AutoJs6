package org.autojs.autojs.core.plugin.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.autojs.plugin.common.api.AutoJs6HostSettingsContract

class OfficialPluginSettingsAccessPolicyTest {
    @Test
    fun `enabled official plugin with protected entry is allowed`() {
        assertNull(OfficialPluginSettingsAccessPolicy.denial(allowedFacts()))
    }

    @Test
    fun `calling package must belong to Binder uid`() {
        assertEquals(
            OfficialPluginSettingsAccessDenial.UID_PACKAGE_MISMATCH,
            OfficialPluginSettingsAccessPolicy.denial(
                allowedFacts().copy(packagesForUid = setOf("another.package")),
            ),
        )
    }

    @Test
    fun `framework must report a calling package`() {
        assertEquals(
            OfficialPluginSettingsAccessDenial.MISSING_CALLING_PACKAGE,
            OfficialPluginSettingsAccessPolicy.denial(
                allowedFacts().copy(callingPackage = null),
            ),
        )
    }

    @Test
    fun `disabled package and missing protected entry are rejected`() {
        assertEquals(
            OfficialPluginSettingsAccessDenial.APPLICATION_DISABLED,
            OfficialPluginSettingsAccessPolicy.denial(
                allowedFacts().copy(applicationEnabled = false),
            ),
        )
        assertEquals(
            OfficialPluginSettingsAccessDenial.PLUGIN_ENTRY_UNAVAILABLE,
            OfficialPluginSettingsAccessPolicy.denial(
                allowedFacts().copy(protectedPluginEntryEnabled = false),
            ),
        )
    }

    @Test
    fun `non-official signer is rejected even when other checks pass`() {
        assertEquals(
            OfficialPluginSettingsAccessDenial.NOT_OFFICIAL,
            OfficialPluginSettingsAccessPolicy.denial(
                allowedFacts().copy(officialSignature = false),
            ),
        )
    }

    @Test
    fun `public protocol one identifiers remain stable`() {
        assertEquals("org.autojs.autojs6", AutoJs6HostSettingsContract.HOST_PACKAGE_NAME)
        assertEquals(
            "content://org.autojs.autojs6.plugin.settings",
            AutoJs6HostSettingsContract.CONTENT_URI,
        )
        assertEquals(1, AutoJs6HostSettingsContract.PROTOCOL_VERSION)
        assertEquals("getSettings", AutoJs6HostSettingsContract.METHOD_GET_SETTINGS)
    }

    private fun allowedFacts() = OfficialPluginSettingsCallerFacts(
        callingPackage = PLUGIN_PACKAGE,
        packagesForUid = setOf(PLUGIN_PACKAGE),
        applicationEnabled = true,
        protectedPluginEntryEnabled = true,
        officialSignature = true,
    )

    private companion object {
        const val PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.threestoneai"
    }
}
