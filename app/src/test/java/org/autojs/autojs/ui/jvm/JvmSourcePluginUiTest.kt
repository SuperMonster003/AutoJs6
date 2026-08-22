package org.autojs.autojs.ui.jvm

import org.junit.Assert.assertEquals
import org.junit.Test

class JvmSourcePluginUiTest {

    @Test
    fun noServiceCandidateIsMissing() {
        assertEquals(
            JvmSourcePluginUi.Availability.MISSING,
            JvmSourcePluginUi.decideAvailability(0, 0),
        )
    }

    @Test
    fun installedButDisabledCandidateIsDisabled() {
        assertEquals(
            JvmSourcePluginUi.Availability.DISABLED,
            JvmSourcePluginUi.decideAvailability(1, 0),
        )
    }

    @Test
    fun anyEnabledCandidateAllowsRuntimeAdmissionToDiagnoseTheRest() {
        assertEquals(
            JvmSourcePluginUi.Availability.READY,
            JvmSourcePluginUi.decideAvailability(2, 1),
        )
    }
}
