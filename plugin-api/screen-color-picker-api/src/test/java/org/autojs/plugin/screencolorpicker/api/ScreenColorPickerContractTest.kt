package org.autojs.plugin.screencolorpicker.api

import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenColorPickerContractTest {

    @Test
    fun identityAndCompatibilityValuesAreStable() {
        assertEquals(1, ScreenColorPickerContract.VERSION)
        assertEquals("org.autojs.plugin.SCREEN_COLOR_PICKER", ScreenColorPickerContract.SERVICE_ACTION)
        assertEquals("screen-color-picker", ScreenColorPickerContract.SERVICE_CATEGORY)
        assertEquals("three-color-picker", ScreenColorPickerContract.PLUGIN_ID)
        assertEquals("screen-color-picker", ScreenColorPickerContract.ENGINE)
        assertEquals("default", ScreenColorPickerContract.VARIANT_DEFAULT)
        assertEquals(
            "io.github.supermonster003.autojs6.plugin.three.color.picker",
            ScreenColorPickerContract.DEFAULT_PACKAGE_NAME,
        )
        assertEquals(5316L, ScreenColorPickerContract.REQUIRED_HOST_VERSION_CODE)
        assertEquals(
            PluginCapabilityKeys.REQUIRES_HOST_VERSION,
            ScreenColorPickerContract.CAPABILITY_REQUIRES_HOST_VERSION,
        )
    }

    @Test
    fun runtimeStatesRemainBoundedAndSemanticallyDistinct() {
        assertEquals(setOf(0, 1, 2, 3), ScreenColorPickerStates.ALL)
        assertFalse(ScreenColorPickerStates.isOn(ScreenColorPickerStates.INACTIVE))
        assertTrue(ScreenColorPickerStates.isOn(ScreenColorPickerStates.STARTING))
        assertTrue(ScreenColorPickerStates.isOn(ScreenColorPickerStates.ACTIVE))
        assertTrue(ScreenColorPickerStates.isOn(ScreenColorPickerStates.STOPPING))
        assertTrue(ScreenColorPickerStates.isTransitioning(ScreenColorPickerStates.STARTING))
        assertTrue(ScreenColorPickerStates.isTransitioning(ScreenColorPickerStates.STOPPING))
        assertFalse(ScreenColorPickerStates.isKnown(-1))
        assertFalse(ScreenColorPickerStates.isKnown(4))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownRuntimeStateIsRejected() {
        ScreenColorPickerStates.requireKnown(Int.MAX_VALUE)
    }
}
