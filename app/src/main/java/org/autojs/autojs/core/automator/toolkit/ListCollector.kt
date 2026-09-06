package org.autojs.autojs.core.automator.toolkit

import org.autojs.autojs.core.automator.UiNode

/**
 * Harvesting a scrolling list (roadmap P4.1, `collectList`): every match of the item selector
 * inside the container is rendered per [Options.resultType] (the `pickup` result types; `"$"`,
 * the content, by default), deduplicated by [NodeFingerprints.content] ([Options.dedupe]), and
 * appended; then the container is scrolled one step (see [Scroller]) and the harvest repeats until
 * the end is reached, [Options.maxSteps] scrolls were made, [Options.maxItems] items were taken, or
 * [Options.until] accepts an item.
 * zh-CN: 采集滚动列表 (Roadmap P4.1, `collectList`): 容器内条目选择器的每个匹配项按 [Options.resultType] (`pickup` 的结果类型; 默认 `"$"`
 * 即内容) 呈现, 按 [NodeFingerprints.content] 去重 ([Options.dedupe]) 后加入结果; 然后把容器滚动一步 (见 [Scroller]) 并重复采集, 直到
 * 到底, 已滚动 [Options.maxSteps] 次, 已取 [Options.maxItems] 项, 或 [Options.until] 接受了某一项.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class ListCollector<N : UiNode<N>> internal constructor(private val kit: Toolkit<N>) {

    data class Options(
        val direction: ScrollDirection = ScrollDirection.DOWN,
        val maxSteps: Int = DEFAULT_MAX_STEPS,
        val method: ScrollMethod = ScrollMethod.ACTION,
        val resultType: Any? = DEFAULT_RESULT_TYPE,
        val dedupe: Boolean = true,
        /** Stops the collection once it returns `true` for an item just taken. zh-CN: 对刚采集的一项返回 `true` 即停止采集. */
        val until: ((Any?) -> Boolean)? = null,
        val maxItems: Int = Int.MAX_VALUE,
        val settleMillis: Long = DEFAULT_SETTLE_MILLIS,
        val gestureDuration: Long = DEFAULT_GESTURE_DURATION,
        /** How long to wait for a given container. zh-CN: 等待指定容器出现的计划. */
        val locate: Toolkit.Locate = Toolkit.Locate.ONCE,
        val humanize: Humanize = Humanize.NONE,
    ) {

        init {
            require(maxSteps >= 0) { "maxSteps ($maxSteps) cannot be negative" }
            require(maxItems > 0) { "maxItems ($maxItems) must be positive" }
            require(settleMillis >= 0) { "settleMillis ($settleMillis) cannot be negative" }
            require(gestureDuration > 0) { "gestureDuration ($gestureDuration) must be positive" }
        }

        companion object {

            const val DEFAULT_MAX_STEPS = 20

            const val DEFAULT_RESULT_TYPE = "$"

            const val DEFAULT_SETTLE_MILLIS = 300L

            const val DEFAULT_GESTURE_DURATION = 400L

        }

    }

    /** Why a collection ended. zh-CN: 采集结束的原因. */
    enum class Stop { END, MAX_STEPS, MAX_ITEMS, UNTIL }

    /** The [items] collected in order, the scrolls made and why it stopped. zh-CN: 按顺序采集的 [items], 滚动次数与停止原因. */
    data class Outcome(val items: List<Any?>, val steps: Int, val stop: Stop) {

        val reachedEnd: Boolean get() = stop == Stop.END

    }

    /**
     * Collects the matches of [item] inside [container] (a selector / node, or `null` for the
     * largest scrollable node of [root] / the active window).
     * zh-CN: 采集 [container] (选择器 / 节点, 或 `null` 取 [root] / 活动窗口中面积最大的可滚动节点) 内 [item] 的匹配项.
     *
     * @throws FlowError `INVALID_TARGET` for a missing container (reason `container`) or none to
     * detect (reason `noScrollable`, action method only), and what [Scroller.scroll] throws.
     */
    fun collect(container: Any?, item: Any?, options: Options = Options(), root: N? = null, step: String = STEP): Outcome {
        val scroller = kit.scroller(container, root, options.direction, options.method, options.settleMillis, options.gestureDuration, options.humanize, true, step, options.locate)
        val scope = scroller.container ?: root
        val items = ArrayList<Any?>()
        val seen = HashSet<Int>()
        while (true) {
            for (node in kit.bindings.findAll(item, scope)) {
                if (options.dedupe && !seen.add(NodeFingerprints.content(node))) continue
                val value = kit.bindings.pick(node, options.resultType)
                items += value
                if (options.until?.invoke(value) == true) return Outcome(items, scroller.steps, Stop.UNTIL)
                if (items.size >= options.maxItems) return Outcome(items, scroller.steps, Stop.MAX_ITEMS)
            }
            if (scroller.steps >= options.maxSteps) return Outcome(items, scroller.steps, Stop.MAX_STEPS)
            if (!scroller.scroll()) return Outcome(items, scroller.steps, Stop.END)
        }
    }

    companion object {

        const val STEP = "collectList"

    }

}
