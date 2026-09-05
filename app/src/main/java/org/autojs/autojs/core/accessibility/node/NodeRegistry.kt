package org.autojs.autojs.core.accessibility.node

import java.util.Collections
import java.util.IdentityHashMap

/**
 * Bookkeeping of the nodes handed out through a runtime's allocator so that whatever a script
 * did not release itself can be released when the script exits. Android-free and thread-safe:
 * nodes are compared by identity, each entry carries its allocation origin (a stack trace when
 * tracing is on, otherwise empty).
 *
 * zh-CN: 记录经某个运行时的分配器交出的节点, 以便脚本退出时释放其未自行释放的部分. Android-free 且线程安全:
 * 节点按同一性比较, 每项带有分配来源 (开启追踪时为堆栈, 否则为空).
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
class NodeRegistry<N : Any> {

    private val entries: MutableMap<N, String> = Collections.synchronizedMap(IdentityHashMap())

    val size: Int get() = entries.size

    fun register(node: N, origin: String = "") {
        entries[node] = origin
    }

    /** Forgets [node]; returns whether it was registered. zh-CN: 忘记 [node]; 返回它此前是否已登记. */
    fun unregister(node: N): Boolean = entries.remove(node) != null

    /**
     * Removes and returns every entry, so that a release pass works on a snapshot while new nodes
     * may keep arriving.
     * zh-CN: 移除并返回全部条目, 使释放过程在快照上进行, 期间仍可登记新节点.
     */
    fun drain(): Map<N, String> = synchronized(entries) {
        val snapshot = IdentityHashMap(entries)
        entries.clear()
        snapshot
    }

}
