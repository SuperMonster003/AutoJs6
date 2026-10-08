package org.autojs.autojs.runtime.api.augment.compose

import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.VarScope
import java.io.Closeable

/** Script-dispatcher dependency tracking. A scheduler tick, never a setter, executes render. */
internal class ComposeReactiveRuntime(
    private val scope: VarScope,
    private val checkThread: () -> Unit,
    private val post: (() -> Unit) -> Unit,
    private val onError: (Throwable) -> Unit,
) : Closeable {
    private val effects = LinkedHashSet<Reaction>()
    private val dirty = LinkedHashSet<Reaction>()
    private var collecting: Reaction? = null
    // compose.memo fragments record the first-read version of every state they read.
    private val recorders = ArrayList<LinkedHashMap<State, Long>>()
    private var scheduled = false
    private var batchDepth = 0
    private var closed = false
    val isRendering: Boolean get() = collecting != null

    inner class State(initial: Any?) : NativeObject() {
        private var stored = initial
        internal val subscribers = LinkedHashSet<Reaction>()
        /** Incremented by every effective write; memo fragments compare it instead of values. */
        internal var version = 0L
            private set
        init {
            RhinoUtils.initNativeObjectPrototype(scope, this)
            defineProperty("value", { ComposeJsErrors.guard(scope) { read() } },
                { next -> ComposeJsErrors.guard(scope) { write(next) } }, PERMANENT)
        }
        override fun getClassName() = "ComposeState"
        fun read(): Any? {
            usable()
            collecting?.reads?.add(this)
            recorders.lastOrNull()?.putIfAbsent(this, version)
            return stored
        }
        fun write(next: Any?) {
            usable()
            if (same(stored, next)) return
            stored = next
            version++
            subscribers.toList().forEach(Reaction::invalidate)
            collecting?.takeIf { this in it.reads }?.invalidate()
        }
    }

    inner class Reaction(private var action: (() -> Unit)?) : Closeable {
        private val dependencies = LinkedHashSet<State>()
        internal var reads = LinkedHashSet<State>()
        private var disposed = false
        fun run() {
            if (disposed) return
            usable()
            val outer = collecting
            val next = LinkedHashSet<State>()
            reads = next
            collecting = this
            var succeeded = false
            try {
                action?.invoke()
                succeeded = true
            } finally {
                collecting = outer
                if (!disposed && !closed) {
                    // A failed render keeps its old view, and attempted dependencies must still
                    // wake it when repaired. The next successful render prunes obsolete reads.
                    val retained = if (succeeded) next else LinkedHashSet(dependencies).apply { addAll(next) }
                    dependencies.filter { it !in retained }.forEach { it.subscribers.remove(this) }
                    retained.forEach { it.subscribers += this }
                    dependencies.clear(); dependencies += retained
                }
                reads = LinkedHashSet()
            }
        }
        fun invalidate() {
            if (!disposed && !closed) { dirty += this; schedule() }
        }
        override fun close() {
            if (disposed) return
            disposed = true
            dependencies.forEach { it.subscribers.remove(this) }
            dependencies.clear(); reads.clear(); dirty.remove(this); effects.remove(this); action = null
        }
    }

    fun state(initial: Any?): State { usable(); return State(initial) }
    fun reaction(action: () -> Unit): Reaction { usable(); return Reaction(action).also(effects::add) }
    /** Run a memo fragment and return the states it read, nested reads included in the outer fragment. */
    fun <T> record(action: () -> T): Pair<T, Map<State, Long>> {
        usable()
        val reads = LinkedHashMap<State, Long>()
        recorders += reads
        try {
            return action() to reads
        } finally {
            recorders.removeAt(recorders.lastIndex)
            recorders.lastOrNull()?.let { outer -> reads.forEach { (state, version) -> outer.putIfAbsent(state, version) } }
        }
    }
    /** True when no state read by a recorded fragment has been written since it was recorded. */
    fun isCurrent(reads: Map<State, Long>): Boolean = reads.all { (state, version) -> state.version == version }
    /** A reused fragment still depends on its states, so the running render and outer fragments subscribe to them. */
    fun replay(reads: Map<State, Long>) {
        usable()
        collecting?.reads?.addAll(reads.keys)
        recorders.lastOrNull()?.let { outer -> reads.forEach { (state, version) -> outer.putIfAbsent(state, version) } }
    }
    fun <T> batch(action: () -> T): T {
        usable(); batchDepth++
        try { return action() } finally { batchDepth--; schedule() }
    }
    private fun schedule() {
        if (closed || batchDepth > 0 || scheduled || dirty.isEmpty()) return
        scheduled = true
        try {
            post {
                scheduled = false
                if (!closed) {
                    val pending = dirty.toList().also { dirty.clear() }
                    pending.forEach { reaction ->
                        try { reaction.run() } catch (failure: Exception) { onError(failure) }
                        catch (failure: LinkageError) { onError(failure) }
                    }
                }
            }
        } catch (failure: Throwable) { scheduled = false; throw failure }
    }
    private fun usable() {
        checkThread()
        if (closed) throw ComposeUiContractException(ComposeUiErrorCodes.SESSION_CLOSED)
    }
    override fun close() {
        if (closed) return
        closed = true
        effects.toList().forEach(Reaction::close)
        dirty.clear(); collecting = null; recorders.clear()
    }
    private fun same(a: Any?, b: Any?): Boolean = when {
        a is Number && b is Number -> a.toDouble().toBits() == b.toDouble().toBits()
        a is String && b is String -> a == b
        a is Boolean && b is Boolean -> a == b
        else -> a === b
    }
}

internal class ComposeRef(scope: VarScope) : NativeObject(), ComposeNodeRef {
    override var current: ComposeNode? = null
    init {
        RhinoUtils.initNativeObjectPrototype(scope, this)
        defineProperty("current", { current?.takeUnless { it.affinity?.isClosed == true }?.js }, null, READONLY or PERMANENT)
    }
    override fun getClassName() = "ComposeRef"
}
