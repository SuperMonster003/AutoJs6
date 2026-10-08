package org.autojs.autojs.core.plugin.compose

import org.autojs.plugin.compose.api.ComposeUiComponents as C
import org.autojs.plugin.compose.api.ComposeUiProps as P
import org.autojs.plugin.compose.api.ComposeUiSlots as S
import org.autojs.plugin.compose.api.model.*
import org.junit.Assert.*
import org.junit.Test

class TreeReconcilerTest {
    private fun list(vararg keys: String?) = UiTree(100, listOf(UiNode(100, C.COLUMN, children = keys.indices.map { it + 101 })) +
        keys.mapIndexed { i, key -> UiNode(i + 101, C.TEXT, key, mapOf(P.TEXT to UiValue.Str(key ?: "$i"))) })
    private fun children(tree: UiTree) = tree.nodes.single { it.nodeId == tree.rootId }.children

    @Test fun keyedReorderKeepsIdsAndUsesMoveAfterRemoval() {
        val reconciler = TreeReconciler()
        val initial = reconciler.reconcile(null, list("a", "b", "c")).tree
        val result = reconciler.reconcile(initial, list("c", "a", "b"))
        assertEquals(listOf(children(initial)[2], children(initial)[0], children(initial)[1]), children(result.tree))
        val move = result.patches.single() as UiPatch.Move
        assertEquals(children(initial)[2], move.nodeId); assertEquals(0, move.toIndex)
    }

    @Test fun insertionAndRemovalPreserveUnaffectedIdentity() {
        val reconciler = TreeReconciler()
        val initial = reconciler.reconcile(null, list("a", "b")).tree
        val result = reconciler.reconcile(initial, list("b", "c"))
        assertEquals(children(initial)[1], children(result.tree)[0])
        assertTrue(result.patches[0] is UiPatch.Remove)
        assertTrue(result.patches[1] is UiPatch.Insert)
        assertTrue(children(result.tree)[1] > initial.nodes.maxOf { it.nodeId })
    }

    @Test fun unkeyedFallbackUsesSameTypeIndexAndWarnsOncePerSession() {
        var warnings = 0
        val reconciler = TreeReconciler { warnings++ }
        val original = UiTree(1, listOf(UiNode(1, C.COLUMN, children = listOf(2, 3, 4)), UiNode(2, C.TEXT), UiNode(3, C.BUTTON), UiNode(4, C.TEXT)))
        val first = reconciler.reconcile(null, original).tree
        val draft = UiTree(1, listOf(UiNode(1, C.COLUMN, children = listOf(3, 2, 4)), UiNode(2, C.TEXT), UiNode(3, C.BUTTON), UiNode(4, C.TEXT)))
        val second = reconciler.reconcile(first, draft).tree
        assertEquals(listOf(children(first)[1], children(first)[0], children(first)[2]), children(second))
        reconciler.reconcile(second, draft)
        assertEquals(1, warnings)
    }

    @Test fun slotsReconcileMatchingRootsAndReplaceChangedType() {
        val reconciler = TreeReconciler()
        fun slot(type: String, text: String = "") = UiTree(1, listOf(UiNode(1, C.BUTTON, slots = mapOf(S.CONTENT to 2)),
            UiNode(2, type, props = if (type == C.TEXT) mapOf(P.TEXT to UiValue.Str(text)) else emptyMap())))
        val first = reconciler.reconcile(null, slot(C.TEXT, "a")).tree
        val update = reconciler.reconcile(first, slot(C.TEXT, "b"))
        assertTrue(update.patches.single() is UiPatch.SetProps)
        val replace = reconciler.reconcile(update.tree, slot(C.COLUMN))
        assertTrue(replace.patches.single() is UiPatch.ReplaceSlot)
        val clear = reconciler.reconcile(replace.tree, UiTree(1, listOf(UiNode(1, C.BUTTON))))
        assertNull((clear.patches.single() as UiPatch.ReplaceSlot).subtree)
    }

    @Test fun changedRootTypeGetsFreshIdentityAndSetRoot() {
        val reconciler = TreeReconciler()
        val first = reconciler.reconcile(null, list("a")).tree
        val result = reconciler.reconcile(first, UiTree(1, listOf(UiNode(1, C.TEXT))))
        assertNotEquals(first.rootId, result.tree.rootId)
        assertTrue(result.patches.single() is UiPatch.SetRoot)
    }

    @Test fun draftIdentityMapIncludesSlotsAndReservesIdsFromStableHandleTrees() {
        val reconciler = TreeReconciler()
        val previous = UiTree(700, listOf(UiNode(700, C.BUTTON, slots = mapOf(S.CONTENT to 701)),
            UiNode(701, C.COLUMN, children = listOf(702)), UiNode(702, C.TEXT, "kept")))
        val draft = UiTree(10, listOf(UiNode(10, C.BUTTON, slots = mapOf(S.CONTENT to 11)),
            UiNode(11, C.COLUMN, children = listOf(12, 13)), UiNode(12, C.TEXT, "kept"), UiNode(13, C.TEXT, "new")))
        val result = reconciler.reconcile(previous, draft)
        assertEquals(mapOf(10 to 700, 11 to 701, 12 to 702, 13 to 703), result.draftToStable)
        assertEquals(4, result.tree.nodes.map { it.nodeId }.toSet().size)
        assertTrue(result.patches.single() is UiPatch.Insert)
    }

    @Test fun crossParentAndChildToSlotTransfersUseOneAtomicReplacement() {
        val previous = UiTree(1, listOf(UiNode(1, C.ROW, children = listOf(2, 3)),
            UiNode(2, C.COLUMN), UiNode(3, C.COLUMN, children = listOf(4)), UiNode(4, C.TEXT, "retained")))
        // The destination is visited before the old owner: an Insert here would duplicate ID 4.
        val moved = UiTree(1, listOf(UiNode(1, C.ROW, children = listOf(2, 3)),
            UiNode(2, C.COLUMN, children = listOf(4)), UiNode(3, C.COLUMN), UiNode(4, C.TEXT, "retained")))
        assertSame(moved, (TreeReconciler.diff(previous, moved).single() as UiPatch.SetRoot).tree)
        val asSlot = UiTree(1, listOf(UiNode(1, C.ROW, children = listOf(2, 3)),
            UiNode(2, C.COLUMN, slots = mapOf(S.CONTENT to 4)), UiNode(3, C.COLUMN), UiNode(4, C.TEXT, "retained")))
        assertSame(asSlot, (TreeReconciler.diff(moved, asSlot).single() as UiPatch.SetRoot).tree)
    }

    @Test fun retainingAnIdWhileItsTypeOrKeyChangesCannotProduceAnIncompletePropsPatch() {
        val before = UiTree(1, listOf(UiNode(1, C.COLUMN, children = listOf(2)), UiNode(2, C.TEXT, "before")))
        for (changed in listOf(UiNode(2, C.TEXT, "after"), UiNode(2, C.BUTTON, "before"))) {
            val next = UiTree(1, listOf(UiNode(1, C.COLUMN, children = listOf(2)), changed))
            assertSame(next, (TreeReconciler.diff(before, next).single() as UiPatch.SetRoot).tree)
        }
    }
}
