package org.autojs.autojs.runtime.api.augment.ai

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.execution.AgentScriptExecutionState
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.util.RhinoUtils.js_json_parse
import org.autojs.autojs.util.RhinoUtils.js_json_stringify

/** Execution-side result/context only. Task control and AgentRun arrive in P5. */
class AiAgent(private val runtime: ScriptRuntime) : Augmentable(runtime) {
    override val selfAssignmentFunctions = listOf(::result.name, ::context.name)
    fun createObject() = createAugmentedObject(runtime.topLevelScope.globalThis, emptyList<Any>())

    @RhinoRuntimeFunctionInterface
    fun result(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLengthInRange(args, 1..1) {
        check(scriptRuntime === runtime)
        val state = AgentScriptExecutionState.fromEngine(runtime.engines.myEngine())
        if (state == null) {
            runtime.console.warn("ai.agent.result: no registered-script context")
            false
        } else {
            val json = js_json_stringify(it[0])
            require(json is String) { "Agent result must be JSON serializable" }
            state.reportResult(json)
        }
    }

    @RhinoRuntimeFunctionInterface
    fun context(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsLengthInRange(args, 0..0) {
        check(scriptRuntime === runtime)
        AgentScriptExecutionState.contextJson(runtime.engines.myEngine())?.let(::js_json_parse)
    }
}
