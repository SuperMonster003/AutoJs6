package org.autojs.autojs.core.automator.event

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * [IdleWaiter]: quiet from creation, quiet at once after an old event, events postponing the
 * quiet period, a busy stream timing out, type filters and argument checks. Real time with
 * generous margins.
 * zh-CN: [IdleWaiter]: 自创建起的安静, 旧事件之后的立即安静, 推迟安静期的事件, 忙碌事件流的超时, 类型过滤与参数检查. 真实时间,
 * 留有充分余量.
 */
class IdleWaiterTest {

    private val clock = { System.currentTimeMillis() }

    @Test
    fun aQuietStreamIsIdleOnceQuietForHasPassedSinceCreation() {
        val waiter = IdleWaiter(clock)
        val result = waiter.await(quietFor = 60, timeout = 5_000)
        assertTrue(result.quiet)
        assertTrue(result.elapsed in 50..4_000)
        assertEquals(0L, result.events)
    }

    @Test
    fun anOldLastEventCountsAsQuietAtOnce() {
        val waiter = IdleWaiter(clock)
        waiter.onEvent(1)
        Thread.sleep(80)
        val result = waiter.await(quietFor = 50, timeout = 5_000)
        assertTrue(result.quiet)
        assertTrue(result.elapsed < 45)
        assertEquals(0L, result.events)
    }

    @Test
    fun eventsPostponeTheQuietPeriod() {
        val waiter = IdleWaiter(clock)
        val poster = thread {
            repeat(3) {
                Thread.sleep(30)
                waiter.onEvent(2048)
            }
        }
        val result = waiter.await(quietFor = 100, timeout = 5_000)
        poster.join()
        assertTrue(result.quiet)
        assertTrue(result.elapsed >= 150)
        assertEquals(3L, result.events)
    }

    @Test
    fun anAbortEndsThePendingWaitOnlyAndLeavesTheWaiterUsable() {
        val waiter = IdleWaiter(clock)
        val cause = EventWaitAborted("service gone")
        val poster = thread {
            repeat(10) {
                Thread.sleep(20)
                waiter.onEvent(1)
            }
        }
        thread {
            Thread.sleep(60)
            waiter.abort(cause)
        }
        val thrown = assertThrows(EventWaitAborted::class.java) { waiter.await(quietFor = 500, timeout = 5_000) }
        assertSame(cause, thrown)
        poster.join()
        Thread.sleep(40)
        assertTrue("a wait started after the abort is not affected", waiter.await(quietFor = 20, timeout = 5_000).quiet)
    }

    @Test
    fun aBusyStreamTimesOut() {
        val waiter = IdleWaiter(clock)
        val running = AtomicBoolean(true)
        val poster = thread {
            while (running.get()) {
                waiter.onEvent(1)
                Thread.sleep(10)
            }
        }
        val result = waiter.await(quietFor = 300, timeout = 150)
        running.set(false)
        poster.join()
        assertFalse(result.quiet)
        assertTrue(result.elapsed >= 140)
        assertTrue(result.events >= 5)
    }

    @Test
    fun onlyTheRequestedTypesCount() {
        val waiter = IdleWaiter(clock)
        waiter.onEvent(1)
        assertNotEquals(IdleWaiter.NEVER, waiter.lastEventAt(setOf(1)))
        assertEquals(IdleWaiter.NEVER, waiter.lastEventAt(setOf(32)))
        assertEquals(1L, waiter.count(setOf(1)))
        assertEquals(0L, waiter.count(setOf(32)))
        assertEquals(1L, waiter.count())
        val result = waiter.await(quietFor = 50, timeout = 5_000, types = setOf(32))
        assertTrue(result.quiet)
        assertEquals(0L, result.events)
    }

    @Test
    fun negativeArgumentsAreRejected() {
        val waiter = IdleWaiter(clock)
        assertThrows(IllegalArgumentException::class.java) { waiter.await(-1, 100) }
        assertThrows(IllegalArgumentException::class.java) { waiter.await(100, -1) }
    }

}
