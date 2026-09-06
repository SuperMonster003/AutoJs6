package org.autojs.autojs.core.automator.flow

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.flow.Stabilizer.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "unchanged for N ms" state machine of roadmap Appendix A.5, with time passed in by hand.
 * zh-CN: Roadmap 附录 A.5 的 "连续 N 毫秒无变化" 状态机, 时间由手工传入.
 */
class StabilizerTest {

    private fun stabilizer(stableFor: Long = 200, missing: MissingPolicy = MissingPolicy.RESET) =
        Stabilizer(Stability(stableFor, StabilityCompare.Fingerprint, missing), { it })

    @Test
    fun stableOnceTheSameSampleHasLastedStableFor() {
        val s = stabilizer(200)
        assertEquals(Verdict.Pending, s.observe("a", 0))
        assertEquals(Verdict.Pending, s.observe("a", 100))
        assertEquals(Verdict.Stable("a"), s.observe("a", 200))
        assertEquals(3, s.observations)
    }

    @Test
    fun aChangeRestartsTheCount() {
        val s = stabilizer(200)
        s.observe("a", 0)
        s.observe("a", 150)
        assertEquals(Verdict.Pending, s.observe("b", 200))
        assertEquals(Verdict.Pending, s.observe("b", 350))
        assertEquals(Verdict.Stable("b"), s.observe("b", 400))
        assertEquals("unstable", s.reason)
    }

    @Test
    fun aTargetNeverSeenIsAwaited() {
        val s = stabilizer(200, MissingPolicy.FAIL)
        assertEquals(Verdict.Pending, s.observe(null, 0))
        assertEquals(Verdict.Pending, s.observe(null, 1000))
        assertFalse(s.seen)
        assertEquals("missing", s.reason)
        assertEquals(Verdict.Pending, s.observe("a", 1100))
        assertTrue(s.seen)
    }

    @Test
    fun missingAfterASightingResetsOrFails() {
        val reset = stabilizer(200, MissingPolicy.RESET)
        reset.observe("a", 0)
        assertEquals(Verdict.Pending, reset.observe(null, 100))
        assertEquals("missing", reset.reason)
        assertEquals(Verdict.Pending, reset.observe("a", 200))
        assertEquals(Verdict.Pending, reset.observe("a", 300))
        assertEquals(Verdict.Stable("a"), reset.observe("a", 400))

        val fail = stabilizer(200, MissingPolicy.FAIL)
        fail.observe("a", 0)
        assertEquals(Verdict.Missing, fail.observe(null, 100))
    }

    @Test
    fun zeroStableForIsStableAtTheFirstSighting() {
        val s = stabilizer(0)
        assertEquals(Verdict.Pending, s.observe(null, 0))
        assertEquals(Verdict.Stable("a"), s.observe("a", 0))
    }

    @Test
    fun keysAreTakenWhenObservedSoAMutatedSampleStillCounts() {
        val node = FakeUiNode.root("n") { text = "a" }
        val s = Stabilizer(Stability(100), { Stabilizer.keyOf(StabilityCompare.Fingerprint, it) })
        assertEquals(Verdict.Pending, s.observe(node, 0))
        node.text = "b"
        assertEquals(Verdict.Pending, s.observe(node, 100))
        assertEquals(Verdict.Pending, s.observe(node, 150))
        assertEquals(Verdict.Stable(node), s.observe(node, 200))
    }

    @Test
    fun keysFollowTheCompareMode() {
        val a = FakeUiNode.root("a") { text = "Login"; desc = "d"; bounds(0, 0, 10, 10) }
        val sameBoundsOtherText = FakeUiNode.root("b") { text = "Logout"; desc = "d"; bounds(0, 0, 10, 10) }
        val sameContentOtherBounds = FakeUiNode.root("c") { text = "Login"; desc = "d"; bounds(5, 5, 20, 20) }
        assertNotEquals(Stabilizer.keyOf(StabilityCompare.Fingerprint, a), Stabilizer.keyOf(StabilityCompare.Fingerprint, sameBoundsOtherText))
        assertNotEquals(Stabilizer.keyOf(StabilityCompare.Fingerprint, a), Stabilizer.keyOf(StabilityCompare.Fingerprint, sameContentOtherBounds))
        assertEquals(Stabilizer.keyOf(StabilityCompare.Bounds, a), Stabilizer.keyOf(StabilityCompare.Bounds, sameBoundsOtherText))
        assertNotEquals(Stabilizer.keyOf(StabilityCompare.Bounds, a), Stabilizer.keyOf(StabilityCompare.Bounds, sameContentOtherBounds))
        assertEquals(Stabilizer.keyOf(StabilityCompare.Content, a), Stabilizer.keyOf(StabilityCompare.Content, sameContentOtherBounds))
        assertNotEquals(Stabilizer.keyOf(StabilityCompare.Content, a), Stabilizer.keyOf(StabilityCompare.Content, sameBoundsOtherText))
        // Non-node samples are compared as they are.
        assertEquals("x", Stabilizer.keyOf(StabilityCompare.Fingerprint, "x"))
        assertEquals(3, Stabilizer.keyOf(StabilityCompare.Bounds, 3))
    }

    @Test
    fun visibilityNeedsVisibleToUserAndNonEmptyBounds() {
        assertTrue(Stabilizer.isVisible(FakeUiNode.root("a") { bounds(0, 0, 10, 10) }))
        assertFalse(Stabilizer.isVisible(FakeUiNode.root("a") { bounds(0, 0, 10, 10); visibleToUser = false }))
        assertFalse(Stabilizer.isVisible(FakeUiNode.root("a") { bounds(0, 0, 0, 10) }))
        assertFalse(Stabilizer.isVisible(null))
        assertTrue(Stabilizer.isVisible("not a node"))
    }

}
