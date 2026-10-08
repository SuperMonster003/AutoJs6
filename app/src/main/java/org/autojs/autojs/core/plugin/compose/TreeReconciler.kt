package org.autojs.autojs.core.plugin.compose

import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.ComposeUiLimits
import org.autojs.plugin.compose.api.catalog.ComponentCatalog
import org.autojs.plugin.compose.api.model.UiNode
import org.autojs.plugin.compose.api.model.UiPatch
import org.autojs.plugin.compose.api.model.UiTree

/** Script-side identity allocation. Draft IDs are references within one render, never live handles. */
class TreeReconciler(private val catalog: ComponentCatalog = ComponentCatalog.V1, private val missingKey: () -> Unit = {}) {
    data class Result(val tree: UiTree, val patches: List<UiPatch>, val draftToStable: Map<Int, Int>)
    private var nextId = 1L
    private var warned = false

    fun reconcile(previous: UiTree?, draft: UiTree): Result {
        val old = previous?.nodes?.associateBy { it.nodeId }.orEmpty()
        // Stable handle submissions may allocate IDs outside this reconciler. Never reuse one.
        old.keys.maxOrNull()?.let { nextId = maxOf(nextId, it.toLong() + 1) }
        val input = draft.nodes.associateBy { it.nodeId }
        val output = LinkedHashMap<Int, UiNode>()
        val identities = LinkedHashMap<Int, Int>()
        fun compatible(a: UiNode?, b: UiNode) = a?.type == b.type && a.key == b.key
        fun visit(draftId: Int, candidate: UiNode?): Int {
            val node = input.getValue(draftId)
            val prior = candidate?.takeIf { compatible(it, node) }
            val id = prior?.nodeId ?: allocateId()
            identities[draftId] = id
            val keyed = prior?.children.orEmpty().map(old::getValue).filter { it.key != null }.associateBy { it.key }
            val unkeyed = prior?.children.orEmpty().map(old::getValue).filter { it.key == null }.groupBy { it.type }
            val indices = HashMap<String, Int>()
            if (!warned && node.children.size > 1 && node.children.any { input.getValue(it).key == null }) {
                warned = true
                missingKey()
            }
            val children = node.children.map { childId ->
                val child = input.getValue(childId)
                val match = if (child.key != null) keyed[child.key] else {
                    val index = indices.getOrDefault(child.type, 0)
                    indices[child.type] = index + 1
                    unkeyed[child.type]?.getOrNull(index)
                }
                visit(childId, match)
            }
            val slots = node.slots.mapValues { (slot, child) -> visit(child, prior?.slots?.get(slot)?.let(old::get)) }
            output[id] = UiNode(id, node.type, node.key, node.props, node.modifier, children, slots, node.callbacks)
            return id
        }
        val root = visit(draft.rootId, previous?.rootId?.let(old::get))
        val tree = UiTree(root, output.values.toList())
        validate(tree, catalog)
        return Result(tree, diff(previous, tree), java.util.Collections.unmodifiableMap(LinkedHashMap(identities)))
    }

    private fun allocateId(): Int {
        if (nextId > Int.MAX_VALUE) throw ComposeUiContractException(ComposeUiErrorCodes.LIMIT_EXCEEDED)
        return (nextId++).toInt()
    }

    companion object {
        fun validate(tree: UiTree, catalog: ComponentCatalog = ComponentCatalog.V1) {
            if (catalog.component(org.autojs.plugin.compose.api.v2.ComposeUiV2.Components.TIME_PICKER) != null) {
                org.autojs.plugin.compose.api.v2.ComposeUiV2.validateTree(tree)
                return
            }
            val nodes = tree.nodes.associateBy { it.nodeId }
            val parents = HashMap<Int, String>()
            tree.nodes.forEach { node -> (node.children + node.slots.values).forEach { parents[it] = node.type } }
            nodes.values.forEach { catalog.validateNode(it, parents[it.nodeId]) }
        }

        /** Both trees already have stable IDs. Moves use indices after removal, as defined by V1. */
        fun diff(previous: UiTree?, next: UiTree): List<UiPatch> {
            if (previous == null || previous.rootId != next.rootId) return listOf(UiPatch.SetRoot(next))
            val old = previous.nodes.associateBy { it.nodeId }
            val nodes = next.nodes.associateBy { it.nodeId }
            // A retained handle can move between parents or between children and named slots.
            // Per-parent insert/remove traversal cannot safely order every such transfer: inserting
            // before the old owner is visited would duplicate an ID. Publish these uncommon edits
            // as one validated atomic replacement; ordinary sibling reorder still emits Move.
            data class Owner(val parent: Int, val slot: String?)
            fun owners(tree: UiTree) = buildMap {
                tree.nodes.forEach { parent ->
                    parent.children.forEach { put(it, Owner(parent.nodeId, null)) }
                    parent.slots.forEach { (slot, child) -> put(child, Owner(parent.nodeId, slot)) }
                }
            }
            val beforeOwners = owners(previous)
            val afterOwners = owners(next)
            if (old.keys.any { id ->
                val after = nodes[id]
                after != null && (old.getValue(id).type != after.type || old.getValue(id).key != after.key ||
                    beforeOwners[id] != afterOwners[id])
            }) return listOf(UiPatch.SetRoot(next))
            val patches = ArrayList<UiPatch>()
            fun add(patch: UiPatch) {
                if (patches.size == ComposeUiLimits.MAX_PATCHES_PER_BATCH) throw ComposeUiContractException(ComposeUiErrorCodes.LIMIT_EXCEEDED)
                patches += patch
            }
            fun subtree(id: Int): UiTree {
                val collected = ArrayList<UiNode>()
                fun collect(child: Int) {
                    val node = nodes.getValue(child)
                    collected += node
                    (node.children + node.slots.values).forEach(::collect)
                }
                collect(id)
                return UiTree(id, collected)
            }
            fun visit(id: Int) {
                val a = old.getValue(id)
                val b = nodes.getValue(id)
                if (a.props != b.props || a.modifier != b.modifier || a.callbacks != b.callbacks) {
                    add(UiPatch.SetProps(id, b.props, b.modifier, b.callbacks))
                }
                val current = a.children.toMutableList()
                val wanted = b.children.toSet()
                a.children.filter { it !in wanted }.forEach { child ->
                    add(UiPatch.Remove(id, child)); current.remove(child)
                }
                b.children.forEachIndexed { index, child ->
                    val from = current.indexOf(child)
                    if (from < 0) {
                        add(UiPatch.Insert(id, index, subtree(child))); current.add(index, child)
                    } else {
                        if (from != index) { add(UiPatch.Move(id, child, index)); current.removeAt(from); current.add(index, child) }
                        visit(child)
                    }
                }
                (a.slots.keys + b.slots.keys).forEach { slot ->
                    val before = a.slots[slot]
                    val after = b.slots[slot]
                    if (before != after) add(UiPatch.ReplaceSlot(id, slot, after?.let(::subtree)))
                    else if (after != null) visit(after)
                }
            }
            visit(next.rootId)
            return patches
        }
    }
}
