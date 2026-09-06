package org.autojs.autojs.core.automator.toolkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HumanizeTest {

    @Test
    fun noneChangesNothing() {
        val random = Random(1)
        assertTrue(Humanize.NONE.isNoop)
        repeat(10) {
            assertEquals(0, Humanize.NONE.offsetX(random))
            assertEquals(0, Humanize.NONE.offsetY(random))
            assertEquals(0L, Humanize.NONE.delay(random))
        }
    }

    @Test
    fun offsetsStayWithinTheirRanges() {
        val random = Random(2)
        val h = Humanize(offsetX = 4, offsetY = 1)
        assertFalse(h.isNoop)
        val xs = List(200) { h.offsetX(random) }
        val ys = List(200) { h.offsetY(random) }
        assertTrue(xs.all { it in -4..4 })
        assertTrue(ys.all { it in -1..1 })
        assertEquals((-4..4).toSet(), xs.toSet())
        assertEquals((-1..1).toSet(), ys.toSet())
    }

    @Test
    fun offsetYDefaultsToOffsetX() {
        assertEquals(3, Humanize(offsetX = 3).offsetY)
    }

    @Test
    fun delaysStayWithinTheirRange() {
        val random = Random(3)
        val h = Humanize(minDelay = 100, maxDelay = 103)
        val delays = List(200) { h.delay(random) }
        assertTrue(delays.all { it in 100L..103L })
        assertEquals((100L..103L).toSet(), delays.toSet())
        assertEquals(50L, Humanize(minDelay = 50).delay(random))
    }

    @Test
    fun unitDrawsCoverTheRangesAndClamp() {
        val h = Humanize(offsetX = 2, offsetY = 1, minDelay = 100, maxDelay = 103)
        assertEquals(-2, h.offsetX(0.0))
        assertEquals(0, h.offsetX(0.5))
        assertEquals(2, h.offsetX(0.999))
        assertEquals(2, h.offsetX(1.0))
        assertEquals(-1, h.offsetY(0.0))
        assertEquals(1, h.offsetY(0.99))
        assertEquals(100L, h.delay(0.0))
        assertEquals(101L, h.delay(0.3))
        assertEquals(103L, h.delay(0.999))
        assertEquals(103L, h.delay(1.0))
        assertEquals(0, Humanize.NONE.offsetX(0.7))
        assertEquals(0L, Humanize.NONE.delay(0.7))
        assertEquals(50L, Humanize(minDelay = 50).delay(0.9))
    }

    @Test
    fun argumentsAreValidated() {
        assertThrows(IllegalArgumentException::class.java) { Humanize(offsetX = -1) }
        assertThrows(IllegalArgumentException::class.java) { Humanize(offsetX = 1, offsetY = -1) }
        assertThrows(IllegalArgumentException::class.java) { Humanize(minDelay = -1) }
        assertThrows(IllegalArgumentException::class.java) { Humanize(minDelay = 10, maxDelay = 5) }
    }

}
