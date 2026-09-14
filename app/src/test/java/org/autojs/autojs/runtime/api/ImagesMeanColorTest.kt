package org.autojs.autojs.runtime.api

import org.junit.Assert.assertEquals
import org.junit.Test
import org.opencv.core.Scalar

class ImagesMeanColorTest {

    @Test
    fun grayscaleMeanBecomesOpaqueGray() {
        assertEquals(0xFF808080.toInt(), Images.meanScalarToColor(Scalar(128.0), 1))
        assertEquals(0xFF7F7F7F.toInt(), Images.meanScalarToColor(Scalar(127.4), 1))
    }

    @Test
    fun rgbMeanIsOpaque() {
        assertEquals(0xFF102030.toInt(), Images.meanScalarToColor(Scalar(16.0, 32.0, 48.0), 3))
    }

    @Test
    fun rgbaMeanKeepsAlphaAndRounds() {
        assertEquals(0x80FF0001.toInt(), Images.meanScalarToColor(Scalar(254.6, 0.4, 0.5, 128.0), 4))
    }

    @Test
    fun valuesAreClampedToChannelRange() {
        assertEquals(0xFFFF0000.toInt(), Images.meanScalarToColor(Scalar(300.0, -5.0, 0.0, 999.0), 4))
    }
}
