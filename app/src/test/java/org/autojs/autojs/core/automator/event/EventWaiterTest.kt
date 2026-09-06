package org.autojs.autojs.core.automator.event

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.concurrent.thread

/**
 * [EventWaiter]: arrival order, the predicate on the waiting thread, timeouts, wake-ups from
 * another thread, the bounded queue and argument checks. Real time with generous margins.
 * zh-CN: [EventWaiter]: 到达顺序, 等待线程上的谓词, 超时, 来自另一线程的唤醒, 有界队列与参数检查. 真实时间, 留有充分余量.
 */
class EventWaiterTest {

    private val clock = { System.currentTimeMillis() }

    @Test
    fun theFirstAcceptedEventIsReturnedInArrivalOrder() {
        val waiter = EventWaiter<String>(clock)
        waiter.offer("a")
        waiter.offer("b")
        waiter.offer("c")
        val result = waiter.await(1_000) { it != "a" }
        assertEquals("b", result.event)
        assertTrue(result.found)
        assertEquals(2, result.seen)
        assertTrue(waiter.hasPending)
        assertEquals("c", waiter.await(0) { true }.event)
        assertFalse(waiter.hasPending)
    }

    @Test
    fun awaitTimesOutWhenNothingMatches() {
        val waiter = EventWaiter<String>(clock)
        waiter.offer("x")
        val result = waiter.await(80) { false }
        assertNull(result.event)
        assertFalse(result.found)
        assertEquals(1, result.seen)
        assertTrue(result.elapsed >= 70)
    }

    @Test
    fun anEventOfferedFromAnotherThreadWakesTheWait() {
        val waiter = EventWaiter<String>(clock)
        thread {
            Thread.sleep(40)
            waiter.offer("late")
        }
        val result = waiter.await(5_000) { true }
        assertEquals("late", result.event)
        assertTrue(result.elapsed < 4_000)
    }

    @Test
    fun thePredicateRunsOnTheWaitingThread() {
        val waiter = EventWaiter<String>(clock)
        val waiting = Thread.currentThread()
        thread { waiter.offer("event") }
        var seenOn: Thread? = null
        waiter.await(5_000) { seenOn = Thread.currentThread(); true }
        assertEquals(waiting, seenOn)
    }

    @Test
    fun theQueueKeepsTheNewestEvents() {
        val waiter = EventWaiter<Int>(clock, capacity = 3)
        (1..5).forEach { waiter.offer(it) }
        assertEquals(5L, waiter.offered)
        assertEquals(2L, waiter.dropped)
        assertEquals(3, waiter.await(0) { true }.event)
        assertEquals(4, waiter.await(0) { true }.event)
        assertEquals(5, waiter.await(0) { true }.event)
    }

    @Test
    fun aZeroTimeoutOnlyDrainsTheQueue() {
        val waiter = EventWaiter<String>(clock)
        val result = waiter.await(0) { true }
        assertNull(result.event)
        assertEquals(0, result.seen)
    }

    @Test
    fun negativeTimeoutsAndEmptyCapacitiesAreRejected() {
        val waiter = EventWaiter<String>(clock)
        assertThrows(IllegalArgumentException::class.java) { waiter.await(-1) { true } }
        assertThrows(IllegalArgumentException::class.java) { EventWaiter<String>(clock, capacity = 0) }
    }

}
