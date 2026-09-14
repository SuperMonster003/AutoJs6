package org.autojs.autojs.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class ColorDetectorTest {

    private val red = 0xFFFF0000.toInt()
    private val green = 0xFF00FF00.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val gray = 0xFF808080.toInt()

    @Test
    fun hueMatchesTheStandardFormula() {
        assertEquals(0.0, ColorDetector.getHueFromRGB(255, 0, 0), 1e-9)
        assertEquals(120.0, ColorDetector.getHueFromRGB(0, 255, 0), 1e-9)
        assertEquals(240.0, ColorDetector.getHueFromRGB(0, 0, 255), 1e-9)
        assertEquals(60.0, ColorDetector.getHueFromRGB(255, 255, 0), 1e-9)
        assertEquals(300.0, ColorDetector.getHueFromRGB(255, 0, 255), 1e-9)
        /* Achromatic colors have no hue. */
        assertEquals(0.0, ColorDetector.getHueFromRGB(0, 0, 0), 1e-9)
        assertEquals(0.0, ColorDetector.getHueFromRGB(128, 128, 128), 1e-9)
    }

    @Test
    fun differenceDetectorUsesMeanAbsoluteDifference() {
        val detector = ColorDetector.DifferenceDetector(0xFF102030.toInt(), 4)
        assertEquals(0.0, detector.distance(0x10, 0x20, 0x30), 1e-9)
        assertEquals(4.0, detector.distance(0x10 + 12, 0x20, 0x30), 1e-9)
        assertTrue(detector.detectColor(0x10 + 12, 0x20, 0x30))
        assertFalse(detector.detectColor(0x10 + 13, 0x20, 0x30))
    }

    @Test
    fun rgbDetectorsUseEuclideanDistanceOverThree() {
        val plain = ColorDetector.RGBDistanceDetector(black, 0)
        assertEquals(sqrt(3.0 * 255 * 255) / 3, plain.distance(255, 255, 255), 1e-9)
        assertTrue(plain.detectColor(0, 0, 0))
        assertFalse(plain.detectColor(1, 0, 0))

        val weighted = ColorDetector.WeightedRGBDistanceDetector(black, 0)
        assertEquals(0.0, weighted.distance(0, 0, 0), 1e-9)
        assertTrue(weighted.distance(0, 10, 0) > weighted.distance(10, 0, 0))
    }

    @Test
    fun hueDetectorMeasuresCircularDistance() {
        val detector = ColorDetector.HDistanceDetector(red, 10)
        /* Hue 350 is only 10 degrees away from hue 0 on the color circle. */
        assertEquals(10.0 / 180.0 * 255, detector.distance(255, 0, 42), 0.5)
        assertEquals(255.0 * 120 / 180, detector.distance(0, 255, 0), 1e-9)
        assertTrue(detector.detectColor(255, 0, 0))
        assertFalse(detector.detectColor(0, 255, 0))
    }

    @Test
    fun hueSaturationDetectorSeparatesDistinctColors() {
        val detector = ColorDetector.HSDistanceDetector(red, 4)
        assertTrue(detector.detectColor(255, 0, 0))
        /* Darker red keeps hue and saturation. */
        assertTrue(detector.detectColor(128, 0, 0))
        assertFalse(detector.detectColor(0, 255, 0))
        assertFalse(detector.detectColor(0, 0, 255))
        assertFalse(detector.detectColor(255, 255, 255))
        /* Cyan is the opposite hue with the same saturation: dh = 1, ds = 0. */
        assertEquals(sqrt(0.5) * 255, detector.distance(0, 255, 255), 1e-9)
        /* Distances stay within the 0..255 threshold scale. */
        assertTrue(detector.distance(255, 255, 255) <= 255.0)
    }

    @Test
    fun hueSaturationDetectorHandlesBlackWithoutDivisionByZero() {
        val detector = ColorDetector.HSDistanceDetector(black, 4)
        assertTrue(detector.detectColor(0, 0, 0))
        assertTrue(detector.detectColor(128, 128, 128))
        assertFalse(detector.detectColor(255, 0, 0))
        assertEquals(0.0, ColorDetector.HSDistanceDetector(white, 0).distance(0, 0, 0), 1e-9)
    }

    @Test
    fun hueSaturationDetectorAcceptsSimilarityConstructor() {
        val detector = ColorDetector.HSDistanceDetector(red, 0.9f)
        assertTrue(detector.detectColor(250, 10, 5))
        assertFalse(detector.detectColor(0, 0, 255))
    }

    @Test
    fun equalityDetectorDistanceIsZeroOnlyForEqualColors() {
        val detector = ColorDetector.EqualityDetector(gray)
        assertEquals(0.0, detector.distance(0x80, 0x80, 0x80), 1e-9)
        assertEquals(1.0, detector.distance(0x81, 0x80, 0x80), 1e-9)
        assertTrue(detector.detectColor(0x80, 0x80, 0x80))
        assertFalse(detector.detectColor(0x80, 0x80, 0x7F))
    }

    @Test
    fun detectColorIsConsistentWithDistanceForEveryAlgorithm() {
        val probes = listOf(Triple(255, 0, 0), Triple(0, 255, 0), Triple(0, 0, 255), Triple(200, 30, 30), Triple(10, 10, 10))
        for (algorithm in listOf("diff", "rgb", "rgb+", "hs", "h")) {
            for (threshold in listOf(0, 4, 30, 255)) {
                val detector = ColorDetector.get(red, algorithm, threshold)
                for ((r, g, b) in probes) {
                    assertEquals("$algorithm/$threshold/($r, $g, $b)", detector.distance(r, g, b) <= threshold, detector.detectColor(r, g, b))
                }
            }
        }
    }

    @Test
    fun factoryNormalizesAlgorithmNames() {
        assertTrue(ColorDetector.get(red, " DIFF ", 4) is ColorDetector.DifferenceDetector)
        assertTrue(ColorDetector.get(red, "Rgb+", 4) is ColorDetector.WeightedRGBDistanceDetector)
        assertTrue(ColorDetector.get(red, "equal", 4) is ColorDetector.EqualityDetector)
        assertTrue(runCatching { ColorDetector.get(red, "unknown", 4) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun greenAndBlueDoNotDependOnAlpha() {
        val opaque = ColorDetector.DifferenceDetector(green, 0)
        val translucent = ColorDetector.DifferenceDetector(green and 0x00FFFFFF, 0)
        assertTrue(opaque.detectColor(0, 255, 0))
        assertTrue(translucent.detectColor(0, 255, 0))
        assertTrue(ColorDetector.DifferenceDetector(blue, 0).detectColor(0, 0, 255))
    }
}
