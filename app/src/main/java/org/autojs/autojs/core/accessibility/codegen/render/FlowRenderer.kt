package org.autojs.autojs.core.accessibility.codegen.render

import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Action
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Mode
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Cardinality
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Family
import org.autojs.autojs.core.accessibility.codegen.plan.PlanReceiver
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorText

/** Flow equivalents, with an explicit timeout and only registered global entry points.
 * zh-CN: Flow 等价输出, 显式指定超时, 只使用已注册的全局入口. */
class FlowRenderer {

    fun render(request: CodeGenRequest<*>, plan: LocatePlan): String {
        RenderSupport.validate(request, plan)
        val self = plan.family == Family.SELF
        val all = plan.cardinality == Cardinality.ALL
        val action = request.action
        val hasAction = action != Action.NONE
        val condition = if (self) SelectorText.render(plan.selector) else "locate"
        val variable = RenderSupport.variable(plan)
        // Compass options apply only to selector conditions, never to function conditions.
        // zh-CN: compass 选项仅适用于选择器条件, 不适用于函数条件.
        val compassReceiver = self && !all && hasAction && plan.receiver is PlanReceiver.Up
        val receiver = if (compassReceiver) PlanReceiver.Self else plan.receiver
        val timeout = when (val mode = request.mode) {
            Mode.WAIT -> "Infinity"
            is Mode.WAIT_TIMEOUT -> mode.millis.toString()
            Mode.CHECK -> "0"
        }
        val options = buildList {
            add("timeout: $timeout")
            if (all) add("resultType: 'widgets'")
            if (compassReceiver) add("compass: 'p${(plan.receiver as PlanReceiver.Up).levels}'")
        }.joinToString(", ", "{ ", " }")

        val code = when {
            request.mode != Mode.WAIT -> {
                val callback = if (hasAction) RenderSupport.callback(action, plan, receiver) else "x => { $variable = x; }"
                (if (hasAction) "" else "let $variable = null;\n") +
                    "flow.whenPresent($condition, $callback, $options).sync();"
            }
            !hasAction -> "let $variable = waitAsync($condition, $options).sync();"
            receiver is PlanReceiver.Up -> "waitAsync($condition, $options).then(${RenderSupport.callback(action, plan, receiver)}).sync();"
            action == Action.CLICK -> "waitThenClick($condition, $options).sync();"
            else -> "waitAsync($condition, $options).${RenderSupport.actionCall(action)}.sync();"
        }
        return (if (self) "" else RenderSupport.locate(plan) + "\n\n") + code
    }
}
