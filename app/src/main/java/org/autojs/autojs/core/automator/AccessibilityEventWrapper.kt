package org.autojs.autojs.core.automator

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Created by 抠脚本人 (https://github.com/little-alei) on Jul 10, 2023.
 */
class AccessibilityEventWrapper(event: AccessibilityEvent) {
    val raw = event
    val packageName: CharSequence? = event.packageName
    val eventType = event.eventType
    val eventTime = event.eventTime
    val action = event.action
    val isFullScreen = event.isFullScreen
    val className = event.className
    val source = event.source?.let { UiObject(it, getDepth(it), getIndexInParent(it)) }

    private fun getDepth(node: AccessibilityNodeInfo): Int {
        var depth = 0
        var father = node.parent
        while (father != null) {
            depth++
            father = father.parent
        }
        return depth
    }

    private fun getIndexInParent(node: AccessibilityNodeInfo): Int {
        return try {
            val parent = node.parent ?: return -1
            findIndexInParent(parent.childCount, parent::getChild, node)
        } catch (_: RuntimeException) {
            -1
        }
    }
}

