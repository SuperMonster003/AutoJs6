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
}
