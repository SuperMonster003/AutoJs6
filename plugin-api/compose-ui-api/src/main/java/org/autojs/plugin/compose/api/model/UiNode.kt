package org.autojs.plugin.compose.api.model

import org.autojs.plugin.compose.api.ComposeUiLimits
import org.autojs.plugin.compose.api.ComposeUiComponents
import org.autojs.plugin.compose.api.ComposeUiModifiers
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.ComposeUiThemeColors

class ModifierOp(name: String, args: List<UiValue> = emptyList(), val scope: ScopeKind = ScopeKind.ANY) : ComposeParcelable() {
    val name = name(name)
    val args = frozenList(args, ComposeUiLimits.MAX_MODIFIER_OPS)
    override fun fields() = listOf(name, args, scope)
    companion object { @JvmField val CREATOR = creator<ModifierOp>() }
}

class UiNode(
    nodeId: Int,
    type: String,
    key: String? = null,
    props: Map<String, UiValue> = emptyMap(),
    modifier: List<ModifierOp> = emptyList(),
    children: List<Int> = emptyList(),
    slots: Map<String, Int> = emptyMap(),
    callbacks: Map<String, Int> = emptyMap(),
) : ComposeParcelable() {
    val nodeId = positive(nodeId)
    val type = name(type)
    val key = key?.let(::text)
    val props = frozenMap(props, ComposeUiLimits.MAX_PROPERTIES_PER_NODE)
    val modifier = frozenList(modifier, ComposeUiLimits.MAX_MODIFIER_OPS)
    val children = frozenList(children, if (type == ComposeUiComponents.LAZY_COLUMN || type == ComposeUiComponents.LAZY_ROW)
        ComposeUiLimits.MAX_LAZY_CHILDREN_PER_NODE else ComposeUiLimits.MAX_CHILDREN_PER_NODE)
    val slots = frozenMap(slots, ComposeUiLimits.MAX_SLOTS_PER_NODE)
    val callbacks = frozenMap(callbacks, ComposeUiLimits.MAX_PROPERTIES_PER_NODE)
    init {
        this.children.forEach(::positive)
        this.slots.values.forEach(::positive)
        this.callbacks.values.forEach(::positive)
    }
    override fun fields() = listOf(nodeId, type, key, props, modifier, children, slots, callbacks)
    companion object { @JvmField val CREATOR = creator<UiNode>() }
}

/** A closed subtree: every child/slot ID resolves exactly once; no orphan, cycle or shared child. */
class UiTree(rootId: Int, nodes: List<UiNode>) : ComposeParcelable() {
    val rootId = positive(rootId)
    val nodes = frozenList(nodes, ComposeUiLimits.MAX_NODES_PER_SESSION)
    init {
        val byId = this.nodes.associateBy { it.nodeId }
        if (byId.size != this.nodes.size || rootId !in byId) invalid("Duplicate node ID or missing root")
        val pending = java.util.ArrayDeque<Pair<Int, Int>>()
        val visited = HashSet<Int>()
        pending.add(rootId to 1)
        val callbacks = HashSet<Int>()
        while (pending.isNotEmpty()) {
            val (id, depth) = pending.removeLast()
            limit(depth <= ComposeUiLimits.MAX_DEPTH, "Tree depth exceeds contract limit")
            if (!visited.add(id)) invalid("Tree contains a cycle or shared child")
            val node = byId[id] ?: invalid("Unresolved child ID")
            callbacks.addAll(node.callbacks.values)
            node.modifier.filter { it.name == ComposeUiModifiers.CLICKABLE }.forEach { op ->
                (op.args.firstOrNull() as? UiValue.Num)?.value?.let { id ->
                    if (id > 0 && id <= Int.MAX_VALUE && id % 1.0 == 0.0) callbacks.add(id.toInt())
                }
            }
            limit(callbacks.size <= ComposeUiLimits.MAX_CALLBACKS_PER_SESSION, "Tree callback count exceeds contract limit")
            val keys = HashSet<String>()
            node.children.forEach { child ->
                byId[child]?.key?.let { key ->
                    if (!keys.add(key)) throw ComposeUiContractException(ComposeUiErrorCodes.DUPLICATE_KEY, nodeId = id)
                }
            }
            (node.children + node.slots.values).forEach { child -> pending.add(child to depth + 1) }
        }
        if (visited.size != byId.size) invalid("Tree contains unreachable nodes")
    }
    override fun fields() = listOf(rootId, nodes)
    companion object { @JvmField val CREATOR = creator<UiTree>() }
}

class ThemeSpec(
    val seedArgb: Int? = null,
    colorOverrides: Map<String, Int> = emptyMap(),
    val dark: Boolean? = null,
    val dynamicColor: Boolean = false,
    fontFamily: String? = null,
    fontScale: Double? = null,
) : ComposeParcelable() {
    val colorOverrides = frozenMap(colorOverrides, ComposeUiLimits.MAX_THEME_COLORS)
    val fontFamily = fontFamily?.let(::name)
    val fontScale = fontScale?.let(UiValue::dimension)
    init {
        if (fontScale != null && fontScale <= 0) invalid("Font scale must be positive")
        this.colorOverrides.keys.firstOrNull { it !in ComposeUiThemeColors.names }?.let {
            throw ComposeUiContractException(ComposeUiErrorCodes.UNKNOWN_PROP, prop = it)
        }
    }
    override fun fields() = listOf(seedArgb, colorOverrides, dark, dynamicColor, fontFamily, fontScale)
    companion object { @JvmField val CREATOR = creator<ThemeSpec>() }
}
