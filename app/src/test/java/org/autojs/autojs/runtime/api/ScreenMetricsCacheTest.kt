package org.autojs.autojs.runtime.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ScreenMetricsCacheTest {
    @Test
    fun activityResetDuringRotationReadCannotTurnWidthOrHeightIntoZero() {
        for (readWidth in listOf(true, false)) {
            lateinit var cache: ScreenMetricsCache
            var measurements = 0
            cache = ScreenMetricsCache(
                measure = { measurements++; ScreenMetricsCache.Sides(720, 1280) },
                rotation = { cache.invalidate(); 1 },
            )
            assertEquals(if (readWidth) 1280 else 720, if (readWidth) cache.width else cache.height)
            assertEquals(1, measurements)
            assertEquals(if (readWidth) 1280 else 720, if (readWidth) cache.width else cache.height)
            assertEquals("A reset affects the next read, not the current snapshot", 2, measurements)
        }
    }

    @Test
    fun rotationUsesCachedSidesAndInvalidationRemeasuresThePair() {
        var turn = 0
        var measurements = 0
        var measured = ScreenMetricsCache.Sides(720, 1280)
        val cache = ScreenMetricsCache({ measurements++; measured }, { turn })
        for (rotation in listOf(0, 1, 2, 3, 0)) {
            turn = rotation
            assertEquals(if (rotation % 2 == 0) 720 else 1280, cache.width)
            assertEquals(if (rotation % 2 == 0) 1280 else 720, cache.height)
        }
        assertEquals(1, measurements)
        measured = ScreenMetricsCache.Sides(1080, 2400)
        cache.invalidate()
        assertEquals(1080, cache.width)
        assertEquals(2400, cache.height)
        assertEquals(2, measurements)
    }

    @Test
    fun invalidMeasurementIsNotPublishedAndCanBeRetried() {
        var width = 0
        val cache = ScreenMetricsCache({ ScreenMetricsCache.Sides(width, 1280) }, { 0 })
        assertThrows(IllegalArgumentException::class.java) { cache.width }
        width = 720
        assertEquals(720, cache.width)
    }
}
