package org.autojs.autojs.core.automator.toolkit

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.flow.FlowError

/**
 * Setting a checkable control (roadmap P4.1, `toggle`): the target (or its first checkable
 * descendant, for a settings row wrapping a switch) is left alone when it already has the wanted
 * state, smart-clicked otherwise, and re-read until it shows the wanted state.
 * zh-CN: 设置可勾选控件 (Roadmap P4.1, `toggle`): 目标 (或其首个可勾选后代, 如包着开关的设置行) 已是期望状态时不动, 否则智能点击, 并重读
 * 直到显示期望状态.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class Toggle<N : UiNode<N>> internal constructor(private val kit: Toolkit<N>) {

    data class Options(
        val locate: Toolkit.Locate = Toolkit.Locate.ONCE,
        val verifyTimeout: Long = DEFAULT_VERIFY_TIMEOUT,
        val verifyInterval: Long = DEFAULT_VERIFY_INTERVAL,
        val click: SmartClick.Options = SmartClick.Options(),
    ) {

        init {
            require(verifyTimeout >= 0) { "verifyTimeout ($verifyTimeout) cannot be negative" }
            require(verifyInterval >= 0) { "verifyInterval ($verifyInterval) cannot be negative" }
        }

        companion object {

            const val DEFAULT_VERIFY_TIMEOUT = 1000L

            const val DEFAULT_VERIFY_INTERVAL = 100L

        }

    }

    /** The checkable [node], whether it had to be clicked ([changed], with the [click] made) and its state now. zh-CN: 可勾选的 [node], 是否需要点击 ([changed], 附所做的 [click]) 及其现在的状态. */
    data class Outcome<N>(val node: N, val changed: Boolean, val checked: Boolean, val click: SmartClick.Outcome<N>? = null)

    /**
     * @throws FlowError `TIMEOUT` when [target] did not show up, `INVALID_TARGET` (reason
     * `notCheckable`), `ACTION_FAILED` (the click's reasons, or `verify` when the state did not
     * change in time).
     */
    fun set(target: Any?, checked: Boolean, options: Options = Options(), root: N? = null, step: String = STEP): Outcome<N> {
        val selector = kit.bindings.describe(target)
        val located = kit.locate(target, root, step, options.locate)
        val node = when {
            located.checkable() -> located
            else -> UiNodes.firstDescendant(located, includeSelf = false) { it.checkable() }
                ?: throw FlowError.InvalidTarget(step, selector, REASON_NOT_CHECKABLE)
        }
        if (node.checked() == checked) return Outcome(node, false, checked)
        val click = kit.smartClick.click(node, options.click, step, root, selector)
        val polled = kit.poll(kit.within(options.verifyTimeout, options.verifyInterval)) {
            if (kit.bindings.refresh(node) && node.checked() == checked) true else null
        }
        if (polled.value == null) throw FlowError.ActionFailed(step, selector, polled.elapsed, polled.attempts, REASON_VERIFY)
        return Outcome(node, true, checked, click)
    }

    companion object {

        const val STEP = "toggle"

        const val REASON_NOT_CHECKABLE = "notCheckable"

        const val REASON_VERIFY = "verify"

    }

}
