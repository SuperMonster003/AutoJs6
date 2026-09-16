package org.autojs.autojs.core.automator.toolkit

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.flow.FlowError
import org.autojs.autojs.core.automator.query.PollPolicy
import org.autojs.autojs.core.automator.query.Poller
import kotlin.math.pow
import kotlin.random.Random

/**
 * The high-level automation tools of roadmap P4 (Appendix B) over one [ToolkitBindings] /
 * [ActionPerformer] pair: the smart click, the scrolling search, text entry, popup dismissal,
 * list collection, app launching and the checkable toggle, plus the small compositions
 * ([findAny], [clickAny], [clickIfExists], [retry]) and what every tool shares: the poller over the
 * injected clock and sleep, the humanized pause, the locate plan and the scroller. Free of
 * Android and Rhino: the runtime builds one per script over `UiObject` (P4.1), the JavaScript
 * surfaces (`automator.*`, the global functions and the Flow steps, P4.2) call it, and the tests
 * drive it over `FakeUiNode`. Failures are the `FlowError`s of the Flow core, with the tool's name
 * as the step and a `reason` refining the code.
 * zh-CN: Roadmap P4 (附录 B) 的高阶自动化工具, 建立在一对 [ToolkitBindings] / [ActionPerformer] 之上: 智能点击, 滚动查找, 文本输入,
 * 弹窗关闭, 列表采集, 应用启动与开关切换, 以及小型组合 ([findAny], [clickAny], [clickIfExists], [retry]) 和各工具共用的部分: 基于注入
 * 时钟与休眠的轮询器, 拟人暂停, 定位计划与滚动器. 不依赖 Android 与 Rhino: 运行时为每个脚本基于 `UiObject` 建一个 (P4.1), JavaScript
 * 表面 (`automator.*`, 全局函数与 Flow 步骤, P4.2) 调用它, 测试则基于 `FakeUiNode` 驱动它. 失败为 Flow 核心的 `FlowError`, 以工具名为
 * 步骤名, `reason` 细化代码.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class Toolkit<N : UiNode<N>>(
    val bindings: ToolkitBindings<N>,
    val actions: ActionPerformer<N>,
    val clock: () -> Long = { System.currentTimeMillis() },
    val sleep: (Long) -> Unit = { Thread.sleep(it) },
    val random: Random = Random.Default,
    /**
     * Whether an exception must stop a tool at once instead of being retried or reported (the
     * runtime names its script interruption here); the default treats nothing as fatal.
     * zh-CN: 异常是否必须立即终止工具而不可重试或仅报告 (运行时在此指定脚本中断异常); 默认没有致命异常.
     */
    val fatal: (Throwable) -> Boolean = { false },
) {

    val smartClick = SmartClick(this)

    val scrollSearch = ScrollSearch(this)

    val textEntry = TextEntry(this)

    val popups = PopupDismisser(this)

    val listCollector = ListCollector(this)

    val apps = AppLauncher(this)

    val toggle = Toggle(this)

    /**
     * How long a tool looks for its target and how often: [timeout] of 0 (the default) is a single
     * look, anything else polls every [interval] milliseconds until the timeout.
     * zh-CN: 工具寻找目标的时长与频率: [timeout] 为 0 (默认) 时只看一次, 否则每 [interval] 毫秒轮询直到超时.
     */
    data class Locate(val timeout: Long = 0, val interval: Long = PollPolicy.DEFAULT_INTERVAL) {

        init {
            require(timeout >= 0) { "timeout ($timeout) cannot be negative" }
            require(interval >= 0) { "interval ($interval) cannot be negative" }
        }

        val policy: PollPolicy get() = within(timeout, interval)

        companion object {

            val ONCE = Locate()

            fun within(timeout: Long, interval: Long = PollPolicy.DEFAULT_INTERVAL): PollPolicy = when {
                timeout > 0 -> PollPolicy(timeout = timeout, interval = interval)
                else -> PollPolicy.ONCE
            }

        }

    }

    /** What a poll ended with: the value (or `null`), the milliseconds spent and the attempts made. zh-CN: 一次轮询的结局: 值 (或 `null`), 已用毫秒数与尝试次数. */
    class Polled<T : Any>(val value: T?, val elapsed: Long, val attempts: Long)

    /** Polls [attempt] under [policy] with this toolkit's clock and sleep. zh-CN: 以本工具集的时钟与休眠按 [policy] 轮询 [attempt]. */
    fun <T : Any> poll(policy: PollPolicy, attempt: (Poller.Progress) -> T?): Polled<T> {
        var last: Poller.Progress? = null
        val start = clock()
        val value = Poller(clock, sleep).poll(policy) { progress ->
            last = progress
            attempt(progress)
        }
        return Polled(value, last?.elapsed ?: (clock() - start), last?.round ?: 0)
    }

    /** [Locate.within] for callers holding raw numbers. zh-CN: 供持有原始数字的调用方使用的 [Locate.within]. */
    fun within(timeout: Long, interval: Long = PollPolicy.DEFAULT_INTERVAL): PollPolicy = Locate.within(timeout, interval)

    /**
     * [target] located under [root] per [locate], or `TIMEOUT` (the target's description as the
     * selector, the time and attempts spent).
     * zh-CN: 按 [locate] 在 [root] 下定位到的 [target], 找不到则 `TIMEOUT` (以目标的描述为选择器, 附已用时间与尝试次数).
     */
    fun locate(target: Any?, root: N?, step: String, locate: Locate = Locate.ONCE): N {
        val polled = poll(locate.policy) { bindings.find(target, root) }
        return polled.value ?: throw FlowError.Timeout(step, bindings.describe(target), polled.elapsed, polled.attempts)
    }

    /** Sleeps the random pause of [humanize], if any. zh-CN: 休眠 [humanize] 的随机暂停时长 (若有). */
    fun pause(humanize: Humanize) {
        val delay = humanize.delay(random)
        if (delay > 0) sleep(delay)
    }

    // Compositions. zh-CN: 组合.

    /** A candidate that was present: its index in the list, the candidate itself and the node. zh-CN: 存在的候选项: 在列表中的索引, 候选项本身与节点. */
    data class Found<N>(val index: Int, val target: Any?, val node: N)

    /**
     * The first of [targets] (in list order) present under [root], polled per [locate]; `null` when
     * none showed up (`findAny`).
     * zh-CN: [targets] 中 (按列表顺序) 首个存在于 [root] 下的候选项, 按 [locate] 轮询; 都没出现则为 `null` (`findAny`).
     */
    fun findAny(targets: List<Any?>, root: N? = null, locate: Locate = Locate.ONCE): Found<N>? {
        require(targets.isNotEmpty()) { "At least one target is required" }
        return poll(locate.policy) { firstPresent(targets, root) }.value
    }

    private fun firstPresent(targets: List<Any?>, root: N?): Found<N>? {
        targets.forEachIndexed { index, target ->
            bindings.find(target, root)?.let { return Found(index, target, it) }
        }
        return null
    }

    /** What [clickAny] clicked and how. zh-CN: [clickAny] 点了什么, 怎样点的. */
    data class Clicked<N>(val found: Found<N>, val outcome: SmartClick.Outcome<N>)

    /**
     * Smart-clicks the first of [targets] present under [root]; `null` when none showed up within
     * [locate], a `FlowError` when the click failed (`clickAny`).
     * zh-CN: 智能点击 [targets] 中首个存在于 [root] 下的候选项; [locate] 内都没出现则为 `null`, 点击失败则为 `FlowError` (`clickAny`).
     */
    fun clickAny(targets: List<Any?>, root: N? = null, locate: Locate = Locate.ONCE, options: SmartClick.Options = SmartClick.Options(), step: String = STEP_CLICK_ANY): Clicked<N>? {
        val found = findAny(targets, root, locate) ?: return null
        return Clicked(found, smartClick.click(found.node, options, step, root, bindings.describe(found.target)))
    }

    /**
     * Smart-clicks [target] when it is present under [root] within [locate]; `null` when it is not
     * (no error), a `FlowError` when the click failed (`clickIfExists`).
     * zh-CN: [target] 在 [locate] 内存在于 [root] 下时智能点击它; 不存在为 `null` (不报错), 点击失败为 `FlowError` (`clickIfExists`).
     */
    fun clickIfExists(target: Any?, root: N? = null, locate: Locate = Locate.ONCE, options: SmartClick.Options = SmartClick.Options(), step: String = STEP_CLICK_IF_EXISTS): SmartClick.Outcome<N>? {
        val node = poll(locate.policy) { bindings.find(target, root) }.value ?: return null
        return smartClick.click(node, options, step, root, bindings.describe(target))
    }

    /**
     * The plan of [retry]: [times] reruns after the first failure (as the Flow `retry` step counts),
     * [delay] milliseconds before the first rerun, multiplied by [backoff] for every further one.
     * zh-CN: [retry] 的计划: 首次失败后重跑 [times] 次 (与 Flow 的 `retry` 步骤计法一致), 首次重跑前等待 [delay] 毫秒, 之后每次乘以 [backoff].
     */
    data class RetryOptions(val times: Int = 3, val delay: Long = 500, val backoff: Double = 1.0) {

        init {
            require(times >= 0) { "times ($times) cannot be negative" }
            require(delay >= 0) { "delay ($delay) cannot be negative" }
            require(backoff >= 1.0) { "backoff ($backoff) must be at least 1" }
        }

        /** The pause before rerun number [rerun] (1-based). zh-CN: 第 [rerun] 次 (从 1 计) 重跑前的等待时长. */
        fun delayBefore(rerun: Int): Long = if (backoff == 1.0) delay else (delay * backoff.pow(rerun - 1)).toLong()

    }

    /**
     * Runs [block] (given the 0-based attempt number) until it returns, rerunning it per [options]
     * when it throws; the last error is rethrown once the reruns are used up. A cancellation, a
     * [fatal] error, a JVM `Error` or a pending interrupt is never retried.
     * zh-CN: 运行 [block] (传入从 0 计的尝试序号) 直到它返回, 抛出时按 [options] 重跑; 重跑用尽后重新抛出最后的错误. 取消, [fatal] 判定
     * 的错误, JVM `Error` 与待处理的中断一律不重试.
     */
    fun <T> retry(options: RetryOptions = RetryOptions(), block: (attempt: Int) -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block(attempt)
            } catch (e: FlowError.Cancelled) {
                throw e
            } catch (e: Error) {
                throw e
            } catch (e: Exception) {
                if (fatal(e) || e is InterruptedException || Thread.currentThread().isInterrupted || attempt >= options.times) throw e
                attempt += 1
                val delay = options.delayBefore(attempt)
                if (delay > 0) sleep(delay)
            }
        }
    }

    // Scrolling support. zh-CN: 滚动支持.

    /**
     * The scroll container behind [container]: the node it locates under [root] per [locate]
     * (`INVALID_TARGET`, reason `container`, when absent), or with `null` the largest scrollable
     * node of [root] / the active window (`null` when nothing scrolls).
     * zh-CN: [container] 对应的滚动容器: 按 [locate] 在 [root] 下定位到的节点 (不存在则 `INVALID_TARGET`, reason `container`), 或 [container]
     * 为 `null` 时 [root] / 活动窗口中面积最大的可滚动节点 (没有则 `null`).
     */
    fun resolveContainer(container: Any?, root: N?, step: String, locate: Locate = Locate.ONCE): N? = when (container) {
        null -> (root ?: bindings.activeRoot())?.let { UiNodes.largestScrollable(it) }
        else -> poll(locate.policy) { bindings.find(container, root) }.value
            ?: throw FlowError.InvalidTarget(step, bindings.describe(container), REASON_CONTAINER)
    }

    /**
     * A [Scroller] over [container] (see [resolveContainer]); the action method needs a container
     * (`INVALID_TARGET`, reason `noScrollable`, otherwise), the gesture method swipes over [root] /
     * the active window / the screen without one.
     * zh-CN: [container] (见 [resolveContainer]) 上的 [Scroller]; 动作方式需要容器 (否则 `INVALID_TARGET`, reason `noScrollable`), 手势
     * 方式没有容器时在 [root] / 活动窗口 / 屏幕上滑动.
     */
    fun scroller(
        container: Any?,
        root: N?,
        direction: ScrollDirection,
        method: ScrollMethod,
        settleMillis: Long,
        gestureDuration: Long,
        humanize: Humanize,
        detectEnd: Boolean,
        step: String,
        locate: Locate = Locate.ONCE,
    ): Scroller<N> {
        val node = resolveContainer(container, root, step, locate)
        if (node == null && method == ScrollMethod.ACTION) {
            throw FlowError.InvalidTarget(step, container?.let { bindings.describe(it) }, REASON_NO_SCROLLABLE)
        }
        return Scroller(this, node, root, direction, method, settleMillis, gestureDuration, humanize, detectEnd, step)
    }

    companion object {

        const val STEP_CLICK_ANY = "clickAny"

        const val STEP_CLICK_BOUNDS_ANY = "clickBoundsAny"

        const val STEP_CLICK_IF_EXISTS = "clickIfExists"

        const val STEP_CLICK_BOUNDS_IF_EXISTS = "clickBoundsIfExists"

        /** A given container is not there. zh-CN: 指定的容器不存在. */
        const val REASON_CONTAINER = "container"

        /** No container was given and nothing scrolls. zh-CN: 未指定容器且没有可滚动节点. */
        const val REASON_NO_SCROLLABLE = "noScrollable"

    }

}
