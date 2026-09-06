package org.autojs.autojs.core.automator.toolkit

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.flow.FlowError
import org.autojs.autojs.core.automator.toolkit.Toolkit.Locate
import org.autojs.autojs.core.automator.toolkit.Toolkit.RetryOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared parts of the toolkit: the locate plan, the compositions (`findAny`, `clickAny`,
 * `clickIfExists`), `retry` and container resolution.
 * zh-CN: 工具集的共用部分: 定位计划, 组合 (`findAny`, `clickAny`, `clickIfExists`), `retry` 与容器解析.
 */
class ToolkitTest {

    private val rig = ToolkitRig()
    private val kit = rig.kit
    private val actions = rig.actions
    private val b = rig.bindings

    private val a = FakeUiNode.root("a", clickable = true)
    private val c = FakeUiNode.root("c", clickable = true)

    private fun error(block: () -> Unit): FlowError = assertThrows(FlowError::class.java) { block() }

    @Test
    fun locatePlanMapsToPollPolicies() {
        assertTrue(Locate.ONCE.policy.times == 1L)
        val plan = Locate(timeout = 800, interval = 40)
        assertEquals(800, plan.policy.timeout)
        assertEquals(40, plan.policy.interval)
        assertEquals(1, kit.within(0).times)
        assertThrows(IllegalArgumentException::class.java) { Locate(timeout = -1) }
    }

    @Test
    fun locateLooksOnceByDefault() {
        val e = error { kit.locate("a", null, "tool") }
        assertEquals(FlowError.Code.TIMEOUT, e.code)
        assertEquals("tool", e.step)
        assertEquals("text(\"a\")", e.selector)
        assertEquals(1, e.attempts)
        assertEquals(0, e.elapsed)
        assertTrue(rig.sleeps.isEmpty())
    }

    @Test
    fun locatePollsPerPlan() {
        rig.appearing("a", a, from = 3)
        assertSame(a, kit.locate("a", null, "tool", Locate(timeout = 1000, interval = 100)))
        assertEquals(listOf(100L, 100L), rig.sleeps)

        val e = error { kit.locate("never", null, "tool", Locate(timeout = 250, interval = 100)) }
        assertEquals(4, e.attempts)
        assertEquals(300, e.elapsed)
    }

    @Test
    fun findAnyReturnsTheFirstPresentInListOrder() {
        b.show("c", c)
        val found = kit.findAny(listOf("a", "b", "c"))!!
        assertEquals(2, found.index)
        assertEquals("c", found.target)
        assertSame(c, found.node)
        assertNull(kit.findAny(listOf("a", "b")))
        assertThrows(IllegalArgumentException::class.java) { kit.findAny(emptyList()) }
    }

    @Test
    fun findAnyPollsAllCandidatesEachRound() {
        rig.appearing("a", a, from = 2)
        val found = kit.findAny(listOf("a", "c"), locate = Locate(timeout = 1000))!!
        assertSame(a, found.node)
        assertEquals(listOf(50L), rig.sleeps)
        assertEquals(3, b.finds)
    }

    @Test
    fun clickAnyClicksWhatItFinds() {
        b.show("c", c)
        val clicked = kit.clickAny(listOf("a", "c"))!!
        assertEquals(1, clicked.found.index)
        assertEquals(SmartClick.Method.NODE, clicked.outcome.method)
        assertEquals(listOf("click(c)"), actions.calls)
        assertNull(kit.clickAny(listOf("a")))
    }

    @Test
    fun clickAnyFailuresCarryTheStepAndSelector() {
        b.show("c", c)
        actions.clickResult = { false }
        val e = error { kit.clickAny(listOf("a", "c"), options = SmartClick.Options(gestureFallback = false)) }
        assertEquals(Toolkit.STEP_CLICK_ANY, e.step)
        assertEquals("text(\"c\")", e.selector)
    }

    @Test
    fun clickIfExistsIsNullWhenAbsent() {
        assertNull(kit.clickIfExists("a"))
        assertTrue(actions.calls.isEmpty())
        b.show("a", a)
        assertEquals(SmartClick.Method.NODE, kit.clickIfExists("a")!!.method)
        assertEquals(listOf("click(a)"), actions.calls)
    }

    @Test
    fun clickIfExistsWaitsPerPlan() {
        rig.appearing("a", a, from = 2)
        assertSame(a, kit.clickIfExists("a", locate = Locate(timeout = 500))!!.target)
        assertEquals(listOf(50L), rig.sleeps)
    }

    @Test
    fun pauseSleepsTheHumanizedDelay() {
        kit.pause(Humanize.NONE)
        assertTrue(rig.sleeps.isEmpty())
        kit.pause(Humanize(minDelay = 5, maxDelay = 5))
        assertEquals(listOf(5L), rig.sleeps)
    }

    @Test
    fun retryRerunsWithBackoffAndReturnsTheFirstSuccess() {
        val attempts = ArrayList<Int>()
        val value = kit.retry(RetryOptions(times = 3, delay = 100, backoff = 2.0)) { attempt ->
            attempts += attempt
            if (attempt < 2) throw IllegalStateException("not yet") else "ok"
        }
        assertEquals("ok", value)
        assertEquals(listOf(0, 1, 2), attempts)
        assertEquals(listOf(100L, 200L), rig.sleeps)
    }

    @Test
    fun retryRethrowsTheLastErrorAfterTheReruns() {
        var runs = 0
        val e = assertThrows(IllegalStateException::class.java) {
            kit.retry(RetryOptions(times = 2, delay = 10)) { throw IllegalStateException("run ${++runs}") }
        }
        assertEquals("run 3", e.message)
        assertEquals(listOf(10L, 10L), rig.sleeps)
    }

    @Test
    fun retryDoesNotRerunCancellationsOrFatalErrors() {
        var runs = 0
        assertThrows(FlowError.Cancelled::class.java) {
            kit.retry { runs += 1; throw FlowError.Cancelled("step") }
        }
        assertEquals(1, runs)

        rig.fatal = { it is IllegalArgumentException }
        runs = 0
        assertThrows(IllegalArgumentException::class.java) {
            kit.retry { runs += 1; throw IllegalArgumentException() }
        }
        assertEquals(1, runs)
        assertTrue(rig.sleeps.isEmpty())
    }

    @Test
    fun retryOptionsAreValidated() {
        assertThrows(IllegalArgumentException::class.java) { RetryOptions(times = -1) }
        assertThrows(IllegalArgumentException::class.java) { RetryOptions(backoff = 0.5) }
        assertEquals(500, RetryOptions().delayBefore(1))
        assertEquals(500, RetryOptions().delayBefore(3))
        assertEquals(400, RetryOptions(delay = 100, backoff = 2.0).delayBefore(3))
    }

    @Test
    fun resolveContainerDetectsOrLocates() {
        val root = FakeUiNode.root {
            child("small") {
                scrollable = true
                bounds(0, 0, 10, 10)
            }
            child("big") {
                scrollable = true
                bounds(0, 0, 100, 100)
            }
        }
        b.root = root
        assertSame(root.find("big"), kit.resolveContainer(null, null, "tool"))
        assertSame(root.find("small"), kit.resolveContainer(null, root.find("small"), "tool"))
        b.show("s", root.find("small"))
        assertSame(root.find("small"), kit.resolveContainer("s", null, "tool"))
        val e = error { kit.resolveContainer("nope", null, "tool") }
        assertEquals(FlowError.Code.INVALID_TARGET, e.code)
        assertEquals(Toolkit.REASON_CONTAINER, e.reason)
        b.root = null
        assertNull(kit.resolveContainer(null, null, "tool"))
    }

}
