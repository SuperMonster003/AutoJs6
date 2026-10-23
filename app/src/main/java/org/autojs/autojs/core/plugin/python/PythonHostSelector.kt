package org.autojs.autojs.core.plugin.python

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.core.automator.filter.Filter
import org.autojs.autojs.core.automator.filter.Selector
import org.autojs.autojs.core.automator.filter.StringMatcher
import org.autojs.autojs.core.automator.filter.StringProperty
import org.autojs.autojs.core.automator.filter.StringPropertyFilter
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

    /**
     * The query as the script runtime's filters (roadmap P5.4), ANDed by [Selector]: literal
     * equality and substring on the string properties, equality on the boolean ones. The string
     * conditions address [StringProperty.ID] and [StringProperty.CLASS_NAME] directly rather than
     * through `IdFilter` / `ClassNameFilter`, whose short-form expansion (`login` for
     * `pkg:id/login`, `Button` for `android.widget.Button`) would change the protocol's literal
     * comparison; a node without the property (no description, no id, no class name) never
     * matches, as before.
     * zh-CN: 查询编译为脚本运行时的过滤器 (Roadmap P5.4), 由 [Selector] 做 AND: 字符串属性按字面相等与子串, 布尔属性按相等.
     * 字符串条件直接面向 [StringProperty.ID] 与 [StringProperty.CLASS_NAME], 不经 `IdFilter` / `ClassNameFilter`
     * (它们的短形式展开 (`pkg:id/login` 的 `login`, `android.widget.Button` 的 `Button`) 会改变协议的字面比较); 没有该属性的节点
     * (无描述, 无 id, 无类名) 一如既往不匹配.
     */
    fun toFilter(): Filter = Selector().apply {
        text?.let { add(StringPropertyFilter(StringProperty.TEXT, StringMatcher.equals(it))) }
        textContains?.let { add(StringPropertyFilter(StringProperty.TEXT, StringMatcher.contains(it))) }
        description?.let { add(StringPropertyFilter(StringProperty.DESC, StringMatcher.equals(it))) }
        descriptionContains?.let { add(StringPropertyFilter(StringProperty.DESC, StringMatcher.contains(it))) }
        resourceId?.let { add(StringPropertyFilter(StringProperty.ID, StringMatcher.equals(it))) }
        className?.let { add(StringPropertyFilter(StringProperty.CLASS_NAME, StringMatcher.equals(it))) }
        clickable?.let { add(BooleanFilter(BooleanFilter.CLICKABLE, it)) }
        editable?.let { add(BooleanFilter(BooleanFilter.EDITABLE, it)) }
        enabled?.let { add(BooleanFilter(BooleanFilter.ENABLED, it)) }
        scrollable?.let { add(BooleanFilter(BooleanFilter.SCROLLABLE, it)) }
    }
}

/**
 * Owns the retained node references of exactly one Python Host broker lifetime, over any [UiNode]
 * tree (roadmap P5.4): the breadth-first walks, the query matching ([PythonHostSelectorQuery.toFilter],
 * the runtime's registry filters) and the recycling ([lifecycle], a no-op from API 33 on) are the
 * script runtime's; [platform] supplies the root, the refresh, the two node actions and the wire
 * values (the Android side runs it over `UiObject`, the JVM tests over `FakeUiNode`). Every public
 * result remains JSON-only; retained nodes are released on eviction, on the next snapshot and on
 * [close]. Node ids are `node-<generation>-<ordinal>`: the generation advances with every snapshot,
 * the ordinal restarts with it.
 * zh-CN: 恰好拥有一次 Python Host broker 生命周期内保留的节点引用, 面向任意 [UiNode] 树 (Roadmap P5.4): 广度优先遍历,
 * 查询匹配 ([PythonHostSelectorQuery.toFilter], 运行时的 registry 过滤器) 与回收 ([lifecycle], API 33 起为空操作) 都是脚本运行时的;
 * [platform] 提供根节点, 刷新, 两个节点动作与线协议值 (Android 侧以 `UiObject` 运行, JVM 测试以 `FakeUiNode` 运行). 全部公开结果仍
 * 只有 JSON; 保留的节点在驱逐, 下一次快照与 [close] 时释放. 节点 id 为 `node-<generation>-<ordinal>`: 每次快照推进 generation,
 * ordinal 随之重新计数.
 */
internal class PythonHostSelectorScope<N : UiNode<N>>(
    private val platform: Platform<N>,
    private val lifecycle: NodeLifecycle,
) : AutoCloseable {

    /** What the platform contributes to the scope. zh-CN: 平台为作用域提供的部分. */
    interface Platform<N : UiNode<N>> {

        /** The root of the active window, `null` when there is none. zh-CN: 活动窗口的根节点, 没有时为 `null`. */
        fun activeRoot(): N?

        /** Re-reads [node] from the platform; `false` when it is gone. zh-CN: 从平台重读 [node]; 已消失时为 `false`. */
        fun refresh(node: N): Boolean

        /** `ACTION_CLICK` on [node] itself. zh-CN: [node] 自身的 `ACTION_CLICK`. */
        fun click(node: N): Boolean

        /** `ACTION_SET_TEXT` on [node]. zh-CN: [node] 的 `ACTION_SET_TEXT`. */
        fun setText(node: N, text: String): Boolean

        /**
         * The wire values of [node]; the default reads them through [UiNode], a platform that keeps
         * more (the null text the protocol distinguishes from an empty one) overrides.
         * zh-CN: [node] 的线协议值; 默认经 [UiNode] 读取, 保留更多信息的平台 (协议区分 null 文本与空文本) 重写.
         */
        fun capture(node: N): PythonHostSelectorValues = PythonHostSelectorValues.of(node)

    }

    private val retainedNodes = LinkedHashMap<String, N>()
    private var generation = 0L
    private var nextNodeOrdinal = 0L

    @Synchronized
    fun snapshot(maxNodes: Int, maxDepth: Int): PythonHostSelectorSnapshot {
        require(maxNodes in 1..PythonHostSelectorPolicy.MAX_SNAPSHOT_NODES)
        require(maxDepth in 0..PythonHostSelectorPolicy.MAX_DEPTH)
        clearRetainedNodes(advanceGeneration = true)

        val pending = ArrayDeque<PendingNode<N>>()
        pending.add(PendingNode(requireRoot(), parentId = null, depth = 0))
        val captured = ArrayList<PythonHostSelectorNode>(maxNodes)
        var truncated = false
        try {
            while (pending.isNotEmpty() && captured.size < maxNodes) {
                val current = pending.removeFirst()
                // Released here unless retained, also when reading it throws: it is neither pending nor retained any more
                // (docs/memory-phase2/ROADMAP.md C44).
                // zh-CN: 未被保留时在此释放, 读取它抛出异常时也一样: 它已不在待处理队列中, 也未被保留 (docs/memory-phase2/ROADMAP.md C44).
                var retained = false
                try {
                    val values = platform.capture(current.node)
                    val nodeId = nextNodeId()
                    val publicNode = values.toPublicNode(nodeId, current.parentId, current.depth)
                    val candidate = PythonHostSelectorSnapshot(
                        generation = generation,
                        truncated = false,
                        nodes = captured + publicNode,
                    )
                    if (candidate.encodedSize() > PythonHostSelectorPolicy.MAX_SNAPSHOT_VALUE_BYTES) {
                        truncated = true
                        break
                    }

                    retain(nodeId, current.node)
                    retained = true
                    captured += publicNode
                    if (current.depth < maxDepth) {
                        for (index in 0 until values.childCount) {
                            current.node.child(index)?.let { child ->
                                pending.add(PendingNode(child, nodeId, current.depth + 1))
                            }
                        }
                    } else if (values.childCount > 0) {
                        truncated = true
                    }
                } finally {
                    if (!retained) release(current.node)
                }
            }
            if (pending.isNotEmpty()) truncated = true
            return PythonHostSelectorSnapshot(generation, truncated, captured)
        } catch (error: Throwable) {
            clearRetainedNodes(advanceGeneration = false)
            throw error
        } finally {
            pending.forEach { release(it.node) }
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
        val filter = query.toFilter()

        val pending = ArrayDeque<PendingNode<N>>()
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
                    if (filter.filter(current.node)) {
                        val nodeId = nextNodeId()
                        val result = platform.capture(current.node).toPublicNode(nodeId, parentId = null, depth = current.depth)
                        retain(nodeId, current.node)
                        retained = true
                        return result
                    }
                    val childCount = current.node.childCount()
                    if (current.depth < maxDepth) {
                        for (index in 0 until childCount) {
                            current.node.child(index)?.let { child ->
                                pending.add(PendingNode(child, parentId = null, depth = current.depth + 1))
                            }
                        }
                    } else if (childCount > 0) {
                        depthTruncated = true
                    }
                } finally {
                    if (!retained) release(current.node)
                }
            }
            if (depthTruncated) throw PythonHostCapabilitySelectorScanLimitException()
            return null
        } finally {
            pending.forEach { release(it.node) }
            pending.clear()
        }
    }

    @Synchronized
    fun click(nodeId: String): Boolean = withRetainedNode(nodeId) { node -> platform.click(node) }

    @Synchronized
    fun setText(nodeId: String, text: String): Boolean = withRetainedNode(nodeId) { node -> platform.setText(node, text) }

    @Synchronized
    override fun close() {
        clearRetainedNodes(advanceGeneration = false)
    }

    private fun requireRoot(): N = platform.activeRoot()
        ?: throw PythonHostCapabilityAccessibilityUnavailableException()

    private fun ensureGeneration() {
        if (generation == 0L) generation = 1L
    }

    private fun nextNodeId(): String {
        ensureGeneration()
        nextNodeOrdinal += 1L
        return "node-$generation-$nextNodeOrdinal"
    }

    private fun retain(nodeId: String, node: N) {
        while (retainedNodes.size >= PythonHostSelectorPolicy.MAX_RETAINED_NODES) {
            val iterator = retainedNodes.entries.iterator()
            val evicted = iterator.next()
            iterator.remove()
            release(evicted.value)
        }
        retainedNodes[nodeId] = node
    }

    private fun <T> withRetainedNode(nodeId: String, action: (N) -> T): T {
        val node = retainedNodes[nodeId] ?: throw PythonHostCapabilityStaleNodeException()
        val refreshed = runCatching { platform.refresh(node) }.getOrDefault(false)
        if (!refreshed) {
            retainedNodes.remove(nodeId)
            release(node)
            throw PythonHostCapabilityStaleNodeException()
        }
        return action(node)
    }

    private fun clearRetainedNodes(advanceGeneration: Boolean) {
        retainedNodes.values.forEach(::release)
        retainedNodes.clear()
        nextNodeOrdinal = 0L
        if (advanceGeneration) {
            generation = if (generation == Long.MAX_VALUE) 1L else generation + 1L
        }
    }

    private fun release(node: N) {
        lifecycle.release { node.recycle() }
    }

    private data class PendingNode<N>(
        val node: N,
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
        /**
         * The wire values read through [UiNode]. `text` is the node's text as [UiNode.text] reports
         * it (empty when the node has none); the Android platform restores the null the protocol
         * distinguishes from an empty text.
         * zh-CN: 经 [UiNode] 读取的线协议值. `text` 为 [UiNode.text] 报告的文本 (节点无文本时为空串); Android 平台补回协议区分于空文本的 null.
         */
        fun of(node: UiNode<*>): PythonHostSelectorValues = PythonHostSelectorValues(
            childCount = node.childCount(),
            text = node.text(),
            description = node.desc(),
            resourceId = node.id(),
            className = node.className(),
            packageName = node.packageName(),
            bounds = PythonHostSelectorBounds.fromPlatform(
                node.left(),
                node.top(),
                node.right(),
                node.bottom(),
            ),
            clickable = node.clickable(),
            editable = node.editable(),
            enabled = node.enabled(),
            focused = node.focused(),
            selected = node.selected(),
            checkable = node.checkable(),
            checked = node.checked(),
            scrollable = node.scrollable(),
            password = node.password(),
            visibleToUser = node.visibleToUser(),
        )
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
