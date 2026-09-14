package org.autojs.autojs.core.accessibility

import androidx.annotation.Keep

/**
 * JSON-friendly copy of a [NodeInfo] tree, the unit stored in a layout file. Rects are
 * `[left, top, right, bottom]`, the center is `[x, y]`; every property has a default so files
 * written by older or newer versions still load.
 * zh-CN: [NodeInfo] 树的 JSON 友好副本, 即布局文件中存储的单元. 矩形为 `[left, top, right, bottom]`, 中心点为 `[x, y]`;
 * 每个属性都有默认值, 旧版或新版写出的文件仍可读取.
 *
 * Created by SuperMonster003 on Sep 14, 2026.
 */
@Keep
class NodeInfoSnapshot(
    var packageName: String? = null,
    var id: String? = null,
    var fullId: String? = null,
    var idHex: String? = null,
    var desc: String? = null,
    var text: String = "",
    var className: String? = null,
    var bounds: IntArray = IntArray(4),
    var boundsInParent: IntArray = IntArray(4),
    var center: DoubleArray = DoubleArray(2),
    var clickable: Boolean = false,
    var longClickable: Boolean = false,
    var scrollable: Boolean = false,
    var indexInParent: Int = -1,
    var childCount: Int = 0,
    var depth: Int = 0,
    var checked: Boolean = false,
    var enabled: Boolean = false,
    var editable: Boolean = false,
    var focusable: Boolean = false,
    var checkable: Boolean = false,
    var selected: Boolean = false,
    var dismissable: Boolean = false,
    var visibleToUser: Boolean = false,
    var contextClickable: Boolean = false,
    var focused: Boolean = false,
    var accessibilityFocused: Boolean = false,
    var rowCount: Int = 0,
    var columnCount: Int = 0,
    var row: Int = -1,
    var column: Int = -1,
    var rowSpan: Int = -1,
    var columnSpan: Int = -1,
    var drawingOrder: Int = 0,
    var actionNames: List<String> = emptyList(),
    var summary: String = "",
    var children: List<NodeInfoSnapshot> = emptyList(),
) {

    /** Rebuilds the [NodeInfo] tree below this snapshot. zh-CN: 重建此快照之下的 [NodeInfo] 树. */
    fun toNodeInfo(parent: NodeInfo? = null): NodeInfo = NodeInfo(this, parent).also { node ->
        children.forEach { child -> node.children += child.toNodeInfo(node) }
    }

    companion object {

        /** Copies [node] and its descendants. zh-CN: 复制 [node] 及其全部后代. */
        fun of(node: NodeInfo): NodeInfoSnapshot = NodeInfoSnapshot(
            packageName = node.packageName,
            id = node.id,
            fullId = node.fullId,
            idHex = node.idHex,
            desc = node.desc,
            text = node.text,
            className = node.className,
            bounds = node.boundsInScreen.let { intArrayOf(it.left, it.top, it.right, it.bottom) },
            boundsInParent = node.boundsInParent.let { intArrayOf(it.left, it.top, it.right, it.bottom) },
            center = doubleArrayOf(node.center.x, node.center.y),
            clickable = node.clickable,
            longClickable = node.longClickable,
            scrollable = node.scrollable,
            indexInParent = node.indexInParent,
            childCount = node.childCount,
            depth = node.depth,
            checked = node.checked,
            enabled = node.enabled,
            editable = node.editable,
            focusable = node.focusable,
            checkable = node.checkable,
            selected = node.selected,
            dismissable = node.dismissable,
            visibleToUser = node.visibleToUser,
            contextClickable = node.contextClickable,
            focused = node.focused,
            accessibilityFocused = node.accessibilityFocused,
            rowCount = node.rowCount,
            columnCount = node.columnCount,
            row = node.row,
            column = node.column,
            rowSpan = node.rowSpan,
            columnSpan = node.columnSpan,
            drawingOrder = node.drawingOrder,
            actionNames = node.actionNames,
            summary = node.summary(),
            children = node.children.map { of(it) },
        )

    }

}
