package org.autojs.autojs.runtime.api.modules.compose

import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.JSFunction
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Render-scoped fragments for compose.memo (roadmap F.6).
 *
 * A slot is found by its scope (the render, or the enclosing memo fragment), then by its key
 * or call order within that scope, and must come from the same function literal. Its previous
 * node is reused only when every dependency value is unchanged, no compose.state read while it
 * was built has been written since, and the node is still inside the session's previous tree.
 * Otherwise the fragment runs again and its result reconciles like any other render output.
 */
internal class ComposeMemoCache {
    internal class Entry(
        val identity: Any,
        val dependencies: List<Any?>,
        val reads: Map<ComposeReactiveRuntime.State, Long>,
        var node: ComposeNode,
        val children: Scope,
    )

    internal class Scope {
        val keyed = HashMap<String, Entry>()
        val ordered = ArrayList<Entry>()
        fun add(key: String?, entry: Entry) { if (key == null) ordered += entry else keyed[key] = entry }
    }

    /** One render. Nothing replaces the committed scope unless the render and its publication succeed. */
    internal class Frame(val previousRoot: ComposeNode?, previous: Scope) {
        val next = Scope()
        val created = ArrayList<Entry>()
        val used: MutableSet<ComposeNode> = Collections.newSetFromMap(IdentityHashMap())
        var calls = 0
        private val levels = ArrayList<Level>().apply { add(Level(previous, next)) }
        class Level(val previous: Scope?, val next: Scope) {
            var ordinal = 0
            val keys = HashSet<String>()
        }
        val current get() = levels.last()
        fun <T> nested(previous: Scope?, next: Scope, action: () -> T): T {
            levels += Level(previous, next)
            try { return action() } finally { levels.removeAt(levels.lastIndex) }
        }
    }

    private var committed = Scope()

    fun begin(previousRoot: ComposeNode?) = Frame(previousRoot, committed)

    fun commit(frame: Frame, resolved: Map<ComposeNode, ComposeNode>) {
        // A fragment built a draft; reconciliation may have kept an older node with the same identity.
        frame.created.forEach { entry -> resolved[entry.node]?.let { entry.node = it } }
        committed = frame.next
    }

    fun clear() { committed = Scope() }

    companion object {
        const val MAX_DEPENDENCIES = 256

        /** Literal functions share one descriptor across closures; other callables must be the same object. */
        fun identity(function: Function): Any = (function as? JSFunction)?.descriptor ?: function

        fun dependencies(raw: Any?): List<Any?> {
            val values: List<Any?> = when (val value = ComposeScriptValues.unwrap(raw)) {
                is NativeArray -> {
                    if (value.length > MAX_DEPENDENCIES) limit()
                    (0 until value.length.toInt()).map { index ->
                        value.get(index, value).takeUnless { it === Scriptable.NOT_FOUND } ?: Undefined.instance
                    }
                }
                is List<*> -> value.also { if (it.size > MAX_DEPENDENCIES) limit() }.toList()
                is Array<*> -> value.also { if (it.size > MAX_DEPENDENCIES) limit() }.toList()
                else -> throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "memo dependencies must be an array", prop = "deps")
            }
            return values.map { ComposeScriptValues.unwrap(it) }
        }

        fun key(raw: Any?): String? = when (val value = ComposeScriptValues.unwrap(raw)) {
            null, is Undefined -> null
            is CharSequence -> value.toString()
            is Number -> if (value.toDouble().isFinite()) Context.toString(value) else invalidKey()
            else -> invalidKey()
        }

        /** Object.is for primitives, identity for objects; script strings may be distinct CharSequence instances. */
        fun same(previous: List<Any?>, next: List<Any?>): Boolean =
            previous.size == next.size && previous.indices.all { index -> sameValue(previous[index], next[index]) }

        private fun sameValue(a: Any?, b: Any?): Boolean = when {
            a is Number && b is Number -> a.toDouble().toBits() == b.toDouble().toBits()
            a is CharSequence && b is CharSequence -> a.toString() == b.toString()
            a is Boolean && b is Boolean -> a == b
            else -> a === b
        }

        private fun limit(): Nothing = throw ComposeUiContractException(ComposeUiErrorCodes.LIMIT_EXCEEDED,
            "memo accepts at most $MAX_DEPENDENCIES dependencies", prop = "deps")
        private fun invalidKey(): Nothing = throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT,
            "memo key must be a string or a finite number", prop = "key")
    }
}
