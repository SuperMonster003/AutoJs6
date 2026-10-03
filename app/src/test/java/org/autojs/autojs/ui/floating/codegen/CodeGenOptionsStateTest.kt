package org.autojs.autojs.ui.floating.codegen

import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Action
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Attr
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Mode
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Receiver
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Scope
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Style
import org.autojs.autojs.core.accessibility.codegen.TargetSets
import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.ui.floating.codegen.CodeGenOptionsState.ActionChoice
import org.autojs.autojs.ui.floating.codegen.CodeGenOptionsState.Error
import org.autojs.autojs.ui.floating.codegen.CodeGenOptionsState.Invalid
import org.autojs.autojs.ui.floating.codegen.CodeGenOptionsState.Query
import org.autojs.autojs.ui.floating.codegen.CodeGenOptionsState.Ready
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeGenOptionsStateTest {
    private val root = FakeUiNode.root {
        child("first") { id = "sample:id/input"; text = "same"; editable = true }
        child("second") { id = "sample:id/input"; editable = true }
        child("third") { id = "sample:id/other"; text = "same"; editable = true }
    }
    private val target = root.find("first")
    private val sets = TargetSets.suggest(root, target)

    @Test
    fun defaultsMatchSingleWaitWithoutAction() {
        val state = CodeGenOptionsState()
        assertEquals(CodeGenRequest(listOf(target)), request(state))
        assertEquals("10000", state.timeoutText)
        assertEquals(0, state.collectionIndex)
    }

    @Test
    fun everyQueryProducesItsOwnRequestMode() {
        assertEquals(Mode.WAIT, request(CodeGenOptionsState(query = Query.WAIT)).mode)
        assertEquals(Mode.WAIT_TIMEOUT(2300), request(CodeGenOptionsState(query = Query.WAIT_TIMEOUT, timeoutText = "2300")).mode)
        assertEquals(Mode.CHECK, request(CodeGenOptionsState(query = Query.CHECK)).mode)
    }

    @Test
    fun everyActionKeepsItsMeaningAndTextParameter() {
        val expected = listOf(Action.NONE, Action.CLICK, Action.LONG_CLICK, Action.SET_TEXT("a\"b\\c\n"), Action.SCROLL_FORWARD, Action.SCROLL_BACKWARD)
        ActionChoice.entries.zip(expected).forEach { (choice, action) ->
            assertEquals(action, request(CodeGenOptionsState(action = choice, actionText = "a\"b\\c\n")).action)
        }
    }

    @Test
    fun emptyAndWhitespaceSetTextAreIntentionalValues() {
        for (text in listOf("", " ", "\n\t")) {
            assertEquals(Action.SET_TEXT(text), request(CodeGenOptionsState(action = ActionChoice.SET_TEXT, actionText = text)).action)
        }
    }

    @Test
    fun selectsTheRequestedCollectionWithoutExpandingIt() {
        assertTrue(sets.size >= 2)
        sets.indices.forEach { index ->
            val request = request(CodeGenOptionsState(scope = Scope.COLLECTION, collectionIndex = index))
            assertEquals(Scope.COLLECTION, request.scope)
            assertEquals(sets[index].nodes, request.targets)
        }
        assertEquals(listOf(target), request(CodeGenOptionsState(collectionIndex = Int.MAX_VALUE)).targets)
    }

    @Test
    fun unavailableCollectionDoesNotSilentlyBecomeSingleTarget() {
        val result = CodeGenOptionsState(scope = Scope.COLLECTION).toRequest(root, target, emptyList()) as Invalid
        assertEquals(listOf(Error.COLLECTION_UNAVAILABLE), result.errors)
    }

    @Test
    fun invalidCollectionIndexHasTypedError() {
        for (index in listOf(-1, sets.size, Int.MAX_VALUE)) {
            val result = CodeGenOptionsState(scope = Scope.COLLECTION, collectionIndex = index).toRequest(root, target, sets) as Invalid
            assertEquals(listOf(Error.COLLECTION_SELECTION_INVALID), result.errors)
        }
    }

    @Test
    fun timeoutRejectsMalformedOverflowAndOutOfRangeInputs() {
        for (text in listOf("", " ", "abc", "99", "600001", "-100", "+100", "1e3", "100.0", "10 000", "9223372036854775808", "１００")) {
            val result = CodeGenOptionsState(query = Query.WAIT_TIMEOUT, timeoutText = text).toRequest(root, target, sets) as Invalid
            assertEquals(text, listOf(Error.TIMEOUT_INVALID), result.errors)
        }
    }

    @Test
    fun timeoutAcceptsBoundsAndSurroundingWhitespace() {
        for ((text, expected) in listOf("100" to 100L, "600000" to 600000L, " 10000 " to 10000L, "00100" to 100L)) {
            assertEquals(Mode.WAIT_TIMEOUT(expected), request(CodeGenOptionsState(query = Query.WAIT_TIMEOUT, timeoutText = text)).mode)
        }
    }

    @Test
    fun inactiveInputsStayRetainedWithoutInvalidatingOtherModes() {
        val state = CodeGenOptionsState(timeoutText = "bad", actionText = "retained")
        assertEquals(Action.NONE, request(state).action)
        assertEquals(Mode.WAIT, request(state).mode)
        assertEquals(Mode.CHECK, request(state.copy(query = Query.CHECK)).mode)
        assertEquals(Action.SET_TEXT("retained"), request(state.copy(action = ActionChoice.SET_TEXT)).action)
        assertEquals("bad", state.copy(query = Query.WAIT_TIMEOUT).timeoutText)
    }

    @Test
    fun propertyCheckboxesAreIndependentAndAllMayBeDisabled() {
        for (mask in 0..7) {
            val attrs = Attr.entries.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()
            assertEquals(attrs, request(CodeGenOptionsState(attributes = attrs)).attributes)
        }
    }

    @Test
    fun receiverAndStyleChoicesArePassedThrough() {
        for (receiver in Receiver.entries) for (style in Style.entries) {
            val request = request(CodeGenOptionsState(receiver = receiver, style = style))
            assertEquals(receiver, request.receiver)
            assertEquals(style, request.style)
        }
    }

    @Test
    fun requestValidationKeepsTheDialogInputAndReturnsTypedErrors() {
        target.editable = false
        val state = CodeGenOptionsState(action = ActionChoice.SET_TEXT, actionText = "retained")
        val result = state.toRequest(root, target, sets) as Invalid
        assertEquals(listOf(CodeGenRequest.Error.TARGET_NOT_EDITABLE), result.requestErrors)
        assertEquals("retained", state.actionText)
        assertEquals(Action.SET_TEXT("retained"), request(state.copy(receiver = Receiver.SELF)).action)
    }

    @Test
    fun foreignTargetAndSeveralInputErrorsAreReportedTogether() {
        val foreign = FakeUiNode.root("foreign")
        val result = CodeGenOptionsState(scope = Scope.COLLECTION, query = Query.WAIT_TIMEOUT, timeoutText = "?")
            .toRequest(root, foreign, emptyList()) as Invalid
        assertEquals(listOf(Error.TARGET_OUTSIDE_ROOT, Error.COLLECTION_UNAVAILABLE, Error.TIMEOUT_INVALID), result.errors)
    }

    @Test
    fun convertedRequestDoesNotRetainMutableAttributeSet() {
        val attrs = mutableSetOf(Attr.ID)
        val request = request(CodeGenOptionsState(attributes = attrs))
        attrs.clear()
        assertEquals(setOf(Attr.ID), request.attributes)
    }

    private fun request(state: CodeGenOptionsState): CodeGenRequest<FakeUiNode> = (state.toRequest(root, target, sets) as Ready).request
}
