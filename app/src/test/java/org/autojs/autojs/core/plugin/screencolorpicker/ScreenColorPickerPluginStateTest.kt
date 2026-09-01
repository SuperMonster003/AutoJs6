package org.autojs.autojs.core.plugin.screencolorpicker

import org.autojs.plugin.screencolorpicker.api.ScreenColorPickerContract
import org.autojs.plugin.screencolorpicker.api.ScreenColorPickerStates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenColorPickerPluginStateTest {

    @Test
    fun availableRuntimeStatesMapToSwitchAndProgressWithoutStartingEnabled() {
        val inactive = available(ScreenColorPickerStates.INACTIVE)
        assertTrue(inactive.installed)
        assertTrue(inactive.enabledInPluginCenter)
        assertTrue(inactive.visibleInDrawer)
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
            val state = ScreenColorPickerPluginStateMonitor.State(
                status = status,
                enabledInPluginCenter = true,
            )
            assertFalse(state.installed)
            assertFalse(state.visibleInDrawer)
            assertFalse(state.available)
            assertFalse(state.active)
            assertFalse(state.transitioning)
        }

        val disabled = ScreenColorPickerPluginStateMonitor.State(
            status = ScreenColorPickerPluginStateMonitor.Status.DISABLED,
            enabledInPluginCenter = false,
        )
        assertTrue(disabled.installed)
        assertFalse(disabled.enabledInPluginCenter)
        assertFalse(disabled.visibleInDrawer)
        assertFalse(disabled.available)
        assertFalse(disabled.active)
        assertFalse(disabled.transitioning)

        val enabledButIneligible = ScreenColorPickerPluginStateMonitor.State(
            status = ScreenColorPickerPluginStateMonitor.Status.INELIGIBLE,
            enabledInPluginCenter = true,
            runtimeState = ScreenColorPickerStates.ACTIVE,
        )
        assertTrue(enabledButIneligible.installed)
        assertTrue(enabledButIneligible.enabledInPluginCenter)
        assertTrue(enabledButIneligible.visibleInDrawer)
        assertFalse(enabledButIneligible.available)
        assertFalse(enabledButIneligible.active)
        assertFalse(enabledButIneligible.transitioning)
    }

    @Test
    fun pluginCenterStateChangesHideImmediatelyAndRestoreAfterInspection() {
        val installedButDisabled = ScreenColorPickerPluginStateMonitor.State(
            status = ScreenColorPickerPluginStateMonitor.Status.DISABLED,
            enabledInPluginCenter = false,
        )

        val reenabled = installedButDisabled.withPluginCenterEnabled(true)
        assertTrue(reenabled.installed)
        assertTrue(reenabled.enabledInPluginCenter)
        assertFalse(reenabled.visibleInDrawer)

        val confirmedReenabled = ScreenColorPickerPluginStateMonitor.State(
            status = ScreenColorPickerPluginStateMonitor.Status.AVAILABLE,
            enabledInPluginCenter = true,
        )
        assertTrue(confirmedReenabled.visibleInDrawer)

        val disabledAgain = available(ScreenColorPickerStates.ACTIVE)
            .withPluginCenterEnabled(false)
        assertEquals(ScreenColorPickerPluginStateMonitor.Status.DISABLED, disabledAgain.status)
        assertFalse(disabledAgain.enabledInPluginCenter)
        assertFalse(disabledAgain.visibleInDrawer)
        assertFalse(disabledAgain.active)

        listOf(
            ScreenColorPickerPluginStateMonitor.Status.LOADING,
            ScreenColorPickerPluginStateMonitor.Status.NOT_INSTALLED,
        ).forEach { originalStatus ->
            val disabledWhileAbsent = ScreenColorPickerPluginStateMonitor.State(
                status = originalStatus,
                enabledInPluginCenter = true,
            ).withPluginCenterEnabled(false)
            assertEquals(originalStatus, disabledWhileAbsent.status)
            assertFalse(disabledWhileAbsent.installed)
            assertFalse(disabledWhileAbsent.visibleInDrawer)
        }
    }

    @Test
    fun pluginCenterStateChangesRefreshOnlyTheOfficialPlugin() {
        assertTrue(
            ScreenColorPickerPluginStateChangePolicy.shouldRefresh(
                ScreenColorPickerContract.DEFAULT_PACKAGE_NAME,
            ),
        )
        assertFalse(
            ScreenColorPickerPluginStateChangePolicy.shouldRefresh(
                "example.unrelated.plugin",
            ),
        )
    }

    private fun available(runtimeState: Int) = ScreenColorPickerPluginStateMonitor.State(
        status = ScreenColorPickerPluginStateMonitor.Status.AVAILABLE,
        enabledInPluginCenter = true,
        runtimeState = runtimeState,
    )
}
