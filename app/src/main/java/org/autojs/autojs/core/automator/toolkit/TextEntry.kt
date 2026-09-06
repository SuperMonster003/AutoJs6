package org.autojs.autojs.core.automator.toolkit

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.flow.FlowError

/**
 * Typing into a field (roadmap P4.1, `typeInto`): the editable node is located by a selector or
 * node (an editable descendant is used when the match itself is not editable), by index among
 * the editable nodes of the scope (an `Int`, negative from the end), or as the focused editable
 * node (`null`); its text is replaced ([Options.clear]) or appended to; the result is verified by
 * re-reading the node until it shows the expected text ([Options.verify], skipped for password
 * fields, whose text is masked); optionally the IME action is sent ([Options.submit], focusing the
 * node first when needed).
 * zh-CN: 向输入框输入 (Roadmap P4.1, `typeInto`): 以选择器或节点定位可编辑节点 (匹配项本身不可编辑时取其可编辑后代), 或按作用域内可编辑
 * 节点的索引 (`Int`, 负数从末尾计), 或取当前焦点所在的可编辑节点 (`null`); 替换 ([Options.clear]) 或追加文本; 重读节点直到显示预期文本
 * 以校验结果 ([Options.verify], 密码框的文本被掩码, 跳过); 可选地发送 IME 动作 ([Options.submit], 必要时先聚焦).
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class TextEntry<N : UiNode<N>> internal constructor(private val kit: Toolkit<N>) {

    data class Options(
        /** Replace the text (`true`) or append to it (`false`). zh-CN: 替换文本 (`true`) 或追加 (`false`). */
        val clear: Boolean = true,
        /** Send the IME action (`imeEnter`) afterwards. zh-CN: 之后发送 IME 动作 (`imeEnter`). */
        val submit: Boolean = false,
        val verify: Boolean = true,
        val verifyTimeout: Long = DEFAULT_VERIFY_TIMEOUT,
        val verifyInterval: Long = DEFAULT_VERIFY_INTERVAL,
        val locate: Toolkit.Locate = Toolkit.Locate.ONCE,
        val humanize: Humanize = Humanize.NONE,
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

    /** The [node] typed into, the [text] it should now hold and whether the IME action was sent. zh-CN: 输入的 [node], 它现在应有的 [text], 以及是否发送了 IME 动作. */
    data class Outcome<N>(val node: N, val text: String, val submitted: Boolean)

    /**
     * @throws FlowError `TIMEOUT` when a selector target did not show up, `INVALID_TARGET` (reason
     * `noFocus` / `noEditable` / `notEditable`) when no editable node is at hand, `ACTION_FAILED`
     * (reason `setText` / `verify` / `submit`).
     */
    fun typeInto(target: Any?, text: String, options: Options = Options(), root: N? = null, step: String = STEP): Outcome<N> {
        val selector = describe(target)
        val node = editable(target, root, step, options.locate)
        kit.pause(options.humanize)
        val expected = if (options.clear) text else node.text() + text
        if (!kit.actions.setText(node, expected)) throw FlowError.ActionFailed(step, selector, reason = REASON_SET_TEXT)
        if (options.verify && !node.password()) {
            val polled = kit.poll(kit.within(options.verifyTimeout, options.verifyInterval)) {
                if (kit.bindings.refresh(node) && node.text() == expected) true else null
            }
            if (polled.value == null) throw FlowError.ActionFailed(step, selector, polled.elapsed, polled.attempts, REASON_VERIFY)
        }
        var submitted = false
        if (options.submit) {
            if (!node.focused()) kit.actions.focus(node)
            if (!kit.actions.imeEnter(node)) throw FlowError.ActionFailed(step, selector, reason = REASON_SUBMIT)
            submitted = true
        }
        return Outcome(node, expected, submitted)
    }

    private fun describe(target: Any?): String? = when (target) {
        null -> DESCRIPTION_FOCUSED
        is Int -> "editable[$target]"
        else -> kit.bindings.describe(target)
    }

    private fun editable(target: Any?, root: N?, step: String, locate: Toolkit.Locate): N = when (target) {
        null -> scope(root)?.let { UiNodes.firstDescendant(it) { n -> n.focused() && n.editable() } }
            ?: throw FlowError.InvalidTarget(step, DESCRIPTION_FOCUSED, REASON_NO_FOCUS)
        is Int -> {
            val all = scope(root)?.let { UiNodes.descendants(it) { n -> n.editable() } }.orEmpty()
            all.getOrNull(if (target < 0) target + all.size else target)
                ?: throw FlowError.InvalidTarget(step, describe(target), REASON_NO_EDITABLE)
        }
        else -> {
            val node = kit.locate(target, root, step, locate)
            when {
                node.editable() -> node
                else -> UiNodes.firstDescendant(node, includeSelf = false) { it.editable() }
                    ?: throw FlowError.InvalidTarget(step, describe(target), REASON_NOT_EDITABLE)
            }
        }
    }

    private fun scope(root: N?): N? = root ?: kit.bindings.activeRoot()

    companion object {

        const val STEP = "typeInto"

        const val DESCRIPTION_FOCUSED = "focused"

        const val REASON_NO_FOCUS = "noFocus"

        const val REASON_NO_EDITABLE = "noEditable"

        const val REASON_NOT_EDITABLE = "notEditable"

        const val REASON_SET_TEXT = "setText"

        const val REASON_VERIFY = "verify"

        const val REASON_SUBMIT = "submit"

    }

}
