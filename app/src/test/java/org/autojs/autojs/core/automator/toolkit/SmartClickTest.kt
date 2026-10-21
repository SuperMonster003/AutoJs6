package org.autojs.autojs.core.automator.toolkit

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.flow.FlowError
import org.autojs.autojs.core.automator.toolkit.SmartClick.Method
import org.autojs.autojs.core.automator.toolkit.SmartClick.Options
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The decision paths of the smart click (roadmap P4.1): node, ancestor, gesture, the failures of
 * each, offsets, humanizing and verification.
 * zh-CN: 智能点击 (Roadmap P4.1) 的各决策路径: 节点, 祖先, 手势, 各自的失败, 偏移, 拟人与校验.
 */
class SmartClickTest {

    private val rig = ToolkitRig()
    private val kit = rig.kit
    private val actions = rig.actions
    private val b = rig.bindings

    //        root
    //      /   |    \
    //   row  button  plain
    //    |
    //  cell
    //    |
    //  label
    private val root = FakeUiNode.root {
        child("row", clickable = true) {
            bounds(0, 0, 1000, 200)
            child("cell") {
                child("label") { bounds(100, 50, 300, 150) }
            }
        }
        child("button", clickable = true) { bounds(0, 300, 200, 400) }
        child("plain") { bounds(0, 500, 200, 600) }
    }

    private fun node(name: String) = root.find(name)

    private fun click(name: String, options: Options = Options(), selector: String? = null) = kit.smartClick.click(node(name), options, selector = selector)

    private fun error(block: () -> Unit): FlowError = assertThrows(FlowError::class.java) { block() }

    @Test fun gestureReadsTheRefreshedRectangleInsteadOfCachedWindowAnimationBounds() {
        b.onRefresh = { it.bounds(40, 100, 240, 200) }
        click("button", Options(boundsOnly = true))
        assertEquals(listOf("tap(140, 150)"), actions.calls)
    }

    @Test
    fun clickableNodeIsClickedDirectly() {
        val outcome = click("button")
        assertEquals(Method.NODE, outcome.method)
        assertSame(node("button"), outcome.target)
        assertNull(outcome.verified)
        assertEquals(listOf("click(button)"), actions.calls)
        assertTrue(rig.sleeps.isEmpty())
    }

    @Test
    fun boundsOnlyBypassesSuccessfulNodeAndAncestorActions() {
        val options = Options(boundsOnly = true, offsetX = -10, offsetY = 5)
        val button = click("button", options)
        val label = click("label", options)
        assertEquals(Method.GESTURE, button.method)
        assertSame(node("button"), button.target)
        assertEquals(Method.GESTURE, label.method)
        assertSame(node("label"), label.target)
        assertEquals(listOf("tap(90, 355)", "tap(190, 105)"), actions.calls)
    }

    @Test
    fun boundsOnlyStillVerifiesAndReportsGestureFailuresWithoutNodeFallback() {
        val options = Options(boundsOnly = true, verify = { "confirmed" })
        val result = kit.smartClick.click(node("button"), options, SmartClick.STEP_BOUNDS)
        assertEquals("confirmed", result.verified)
        actions.tapResult = { _, _ -> false }
        val failed = error { kit.smartClick.click(node("button"), options, SmartClick.STEP_BOUNDS) }
        assertEquals(SmartClick.STEP_BOUNDS, failed.step)
        assertEquals(SmartClick.REASON_GESTURE, failed.reason)
        assertEquals(listOf("tap(100, 350)", "tap(100, 350)"), actions.calls)
        actions.tapResult = { _, _ -> true }
        val unverified = error {
            kit.smartClick.click(node("button"), options.copy(verify = { false }, verifyTimeout = 0), SmartClick.STEP_BOUNDS)
        }
        assertEquals(SmartClick.REASON_VERIFY, unverified.reason)
    }

    @Test
    fun nonClickableNodeClimbsToTheNearestClickableAncestor() {
        val outcome = click("label")
        assertEquals(Method.ANCESTOR, outcome.method)
        assertSame(node("row"), outcome.target)
        assertEquals(listOf("click(row)"), actions.calls)
    }

    @Test
    fun climbIsBoundedByMaxClimb() {
        // label -> cell -> row is two levels up.
        val outcome = click("label", Options(maxClimb = 1))
        assertEquals(Method.GESTURE, outcome.method)
        assertEquals(listOf("tap(200, 100)"), actions.calls)

        val e = error { click("label", Options(maxClimb = 1, gestureFallback = false)) }
        assertEquals(FlowError.Code.INVALID_TARGET, e.code)
        assertEquals(SmartClick.REASON_NOT_CLICKABLE, e.reason)
        assertEquals(SmartClick.STEP, e.step)
    }

    @Test
    fun climbDisabledGoesStraightToTheGesture() {
        val outcome = click("label", Options(climb = false))
        assertEquals(Method.GESTURE, outcome.method)
        assertSame(node("label"), outcome.target)
        assertEquals(listOf("tap(200, 100)"), actions.calls)
    }

    @Test
    fun refusedClickFallsBackToTheGesture() {
        actions.clickResult = { false }
        val outcome = click("button")
        assertEquals(Method.GESTURE, outcome.method)
        assertSame(node("button"), outcome.target)
        assertEquals(listOf("click(button)", "tap(100, 350)"), actions.calls)
    }

    @Test
    fun refusedClickWithoutGestureIsActionFailed() {
        actions.clickResult = { false }
        val e = error { click("button", Options(gestureFallback = false)) }
        assertEquals(FlowError.Code.ACTION_FAILED, e.code)
        assertEquals(SmartClick.REASON_CLICK, e.reason)

        val e2 = error { click("label", Options(gestureFallback = false)) }
        assertEquals(SmartClick.REASON_CLICK, e2.reason)
        assertEquals(listOf("click(button)", "click(row)"), actions.calls)
    }

    @Test
    fun nothingClickableWithoutGestureIsInvalidTarget() {
        val e = error { click("plain", Options(gestureFallback = false), selector = "text(\"Plain\")") }
        assertEquals(FlowError.Code.INVALID_TARGET, e.code)
        assertEquals(SmartClick.REASON_NOT_CLICKABLE, e.reason)
        assertEquals("text(\"Plain\")", e.selector)
        assertTrue(actions.calls.isEmpty())
    }

    @Test
    fun gestureRefusedIsActionFailed() {
        actions.tapResult = { _, _ -> false }
        val e = error { click("plain") }
        assertEquals(FlowError.Code.ACTION_FAILED, e.code)
        assertEquals(SmartClick.REASON_GESTURE, e.reason)
        assertEquals(listOf("tap(100, 550)"), actions.calls)
    }

    @Test
    fun gestureTapsTheCentrePlusTheOffsets() {
        click("plain", Options(offsetX = 5, offsetY = -7))
        assertEquals(listOf(105 to 543), actions.taps)
    }

    @Test
    fun humanizedGestureStaysWithinTheOffsetRange() {
        repeat(20) { click("plain", Options(humanize = Humanize(offsetX = 10, offsetY = 3))) }
        assertEquals(20, actions.taps.size)
        actions.taps.forEach { (x, y) ->
            assertTrue("x $x", abs(x - 100) <= 10)
            assertTrue("y $y", abs(y - 550) <= 3)
        }
        assertTrue(actions.taps.any { it.first != 100 })
    }

    @Test
    fun humanizedDelayIsSleptBeforeTheClick() {
        click("button", Options(humanize = Humanize(minDelay = 50, maxDelay = 50)))
        assertEquals(listOf(50L), rig.sleeps)
        assertEquals(listOf("click(button)"), actions.calls)
    }

    @Test
    fun verifyPollsTheConditionAfterTheClick() {
        rig.appearing("done", node("plain"), from = 3)
        val outcome = click("button", Options(verify = "done", verifyInterval = 100))
        assertEquals(Method.NODE, outcome.method)
        assertSame(node("plain"), outcome.verified)
        assertEquals(listOf(100L, 100L), rig.sleeps)
        assertEquals(3, b.finds)
    }

    @Test
    fun verifyTimingOutIsActionFailed() {
        val e = error { click("button", Options(verify = "never", verifyTimeout = 300, verifyInterval = 100)) }
        assertEquals(FlowError.Code.ACTION_FAILED, e.code)
        assertEquals(SmartClick.REASON_VERIFY, e.reason)
        assertEquals("text(\"never\")", e.selector)
        // Attempts at 0, 100, 200, 300 and 400 ms: the timeout is exceeded after the fifth.
        assertEquals(400, e.elapsed)
        assertEquals(5, e.attempts)
        assertEquals(listOf("click(button)"), actions.calls)
    }

    @Test
    fun verifyWithoutTimeoutLooksOnceAndKeepsTheGivenSelector() {
        val e = error { click("button", Options(verify = "never", verifyTimeout = 0), selector = "text(\"Login\")") }
        assertEquals(SmartClick.REASON_VERIFY, e.reason)
        assertEquals("text(\"Login\")", e.selector)
        assertEquals(1, e.attempts)
        assertEquals(0, e.elapsed)
        assertTrue(rig.sleeps.isEmpty())
    }

    @Test
    fun verifyConditionIsProbedUnderTheRoot() {
        rig.appearing("done", node("plain"), from = 1)
        kit.smartClick.click(node("button"), Options(verify = "done"), root = root)
        assertEquals(listOf<Pair<Any?, FakeUiNode?>>("done" to root), b.findLog)
    }

    @Test
    fun optionsAreValidated() {
        assertThrows(IllegalArgumentException::class.java) { Options(maxClimb = -1) }
        assertThrows(IllegalArgumentException::class.java) { Options(verifyTimeout = -1) }
        assertThrows(IllegalArgumentException::class.java) { Options(verifyInterval = -1) }
    }

}
