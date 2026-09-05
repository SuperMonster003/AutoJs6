package org.autojs.autojs.core.automator

import kotlin.math.roundToInt

/**
 * The human-readable and comparable views of a node: [summary] (the property listing behind
 * `UiObject.summary()` / `toString()`), [fingerprint] (a cheap hash of the node's own state, for
 * telling "the same node, unchanged" from "changed" without walking siblings or children), and
 * [isSimilar] (the exhaustive comparison `UiObject.isSimilar()` has always made). The first two
 * work on any [UiNode]; the last needs the platform node.
 * zh-CN: 节点的可读与可比较视图: [summary] (`UiObject.summary()` / `toString()` 背后的属性列表), [fingerprint]
 * (节点自身状态的廉价哈希, 用于在不遍历兄弟与子节点的情况下区分 "同一节点未变" 与 "已变化"), 以及 [isSimilar]
 * (`UiObject.isSimilar()` 一贯的详尽比较). 前两者面向任意 [UiNode]; 后者需要平台节点.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
object UiObjectSummary {

    /** A value rendered verbatim, unlike strings, which are quoted. zh-CN: 原样渲染的值; 字符串则加引号. */
    private class Raw(val text: String) {
        override fun toString() = text
    }

    /**
     * The multi-line `{ name=value ... }` listing of the node's properties, strings quoted, lists
     * expanded one item per line, bounds as `Rect(l, t - r, b)` and the centre as `{x, y}`.
     * zh-CN: 节点属性的多行 `{ name=value ... }` 列表: 字符串加引号, 列表逐行展开, 边界为 `Rect(l, t - r, b)`, 中心为 `{x, y}`.
     */
    fun summary(node: UiNode<*>): String = listOf(

        /* Common */

        "packageName" to { node.packageName() },
        "parent" to { node.parent()?.className() },
        "id" to { node.id() },
        "fullId" to { node.id() },
        "idHex" to { node.idHex() },
        "desc" to { node.desc() },
        "text" to { node.text() },
        "bounds" to { Raw(bounds(node)) },
        "center" to { Raw(center(node)) },
        "className" to { node.className() },
        "clickable" to { node.clickable() },
        "longClickable" to { node.longClickable() },
        "scrollable" to { node.scrollable() },
        "indexInParent" to { node.indexInParent() },
        "childCount" to { node.childCount() },
        "depth" to { node.depth() },

        /* Regular */

        "checked" to { node.checked() },
        "enabled" to { node.enabled() },
        "editable" to { node.editable() },
        "focusable" to { node.focusable() },
        "checkable" to { node.checkable() },
        "selected" to { node.selected() },
        "dismissable" to { node.dismissable() },
        "visibleToUser" to { node.visibleToUser() },

        /* Rare */

        "contextClickable" to { node.contextClickable() },
        "focused" to { node.focused() },
        "accessibilityFocused" to { node.accessibilityFocused() },
        "rowCount" to { node.rowCount() },
        "columnCount" to { node.columnCount() },
        "row" to { node.row() },
        "column" to { node.column() },
        "rowSpan" to { node.rowSpan() },
        "columnSpan" to { node.columnSpan() },
        "drawingOrder" to { node.drawingOrder() },

        /* List */

        "actions" to { node.actionNames() },
    ).let { format(it) }

    /** `[label] SimpleClassName { summary }`, the `toString()` form. zh-CN: `[label] 简单类名 { summary }`, 即 `toString()` 形式. */
    fun describe(node: UiNode<*>, label: String = "UiObject"): String {
        val simpleClassName = "${node.className()}".substringAfterLast(".")
        return "[$label] $simpleClassName ${summary(node)}"
    }

    /**
     * A hash of bounds, text, description, class name, child count and the key boolean
     * properties; equal for the same node in the same state, cheap because nothing beyond the
     * node itself is read. Collisions are possible: a difference proves change, equality suggests
     * stability.
     * zh-CN: 边界, 文本, 描述, 类名, 子节点数与关键布尔属性的哈希; 同一节点同一状态下相等, 只读取节点自身因而廉价.
     * 可能碰撞: 不同即证明变化, 相同只是稳定的迹象.
     */
    fun fingerprint(node: UiNode<*>): Int {
        var hash = 17
        fun mix(value: Any?) {
            hash = 31 * hash + (value?.hashCode() ?: 0)
        }
        mix(node.left())
        mix(node.top())
        mix(node.right())
        mix(node.bottom())
        mix(node.text())
        mix(node.desc())
        mix(node.className())
        mix(node.childCount())
        mix(node.clickable())
        mix(node.longClickable())
        mix(node.scrollable())
        mix(node.checkable())
        mix(node.checked())
        mix(node.enabled())
        mix(node.selected())
        mix(node.visibleToUser())
        return hash
    }

    /**
     * The comparison behind `UiObject.isSimilar()`: the same platform node, every listed property
     * equal, and the string forms of the first / last sibling and child equal.
     * zh-CN: `UiObject.isSimilar()` 背后的比较: 同一平台节点, 所列属性全部相等, 且首 / 末兄弟与子节点的字符串形式相等.
     */
    fun isSimilar(node: UiObject, other: Any?): Boolean {
        if (node === other) return true
        if (other !is UiObject) return false

        // @Reference to Hunter1023 (https://github.com/Hunter1023) by SuperMonster003 on Mar 28, 2025.
        //  ! http://issues.autojs6.com/288#issuecomment-2692248348
        val info = node.unwrap() ?: return false

        return info == other.unwrap() &&
                node.bounds() == other.bounds() &&
                node.packageName() == other.packageName() &&
                node.parent == other.parent &&
                node.id() == other.id() &&
                node.fullId() == other.fullId() &&
                node.idHex() == other.idHex() &&
                node.desc() == other.desc() &&
                node.text() == other.text() &&
                node.className() == other.className() &&
                node.clickable() == other.clickable() &&
                node.longClickable() == other.longClickable() &&
                node.scrollable() == other.scrollable() &&
                node.indexInParent() == other.indexInParent() &&
                node.childCount() == other.childCount() &&
                node.depth() == other.depth() &&
                node.checked() == other.checked() &&
                node.enabled() == other.enabled() &&
                node.editable() == other.editable() &&
                node.focusable() == other.focusable() &&
                node.checkable() == other.checkable() &&
                node.selected() == other.selected() &&
                node.isDismissable == other.isDismissable &&
                node.visibleToUser() == other.visibleToUser() &&
                node.isContextClickable == other.isContextClickable &&
                node.focused() == other.focused() &&
                node.isAccessibilityFocused == other.isAccessibilityFocused &&
                node.rowCount() == other.rowCount() &&
                node.columnCount() == other.columnCount() &&
                node.row() == other.row() &&
                node.column() == other.column() &&
                node.rowSpan() == other.rowSpan() &&
                node.columnSpan() == other.columnSpan() &&
                node.drawingOrder == other.drawingOrder &&
                node.actionNames() == other.actionNames() &&
                node.isSingleton() == other.isSingleton() &&
                "${node.firstSibling() ?: ""}" == "${other.firstSibling()}" &&
                "${node.lastSibling() ?: ""}" == "${other.lastSibling()}" &&
                "${node.firstChild() ?: ""}" == "${other.firstChild()}" &&
                "${node.lastChild() ?: ""}" == "${other.lastChild()}"
    }

    private fun bounds(node: UiNode<*>) = "Rect(${node.left()}, ${node.top()} - ${node.right()}, ${node.bottom()})"

    // The centre as `android.graphics.Rect.exactCenterX()` rounds it: half-way values go up.
    // zh-CN: 中心为 `android.graphics.Rect.exactCenterX()` 四舍五入后的值: 半值进位.
    private fun center(node: UiNode<*>): String {
        val x = ((node.left() + node.right()) * 0.5).roundToInt()
        val y = ((node.top() + node.bottom()) * 0.5).roundToInt()
        return "{$x, $y}"
    }

    private fun format(entries: List<Pair<String, () -> Any?>>): String {
        val separatorLv0 = "\n"
        val separatorLv1 = "$separatorLv0  "
        val separatorLv2 = "$separatorLv1  "
        return entries.joinToString(prefix = "{$separatorLv1", separator = separatorLv1, postfix = "$separatorLv0}") { (name, read) ->
            val value = when (val result = read()) {
                is CharSequence -> "\"$result\""
                is Iterable<*> -> result.joinToString(prefix = "[$separatorLv2", separator = separatorLv2, postfix = "$separatorLv1]")
                is Array<*> -> result.joinToString(prefix = "[$separatorLv2", separator = separatorLv2, postfix = "$separatorLv1]")
                else -> result
            }
            "$name=$value"
        }
    }

}
