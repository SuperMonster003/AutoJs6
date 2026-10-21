package org.autojs.autojs.core.automator.toolkit

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.flow.FlowError

/**
 * The click that copes with how apps really build their views (roadmap P4.1): a clickable node
 * is clicked; a node that is not clickable is climbed from to the nearest clickable ancestor
 * (the compass `k` walk, [Options.climb]); when there is none, or the click was refused, a tap
 * gesture at the node's centre (plus [Options.offsetX] / [Options.offsetY] and the humanized
 * offset) takes over ([Options.gestureFallback]); an optional [Options.verify] condition is then
 * polled to confirm the click did what it should. The [Outcome] says which method worked and on
 * which node.
 * zh-CN: 适应应用真实视图结构的点击 (Roadmap P4.1): 可点击的节点直接点击; 不可点击的节点上溯到最近的可点击祖先 (罗盘 `k` 行走,
 * [Options.climb]); 没有祖先或点击被拒绝时, 改为在节点中心 (加 [Options.offsetX] / [Options.offsetY] 与拟人偏移) 做点按手势
 * ([Options.gestureFallback]); 之后可选地轮询 [Options.verify] 条件确认点击生效. [Outcome] 说明哪种方式成功以及作用在哪个节点上.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class SmartClick<N : UiNode<N>> internal constructor(private val kit: Toolkit<N>) {

    data class Options(
        val climb: Boolean = true,
        /** How many ancestors the climb may pass. zh-CN: 上溯至多经过多少级祖先. */
        val maxClimb: Int = DEFAULT_MAX_CLIMB,
        val gestureFallback: Boolean = true,
        /** A condition to poll after the click; `null` skips verification. zh-CN: 点击后轮询的条件; `null` 不校验. */
        val verify: Any? = null,
        val verifyTimeout: Long = DEFAULT_VERIFY_TIMEOUT,
        val verifyInterval: Long = DEFAULT_VERIFY_INTERVAL,
        /** Pixels added to the tap point of the gesture. zh-CN: 手势点按坐标的像素偏移. */
        val offsetX: Int = 0,
        val offsetY: Int = 0,
        val humanize: Humanize = Humanize.NONE,
        /** Always tap this node's bounds, without trying node or ancestor actions. zh-CN: 始终点按本节点边界, 不尝试节点或祖先动作. */
        val boundsOnly: Boolean = false,
    ) {

        init {
            require(maxClimb >= 0) { "maxClimb ($maxClimb) cannot be negative" }
            require(verifyTimeout >= 0) { "verifyTimeout ($verifyTimeout) cannot be negative" }
            require(verifyInterval >= 0) { "verifyInterval ($verifyInterval) cannot be negative" }
        }

        companion object {

            const val DEFAULT_MAX_CLIMB = 5

            const val DEFAULT_VERIFY_TIMEOUT = 2000L

            const val DEFAULT_VERIFY_INTERVAL = 100L

        }

    }

    /** How the click was delivered. zh-CN: 点击的送达方式. */
    enum class Method(val label: String) {

        /** `ACTION_CLICK` on the node itself. zh-CN: 节点自身的 `ACTION_CLICK`. */
        NODE("node"),

        /** `ACTION_CLICK` on a clickable ancestor. zh-CN: 可点击祖先的 `ACTION_CLICK`. */
        ANCESTOR("ancestor"),

        /** A tap gesture at the node's centre. zh-CN: 节点中心的点按手势. */
        GESTURE("gesture"),

    }

    /** [method] used, the [target] it was used on and what [Options.verify] yielded (`null` without verification). zh-CN: 所用的 [method], 作用的 [target], 以及 [Options.verify] 得到的值 (未校验为 `null`). */
    data class Outcome<N>(val method: Method, val target: N, val verified: Any? = null)

    /**
     * Clicks [node] as described above; [selector] is the description of the target for error
     * messages, [root] the scope the verify condition is probed under.
     * zh-CN: 按上述方式点击 [node]; [selector] 为目标在错误信息中的描述, [root] 为校验条件的探测作用域.
     *
     * @throws FlowError `ACTION_FAILED` (reason `click` when a node refused the click, `gesture` when
     * the tap was not dispatched, `verify` when the condition did not hold), `INVALID_TARGET`
     * (reason `notClickable` when nothing is clickable and the gesture is disabled).
     */
    fun click(node: N, options: Options = Options(), step: String = STEP, root: N? = null, selector: String? = null): Outcome<N> {
        kit.pause(options.humanize)
        if (options.boundsOnly) return tap(node, options, step, root, selector)
        var refused = false
        if (node.clickable()) {
            if (kit.actions.click(node)) return verify(Outcome(Method.NODE, node), options, step, root, selector)
            refused = true
        } else if (options.climb) {
            val ancestor = UiNodes.clickableAncestorOrSelf(node, options.maxClimb)
            if (ancestor != null) {
                if (kit.actions.click(ancestor)) return verify(Outcome(Method.ANCESTOR, ancestor), options, step, root, selector)
                refused = true
            }
        }
        if (options.gestureFallback) {
            return tap(node, options, step, root, selector)
        }
        throw when {
            refused -> FlowError.ActionFailed(step, selector, reason = REASON_CLICK)
            else -> FlowError.InvalidTarget(step, selector, REASON_NOT_CLICKABLE)
        }
    }

    private fun tap(node: N, options: Options, step: String, root: N?, selector: String?): Outcome<N> {
        // A cached accessibility rectangle can still describe an entering/moving window.
        if (!kit.bindings.refresh(node)) throw FlowError.InvalidTarget(step, selector, "stale")
        val x = node.centerX() + options.offsetX + options.humanize.offsetX(kit.random)
        val y = node.centerY() + options.offsetY + options.humanize.offsetY(kit.random)
        if (kit.actions.tap(x, y)) return verify(Outcome(Method.GESTURE, node), options, step, root, selector)
        throw FlowError.ActionFailed(step, selector, reason = REASON_GESTURE)
    }

    private fun verify(outcome: Outcome<N>, options: Options, step: String, root: N?, selector: String?): Outcome<N> {
        val cond = options.verify ?: return outcome
        val polled = kit.poll(kit.within(options.verifyTimeout, options.verifyInterval)) { kit.bindings.probe(cond, root) }
        val value = polled.value ?: throw FlowError.ActionFailed(step, selector ?: kit.bindings.describe(cond), polled.elapsed, polled.attempts, REASON_VERIFY)
        return outcome.copy(verified = value)
    }

    companion object {

        const val STEP = "smartClick"

        const val STEP_BOUNDS = "smartClickBounds"

        const val REASON_CLICK = "click"

        const val REASON_GESTURE = "gesture"

        const val REASON_VERIFY = "verify"

        const val REASON_NOT_CLICKABLE = "notClickable"

    }

}
