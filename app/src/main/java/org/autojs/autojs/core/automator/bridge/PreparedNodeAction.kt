package org.autojs.autojs.core.automator.bridge

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.diagnostics.NodeRefSnapshots

/** Prepared actions never climb to a different ancestor after inspection. */
object PreparedNodeAction {
    val methods = setOf("click", "longClick", "setText", "scrollForward", "scrollBackward")
    fun <N : UiNode<N>> find(root: N, matches: (N) -> Boolean, release: (N) -> Unit): N? {
        var visited = 0
        var selected: N? = null
        fun walk(node: N, depth: Int) {
            if (++visited > 2000 || depth > 32) throw NodeRefSnapshots.Stale("inspection exceeded the tree budget")
            if (!node.visibleToUser()) return
            if (matches(node)) { selected = node; return }
            for (index in 0 until node.childCount().coerceAtLeast(0)) {
                if (visited >= 2000) throw NodeRefSnapshots.Stale("inspection exceeded the tree budget")
                val child = node.child(index)
                if (child == null) { visited++; continue }
                try { walk(child, depth + 1) } finally { if (child !== selected && child !== root) release(child) }
                if (selected != null) return
            }
        }
        walk(root, 0)
        return selected
    }
    fun accepts(node: UiNode<*>, method: String): Boolean = when (method) {
        "click" -> node.clickable()
        "longClick" -> node.longClickable()
        "setText" -> node.editable()
        "scrollForward", "scrollBackward" -> node.scrollable()
        else -> false
    }
    fun text(existing: String, input: String, append: Boolean, password: Boolean): String {
        require(!append || !password) { "CAPABILITY_DENIED: Password fields require replacement." }
        val result = if (append) existing + input else input
        require(result.toByteArray(Charsets.UTF_8).size <= 64 * 1024) { "LIMIT_EXCEEDED: Text exceeds the action limit." }
        return result
    }
    fun validate(node: UiNode<*>, method: String, password: Boolean, enabled: Boolean) {
        if (node.password() != password || node.enabled() != enabled || !accepts(node, method)) {
            throw NodeRefSnapshots.Stale("prepared action state changed")
        }
    }
}
