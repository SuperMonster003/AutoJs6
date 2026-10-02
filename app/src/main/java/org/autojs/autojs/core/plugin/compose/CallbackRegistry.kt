package org.autojs.autojs.core.plugin.compose

import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.model.UiEvent
import org.autojs.plugin.compose.api.model.UiTree
import org.autojs.plugin.compose.api.model.UiValue

/** Only the script dispatcher touches this registry. No script function crosses the renderer ABI. */
class CallbackRegistry {
    private val callbacks = LinkedHashMap<Int, (UiEvent) -> Unit>()
    private var bindings = emptySet<Binding>()
    private var generation = -1L
    private var nextId = 1L
    private var closed = false
    private data class Binding(val node: Int, val event: String, val callback: Int)
    val size get() = callbacks.size

    fun register(callback: (UiEvent) -> Unit): Int {
        if (closed) throw ComposeUiContractException(ComposeUiErrorCodes.SESSION_CLOSED)
        if (callbacks.size >= ComposeUiLimits.MAX_CALLBACKS_PER_SESSION || nextId > Int.MAX_VALUE) {
            throw ComposeUiContractException(ComposeUiErrorCodes.LIMIT_EXCEEDED)
        }
        return (nextId++).toInt().also { callbacks[it] = callback }
    }

    fun validate(tree: UiTree) {
        if (!callbacks.keys.containsAll(ids(tree))) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "Unregistered callback")
    }

    fun commit(tree: UiTree, generation: Long, pending: UiTree? = null) {
        check(!closed)
        validate(tree)
        this.generation = generation
        bindings = bindings(tree)
        retain(tree, pending)
    }

    fun retain(tree: UiTree?, pending: UiTree? = null) {
        callbacks.keys.retainAll(ids(tree) + ids(pending))
    }

    /** Check again AFTER scheduling, not only when the event arrives from the renderer. */
    fun resolve(generation: Long, nodeId: Int, event: String, callbackId: Int): ((UiEvent) -> Unit)? =
        if (closed || generation != this.generation || Binding(nodeId, event, callbackId) !in bindings) null else callbacks[callbackId]

    fun close() { closed = true; callbacks.clear(); bindings = emptySet() }

    private fun bindings(tree: UiTree): Set<Binding> = buildSet {
        tree.nodes.forEach { node ->
            node.callbacks.forEach { (event, id) -> add(Binding(node.nodeId, event, id)) }
            node.modifier.filter { it.name == ComposeUiModifiers.CLICKABLE }.forEach { op ->
                (op.args.firstOrNull() as? UiValue.Num)?.value?.toInt()?.let { add(Binding(node.nodeId, ComposeUiEvents.CLICK, it)) }
            }
        }
    }

    private fun ids(tree: UiTree?) = tree?.let { bindings(it).mapTo(HashSet()) { binding -> binding.callback } }.orEmpty()
}
