package org.autojs.autojs.runtime.api.modules.global

import org.autojs.autojs.runtime.api.Scale
import org.autojs.autojs.runtime.api.modules.global.CoordinateScale.Axis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mozilla.javascript.Undefined

class CoordinateScaleTest {

    private fun convert(axis: Axis, vararg args: Any?, scale: Scale = Scale(), width: Int = 1080, height: Int = 1920) =
        CoordinateScale.convert(axis, width, height, scale, args)

    @Test
    fun onlyDirectAxesHaveNoArgumentDimensionForms() {
        assertEquals(1080.0, convert(Axis.X), 0.0)
        assertEquals(1920.0, convert(Axis.Y), 0.0)
        assertThrows(IllegalArgumentException::class.java) { convert(Axis.Y_FROM_X) }
        assertThrows(IllegalArgumentException::class.java) { convert(Axis.X_FROM_Y) }
    }

    @Test
    fun automaticModeUsesStrictlyBetweenMinusOneAndOne() {
        assertEquals(540.0, convert(Axis.X, 0.5), 0.0)
        assertEquals(-960.0, convert(Axis.Y, -0.5), 0.0)
        assertEquals(2.0, convert(Axis.X, 1), 0.0)
        assertEquals(-1.0, convert(Axis.Y, -1), 0.0)
        assertEquals(0.0, convert(Axis.X, 0), 0.0)
        assertEquals(150.0, convert(Axis.X, 100, 720), 0.0)
    }

    @Test
    fun explicitFalseForcesAbsoluteCoordinatesOnEveryAxis() {
        for (axis in Axis.entries) {
            assertEquals(axis.functionName, 1.0, convert(axis, 0.5, false), 0.0)
            val base = if (axis.usesWidth) 720 else 1280
            assertEquals(axis.functionName, 1.0, convert(axis, 0.5, base, false), 0.0)
        }
    }

    @Test
    fun explicitTrueForcesRatiosOutsideAutomaticRange() {
        assertEquals(2160.0, convert(Axis.X, 2, true), 0.0)
        assertEquals(3840.0, convert(Axis.Y, 2, true), 0.0)
        assertEquals(3840.0, convert(Axis.Y_FROM_X, 2, true), 0.0)
        assertEquals(2160.0, convert(Axis.X_FROM_Y, 2, true), 0.0)
        assertEquals(2160.0, convert(Axis.X, 2, 0, true), 0.0)
    }

    @Test
    fun autoAndNullishModesRemainAutomatic() {
        for (mode in listOf("auto", null, Undefined.instance)) {
            assertEquals(540.0, convert(Axis.X, 0.5, null, mode), 0.0)
            assertEquals(150.0, convert(Axis.Y_FROM_X, 100, null, mode), 0.0)
        }
        assertEquals(540.0, convert(Axis.X, "0.5", "auto"), 0.0)
        assertEquals(960.0, convert(Axis.Y_FROM_X, 0.5, "auto"), 0.0)
        assertEquals(150.0, convert(Axis.X_FROM_Y, 100, "auto"), 0.0)
    }

    @Test
    fun crossAxesIgnoreTheOtherDeviceDimension() {
        assertEquals(960.0, convert(Axis.Y_FROM_X, 0.5, "9:16", height = 2560), 0.0)
        assertEquals(540.0, convert(Axis.X_FROM_Y, 0.5, "9:16", width = 1440), 0.0)
        assertEquals(960.0, convert(Axis.Y_FROM_X, 0.5, 9.0 / 16), 0.0)
        assertEquals(540.0, convert(Axis.X_FROM_Y, 0.5, " 9 : 16 ", "auto"), 0.0)
    }

    @Test
    fun widthHeightRatiosPreserveLandscapeOrientation() {
        val scale = Scale().apply { baseX = 1280; baseY = 720 }
        assertEquals(540.0, convert(Axis.Y_FROM_X, 0.5, scale = scale, width = 1920, height = 1080), 0.0)
        assertEquals(960.0, convert(Axis.X_FROM_Y, 0.5, scale = scale, width = 1920, height = 1080), 0.0)
        assertEquals(540.0, convert(Axis.Y_FROM_X, 0.5, "16:9", width = 1920, height = 1080), 0.0)
        assertEquals(960.0, convert(Axis.X_FROM_Y, 0.5, 16.0 / 9, width = 1920, height = 1080), 0.0)
    }

    @Test
    fun changingOneBaseDoesNotBreakAbsoluteOrExplicitRatioConversions() {
        val scale = Scale().apply { baseX = 1080 }
        assertEquals(384.0, convert(Axis.Y_FROM_X, 384, scale = scale), 0.0)
        assertEquals(576.0, convert(Axis.X_FROM_Y, 384, scale = scale), 0.0)
        assertEquals(960.0, convert(Axis.Y_FROM_X, 0.5, "9:16", scale = scale), 0.0)
        assertEquals(540.0, convert(Axis.X_FROM_Y, 0.5, 9.0 / 16, scale = scale), 0.0)
        assertThrows(IllegalArgumentException::class.java) { convert(Axis.Y_FROM_X, 0.5, scale = scale) }
        assertThrows(IllegalArgumentException::class.java) { convert(Axis.X_FROM_Y, 0.5, scale = scale) }
    }

    @Test
    fun absoluteBasesMustBePositiveFiniteIntegersWithinTheScaleRange() {
        for (axis in Axis.entries) {
            for (base in listOf(0, -1, 0.5, 2147483648.0, Double.NaN, Double.POSITIVE_INFINITY)) {
                assertThrows("${axis.functionName}: $base", IllegalArgumentException::class.java) {
                    convert(axis, 100, base, false)
                }
            }
        }
    }

    @Test
    fun ratiosRejectZeroNegativeNonFiniteAndMalformedTerms() {
        val invalid = listOf(0, -1, Double.NaN, Double.POSITIVE_INFINITY, "0:9", "1:0", "-9:-16", "NaN:1", "1:Infinity", "16:9:1", "9", "foo")
        for (axis in listOf(Axis.Y_FROM_X, Axis.X_FROM_Y)) {
            for (ratio in invalid) {
                assertThrows("${axis.functionName}: $ratio", IllegalArgumentException::class.java) { convert(axis, 0.5, ratio) }
            }
        }
    }

    @Test
    fun conflictingOrInvalidModesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { convert(Axis.Y_FROM_X, 0.5, "9:16", false) }
        assertThrows(IllegalArgumentException::class.java) { convert(Axis.X, 0.5, false, true) }
        assertThrows(IllegalArgumentException::class.java) { convert(Axis.Y, 0.5, "auto", false) }
        assertThrows(IllegalArgumentException::class.java) { convert(Axis.X, 0.5, null, "false") }
        assertThrows(IllegalArgumentException::class.java) { convert(Axis.X, 1, 2, false, 4) }
    }

    @Test
    fun roundingKeepsNegativeHalfTiesAndLargeFiniteNumbers() {
        assertEquals(1.0, convert(Axis.X, 0.5, 1080, false), 0.0)
        assertEquals(0.0, convert(Axis.X, -0.5, 1080, false), 0.0)
        assertEquals(-1.0, convert(Axis.X, -1.5, 1080, false), 0.0)
        assertEquals(1e20, convert(Axis.X, 1e20, 1080, false), 0.0)
        assertEquals(-1e20, convert(Axis.Y, -1e20, 1920, false), 0.0)
    }

    @Test
    fun nonFiniteCoordinatesAndOverflowDoNotTurnIntoSaturatedLongs() {
        for (axis in Axis.entries) {
            for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
                assertThrows(IllegalArgumentException::class.java) { convert(axis, value) }
            }
            assertThrows(IllegalArgumentException::class.java) { convert(axis, Double.MAX_VALUE, true) }
        }
    }

    @Test
    fun finiteResultsSurviveIntermediateMultiplicationOverflow() {
        assertEquals(1e308, convert(Axis.X, 1e308, 1080, false), 0.0)
        assertEquals(1e308, convert(Axis.Y_FROM_X, 1e308, 1080, true), 0.0)
        assertEquals(1920.0, convert(Axis.X_FROM_Y, 1e308, 1e-308, true), 0.0)
    }
}
