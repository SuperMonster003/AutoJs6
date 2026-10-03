package org.autojs.autojs.core.accessibility.codegen

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.NodeInfoSnapshot
import org.autojs.autojs.core.automator.ActionArgument
import org.autojs.autojs.core.automator.UiNode
import java.io.File
import java.util.ArrayDeque

/**
 * A layout-file tree usable on the JVM without constructing Android-backed NodeInfo objects.
 * Each captured node has one wrapper; parent/child navigation preserves object identity.
 * Captured depth and index metadata, full resource ids and uncaptured boolean defaults follow
 * InspectorNode. Child count uses the children actually captured.
 * zh-CN: 无需构造依赖 Android 的 NodeInfo, 即可在 JVM 使用的布局文件树. 每个节点只有一个包装对象,
 * 父子导航保持对象身份. 深度, 序号, 完整资源 id 与未捕获布尔属性的默认值沿用 InspectorNode 的语义;
 * 子节点数采用实际捕获的数量.
 */
class SnapshotNode private constructor(
    val snapshot: NodeInfoSnapshot,
    private val parentNode: SnapshotNode?,
) : UiNode<SnapshotNode> {

    private val children = ArrayList<SnapshotNode>()

    init {
        require(snapshot.bounds.size == 4) { "Layout node bounds must contain four coordinates" }
    }

    constructor(snapshot: NodeInfoSnapshot) : this(snapshot, null) {
        // Iterative construction also handles deeply nested captured trees.
        // zh-CN: 迭代构造同样适用于深度很大的捕获树.
        val pending = ArrayDeque<SnapshotNode>()
        pending.add(this)
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            for (child in node.snapshot.children) {
                val wrapped = SnapshotNode(child, node)
                node.children.add(wrapped)
                pending.add(wrapped)
            }
        }
    }

    override fun parent(): SnapshotNode? = parentNode
    override fun child(i: Int): SnapshotNode? = children.getOrNull(if (i < 0) i + children.size else i)
    override fun childCount(): Int = children.size
    override fun indexInParent(): Int = snapshot.indexInParent
    override fun depth(): Int = snapshot.depth
    override fun drawingOrder(): Int = snapshot.drawingOrder

    override fun packageName(): String? = snapshot.packageName
    override fun className(): String? = snapshot.className
    override fun id(): String? = snapshot.fullId
    override fun idHex(): String? = snapshot.idHex
    override fun text(): String = snapshot.text
    override fun desc(): String? = snapshot.desc

    override fun left(): Int = snapshot.bounds[0]
    override fun top(): Int = snapshot.bounds[1]
    override fun right(): Int = snapshot.bounds[2]
    override fun bottom(): Int = snapshot.bounds[3]
    override fun width(): Int = right() - left()
    override fun height(): Int = bottom() - top()
    override fun centerX(): Int = (left() + right()) shr 1
    override fun centerY(): Int = (top() + bottom()) shr 1

    override fun checkable(): Boolean = snapshot.checkable
    override fun checked(): Boolean = snapshot.checked
    override fun focusable(): Boolean = snapshot.focusable
    override fun focused(): Boolean = snapshot.focused
    override fun visibleToUser(): Boolean = snapshot.visibleToUser
    override fun accessibilityFocused(): Boolean = snapshot.accessibilityFocused
    override fun selected(): Boolean = snapshot.selected
    override fun clickable(): Boolean = snapshot.clickable
    override fun longClickable(): Boolean = snapshot.longClickable
    override fun enabled(): Boolean = snapshot.enabled
    override fun password(): Boolean = false
    override fun scrollable(): Boolean = snapshot.scrollable
    override fun editable(): Boolean = snapshot.editable
    override fun contentInvalid(): Boolean = false
    override fun contextClickable(): Boolean = snapshot.contextClickable
    override fun multiLine(): Boolean = false
    override fun dismissable(): Boolean = snapshot.dismissable

    override fun row(): Int = snapshot.row
    override fun column(): Int = snapshot.column
    override fun rowSpan(): Int = snapshot.rowSpan
    override fun columnSpan(): Int = snapshot.columnSpan
    override fun rowCount(): Int = snapshot.rowCount
    override fun columnCount(): Int = snapshot.columnCount
    override fun actionNames(): List<String> = snapshot.actionNames
    override fun performAction(action: Int, vararg arguments: ActionArgument): Boolean = false
    override fun recycle() = Unit

    companion object {
        /** Reads the same envelope as LayoutSnapshotFiles, without its Android dependencies.
         * zh-CN: 读取与 LayoutSnapshotFiles 相同的信封结构, 不引入其 Android 依赖. */
        fun load(file: File): SnapshotNode = file.reader(Charsets.UTF_8).use { reader ->
            val envelope = JsonParser.parseReader(reader)
            require(envelope.isJsonObject) { "Layout file must contain an object" }
            val root = envelope.asJsonObject.get("root")
            require(root != null && root.isJsonObject) { "Layout file must contain a root node" }
            SnapshotNode(Gson().fromJson(root, NodeInfoSnapshot::class.java))
        }
    }
}
