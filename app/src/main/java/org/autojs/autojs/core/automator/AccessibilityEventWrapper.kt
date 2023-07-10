package org.autojs.autojs.core.automator

import android.view.accessibility.AccessibilityEvent

class AccessibilityEventWrapper(event: AccessibilityEvent) {
    private val node = event.source

    val source = if (node != null) UiObject(node, null, getDepth(), getIndexInParent()) else null

    private fun getDepth(): Int {
        var depth = 0
        if (node == null) return 0
        while (node.parent != null) {
            depth++
        }
        return depth
    }

    private fun getIndexInParent(): Int {
        val parent = node!!.parent
        var index = 0
        while (parent.getChild(index) != node) {
            index++
        }
        return index
    }
}