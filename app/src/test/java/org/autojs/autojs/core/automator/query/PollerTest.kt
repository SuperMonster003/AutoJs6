package org.autojs.autojs.core.automator.query

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [Poller] under a fake clock and a recording sleep: the order of the checks (attempt, times,
 * timeout, sleep), the strict timeout of `findOne(timeout)`, the backoff, and interruption.
 * zh-CN: 以假时钟与记录式休眠运行 [Poller]: 检查顺序 (尝试, 次数, 超时, 休眠), `findOne(timeout)` 的严格超时, 退避与中断.
 */
class PollerTest {

    private var now = 1_000L
    private val sleeps = mutableListOf<Long>()
    private var timePerAttempt = 0L

    private val poller = Poller({ now }) { millis -> sleeps += millis; now += millis }

    private fun <T : Any> poll(policy: PollPolicy, results: List<T?>): Pair<T?, Int> {
        var attempts = 0
        val result = poller.poll(policy) { progress ->
            attempts += 1
            assertEquals(attempts.toLong(), progress.round)
            now += timePerAttempt
            results[attempts - 1]
        }
        return result to attempts
    }

    @Test
    fun aSuccessfulFirstAttemptReturnsWithoutSleeping() {
        val (result, attempts) = poll(PollPolicy.FOREVER, listOf("found"))
        assertEquals("found", result)
        assertEquals(1, attempts)
        assertEquals(emptyList<Long>(), sleeps)
    }

    @Test
    fun onceAttemptsExactlyOnceAndNeverSleeps() {
        val (result, attempts) = poll(PollPolicy.ONCE, listOf(null, "late"))
        assertNull(result)
        assertEquals(1, attempts)
        assertEquals(emptyList<Long>(), sleeps)
    }

    @Test
    fun aNoopPolicyDoesNotAttemptAtAll() {
        val (result, attempts) = poll(PollPolicy(times = 0), listOf("found"))
        assertNull(result)
        assertEquals(0, attempts)
    }

    @Test
    fun timesBoundsTheAttemptsWithTheIntervalInBetween() {
        val (result, attempts) = poll(PollPolicy(times = 3, interval = 20), listOf(null, null, null, "late"))
        assertNull(result)
        assertEquals(3, attempts)
        assertEquals(listOf(20L, 20L), sleeps)
    }

    @Test
    fun theResultOfALaterAttemptIsReturned() {
        val (result, attempts) = poll(PollPolicy(times = 5), listOf(null, null, "third", "fourth"))
        assertEquals("third", result)
        assertEquals(3, attempts)
        assertEquals(listOf(50L, 50L), sleeps)
    }

    @Test
    fun timeoutIsMeasuredAfterTheAttemptAndExceededStrictly() {
        // findOne(100): the check follows the attempt, so attempts are made at 0 / 50 / 100 / 150 ms
        // elapsed (100 is not "more than" the timeout, 150 is checked only after its attempt).
        // zh-CN: findOne(100): 检查在尝试之后, 因此已用 0 / 50 / 100 / 150 毫秒时都会尝试 (100 不算 "超过" 超时,
        // 150 在其尝试之后才被检查).
        val (result, attempts) = poll(PollPolicy.within(100), listOf(null, null, null, null, null))
        assertNull(result)
        assertEquals(4, attempts)
        assertEquals(listOf(50L, 50L, 50L), sleeps)
    }

    @Test
    fun aSlowAttemptStillGetsItsResultTakenBeforeTheTimeoutCheck() {
        timePerAttempt = 500
        val (result, attempts) = poll(PollPolicy.within(100), listOf(null, "second"))
        // The first attempt already exceeds the timeout, so there is no second one.
        // zh-CN: 首次尝试已超过超时, 因此没有第二次.
        assertNull(result)
        assertEquals(1, attempts)
        assertEquals(emptyList<Long>(), sleeps)

        sleeps.clear()
        val (found, made) = poll(PollPolicy.within(100), listOf("first"))
        assertEquals("first", found)
        assertEquals(1, made)
    }

    @Test
    fun progressReportsElapsedAndRemaining() {
        val seen = mutableListOf<Pair<Long, Long>>()
        poller.poll(PollPolicy.within(120, interval = 50)) { progress ->
            seen += progress.elapsed to progress.remaining
            null
        }
        assertEquals(listOf(0L to 120L, 50L to 70L, 100L to 20L, 150L to 0L), seen)

        val unbounded = mutableListOf<Long>()
        poller.poll(PollPolicy(times = 2)) { progress -> unbounded += progress.remaining; null }
        assertEquals(listOf(PollPolicy.UNBOUNDED, PollPolicy.UNBOUNDED), unbounded)
    }

    @Test
    fun theSleepsFollowTheBackoff() {
        val policy = PollPolicy(times = 6, interval = 10, backoff = 2.0, maxInterval = 50)
        val (_, attempts) = poll(policy, List(6) { null })
        assertEquals(6, attempts)
        assertEquals(listOf(10L, 20L, 40L, 50L, 50L), sleeps)
    }

    @Test
    fun anExceptionFromTheSleepEndsThePoll() {
        val interrupting = Poller({ now }) { throw InterruptedException("stopped") }
        var attempts = 0
        assertThrows(InterruptedException::class.java) {
            interrupting.poll(PollPolicy.FOREVER) { attempts += 1; null }
        }
        assertEquals(1, attempts)
    }

    @Test
    fun anExceptionFromTheAttemptPropagates() {
        assertThrows(IllegalStateException::class.java) {
            poller.poll(PollPolicy.FOREVER) { throw IllegalStateException("ui thread") }
        }
        assertEquals(emptyList<Long>(), sleeps)
    }

}
