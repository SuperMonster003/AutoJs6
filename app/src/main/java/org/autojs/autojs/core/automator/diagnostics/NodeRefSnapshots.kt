package org.autojs.autojs.core.automator.diagnostics

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.bridge.BridgeAccessibilityLimits
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract
import java.util.UUID

/** One provider/link's LRU. It retains bounded value fingerprints, never AccessibilityNodeInfo. */
class NodeRefSnapshots(private val capacity: Int = MAX_SNAPSHOTS) : AutoCloseable {
    init { require(capacity in 1..MAX_SNAPSHOTS) }

    data class Snapshot(val id: String, val window: String, val windowKey: String, val nodes: List<CompactNodeText.Node>)
    data class Reference(val snapshot: Snapshot, val node: CompactNodeText.Node)
    class Stale(val reason: String) : IllegalArgumentException("NODE_REF_STALE: $reason; take a new compact dump.")

    private val lock = Any()
    private val prefix = UUID.randomUUID().toString()
    private var counter = 0L
    private var latest: String? = null
    private var closed = false
    private val snapshots = LinkedHashMap<String, Snapshot>(MAX_SNAPSHOTS, .75f, true)

    fun register(nodes: List<CompactNodeText.Node>, window: String, windowKey: String): Snapshot = synchronized(lock) {
        if (closed) throw Stale("provider scope is closed")
        require(nodes.size <= BridgeAccessibilityLimits.MAX_NODES)
        require(window == "active" || window == "all")
        require(windowKey.length <= MAX_WINDOW_KEY_CHARS)
        val snapshot = Snapshot("$prefix:${++counter}", window, windowKey, nodes.toList())
        snapshots[snapshot.id] = snapshot
        latest = snapshot.id
        while (snapshots.size > capacity) snapshots.remove(snapshots.keys.first())
        snapshot
    }

    fun resolve(ref: String, snapshotId: String? = null): Reference = synchronized(lock) {
        val number = REF.matchEntire(ref)?.groupValues?.get(1)?.toIntOrNull() ?: throw Stale("malformed reference")
        val id = snapshotId ?: latest ?: throw Stale("no snapshot")
        val snapshot = snapshots[id] ?: throw Stale("unknown or evicted snapshot")
        val node = snapshot.nodes.getOrNull(number - 1) ?: throw Stale("unknown node")
        if (node.fingerprint == null) throw Stale("node identity exceeds the fingerprint limit")
        Reference(snapshot, node)
    }

    /** Fresh bounded tree walk; only a unique matching identity within the old bounds may act. */
    fun <N : UiNode<N>, T> relocate(
        reference: Reference, roots: List<N>, windowKey: String, release: (N) -> Unit = {}, action: (N) -> T,
    ): T {
        if (reference.snapshot.windowKey != windowKey) throw Stale("window changed")
        var visited = 0
        var budgetExceeded = false
        var matches = 0
        var selected: N? = null
        fun walk(node: N, depth: Int) {
            if (visited++ >= CompactNodeText.MAX_VISITED_NODES) { budgetExceeded = true; return }
            val value = CompactNodeText.capture(node, depth)
            if (node.visibleToUser() && reference.node.fingerprint == value.fingerprint && reference.node.bounds.permits(value.bounds)) {
                matches++
                if (matches == 1) selected = node
            }
            if (depth >= MAX_ACTION_TREE_DEPTH) {
                if (node.childCount() > 0) budgetExceeded = true
                return
            }
            for (index in 0 until node.childCount().coerceAtLeast(0)) {
                if (visited >= CompactNodeText.MAX_VISITED_NODES) { budgetExceeded = true; return }
                val child = node.child(index)
                if (child == null) { visited++; continue }
                try { walk(child, depth + 1) } finally {
                    if (child !== selected && roots.none { it === child }) release(child)
                }
            }
        }
        try {
            roots.forEach { if (visited < CompactNodeText.MAX_VISITED_NODES) walk(it, 0) else budgetExceeded = true }
            if (budgetExceeded) throw Stale("relocation exceeded the tree budget")
            if (matches != 1) throw Stale(if (matches == 0) "node no longer matches" else "ambiguous node identity")
            synchronized(lock) {
                if (snapshots[reference.snapshot.id] !== reference.snapshot) throw Stale("snapshot was cleared or evicted")
            }
            // A framework action can block; do not hold the snapshot lock across remote app code.
            return action(checkNotNull(selected))
        } finally {
            selected?.let { if (roots.none { root -> root === it }) release(it) }
        }
    }

    fun clear() = synchronized(lock) { snapshots.clear(); latest = null }
    override fun close() = synchronized(lock) { closed = true; snapshots.clear(); latest = null }
    val size: Int get() = synchronized(lock) { snapshots.size }

    companion object {
        const val MAX_SNAPSHOTS = ThreeStoveAgentContract.MAX_SNAPSHOTS_PER_LINK
        const val MAX_WINDOW_KEY_CHARS = 2048
        // Action lookup must inspect beyond the display dump's 32-level formatting limit.
        // The independent 2000-node visit cap and tool deadline still bound the work.
        const val MAX_ACTION_TREE_DEPTH = 64
        private val REF = Regex("(?:#?n)?([1-9][0-9]{0,3})")
    }
}
