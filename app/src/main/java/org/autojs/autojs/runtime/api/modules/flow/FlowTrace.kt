package org.autojs.autojs.runtime.api.modules.flow

import org.autojs.autojs.core.automator.flow.FlowError
import org.autojs.autojs.core.automator.flow.FlowListener
import org.autojs.autojs.core.automator.flow.FlowNode
import org.autojs.autojs.core.automator.flow.FlowStep
import org.autojs.autojs.runtime.api.Console
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.Undefined

/**
 * `flow.trace(true)` (roadmap P3.3): one console line when a step starts and one when it settles,
 * with the step, its target, a short node id (to tell concurrent chains apart), the elapsed time
 * and a brief of the outcome. Formatting is in [FlowTraceFormat], free of Android and Rhino
 * runtime state so it can be unit-tested.
 * zh-CN: `flow.trace(true)` (Roadmap P3.3): 步骤开始与结算时各输出一行控制台日志, 含步骤, 其目标, 短节点 id (区分并发的链), 耗时与结果摘要.
 * 格式化在 [FlowTraceFormat], 不依赖 Android 与 Rhino 运行时状态, 便于单元测试.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
internal class FlowTrace(private val console: Console) : FlowListener {

    override fun onStepStart(node: FlowNode) = console.verbose(FlowTraceFormat.start(node))

    override fun onStepEnd(node: FlowNode, state: FlowNode.State, elapsed: Long) = console.verbose(FlowTraceFormat.end(node, state, elapsed))

}

internal object FlowTraceFormat {

    private const val PREFIX = "[flow]"

    private const val MAX_BRIEF = 80

    fun start(node: FlowNode): String = "$PREFIX > ${label(node)}"

    fun end(node: FlowNode, state: FlowNode.State, elapsed: Long): String = "$PREFIX < ${label(node)} $elapsed ms -> ${outcome(state)}"

    /** `wait content("x") #1a2b`: the step name, its target and the node id. zh-CN: `wait content("x") #1a2b`: 步骤名, 其目标与节点 id. */
    fun label(node: FlowNode): String = buildString {
        append(node.step.name)
        detail(node.step)?.let { append(' ').append(it) }
        append(" #").append(id(node))
    }

    fun outcome(state: FlowNode.State): String = when (state) {
        is FlowNode.State.Pending -> "pending"
        is FlowNode.State.Fulfilled -> "fulfilled: ${brief(state.value)}"
        is FlowNode.State.Rejected -> when (val error = state.error) {
            is FlowError.Cancelled -> "cancelled"
            is FlowError -> "rejected: ${error.code} (${error.message})"
            is JavaScriptException -> "rejected: ${brief(error.value)}"
            else -> "rejected: $error"
        }
    }

    fun brief(value: Any?): String = when (value) {
        null -> "null"
        is Undefined -> "undefined"
        is String -> "\"${truncate(value)}\""
        // JavaScript numbers arrive as Double; an integral one reads better without the ".0".
        // zh-CN: JavaScript 数字到达时为 Double; 整数值不带 ".0" 更易读.
        is Double -> if (value.isFinite() && value == Math.floor(value) && Math.abs(value) < 1e15) value.toLong().toString() else value.toString()
        is FlowNode -> "Flow(${value.step.name})"
        is Throwable -> truncate(value.toString())
        else -> truncate(value.toString())
    }

    private fun detail(step: FlowStep): String? = when (step) {
        is FlowStep.Wait -> describe(step.cond)
        is FlowStep.WaitStable -> describe(step.cond)
        is FlowStep.Sleep -> if (step.min == step.max) "${step.min} ms" else "${step.min}..${step.max} ms"
        is FlowStep.Action -> arguments(step.args)
        is FlowStep.GlobalAction -> arguments(step.args)
        is FlowStep.Timeout -> "${step.millis} ms"
        is FlowStep.Retry -> "x${step.times}" + if (step.delay > 0) " / ${step.delay} ms" else ""
        is FlowStep.Combinator -> "${step.sources.size} source(s)"
        is FlowStep.Tool -> step.detail
        else -> null
    }

    private fun arguments(args: List<Any?>): String? = args.takeIf { it.isNotEmpty() }?.joinToString(", ", "(", ")") { brief(it) }

    private fun describe(cond: Any?): String = (cond as? FlowCondition)?.description ?: brief(cond)

    private fun truncate(s: String): String = s.replace('\n', ' ').let { if (it.length <= MAX_BRIEF) it else it.substring(0, MAX_BRIEF - 3) + "..." }

    private fun id(node: FlowNode): String = Integer.toHexString(System.identityHashCode(node)).takeLast(4)

}
