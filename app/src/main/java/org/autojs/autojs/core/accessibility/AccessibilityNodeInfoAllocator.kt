package org.autojs.autojs.core.accessibility

import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import java.util.*

/**
 * Created by Stardust on Mar 22, 2017.
 * Modified by SuperMonster003 as of Jun 17, 2022.
 */
open class AccessibilityNodeInfoAllocator {

    // Values hold the allocation origin (stack trace) when [isTracingOrigin] is on, otherwise an empty string.
    // zh-CN: 开启 [isTracingOrigin] 时值为分配来源 (堆栈), 否则为空字符串.
    private val mAccessibilityNodeInfoList = Collections.synchronizedMap(HashMap<AccessibilityNodeInfo, String>())

    open fun getChild(parent: AccessibilityNodeInfo, i: Int) = add(parent.getChild(i))

    open fun getChild(parent: AccessibilityNodeInfoCompat, i: Int): AccessibilityNodeInfoCompat = parent.getChild(i).also { add(it.unwrap()) }

    open fun getParent(n: AccessibilityNodeInfo) = add(n.parent)

    open fun getParent(n: AccessibilityNodeInfoCompat): AccessibilityNodeInfoCompat? = n.parent?.also { add(it.unwrap()) }

    open fun findAccessibilityNodeInfosByText(root: AccessibilityNodeInfo, text: String): List<AccessibilityNodeInfo> =
        root.findAccessibilityNodeInfosByText(text).also { addAll(it) }

    open fun findAccessibilityNodeInfosByText(root: AccessibilityNodeInfoCompat, text: String): List<AccessibilityNodeInfoCompat> =
        root.findAccessibilityNodeInfosByText(text).also { addAll(it) }

    open fun findAccessibilityNodeInfosByViewId(root: AccessibilityNodeInfo, id: String): List<AccessibilityNodeInfo> =
        root.findAccessibilityNodeInfosByViewId(id).also { addAll(it) }

    open fun findAccessibilityNodeInfosByViewId(root: AccessibilityNodeInfoCompat, id: String): List<AccessibilityNodeInfoCompat> =
        root.findAccessibilityNodeInfosByViewId(id).also { addAll(it) }

    open fun recycle(nodeInfo: AccessibilityNodeInfo) {
        nodeInfo.apply {
            @Suppress("DEPRECATION")
            takeIf { SDK_INT < TIRAMISU }?.recycle()
        }.let { mAccessibilityNodeInfoList.remove(it) }
    }

    open fun recycle(nodeInfo: AccessibilityNodeInfoCompat) {
        recycle(nodeInfo.unwrap())
    }

    open fun recycleAll(): Int {
        val snapshot = synchronized(mAccessibilityNodeInfoList) { mAccessibilityNodeInfoList.toMap() }
        var notRecycledCount = 0
        snapshot.forEach { (key, origin) ->
            try {
                @Suppress("DEPRECATION")
                key.takeIf { SDK_INT < TIRAMISU }?.recycle()
                notRecycledCount++
                origin.takeIf { it.isNotEmpty() }?.let { Log.w(TAG, it) }
            } catch (_: IllegalStateException) {
                /* Ignored. */
            }
        }
        return notRecycledCount.also { Log.v(TAG, "Total: ${snapshot.size}; Not recycled: $it") }
    }

    fun add(nodeInfo: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
        nodeInfo?.also { mAccessibilityNodeInfoList[it] = captureOrigin() }

    private fun addAll(nodeInfoList: Collection<*>) {
        captureOrigin().run {
            nodeInfoList.forEach {
                when (it) {
                    is AccessibilityNodeInfo -> mAccessibilityNodeInfoList[it] = this
                    is AccessibilityNodeInfoCompat -> mAccessibilityNodeInfoList[it.unwrap()] = this
                }
            }
        }
    }

    // Capturing a full stack trace for every node is expensive, so it is opt-in for leak diagnosis only.
    // zh-CN: 为每个节点捕获完整堆栈开销很大, 因此仅在排查泄漏时按需开启.
    private fun captureOrigin(): String = when {
        isTracingOrigin -> Thread.currentThread().stackTrace.contentToString()
        else -> ""
    }

    private class NoOpAllocator : AccessibilityNodeInfoAllocator() {

        override fun getParent(n: AccessibilityNodeInfoCompat): AccessibilityNodeInfoCompat? = n.parent

        override fun getParent(n: AccessibilityNodeInfo): AccessibilityNodeInfo? = n.parent

        override fun getChild(parent: AccessibilityNodeInfoCompat, i: Int): AccessibilityNodeInfoCompat = parent.getChild(i)

        override fun getChild(parent: AccessibilityNodeInfo, i: Int): AccessibilityNodeInfo? = parent.getChild(i)

        override fun findAccessibilityNodeInfosByViewId(root: AccessibilityNodeInfoCompat, id: String): List<AccessibilityNodeInfoCompat> =
            root.findAccessibilityNodeInfosByViewId(id)

        override fun findAccessibilityNodeInfosByViewId(root: AccessibilityNodeInfo, id: String): List<AccessibilityNodeInfo> =
            root.findAccessibilityNodeInfosByViewId(id)

        override fun findAccessibilityNodeInfosByText(root: AccessibilityNodeInfo, text: String): List<AccessibilityNodeInfo> =
            root.findAccessibilityNodeInfosByText(text)

        override fun findAccessibilityNodeInfosByText(root: AccessibilityNodeInfoCompat, text: String): List<AccessibilityNodeInfoCompat> =
            root.findAccessibilityNodeInfosByText(text)

        override fun recycleAll(): Int = -1

    }

    companion object {

        private const val TAG = "AccessibilityAllocator"

        /**
         * Whether to record the allocation stack trace of every node for leak diagnosis. Off by default.
         * zh-CN: 是否为每个节点记录分配堆栈以排查泄漏, 默认关闭.
         */
        @JvmStatic
        @Volatile
        var isTracingOrigin = false

        val NONE: AccessibilityNodeInfoAllocator = NoOpAllocator()

        val global = AccessibilityNodeInfoAllocator()

        fun recycleList(root: AccessibilityNodeInfo, list: List<AccessibilityNodeInfo>) {
            // FIXME by Stardust on May 1, 2017.
            //  ! Issue #180.
            list.takeIf { SDK_INT < TIRAMISU }
                ?.filter { it !== root }
                ?.forEach {
                    @Suppress("DEPRECATION")
                    it.recycle()
                }
        }

    }

}
