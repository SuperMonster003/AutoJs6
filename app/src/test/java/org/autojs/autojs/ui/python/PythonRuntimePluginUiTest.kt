package org.autojs.autojs.ui.python

import org.junit.Assert.assertEquals
import org.junit.Test

class PythonRuntimePluginUiTest {

    @Test
    fun missingPluginTakesPrecedence() {
        assertAvailability(
            PythonRuntimePluginUi.Availability.MISSING,
            installed = false,
            logicallyEnabled = true,
            androidEnabled = true,
            contractEligible = true,
        )
    }

    @Test
    fun logicalAndAndroidDisablementRemainActionable() {
        assertAvailability(
            PythonRuntimePluginUi.Availability.DISABLED,
            logicallyEnabled = false,
        )
        assertAvailability(
            PythonRuntimePluginUi.Availability.DISABLED,
            androidEnabled = false,
        )
    }

    @Test
    fun incompatiblePluginIsNotTreatedAsMissing() {
        assertAvailability(
            PythonRuntimePluginUi.Availability.INCOMPATIBLE,
            contractEligible = false,
        )
    }

    @Test
    fun eligibleEnabledPluginIsReady() {
        assertAvailability(PythonRuntimePluginUi.Availability.READY)
    }

    private fun assertAvailability(
        expected: PythonRuntimePluginUi.Availability,
        installed: Boolean = true,
        logicallyEnabled: Boolean = true,
        androidEnabled: Boolean = true,
        contractEligible: Boolean = true,
    ) {
        assertEquals(
            expected,
            PythonRuntimePluginUi.decideAvailability(
                installed = installed,
                logicallyEnabled = logicallyEnabled,
                androidEnabled = androidEnabled,
                contractEligible = contractEligible,
            ),
        )
    }
}
