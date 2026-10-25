package org.autojs.autojs.runtime.api.modules.ai

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.execution.AgentScriptExecutionState
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.ai.AiAgentArguments
import org.autojs.autojs.runtime.api.ai.AiAgentService
import org.autojs.autojs.runtime.api.modules.toJsPromise
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.util.RhinoUtils.js_json_parse
import org.autojs.autojs.util.RhinoUtils.js_json_stringify

/** Script task controls and the registered-script result channel share the existing ai object. */
class AiAgent internal constructor(private val runtime: ScriptRuntime, serviceFactory: () -> AiAgentService = { AiAgentService(runtime) }) : Augmentable(runtime) {
    private val service by lazy(serviceFactory)
    override val selfAssignmentFunctions = listOf(::run.name, ::create.name, ::get.name, ::list.name,
        ::catalog.name, ::presets.name, ::status.name, ::result.name, ::context.name)
    fun createObject() = createAugmentedObject(runtime.topLevelScope.globalThis, emptyList<Any>())

    @RhinoRuntimeFunctionInterface
    fun run(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 1..2) {
        check(scriptRuntime === runtime)
        service.run(goal(it[0]), options(it.getOrNull(1)))
    }

    @RhinoRuntimeFunctionInterface
    fun create(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 1..1) {
        check(scriptRuntime === runtime)
        val base = options(it[0])
        NativeObject().apply {
            RhinoUtils.initNativeObjectPrototype(runtime.topLevelScope, this)
            defineProperty("options", { js_json_parse(base.toString()) }, null, ScriptableObject.READONLY or ScriptableObject.PERMANENT)
            defineProperty("run", RhinoUtils.newBaseFunction(runtime.topLevelScope, "run", { values ->
                AiAgentJsErrors.guard {
                    require(values.size in 1..2)
                    service.run(goal(values[0]), AiAgentArguments.merge(base, options(values.getOrNull(1))))
                }
            }, RhinoUtils.NOT_CONSTRUCTABLE), ScriptableObject.READONLY or ScriptableObject.PERMANENT)
        }
    }

    @RhinoRuntimeFunctionInterface
    fun get(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsLengthInRange(args, 1..1) {
        check(scriptRuntime === runtime); require(it[0] is CharSequence)
        AiAgentJsErrors.guard { service.get(it[0].toString()) }
    }

    @RhinoRuntimeFunctionInterface
    fun list(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 0..1) {
        check(scriptRuntime === runtime)
        service.list(objectArgument(it.getOrNull(0), setOf("state", "preset", "since", "until", "limit"))).toJsPromise(runtime)
    }

    @RhinoRuntimeFunctionInterface
    fun catalog(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 0..1) {
        check(scriptRuntime === runtime)
        val query = it.getOrNull(0).takeUnless { value -> value.isJsNullish() }
        require(query == null || query is CharSequence)
        service.catalog(query?.toString()).toJsPromise(runtime)
    }

    @RhinoRuntimeFunctionInterface
    fun presets(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 0..0) {
        check(scriptRuntime === runtime); service.presets().toJsPromise(runtime)
    }

    @RhinoRuntimeFunctionInterface
    fun status(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsLengthInRange(args, 0..0) {
        check(scriptRuntime === runtime); js_json_parse(service.status().toString())
    }

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

    companion object {
        private fun goal(value: Any?): String {
            require(value is CharSequence) { "Agent goal must be a string" }
            return AiAgentArguments.goal(value.toString())
        }
        internal fun options(value: Any?) = AiAgentArguments.options(objectArgument(value, AiAgentArguments.optionKeys))
        private fun objectArgument(value: Any?, keys: Set<String>): JsonObject {
            if (value.isJsNullish()) return JsonObject()
            require(value is NativeObject) { "Agent options must be an object" }
            // Check the original keys before JSON.stringify can omit undefined/function values.
            require(value.ids.all { it is String && it in keys }) { "Unknown Agent option" }
            for (id in value.ids) require(!value.get(id.toString(), value).isJsNullish()) { "Agent option cannot be null or undefined" }
            val json = js_json_stringify(value) as? String ?: throw IllegalArgumentException("Agent options must be JSON")
            val parsed = JsonParser.parseString(json).asJsonObject
            require(parsed.keySet() == value.ids.map { it.toString() }.toSet()) { "Agent options must be JSON" }
            return parsed
        }
    }
}
