package org.autojs.autojs.runtime.api.modules.compose

import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.model.UiCommand
import org.autojs.plugin.compose.api.v2.ComposeUiV2.Components as C
import org.autojs.plugin.compose.api.v2.ComposeUiV2.Props as P
import org.mozilla.javascript.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ComposeV2NodeGraphTest {
    private lateinit var graph: ComposeNodeGraph
    @Before fun enter() {
        val context = Context.enter().apply { languageVersion = Context.VERSION_ES6; isInterpretedMode = true }
        val scope = TopLevelScope().apply { initStandardObjects(context, false) }
        graph = ComposeNodeGraph(scope, ComposeScriptValues(colorParser = { (it as Number).toInt() }), { _, _, _ -> })
    }
    @After fun leave() { graph.close(); Context.exit() }
    private fun text(value: String = "label") = graph.create("Text", mapOf("text" to value))
    private fun item(selected: Boolean = false) = graph.create(C.SEGMENTED_BUTTON_ITEM,
        mapOf("selected" to selected, "label" to text().js))
    private class Binding : ComposeNodeBinding {
        override var isClosed = false
        val frames = ArrayList<ComposeNodeSnapshot>()
        val commands = ArrayList<UiCommand>()
        override fun publish(snapshot: ComposeNodeSnapshot) { frames += snapshot }
        override fun execute(command: UiCommand) { commands += command }
    }
    private fun fails(code: String = ComposeUiErrorCodes.INVALID_ARGUMENT, action: () -> Unit) {
        try { action(); fail("Expected $code") }
        catch (failure: ComposeUiContractException) { assertEquals(code, failure.code) }
    }

    @Test fun scopedItemsCanBePreparedDetachedButOnlyMountedAsPrimaryGroupChildren() {
        val first = item()
        fails(ComposeUiErrorCodes.SCOPE_MISMATCH) { graph.mount(first, Binding()) }
        val group = graph.create(C.SEGMENTED_BUTTON, children = listOf(first))
        val binding = Binding()
        graph.mount(group, binding)
        assertEquals(1, binding.frames.size)
        val wrong = text("invalid")
        fails { group.append(wrong) }
        assertNull(wrong.parent)
        assertEquals(listOf(first), group.children)
        assertEquals(1, binding.frames.size)
        fails { group.clear() }
        assertEquals(listOf(first), group.children)
    }

    @Test fun selectionTransitionRequiresOneValidProspectiveTreeAndRollsBackOnFailure() {
        val first = item(true)
        val second = item(false)
        val group = graph.create(C.SEGMENTED_BUTTON, children = listOf(first, second))
        val binding = Binding()
        graph.mount(group, binding)
        fails { second.set(mapOf(P.SELECTED to true)) }
        assertEquals(false, second.get(P.SELECTED))
        assertEquals(true, first.get(P.SELECTED))
        graph.transaction {
            first.set(mapOf(P.SELECTED to false))
            second.set(mapOf(P.SELECTED to true))
        }
        assertEquals(2, binding.frames.size)
        assertEquals(false, first.get(P.SELECTED))
        assertEquals(true, second.get(P.SELECTED))
        second.focus()
        second.blur()
        assertEquals(listOf(UiCommand.Focus::class.java, UiCommand.Blur::class.java), binding.commands.map { it.javaClass })
        group.set(mapOf(P.MODE to "multi"))
        first.set(mapOf(P.SELECTED to true))
        assertEquals(true, first.get(P.SELECTED))
    }

    @Test fun dependentTabAndPageMutationsPreserveChildrenAndSelectedPositionWhenRejected() {
        val one = graph.create(C.TAB, mapOf("text" to text("one").js))
        val two = graph.create(C.TAB, mapOf("text" to text("two").js))
        val tabs = graph.create(C.TAB_ROW, mapOf(P.SELECTED_INDEX to 1), listOf(one, two))
        fails { tabs.remove(two) }
        assertEquals(listOf(one, two), tabs.children)
        assertEquals(1.0, tabs.get(P.SELECTED_INDEX))
        graph.transaction { tabs.set(mapOf(P.SELECTED_INDEX to 0)); tabs.remove(two) }
        assertEquals(listOf(one), tabs.children)
        val pager = graph.create(C.HORIZONTAL_PAGER, children = listOf(text("page0"), text("page1")))
        val binding = Binding()
        graph.mount(pager, binding)
        graph.scrollTo(pager, mapOf("index" to 1, "offset" to 7))
        assertEquals(1, (binding.commands.single() as UiCommand.ScrollTo).index)
        assertEquals(7, (binding.commands.single() as UiCommand.ScrollTo).offset)
        fails { graph.scrollTo(pager, 2) }
        fails { pager.set(mapOf(P.PAGE to 2)) }
        assertEquals(1, binding.frames.size)
    }

    @Test fun datesTimesAndSearchUseTypedPropertiesAndKeepLastAcceptedValues() {
        val date = graph.create(C.DATE_PICKER, mapOf(P.SELECTED_DATE_MILLIS to 0))
        fails { date.set(mapOf(P.YEAR_START to 2000)) }
        assertEquals(0.0, date.get(P.SELECTED_DATE_MILLIS))
        fails { date.set(mapOf(P.SELECTED_DATE_MILLIS to 100)) }
        date.set(mapOf(P.SELECTED_DATE_MILLIS to null))
        assertNull(date.get(P.SELECTED_DATE_MILLIS))
        assertNull(graph.create(C.DATE_PICKER).get(P.SELECTED_DATE_MILLIS))
        // Preserve the null/absent distinction for the already-published V1 nullable fields too.
        assertNull(graph.create("CircularProgressIndicator").get("progress"))
        val label = text()
        assertSame(org.mozilla.javascript.Undefined.instance, label.get("color"))
        label.set(mapOf("color" to null))
        assertNull(label.get("color"))
        val time = graph.create(C.TIME_PICKER, mapOf(P.HOUR to 23, P.MINUTE to 59))
        fails { time.set(mapOf(P.MINUTE to 60, P.HOUR to 0)) }
        assertEquals(23.0, time.get(P.HOUR))
        assertEquals(59.0, time.get(P.MINUTE))
        val search = graph.create(C.SEARCH_BAR, mapOf(P.QUERY to "needle"))
        assertEquals("needle", search.get(P.QUERY))
        fails { search.set(mapOf(P.QUERY to 123)) }
        assertEquals("needle", search.get(P.QUERY))
    }

    @Test fun gridCommandsAndPaddingShareTheSameBoundedInputPath() {
        val grid = graph.create(C.LAZY_VERTICAL_GRID, mapOf(P.COLUMNS to 2, "contentPadding" to listOf(4, 8)),
            listOf(text("one"), text("two")))
        val binding = Binding()
        graph.mount(grid, binding)
        graph.scrollTo(grid, 1)
        assertEquals(1, (binding.commands.single() as UiCommand.ScrollTo).index)
        fails { graph.scrollTo(grid, 2) }
        fails { grid.set(mapOf("contentPadding" to -1)) }
        assertEquals(2.0, grid.get(P.COLUMNS))
    }
}
