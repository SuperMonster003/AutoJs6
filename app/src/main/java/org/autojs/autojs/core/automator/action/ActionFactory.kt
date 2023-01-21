package org.autojs.autojs.core.automator.action

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import org.autojs.autojs.core.automator.UiObject

/**
 * Created by Stardust on 2017/1/27.
 */
object ActionFactory {

    private val searchUpActions = listOf(
        AccessibilityNodeInfo.ACTION_CLICK,
        AccessibilityNodeInfo.ACTION_LONG_CLICK,
        AccessibilityNodeInfo.ACTION_SELECT,
        AccessibilityNodeInfo.ACTION_FOCUS,
        AccessibilityNodeInfo.ACTION_SELECT,
        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
    )

    fun createActionWithTextFilter(action: Int, text: String, index: Int) = when {
        searchUpActions.contains(action) -> SearchUpTargetAction(action, FilterAction.TextFilter(text, index))
        else -> DepthFirstSearchTargetAction(action, FilterAction.TextFilter(text, index))
    }

    fun createActionWithBoundsFilter(action: Int, rect: Rect) = when {
        searchUpActions.contains(action) -> SearchUpTargetAction(action, FilterAction.BoundsFilter(rect))
        else -> DepthFirstSearchTargetAction(action, FilterAction.BoundsFilter(rect))
    }

    fun createActionWithEditableFilter(action: Int, index: Int, text: String): SimpleAction {
        return object : SearchTargetAction(action, EditableFilter(index)) {

            override fun performAction(node: UiObject) = Bundle().apply {
                when (action == AccessibilityNodeInfo.ACTION_SET_TEXT) {
                    true -> putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                    else -> putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, node.text() + text)
                }
            }.let { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, it) }

        }
    }

    fun createScrollMaxAction(action: Int) = ScrollMaxAction(action)

    fun createScrollAction(action: Int, i: Int) = ScrollAction(action, i)

    fun createActionWithIdFilter(action: Int, id: String) = FilterAction.SimpleFilterAction(action, FilterAction.IdFilter(id))

}
