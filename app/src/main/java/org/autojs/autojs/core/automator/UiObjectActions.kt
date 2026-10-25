package org.autojs.autojs.core.automator

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.core.accessibility.UiSelector

interface UiObjectActions {

    fun performAction(action: Int, vararg arguments: ActionArgument): Boolean

    @ScriptInterface
    fun click() = performAction(AccessibilityActionCompat.ACTION_CLICK.id)

    @ScriptInterface
    fun longClick() = performAction(AccessibilityActionCompat.ACTION_LONG_CLICK.id)

    @ScriptInterface
    fun accessibilityFocus() = performAction(AccessibilityActionCompat.ACTION_ACCESSIBILITY_FOCUS.id)

    @ScriptInterface
    fun clearAccessibilityFocus() = performAction(AccessibilityActionCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS.id)

    @ScriptInterface
    fun focus() = performAction(AccessibilityActionCompat.ACTION_FOCUS.id)

    @ScriptInterface
    fun clearFocus() = performAction(AccessibilityActionCompat.ACTION_CLEAR_FOCUS.id)

    @ScriptInterface
    fun clearSelection() = performAction(AccessibilityActionCompat.ACTION_CLEAR_SELECTION.id)

    @ScriptInterface
    fun dragCancel() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) performAction(AccessibilityAction.ACTION_DRAG_CANCEL.id) else false

    @ScriptInterface
    fun dragDrop() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) performAction(AccessibilityAction.ACTION_DRAG_DROP.id) else false

    @ScriptInterface
    fun dragStart() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) performAction(AccessibilityAction.ACTION_DRAG_START.id) else false

    @ScriptInterface
    fun hideTooltip() = performTooltipAction(android.R.id.accessibilityActionHideTooltip)

    @ScriptInterface
    fun imeEnter() = performAction(AccessibilityActionCompat.ACTION_IME_ENTER.id)

    @ScriptInterface
    fun moveWindow(x: Int, y: Int) = performAction(
        AccessibilityActionCompat.ACTION_MOVE_WINDOW.id,
        ActionArgument.IntActionArgument(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVE_WINDOW_X, x),
        ActionArgument.IntActionArgument(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVE_WINDOW_Y, y),
    )

    @ScriptInterface
    fun nextAtMovementGranularity(granularity: Int, isExtendSelection: Boolean) = performAction(
        AccessibilityActionCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY.id,
        ActionArgument.IntActionArgument(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, granularity),
        ActionArgument.BooleanActionArgument(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, isExtendSelection),
    )

    @ScriptInterface
    fun nextHtmlElement(element: String) = performAction(
        AccessibilityActionCompat.ACTION_NEXT_HTML_ELEMENT.id,
        ActionArgument.StringActionArgument(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_HTML_ELEMENT_STRING, element),
    )

    @ScriptInterface
    fun pageDown() = performAction(AccessibilityActionCompat.ACTION_PAGE_DOWN.id)

    @ScriptInterface
    fun pageLeft() = performAction(AccessibilityActionCompat.ACTION_PAGE_LEFT.id)

    @ScriptInterface
    fun pageRight() = performAction(AccessibilityActionCompat.ACTION_PAGE_RIGHT.id)

    @ScriptInterface
    fun pageUp() = performAction(AccessibilityActionCompat.ACTION_PAGE_UP.id)

    @ScriptInterface
    fun pressAndHold() = performAction(AccessibilityActionCompat.ACTION_PRESS_AND_HOLD.id)

    @ScriptInterface
    fun previousAtMovementGranularity(granularity: Int, isExtendSelection: Boolean) = performAction(
        AccessibilityActionCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY.id,
        ActionArgument.IntActionArgument(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, granularity),
        ActionArgument.BooleanActionArgument(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, isExtendSelection),
    )

    @ScriptInterface
    fun previousHtmlElement(element: String) = performAction(
        AccessibilityActionCompat.ACTION_PREVIOUS_HTML_ELEMENT.id,
        ActionArgument.StringActionArgument(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_HTML_ELEMENT_STRING, element),
    )

    @ScriptInterface
    fun showTextSuggestions() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) performAction(AccessibilityAction.ACTION_SHOW_TEXT_SUGGESTIONS.id) else false

    @ScriptInterface
    fun showTooltip() = performTooltipAction(android.R.id.accessibilityActionShowTooltip)

    @ScriptInterface
    fun copy() = performAction(AccessibilityActionCompat.ACTION_COPY.id)

    @ScriptInterface
    fun paste() = performAction(AccessibilityActionCompat.ACTION_PASTE.id)

    @ScriptInterface
    fun select() = performAction(AccessibilityActionCompat.ACTION_SELECT.id)

    @ScriptInterface
    fun cut() = performAction(AccessibilityActionCompat.ACTION_CUT.id)

    @ScriptInterface
    fun collapse() = performAction(AccessibilityActionCompat.ACTION_COLLAPSE.id)

    @ScriptInterface
    fun expand() = performAction(AccessibilityActionCompat.ACTION_EXPAND.id)

    @ScriptInterface
    fun dismiss() = performAction(AccessibilityActionCompat.ACTION_DISMISS.id)

    @ScriptInterface
    fun show() = performAction(AccessibilityActionCompat.ACTION_SHOW_ON_SCREEN.id)

    @ScriptInterface
    fun scrollForward() = performAction(AccessibilityActionCompat.ACTION_SCROLL_FORWARD.id)

    @ScriptInterface
    fun scrollBackward() = performAction(AccessibilityActionCompat.ACTION_SCROLL_BACKWARD.id)

    @ScriptInterface
    fun scrollUp() = performAction(AccessibilityActionCompat.ACTION_SCROLL_UP.id)

    @ScriptInterface
    fun scrollDown() = performAction(AccessibilityActionCompat.ACTION_SCROLL_DOWN.id)

    @ScriptInterface
    fun scrollLeft() = performAction(AccessibilityActionCompat.ACTION_SCROLL_LEFT.id)

    @ScriptInterface
    fun scrollRight() = performAction(AccessibilityActionCompat.ACTION_SCROLL_RIGHT.id)

    @ScriptInterface
    fun contextClick() = performAction(AccessibilityActionCompat.ACTION_CONTEXT_CLICK.id)

    @ScriptInterface
    fun setSelection(s: Int, e: Int) = performAction(
        AccessibilityActionCompat.ACTION_SET_SELECTION.id,
        ActionArgument.IntActionArgument(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT, s),
        ActionArgument.IntActionArgument(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT, e),
    )

    @ScriptInterface
    fun setText(text: String) = performAction(
        AccessibilityActionCompat.ACTION_SET_TEXT.id,
        ActionArgument.CharSequenceActionArgument(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text),
    )

    @ScriptInterface
    fun setProgress(progress: Float) = performAction(
        AccessibilityActionCompat.ACTION_SET_PROGRESS.id,
        ActionArgument.FloatActionArgument(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_PROGRESS_VALUE, progress),
    )

    @ScriptInterface
    fun scrollTo(row: Int, column: Int) = performAction(
        AccessibilityActionCompat.ACTION_SCROLL_TO_POSITION.id,
        ActionArgument.IntActionArgument(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_ROW_INT, row),
        ActionArgument.IntActionArgument(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_COLUMN_INT, column),
    )

    /**
     * [performAction] of a tooltip [action] (the framework id, the same as that of `AccessibilityActionCompat`) only on
     * the nodes whose action list has it: for a View with no tooltip the framework throws on the main thread of the
     * node's process (`View.showLongClickTooltip`), which crashes that app (docs/selector/ROADMAP.md appendix C Q8). A
     * node without it is `false` and gets nothing; a selector and a collection go node by node as their
     * [performAction] does.
     * zh-CN: 只在动作列表含有 tooltip 动作 [action] (框架 id, 与 AccessibilityActionCompat 的相同) 的节点上执行 [performAction]: 对没有 tooltip 的 View,
     * 框架会在节点所属进程的主线程抛出 (View.showLongClickTooltip), 使该应用崩溃 (docs/selector/ROADMAP.md 附录 C Q8). 不含该动作的节点为 false
     * 且不执行; 选择器与集合按其 [performAction] 的方式逐节点进行.
     */
    private fun performTooltipAction(action: Int): Boolean {
        val nodes = when (this) {
            is UiSelector -> untilFind().nodes
            is UiObjectCollection -> nodes
            else -> return when {
                this is UiNode<*> && !hasAction(action) -> false
                else -> performAction(action)
            }
        }
        // Every node attempted and null entries skipped, as UiObjectCollection.performAction does.
        // zh-CN: 与 UiObjectCollection.performAction 相同: 每个节点都尝试, 跳过 null 项.
        return nodes.filterNotNull().fold(true) { all, node -> (node.hasAction(action) && node.performAction(action)) and all }
    }
}
