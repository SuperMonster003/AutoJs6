package org.autojs.autojs.core.plugin.center

import org.autojs.plugin.screencolorpicker.api.ScreenColorPickerContract
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginCenterScreenColorPickerRegistrationTest {

    @Test
    fun engineResolvesToDedicatedServiceAction() {
        assertEquals(
            ScreenColorPickerContract.SERVICE_ACTION,
            PluginCenterViewModel.serviceActionFromEngine(ScreenColorPickerContract.ENGINE),
        )
        assertEquals(
            ScreenColorPickerContract.SERVICE_ACTION,
            PluginCenterViewModel.serviceActionFromEngine(" SCREEN-COLOR-PICKER "),
        )
    }
}
