package org.autojs.autojs.core.accessibility.codegen.render

import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Action
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Mode
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Cardinality
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Family
import org.autojs.autojs.core.accessibility.codegen.plan.PlanReceiver
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorText

/** Classic public selector/wait APIs for a previously verified plan.
 * zh-CN: 将已验证的计划渲染为经典风格的公开选择器与 wait API. */
class ClassicRenderer {

    fun render(request: CodeGenRequest<*>, plan: LocatePlan): String {
        RenderSupport.validate(request, plan)
        return if (plan.family == Family.SELF) renderSelf(request, plan) else renderLocate(request, plan)
    }

    private fun renderSelf(request: CodeGenRequest<*>, plan: LocatePlan): String {
        val selector = SelectorText.render(plan.selector)
        val all = plan.cardinality == Cardinality.ALL
        val variable = RenderSupport.variable(plan)
        val action = request.action
        val hasAction = action != Action.NONE
        return when (val mode = request.mode) {
            Mode.WAIT -> {
                val query = "$selector.${if (all) "untilFind" else "untilFindOne"}()"
                when {
                    !hasAction -> "let $variable = $query;"
                    plan.receiver == PlanReceiver.Self -> "$query.${RenderSupport.actionCall(action, all)};"
                    else -> "let $variable = $query;\n${RenderSupport.actionBody(action, plan)}"
                }
            }
            is Mode.WAIT_TIMEOUT -> {
                val query = "$selector.findOne(${mode.millis})"
                when {
                    !hasAction && all -> "let ws = $query ? $selector.find() : null;"
                    !hasAction -> "let w = $query;"
                    all -> RenderSupport.block("if ($query)", when (plan.receiver) {
                        PlanReceiver.Self -> "$selector.find().${RenderSupport.actionCall(action, each = true)};"
                        is PlanReceiver.Up -> "let ws = $selector.find();\n${RenderSupport.actionBody(action, plan)}"
                    })
                    else -> "let w = $query;\n" + RenderSupport.block("if (w)", RenderSupport.actionBody(action, plan))
                }
            }
            Mode.CHECK -> when {
                !hasAction && all -> "let ws = $selector.find();"
                !hasAction -> "let found = $selector.exists();"
                else -> {
                    val query = "$selector.${if (all) "find" else "findOnce"}()"
                    val condition = if (all) "ws.nonEmpty()" else "w"
                    "let $variable = $query;\n" + RenderSupport.block("if ($condition)", RenderSupport.actionBody(action, plan))
                }
            }
        }
    }

    private fun renderLocate(request: CodeGenRequest<*>, plan: LocatePlan): String {
        val variable = RenderSupport.variable(plan)
        val action = request.action
        val hasAction = action != Action.NONE
        val use = when (val mode = request.mode) {
            Mode.CHECK -> "let $variable = locate();" + if (hasAction) {
                "\n" + RenderSupport.block("if ($variable)", RenderSupport.actionBody(action, plan))
            } else ""
            else -> {
                val timeout = when (mode) {
                    Mode.WAIT -> "Infinity"
                    is Mode.WAIT_TIMEOUT -> mode.millis.toString()
                    else -> error("Unexpected mode")
                }
                val callback = if (hasAction) RenderSupport.callback(action, plan) else "$variable => $variable"
                // A timed-out global wait returns false. A null-valued else callback does not replace it.
                // zh-CN: 全局 wait 超时返回 false; 返回 null 的 else 回调不会覆盖此结果.
                (if (hasAction) "" else "let $variable = ") + "wait(locate, $timeout, 200, { then: $callback });"
            }
        }
        return RenderSupport.locate(plan) + "\n\n" + use
    }
}
