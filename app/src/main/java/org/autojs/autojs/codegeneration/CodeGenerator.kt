package org.autojs.autojs.codegeneration

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.autojs.autojs.core.accessibility.NodeInfo

/**
 * Generates the selector code for a node of a captured layout: a selector that singles the target
 * out under the root (via [UiSelectorGenerator]), refined through children (`.parent()`) or
 * parents (`.child(i)`) up to two levels when the node itself is not distinctive, or the
 * collection form (`.children().forEach(...)`) when the target sits in a list or grid; then the
 * search mode and the action.
 * zh-CN: 为捕获的布局中的节点生成选择器代码: 在根节点下唯一确定目标的选择器 (经 [UiSelectorGenerator]), 节点本身不够独特时
 * 借助至多两级子节点 (`.parent()`) 或父节点 (`.child(i)`) 细化, 目标位于列表 / 网格中时生成集合形式
 * (`.children().forEach(...)`); 然后附加搜索模式与动作.
 *
 * Created by Stardust on Dec 7, 2017.
 * Transformed by SuperMonster003 on Sep 6, 2026.
 */
class CodeGenerator(private val root: ReadOnlyUiObject, private val target: ReadOnlyUiObject) {

    constructor(root: NodeInfo, target: NodeInfo) : this(ReadOnlyUiObject(root), ReadOnlyUiObject(target))

    var usingId = true
    var usingDesc = true
    var usingText = true
    var searchMode = FIND_ONE
    var action = -1

    /** The style of the code: classic or Flow, see [CodeStyle]. zh-CN: 代码风格: 经典或 Flow, 见 [CodeStyle]. */
    var style = CodeStyle.CLASSIC

    fun generateCode(): String? {
        val collection = getCollectionParent(target)
        if (collection != null) {
            // @Hint by SuperMonster003 on Jan 12, 2026.
            //  ! Collection-based generation may fail in some apps due to unstable accessibility trees.
            //  ! zh-CN: 基于集合控件的生成在某些应用中可能因无障碍树不稳定而失败.
            //  # return generateCodeForCollectionChild(collection, mTarget);
            generateCodeForCollectionChild(collection, target)?.let { return it }
        }
        val generator = UiSelectorGenerator(root, target).also {
            it.setSearchMode(searchMode)
            it.setUsingDesc(usingDesc)
            it.setUsingId(usingId)
            it.setUsingText(usingText)
        }
        if (style == CodeStyle.FLOW) {
            // The Flow form only for a target its own selector singles out; anything refined through
            // parents or children falls back to the classic code.
            // zh-CN: 只对能由自身选择器唯一确定的目标给出 Flow 形式; 需经父 / 子节点细化的一律回退到经典代码.
            generator.generateSelector()?.let { selector -> FlowCodeStyle.render(selector.toString(), searchMode, getAction())?.let { return it } }
        }
        val selector = generateCode(generator, root, target, 2, 2, true) ?: return null
        return generateAction(selector)
    }

    private fun generateCode(generator: UiSelectorGenerator, root: ReadOnlyUiObject, target: ReadOnlyUiObject, maxParentLevel: Int, maxChildrenLevel: Int, withFind: Boolean): String? {
        val selector = when (withFind) {
            true -> generator.generateSelectorCode()
            else -> generator.generateSelector()?.toString()
        }
        if (selector != null) {
            return selector
        }
        if (maxChildrenLevel > 0) {
            for (i in 0 until target.childCount()) {
                val child = target.child(i) ?: continue
                val childCode = generateCode(root, child, 0, maxChildrenLevel - 1)
                if (childCode != null) {
                    return "$childCode.parent()"
                }
            }
        }
        // Unreachable in practice: the nodes this level sees (the target itself, parents) carry no
        // index (see ReadOnlyUiObject.indexInParent), so this has never produced code; kept as is.
        // zh-CN: 实际不可达: 本层看到的节点 (目标本身, 父节点) 没有序号 (见 ReadOnlyUiObject.indexInParent), 因此从未生成过代码; 原样保留.
        if (maxParentLevel > 0) {
            val parent = target.parent()
            if (parent != null) {
                val index = target.indexInParent()
                if (index > 0) {
                    val parentCode = generateCode(root, parent, maxParentLevel - 1, 0)
                    if (parentCode != null) {
                        return "$parentCode.child($index)"
                    }
                }
            }
        }
        return null
    }

    private fun generateCode(root: ReadOnlyUiObject, target: ReadOnlyUiObject, maxParentLevel: Int, maxChildrenLevel: Int, withFind: Boolean = true): String? {
        val generator = UiSelectorGenerator(root, target).also { it.setUsingId(usingId) }
        return generateCode(generator, root, target, maxParentLevel, maxChildrenLevel, withFind)
    }

    private fun generateAction(selector: String): String {
        if (searchMode == WAIT_FOR) {
            return "$selector.waitFor()"
        }
        if (searchMode == EXISTS) {
            return "if($selector.exists()){\n  \n}"
        }
        val action = getAction()
        return when {
            action.isEmpty() -> selector
            else -> "$selector.$action"
        }
    }

    private fun getAction(): String = when (action) {
        AccessibilityNodeInfoCompat.ACTION_CLICK -> "click()"
        AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> "longClick()"
        AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD -> "scrollBackward()"
        AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD -> "scrollForward()"
        AccessibilityNodeInfoCompat.ACTION_SET_TEXT -> "setText(\"\")"
        else -> ""
    }

    private fun generateCodeForCollectionChild(collection: ReadOnlyUiObject, target: ReadOnlyUiObject): String? {
        target.parent() ?: return null
        val collectionItem = (0 until collection.childCount())
            .mapNotNull { collection.child(it) }
            .firstOrNull { inherits(it, target) }
            ?: return null
        val collectionCode = generateCode(root, collection, 2, 0) ?: return null
        val itemCode = generateCode(collectionItem, target, 1, 2, false) ?: return null
        // Without an action there is nothing to call on the found node (formerly `target.;`).
        // zh-CN: 没有动作时对找到的节点无可调用 (原先生成 `target.;`).
        val actionLine = getAction().takeIf { it.isNotEmpty() }?.let { "target.$it;\n" } ?: ""
        return "$collectionCode.children().forEach(child => {\n" +
                "var target = child.findOne($itemCode);\n" +
                actionLine +
                "});"
    }

    private fun inherits(root: ReadOnlyUiObject, target: ReadOnlyUiObject): Boolean {
        for (i in 0 until root.childCount()) {
            val child = root.child(i) ?: continue
            if (child == target || inherits(child, target)) {
                return true
            }
        }
        return false
    }

    private fun getCollectionParent(target: ReadOnlyUiObject): ReadOnlyUiObject? {
        var parent = target.parent()
        while (parent != null) {
            if (parent.rowCount() > 0 || parent.columnCount() > 0) {
                return parent
            }
            parent = parent.parent()
        }
        return null
    }

    companion object {

        const val UNTIL_FIND = 0
        const val FIND_ONE = 1
        const val WAIT_FOR = 2
        const val EXISTS = 3

    }

}
