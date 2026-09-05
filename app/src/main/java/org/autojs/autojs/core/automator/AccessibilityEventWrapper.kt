package org.autojs.autojs.core.automator

import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * The event object handed to script callbacks (`auto.registerEvent`). Cheap fields are copied
 * eagerly; [source] is resolved on first access because walking the parent chain for its depth and
 * index is the expensive part and most callbacks never read it.
 *
 * zh-CN: 交给脚本回调 (`auto.registerEvent`) 的事件对象. 廉价字段急切拷贝; [source] 在首次访问时解析,
 * 因为为求深度与索引而遍历父链是其中昂贵的部分, 而多数回调并不读取它.
 *
 * Created by 抠脚本人 (https://github.com/little-alei) on Jul 10, 2023.
 * Modified by SuperMonster003 as of Sep 5, 2026.
 */
class AccessibilityEventWrapper(event: AccessibilityEvent) {
    val raw = event
    val packageName: CharSequence? = event.packageName
    val eventType = event.eventType
    val eventTime = event.eventTime
    val action = event.action
    val isFullScreen = event.isFullScreen
    val className = event.className

    private val sourceLock = Any()
    private var isSourceResolved = false
    private var resolvedSource: UiObject? = null

    @Volatile
    private var isSealed = false

    /**
     * The node the event originated from, wrapped with its depth and index in parent, or null.
     * Resolved lazily and cached. On API < 33 the framework recycles [raw] once the callback has
     * returned, so a source that was not read during the callback is null afterwards (see [seal]);
     * API >= 33 events are plain objects and stay readable.
     *
     * zh-CN: 事件来源节点 (带深度与父内索引), 无则为 null. 惰性解析并缓存. API < 33 上框架在回调返回后回收 [raw],
     * 因此回调期间未读取的 source 之后为 null (见 [seal]); API >= 33 的事件是普通对象, 之后仍可读取.
     */
    val source: UiObject?
        get() = synchronized(sourceLock) {
            if (!isSourceResolved) {
                if (isSealed && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
                resolvedSource = runCatching { raw.source?.let { UiObject(it, getDepth(it), getIndexInParent(it)) } }.getOrNull()
                isSourceResolved = true
            }
            resolvedSource
        }

    /** Marks that the framework callback has returned and [raw] may have been recycled. zh-CN: 标记框架回调已返回, [raw] 可能已被回收. */
    fun seal() {
        isSealed = true
    }

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
