package org.autojs.autojs.core.accessibility.codegen.render

import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Action
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Cardinality
import org.autojs.autojs.core.accessibility.codegen.plan.CompassText
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Family
import org.autojs.autojs.core.accessibility.codegen.plan.PlanReceiver
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorText
import org.autojs.autojs.core.automator.filter.JsLiteral

/** Shared literal rendering and single-probe semantics; no Android or script runtime is needed.
 * zh-CN: 共用的字面量渲染与单次探测语义, 不依赖 Android 或脚本运行时. */
internal object RenderSupport {

    fun validate(request: CodeGenRequest<*>, plan: LocatePlan) {
        require(request.validate().isEmpty()) { "Invalid code-generation request" }
        require((request.scope == CodeGenRequest.Scope.COLLECTION) == (plan.cardinality == Cardinality.ALL)) {
            "Request scope does not match plan cardinality"
        }
        require(request.receiver != CodeGenRequest.Receiver.SELF || plan.receiver == PlanReceiver.Self) {
            "A self-only request cannot use an ancestor receiver"
        }
    }

    fun variable(plan: LocatePlan): String = if (plan.cardinality == Cardinality.ALL) "ws" else "w"

    fun indent(source: String): String = source.lineSequence().joinToString("\n") { "    $it" }

    fun block(prefix: String, body: String, suffix: String = "}"): String = "$prefix {\n${indent(body)}\n$suffix"

    /** Each relation path is one compass call, including negative child indices and sibling offsets.
     * zh-CN: 每条关系路径统一为一次罗盘调用, 包含负子节点序号与兄弟偏移. */
    private fun compass(steps: List<PlanStep>): String = JsLiteral.quote(CompassText.render(steps))

    fun locate(plan: LocatePlan): String {
        val selector = SelectorText.render(plan.selector)
        val body = when (plan.family) {
            Family.SELF -> error("SELF is rendered with the selector API")
            Family.SELF_INDEX -> "return $selector.findOnce(${(plan.steps.single() as PlanStep.Index).index});"
            Family.ANCHOR_PATH -> "let a = $selector.findOnce();\nreturn a && a.compass(${compass(plan.steps)});"
            Family.SELF_RELATIVE -> {
                val relative = SelectorText.render(requireNotNull(plan.relativeMatch))
                // The filter keeps entry matches whose compass neighbour, or its subtree, matches the nested selector.
                // zh-CN: 过滤器保留 "罗盘邻居或其子树匹配嵌套选择器" 的入口匹配项.
                val predicate = "let r = w.compass(${compass(plan.relation)});\nreturn r !== null && r.findOne($relative) !== null;"
                val filtered = "$selector.filter(w => {\n${indent(predicate)}\n})"
                if (plan.cardinality == Cardinality.ONE) "return $filtered.findOnce();"
                else "let ws = $filtered.find();\nreturn ws.nonEmpty() ? ws : null;"
            }
            Family.ANCHOR_SEARCH -> {
                val target = SelectorText.render((plan.steps.single() as PlanStep.Search).selector)
                "let a = $selector.findOnce();\nreturn a && a.findOne($target);"
            }
            Family.CONTAINER_ITEMS -> {
                val target = SelectorText.render((plan.steps.single() as PlanStep.Search).selector)
                // Function conditions consider even an empty collection satisfied; explicitly reject it.
                // zh-CN: 函数条件会把空集合也视为满足, 因此必须显式排除空集合.
                "let a = $selector.findOnce();\nlet ws = a && a.find($target);\nreturn ws && ws.nonEmpty() ? ws : null;"
            }
        }
        return block("function locate()", body)
    }

    fun actionCall(action: Action, each: Boolean = false): String {
        val name = when (action) {
            Action.NONE -> error("No action to render")
            Action.CLICK -> "click"
            Action.LONG_CLICK -> "longClick"
            is Action.SET_TEXT -> "setText"
            Action.SCROLL_FORWARD -> "scrollForward"
            Action.SCROLL_BACKWARD -> "scrollBackward"
        } + if (each) "Each" else ""
        val argument = if (action is Action.SET_TEXT) JsLiteral.quote(action.text) else ""
        return "$name($argument)"
    }

    /** Acts on an already present target; ancestor receivers are checked after traversal.
     * zh-CN: 对已找到的目标执行动作; 向上查找动作接收者后仍需判空. */
    fun actionBody(action: Action, plan: LocatePlan, variable: String = variable(plan), receiver: PlanReceiver = plan.receiver): String = when (receiver) {
        PlanReceiver.Self -> "$variable.${actionCall(action, plan.cardinality == Cardinality.ALL)};"
        is PlanReceiver.Up -> {
            val one = "let r = ${if (plan.cardinality == Cardinality.ALL) "w" else variable}.parent(${receiver.levels});\n" +
                block("if (r)", "r.${actionCall(action)};")
            if (plan.cardinality == Cardinality.ALL) block("$variable.each(w =>", one, "});") else one
        }
    }

    fun callback(action: Action, plan: LocatePlan, receiver: PlanReceiver = plan.receiver): String {
        val variable = variable(plan)
        return when (receiver) {
            PlanReceiver.Self -> "$variable => $variable.${actionCall(action, plan.cardinality == Cardinality.ALL)}"
            is PlanReceiver.Up -> block("$variable =>", actionBody(action, plan, variable, receiver))
        }
    }
}
