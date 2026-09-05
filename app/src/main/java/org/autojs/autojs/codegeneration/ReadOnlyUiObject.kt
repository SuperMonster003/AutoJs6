package org.autojs.autojs.codegeneration

import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.core.automator.ActionArgument
import org.autojs.autojs.core.automator.UiNode

/**
 * A captured [NodeInfo] tree seen as a [UiNode] tree, so that the selector search and the code
 * generator work on the layout inspector's snapshot exactly as on live nodes. Read-only: actions
 * do nothing and report `false`. [id] is the id entry (`btn`, not `pkg:id/btn`), the form the
 * generated `id(...)` selector uses; the properties the snapshot does not record (`password`,
 * `contentInvalid`, `multiLine`) are `false`.
 * zh-CN: 把捕获的 [NodeInfo] 树视为 [UiNode] 树, 使选择器搜索与代码生成器在布局分析器的快照上与在真实节点上一样工作.
 * 只读: 动作不做任何事并返回 `false`. [id] 为 id 条目名 (`btn` 而非 `pkg:id/btn`), 即生成的 `id(...)` 选择器所用的形式;
 * 快照未记录的属性 (`password`, `contentInvalid`, `multiLine`) 为 `false`.
 *
 * Created by Stardust on Nov 5, 2017.
 * Transformed by SuperMonster003 on Sep 6, 2026 (a UiNode over the snapshot instead of a UiObject subclass).
 */
class ReadOnlyUiObject(val info: NodeInfo, private val index: Int = UNKNOWN_INDEX) : UiNode<ReadOnlyUiObject> {

    /* Tree. zh-CN: 树. */

    override fun parent(): ReadOnlyUiObject? = info.parent?.let { ReadOnlyUiObject(it) }

    override fun child(i: Int): ReadOnlyUiObject? = info.children.getOrNull(i)?.let { ReadOnlyUiObject(it, i) }

    override fun childCount(): Int = info.children.size

    // Only child(i) knows the index; the root and the nodes reached through parent() report -1, as
    // they always have. The code generator's "parent().child(i)" path therefore stays unreachable
    // for them (it also lacks a dot before `child`); making it live is a generator change, not a port.
    // zh-CN: 只有 child(i) 知道序号; 根节点与经 parent() 到达的节点一如既往报告 -1. 代码生成器的 "parent().child(i)" 路径
    // 因此对它们仍不可达 (该路径在 `child` 前还缺一个点); 让它生效是生成器的改动, 不属于本次移植.
    override fun indexInParent(): Int = index

    override fun depth(): Int = info.depth

    override fun drawingOrder(): Int = info.drawingOrder

    /* Identity and text. zh-CN: 标识与文本. */

    override fun packageName(): String? = info.packageName

    override fun className(): String? = info.className

    override fun id(): String? = info.id

    override fun idHex(): String? = info.idHex

    override fun text(): String = info.text

    override fun desc(): String? = info.desc

    /* Bounds on screen. zh-CN: 屏幕边界. */

    override fun left(): Int = info.boundsInScreen.left

    override fun top(): Int = info.boundsInScreen.top

    override fun right(): Int = info.boundsInScreen.right

    override fun bottom(): Int = info.boundsInScreen.bottom

    override fun width(): Int = info.boundsInScreen.width()

    override fun height(): Int = info.boundsInScreen.height()

    override fun centerX(): Int = info.boundsInScreen.centerX()

    override fun centerY(): Int = info.boundsInScreen.centerY()

    /* Boolean properties. zh-CN: 布尔属性. */

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

    /* Collection info. zh-CN: 集合信息. */

    override fun row(): Int = info.row

    override fun column(): Int = info.column

    override fun rowSpan(): Int = info.rowSpan

    override fun columnSpan(): Int = info.columnSpan

    override fun rowCount(): Int = info.rowCount

    override fun columnCount(): Int = info.columnCount

    /* Actions and lifecycle. zh-CN: 动作与生命周期. */

    override fun actionNames(): List<String> = info.actionNames

    override fun performAction(action: Int, vararg arguments: ActionArgument): Boolean = false

    override fun recycle() {
        /* Nothing to release. zh-CN: 无需释放. */
    }

    override fun equals(other: Any?): Boolean = this === other || other is ReadOnlyUiObject && other.info === info

    override fun hashCode(): Int = System.identityHashCode(info)

    override fun toString(): String = info.toString()

    companion object {

        private const val UNKNOWN_INDEX = -1

    }

}
