package org.autojs.autojs.ui.explorer

import org.autojs.autojs.model.explorer.ExplorerItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerSelectionControllerTest {

    @Test
    fun rejectedItemCannotEnterOrToggleSelection() {
        val item = item("/workspace/rejected.js")
        val selection = ExplorerSelectionController { false }

        assertFalse(selection.enter(item))
        assertFalse(selection.toggle(item))
        assertFalse(selection.isActive)
        assertEquals(emptyList<ExplorerItem>(), selection.items)
    }

    @Test
    fun selectionKeepsInsertionOrderAndLatestInstanceForAPath() {
        val first = item("/workspace/first.js")
        val second = item("/workspace/second.js")
        val refreshedFirst = item("/workspace/first.js")
        val selection = ExplorerSelectionController { true }

        assertTrue(selection.enter(first))
        assertTrue(selection.enter(second))
        assertTrue(selection.enter(refreshedFirst))

        assertEquals(listOf(first.path, second.path), selection.items.map { it.path })
        assertSame(refreshedFirst, selection.items.first())
    }

    @Test
    fun toggleRemoveReplaceAndClearHaveExplicitMutationResults() {
        val first = item("/workspace/first.js")
        val second = item("/workspace/second.js")
        val replacement = item("/workspace/reindexed.js")
        val selection = ExplorerSelectionController { true }

        assertTrue(selection.toggle(first))
        assertTrue(selection.toggle(second))
        assertTrue(selection.toggle(first))
        assertEquals(listOf(second), selection.items)

        assertFalse(selection.remove("/workspace/missing.js"))
        assertTrue(selection.remove(second.path))
        assertFalse(selection.isActive)

        selection.replace(listOf(replacement))
        assertEquals(listOf(replacement), selection.items)
        assertTrue(selection.clear())
        assertFalse(selection.clear())
    }

    private fun item(path: String) = TestExplorerItem(path)
}
