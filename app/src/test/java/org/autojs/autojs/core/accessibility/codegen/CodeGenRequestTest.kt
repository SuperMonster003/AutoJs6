package org.autojs.autojs.core.accessibility.codegen

import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Action
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Attr
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Error
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Mode
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Receiver
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Scope
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Style
import org.autojs.autojs.core.automator.FakeUiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeGenRequestTest {

    private val first = FakeUiNode.root("first")
    private val second = FakeUiNode.root("second")

    @Test
    fun defaultRequestIsValidSingleTargetWait() {
        val request = CodeGenRequest(listOf(first))
        assertEquals(Scope.SINGLE, request.scope)
        assertEquals(Mode.WAIT, request.mode)
        assertEquals(Action.NONE, request.action)
        assertEquals(Receiver.AUTO, request.receiver)
        assertEquals(Attr.entries.toSet(), request.attributes)
        assertEquals(Style.CLASSIC, request.style)
        assertTrue(request.validate().isEmpty())
    }

    @Test
    fun emptyTargetsAreReportedForBothScopes() {
        for (scope in Scope.entries) {
            assertEquals(listOf(Error.EMPTY_TARGETS), CodeGenRequest<FakeUiNode>(emptyList(), scope).validate())
        }
    }

    @Test
    fun singleScopeRejectsMoreThanOneTarget() {
        val request = CodeGenRequest(listOf(first, second))
        assertEquals(listOf(Error.SINGLE_REQUIRES_ONE_TARGET), request.validate())
        assertTrue(request.copy(scope = Scope.COLLECTION).validate().isEmpty())
    }

    @Test
    fun timeoutIsBoundedAndBothEndpointsAreAccepted() {
        val request = CodeGenRequest(listOf(first))
        for (millis in listOf(Long.MIN_VALUE, -1L, 0L, 99L, 600_001L, Long.MAX_VALUE)) {
            assertEquals(listOf(Error.TIMEOUT_OUT_OF_RANGE), request.copy(mode = Mode.WAIT_TIMEOUT(millis)).validate())
        }
        for (millis in listOf(100L, CodeGenRequest.DEFAULT_TIMEOUT_MS, 600_000L)) {
            assertTrue(request.copy(mode = Mode.WAIT_TIMEOUT(millis)).validate().isEmpty())
        }
    }

    @Test
    fun automaticSetTextRequiresEveryTargetToBeEditable() {
        first.editable = true
        val request = CodeGenRequest(listOf(first, second), Scope.COLLECTION, action = Action.SET_TEXT(""))
        assertEquals(listOf(Error.TARGET_NOT_EDITABLE), request.validate())
        second.editable = true
        assertTrue(request.validate().isEmpty())
    }

    @Test
    fun explicitSelfReceiverAllowsUnsupportedSetText() {
        assertTrue(CodeGenRequest(listOf(first), action = Action.SET_TEXT("a\"b\\c"), receiver = Receiver.SELF).validate().isEmpty())
    }

    @Test
    fun validationCollectsIndependentErrorsWithoutThrowing() {
        val request = CodeGenRequest(listOf(first, second), mode = Mode.WAIT_TIMEOUT(99), action = Action.SET_TEXT("hello"))
        assertEquals(listOf(Error.SINGLE_REQUIRES_ONE_TARGET, Error.TIMEOUT_OUT_OF_RANGE, Error.TARGET_NOT_EDITABLE), request.validate())
    }

    @Test
    fun findOneMapsToSingleWait() {
        assertEquals(CodeGenRequest(listOf(first)), LegacyMode.FIND_ONE.toRequest(first))
    }

    @Test
    fun untilFindUsesTheFirstSuggestedCollection() {
        val targets = listOf(first, second)
        assertEquals(CodeGenRequest(targets, Scope.COLLECTION), LegacyMode.UNTIL_FIND.toRequest(first, listOf(targets, listOf(second))))
        assertEquals(listOf(Error.EMPTY_TARGETS), LegacyMode.UNTIL_FIND.toRequest(first).validate())
    }

    @Test
    fun waitForPreservesTheSelectedAction() {
        val request = LegacyMode.WAIT_FOR.toRequest(first, action = Action.CLICK)
        assertEquals(CodeGenRequest(listOf(first), action = Action.CLICK), request)
    }

    @Test
    fun existsMapsToImmediateCheckAndRetainsEveryOtherOption() {
        val request = LegacyMode.EXISTS.toRequest(first, action = Action.SET_TEXT("a\"b\\c"), receiver = Receiver.SELF, attributes = setOf(Attr.ID), style = Style.FLOW)
        assertEquals(CodeGenRequest(listOf(first), mode = Mode.CHECK, action = Action.SET_TEXT("a\"b\\c"), receiver = Receiver.SELF, attributes = setOf(Attr.ID), style = Style.FLOW), request)
    }

    @Test
    fun missingLegacyChoiceMapsToTheOriginalDefault() {
        assertEquals(CodeGenRequest(listOf(first)), LegacyMode.NONE.toRequest(first))
    }
}
