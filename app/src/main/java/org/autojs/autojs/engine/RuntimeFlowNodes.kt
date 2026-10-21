package org.autojs.autojs.engine

import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.NodeRecyclePolicy
import org.autojs.autojs.core.accessibility.node.default
import org.autojs.autojs.core.accessibility.node.release
import org.autojs.autojs.core.automator.search.DFS
import org.autojs.autojs.core.automator.ActionArgument
import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.flow.FlowError
import org.json.JSONObject
import java.io.Closeable
import java.util.Collections
import java.util.IdentityHashMap

/** A tool owns every navigation result until it has serialized its outcome. No nodes reach JS. */
internal class RuntimeFlowNodes(private val check: () -> Unit, private val rootProvider: () -> UiObject?) : Closeable {
    private val owned = Collections.newSetFromMap(IdentityHashMap<UiObject, Boolean>())
    private val borrowed = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
    private val traversal = NodeLifecycle(NodeRecyclePolicy.NO_OP)

    fun wrap(value: UiObject?): RuntimeFlowNode? = value?.let {
        owned += it
        check()
        require(owned.size <= 100000) { "Flow operation exceeded its private node limit" }
        RuntimeFlowNode(it, this)
    }
    fun root(): RuntimeFlowNode? {
        check()
        return rootProvider()?.let { node -> node.unwrap()?.let { borrowed += it }; wrap(node) }
    }
    fun search(selector: JSONObject, scope: RuntimeFlowNode?, all: Boolean = false): List<RuntimeFlowNode> {
        check()
        val filter = RuntimeFlowQuery.filter(selector)
        val root = scope ?: root() ?: return emptyList()
        return DFS.search(root, filter, limit = if (all) Int.MAX_VALUE else 1, lifecycle = traversal)
    }
    fun reference(snapshot: JSONObject): RuntimeFlowNode {
        check()
        val root = root() ?: throw FlowError.InvalidTarget("target", reason = "window missing")
        require(snapshot.has("windowId")) { "Obtain a fresh Flow snapshot from the matching host before acting" }
        if (root.value.windowId != snapshot.getInt("windowId")) throw FlowError.InvalidTarget("target", reason = "window changed")
        val identity = listOf("packageName", "className", "id", "text", "desc", "depth", "indexInParent", "bounds")
        identity.forEach { require(snapshot.has(it)) { "Incomplete Flow node snapshot: $it" } }
        val filter = object : org.autojs.autojs.core.automator.filter.Filter {
            override fun filter(node: UiNode<*>): Boolean {
                check()
                val value = node as RuntimeFlowNode
                val now = RuntimeFlowQuery.snapshot(value.value)
                return identity.all { key -> if (key == "bounds") {
                    listOf("left", "top", "right", "bottom").all { coordinate ->
                        now.getJSONObject(key).getInt(coordinate) == snapshot.getJSONObject(key).getInt(coordinate)
                    }
                } else now.get(key).toString() == snapshot.get(key).toString() }
            }
        }
        val matches = DFS.search(root, filter, limit = 2, lifecycle = traversal)
        if (matches.size != 1) throw FlowError.InvalidTarget("target", reason = if (matches.isEmpty()) "stale snapshot" else "ambiguous snapshot")
        return matches.single()
    }
    override fun close() {
        val released = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        for (node in owned) node.unwrap()?.let { raw ->
            if (raw !in borrowed && released.add(raw)) NodeLifecycle.default.release(node)
        }
        owned.clear(); borrowed.clear()
    }
    fun checkpoint() = check()
}

internal class RuntimeFlowNode(val value: UiObject, private val arena: RuntimeFlowNodes) : UiNode<RuntimeFlowNode> {
    override fun parent() = arena.wrap(value.parent())
    override fun child(i: Int) = arena.wrap(value.child(i))
    override fun childCount() = value.childCount()
    override fun indexInParent() = value.indexInParent()
    override fun depth() = value.depth()
    override fun drawingOrder() = value.drawingOrder()
    override fun packageName() = value.packageName()
    override fun className() = value.className()
    override fun id() = value.id()
    override fun idHex() = value.idHex()
    override fun text() = value.text()
    override fun desc() = value.desc()
    override fun left() = value.left()
    override fun top() = value.top()
    override fun right() = value.right()
    override fun bottom() = value.bottom()
    override fun width() = value.width()
    override fun height() = value.height()
    override fun centerX() = value.centerX()
    override fun centerY() = value.centerY()
    override fun checkable() = value.checkable()
    override fun checked() = value.checked()
    override fun focusable() = value.focusable()
    override fun focused() = value.focused()
    override fun visibleToUser() = value.visibleToUser()
    override fun accessibilityFocused() = value.accessibilityFocused()
    override fun selected() = value.selected()
    override fun clickable() = value.clickable()
    override fun longClickable() = value.longClickable()
    override fun enabled() = value.enabled()
    override fun password() = value.password()
    override fun scrollable() = value.scrollable()
    override fun editable() = value.editable()
    override fun contentInvalid() = value.contentInvalid()
    override fun contextClickable() = value.contextClickable()
    override fun multiLine() = value.multiLine()
    override fun dismissable() = value.dismissable()
    override fun row() = value.row()
    override fun column() = value.column()
    override fun rowSpan() = value.rowSpan()
    override fun columnSpan() = value.columnSpan()
    override fun rowCount() = value.rowCount()
    override fun columnCount() = value.columnCount()
    override fun actionNames() = value.actionNames()
    override fun performAction(action: Int, vararg arguments: ActionArgument): Boolean {
        arena.checkpoint(); return value.performAction(action, *arguments)
    }
    override fun recycle() = Unit // The arena owns the private raw nodes, including ancestors.
}
