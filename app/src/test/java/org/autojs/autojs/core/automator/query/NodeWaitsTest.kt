package org.autojs.autojs.core.automator.query

import org.autojs.autojs.core.automator.flow.MissingPolicy
import org.autojs.autojs.core.automator.flow.Stability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The node waits of roadmap P5.3 as poll loops: [NodeWaits.untilGone] and [NodeWaits.untilStable]
 * under a fake clock.
 * zh-CN: 作为轮询循环的 Roadmap P5.3 节点等待: 假时钟下的 [NodeWaits.untilGone] 与 [NodeWaits.untilStable].
 */
class NodeWaitsTest {

    private var now = 0L
    private val sleeps = mutableListOf<Long>()
    private val poller = Poller({ now }) { millis -> sleeps += millis; now += millis }

    @Test
    fun untilGoneReturnsTrueAsSoonAsTheProbeSaysSo() {
        var attempts = 0
        assertTrue(NodeWaits.untilGone(poller, PollPolicy.within(1_000, 100)) { attempts += 1; attempts == 3 })
        assertEquals(3, attempts)
        assertEquals(listOf(100L, 100L), sleeps)
        assertEquals(200L, now)
    }

    @Test
    fun untilGoneGivesUpAtTheTimeout() {
        var attempts = 0
        assertFalse(NodeWaits.untilGone(poller, PollPolicy.within(250, 100)) { attempts += 1; false })
        assertTrue("attempts $attempts", attempts in 3..4)
        assertTrue("now $now", now >= 250)
    }

    @Test
    fun untilGoneIsTrueAtOnceWhenAlreadyGone() {
        assertTrue(NodeWaits.untilGone(poller, PollPolicy.within(1_000, 100)) { true })
        assertEquals(emptyList<Long>(), sleeps)
    }

    @Test
    fun untilStableWaitsForTheQuietPeriodAfterTheLastChange() {
        val samples = ArrayDeque(listOf("a", "b", "b", "b", "b", "b", "b", "b"))
        assertTrue(NodeWaits.untilStable(poller, PollPolicy.within(5_000, 100), { now }, Stability(stableFor = 300)) { samples.removeFirst() })
        // "a" at 0, "b" first seen at 100, still "b" at 200, 300 and 400: quiet for 300 ms at 400.
        // zh-CN: 0 时为 "a", 100 时首见 "b", 200, 300, 400 时仍为 "b": 400 时已安静 300 ms.
        assertEquals(3, samples.size)
        assertEquals(400L, now)
    }

    @Test
    fun untilStableFailsAtOnceWhenTheTargetGoesMissingUnderFail() {
        val samples = ArrayDeque<String?>(listOf("a", "a", null, "a", "a", "a", "a", "a"))
        val stability = Stability(stableFor = 300, missing = MissingPolicy.FAIL)
        assertFalse(NodeWaits.untilStable(poller, PollPolicy.within(5_000, 100), { now }, stability) { samples.removeFirst() })
        assertEquals(5, samples.size)
    }

    @Test
    fun untilStableStartsOverWhenTheTargetGoesMissingUnderReset() {
        val samples = ArrayDeque<String?>(listOf("a", "a", null, "a", "a", "a", "a", "a"))
        val stability = Stability(stableFor = 200, missing = MissingPolicy.RESET)
        assertTrue(NodeWaits.untilStable(poller, PollPolicy.within(5_000, 100), { now }, stability) { samples.removeFirst() })
        // "a" again at 300, quiet for 200 ms at 500.
        // zh-CN: 300 时再见 "a", 500 时已安静 200 ms.
        assertEquals(500L, now)
    }

    @Test
    fun aTargetNotYetSeenOnlyKeepsWaiting() {
        val samples = ArrayDeque<String?>(listOf(null, null, "a", "a", "a", "a", "a", "a"))
        val stability = Stability(stableFor = 200, missing = MissingPolicy.FAIL)
        assertTrue(NodeWaits.untilStable(poller, PollPolicy.within(5_000, 100), { now }, stability) { samples.removeFirst() })
        assertEquals(400L, now)
    }

    @Test
    fun untilStableGivesUpWhenNothingSettlesInTime() {
        var i = 0
        assertFalse(NodeWaits.untilStable(poller, PollPolicy.within(450, 100), { now }, Stability(stableFor = 300)) { i += 1; "s$i" })
        assertTrue("now $now", now >= 450)
    }

}
