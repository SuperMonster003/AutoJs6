package org.autojs.autojs.core.plugin.python

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.LinkedHashMap

/** Hard bounds for the execution-local Python selector data plane. */
internal object PythonHostSelectorPolicy {
    const val SNAPSHOT_SCHEMA = "autojs6-python-ui-tree-v1"
    const val DEFAULT_SNAPSHOT_NODES = 64
    const val MAX_SNAPSHOT_NODES = 128
    const val DEFAULT_SNAPSHOT_DEPTH = 16
    const val DEFAULT_FIND_NODES = 512
    const val MAX_FIND_NODES = 1_024
    const val MAX_DEPTH = 32
    const val MAX_QUERY_TEXT_BYTES = 1_024
    const val MAX_SET_TEXT_BYTES = 4 * 1_024
    const val MAX_NODE_TEXT_CODE_POINTS = 256
    const val MAX_RETAINED_NODES = 128
    const val MAX_SNAPSHOT_VALUE_BYTES = 48 * 1_024

    val NODE_ID = Regex("node-[1-9][0-9]*-[1-9][0-9]*")
    val QUERY_KEYS = setOf(
        "text",
        "textContains",
        "description",
        "descriptionContains",
        "resourceId",
        "className",
        "clickable",
        "editable",
        "enabled",
        "scrollable",
    )
}

internal data class PythonHostSelectorBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(right >= left && bottom >= top) {
            "Python selector bounds must not be inverted"
        }
    }

    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("left", left)
        addProperty("top", top)
        addProperty("right", right)
        addProperty("bottom", bottom)
    }

    companion object {
        /**
         * Android can report an inverted axis for a partially clipped accessibility node. Keep
         * its anchor and collapse only the invalid axis so the pure-data protocol never invents
         * a visible area or emits bounds which its clients must reject.
         */
        fun fromPlatform(left: Int, top: Int, right: Int, bottom: Int) =
            PythonHostSelectorBounds(
                left = left,
                top = top,
                right = maxOf(left, right),
                bottom = maxOf(top, bottom),
            )
    }
}

/** Detached node data. [id] is opaque outside the execution-scoped Host broker. */
internal data class PythonHostSelectorNode(
    val id: String,
    val parentId: String?,
    val depth: Int,
    val childCount: Int,
    val text: String?,
    val description: String?,
    val resourceId: String?,
    val className: String?,
    val packageName: String?,
    val bounds: PythonHostSelectorBounds,
    val clickable: Boolean,
    val editable: Boolean,
    val enabled: Boolean,
    val focused: Boolean,
    val selected: Boolean,
    val checkable: Boolean,
    val checked: Boolean,
    val scrollable: Boolean,
    val password: Boolean,
    val visibleToUser: Boolean,
    val truncatedFields: List<String>,
) {
    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("id", id)
        if (parentId == null) add("parentId", JsonNull.INSTANCE) else addProperty("parentId", parentId)
        addProperty("depth", depth)
        addProperty("childCount", childCount)
        addNullableString("text", text)
        addNullableString("description", description)
        addNullableString("resourceId", resourceId)
        addNullableString("className", className)
        addNullableString("packageName", packageName)
        add("bounds", bounds.toJson())
        addProperty("clickable", clickable)
        addProperty("editable", editable)
        addProperty("enabled", enabled)
        addProperty("focused", focused)
        addProperty("selected", selected)
        addProperty("checkable", checkable)
        addProperty("checked", checked)
        addProperty("scrollable", scrollable)
        addProperty("password", password)
        addProperty("visibleToUser", visibleToUser)
        add(
            "truncatedFields",
            JsonArray().apply { truncatedFields.forEach(::add) },
        )
    }
}

internal data class PythonHostSelectorSnapshot(
    val generation: Long,
    val truncated: Boolean,
    val nodes: List<PythonHostSelectorNode>,
) {
    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("schema", PythonHostSelectorPolicy.SNAPSHOT_SCHEMA)
        addProperty("generation", generation)
        addProperty("truncated", truncated)
        add("nodes", JsonArray().apply { nodes.forEach { add(it.toJson()) } })
    }
}

internal data class PythonHostSelectorQuery(
    val text: String? = null,
    val textContains: String? = null,
    val description: String? = null,
    val descriptionContains: String? = null,
    val resourceId: String? = null,
    val className: String? = null,
    val clickable: Boolean? = null,
    val editable: Boolean? = null,
    val enabled: Boolean? = null,
    val scrollable: Boolean? = null,
) {
    init {
        require(
            listOf(
                text,
                textContains,
                description,
                descriptionContains,
                resourceId,
                className,
                clickable,
                editable,
                enabled,
                scrollable,
            ).any { it != null },
        ) {
            "Python selector query must contain at least one condition"
        }
    }

    fun matches(value: PythonHostSelectorValues): Boolean =
        (text == null || value.text == text) &&
            (textContains == null || value.text?.contains(textContains) == true) &&
            (description == null || value.description == description) &&
            (descriptionContains == null || value.description?.contains(descriptionContains) == true) &&
            (resourceId == null || value.resourceId == resourceId) &&
            (className == null || value.className == className) &&
            (clickable == null || value.clickable == clickable) &&
            (editable == null || value.editable == editable) &&
            (enabled == null || value.enabled == enabled) &&
            (scrollable == null || value.scrollable == scrollable)
}

/**
 * Owns native accessibility node references for exactly one Python Host broker lifetime.
 * Every public result remains JSON-only; native nodes are recycled on eviction and close.
 */
internal class PythonHostSelectorScope(
    private val activeRoot: () -> AccessibilityNodeInfo?,
) : AutoCloseable {
    private val retainedNodes = LinkedHashMap<String, AccessibilityNodeInfo>()
    private var generation = 0L
    private var nextNodeOrdinal = 0L

    @Synchronized
    fun snapshot(maxNodes: Int, maxDepth: Int): PythonHostSelectorSnapshot {
        require(maxNodes in 1..PythonHostSelectorPolicy.MAX_SNAPSHOT_NODES)
        require(maxDepth in 0..PythonHostSelectorPolicy.MAX_DEPTH)
        clearRetainedNodes(advanceGeneration = true)

        val pending = ArrayDeque<PendingNode>()
        pending.add(PendingNode(requireRoot(), parentId = null, depth = 0))
        val captured = ArrayList<PythonHostSelectorNode>(maxNodes)
        var truncated = false
        try {
            while (pending.isNotEmpty() && captured.size < maxNodes) {
                val current = pending.removeFirst()
                val values = PythonHostSelectorValues.capture(current.node)
                val nodeId = nextNodeId()
                val publicNode = values.toPublicNode(nodeId, current.parentId, current.depth)
                val candidate = PythonHostSelectorSnapshot(
                    generation = generation,
                    truncated = false,
                    nodes = captured + publicNode,
                )
                if (candidate.encodedSize() > PythonHostSelectorPolicy.MAX_SNAPSHOT_VALUE_BYTES) {
                    recycleQuietly(current.node)
                    truncated = true
                    break
                }

                retain(nodeId, current.node)
                captured += publicNode
                if (current.depth < maxDepth) {
                    for (index in 0 until values.childCount) {
                        current.node.getChild(index)?.let { child ->
                            pending.add(PendingNode(child, nodeId, current.depth + 1))
                        }
                    }
                } else if (values.childCount > 0) {
                    truncated = true
                }
            }
            if (pending.isNotEmpty()) truncated = true
            return PythonHostSelectorSnapshot(generation, truncated, captured)
        } catch (error: Throwable) {
            clearRetainedNodes(advanceGeneration = false)
            throw error
        } finally {
            pending.forEach { recycleQuietly(it.node) }
            pending.clear()
        }
    }

    @Synchronized
    fun find(
        query: PythonHostSelectorQuery,
        maxNodes: Int,
        maxDepth: Int,
    ): PythonHostSelectorNode? {
        require(maxNodes in 1..PythonHostSelectorPolicy.MAX_FIND_NODES)
        require(maxDepth in 0..PythonHostSelectorPolicy.MAX_DEPTH)
        ensureGeneration()

        val pending = ArrayDeque<PendingNode>()
        pending.add(PendingNode(requireRoot(), parentId = null, depth = 0))
        var visited = 0
        var depthTruncated = false
        try {
            while (pending.isNotEmpty()) {
                if (visited >= maxNodes) throw PythonHostCapabilitySelectorScanLimitException()
                val current = pending.removeFirst()
                var retained = false
                try {
                    visited += 1
                    val values = PythonHostSelectorValues.capture(current.node)
                    if (query.matches(values)) {
                        val nodeId = nextNodeId()
                        val result = values.toPublicNode(nodeId, parentId = null, depth = current.depth)
                        retain(nodeId, current.node)
                        retained = true
                        return result
                    }
                    if (current.depth < maxDepth) {
                        for (index in 0 until values.childCount) {
                            current.node.getChild(index)?.let { child ->
                                pending.add(PendingNode(child, parentId = null, depth = current.depth + 1))
                            }
                        }
                    } else if (values.childCount > 0) {
                        depthTruncated = true
                    }
                } finally {
                    if (!retained) recycleQuietly(current.node)
                }
            }
            if (depthTruncated) throw PythonHostCapabilitySelectorScanLimitException()
            return null
        } finally {
            pending.forEach { recycleQuietly(it.node) }
            pending.clear()
        }
    }

    @Synchronized
    fun click(nodeId: String): Boolean = withRetainedNode(nodeId) { node ->
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    @Synchronized
    fun setText(nodeId: String, text: String): Boolean = withRetainedNode(nodeId) { node ->
        node.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            },
        )
    }

    @Synchronized
    override fun close() {
        clearRetainedNodes(advanceGeneration = false)
    }

    private fun requireRoot(): AccessibilityNodeInfo = activeRoot()
        ?: throw PythonHostCapabilityAccessibilityUnavailableException()

    private fun ensureGeneration() {
        if (generation == 0L) generation = 1L
    }

    private fun nextNodeId(): String {
        ensureGeneration()
        nextNodeOrdinal += 1L
        return "node-$generation-$nextNodeOrdinal"
    }

    private fun retain(nodeId: String, node: AccessibilityNodeInfo) {
        while (retainedNodes.size >= PythonHostSelectorPolicy.MAX_RETAINED_NODES) {
            val iterator = retainedNodes.entries.iterator()
            val evicted = iterator.next()
            iterator.remove()
            recycleQuietly(evicted.value)
        }
        retainedNodes[nodeId] = node
    }

    private fun <T> withRetainedNode(nodeId: String, action: (AccessibilityNodeInfo) -> T): T {
        val node = retainedNodes[nodeId] ?: throw PythonHostCapabilityStaleNodeException()
        val refreshed = runCatching { node.refresh() }.getOrDefault(false)
        if (!refreshed) {
            retainedNodes.remove(nodeId)
            recycleQuietly(node)
            throw PythonHostCapabilityStaleNodeException()
        }
        return action(node)
    }

    private fun clearRetainedNodes(advanceGeneration: Boolean) {
        retainedNodes.values.forEach(::recycleQuietly)
        retainedNodes.clear()
        nextNodeOrdinal = 0L
        if (advanceGeneration) {
            generation = if (generation == Long.MAX_VALUE) 1L else generation + 1L
        }
    }

    private data class PendingNode(
        val node: AccessibilityNodeInfo,
        val parentId: String?,
        val depth: Int,
    )
}

internal data class PythonHostSelectorValues(
    val childCount: Int,
    val text: String?,
    val description: String?,
    val resourceId: String?,
    val className: String?,
    val packageName: String?,
    val bounds: PythonHostSelectorBounds,
    val clickable: Boolean,
    val editable: Boolean,
    val enabled: Boolean,
    val focused: Boolean,
    val selected: Boolean,
    val checkable: Boolean,
    val checked: Boolean,
    val scrollable: Boolean,
    val password: Boolean,
    val visibleToUser: Boolean,
) {
    fun toPublicNode(id: String, parentId: String?, depth: Int): PythonHostSelectorNode {
        val bounded = listOf(
            "text" to text.boundedNodeText(),
            "description" to description.boundedNodeText(),
            "resourceId" to resourceId.boundedNodeText(),
            "className" to className.boundedNodeText(),
            "packageName" to packageName.boundedNodeText(),
        ).toMap()
        return PythonHostSelectorNode(
            id = id,
            parentId = parentId,
            depth = depth,
            childCount = childCount,
            text = bounded.getValue("text").value,
            description = bounded.getValue("description").value,
            resourceId = bounded.getValue("resourceId").value,
            className = bounded.getValue("className").value,
            packageName = bounded.getValue("packageName").value,
            bounds = bounds,
            clickable = clickable,
            editable = editable,
            enabled = enabled,
            focused = focused,
            selected = selected,
            checkable = checkable,
            checked = checked,
            scrollable = scrollable,
            password = password,
            visibleToUser = visibleToUser,
            truncatedFields = bounded.filterValues(BoundedNodeText::truncated).keys.toList(),
        )
    }

    companion object {
        @Suppress("DEPRECATION")
        fun capture(node: AccessibilityNodeInfo): PythonHostSelectorValues {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            return PythonHostSelectorValues(
                childCount = node.childCount,
                text = node.text?.toString(),
                description = node.contentDescription?.toString(),
                resourceId = node.viewIdResourceName,
                className = node.className?.toString(),
                packageName = node.packageName?.toString(),
                bounds = PythonHostSelectorBounds.fromPlatform(
                    rect.left,
                    rect.top,
                    rect.right,
                    rect.bottom,
                ),
                clickable = node.isClickable,
                editable = node.isEditable,
                enabled = node.isEnabled,
                focused = node.isFocused,
                selected = node.isSelected,
                checkable = node.isCheckable,
                checked = node.isChecked,
                scrollable = node.isScrollable,
                password = node.isPassword,
                visibleToUser = node.isVisibleToUser,
            )
        }
    }
}

private data class BoundedNodeText(val value: String?, val truncated: Boolean)

private fun String?.boundedNodeText(): BoundedNodeText {
    if (this == null) return BoundedNodeText(null, false)
    val count = codePointCount(0, length)
    if (count <= PythonHostSelectorPolicy.MAX_NODE_TEXT_CODE_POINTS) {
        return BoundedNodeText(this, false)
    }
    val end = offsetByCodePoints(0, PythonHostSelectorPolicy.MAX_NODE_TEXT_CODE_POINTS)
    return BoundedNodeText(substring(0, end), true)
}

private fun PythonHostSelectorSnapshot.encodedSize(): Int =
    toJson().toString().toByteArray(StandardCharsets.UTF_8).size

private fun JsonObject.addNullableString(name: String, value: String?) {
    if (value == null) add(name, JsonNull.INSTANCE) else addProperty(name, value)
}

@Suppress("DEPRECATION")
private fun recycleQuietly(node: AccessibilityNodeInfo) {
    runCatching { node.recycle() }
}
