package org.autojs.autojs.core.accessibility.node

/**
 * The one place that decides whether a node is recycled and how a failing recycle is treated:
 * every former inline `recycle()` of the search algorithms, scroll actions, the service's cached
 * root and the allocator goes through here. Android-free: the node type is whatever the caller
 * passes, together with the function that recycles it (see `NodeLifecycles.kt` for the framework
 * node types). Recycling only happens when the [policy] requires it; a recycle that throws (node
 * already recycled, framework error) is logged and swallowed, never propagated into a query.
 *
 * zh-CN: 决定 "节点是否回收" 与 "回收失败如何处理" 的唯一位置: 搜索算法, 滚动动作, 服务缓存的根节点与分配器原先的内联
 * `recycle()` 全部经由此处. Android-free: 节点类型由调用方决定, 连同回收它的函数一起传入 (框架节点类型见 `NodeLifecycles.kt`).
 * 仅当 [policy] 要求时才回收; 回收抛出的异常 (节点已回收, 框架错误) 会被记录并吞掉, 不会传播到查询中.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
class NodeLifecycle(
    val policy: NodeRecyclePolicy,
    private val log: (String, Throwable) -> Unit = { _, _ -> },
) {

    /** Whether nodes have to be recycled at all on this platform. zh-CN: 本平台上是否需要回收节点. */
    val recycles: Boolean get() = policy.recycles

    /**
     * Runs [recycle] when the policy requires recycling; returns whether it ran to completion.
     * zh-CN: 策略要求回收时执行 [recycle]; 返回它是否正常执行完毕.
     */
    fun release(recycle: () -> Unit): Boolean {
        if (!policy.recycles) return false
        return try {
            recycle()
            true
        } catch (e: Throwable) {
            log("Unable to recycle accessibility node", e)
            false
        }
    }

    /**
     * Releases every node of [nodes] but [except] (compared by identity, typically the root the
     * caller does not own); returns how many were released.
     * zh-CN: 释放 [nodes] 中除 [except] (按同一性比较, 通常是调用方并不拥有的根节点) 以外的每个节点; 返回释放的数量.
     */
    fun <N : Any> releaseAll(nodes: Iterable<N>, except: N? = null, recycle: (N) -> Unit): Int {
        if (!policy.recycles) return 0
        var released = 0
        for (node in nodes) {
            if (node === except) continue
            if (release { recycle(node) }) released++
        }
        return released
    }

    /** Extension anchor: `NodeLifecycle.default` lives in `NodeLifecycles.kt`. zh-CN: 扩展锚点: `NodeLifecycle.default` 位于 `NodeLifecycles.kt`. */
    companion object

}
