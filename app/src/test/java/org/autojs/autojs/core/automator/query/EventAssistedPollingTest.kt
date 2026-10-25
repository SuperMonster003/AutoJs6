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

        assertEquals(PollPolicy.EVENT_MIN_SPACING, fast.minSpacing)
        assertEquals(50L, PollPolicy.EVENT_MIN_SPACING)

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

    /**
     * docs/selector/ROADMAP.md D9 / P3.1: an event sooner than [PollPolicy.minSpacing] after the attempt sleeps the rest
     * of the spacing, and the events of that rest are consumed with it (`eventSleep(0)`) instead of waking the next sleep.
     */
    @Test
    fun anEarlyEventSleepsTheRestOfTheSpacing() {
        val stormy = Poller({ now }, { millis -> sleeps += millis; now += millis }) { millis ->
            eventSleeps += millis
            now += minOf(millis, 10)
        }
        val result = stormy.poll(PollPolicy(times = 3, interval = 200).assisted()) { null }
        assertNull(result)
        assertEquals(listOf(200L, 0L, 200L, 0L), eventSleeps)
        assertEquals(listOf(40L, 40L), sleeps)
    }

    /** D9 / P3.1: under an event every 16 ms, event-assisted polling attempts no more often than the fixed 50 ms interval. */
    @Test
    fun aStormPollsNoMoreOftenThanTheFixedInterval() {
        fun attempts(poller: Poller, policy: PollPolicy): Int {
            now = 0
            var attempts = 0
            poller.poll(policy) { attempts += 1; null }
            return attempts
        }
        val storm = Poller({ now }, { millis -> now += millis }) { millis -> now += minOf(millis, 16) }
        val assisted = attempts(storm, PollPolicy.within(1_000).assisted())
        val fixed = attempts(storm, PollPolicy.within(1_000))
        assertEquals(fixed, assisted)
        assertEquals(22, fixed)
        // Without the spacing (as before P3.1) the storm polls about three times as often. zh-CN: 没有间隔下限时 (P3.1 之前) 风暴下的轮询约为三倍.
        assertEquals(64, attempts(storm, PollPolicy.within(1_000).assisted().copy(minSpacing = 0)))
    }

    @Test
    fun withoutAnEventSleepAnAssistedPolicyFallsBackToTheOrdinarySleep() {
        val plain = Poller({ now }) { millis -> sleeps += millis; now += millis }
        val result = plain.poll(PollPolicy(times = 3, interval = 50).assisted()) { null }
        assertNull(result)
        assertEquals(listOf(200L, 200L), sleeps)
    }

}
