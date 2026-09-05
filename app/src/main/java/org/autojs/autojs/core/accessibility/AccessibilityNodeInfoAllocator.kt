package org.autojs.autojs.core.accessibility

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.NodeRegistry
import org.autojs.autojs.core.accessibility.node.default
import org.autojs.autojs.core.accessibility.node.release

/**
 * Hands out nodes and remembers them, so that [recycleAll] releases what the holder did not
 * release itself. Recycling itself is decided by [lifecycle]: on platforms without node recycling
 * nothing is remembered either. The compat overloads work on the raw node of the given compat
 * wrapper (a `UiObject` overrides the compat accessors to call back into the allocator, so
 * calling them here would recurse).
 *
 * Not wired into script runtimes on purpose (roadmap P1.5, 2026-09-05): below API 33 nodes are
 * pooled per process, so an instance a script recycled by itself can already be in use by another
 * script when the runtime exits, and [recycleAll] would then recycle someone else's live node
 * (observed on an API 24 emulator). Not recycling merely forgoes pooling; recycling wrongly
 * corrupts other scripts. Kept as the reference implementation for diagnostics and future use.
 *
 * zh-CN: 交出节点并记住它们, 以便 [recycleAll] 释放持有方未自行释放的部分. 是否回收由 [lifecycle] 决定: 无需回收节点的平台上也不做记录.
 * compat 重载在给定 compat 包装的原始节点上操作 (`UiObject` 重写了 compat 访问器并回调分配器, 在此调用它们会递归).
 * 有意不接入脚本运行时 (路线图 P1.5, 2026-09-05): API 33 以下节点按进程池化, 脚本自行回收过的实例在运行时退出时可能已被另一个脚本使用,
 * [recycleAll] 会回收到别人的活节点 (在 API 24 模拟器上观察到). 不回收只是失去池化, 回收错了会损坏其它脚本. 保留为诊断与将来使用的参考实现.
 *
 * Created by Stardust on Mar 22, 2017.
 * Modified by SuperMonster003 as of Sep 5, 2026.
 */
open class AccessibilityNodeInfoAllocator(private val lifecycle: NodeLifecycle = NodeLifecycle.default) {

    private val registry = NodeRegistry<AccessibilityNodeInfo>()

    /** Number of nodes handed out and not released yet. zh-CN: 已交出且尚未释放的节点数. */
    val registeredCount: Int get() = registry.size

    open fun getChild(parent: AccessibilityNodeInfo, i: Int): AccessibilityNodeInfo? = add(parent.getChild(i))

    open fun getChild(parent: AccessibilityNodeInfoCompat, i: Int): AccessibilityNodeInfoCompat? = add(parent.unwrap()?.getChild(i))?.let(AccessibilityNodeInfoCompat::wrap)

    open fun getParent(n: AccessibilityNodeInfo): AccessibilityNodeInfo? = add(n.parent)

    open fun getParent(n: AccessibilityNodeInfoCompat): AccessibilityNodeInfoCompat? = add(n.unwrap()?.parent)?.let(AccessibilityNodeInfoCompat::wrap)

    open fun findAccessibilityNodeInfosByText(root: AccessibilityNodeInfo, text: String): List<AccessibilityNodeInfo> =
        root.findAccessibilityNodeInfosByText(text).also { addAll(it) }

    open fun findAccessibilityNodeInfosByText(root: AccessibilityNodeInfoCompat, text: String): List<AccessibilityNodeInfoCompat> =
        root.unwrap()?.findAccessibilityNodeInfosByText(text).orEmpty().also { addAll(it) }.map(AccessibilityNodeInfoCompat::wrap)

    open fun findAccessibilityNodeInfosByViewId(root: AccessibilityNodeInfo, id: String): List<AccessibilityNodeInfo> =
        root.findAccessibilityNodeInfosByViewId(id).also { addAll(it) }

    open fun findAccessibilityNodeInfosByViewId(root: AccessibilityNodeInfoCompat, id: String): List<AccessibilityNodeInfoCompat> =
        root.unwrap()?.findAccessibilityNodeInfosByViewId(id).orEmpty().also { addAll(it) }.map(AccessibilityNodeInfoCompat::wrap)

    open fun recycle(nodeInfo: AccessibilityNodeInfo) {
        lifecycle.release(nodeInfo)
        registry.unregister(nodeInfo)
    }

    open fun recycle(nodeInfo: AccessibilityNodeInfoCompat) {
        nodeInfo.unwrap()?.let { recycle(it) }
    }

    /**
     * Releases every node still registered; returns how many were released now (nodes the script
     * already recycled itself fail quietly and are not counted).
     * zh-CN: 释放仍登记在册的全部节点; 返回本次释放的数量 (脚本已自行回收的节点会静默失败, 不计入).
     */
    open fun recycleAll(): Int {
        val snapshot = registry.drain()
        var released = 0
        snapshot.forEach { (node, origin) ->
            if (lifecycle.release(node)) {
                released++
                origin.takeIf { it.isNotEmpty() }?.let { Log.w(TAG, it) }
            }
        }
        return released.also { Log.v(TAG, "Registered: ${snapshot.size}; released now: $it") }
    }

    fun add(nodeInfo: AccessibilityNodeInfo?): AccessibilityNodeInfo? = nodeInfo?.also { register(it, captureOrigin()) }

    private fun addAll(nodeInfoList: Collection<AccessibilityNodeInfo>) {
        if (nodeInfoList.isEmpty()) return
        val origin = captureOrigin()
        nodeInfoList.forEach { register(it, origin) }
    }

    // Nothing to release later on platforms without recycling, so nothing is remembered either.
    // zh-CN: 无需回收的平台上以后也无需释放, 因此也不做记录.
    protected open fun register(node: AccessibilityNodeInfo, origin: String) {
        if (lifecycle.recycles) registry.register(node, origin)
    }

    // Capturing a full stack trace for every node is expensive, so it is opt-in for leak diagnosis only.
    // zh-CN: 为每个节点捕获完整堆栈开销很大, 因此仅在排查泄漏时按需开启.
    private fun captureOrigin(): String = when {
        isTracingOrigin -> Thread.currentThread().stackTrace.contentToString()
        else -> ""
    }

    /** Hands nodes out without remembering them. zh-CN: 交出节点但不记录. */
    private class NoOpAllocator : AccessibilityNodeInfoAllocator() {

        override fun register(node: AccessibilityNodeInfo, origin: String) = Unit

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

        /** Releases [list] but [root] through the process-wide lifecycle. zh-CN: 经进程级生命周期对象释放 [list] 中除 [root] 外的节点. */
        @Suppress("DEPRECATION")
        fun recycleList(root: AccessibilityNodeInfo, list: List<AccessibilityNodeInfo>) {
            // FIXME by Stardust on May 1, 2017.
            //  ! Issue #180.
            NodeLifecycle.default.releaseAll(list, except = root) { it.recycle() }
        }

    }

}
