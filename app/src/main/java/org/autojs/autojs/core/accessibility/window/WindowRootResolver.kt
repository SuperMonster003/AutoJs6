package org.autojs.autojs.core.accessibility.window

/**
 * The one implementation of "which roots does a query run against", shared by the three root
 * queries of `AccessibilityBridge` ([roots] for `windowRoots()`, [currentRoot] for
 * `getRootInCurrentWindow()`, [activeRoot] for `getRootInActiveWindow()`) and by the layout
 * inspector. Android-free: windows ([W]) and roots ([R]) are opaque and every accessor of the
 * service is behind [Source], so the selection rules are unit tested on the JVM.
 *
 * Every accessor may throw (the framework does when the service is gone or a window is stale);
 * a throw is treated as "unavailable" and reported through [log].
 *
 * zh-CN: "查询针对哪些根节点" 的唯一实现, 由 `AccessibilityBridge` 的三个根节点查询 (`windowRoots()` 用 [roots],
 * `getRootInCurrentWindow()` 用 [currentRoot], `getRootInActiveWindow()` 用 [activeRoot]) 与布局分析器共用.
 * Android-free: 窗口 ([W]) 与根节点 ([R]) 均为不透明类型, 服务的每个访问器都藏在 [Source] 之后, 选取规则因此可在 JVM 上单元测试.
 * 每个访问器都可能抛出异常 (服务已消失或窗口已过期时框架会抛出), 抛出视为 "不可用" 并经 [log] 报告.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
class WindowRootResolver<W : Any, R : Any>(
    private val source: Source<W, R>,
    private val log: (String, Throwable) -> Unit = { _, _ -> },
) {

    /** Accessors of the accessibility service behind the resolver. zh-CN: 解析器背后无障碍服务的访问器. */
    interface Source<W : Any, R : Any> {

        /** `service.windows`; `null` when there is no service. zh-CN: `service.windows`; 无服务时为 `null`. */
        fun windows(): List<W>?

        /** `window.root`. zh-CN: `window.root`. */
        fun rootOf(window: W): R?

        /** `service.rootInActiveWindow`; `null` when there is no service or no active window. zh-CN: `service.rootInActiveWindow`; 无服务或无活动窗口时为 `null`. */
        fun activeRoot(): R?

        /** The root cached from the latest window event ([WindowRootPolicy.Mode.FAST]). zh-CN: 最近一次窗口事件缓存的根节点 ([WindowRootPolicy.Mode.FAST]). */
        fun fastRoot(): R?

        fun isApplication(window: W): Boolean

        fun isFocused(window: W): Boolean

        fun isActive(window: W): Boolean

    }

    /**
     * The roots a query runs against, never containing `null`: the roots of the windows passing
     * the filter, or the active window root (with the policy's fallback), or nothing.
     * zh-CN: 查询所针对的根节点, 不含 `null`: 通过过滤器的窗口的根节点, 或活动窗口根节点 (含策略的兜底), 或空.
     */
    fun roots(policy: WindowRootPolicy<W>): List<R> {
        val filter = policy.filter
        if (filter != null) {
            return filteredWindows(filter).mapNotNull(::rootOfSafely)
        }
        val root = modeRoot(policy.mode)
        return when {
            root != null -> listOf(root)
            else -> fallbackRoots(policy.fallback)
        }
    }

    /**
     * The first root of [roots], resolved lazily: with a filter, the root of the first passing
     * window that has one (a window without a root is skipped rather than ending the search).
     * zh-CN: [roots] 的首个根节点, 惰性解析: 有过滤器时为首个通过过滤且有根节点的窗口的根节点 (没有根节点的窗口被跳过, 而非终止查找).
     */
    fun currentRoot(policy: WindowRootPolicy<W>): R? {
        val filter = policy.filter
        if (filter != null) {
            return filteredWindows(filter).asSequence().mapNotNull(::rootOfSafely).firstOrNull()
        }
        return modeRoot(policy.mode) ?: fallbackRoots(policy.fallback).firstOrNull()
    }

    /**
     * The active window root regardless of the filter (the policy's mode and fallback still apply).
     * zh-CN: 不受过滤器影响的活动窗口根节点 (策略的模式与兜底仍然生效).
     */
    fun activeRoot(policy: WindowRootPolicy<W>): R? {
        return modeRoot(policy.mode) ?: fallbackRoots(policy.fallback).firstOrNull()
    }

    /** `service.windows` as a list, empty when unavailable. zh-CN: 列表形式的 `service.windows`, 不可用时为空. */
    fun windows(): List<W> {
        return try {
            source.windows().orEmpty()
        } catch (e: Throwable) {
            log("Unable to get accessibility windows", e)
            emptyList()
        }
    }

    private fun modeRoot(mode: WindowRootPolicy.Mode): R? {
        return try {
            when (mode) {
                WindowRootPolicy.Mode.NORMAL -> source.activeRoot()
                WindowRootPolicy.Mode.FAST -> source.fastRoot()
            }
        } catch (e: Throwable) {
            log("Unable to get the active window root", e)
            null
        }
    }

    private fun fallbackRoots(fallback: WindowRootPolicy.Fallback): List<R> = when (fallback) {
        WindowRootPolicy.Fallback.NONE -> emptyList()
        WindowRootPolicy.Fallback.APPLICATION_WINDOWS -> windows()
            .filter { window -> attributeSafely(window, source::isApplication) }
            // Focused first, then active, then list order (sortedBy is stable).
            // zh-CN: 焦点窗口优先, 其次活动窗口, 其余保持列表顺序 (sortedBy 是稳定排序).
            .sortedBy { window ->
                when {
                    attributeSafely(window, source::isFocused) -> 0
                    attributeSafely(window, source::isActive) -> 1
                    else -> 2
                }
            }
            .mapNotNull(::rootOfSafely)
    }

    private fun filteredWindows(filter: (W) -> Boolean): List<W> = windows().filter { window ->
        try {
            filter(window)
        } catch (e: Throwable) {
            log("Unable to filter accessibility window", e)
            false
        }
    }

    private fun rootOfSafely(window: W): R? {
        return try {
            source.rootOf(window)
        } catch (e: Throwable) {
            log("Unable to get accessibility window root", e)
            null
        }
    }

    private fun attributeSafely(window: W, attribute: (W) -> Boolean): Boolean {
        return try {
            attribute(window)
        } catch (e: Throwable) {
            log("Unable to read accessibility window attribute", e)
            false
        }
    }

}
