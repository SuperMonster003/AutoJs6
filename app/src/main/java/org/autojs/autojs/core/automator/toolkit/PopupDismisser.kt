package org.autojs.autojs.core.automator.toolkit

import org.autojs.autojs.core.automator.UiNode
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Closing popups (roadmap P4.1, `dismissPopups`): given candidates (selectors of the buttons
 * that close them, in order of preference), [dismissOnce] clicks the first one present,
 * [dismissAll] keeps doing so until none is left (bounded by [Options.maxRounds]), and [watch]
 * starts a [Guard] that does the same on every window change reported by the bindings
 * (event-driven through the accessibility event bus, not polling) until stopped. Clicks are smart
 * clicks; a candidate that is absent is not an error.
 * zh-CN: 关闭弹窗 (Roadmap P4.1, `dismissPopups`): 给定候选项 (关闭按钮的选择器, 按优先顺序), [dismissOnce] 点击首个存在的, [dismissAll]
 * 反复如此直到没有剩余 (以 [Options.maxRounds] 为界), [watch] 启动一个 [Guard], 在 bindings 报告的每次窗口变化时做同样的事 (经无障碍
 * 事件总线事件驱动, 而非轮询), 直到被停止. 点击为智能点击; 候选项不存在不算错误.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class PopupDismisser<N : UiNode<N>> internal constructor(internal val kit: Toolkit<N>) {

    data class Options(
        /** How long to wait for the first popup. zh-CN: 等待第一个弹窗出现的计划. */
        val locate: Toolkit.Locate = Toolkit.Locate.ONCE,
        /** The pause between two dismissals of one pass. zh-CN: 一轮中两次关闭之间的停顿. */
        val settleMillis: Long = DEFAULT_SETTLE_MILLIS,
        /** Dismissals per pass at most. zh-CN: 一轮至多关闭的个数. */
        val maxRounds: Int = DEFAULT_MAX_ROUNDS,
        val click: SmartClick.Options = SmartClick.Options(),
        /** The guard runs at most once per this many milliseconds of window changes. zh-CN: 守护在这么多毫秒的窗口变化内至多运行一次. */
        val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    ) {

        init {
            require(settleMillis >= 0) { "settleMillis ($settleMillis) cannot be negative" }
            require(maxRounds > 0) { "maxRounds ($maxRounds) must be positive" }
            require(debounceMillis >= 0) { "debounceMillis ($debounceMillis) cannot be negative" }
        }

        companion object {

            const val DEFAULT_SETTLE_MILLIS = 300L

            const val DEFAULT_MAX_ROUNDS = 10

            const val DEFAULT_DEBOUNCE_MILLIS = 300L

        }

    }

    /** A popup that was closed: which candidate ([index] / [target]), which [node] and how ([outcome]). zh-CN: 被关闭的弹窗: 哪个候选项 ([index] / [target]), 哪个 [node], 如何点击的 ([outcome]). */
    data class Dismissed<N>(val index: Int, val target: Any?, val node: N, val outcome: SmartClick.Outcome<N>)

    /** Clicks the first of [candidates] present within [Options.locate]; `null` when none is. zh-CN: 点击 [Options.locate] 内首个存在的 [candidates]; 都不存在为 `null`. */
    fun dismissOnce(candidates: List<Any?>, options: Options = Options(), root: N? = null, step: String = STEP): Dismissed<N>? {
        val found = kit.findAny(candidates, root, options.locate) ?: return null
        val outcome = kit.smartClick.click(found.node, options.click, step, root, kit.bindings.describe(found.target))
        return Dismissed(found.index, found.target, found.node, outcome)
    }

    /**
     * [dismissOnce] until nothing is present (the first look honours [Options.locate], later ones
     * are single looks after [Options.settleMillis]) or [Options.maxRounds] popups were closed.
     * zh-CN: 反复 [dismissOnce] 直到没有存在的候选项 (首次查找遵循 [Options.locate], 之后各次在 [Options.settleMillis] 后只看一次) 或已
     * 关闭 [Options.maxRounds] 个弹窗.
     */
    fun dismissAll(candidates: List<Any?>, options: Options = Options(), root: N? = null, step: String = STEP): List<Dismissed<N>> {
        val result = ArrayList<Dismissed<N>>()
        var locate = options.locate
        while (result.size < options.maxRounds) {
            result += dismissOnce(candidates, options.copy(locate = locate), root, step) ?: break
            locate = Toolkit.Locate.ONCE
            if (options.settleMillis > 0) kit.sleep(options.settleMillis)
        }
        return result
    }

    /** A running [Guard] over [candidates]; it makes a first pass right away. zh-CN: 对 [candidates] 的运行中 [Guard]; 立即做第一轮. */
    fun watch(candidates: List<Any?>, options: Options = Options(), root: N? = null, step: String = STEP_WATCH): Guard<N> {
        require(candidates.isNotEmpty()) { "At least one candidate is required" }
        return Guard(this, candidates, options, root, step).also { it.start() }
    }

    /**
     * The background popup guard: on every window change (and once at start) it runs a
     * [dismissAll] pass on the bindings' worker thread, one pass at a time, recording what it
     * closed; errors of a pass go to [onError] (a [Toolkit.fatal] one stops the guard and is
     * rethrown to the worker). [stop] ends the watch; a pass already running completes.
     * zh-CN: 后台弹窗守护: 每次窗口变化时 (以及启动时) 在 bindings 的工作线程上跑一轮 [dismissAll], 同时只跑一轮, 记录关闭了什么; 一轮的错误
     * 交给 [onError] ([Toolkit.fatal] 判定的错误则停止守护并重新抛给工作线程). [stop] 结束观察; 正在进行的一轮会跑完.
     */
    class Guard<N : UiNode<N>> internal constructor(
        private val dismisser: PopupDismisser<N>,
        private val candidates: List<Any?>,
        private val options: Options,
        private val root: N?,
        private val step: String,
    ) : ToolkitBindings.Watch {

        private var watch: ToolkitBindings.Watch? = null

        private val busy = AtomicBoolean()

        private val passCount = AtomicInteger()

        private val record = Collections.synchronizedList(ArrayList<Dismissed<N>>())

        @Volatile
        var isRunning = false
            private set

        /** Passes started so far. zh-CN: 至今开始的轮数. */
        val passes: Int get() = passCount.get()

        /** Everything closed so far, oldest first. zh-CN: 至今关闭的全部弹窗, 最早的在前. */
        val dismissed: List<Dismissed<N>> get() = synchronized(record) { ArrayList(record) }

        val count: Int get() = record.size

        /** Called on the worker thread for every popup closed. zh-CN: 每关闭一个弹窗即在工作线程上调用. */
        @Volatile
        var onDismissed: ((Dismissed<N>) -> Unit)? = null

        /** Called on the worker thread when a pass fails. zh-CN: 一轮失败时在工作线程上调用. */
        @Volatile
        var onError: ((Throwable) -> Unit)? = null

        internal fun start() {
            isRunning = true
            watch = dismisser.kit.bindings.watchWindows(options.debounceMillis) { pass() }.also { it.trigger() }
        }

        override fun stop() {
            isRunning = false
            watch?.stop()
            watch = null
        }

        override fun trigger() {
            watch?.trigger()
        }

        private fun pass() {
            if (!isRunning || !busy.compareAndSet(false, true)) return
            try {
                passCount.incrementAndGet()
                dismisser.dismissAll(candidates, options.copy(locate = Toolkit.Locate.ONCE), root, step).forEach {
                    record += it
                    onDismissed?.invoke(it)
                }
            } catch (e: Throwable) {
                if (dismisser.kit.fatal(e)) {
                    stop()
                    throw e
                }
                onError?.invoke(e)
            } finally {
                busy.set(false)
            }
        }

    }

    companion object {

        const val STEP = "dismissPopups"

        const val STEP_WATCH = "dismissPopups.watch"

    }

}
