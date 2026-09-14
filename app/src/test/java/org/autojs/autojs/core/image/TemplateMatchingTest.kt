package org.autojs.autojs.core.image

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc

/** Pure-logic parts of the pyramid template matcher (no native OpenCV needed). */
class TemplateMatchingTest {

    private fun match(x: Double, y: Double, similarity: Double, width: Int = 20, height: Int = 10) =
        TemplateMatching.Match(Point(x, y), similarity, width, height, 1.0)

    @Test
    fun overlappingMatchesKeepOnlyTheBestOne() {
        val best = match(100.0, 100.0, 0.98)
        val shiftedByOnePixel = match(101.0, 100.0, 0.97)
        val touchingRight = match(120.0, 100.0, 0.95)
        val touchingBelow = match(100.0, 110.0, 0.96)
        val farAway = match(300.0, 300.0, 0.99)

        val kept = TemplateMatching.suppressOverlapping(listOf(shiftedByOnePixel, touchingRight, best, farAway, touchingBelow))

        assertEquals(listOf(farAway, best, touchingBelow, touchingRight), kept)
    }

    @Test
    fun suppressionAcrossScalesUsesEachMatchOwnSize() {
        val large = TemplateMatching.Match(Point(0.0, 0.0), 0.9, 40, 40, 2.0)
        val smallInside = TemplateMatching.Match(Point(30.0, 30.0), 0.95, 10, 10, 0.5)
        val smallOutside = TemplateMatching.Match(Point(40.0, 0.0), 0.8, 10, 10, 0.5)

        val kept = TemplateMatching.suppressOverlapping(listOf(large, smallInside, smallOutside))

        assertEquals(listOf(smallInside, smallOutside), kept)
    }

    @Test
    fun similarityMapsEveryMethodOntoOneForAPerfectMatch() {
        assertEquals(1.0, TemplateMatching.toSimilarity(Imgproc.TM_SQDIFF_NORMED, 0.0, 100.0), 1e-9)
        assertEquals(0.25, TemplateMatching.toSimilarity(Imgproc.TM_SQDIFF_NORMED, 0.75, 100.0), 1e-9)
        assertEquals(1.0, TemplateMatching.toSimilarity(Imgproc.TM_SQDIFF, 0.0, 100.0), 1e-9)
        /* Every one of 100 samples differs by 255: the worst possible squared difference. */
        assertEquals(0.0, TemplateMatching.toSimilarity(Imgproc.TM_SQDIFF, 100.0 * 255 * 255, 100.0), 1e-9)
        /* Every sample differs by 51 (20% of the range). */
        assertEquals(0.8, TemplateMatching.toSimilarity(Imgproc.TM_SQDIFF, 100.0 * 51 * 51, 100.0), 1e-9)
        assertEquals(0.93, TemplateMatching.toSimilarity(Imgproc.TM_CCOEFF_NORMED, 0.93, 100.0), 1e-9)
    }

    @Test
    fun automaticPyramidDepthKeepsSixteenPixelsAndCapsAtSix() {
        assertEquals(0, TemplateMatching.selectPyramidLevel(1000, 1000, 16, 31))
        assertEquals(1, TemplateMatching.selectPyramidLevel(1000, 1000, 32, 40))
        assertEquals(2, TemplateMatching.selectPyramidLevel(2400, 1080, 100, 100))
        assertEquals(2, TemplateMatching.selectPyramidLevel(64, 64, 200, 200))
        assertEquals(6, TemplateMatching.selectPyramidLevel(8000, 8000, 4000, 4000))
    }

    @Test
    fun callerLevelIsClampedToKeepTheTemplateUsable() {
        /* 20 px halves to 10, 5, 3, 2, 1: level 4 keeps two pixels, level 5 does not. */
        assertEquals(4, TemplateMatching.clampLevel(5, 1000, 1000, 20, 20))
        assertEquals(4, TemplateMatching.clampLevel(4, 1000, 1000, 20, 20))
        assertEquals(0, TemplateMatching.clampLevel(0, 1000, 1000, 20, 20))
        /* A template as large as the image must still fit at the chosen level. */
        assertEquals(1, TemplateMatching.clampLevel(1, 21, 21, 20, 20))
        /* Negative values fall back to the automatic choice. */
        assertEquals(TemplateMatching.selectPyramidLevel(1000, 1000, 100, 100), TemplateMatching.clampLevel(-3, 1000, 1000, 100, 100))
    }

    @Test
    fun sideAtLevelRoundsUpLikeThePyramid() {
        assertEquals(20, TemplateMatching.sideAtLevel(20, 0))
        assertEquals(10, TemplateMatching.sideAtLevel(20, 1))
        assertEquals(3, TemplateMatching.sideAtLevel(20, 3))
        assertEquals(1, TemplateMatching.sideAtLevel(1, 5))
    }

    @Test
    fun searchWindowIsCenteredOnTheTemplateAndClampedToTheImage() {
        val centered = TemplateMatching.getROI(40.0, 30.0, 200, 160, 24, 24)!!
        assertEquals(36, centered.width)
        assertEquals(36, centered.height)
        assertEquals(34, centered.x)
        assertEquals(24, centered.y)

        val topLeft = TemplateMatching.getROI(0.0, 0.0, 200, 160, 24, 24)!!
        assertEquals(0, topLeft.x)
        assertEquals(0, topLeft.y)

        val bottomRight = TemplateMatching.getROI(190.0, 150.0, 200, 160, 24, 24)!!
        assertEquals(200 - 36, bottomRight.x)
        assertEquals(160 - 36, bottomRight.y)

        /* The window never exceeds the image. */
        val tiny = TemplateMatching.getROI(0.0, 0.0, 30, 30, 24, 24)!!
        assertEquals(30, tiny.width)
        assertEquals(0, tiny.x)

        assertNull(TemplateMatching.getROI(0.0, 0.0, 20, 20, 24, 24))
    }

    @Test
    fun scalesAreValidatedAndDefaultToTheOriginalSize() {
        assertArrayEquals(doubleArrayOf(1.0), TemplateMatching.Options.normalizeScales(null), 0.0)
        assertArrayEquals(doubleArrayOf(1.0), TemplateMatching.Options.normalizeScales(DoubleArray(0)), 0.0)
        assertArrayEquals(doubleArrayOf(0.5, 1.0, 2.0), TemplateMatching.Options.normalizeScales(doubleArrayOf(0.5, 1.0, 2.0)), 0.0)
        for (bad in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val error = runCatching { TemplateMatching.Options.normalizeScales(doubleArrayOf(1.0, bad)) }.exceptionOrNull()
            assertTrue("$bad should be rejected", error is IllegalArgumentException)
        }
        val options = TemplateMatching.Options(TemplateMatching.MATCHING_METHOD_NONE, 0.6f, 0.9f, -1, false, 5, doubleArrayOf(0.5))
        assertArrayEquals(doubleArrayOf(0.5), options.scales, 0.0)
        /* The returned array is a copy. */
        options.scales[0] = 9.0
        assertArrayEquals(doubleArrayOf(0.5), options.scales, 0.0)
    }

    @Test
    fun matchExposesItsGeometry() {
        val m = TemplateMatching.Match(Point(10.0, 20.0), 0.9, 30, 40, 1.5)
        assertEquals(25.0, m.center.x, 1e-9)
        assertEquals(40.0, m.center.y, 1e-9)
        assertEquals(10, m.rect.x)
        assertEquals(20, m.rect.y)
        assertEquals(30, m.rect.width)
        assertEquals(40, m.rect.height)
        assertEquals(1.5, m.scale, 0.0)
        val legacy = TemplateMatching.Match(Point(1.0, 2.0), 0.5)
        assertEquals(0, legacy.width)
        assertEquals(1.0, legacy.scale, 0.0)
        assertSame(legacy.point, legacy.point)
        assertTrue(m.toString().contains("width=30"))
    }

    @Test
    fun defaultMethodUsesTheCorrelationCoefficientWithAndWithoutMask() {
        val plain = TemplateMatching.Options(TemplateMatching.MATCHING_METHOD_NONE, 0.6f, 0.9f, -1, false, 5)
        val masked = TemplateMatching.Options(TemplateMatching.MATCHING_METHOD_NONE, 0.6f, 0.9f, -1, true, 5)
        assertEquals(Imgproc.TM_CCOEFF_NORMED, plain.matchingMethod)
        assertEquals(Imgproc.TM_CCOEFF_NORMED, masked.matchingMethod)
        assertTrue(TemplateMatching.isSquaredDifferenceMethod(Imgproc.TM_SQDIFF))
        assertTrue(TemplateMatching.isSquaredDifferenceMethod(Imgproc.TM_SQDIFF_NORMED))
    }
}
