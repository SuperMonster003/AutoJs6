package org.autojs.autojs.core.virtualscreen

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingWindowGeometryTest {

    @Test
    fun portraitDisplayKeepsItsAspectRatio() {
        // 1080x1920 at a 148dp card width -> 263dp, clamped to the maximum.
        assertEquals(
            FloatingWindowGeometry.MAX_PREVIEW_HEIGHT_DP,
            FloatingWindowGeometry.previewHeightDp(1080, 1920),
        )
        // A squarer display fits without clamping.
        assertEquals(197, FloatingWindowGeometry.previewHeightDp(1080, 1440))
    }

    @Test
    fun landscapeDisplayIsClampedToMinimum() {
        assertEquals(
            FloatingWindowGeometry.MIN_PREVIEW_HEIGHT_DP,
            FloatingWindowGeometry.previewHeightDp(1920, 1080),
        )
    }

    @Test
    fun invalidGeometryFallsBackToDefault() {
        val fallback = FloatingWindowGeometry.DEFAULT_PREVIEW_HEIGHT_DP
        assertEquals(fallback, FloatingWindowGeometry.previewHeightDp(0, 1080))
        assertEquals(fallback, FloatingWindowGeometry.previewHeightDp(1080, -1))
        assertEquals(fallback, FloatingWindowGeometry.previewHeightDp(1080, 1080, 0))
    }

    @Test
    fun cardWidthScalesTheHeight() {
        assertEquals(120, FloatingWindowGeometry.previewHeightDp(1000, 1000, 120))
        assertEquals(240, FloatingWindowGeometry.previewHeightDp(1000, 1000, 240))
    }

}
