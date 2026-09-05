package org.autojs.autojs.core.automator

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * Symbolic names of accessibility actions and the matching used by [UiNode.hasAction]. Only
 * compile-time constants of the framework are referenced, so this is usable on the JVM.
 *
 * zh-CN: 无障碍动作的符号名, 以及 [UiNode.hasAction] 所用的匹配规则. 只引用框架的编译期常量, 因此可在 JVM 上使用.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
object UiNodeActions {

    const val UNKNOWN = "ACTION_UNKNOWN"

    private const val PREFIX = "ACTION_"

    /**
     * Whether [actionNames] (as [UiNode.actionNames] reports them) contain every entry of [actions]:
     * ints and doubles are resolved through [symbolicName], strings may omit the `ACTION_` prefix,
     * anything else never matches.
     * zh-CN: [actionNames] (即 [UiNode.actionNames] 的结果) 是否包含 [actions] 的每一项: 整数与浮点数经 [symbolicName]
     * 解析, 字符串可省略 `ACTION_` 前缀, 其它类型一律不匹配.
     */
    fun hasAll(actionNames: List<String>, actions: Array<out Any>): Boolean = actions.all { action ->
        when (action) {
            is Int -> symbolicName(action) in actionNames
            is Double -> symbolicName(action.toInt()) in actionNames
            is String -> (if (action.startsWith(PREFIX)) action else PREFIX + action) in actionNames
            else -> false
        }
    }

    // @Hint Copied by SuperMonster003 on Nov 5, 2022 (moved here on Sep 5, 2026).
    //  ! androidx.core.view.accessibility.AccessibilityNodeInfoCompat.getActionSymbolicName
    fun symbolicName(action: Int): String = when (action) {
        AccessibilityNodeInfoCompat.ACTION_FOCUS -> "ACTION_FOCUS"
        AccessibilityNodeInfoCompat.ACTION_CLEAR_FOCUS -> "ACTION_CLEAR_FOCUS"
        AccessibilityNodeInfoCompat.ACTION_SELECT -> "ACTION_SELECT"
        AccessibilityNodeInfoCompat.ACTION_CLEAR_SELECTION -> "ACTION_CLEAR_SELECTION"
        AccessibilityNodeInfoCompat.ACTION_CLICK -> "ACTION_CLICK"
        AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> "ACTION_LONG_CLICK"
        AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS -> "ACTION_ACCESSIBILITY_FOCUS"
        AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> "ACTION_CLEAR_ACCESSIBILITY_FOCUS"
        AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY -> "ACTION_NEXT_AT_MOVEMENT_GRANULARITY"
        AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY -> "ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY"
        AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT -> "ACTION_NEXT_HTML_ELEMENT"
        AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT -> "ACTION_PREVIOUS_HTML_ELEMENT"
        AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD -> "ACTION_SCROLL_FORWARD"
        AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD -> "ACTION_SCROLL_BACKWARD"
        AccessibilityNodeInfoCompat.ACTION_CUT -> "ACTION_CUT"
        AccessibilityNodeInfoCompat.ACTION_COPY -> "ACTION_COPY"
        AccessibilityNodeInfoCompat.ACTION_PASTE -> "ACTION_PASTE"
        AccessibilityNodeInfoCompat.ACTION_SET_SELECTION -> "ACTION_SET_SELECTION"
        AccessibilityNodeInfoCompat.ACTION_EXPAND -> "ACTION_EXPAND"
        AccessibilityNodeInfoCompat.ACTION_COLLAPSE -> "ACTION_COLLAPSE"
        AccessibilityNodeInfoCompat.ACTION_SET_TEXT -> "ACTION_SET_TEXT"
        AccessibilityNodeInfoCompat.ACTION_DISMISS -> "ACTION_DISMISS"
        android.R.id.accessibilityActionDragStart -> "ACTION_DRAG_START"
        android.R.id.accessibilityActionDragDrop -> "ACTION_DRAG_DROP"
        android.R.id.accessibilityActionDragCancel -> "ACTION_DRAG_CANCEL"
        android.R.id.accessibilityActionScrollUp -> "ACTION_SCROLL_UP"
        android.R.id.accessibilityActionScrollLeft -> "ACTION_SCROLL_LEFT"
        android.R.id.accessibilityActionScrollDown -> "ACTION_SCROLL_DOWN"
        android.R.id.accessibilityActionScrollRight -> "ACTION_SCROLL_RIGHT"
        android.R.id.accessibilityActionPageDown -> "ACTION_PAGE_DOWN"
        android.R.id.accessibilityActionPageUp -> "ACTION_PAGE_UP"
        android.R.id.accessibilityActionPageLeft -> "ACTION_PAGE_LEFT"
        android.R.id.accessibilityActionPageRight -> "ACTION_PAGE_RIGHT"
        android.R.id.accessibilityActionShowOnScreen -> "ACTION_SHOW_ON_SCREEN"
        android.R.id.accessibilityActionScrollToPosition -> "ACTION_SCROLL_TO_POSITION"
        android.R.id.accessibilityActionContextClick -> "ACTION_CONTEXT_CLICK"
        android.R.id.accessibilityActionSetProgress -> "ACTION_SET_PROGRESS"
        android.R.id.accessibilityActionMoveWindow -> "ACTION_MOVE_WINDOW"
        android.R.id.accessibilityActionShowTextSuggestions -> "ACTION_SHOW_TEXT_SUGGESTIONS"
        android.R.id.accessibilityActionShowTooltip -> "ACTION_SHOW_TOOLTIP"
        android.R.id.accessibilityActionHideTooltip -> "ACTION_HIDE_TOOLTIP"
        android.R.id.accessibilityActionPressAndHold -> "ACTION_PRESS_AND_HOLD"
        android.R.id.accessibilityActionImeEnter -> "ACTION_IME_ENTER"
        else -> UNKNOWN
    }

}
