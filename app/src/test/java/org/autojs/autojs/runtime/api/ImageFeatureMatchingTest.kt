package org.autojs.autojs.runtime.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opencv.core.CvType
import org.opencv.core.Point
import org.opencv.features2d.DescriptorMatcher

/** Pure-logic parts of feature matching (no native OpenCV needed). */
class ImageFeatureMatchingTest {

    private fun quad(vararg xy: Double) = listOf(Point(xy[0], xy[1]), Point(xy[2], xy[3]), Point(xy[4], xy[5]), Point(xy[6], xy[7]))

    @Test
    fun axisAlignedAndRotatedQuadsAreValid() {
        /* TL, TR, BL, BR */
        assertTrue(ImageFeatureMatching.isValidQuad(quad(300.0, 200.0, 460.0, 200.0, 300.0, 320.0, 460.0, 320.0)))
        /* Rotated by 90 degrees clockwise. */
        assertTrue(ImageFeatureMatching.isValidQuad(quad(220.0, 100.0, 220.0, 260.0, 100.0, 100.0, 100.0, 260.0)))
        /* Mild perspective. */
        assertTrue(ImageFeatureMatching.isValidQuad(quad(10.0, 10.0, 110.0, 20.0, 5.0, 90.0, 120.0, 100.0)))
    }

    @Test
    fun degenerateQuadsAreRejected() {
        /* Bow-tie: TR and BR swapped, so the boundary crosses itself. */
        assertFalse(ImageFeatureMatching.isValidQuad(quad(0.0, 0.0, 100.0, 100.0, 0.0, 100.0, 100.0, 0.0)))
        /* Collapsed to a line. */
        assertFalse(ImageFeatureMatching.isValidQuad(quad(0.0, 0.0, 100.0, 0.0, 200.0, 0.0, 300.0, 0.0)))
        /* Tiny area. */
        assertFalse(ImageFeatureMatching.isValidQuad(quad(0.0, 0.0, 0.5, 0.0, 0.0, 0.5, 0.5, 0.5)))
        /* Non-finite corner. */
        assertFalse(ImageFeatureMatching.isValidQuad(quad(0.0, 0.0, Double.NaN, 0.0, 0.0, 100.0, 100.0, 100.0)))
        assertFalse(ImageFeatureMatching.isValidQuad(null))
        assertFalse(ImageFeatureMatching.isValidQuad(quad(0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 1.0).subList(0, 3)))
    }

    @Test
    fun matcherIsCorrectedToFitTheDescriptorType() {
        val binary = CvType.CV_8UC1
        val float = CvType.CV_32FC1
        assertEquals(DescriptorMatcher.BRUTEFORCE_HAMMING, ImageFeatureMatching.resolveMatcherType(-1, binary))
        assertEquals(DescriptorMatcher.BRUTEFORCE_HAMMING, ImageFeatureMatching.resolveMatcherType(DescriptorMatcher.FLANNBASED, binary))
        assertEquals(DescriptorMatcher.BRUTEFORCE_HAMMINGLUT, ImageFeatureMatching.resolveMatcherType(DescriptorMatcher.BRUTEFORCE_HAMMINGLUT, binary))
        assertEquals(DescriptorMatcher.FLANNBASED, ImageFeatureMatching.resolveMatcherType(-1, float))
        assertEquals(DescriptorMatcher.FLANNBASED, ImageFeatureMatching.resolveMatcherType(DescriptorMatcher.BRUTEFORCE_HAMMING, float))
        assertEquals(DescriptorMatcher.BRUTEFORCE, ImageFeatureMatching.resolveMatcherType(DescriptorMatcher.BRUTEFORCE, float))
        assertEquals(DescriptorMatcher.BRUTEFORCE_SL2, ImageFeatureMatching.resolveMatcherType(DescriptorMatcher.BRUTEFORCE_SL2, float))
        /* Unknown numbers fall back to the default of the type. */
        assertEquals(DescriptorMatcher.FLANNBASED, ImageFeatureMatching.resolveMatcherType(99, float))
    }
}
