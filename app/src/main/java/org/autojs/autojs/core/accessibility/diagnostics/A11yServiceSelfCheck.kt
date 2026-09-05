package org.autojs.autojs.core.accessibility.diagnostics

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.default
import org.autojs.autojs.core.accessibility.node.release
import java.util.Locale

/**
 * Bounded self-check of a bound accessibility service instance, run from the service manager dialog:
 * counts the window roots, walks the node trees (node and time bounded) and then waits for an event.
 * "No event" is not a failure by itself, a still screen may simply not produce any.
 *
 * zh-CN: 对已绑定服务实例的有界自检, 由服务管理器对话框触发: 统计窗口根节点, 遍历节点树 (节点数与时间均有上限),
 * 然后等待事件投递. "无事件" 本身不算失败, 静止的屏幕可能就是没有事件.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
object A11yServiceSelfCheck {

    const val MAX_NODES = 1000
    const val WALK_DEADLINE_MILLIS = 1500L
    const val EVENT_TIMEOUT_MILLIS = 2000L

    private const val EVENT_POLL_INTERVAL_MILLIS = 50L

    data class Result(
        val windows: Int,
        val roots: Int,
        /** Visited nodes; [truncated] tells whether the node or time bound stopped the walk. zh-CN: 已访问的节点数; [truncated] 表示遍历因节点数或时间上限而中止. */
        val nodes: Int,
        val truncated: Boolean,
        val walkMillis: Long,
        /** Time from the start of the check until an event was observed, null when none arrived in time. zh-CN: 自检开始到观察到事件的时间, 超时无事件为 null. */
        val eventMillis: Long?,
        val totalMillis: Long,
    ) {

        /** e.g. `windows 3, roots 3, nodes 1000+, walk 412 ms, event 96 ms, total 0.5 s`. */
        fun toText(): String = buildString {
            append("windows ").append(windows)
            append(", roots ").append(roots)
            append(", nodes ").append(nodes)
            if (truncated) append('+')
            append(", walk ").append(walkMillis).append(" ms")
            append(", event ").append(eventMillis?.let { "$it ms" } ?: "none")
            append(", total ").append(String.format(Locale.US, "%.1f s", totalMillis / 1000.0))
        }

    }

    /** Blocking (up to ~3.5 s); must not be called on the main thread. zh-CN: 阻塞 (最长约 3.5 s); 不得在主线程调用. */
    fun run(service: AccessibilityService): Result {
        val start = SystemClock.elapsedRealtime()
        val eventsBefore = service.eventCount.get()

        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        val roots = windows.mapNotNull { window -> runCatching { window.root }.getOrNull() }.toMutableList()
        if (roots.isEmpty()) {
            service.rootInActiveWindow?.let(roots::add)
        }

        val walk = walk(roots, deadline = start + WALK_DEADLINE_MILLIS)
        val walkEnd = SystemClock.elapsedRealtime()

        val eventMillis = awaitEvent(service, eventsBefore, start, deadline = walkEnd + EVENT_TIMEOUT_MILLIS)
        val end = SystemClock.elapsedRealtime()

        return Result(
            windows = windows.size,
            roots = roots.size,
            nodes = walk.nodes,
            truncated = walk.truncated,
            walkMillis = walkEnd - start,
            eventMillis = eventMillis,
            totalMillis = end - start,
        )
    }

    private class Walk(val nodes: Int, val truncated: Boolean)

    // Iterative DFS with the recycling rules of core/automator/search/DFS.kt: a parent is recycled once
    // its children are on the stack, the remaining stack is recycled when the walk stops early, and
    // every root is recycled exactly once at the end. fastRootInActiveWindow is never touched.
    // zh-CN: 迭代 DFS, 回收规则同 core/automator/search/DFS.kt: 子节点入栈后回收父节点, 提前中止时回收栈中剩余节点,
    // 每个根节点最后各回收一次. 不触碰 fastRootInActiveWindow.
    private fun walk(roots: List<AccessibilityNodeInfo>, deadline: Long): Walk {
        var count = 0
        var truncated = false
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        try {
            roots@ for (root in roots) {
                stack.addLast(root)
                while (stack.isNotEmpty()) {
                    if (count >= MAX_NODES || SystemClock.elapsedRealtime() > deadline) {
                        truncated = true
                        break@roots
                    }
                    val node = stack.removeLast()
                    count++
                    val childCount = runCatching { node.childCount }.getOrDefault(0)
                    for (i in childCount - 1 downTo 0) {
                        val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                        stack.addLast(child)
                    }
                    if (roots.none { it === node }) {
                        recycle(node)
                    }
                }
            }
        } finally {
            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                if (roots.none { it === node }) {
                    recycle(node)
                }
            }
            roots.forEach(::recycle)
        }
        return Walk(count, truncated)
    }

    private fun recycle(node: AccessibilityNodeInfo) {
        NodeLifecycle.default.release(node)
    }

    private fun awaitEvent(service: AccessibilityService, eventsBefore: Long, start: Long, deadline: Long): Long? {
        while (true) {
            val now = SystemClock.elapsedRealtime()
            if (service.eventCount.get() > eventsBefore) return now - start
            if (now >= deadline) return null
            try {
                Thread.sleep(EVENT_POLL_INTERVAL_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
        }
    }

}
