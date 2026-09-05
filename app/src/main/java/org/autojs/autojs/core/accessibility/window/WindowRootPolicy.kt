package org.autojs.autojs.core.accessibility.window

/**
 * How the window roots a query runs against are chosen. Android-free: [W] is the window type
 * (`AccessibilityWindowInfo` in production), the filter is a plain predicate on it.
 *
 * - With a [filter], the roots are those of the windows passing it (`service.windows`), whatever
 *   the mode; this is what `auto.setWindowFilter()` sets up.
 * - Without one, the single root of the active window: `rootInActiveWindow` in [Mode.NORMAL],
 *   the root cached from the latest window event in [Mode.FAST].
 * - When that root is unavailable (the system does not know the active window yet, which happens
 *   right after the service has been bound and on API 26 can last until the next window change),
 *   [fallback] decides whether the application windows of the window list stand in.
 *
 * zh-CN: 查询所针对的窗口根节点如何选取. Android-free: [W] 为窗口类型 (生产环境为 `AccessibilityWindowInfo`), 过滤器是其上的普通谓词.
 * 有 [filter] 时, 根节点为通过过滤的窗口 (`service.windows`) 的根节点, 与模式无关 (即 `auto.setWindowFilter()` 的效果);
 * 无过滤器时为活动窗口的单个根节点: [Mode.NORMAL] 取 `rootInActiveWindow`, [Mode.FAST] 取最近一次窗口事件缓存的根节点;
 * 该根节点不可用时 (系统尚不知道活动窗口, 服务刚绑定后即如此, API 26 上可持续到下一次窗口变化), 由 [fallback] 决定是否以窗口列表中的应用窗口代替.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
data class WindowRootPolicy<W>(
    val mode: Mode = Mode.NORMAL,
    val filter: ((W) -> Boolean)? = null,
    val fallback: Fallback = Fallback.NONE,
) {

    enum class Mode { NORMAL, FAST }

    /** What stands in for the active window root while the system does not provide one. zh-CN: 系统不提供活动窗口根节点时以什么代替. */
    enum class Fallback {

        /** Nothing: an empty root list, `null` for the single-root queries. zh-CN: 不代替: 根节点列表为空, 单根节点查询为 `null`. */
        NONE,

        /**
         * The roots of the application windows of the window list, focused ones first, then active
         * ones, then the rest in list order.
         * zh-CN: 窗口列表中应用窗口的根节点, 焦点窗口优先, 其次活动窗口, 其余按列表顺序.
         */
        APPLICATION_WINDOWS,
    }

    companion object {

        /** Legacy `AccessibilityBridge.MODE_*` value of [Mode]. zh-CN: [Mode] 对应的旧 `AccessibilityBridge.MODE_*` 值. */
        fun modeOf(legacyMode: Int): Mode = if (legacyMode and 1 != 0) Mode.FAST else Mode.NORMAL

    }

}
