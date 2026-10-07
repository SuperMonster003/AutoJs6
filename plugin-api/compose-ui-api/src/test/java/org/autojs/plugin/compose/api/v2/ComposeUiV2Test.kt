package org.autojs.plugin.compose.api.v2

import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.catalog.ComponentCatalog
import org.autojs.plugin.compose.api.model.*
import org.autojs.plugin.compose.api.interop.AndroidViewInteropV1
import org.autojs.plugin.compose.api.v2.ComposeUiV2.Components as C
import org.autojs.plugin.compose.api.v2.ComposeUiV2.Props as P
import org.junit.Assert.*
import org.junit.Test

class ComposeUiV2Test {
    @Test fun appendOnlyCatalogRetainsTheExactV1AndInteropObjects() {
        assertEquals(55, ComposeUiV2.catalog.components.size)
        assertEquals(56, ComposeUiV2.interopCatalog.components.size)
        assertEquals(25, ComposeUiV2.componentNames.size)
        assertEquals(5322L, ComposeUiV2.REQUIRED_HOST_VERSION_CODE)
        ComponentCatalog.V1.components.forEach { assertSame(it, ComposeUiV2.catalog.component(it.name)) }
        ComponentCatalog.V1.modifiers.forEach { assertSame(it, ComposeUiV2.catalog.modifier(it.name)) }
        assertSame(AndroidViewInteropV1.component, ComposeUiV2.interopCatalog.component(AndroidViewInteropV1.COMPONENT))
        assertNull(ComponentCatalog.V1.component(C.DATE_PICKER))
        assertNull(AndroidViewInteropV1.catalog.component(C.DATE_PICKER))
        assertEquals(55, ComposeUiV2.catalog.components.map { it.name }.distinct().size)
        val focusable = setOf(C.NAVIGATION_BAR_ITEM, C.NAVIGATION_RAIL_ITEM, C.NAVIGATION_DRAWER_ITEM,
            C.TAB, C.DROPDOWN_MENU_ITEM, C.ASSIST_CHIP, C.FILTER_CHIP, C.INPUT_CHIP,
            C.SEGMENTED_BUTTON_ITEM, C.FLOATING_ACTION_BUTTON, C.SEARCH_BAR)
        assertEquals(focusable, ComposeUiV2.catalog.components.filter {
            it.name in ComposeUiV2.componentNames && ComposeUiCommands.FOCUS in it.commands
        }.map { it.name }.toSet())
        focusable.forEach { assertEquals(listOf(ComposeUiCommands.FOCUS, ComposeUiCommands.BLUR),
            ComposeUiV2.catalog.requireComponent(it).commands) }
    }

    @Test fun dateRangeAndUtcMidnightAreValidatedTogether() {
        val defaults = UiNode(1, C.DATE_PICKER)
        ComposeUiV2.validateNode(defaults)
        ComposeUiV2.validateNode(UiNode(1, C.DATE_PICKER, props = mapOf(P.SELECTED_DATE_MILLIS to UiValue.Null)))
        ComposeUiV2.validateNode(UiNode(1, C.DATE_PICKER, props = mapOf(P.SELECTED_DATE_MILLIS to UiValue.Num(0.0))))
        for (props in listOf(
            mapOf(P.YEAR_START to UiValue.Num(2027.0), P.YEAR_END to UiValue.Num(2026.0)),
            mapOf(P.SELECTED_DATE_MILLIS to UiValue.Num(1.0)),
            mapOf(P.SELECTED_DATE_MILLIS to UiValue.Num(0.0), P.YEAR_START to UiValue.Num(2000.0)),
            mapOf(P.YEAR_START to UiValue.Num(0.0)),
            mapOf(P.YEAR_START to UiValue.Num(1582.0)),
            mapOf(P.YEAR_END to UiValue.Num(10000.0)),
        )) fails { ComposeUiV2.validateNode(UiNode(1, C.DATE_PICKER, props = props)) }
        for ((year, millis) in listOf(1583 to -12212553600000.0, 9999 to 253402214400000.0)) {
            ComposeUiV2.validateNode(UiNode(1, C.DATE_PICKER, props = mapOf(P.YEAR_START to UiValue.Num(year.toDouble()),
                P.YEAR_END to UiValue.Num(year.toDouble()), P.SELECTED_DATE_MILLIS to UiValue.Num(millis))))
        }
    }

    @Test fun timeGridAndEnumBoundsRejectCoercions() {
        ComposeUiV2.validateNode(UiNode(1, C.TIME_PICKER, props = mapOf(P.HOUR to UiValue.Num(23.0), P.MINUTE to UiValue.Num(59.0))))
        for ((key, value) in listOf(P.HOUR to -1.0, P.HOUR to 24.0, P.MINUTE to 60.0, P.MINUTE to 0.5)) {
            fails { ComposeUiV2.validateNode(UiNode(1, C.TIME_PICKER, props = mapOf(key to UiValue.Num(value)))) }
        }
        for (columns in listOf(0.0, 65.0, 1.5)) fails {
            ComposeUiV2.validateNode(UiNode(1, C.LAZY_VERTICAL_GRID, props = mapOf(P.COLUMNS to UiValue.Num(columns))))
        }
        fails { ComposeUiV2.validateNode(UiNode(1, C.TIME_PICKER, props = mapOf(P.DISPLAY_MODE to UiValue.Enum("unknown")))) }
        fails { ComposeUiV2.validateNode(UiNode(1, C.LAZY_VERTICAL_GRID,
            props = mapOf(ComposeUiProps.CONTENT_PADDING to UiValue.ListOf(List(3) { UiValue.Dp(1.0) })))) }
        fails { ComposeUiV2.validateNode(UiNode(1, C.LAZY_VERTICAL_GRID,
            props = mapOf(ComposeUiProps.CONTENT_PADDING to UiValue.Dp(-1.0)))) }
    }

    @Test fun tabsPagesAndScopedItemsValidateTheProspectiveTree() {
        val tab = UiNode(2, C.TAB)
        val row = UiNode(1, C.TAB_ROW, children = listOf(2))
        ComposeUiV2.validateTree(UiTree(1, listOf(row, tab)))
        fails { ComposeUiV2.validateTree(UiTree(1, listOf(UiNode(1, C.TAB_ROW,
            props = mapOf(P.SELECTED_INDEX to UiValue.Num(1.0)), children = listOf(2)), tab))) }
        fails { ComposeUiV2.validateNode(UiNode(1, C.TAB_ROW)) }
        fails(ComposeUiErrorCodes.SCOPE_MISMATCH) { ComposeUiV2.validateTree(UiTree(2, listOf(tab))) }
        ComposeUiV2.validateTree(UiTree(2, listOf(tab)), allowDetachedRoot = true)
        fails(ComposeUiErrorCodes.SCOPE_MISMATCH) {
            ComposeUiV2.validateTree(UiTree(1, listOf(UiNode(1, C.NAVIGATION_RAIL, children = listOf(2),
                slots = mapOf(ComposeUiV2.Slots.HEADER to 4)),
                UiNode(2, C.NAVIGATION_RAIL_ITEM, slots = mapOf(ComposeUiSlots.ICON to 3)), UiNode(3, ComposeUiComponents.TEXT),
                UiNode(4, C.NAVIGATION_RAIL_ITEM, slots = mapOf(ComposeUiSlots.ICON to 5)), UiNode(5, ComposeUiComponents.TEXT))))
        }
        fails { ComposeUiV2.validateTree(UiTree(1, listOf(row, UiNode(2, ComposeUiComponents.TEXT)))) }
        ComposeUiV2.validateTree(UiTree(1, listOf(UiNode(1, C.HORIZONTAL_PAGER, children = listOf(2)), UiNode(2, ComposeUiComponents.TEXT))))
        fails { ComposeUiV2.validateNode(UiNode(1, C.HORIZONTAL_PAGER, props = mapOf(P.PAGE to UiValue.Num(1.0)), children = listOf(2))) }
    }

    @Test fun singleSelectionAndRequiredSlotsAreAtomicTreeRules() {
        val first = UiNode(2, C.SEGMENTED_BUTTON_ITEM, props = mapOf(P.SELECTED to UiValue.Bool(true)), slots = mapOf(ComposeUiSlots.LABEL to 4))
        val second = UiNode(3, C.SEGMENTED_BUTTON_ITEM, props = mapOf(P.SELECTED to UiValue.Bool(true)), slots = mapOf(ComposeUiSlots.LABEL to 5))
        fun tree(mode: String) = UiTree(1, listOf(UiNode(1, C.SEGMENTED_BUTTON, props = mapOf(P.MODE to UiValue.Enum(mode)), children = listOf(2, 3)),
            first, second, UiNode(4, ComposeUiComponents.TEXT), UiNode(5, ComposeUiComponents.TEXT)))
        fails { ComposeUiV2.validateTree(tree("single")) }
        ComposeUiV2.validateTree(tree("multi"))
        for (type in listOf(C.ASSIST_CHIP, C.FILTER_CHIP, C.INPUT_CHIP, C.DROPDOWN_MENU, C.NAVIGATION_DRAWER, C.TOOLTIP)) {
            fails { ComposeUiV2.validateNode(UiNode(1, type)) }
        }
    }

    @Test fun newLazyContainersKeepTheReviewedWireChildLimit() {
        for (type in listOf(C.HORIZONTAL_PAGER, C.LAZY_VERTICAL_GRID)) {
            UiNode(1, type, children = (2..2001).toList())
            fails(ComposeUiErrorCodes.LIMIT_EXCEEDED) { UiNode(1, type, children = (2..2002).toList()) }
        }
    }

    private fun fails(code: String = ComposeUiErrorCodes.INVALID_ARGUMENT, action: () -> Unit) {
        try { action(); fail("Expected $code") }
        catch (failure: ComposeUiContractException) { assertEquals(code, failure.code) }
    }
}
