package org.autojs.autojs.core.plugin.compose

import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.model.UiEvent
import org.autojs.plugin.compose.api.model.UiTree
import org.autojs.plugin.compose.api.model.UiValue

/** Registry maps belong to the script dispatcher. No script function crosses the renderer ABI. */
class CallbackRegistry {
    private class Callback(val invoke: (UiEvent) -> Unit, var usedByCommand: Boolean = false)
    private val callbacks = LinkedHashMap<Int, Callback>()
    private var bindings = emptySet<Binding>()
    private var treeReferences = emptySet<Int>()
    private var stagedReferences = emptySet<Int>()
    private val commands = HashMap<Int, CommandBinding>()
    private var generation = -1L
    private var nextId = 1L
    private var closed = false
    private data class Binding(val node: Int, val event: String, val callback: Int)
    internal class CommandBinding internal constructor(val callbackId: Int) {
        @Volatile internal var generation: Long? = null
            private set

        /** The main thread publishes only this ticket value immediately before executing its command. */
        internal fun publishGeneration(generation: Long) { this.generation = generation }
    }
    val size get() = callbacks.size
    fun contains(callbackId: Int) = callbackId in callbacks

    fun register(callback: (UiEvent) -> Unit): Int {
        if (closed) throw ComposeUiContractException(ComposeUiErrorCodes.SESSION_CLOSED)
        if (callbacks.size >= ComposeUiLimits.MAX_CALLBACKS_PER_SESSION || nextId > Int.MAX_VALUE) {
            throw ComposeUiContractException(ComposeUiErrorCodes.LIMIT_EXCEEDED)
        }
        return (nextId++).toInt().also { callbacks[it] = Callback(callback) }
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

    fun retain(tree: UiTree?, pending: UiTree? = null, flight: UiTree? = null) {
        treeReferences = ids(tree) + ids(pending) + ids(flight)
        stagedReferences = ids(pending) + ids(flight)
        callbacks.keys.retainAll(treeReferences + commands.keys)
    }

    /** Staging a tree protects references; only a commit or rollback is allowed to prune them. */
    internal fun stage(flight: UiTree?, pending: UiTree?) {
        stagedReferences = ids(flight) + ids(pending)
    }

    /** A command holds its callback independently of node commits until one terminal result. */
    internal fun reserveCommand(callbackId: Int): CommandBinding {
        if (closed) throw ComposeUiContractException(ComposeUiErrorCodes.SESSION_CLOSED)
        val callback = callbacks[callbackId]
            ?: throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "Unregistered command callback")
        if (callback.usedByCommand) {
            throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "Command callback has already been used")
        }
        callback.usedByCommand = true
        return CommandBinding(callbackId).also { commands[callbackId] = it }
    }

    internal fun releaseCommand(binding: CommandBinding) {
        if (commands[binding.callbackId] !== binding) return
        commands.remove(binding.callbackId)
        if (binding.callbackId !in treeReferences && binding.callbackId !in stagedReferences) callbacks.remove(binding.callbackId)
    }

    internal fun discardCommandResult(generation: Long, callbackId: Int) {
        val command = commands[callbackId] ?: return
        if (command.generation == generation) releaseCommand(command)
    }

    internal fun discardAllCommands() = commands.values.toList().forEach(::releaseCommand)

    /** Check again AFTER scheduling, not only when the event arrives from the renderer. */
    fun resolve(generation: Long, nodeId: Int, event: String, callbackId: Int): ((UiEvent) -> Unit)? {
        if (closed) return null
        if (nodeId == ComposeUiContract.NO_NODE_ID) {
            val command = commands[callbackId] ?: return null
            if (command.generation != generation || event !in COMMAND_RESULTS) return null
            // Consume before user code so a reentrant callback or duplicate terminal event cannot run twice.
            val callback = callbacks[callbackId]?.invoke
            releaseCommand(command)
            return callback
        }
        return if (generation != this.generation || Binding(nodeId, event, callbackId) !in bindings) null else callbacks[callbackId]?.invoke
    }

    fun close() { closed = true; callbacks.clear(); bindings = emptySet(); treeReferences = emptySet(); stagedReferences = emptySet(); commands.clear() }

    private fun bindings(tree: UiTree): Set<Binding> = buildSet {
        tree.nodes.forEach { node ->
            node.callbacks.forEach { (event, id) -> add(Binding(node.nodeId, event, id)) }
            node.modifier.filter { it.name == ComposeUiModifiers.CLICKABLE }.forEach { op ->
                (op.args.firstOrNull() as? UiValue.Num)?.value?.toInt()?.let { add(Binding(node.nodeId, ComposeUiEvents.CLICK, it)) }
            }
        }
    }

    private fun ids(tree: UiTree?) = tree?.let { bindings(it).mapTo(HashSet()) { binding -> binding.callback } }.orEmpty()

    private companion object {
        val COMMAND_RESULTS = setOf(ComposeUiEvents.ACTION, ComposeUiEvents.DISMISS)
    }
}

/** Payload-free overflow cleanup, guarded by the session's event lock. */
internal class CommandResultEvictions {
    private data class Result(val generation: Long, val callback: Int)
    private val results = LinkedHashSet<Result>()
    private var all = false

    fun add(generation: Long, node: Int, event: String, callback: Int) {
        if (all || node != ComposeUiContract.NO_NODE_ID || callback == ComposeUiContract.SYSTEM_CALLBACK_ID ||
            event != ComposeUiEvents.ACTION && event != ComposeUiEvents.DISMISS) return
        val result = Result(generation, callback)
        if (result in results) return
        if (results.size == ComposeUiLimits.MAX_CALLBACKS_PER_SESSION) {
            // Invalid or replayed headers must not turn overflow accounting into an unbounded queue.
            all = true
            results.clear()
        } else results += result
    }

    fun drain(registry: CallbackRegistry) {
        if (all) registry.discardAllCommands()
        else results.forEach { registry.discardCommandResult(it.generation, it.callback) }
        clear()
    }

    fun clear() { results.clear(); all = false }
}
