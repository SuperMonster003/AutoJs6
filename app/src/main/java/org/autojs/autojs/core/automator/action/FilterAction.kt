package org.autojs.autojs.core.automator.action

import android.graphics.Rect
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.automator.UiObject
import java.util.*

/**
 * Created by Stardust on Jan 27, 2017.
 */
abstract class FilterAction(private val mFilter: Filter) : SimpleAction() {

    abstract fun perform(nodes: List<UiObject>): Boolean

    interface Filter {
        fun filter(root: UiObject): List<UiObject>
    }

    class TextFilter(private var mText: String, private var mIndex: Int) : Filter {

        override fun filter(root: UiObject): List<UiObject> {
            val list = UiSelector(AccessibilityService.bridge!!).textContains(mText).findAndReturnList(root)
            return when (mIndex) {
                -1 -> list
                else -> when {
                    mIndex >= list.size -> emptyList()
                    else -> listOf(list[mIndex])
                }
            }
        }

        override fun toString() = "TextFilter{mText='$mText', mIndex=$mIndex}"

    }

    class BoundsFilter(private var mBoundsInScreen: Rect) : Filter {

        override fun filter(root: UiObject): List<UiObject> {
            val list = ArrayList<UiObject>()
            findAccessibilityNodeInfoByBounds(root, list)
            return list
        }

        private fun findAccessibilityNodeInfoByBounds(root: UiObject?, list: MutableList<UiObject>) {
            when {
                root != null -> {
                    val rect = Rect()
                    root.getBoundsInScreen(rect)
                    if (rect == mBoundsInScreen) {
                        list.add(root)
                    }
                    val oldSize = list.size
                    (0 until root.childCount)
                        .mapNotNull { root.child(it) }
                        .forEach { findAccessibilityNodeInfoByBounds(it, list) }
                    when {
                        oldSize == list.size && rect.contains(mBoundsInScreen) -> list.add(root)
                    }
                }
            }
        }

        override fun toString() = "BoundsFilter{mBoundsInScreen=$mBoundsInScreen}"

    }

    class EditableFilter(private val mIndex: Int) : Filter {

        override fun filter(root: UiObject): List<UiObject> {
            val editableList = findEditable(root)
            return when (mIndex) {
                -1 -> editableList
                else -> when {
                    mIndex >= editableList.size -> emptyList()
                    else -> listOf(editableList[mIndex])
                }
            }
        }

        override fun toString() = "EditableFilter{mIndex=$mIndex}"

        companion object {

            fun findEditable(root: UiObject?): List<UiObject> = when {
                root == null -> emptyList()
                root.isEditable -> listOf(root)
                else -> {
                    val list = LinkedList<UiObject>()
                    (0 until root.childCount).forEach { i -> list.addAll(findEditable(root.child(i))) }
                    list
                }
            }

        }

    }

    class IdFilter(private val mId: String) : Filter {

        override fun filter(root: UiObject): List<UiObject> = UiSelector(AccessibilityService.bridge!!).id(mId).findAndReturnList(root)

        override fun toString() = "IdFilter{mId='$mId'}"

    }

    override fun perform(root: UiObject): Boolean = perform(mFilter.filter(root))

    class SimpleFilterAction(private val mAction: Int, filter: Filter) : FilterAction(filter) {

        override fun perform(nodes: List<UiObject>): Boolean = when {
            nodes.isEmpty() -> false
            else -> {
                var success = true
                nodes.forEach { node ->
                    node.performAction(mAction).also { success = success and it }
                }
                success
            }
        }

    }

    override fun toString() = "FilterAction{mFilter=$mFilter}"
}
