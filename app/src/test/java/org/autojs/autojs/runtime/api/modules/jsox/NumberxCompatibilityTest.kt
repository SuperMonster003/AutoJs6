package org.autojs.autojs.runtime.api.modules.jsox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.Undefined

class NumberxCompatibilityTest {

    @Test
    fun malformedRatioReturnsNaN() {
        assertTrue(Numberx.parseRatioRhino("3").isNaN())
        assertTrue(Numberx.parseRatioRhino("").isNaN())
    }

    @Test
    fun fractionDigitsUseToIntegerSemantics() {
        assertEquals(1.23, Numberx.toFixedNumRhino(1.234, 2.9), 0.0)
        assertEquals(1.0, Numberx.toFixedNumRhino(1.4, Undefined.instance), 0.0)
        assertEquals(1.0, Numberx.toFixedNumRhino(1.4, Double.NaN), 0.0)
    }

    @Test
    fun fractionDigitsOutsideTheEcmaRangeThrowRangeError() {
        val below = assertThrows(EcmaError::class.java) {
            Numberx.toFixedNumRhino(1, -1)
        }
        val above = assertThrows(EcmaError::class.java) {
            Numberx.toFixedNumRhino(1, 101)
        }

        assertEquals("RangeError", below.name)
        assertEquals("RangeError", above.name)
    }

}
