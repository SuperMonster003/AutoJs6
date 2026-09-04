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
    // Resolved eagerly because the raw event may be recycled by the framework once the callback returns (API < 33).
    // zh-CN: 急切解析, 因为回调返回后原始事件可能被框架回收 (API < 33).
    val source = runCatching { event.source?.let { UiObject(it, getDepth(it), getIndexInParent(it)) } }.getOrNull()

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
        val parent = node.parent ?: return 0
        // The tree may change between obtaining the parent and iterating its children,
        // so the lookup must be bounded, otherwise getChild() throws or the loop never ends.
        // zh-CN: 获取父节点与遍历其子节点之间节点树可能已变化, 因此查找必须有界, 否则 getChild() 会抛出异常或循环永不结束.
        val childCount = runCatching { parent.childCount }.getOrDefault(0)
        for (index in 0 until childCount) {
            val child = runCatching { parent.getChild(index) }.getOrNull() ?: continue
            if (child == node) return index
        }
        return -1
    }

    override fun toString(): String {
        return "AccessibilityEventWrapper(packageName=$packageName, eventType=$eventType, eventTime=$eventTime, action=$action, isFullScreen=$isFullScreen, className=$className)"
    }
}

