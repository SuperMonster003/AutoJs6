package org.autojs.autojs.core.automator.search

import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.default
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.filter.Filter

interface SearchAlgorithm {

    /**
     * The nodes under (and including) [root] passing [filter], at most [limit]. Visited nodes that
     * are not part of the result are released through [lifecycle]; [root] itself never is.
     * zh-CN: [root] 之下 (含自身) 通过 [filter] 的节点, 至多 [limit] 个. 访问过但不在结果中的节点经 [lifecycle] 释放; [root] 本身从不释放.
     */
    fun search(root: UiObject, filter: Filter, limit: Int = Int.MAX_VALUE, lifecycle: NodeLifecycle = NodeLifecycle.default): ArrayList<UiObject>

}