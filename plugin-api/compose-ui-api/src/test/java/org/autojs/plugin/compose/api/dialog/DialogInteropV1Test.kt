package org.autojs.plugin.compose.api.dialog

import android.view.WindowManager
import org.autojs.plugin.compose.api.ComposeUiContract
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.catalog.ComponentCatalog
import org.autojs.plugin.compose.api.interop.AndroidViewInteropV1
import org.junit.Assert.*
import org.junit.Test

class DialogInteropV1Test {
    @Test fun presentationNegotiationDoesNotChangeEitherFrozenCatalog() {
        assertTrue(ComposeUiContract.supports(1))
        assertEquals(30, ComponentCatalog.V1.components.size)
        assertEquals(31, AndroidViewInteropV1.catalog.components.size)
        assertEquals("dialog-v1", DialogInteropV1.FEATURE)
        assertEquals("dialogFactoryV1", DialogInteropV1.FACTORY_CLASS_KEY)
        assertEquals("dialogDismissRequest", DialogInteropV1.DISMISS_REQUEST)
        val defaults = DialogOptionsV1()
        assertEquals(DialogInteropV1.ALERT, defaults.type)
        assertTrue(defaults.cancelable)
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION, defaults.windowType)
        assertNull(defaults.windowToken)
    }

    @Test fun acceptsOnlyTheTwoPresentationsAndThreeHostSelectedWindowTypes() {
        @Suppress("DEPRECATION")
        val types = listOf(WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        for (type in listOf(DialogInteropV1.ALERT, DialogInteropV1.BOTTOM_SHEET)) {
            for (window in types) {
                val options = DialogOptionsV1(type, false, window)
                assertEquals(type, options.type)
                assertFalse(options.cancelable)
                assertEquals(window, options.windowType)
            }
        }
        for (type in listOf("", "Alert", "bottomsheet", "popup")) failure("type") { DialogOptionsV1(type) }
        for (type in listOf(-1, 0, WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.TYPE_TOAST)) failure("windowType") { DialogOptionsV1(windowType = type) }
    }

    private fun failure(prop: String, action: () -> Unit) {
        try { action(); fail("Expected invalid $prop") }
        catch (error: ComposeUiContractException) {
            assertEquals(ComposeUiErrorCodes.INVALID_ARGUMENT, error.code)
            assertEquals(prop, error.prop)
            assertTrue(error.message!!.contains("dialog.$prop"))
        }
    }
}
