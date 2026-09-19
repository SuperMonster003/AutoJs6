package org.autojs.autojs.core.automator.toolkit

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.flow.FlowError
import org.autojs.autojs.core.automator.toolkit.Toggle.Options
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The checkable toggle (roadmap P4.1): the no-op case, the click through the row, verification
 * and its failures.
 * zh-CN: 可勾选开关 (Roadmap P4.1): 无需操作的情况, 经由行的点击, 校验及其失败.
 */
class ToggleTest {

    private val rig = ToolkitRig()
    private val kit = rig.kit
    private val actions = rig.actions
    private val b = rig.bindings

    //     root
    //    /    \
    //  row   label
    //   |
    // switch
    private val root = FakeUiNode.root {
        child("row", clickable = true) {
            child("switch") { checkable = true }
        }
        child("label")
    }

    private val row = root.find("row")

    private val switch = root.find("switch")

    init {
        b.root = root
        b.show("row", row)
        b.show("label", root.find("label"))
        actions.clickResult = {
            switch.checked = !switch.checked
            true
        }
    }

    private fun error(block: () -> Unit): FlowError = assertThrows(FlowError::class.java) { block() }

    @Test
    fun matchingStateIsLeftAlone() {
        val outcome = kit.toggle.set(switch, false)
        assertFalse(outcome.changed)
        assertFalse(outcome.checked)
        assertSame(switch, outcome.node)
        assertNull(outcome.click)
        assertTrue(actions.calls.isEmpty())
    }

    @Test
    fun clicksThroughTheRowAndVerifiesTheState() {
        val outcome = kit.toggle.set(switch, true)
        assertTrue(outcome.changed)
        assertTrue(outcome.checked)
        assertTrue(switch.checked)
        assertEquals(SmartClick.Method.ANCESTOR, outcome.click!!.method)
        assertSame(row, outcome.click.target)
        assertEquals(listOf("click(row)"), actions.calls)
        assertTrue(rig.sleeps.isEmpty())
    }

    @Test
    fun rowTargetUsesItsCheckableDescendant() {
        val outcome = kit.toggle.set("row", true)
        assertSame(switch, outcome.node)
        assertTrue(switch.checked)
    }

    @Test
    fun uncheckingWorksTheSameWay() {
        switch.checked = true
        assertTrue(kit.toggle.set("row", false).changed)
        assertFalse(switch.checked)
    }

    @Test
    fun notCheckableIsInvalidTarget() {
        val e = error { kit.toggle.set("label", true) }
        assertEquals(FlowError.Code.INVALID_TARGET, e.code)
        assertEquals(Toggle.REASON_NOT_CHECKABLE, e.reason)
        assertEquals("text(\"label\")", e.selector)
        assertEquals(Toggle.STEP, e.step)
    }

    @Test
    fun missingTargetTimesOut() {
        val e = error { kit.toggle.set("nope", true) }
        assertEquals(FlowError.Code.TIMEOUT, e.code)
        assertEquals(Toggle.STEP, e.step)
    }

    @Test
    fun verifyWaitsForTheStateToChange() {
        actions.clickResult = { true }
        b.onRefresh = { if (b.refreshes == 2) switch.checked = true }
        kit.toggle.set(switch, true)
        assertEquals(listOf(100L), rig.sleeps)
    }

    @Test
    fun stateNotChangingIsActionFailed() {
        actions.clickResult = { true }
        val e = error { kit.toggle.set(switch, true, Options(verifyTimeout = 200, verifyInterval = 100)) }
        assertEquals(FlowError.Code.ACTION_FAILED, e.code)
        assertEquals(Toggle.REASON_VERIFY, e.reason)
        assertEquals("switch", e.selector)
        assertEquals(300, e.elapsed)
        assertEquals(4, e.attempts)
    }

    @Test
    fun clickFailuresPropagateWithTheToggleStep() {
        actions.clickResult = { false }
        val e = error { kit.toggle.set(switch, true, Options(click = SmartClick.Options(gestureFallback = false))) }
        assertEquals(FlowError.Code.ACTION_FAILED, e.code)
        assertEquals(SmartClick.REASON_CLICK, e.reason)
        assertEquals(Toggle.STEP, e.step)
    }

    @Test
    fun locateWaitsForTheTarget() {
        rig.appearing("late", switch, from = 2)
        kit.toggle.set("late", true, Options(locate = Toolkit.Locate(timeout = 500)))
        assertEquals(listOf(50L), rig.sleeps)
    }

}
