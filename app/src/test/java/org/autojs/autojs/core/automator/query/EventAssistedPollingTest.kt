package org.autojs.autojs.core.automator.query

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Event-assisted polling (roadmap P5.2): [PollPolicy.assisted] and the [Poller]'s event sleep.
 * zh-CN: 事件辅助轮询 (Roadmap P5.2): [PollPolicy.assisted] 与 [Poller] 的事件休眠.
 */
class EventAssistedPollingTest {

    private var now = 0L
    private val sleeps = mutableListOf<Long>()
    private val eventSleeps = mutableListOf<Long>()

    private val poller = Poller({ now }, { millis -> sleeps += millis; now += millis }) { millis ->
        eventSleeps += millis
        now += millis / 2
    }

    @Test
    fun assistedRaisesTheIntervalFloorAndKeepsLargerIntervals() {
        val fast = PollPolicy(timeout = 1_000, interval = 50).assisted()
        assertTrue(fast.eventAssisted)
        assertEquals(200L, fast.interval)
        assertEquals(200L, fast.maxInterval)
        assertEquals(1_000L, fast.timeout)

        val slow = PollPolicy(interval = 300, backoff = 2.0, maxInterval = 900).assisted()
        assertTrue(slow.eventAssisted)
        assertEquals(300L, slow.interval)
        assertEquals(900L, slow.maxInterval)

        assertFalse(PollPolicy.ONCE.eventAssisted)
        assertEquals(PollPolicy(times = 1, interval = 200, eventAssisted = true), PollPolicy.ONCE.assisted())
        assertEquals(PollPolicy(interval = 500, maxInterval = 500, eventAssisted = true), PollPolicy.FOREVER.assisted(500))
    }

    @Test
    fun anAssistedPolicySleepsThroughTheEventSleep() {
        var attempts = 0
        val result = poller.poll(PollPolicy(timeout = 1_000, interval = 200).assisted()) { attempts += 1; if (attempts == 3) "found" else null }
        assertEquals("found", result)
        assertEquals(listOf(200L, 200L), eventSleeps)
        assertEquals(emptyList<Long>(), sleeps)
    }

    @Test
    fun aPlainPolicyKeepsTheOrdinarySleep() {
        val result = poller.poll(PollPolicy(times = 2, interval = 50)) { null }
        assertNull(result)
        assertEquals(listOf(50L), sleeps)
        assertEquals(emptyList<Long>(), eventSleeps)
    }

    @Test
    fun withoutAnEventSleepAnAssistedPolicyFallsBackToTheOrdinarySleep() {
        val plain = Poller({ now }) { millis -> sleeps += millis; now += millis }
        val result = plain.poll(PollPolicy(times = 3, interval = 50).assisted()) { null }
        assertNull(result)
        assertEquals(listOf(200L, 200L), sleeps)
    }

}
