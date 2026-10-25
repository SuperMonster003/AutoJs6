package org.autojs.autojs.core.automator.query

/**
 * The check of a single result before the script receives it (docs/selector/ROADMAP.md D6 (c), P3.2): the node found
 * is refreshed, and when it has left the window since the search (its refresh fails) the selector searches again, at
 * most [RETRIES] times (a query without limits searches until a node refreshes); a node still stale then counts as
 * nothing found. `find()` / `findOnce(index)` and the other results of all nodes are not checked. Android-free: the
 * refresh and the search are given.
 * zh-CN: 单个结果交给脚本前的校验 (D6 (c), P3.2): 刷新找到的节点, 它在搜索之后已离开窗口 (刷新失败) 时重新搜索, 至多 [RETRIES] 次
 * (无上限的查询一直搜索到节点刷新成功为止); 仍然过期则按未找到处理. `find()` / `findOnce(index)` 等全部结果不校验. 不依赖 Android: 刷新与搜索由调用方给出.
 *
 * Created by SuperMonster003 on Oct 26, 2026.
 */
object HitVerifier {

    /** The searches after a stale node, at most. zh-CN: 遇到过期节点后的最多搜索次数. */
    const val RETRIES = 2

    /**
     * [found] if its [refresh] succeeds, else the first node of at most [retries] searches [again] whose refresh does;
     * null when a search finds nothing or every node is stale. [stale] receives every node whose refresh failed.
     * zh-CN: [refresh] 成功时为 [found], 否则为至多 [retries] 次 [again] 搜索中首个刷新成功的节点; 某次搜索没有结果或节点全部过期时为 null.
     * [stale] 接收每个刷新失败的节点.
     */
    inline fun <N : Any> verify(found: N, retries: Int = RETRIES, refresh: (N) -> Boolean, stale: (N) -> Unit = {}, again: () -> N?): N? {
        var node = found
        var searches = 0
        while (true) {
            if (refresh(node)) return node
            stale(node)
            if (searches >= retries) return null
            searches += 1
            node = again() ?: return null
        }
    }

}
