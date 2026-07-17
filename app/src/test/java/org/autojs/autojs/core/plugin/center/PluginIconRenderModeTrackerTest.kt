package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginIconRenderModeTrackerTest {

    @Test
    fun initialAndChangedModesForceRender() {
        val tracker = PluginIconRenderModeTracker()

        assertTrue(tracker.shouldForceRender(nightModeEnabled = false))
        tracker.markRendered(nightModeEnabled = false)
        assertFalse(tracker.shouldForceRender(nightModeEnabled = false))
        assertTrue(tracker.shouldForceRender(nightModeEnabled = true))
    }

    @Test
    fun modeIsNotAdvancedUntilItemsAreRendered() {
        val tracker = PluginIconRenderModeTracker()
        tracker.markRendered(nightModeEnabled = false)

        assertTrue(tracker.shouldForceRender(nightModeEnabled = true))
        assertTrue(tracker.shouldForceRender(nightModeEnabled = true))
        assertFalse(tracker.shouldForceRender(nightModeEnabled = false))

        tracker.markRendered(nightModeEnabled = true)
        assertFalse(tracker.shouldForceRender(nightModeEnabled = true))
        assertTrue(tracker.shouldForceRender(nightModeEnabled = false))
    }
}
