package org.autojs.autojs.core.accessibility

import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import java.util.*

/**
 * Created by Stardust on 2017/3/22.
 * Modified by SuperMonster003 as of Jun 17, 2022.
 */
open class AccessibilityNodeInfoAllocator {

    private val mAccessibilityNodeInfoList = HashMap<AccessibilityNodeInfo, String?>()

    open fun getChild(parent: AccessibilityNodeInfo, i: Int) = add(parent.getChild(i))

    open fun getChild(parent: AccessibilityNodeInfoCompat, i: Int): AccessibilityNodeInfoCompat = parent.getChild(i).also { add(it.unwrap()) }

    open fun getParent(n: AccessibilityNodeInfo) = add(n.parent)

    open fun getParent(n: AccessibilityNodeInfoCompat): AccessibilityNodeInfoCompat = n.parent.apply { unwrap() }

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

    open fun recycleAll(): Int = mAccessibilityNodeInfoList.run {
        var notRecycledCount = 0
        forEach { (key, value) ->
            try {
                @Suppress("DEPRECATION")
                key.takeIf { SDK_INT < TIRAMISU }?.recycle()
                notRecycledCount++
                value?.let { Log.w(TAG, it) }
            } catch (_: IllegalStateException) {
                // Ignored
            }
        }
        notRecycledCount.also { Log.v(TAG, "Total: ${this.size}; Not recycled: $it") }
    }

    fun add(nodeInfo: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
        nodeInfo?.also { mAccessibilityNodeInfoList[it] = Arrays.toString(Thread.currentThread().stackTrace) }

    private fun addAll(nodeInfoList: Collection<*>) {
        Arrays.toString(Thread.currentThread().stackTrace).run {
            nodeInfoList.forEach {
                when (it) {
                    is AccessibilityNodeInfo -> mAccessibilityNodeInfoList[it] = this
                    is AccessibilityNodeInfoCompat -> mAccessibilityNodeInfoList[it.unwrap()] = this
                }
            }
        }
    }

    private class NoOpAllocator : AccessibilityNodeInfoAllocator() {

        override fun getParent(n: AccessibilityNodeInfoCompat): AccessibilityNodeInfoCompat = n.parent

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

        val NONE: AccessibilityNodeInfoAllocator = NoOpAllocator()

        val global = AccessibilityNodeInfoAllocator()

        fun recycleList(root: AccessibilityNodeInfo, list: List<AccessibilityNodeInfo>) {
            // FIXME: 2017/5/1 Issue #180
            list.takeIf { SDK_INT < TIRAMISU }
                ?.filter { it !== root }
                ?.forEach {
                    @Suppress("DEPRECATION")
                    it.recycle()
                }
        }

    }

}
