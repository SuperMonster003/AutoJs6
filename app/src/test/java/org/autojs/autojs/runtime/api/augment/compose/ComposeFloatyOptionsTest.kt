package org.autojs.autojs.runtime.api.augment.compose

import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.mozilla.javascript.Context
import org.mozilla.javascript.Undefined
import org.junit.Assert.*
import org.junit.Test

class ComposeFloatyOptionsTest {
    @Test fun omittedOptionsKeepResizableTouchableUnfocusedDefaultsAndNativeGeometry() {
        for (raw in listOf(null, Undefined.instance, emptyMap<String, Any>())) {
            assertEquals(ComposeFloatyOptions(), ComposeFloatyOptions.parse(raw))
        }
        assertEquals(ComposeFloatyOptions(), ComposeFloatyOptions.parse(mapOf("theme" to mapOf("dark" to true))))
    }

    @Test fun geometryUsesExactPixelsAndLegacySizeSentinelsWithoutDensityConversion() {
        assertEquals(ComposeFloatyOptions(true, -20, 37, 301, 203, false, true), ComposeFloatyOptions.parse(mapOf(
            "raw" to true, "x" to -20, "y" to 37.0, "width" to 301, "height" to 203L, "touchable" to false, "focusable" to true)))
        assertEquals(ComposeFloatyOptions(x = Int.MIN_VALUE, y = Int.MAX_VALUE, width = -1, height = -2), ComposeFloatyOptions.parse(mapOf(
            "x" to Int.MIN_VALUE, "y" to Int.MAX_VALUE, "width" to -1, "height" to -2)))
        assertEquals(0, ComposeFloatyOptions.parse(mapOf("width" to 0)).width)
        assertNull(ComposeFloatyOptions.parse(mapOf("height" to 4)).width)
    }

    @Test fun suppliedValuesNeverCoerceStringsNullsFractionsOrUnknownOptions() {
        for (name in listOf("raw", "touchable", "focusable")) {
            for (value in listOf(null, 0, "false", Undefined.instance)) failure(name) { ComposeFloatyOptions.parse(mapOf(name to value)) }
        }
        for (name in listOf("x", "y", "width", "height")) {
            for (value in listOf(null, "12", "12dp", true, 1.5, Double.NaN, Double.POSITIVE_INFINITY, Int.MAX_VALUE.toLong() + 1)) {
                failure(name) { ComposeFloatyOptions.parse(mapOf(name to value)) }
            }
        }
        for (name in listOf("width", "height")) failure(name) { ComposeFloatyOptions.parse(mapOf(name to -3)) }
        failure("modal", ComposeUiErrorCodes.UNKNOWN_PROP) { ComposeFloatyOptions.parse(mapOf("modal" to true)) }
    }

    @Test fun rhinoOptionObjectsAreReadThroughTheSameStrictParser() {
        val context = Context.enter().apply { languageVersion = Context.VERSION_ES6; isInterpretedMode = true }
        try {
            val scope = TopLevelScope().apply { initStandardObjects(context, false) }
            val raw = context.evaluateString(scope, "({raw:true,x:15,y:-7,width:240,height:120,touchable:false,focusable:true})", "floaty-options", 1, null)
            assertEquals(ComposeFloatyOptions(true, 15, -7, 240, 120, false, true), ComposeFloatyOptions.parse(raw))
            val invalid = context.evaluateString(scope, "({width:'240'})", "floaty-options", 1, null)
            failure("width") { ComposeFloatyOptions.parse(invalid) }
        } finally { Context.exit() }
    }

    private fun failure(property: String, code: String = ComposeUiErrorCodes.INVALID_ARGUMENT, action: () -> Unit) {
        try { action(); fail("Expected $code for $property") }
        catch (failure: ComposeUiContractException) {
            assertEquals(code, failure.code); assertEquals(property, failure.prop)
            assertTrue(failure.message!!.contains("compose.floaty.$property"))
        }
    }
}
