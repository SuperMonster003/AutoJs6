package org.autojs.autojs.core.automator.toolkit

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.flow.FlowError
import org.autojs.autojs.core.automator.toolkit.PopupDismisser.Options
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Popup dismissal (roadmap P4.1): the candidate order, waiting, repeated passes, and the guard
 * driven by window changes.
 * zh-CN: 弹窗关闭 (Roadmap P4.1): 候选项顺序, 等待, 重复轮次, 以及由窗口变化驱动的守护.
 */
class PopupDismisserTest {

    private val rig = ToolkitRig()
    private val kit = rig.kit
    private val actions = rig.actions
    private val b = rig.bindings

    private val allow = FakeUiNode.root("allow", clickable = true)
    private val agree = FakeUiNode.root("agree", clickable = true)
    private val skip = FakeUiNode.root("skip", clickable = true)

    private val candidates = listOf<Any?>("allow", "agree", "skip")

    /** A click removes the popup from the screen. zh-CN: 点击即把弹窗从屏幕移除. */
    private fun closingOnClick() {
        actions.clickResult = { node ->
            b.show(node.name, null)
            true
        }
    }

    private fun error(block: () -> Unit): FlowError = assertThrows(FlowError::class.java) { block() }

    @Test
    fun dismissOnceClicksTheFirstPresentCandidateInListOrder() {
        b.show("agree", agree)
        b.show("skip", skip)
        val dismissed = kit.popups.dismissOnce(candidates)!!
        assertEquals(1, dismissed.index)
        assertEquals("agree", dismissed.target)
        assertSame(agree, dismissed.node)
        assertEquals(SmartClick.Method.NODE, dismissed.outcome.method)
        assertEquals(listOf("click(agree)"), actions.calls)
    }

    @Test
    fun dismissOnceIsNullWhenNothingIsPresent() {
        assertNull(kit.popups.dismissOnce(candidates))
        assertTrue(actions.calls.isEmpty())
        assertEquals(3, b.finds)
    }

    @Test
    fun dismissOnceWaitsPerLocate() {
        rig.appearing("skip", skip, from = 2)
        val dismissed = kit.popups.dismissOnce(candidates, Options(locate = Toolkit.Locate(timeout = 500)))!!
        assertSame(skip, dismissed.node)
        assertEquals(listOf(50L), rig.sleeps)
    }

    @Test
    fun aFailedClickPropagates() {
        b.show("allow", allow)
        actions.clickResult = { false }
        val e = error { kit.popups.dismissOnce(candidates, Options(click = SmartClick.Options(gestureFallback = false))) }
        assertEquals(FlowError.Code.ACTION_FAILED, e.code)
        assertEquals(PopupDismisser.STEP, e.step)
        assertEquals("text(\"allow\")", e.selector)
    }

    @Test
    fun dismissAllRepeatsUntilNothingRemains() {
        b.show("agree", agree)
        b.show("skip", skip)
        closingOnClick()
        val dismissed = kit.popups.dismissAll(candidates)
        assertEquals(listOf("agree", "skip"), dismissed.map { it.target })
        assertEquals(listOf("click(agree)", "click(skip)"), actions.calls)
        assertEquals(listOf(300L, 300L), rig.sleeps)
    }

    @Test
    fun dismissAllIsBoundedByMaxRounds() {
        b.show("allow", allow)
        val dismissed = kit.popups.dismissAll(candidates, Options(maxRounds = 3, settleMillis = 0))
        assertEquals(3, dismissed.size)
        assertEquals(3, actions.calls("click("))
        assertTrue(rig.sleeps.isEmpty())
    }

    @Test
    fun dismissAllWaitsOnlyForTheFirstPopup() {
        rig.appearing("allow", allow, from = 3)
        closingOnClick()
        val dismissed = kit.popups.dismissAll(candidates, Options(locate = Toolkit.Locate(timeout = 1000), settleMillis = 0))
        assertEquals(1, dismissed.size)
        // Two polls of 50 ms before the popup showed, then single looks.
        assertEquals(listOf(50L, 50L), rig.sleeps)
    }

    @Test
    fun guardMakesAPassAtStartAndOnEveryWindowChange() {
        b.show("allow", allow)
        closingOnClick()
        val guard = kit.popups.watch(candidates, Options(settleMillis = 0, debounceMillis = 250))
        val watch = b.watches.single()
        assertEquals(250, watch.debounceMillis)
        assertEquals(1, watch.triggers)
        assertTrue(guard.isRunning)
        assertEquals(1, guard.passes)
        assertEquals(listOf("allow"), guard.dismissed.map { it.target })

        b.show("skip", skip)
        watch.change()
        assertEquals(2, guard.passes)
        assertEquals(2, guard.count)
        assertEquals("skip", guard.dismissed.last().target)
        assertEquals(listOf("click(allow)", "click(skip)"), actions.calls)
    }

    @Test
    fun guardReportsEveryDismissal() {
        b.show("allow", allow)
        closingOnClick()
        val seen = ArrayList<String>()
        val guard = kit.popups.watch(candidates, Options(settleMillis = 0))
        guard.onDismissed = { seen += it.node.name }
        b.show("agree", agree)
        b.watches.single().change()
        assertEquals(listOf("agree"), seen)
        assertEquals(2, guard.count)
    }

    @Test
    fun stoppedGuardIgnoresLaterChanges() {
        val guard = kit.popups.watch(candidates)
        val watch = b.watches.single()
        guard.stop()
        assertFalse(guard.isRunning)
        assertTrue(watch.stopped)
        b.show("allow", allow)
        watch.change()
        guard.trigger()
        assertEquals(1, guard.passes)
        assertEquals(0, guard.count)
    }

    @Test
    fun guardReportsErrorsAndKeepsRunning() {
        b.show("allow", allow)
        actions.clickResult = { false }
        val errors = ArrayList<Throwable>()
        val guard = kit.popups.watch(candidates, Options(click = SmartClick.Options(gestureFallback = false)))
        guard.onError = { errors += it }
        b.watches.single().change()
        assertEquals(1, errors.size)
        assertTrue(errors.all { it is FlowError.ActionFailed })
        assertTrue(guard.isRunning)
        assertEquals(0, guard.count)
    }

    @Test
    fun guardStopsOnAFatalError() {
        rig.fatal = { it is IllegalStateException }
        var interrupted = false
        b.show("allow") { if (interrupted) throw IllegalStateException("script stopped") else null }
        val guard = kit.popups.watch(candidates)
        assertTrue(guard.isRunning)
        val watch = b.watches.single()
        interrupted = true
        assertThrows(IllegalStateException::class.java) { watch.change() }
        assertFalse(guard.isRunning)
        assertTrue(watch.stopped)
    }

    @Test
    fun guardDoesNotOverlapPasses() {
        actions.clickResult = { node ->
            // A window change reported while the pass is still clicking: no second pass starts.
            b.watches.singleOrNull()?.change()
            b.show(node.name, null)
            true
        }
        b.show("allow", allow)
        val guard = kit.popups.watch(candidates, Options(settleMillis = 0))
        assertEquals(1, guard.passes)
        b.show("skip", skip)
        b.watches.single().change()
        assertEquals(2, guard.passes)
        assertEquals(listOf("allow", "skip"), guard.dismissed.map { it.target })
    }

    @Test
    fun watchNeedsCandidates() {
        assertThrows(IllegalArgumentException::class.java) { kit.popups.watch(emptyList()) }
    }

}
