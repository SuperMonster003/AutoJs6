package org.autojs.autojs.core.automator.toolkit

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.flow.FlowError

/**
 * "Scroll until it shows up" (roadmap P4.1, `scrollUntil`): looks for the target, scrolls the
 * container (given, or the largest scrollable node of the scope) one step towards
 * [Options.direction] when it is absent, and repeats until the target is found, [Options.maxSteps]
 * scrolls have been made, or the end has been reached (see [Scroller]; ignored when
 * [Options.stopAtEnd] is off). After the last scroll the target is looked for once more.
 * zh-CN: "滚动直到出现" (Roadmap P4.1, `scrollUntil`): 查找目标, 不存在时把容器 (指定的, 或作用域内面积最大的可滚动节点) 向
 * [Options.direction] 滚动一步, 重复直到找到目标, 已滚动 [Options.maxSteps] 次, 或已到底 (见 [Scroller]; [Options.stopAtEnd] 关闭时忽略).
 * 最后一次滚动后再查找一次.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class ScrollSearch<N : UiNode<N>> internal constructor(private val kit: Toolkit<N>) {

    data class Options(
        /** The scroll container (a selector or a node) or `null` to detect it. zh-CN: 滚动容器 (选择器或节点), `null` 为自动探测. */
        val container: Any? = null,
        val direction: ScrollDirection = ScrollDirection.DOWN,
        val maxSteps: Int = DEFAULT_MAX_STEPS,
        val method: ScrollMethod = ScrollMethod.ACTION,
        val stopAtEnd: Boolean = true,
        /** The pause after every scroll, for the content to settle. zh-CN: 每次滚动后的停顿, 等内容稳定. */
        val settleMillis: Long = DEFAULT_SETTLE_MILLIS,
        val gestureDuration: Long = DEFAULT_GESTURE_DURATION,
        /** How long to wait for a given container. zh-CN: 等待指定容器出现的计划. */
        val locate: Toolkit.Locate = Toolkit.Locate.ONCE,
        val humanize: Humanize = Humanize.NONE,
    ) {

        init {
            require(maxSteps >= 0) { "maxSteps ($maxSteps) cannot be negative" }
            require(settleMillis >= 0) { "settleMillis ($settleMillis) cannot be negative" }
            require(gestureDuration > 0) { "gestureDuration ($gestureDuration) must be positive" }
        }

        companion object {

            const val DEFAULT_MAX_STEPS = 20

            const val DEFAULT_SETTLE_MILLIS = 300L

            const val DEFAULT_GESTURE_DURATION = 400L

        }

    }

    /** The [node] found, after [steps] scrolls of [container]. zh-CN: 找到的 [node], 经过对 [container] 的 [steps] 次滚动. */
    data class Outcome<N>(val node: N, val steps: Int, val container: N?)

    /**
     * @throws FlowError `TIMEOUT` (reason `maxSteps` or `end`) when the target never showed up,
     * `INVALID_TARGET` for a missing container (reason `container`) or none to detect (reason
     * `noScrollable`, action method only), and what [Scroller.scroll] throws.
     */
    fun scrollUntil(target: Any?, options: Options = Options(), root: N? = null, step: String = STEP): Outcome<N> {
        val start = kit.clock()
        val selector = kit.bindings.describe(target)
        val scroller = kit.scroller(options.container, root, options.direction, options.method, options.settleMillis, options.gestureDuration, options.humanize, options.stopAtEnd, step, options.locate)
        var attempts = 0L
        while (true) {
            attempts += 1
            kit.bindings.find(target, root)?.let { return Outcome(it, scroller.steps, scroller.container) }
            if (scroller.steps >= options.maxSteps) {
                throw FlowError.Timeout(step, selector, kit.clock() - start, attempts, REASON_MAX_STEPS)
            }
            if (!scroller.scroll() && options.stopAtEnd) {
                attempts += 1
                kit.bindings.find(target, root)?.let { return Outcome(it, scroller.steps, scroller.container) }
                throw FlowError.Timeout(step, selector, kit.clock() - start, attempts, REASON_END)
            }
        }
    }

    companion object {

        const val STEP = "scrollUntil"

        const val REASON_MAX_STEPS = "maxSteps"

        const val REASON_END = "end"

    }

}
