package org.autojs.plugin.compose.api

import org.autojs.plugin.compose.api.model.*
import org.junit.Assert.*
import org.junit.Test

class ComposeUiModelTest {
    private fun fails(code: String, block: () -> Unit) {
        try { block(); fail("Expected $code") } catch (failure: ComposeUiContractException) { assertEquals(code, failure.code) }
    }
    @Test fun allCollectionsAreDetachedAndCannotBeMutatedThroughJavaViews() {
        val props = linkedMapOf<String, UiValue>("text" to UiValue.Str("first"))
        val children = mutableListOf(2)
        val arguments = mutableListOf<UiValue>(UiValue.Dp(8.0))
        val modifiers = mutableListOf(ModifierOp("padding", arguments))
        val node = UiNode(1, "Column", props = props, children = children, modifier = modifiers)
        props.clear(); children.clear(); arguments.clear(); modifiers.clear()
        assertEquals(UiValue.Str("first"), node.props["text"])
        assertEquals(listOf(2), node.children)
        assertEquals(listOf(UiValue.Dp(8.0)), node.modifier.single().args)
        try { (node.children as MutableList).clear(); fail("Mutable children") } catch (_: UnsupportedOperationException) {}
        try { (node.props as MutableMap).clear(); fail("Mutable props") } catch (_: UnsupportedOperationException) {}
        val list = mutableListOf<UiValue>(UiValue.Str("nested"))
        val value = UiValue.ListOf(list); list.clear()
        assertEquals(1, value.values.size)
    }
    @Test fun subtreeRejectsCyclesOrphansDuplicatesAndDuplicateSiblingKeys() {
        fails(ComposeUiErrorCodes.INVALID_ARGUMENT) { UiTree(1, listOf(UiNode(1, "Column", children = listOf(1)))) }
        fails(ComposeUiErrorCodes.INVALID_ARGUMENT) { UiTree(1, listOf(UiNode(1, "Text"), UiNode(2, "Text"))) }
        fails(ComposeUiErrorCodes.INVALID_ARGUMENT) { UiTree(1, listOf(UiNode(1, "Column", children = listOf(2)), UiNode(2, "Text", slots = mapOf("content" to 1)))) }
        fails(ComposeUiErrorCodes.INVALID_ARGUMENT) { UiTree(1, listOf(UiNode(1, "Text"), UiNode(1, "Text"))) }
        fails(ComposeUiErrorCodes.DUPLICATE_KEY) { UiTree(1, listOf(UiNode(1, "Column", children = listOf(2, 3)), UiNode(2, "Text", key = "same"), UiNode(3, "Text", key = "same"))) }
    }
    @Test fun structuralLimitsAcceptBoundaryAndRejectOneOver() {
        UiValue.Str("a".repeat(ComposeUiLimits.MAX_STRING_CHARS))
        fails(ComposeUiErrorCodes.LIMIT_EXCEEDED) { UiValue.Str("a".repeat(ComposeUiLimits.MAX_STRING_CHARS + 1)) }
        val chain = (1..64).map { UiNode(it, "Column", children = if (it == 64) emptyList() else listOf(it + 1)) }
        UiTree(1, chain)
        fails(ComposeUiErrorCodes.LIMIT_EXCEEDED) { UiTree(1, (1..65).map { UiNode(it, "Column", children = if (it == 65) emptyList() else listOf(it + 1)) }) }
        UiNode(1, "Column", children = List(2000) { it + 2 })
        fails(ComposeUiErrorCodes.LIMIT_EXCEEDED) { UiNode(1, "Column", children = List(2001) { it + 2 }) }
        UiNode(1, "LazyColumn", children = List(10000) { it + 2 })
        fails(ComposeUiErrorCodes.LIMIT_EXCEEDED) { UiNode(1, "LazyColumn", children = List(10001) { it + 2 }) }
        val patch = UiPatch.Remove(1, 2)
        UiPatchBatch(1, 0, List(2000) { patch })
        fails(ComposeUiErrorCodes.LIMIT_EXCEEDED) { UiPatchBatch(1, 0, List(2001) { patch }) }
    }
    @Test fun nestedValuesFiniteNumbersAndEditOffsetsAreValidated() {
        var value: UiValue = UiValue.Str("v")
        repeat(63) { value = UiValue.ListOf(listOf(value)) }
        fails(ComposeUiErrorCodes.LIMIT_EXCEEDED) { UiValue.ListOf(listOf(value)) }
        fails(ComposeUiErrorCodes.INVALID_ARGUMENT) { UiValue.Num(Double.NaN) }
        fails(ComposeUiErrorCodes.INVALID_ARGUMENT) { UiValue.Dp(Double.MAX_VALUE) }
        UiCommand.Edit(1, "abc", TextSelection(3, 1), 7)
        fails(ComposeUiErrorCodes.INVALID_ARGUMENT) { UiCommand.Edit(1, "abc", TextSelection(4, 0), 7) }
        fails(ComposeUiErrorCodes.INVALID_ARGUMENT) { UiCommand.ScrollTo(1) }
        fails(ComposeUiErrorCodes.INVALID_ARGUMENT) { UiPatchBatch(1, -1, emptyList()) }
        validateBitmapPixels(8000, 4000)
        fails(ComposeUiErrorCodes.LIMIT_EXCEEDED) { validateBitmapPixels(8001, 4000) }
        fails(ComposeUiErrorCodes.LIMIT_EXCEEDED) { validateBitmapPixels(Int.MAX_VALUE, Int.MAX_VALUE) }
    }
    @Test fun sessionNodeModifierAndUniqueCallbackBudgetsAreEnforced() {
        val leafs = (2..5000).map { UiNode(it, "Text", callbacks = mapOf("click" to 1)) }
        UiTree(1, listOf(UiNode(1, "LazyColumn", children = leafs.map { it.nodeId })) + leafs)
        fails(ComposeUiErrorCodes.LIMIT_EXCEEDED) { UiTree(1, listOf(UiNode(1, "LazyColumn", children = leafs.map { it.nodeId })) + leafs + UiNode(5001, "Text")) }
        val op = ModifierOp("alpha", listOf(UiValue.Num(1.0)))
        UiNode(1, "Text", modifier = List(64) { op })
        fails(ComposeUiErrorCodes.LIMIT_EXCEEDED) { UiNode(1, "Text", modifier = List(65) { op }) }
        val callbacks = (2..4098).map { UiNode(it, "Text", callbacks = mapOf("click" to it)) }
        fails(ComposeUiErrorCodes.LIMIT_EXCEEDED) { UiTree(1, listOf(UiNode(1, "LazyColumn", children = callbacks.map { it.nodeId })) + callbacks) }
        fails(ComposeUiErrorCodes.UNKNOWN_PROP) { ThemeSpec(colorOverrides = mapOf("unknown" to 0)) }
    }
}
