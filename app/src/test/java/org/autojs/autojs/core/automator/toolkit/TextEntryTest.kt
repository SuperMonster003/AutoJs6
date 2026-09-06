package org.autojs.autojs.core.automator.toolkit

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.flow.FlowError
import org.autojs.autojs.core.automator.toolkit.TextEntry.Options
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Text entry (roadmap P4.1): the three ways of naming the field, replace / append, verification
 * by re-reading, password fields, submitting, and every failure.
 * zh-CN: 文本输入 (Roadmap P4.1): 指定输入框的三种方式, 替换 / 追加, 重读校验, 密码框, 提交, 以及各种失败.
 */
class TextEntryTest {

    private val rig = ToolkitRig()
    private val kit = rig.kit
    private val actions = rig.actions
    private val b = rig.bindings

    //        root
    //         |
    //        form
    //     /   |   \    \
    //  name  pass label row
    //                    |
    //                  inner
    private val root = FakeUiNode.root {
        child("form") {
            child("name") { editable = true }
            child("pass") {
                editable = true
                password = true
            }
            child("label") { text = "Name:" }
            child("row") {
                child("inner") { editable = true }
            }
        }
    }

    private fun node(name: String) = root.find(name)

    init {
        b.root = root
        listOf("name", "pass", "label", "row").forEach { b.show(it, node(it)) }
    }

    private fun typeInto(target: Any?, text: String, options: Options = Options()) = kit.textEntry.typeInto(target, text, options)

    private fun error(block: () -> Unit): FlowError = assertThrows(FlowError::class.java) { block() }

    @Test
    fun replacesTheTextAndVerifiesIt() {
        node("name").text = "old"
        val outcome = typeInto("name", "Ann")
        assertSame(node("name"), outcome.node)
        assertEquals("Ann", outcome.text)
        assertFalse(outcome.submitted)
        assertEquals("Ann", node("name").text)
        assertEquals(listOf("setText(name, Ann)"), actions.calls)
        assertEquals(1, b.refreshes)
        assertTrue(rig.sleeps.isEmpty())
    }

    @Test
    fun appendsWhenClearIsOff() {
        node("name").text = "A"
        val outcome = typeInto("name", "nn", Options(clear = false))
        assertEquals("Ann", outcome.text)
        assertEquals(listOf("setText(name, Ann)"), actions.calls)
    }

    @Test
    fun indexTargetPicksTheNthEditableOfTheScope() {
        assertSame(node("pass"), typeInto(1, "x").node)
        assertSame(node("inner"), typeInto(-1, "x").node)
        assertSame(node("name"), typeInto(0, "x").node)
    }

    @Test
    fun missingIndexIsInvalidTarget() {
        val e = error { typeInto(3, "x") }
        assertEquals(FlowError.Code.INVALID_TARGET, e.code)
        assertEquals(TextEntry.REASON_NO_EDITABLE, e.reason)
        assertEquals("editable[3]", e.selector)
    }

    @Test
    fun nullTargetUsesTheFocusedEditable() {
        node("pass").focused = true
        assertSame(node("pass"), typeInto(null, "x").node)
    }

    @Test
    fun nullTargetWithoutFocusIsInvalidTarget() {
        val e = error { typeInto(null, "x") }
        assertEquals(TextEntry.REASON_NO_FOCUS, e.reason)
        assertEquals(TextEntry.DESCRIPTION_FOCUSED, e.selector)
    }

    @Test
    fun nonEditableMatchUsesItsEditableDescendant() {
        assertSame(node("inner"), typeInto("row", "x").node)
    }

    @Test
    fun matchWithoutEditableDescendantIsInvalidTarget() {
        val e = error { typeInto("label", "x") }
        assertEquals(FlowError.Code.INVALID_TARGET, e.code)
        assertEquals(TextEntry.REASON_NOT_EDITABLE, e.reason)
        assertEquals("text(\"label\")", e.selector)
    }

    @Test
    fun selectorTargetIsWaitedForPerLocate() {
        val late = FakeUiNode.root("late") { editable = true }
        rig.appearing("late", late, from = 2)
        assertSame(late, typeInto("late", "x", Options(locate = Toolkit.Locate(timeout = 1000))).node)
        assertEquals(listOf(50L), rig.sleeps)

        val e = error { typeInto("never", "x") }
        assertEquals(FlowError.Code.TIMEOUT, e.code)
        assertEquals(1, e.attempts)
    }

    @Test
    fun refusedSetTextIsActionFailed() {
        actions.setTextResult = { _, _ -> false }
        val e = error { typeInto("name", "x") }
        assertEquals(FlowError.Code.ACTION_FAILED, e.code)
        assertEquals(TextEntry.REASON_SET_TEXT, e.reason)
        assertEquals(TextEntry.STEP, e.step)
    }

    @Test
    fun verifyReadsBackUntilTheTextShows() {
        var pending = ""
        actions.setTextResult = { _, text ->
            pending = text
            true
        }
        b.onRefresh = { if (b.refreshes == 2) it.text = pending }
        typeInto("name", "Ann", Options(verifyInterval = 100))
        assertEquals("Ann", node("name").text)
        assertEquals(listOf(100L), rig.sleeps)
    }

    @Test
    fun verifyTimingOutIsActionFailed() {
        actions.setTextResult = { _, _ -> true }
        val e = error { typeInto("name", "Ann", Options(verifyTimeout = 300, verifyInterval = 100)) }
        assertEquals(FlowError.Code.ACTION_FAILED, e.code)
        assertEquals(TextEntry.REASON_VERIFY, e.reason)
        assertEquals(400, e.elapsed)
        assertEquals(5, e.attempts)
    }

    @Test
    fun verifyCanBeSwitchedOff() {
        actions.setTextResult = { _, _ -> true }
        typeInto("name", "Ann", Options(verify = false))
        assertEquals(0, b.refreshes)
    }

    @Test
    fun passwordFieldsAreNotVerified() {
        actions.setTextResult = { _, _ -> true }
        val outcome = typeInto("pass", "secret")
        assertEquals("secret", outcome.text)
        assertEquals(0, b.refreshes)
    }

    @Test
    fun submitFocusesWhenNeededAndSendsTheImeAction() {
        val outcome = typeInto("name", "Ann", Options(submit = true))
        assertTrue(outcome.submitted)
        assertEquals(listOf("setText(name, Ann)", "focus(name)", "imeEnter(name)"), actions.calls)

        actions.calls.clear()
        node("name").focused = true
        typeInto("name", "Ann", Options(submit = true))
        assertEquals(listOf("setText(name, Ann)", "imeEnter(name)"), actions.calls)
    }

    @Test
    fun refusedImeActionIsActionFailed() {
        actions.imeEnterResult = { false }
        val e = error { typeInto("name", "Ann", Options(submit = true)) }
        assertEquals(TextEntry.REASON_SUBMIT, e.reason)
    }

    @Test
    fun humanizedDelayIsSleptBeforeTyping() {
        typeInto("name", "Ann", Options(humanize = Humanize(minDelay = 30, maxDelay = 30)))
        assertEquals(listOf(30L), rig.sleeps)
    }

}
