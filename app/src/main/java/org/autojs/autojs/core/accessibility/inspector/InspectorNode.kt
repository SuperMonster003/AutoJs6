package org.autojs.autojs.core.accessibility.inspector

import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.core.automator.ActionArgument
import org.autojs.autojs.core.automator.UiNode

/**
 * A captured [NodeInfo] seen through the [UiNode] interface, so that the script selector filters
 * (`text`, `clickable`, `boundsInside`, ...) evaluate over the layout inspector's detached tree
 * exactly as they do over live nodes (roadmap D10). Wrappers are created on demand by [parent] and
 * [child] and compare equal when they wrap the same [NodeInfo], so a set of wrappers can be probed
 * with a fresh wrapper. [id] is the full resource id (`package:id/entry`), as [UiNode.id] specifies.
 *
 * [NodeInfo] does not capture `password`, `contentInvalid` and `multiLine`; they read as `false`.
 * Actions cannot be performed on a snapshot, so [performAction] reports `false` and [recycle] is a
 * no-op.
 *
 * zh-CN: 以 [UiNode] 接口观察已捕获的 [NodeInfo], 使脚本选择器的过滤器 (`text`, `clickable`, `boundsInside` 等) 对布局分析
 * 的独立节点树求值, 与对实时节点求值完全一致 (Roadmap D10). 包装对象由 [parent] / [child] 按需创建, 包装同一 [NodeInfo] 时
 * 相等, 因此可以用新建的包装对象探查包装对象集合. [id] 为完整资源 id (`package:id/entry`), 同 [UiNode.id] 的约定.
 * [NodeInfo] 未捕获 `password` / `contentInvalid` / `multiLine`, 三者读为 `false`; 快照上无法执行动作, [performAction]
 * 返回 `false`, [recycle] 为空操作.
 *
 * Created by SuperMonster003 on Oct 2, 2026.
 */
class InspectorNode(val info: NodeInfo) : UiNode<InspectorNode> {

    override fun parent(): InspectorNode? = info.parent?.let(::InspectorNode)

    override fun child(i: Int): InspectorNode? {
        val children = info.children
        return children.getOrNull(if (i < 0) i + children.size else i)?.let(::InspectorNode)
    }

    /** The children actually captured, which [child] indexes; may be fewer than the platform's count. zh-CN: 实际捕获到的子节点数 ([child] 按其索引), 可能少于平台报告的数量. */
    override fun childCount(): Int = info.children.size

    override fun indexInParent(): Int = info.indexInParent

    override fun depth(): Int = info.depth

    override fun drawingOrder(): Int = info.drawingOrder

    override fun packageName(): String? = info.packageName

    override fun className(): String? = info.className

    override fun id(): String? = info.fullId

    override fun idHex(): String? = info.idHex

    override fun text(): String = info.text

    override fun desc(): String? = info.desc

    override fun left(): Int = info.boundsInScreen.left

    override fun top(): Int = info.boundsInScreen.top

    override fun right(): Int = info.boundsInScreen.right

    override fun bottom(): Int = info.boundsInScreen.bottom

    override fun width(): Int = info.boundsInScreen.width()

    override fun height(): Int = info.boundsInScreen.height()

    override fun centerX(): Int = info.boundsInScreen.centerX()

    override fun centerY(): Int = info.boundsInScreen.centerY()

    override fun checkable(): Boolean = info.checkable

    override fun checked(): Boolean = info.checked

    override fun focusable(): Boolean = info.focusable

    override fun focused(): Boolean = info.focused

    override fun visibleToUser(): Boolean = info.visibleToUser

    override fun accessibilityFocused(): Boolean = info.accessibilityFocused

    override fun selected(): Boolean = info.selected

    override fun clickable(): Boolean = info.clickable

    override fun longClickable(): Boolean = info.longClickable

    override fun enabled(): Boolean = info.enabled

    override fun password(): Boolean = false

    override fun scrollable(): Boolean = info.scrollable

    override fun editable(): Boolean = info.editable

    override fun contentInvalid(): Boolean = false

    override fun contextClickable(): Boolean = info.contextClickable

    override fun multiLine(): Boolean = false

    override fun dismissable(): Boolean = info.dismissable

    override fun row(): Int = info.row

    override fun column(): Int = info.column

    override fun rowSpan(): Int = info.rowSpan

    override fun columnSpan(): Int = info.columnSpan

    override fun rowCount(): Int = info.rowCount

    override fun columnCount(): Int = info.columnCount

    override fun actionNames(): List<String> = info.actionNames

    override fun performAction(action: Int, vararg arguments: ActionArgument): Boolean = false

    override fun recycle() {
        /* Nothing to give back: the snapshot owns no platform node. zh-CN: 无需归还: 快照不持有平台节点. */
    }

    override fun equals(other: Any?): Boolean = other is InspectorNode && other.info === info

    override fun hashCode(): Int = System.identityHashCode(info)

    override fun toString(): String = "InspectorNode($info)"

}
