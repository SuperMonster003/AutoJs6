package org.autojs.autojs.runtime.api.modules.jsox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor

class MathxCompatibilityTest {

    @Test
    fun singleBoundRandomIntUsesZeroAsTheOtherBound() {
        val positive = List(100) { Mathx.randomIntRhino(5) }
        val negative = List(100) { Mathx.randomIntRhino(-5) }

        assertTrue(positive.all { it in 0.0..5.0 && it == floor(it) })
        assertTrue(positive.any { it < 5.0 })
        assertTrue(negative.all { it in -5.0..0.0 && it == floor(it) })
        assertTrue(negative.any { it > -5.0 })
    }

    @Test
    fun zeroBoundRandomFloatIsZero() {
        assertEquals(0.0, Mathx.randomFloatRhino(0), 0.0)
    }

    @Test
    fun coefficientOfVariationUsesSampleStandardDeviation() {
        assertEquals(0.5, Mathx.cvRhino(1, 2, 3), 1e-12)
        assertTrue(Mathx.cvRhino(1).isNaN())
    }
}
