package org.autojs.autojs.core.plugin.screencolorpicker

import org.autojs.plugin.screencolorpicker.api.ScreenColorPickerStates
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenColorPickerPluginStateTest {

    @Test
    fun availableRuntimeStatesMapToSwitchAndProgressWithoutStartingEnabled() {
        val inactive = available(ScreenColorPickerStates.INACTIVE)
        assertTrue(inactive.installed)
        assertTrue(inactive.available)
        assertFalse(inactive.active)
        assertFalse(inactive.transitioning)

        val starting = available(ScreenColorPickerStates.STARTING)
        assertTrue(starting.active)
        assertTrue(starting.transitioning)

        val active = available(ScreenColorPickerStates.ACTIVE)
        assertTrue(active.active)
        assertFalse(active.transitioning)

        val stopping = available(ScreenColorPickerStates.STOPPING)
        assertTrue(stopping.active)
        assertTrue(stopping.transitioning)
    }

    @Test
    fun installAndEligibilityStatesControlDrawerVisibilityAndAvailability() {
        listOf(
            ScreenColorPickerPluginStateMonitor.Status.LOADING,
            ScreenColorPickerPluginStateMonitor.Status.NOT_INSTALLED,
        ).forEach { status ->
            val state = ScreenColorPickerPluginStateMonitor.State(status)
            assertFalse(state.installed)
            assertFalse(state.available)
            assertFalse(state.active)
            assertFalse(state.transitioning)
        }

        listOf(
            ScreenColorPickerPluginStateMonitor.Status.DISABLED,
            ScreenColorPickerPluginStateMonitor.Status.INELIGIBLE,
        ).forEach { status ->
            val state = ScreenColorPickerPluginStateMonitor.State(
                status = status,
                runtimeState = ScreenColorPickerStates.ACTIVE,
            )
            assertTrue(state.installed)
            assertFalse(state.available)
            assertFalse(state.active)
            assertFalse(state.transitioning)
        }
    }

    private fun available(runtimeState: Int) = ScreenColorPickerPluginStateMonitor.State(
        status = ScreenColorPickerPluginStateMonitor.Status.AVAILABLE,
        runtimeState = runtimeState,
    )
}
