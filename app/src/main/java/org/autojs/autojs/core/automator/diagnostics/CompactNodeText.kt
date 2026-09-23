package org.autojs.autojs.core.automator.diagnostics

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.bridge.BridgeAccessibilityLimits
import java.security.MessageDigest

/** Android-free MCP compact format. Stored rows contain bounded values, never live nodes. */
object CompactNodeText {
    const val MAX_TEXT_CHARS = 40
    const val MAX_VISITED_NODES = 2000
    const val MAX_IDENTITY_CHARS = 4096

    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val centerX: Int get() = ((left.toLong() + right) / 2).toInt()
        val centerY: Int get() = ((top.toLong() + bottom) / 2).toInt()
        fun compact() = "[$left,$top][$right,$bottom]"
        fun permits(candidate: Bounds): Boolean {
            val dx = minOf(48L, maxOf(0L, right.toLong() - left) / 2)
            val dy = minOf(48L, maxOf(0L, bottom.toLong() - top) / 2)
            // Bound every edge's movement; containment alone aliases smaller anonymous descendants.
            return kotlin.math.abs(candidate.left.toLong() - left) <= dx && kotlin.math.abs(candidate.right.toLong() - right) <= dx &&
                kotlin.math.abs(candidate.top.toLong() - top) <= dy && kotlin.math.abs(candidate.bottom.toLong() - bottom) <= dy
        }
    }

    data class Node(
        val className: String,
        val id: String,
        val text: String,
        val desc: String,
        val bounds: Bounds,
        val depth: Int,
        val childCount: Int,
        val markers: List<String>,
        val fingerprint: String?,
    )

    data class Collection(val nodes: List<Node>, val truncated: Boolean)

    /** Retains at most maxNodes rows and visits at most 2000 nodes, including hidden nodes. */
    fun <N : UiNode<N>> collect(
        roots: List<N>, maxNodes: Int = 200, maxDepth: Int = 32, visibleOnly: Boolean = true,
        release: (N) -> Unit = {},
    ): Collection {
        require(maxNodes in 1..BridgeAccessibilityLimits.MAX_NODES)
        require(maxDepth in 0..BridgeAccessibilityLimits.MAX_DEPTH)
        val rows = ArrayList<Node>()
        var visited = 0
        var truncated = false
        fun walk(node: N, depth: Int): Boolean {
            if (visited++ >= MAX_VISITED_NODES) { truncated = true; return false }
            if (visibleOnly && !node.visibleToUser()) return true
            if (rows.size >= maxNodes) { truncated = true; return false }
            rows += capture(node, depth)
            val childCount = node.childCount().coerceAtLeast(0)
            if (depth >= maxDepth) {
                if (childCount > 0) truncated = true
                return true
            }
            // Count absent/hostile child slots too, so a bogus childCount cannot create an unbounded walk.
            for (index in 0 until childCount) {
                if (visited >= MAX_VISITED_NODES || rows.size >= maxNodes) { truncated = true; return false }
                val child = node.child(index)
                if (child == null) { visited++; continue }
                try {
                    if (!walk(child, depth + 1)) return false
                } finally {
                    if (roots.none { it === child }) release(child)
                }
            }
            return true
        }
        for (root in roots) if (!walk(root, 0)) break
        return Collection(rows, truncated)
    }

    fun capture(node: UiNode<*>, depth: Int): Node {
        // Capabilities distinguish actionable containers from identically sized passive wrappers.
        // Transient checked/focused/selected/enabled state is validated separately when required.
        val identity = listOf(node.packageName().orEmpty(), node.className().orEmpty(), node.id().orEmpty(), node.text(), node.desc().orEmpty(),
            node.clickable().toString(), node.longClickable().toString(), node.checkable().toString(), node.scrollable().toString(), node.editable().toString())
        val digest = if (identity.any { it.length > MAX_IDENTITY_CHARS }) null else {
            val hash = MessageDigest.getInstance("SHA-256")
            identity.forEach { value ->
                val bytes = value.toByteArray(Charsets.UTF_8)
                hash.update(byteArrayOf((bytes.size ushr 24).toByte(), (bytes.size ushr 16).toByte(), (bytes.size ushr 8).toByte(), bytes.size.toByte()))
                hash.update(bytes)
            }
            buildString(64) {
                hash.digest().forEach { byte ->
                    val value = byte.toInt() and 255
                    append("0123456789abcdef"[value ushr 4]).append("0123456789abcdef"[value and 15])
                }
            }
        }
        val markers = buildList {
            if (node.clickable()) add("clickable")
            if (node.longClickable()) add("long_clickable")
            if (node.checkable()) add("checkable")
            if (node.checked()) add("checked")
            if (node.scrollable()) add("scrollable")
            if (node.editable()) add("editable")
            if (node.focused()) add("focused")
            if (node.selected()) add("selected")
            if (!node.enabled()) add("!enabled")
            if (!node.visibleToUser()) add("hidden")
        }
        return Node(
            className = identity[1].take(256), id = identity[2].take(512),
            text = if (node.password()) "[password]" else identity[3].take(128),
            desc = if (node.password()) "" else identity[4].take(128),
            bounds = Bounds(node.left(), node.top(), node.right(), node.bottom()),
            depth = depth.coerceIn(0, BridgeAccessibilityLimits.MAX_DEPTH),
            childCount = node.childCount().coerceAtLeast(0), markers = markers, fingerprint = digest,
        )
    }

    /** Matches the released MCP formatter's header, marker order, escaping and footer. */
    fun render(packageName: String, activityName: String, nodes: List<Node>, truncated: Boolean, maxNodes: Int, maxDepth: Int): String = buildString {
        val pkg = token(packageName.take(256))
        val activity = token(activityName.take(256))
        append("window: ").append(pkg.ifEmpty { "?" })
        if (activity.isNotEmpty()) append('/').append(shortActivity(pkg, activity))
        rootBounds(nodes)?.let { append("  bounds=").append(it.compact()) }
        append("  nodes=").append(nodes.size)
        if (truncated) append("  truncated=true")
        nodes.forEachIndexed { index, node -> append('\n').append(line(index + 1, node)) }
        val unlisted = nodesWithUnlistedChildren(nodes)
        if (truncated || unlisted > 0) {
            append("\n(")
            if (truncated) {
                if (nodes.size >= maxNodes) append("the maxNodes=$maxNodes budget cut the tree; ")
                else append("a depth, traversal, or text budget cut the tree; ")
            }
            if (unlisted > 0) append("$unlisted listed nodes have children that are not shown (visibleOnly, maxDepth=$maxDepth, or the budget); ")
            append("raise maxNodes (up to ${BridgeAccessibilityLimits.MAX_NODES}), lower maxDepth, or use ui_find)")
        }
    }

    fun line(number: Int, node: Node): String = buildString {
        append("#n").append(number).append(' ')
        repeat(node.depth) { append(' ') }
        append(token(node.className.substringAfterLast('.')).ifEmpty { "?" })
        node.markers.forEach { append(' ').append(it) }
        if (node.text.isNotEmpty()) append(" \"").append(clip(node.text)).append('"')
        if (node.desc.isNotEmpty()) append(" desc=\"").append(clip(node.desc)).append('"')
        if (node.id.isNotEmpty()) append(" id=").append(token(node.id.substringAfterLast('/')))
        append(' ')
        if (node.childCount > 0) append(node.bounds.compact())
        else append("c=(").append(node.bounds.centerX).append(',').append(node.bounds.centerY).append(')')
    }

    fun clip(text: String): String {
        val escaped = buildString {
            for (char in text) {
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(if (char.isISOControl()) ' ' else char)
                }
                if (length > MAX_TEXT_CHARS) break
            }
        }
        return if (escaped.length <= MAX_TEXT_CHARS) escaped else escaped.take(MAX_TEXT_CHARS) + "..."
    }

    private fun token(value: String) = value.map { if (it.isWhitespace() || it.isISOControl()) '_' else it }.joinToString("")
    fun shortActivity(packageName: String, activity: String) =
        if (packageName.isNotEmpty() && activity.startsWith("$packageName.")) activity.substring(packageName.length) else activity

    fun rootBounds(nodes: List<Node>): Bounds? {
        val roots = nodes.filter { it.depth == 0 }.map { it.bounds }
        if (roots.isEmpty()) return null
        return Bounds(roots.minOf { it.left }, roots.minOf { it.top }, roots.maxOf { it.right }, roots.maxOf { it.bottom })
    }

    fun nodesWithUnlistedChildren(nodes: List<Node>): Int = nodes.indices.count { index ->
        val node = nodes[index]
        var listed = 0
        var next = index + 1
        while (next < nodes.size && nodes[next].depth > node.depth) {
            if (nodes[next].depth == node.depth + 1) listed++
            next++
        }
        listed < node.childCount
    }
}
