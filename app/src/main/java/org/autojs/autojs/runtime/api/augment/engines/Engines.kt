package org.autojs.autojs.runtime.api.augment.engines

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.engine.JavaScriptEngine
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.extension.AnyExtensions.toRuntimePath
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.extension.ScriptableObjectExtensions.acquire
import org.autojs.autojs.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject

@Suppress("unused", "UNUSED_PARAMETER")
class Engines(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val selfAssignmentFunctions = listOf(
        ::all.name,
        ::myEngine.name,
        ::getEngines.name,
        ::stopAll.name,
        ::stopAllAndToast.name,
        ::execScript.name,
        ::execScriptFile.name,
        ::execAutoFile.name,
    )

    init {
        setEngineExecArgv()
    }

    private fun setEngineExecArgv() {
        when (val tag = scriptRuntime.engines.myEngine().getTag(ExecutionConfig.tag)) {
            is ExecutionConfig -> {
                val execArgv = newNativeObject()
                val iterator = tag.arguments.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    put(execArgv, entry.key to entry.value)
                }
                scriptRuntime.engines.myEngine().setExecArgv(execArgv)
            }
        }
    }

    companion object : FlexibleArray() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun all(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsIsEmpty(args) {
            scriptRuntime.engines.all()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun myEngine(scriptRuntime: ScriptRuntime, args: Array<out Any?>): JavaScriptEngine = ensureArgumentsIsEmpty(args) {
            scriptRuntime.engines.myEngine()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getEngines(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Set<ScriptEngine<out ScriptSource>> = ensureArgumentsIsEmpty(args) {
            scriptRuntime.engines.engines
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun stopAll(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Int = ensureArgumentsIsEmpty(args) {
            scriptRuntime.engines.stopAll()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun stopAllAndToast(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Int = ensureArgumentsIsEmpty(args) {
            scriptRuntime.engines.stopAllAndToast()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun execScript(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ScriptExecution = ensureArgumentsLengthInRange(args, 2..3) {
            val (name, script, config) = it
            scriptRuntime.engines.execScript(coerceString(name), coerceString(script), fillConfig(scriptRuntime, config))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun execScriptFile(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ScriptExecution = ensureArgumentsLengthInRange(args, 1..2) {
            val (path, config) = it
            scriptRuntime.engines.execScriptFile(coerceString(path), fillConfig(scriptRuntime, config))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun execAutoFile(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ScriptExecution = ensureArgumentsLengthInRange(args, 1..2) {
            val (path, config) = it
            scriptRuntime.engines.execAutoFile(coerceString(path), fillConfig(scriptRuntime, config))
        }

        private fun fillConfig(scriptRuntime: ScriptRuntime, config: Any?): ExecutionConfig {
            val c = ExecutionConfig()
            when {
                config.isJsNullish() -> Unit
                config is ExecutionConfig -> {
                    c.workingDirectory = config.workingDirectory
                    c.delay = config.delay
                    c.interval = config.interval
                    c.loopTimes = config.loopTimes
                    config.arguments.entries.forEach { c.setArgument(it.key, it.value) }
                }
                config is ScriptableObject -> {
                    c.workingDirectory = config.acquire("path") { it.toRuntimePath(scriptRuntime) }
                    c.delay = config.inquire("delay", ::coerceLongNumber, 0L)
                    c.interval = config.inquire("interval", ::coerceLongNumber, 0L)
                    c.loopTimes = config.inquire("loopTimes", ::coerceIntNumber, 1)
                    config.inquire("arguments") {
                        require(it is NativeObject) { "Property \"arguments\" of config for Engines#fillConfig must be a JavaScript Object" }
                        it.entries.forEach { (key, value) -> c.setArgument(coerceString(key), value) }
                    }
                }
                else -> throw WrappedIllegalArgumentException("Argument config ${config.jsBrief()} for Engines#fillConfig is invalid")
            }
            return c
        }

    }

}
