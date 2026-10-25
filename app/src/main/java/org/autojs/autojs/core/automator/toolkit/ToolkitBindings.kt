package org.autojs.autojs.core.automator.toolkit

import org.autojs.autojs.core.automator.UiNode

/**
 * What the toolkit (roadmap P4.1) needs from the runtime on the query side and knows nothing
 * about: how a target (a selector in any `pickup` form, a node, a predicate) is located under a
 * root, how a node is re-read or rendered as a value, which package is in front, how the screen
 * is sized, and how window changes are observed. The runtime implements it over
 * `UiSelector.pickup` / `UiObject.detect` / `ActivityInfoProvider` / the accessibility event bus
 * (`RuntimeToolkitBindings`); the tests implement it over `FakeUiNode` (`FakeToolkitBindings`).
 * Effects go through [ActionPerformer] instead, so that a test can program their outcomes
 * independently of the tree.
 * zh-CN: 工具集 (Roadmap P4.1) 在查询侧需要而自身一无所知的运行时能力: 如何在根节点下定位目标 (`pickup` 任一形式的选择器, 节点,
 * 断言函数), 如何重读节点或把节点呈现为值, 前台是哪个包, 屏幕多大, 以及如何观察窗口变化. 运行时基于 `UiSelector.pickup` / `UiObject.detect` /
 * `ActivityInfoProvider` / 无障碍事件总线实现 (`RuntimeToolkitBindings`); 测试基于 `FakeUiNode` 实现 (`FakeToolkitBindings`).
 * 副作用则走 [ActionPerformer], 使测试能独立于节点树编排动作结果.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
interface ToolkitBindings<N : UiNode<N>> {

    /**
     * One attempt at locating [target] under [root] (`null` = the active window): the node, or
     * `null` when it is not there right now. A node target counts as found while it can be
     * refreshed. May throw a `FlowError` (`A11Y_UNAVAILABLE`) to fail the tool at once.
     * zh-CN: 在 [root] (`null` 为活动窗口) 下定位 [target] 的一次尝试: 节点, 或此刻不存在时为 `null`. 节点目标在能刷新时即算找到.
     * 可抛出 `FlowError` (`A11Y_UNAVAILABLE`) 使工具立即失败.
     */
    fun find(target: Any?, root: N?): N?

    /** Every node matching [target] under [root], in document order; empty when none. zh-CN: [root] 下匹配 [target] 的全部节点, 按文档顺序; 无则为空. */
    fun findAll(target: Any?, root: N?): List<N>

    /**
     * One attempt at the wait condition [cond] under [root]: the satisfying value (a node for a
     * selector, the result for a predicate), or `null` when not satisfied yet, following the
     * truthiness rule of the synchronous `wait`.
     * zh-CN: 对等待条件 [cond] 在 [root] 下的一次尝试: 使条件成立的值 (选择器为节点, 断言函数为其结果), 尚不成立时为 `null`,
     * 遵循同步 `wait` 的真值规则.
     */
    fun probe(cond: Any?, root: N?): Any?

    /** A short rendering of [target] for error messages, e.g. the selector's string form. zh-CN: [target] 用于错误信息的简短呈现, 如选择器的字符串形式. */
    fun describe(target: Any?): String? = target?.toString()

    /** The root of the active window, `null` when there is none. zh-CN: 活动窗口的根节点, 没有时为 `null`. */
    fun activeRoot(): N?

    /** Re-reads [node] from the platform; `false` when it is gone. zh-CN: 从平台重读 [node]; 已消失时为 `false`. */
    fun refresh(node: N): Boolean

    /**
     * [node] rendered as [resultType] would render it in `pickup` / `detect` (`"$"` the content,
     * `"#"` the node itself, a method name, ...); `null` means the default.
     * zh-CN: 把 [node] 按 `pickup` / `detect` 的 [resultType] 呈现 (`"$"` 为内容, `"#"` 为节点自身, 方法名, ...); `null` 为默认.
     */
    fun pick(node: N, resultType: Any?): Any?

    /** The package name in front, `null` when unknown. zh-CN: 前台的包名, 未知时为 `null`. */
    fun currentPackage(): String?

    /**
     * The package name behind [app] (a package name, an application label, an alias or an `App`
     * value), `null` when nothing installed matches.
     * zh-CN: [app] (包名, 应用名, 别名或 `App` 值) 对应的包名, 没有已安装应用匹配时为 `null`.
     */
    fun resolvePackage(app: Any?): String?

    fun screenWidth(): Int

    fun screenHeight(): Int

    /**
     * Runs [onChange] on a worker thread whenever the window state or content changes, at most
     * once per [debounceMillis]; the popup guard is built on it (roadmap P4.1: event-driven, not
     * polling). [Watch.trigger] runs [onChange] once without waiting for a change.
     * zh-CN: 窗口状态或内容变化时在工作线程上运行 [onChange], 每 [debounceMillis] 至多一次; 弹窗守护基于它 (Roadmap P4.1: 事件驱动而非
     * 轮询). [Watch.trigger] 不等变化即运行一次 [onChange].
     */
    fun watchWindows(debounceMillis: Long, onChange: Runnable): Watch

    /** A running window watch. zh-CN: 一个进行中的窗口观察. */
    interface Watch {

        fun stop()

        fun trigger()

    }

}

/**
 * The effect side of the toolkit: node actions, gestures and global actions, each reporting
 * whether the platform accepted it. The runtime implements it over `UiObject` and the script's
 * gesture dispatcher (`RuntimeActionPerformer`); the tests program outcomes and record calls
 * (`FakeActionPerformer`). Coordinates are device pixels.
 * zh-CN: 工具集的副作用侧: 节点动作, 手势与全局动作, 各自报告平台是否接受. 运行时基于 `UiObject` 与脚本的手势派发器实现
 * (`RuntimeActionPerformer`); 测试编排结果并记录调用 (`FakeActionPerformer`). 坐标为设备像素.
 */
interface ActionPerformer<N : UiNode<N>> {

    fun click(node: N): Boolean

    fun longClick(node: N): Boolean

    fun focus(node: N): Boolean

    /** Replaces the text of [node] with [text]. zh-CN: 把 [node] 的文本替换为 [text]. */
    fun setText(node: N, text: String): Boolean

    fun imeEnter(node: N): Boolean

    /**
     * Scrolls [node] one step towards [direction] with the best accessibility action it supports;
     * `false` when it cannot scroll further (or at all).
     * zh-CN: 以 [node] 支持的最合适的无障碍动作向 [direction] 滚动一步; 无法继续滚动 (或根本不能滚动) 时为 `false`.
     */
    fun scroll(node: N, direction: ScrollDirection): Boolean

    fun tap(x: Int, y: Int): Boolean

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMillis: Long): Boolean

    fun back(): Boolean

    /** Starts the launcher activity of [packageName]. zh-CN: 启动 [packageName] 的启动器 Activity. */
    fun launch(packageName: String): Boolean

}

/** Where the content moves out of view when scrolling: `DOWN` reveals what is below. zh-CN: 滚动时内容移出视野的方向: `DOWN` 露出下方的内容. */
enum class ScrollDirection {

    UP,
    DOWN,
    LEFT,
    RIGHT;

    val isForward: Boolean get() = this == DOWN || this == RIGHT

    val isHorizontal: Boolean get() = this == LEFT || this == RIGHT

    companion object {

        /** The direction named [name] (any case), `null` for anything else. zh-CN: 名为 [name] (不分大小写) 的方向, 其它为 `null`. */
        fun of(name: String?): ScrollDirection? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }

    }

}

/** How a container is scrolled: accessibility actions or a swipe gesture. zh-CN: 容器的滚动方式: 无障碍动作或滑动手势. */
enum class ScrollMethod {

    ACTION,
    GESTURE;

    companion object {

        fun of(name: String?): ScrollMethod? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }

    }

}
