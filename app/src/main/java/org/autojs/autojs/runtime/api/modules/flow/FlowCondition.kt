package org.autojs.autojs.runtime.api.modules.flow

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.flow.FlowNode
import org.autojs.autojs.core.automator.flow.FlowScope
import org.autojs.autojs.core.automator.pick.PickArguments
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.regexp.NativeRegExp
import java.math.BigInteger

/**
 * A wait condition of the Flow API (roadmap Appendix A.2), normalised once on the script thread
 * when the step is created and probed repeatedly on the worker by [RuntimeFlowBindings]: a
 * selector in any `pickup` form, a JavaScript predicate, a Kotlin predicate, a node re-validated
 * by `refresh()`, or a flow / promise whose settlement is awaited.
 * zh-CN: Flow API 的等待条件 (Roadmap 附录 A.2), 在步骤创建时于脚本线程归一化一次, 由 [RuntimeFlowBindings] 在工作线程反复探测:
 * `pickup` 任一形式的选择器, JavaScript 断言函数, Kotlin 断言函数, 以 `refresh()` 重新校验的节点, 或等待其结算的 Flow / Promise.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
internal sealed class FlowCondition {

    /** What `FlowError.selector` shows for this condition. zh-CN: 此条件在 `FlowError.selector` 中的呈现. */
    abstract val description: String

    /**
     * A `Pickup.Selector` (string, regex, `UiSelector`, map, list, number) searched with
     * `UiSelector.pickup`; [scope] carries the `root` / `compass` / `resultType` options given to
     * this wait alone, laid over the chain's scope.
     * zh-CN: 以 `UiSelector.pickup` 搜索的 `Pickup.Selector` (字符串, 正则, `UiSelector`, 集合, 列表, 数字); [scope] 为只给这一次
     * 等待的 `root` / `compass` / `resultType` 选项, 覆盖在链的作用域之上.
     */
    class Selector(val selector: Any, val scope: FlowScope, override val description: String) : FlowCondition()

    /** A JavaScript function polled until its result passes the `wait` truthiness rule. zh-CN: 轮询直到结果通过 `wait` 真值规则的 JavaScript 函数. */
    class Predicate(val fn: Callable) : FlowCondition() {
        override val description get() = "function"
    }

    /** A Kotlin predicate (`waitForActivity`, `waitForPackage`). zh-CN: Kotlin 断言函数 (`waitForActivity`, `waitForPackage`). */
    class Native(override val description: String, val fn: () -> Any?) : FlowCondition()

    /** A node that counts as found while `refresh()` succeeds. zh-CN: `refresh()` 成功即算找到的节点. */
    class Node(val node: UiObject) : FlowCondition() {
        override val description get() = "UiObject"
    }

    /**
     * A flow or promise: found once fulfilled (an `undefined` value stands in for `null`), failed
     * with its error once rejected. [state] is read on the worker.
     * zh-CN: Flow 或 Promise: 兑现即算找到 (值为 `null` 时以 `undefined` 代替), 拒绝则以其错误失败. [state] 在工作线程读取.
     */
    class Settling(override val description: String, val state: () -> FlowNode.State?) : FlowCondition()

    companion object {

        /** Whether [value] is a selector form `UiSelector.pickup` accepts; see [PickArguments.isSelectorForm]. zh-CN: [value] 是否为 `UiSelector.pickup` 接受的选择器形式; 见 [PickArguments.isSelectorForm]. */
        fun isSelectorForm(value: Any?): Boolean = PickArguments.isSelectorForm(value)

        /** A short rendering of a selector form for error messages. zh-CN: 选择器形式用于错误信息的简短呈现. */
        fun describeSelector(selector: Any): String = when (selector) {
            is String -> "content(\"$selector\")"
            is NativeRegExp -> "contentMatch(${Context.toString(selector)})"
            is UiSelector -> selector.toString()
            is BigInteger, is Number -> "content(\"${if (selector is Double) selector.toInt() else selector}\")"
            is Map<*, *> -> selector.entries.joinToString(", ", "{", "}") { (k, v) -> "$k: ${render(v)}" }
            is List<*> -> selector.joinToString(", ", "[", "]") { render(it) }
            else -> selector.toString()
        }

        private fun render(value: Any?): String = when (value) {
            is String -> "\"$value\""
            is Map<*, *>, is List<*>, is UiSelector, is NativeRegExp -> describeSelector(value)
            null -> "null"
            else -> Context.toString(value)
        }

    }

}
