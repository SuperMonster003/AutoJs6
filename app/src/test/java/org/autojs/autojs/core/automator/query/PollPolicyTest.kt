package org.autojs.autojs.core.automator.query

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The polling plans behind the selector queries: the historical defaults, the `findOne(timeout)`
 * mapping, argument validation and the backoff of the interval.
 * zh-CN: 选择器查询背后的轮询计划: 历史默认值, `findOne(timeout)` 的映射, 参数校验与间隔的退避.
 */
class PollPolicyTest {

    @Test
    fun defaultsAreTheHistoricalOnes() {
        val policy = PollPolicy()
        assertEquals(PollPolicy.UNBOUNDED, policy.timeout)
        assertEquals(50L, policy.interval)
        assertEquals(PollPolicy.UNBOUNDED, policy.times)
        assertEquals(1.0, policy.backoff, 0.0)
        assertEquals(policy.interval, policy.maxInterval)
        assertTrue(policy.isUnbounded)
        assertFalse(policy.isNoop)
    }

    @Test
    fun onceAndForeverAreTheSingleAttemptAndTheUnboundedPlans() {
        assertEquals(1L, PollPolicy.ONCE.times)
        assertEquals(PollPolicy.UNBOUNDED, PollPolicy.ONCE.timeout)
        assertFalse(PollPolicy.ONCE.isUnbounded)
        assertEquals(PollPolicy(), PollPolicy.FOREVER)
        assertTrue(PollPolicy.FOREVER.isUnbounded)
    }

    @Test
    fun withinMapsAPositiveTimeoutAndTreatsZeroOrLessAsForever() {
        assertEquals(PollPolicy(timeout = 1500), PollPolicy.within(1500))
        assertEquals(PollPolicy(timeout = 1500, interval = 10), PollPolicy.within(1500, interval = 10))
        assertEquals(PollPolicy.FOREVER, PollPolicy.within(0))
        assertEquals(PollPolicy.FOREVER, PollPolicy.within(-1))
        assertEquals(PollPolicy(interval = 200), PollPolicy.within(Long.MIN_VALUE, interval = 200))
    }

    @Test
    fun zeroTimesIsANoopAndNegativeArgumentsAreRejected() {
        assertTrue(PollPolicy(times = 0).isNoop)
        assertFalse(PollPolicy(times = 0).isUnbounded)
        assertThrows(IllegalArgumentException::class.java) { PollPolicy(timeout = -1) }
        assertThrows(IllegalArgumentException::class.java) { PollPolicy(interval = -1) }
        assertThrows(IllegalArgumentException::class.java) { PollPolicy(times = -1) }
        assertThrows(IllegalArgumentException::class.java) { PollPolicy(backoff = 0.5) }
        assertThrows(IllegalArgumentException::class.java) { PollPolicy(interval = 100, maxInterval = 50) }
    }

    @Test
    fun intervalIsConstantWithoutBackoff() {
        val policy = PollPolicy(interval = 50)
        assertEquals(50L, policy.intervalAfter(0))
        assertEquals(50L, policy.intervalAfter(1))
        assertEquals(50L, policy.intervalAfter(1000))
    }

    @Test
    fun intervalGrowsByTheBackoffUpToTheMaximum() {
        val policy = PollPolicy(interval = 100, backoff = 2.0, maxInterval = 1000)
        assertEquals(100L, policy.intervalAfter(0))
        assertEquals(200L, policy.intervalAfter(1))
        assertEquals(400L, policy.intervalAfter(2))
        assertEquals(800L, policy.intervalAfter(3))
        assertEquals(1000L, policy.intervalAfter(4))
        assertEquals(1000L, policy.intervalAfter(60))
        assertEquals(1000L, policy.intervalAfter(Long.MAX_VALUE))
    }

    @Test
    fun backoffWithoutAMaximumIsCappedAtTheInterval() {
        // `maxInterval` defaults to the interval, so a backoff alone changes nothing.
        // zh-CN: `maxInterval` 默认为间隔本身, 因此仅有退避系数不改变任何东西.
        val policy = PollPolicy(interval = 100, backoff = 3.0)
        assertEquals(100L, policy.intervalAfter(0))
        assertEquals(100L, policy.intervalAfter(5))
    }

}
