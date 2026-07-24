package org.autojs.autojs.runtime.api.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TtsCapacityLimiterTest {

    @Test
    fun boundsItemsAndWeightBeforeMailboxSubmission() {
        val limiter = TtsCapacityLimiter(maxItems = 2, maxWeight = 10L)
        val first = limiter.tryAcquire(7L)
        val second = limiter.tryAcquire(3L)

        assertNotNull(first)
        assertNotNull(second)
        assertNull(limiter.tryAcquire(0L))
        assertEquals(2 to 10L, limiter.snapshot())

        requireNotNull(first).release()
        assertNotNull(limiter.tryAcquire(7L))
    }

    @Test
    fun releaseIsIdempotent() {
        val limiter = TtsCapacityLimiter(maxItems = 1, maxWeight = 4L)
        val reservation = requireNotNull(limiter.tryAcquire(4L))

        reservation.release()
        reservation.release()

        assertEquals(0 to 0L, limiter.snapshot())
        assertNotNull(limiter.tryAcquire(4L))
    }
}
