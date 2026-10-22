package org.autojs.autojs.runtime.api.modules.engines

import org.autojs.autojs.engine.JavaScriptEngine
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInstall
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptModuleInstallation
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Wrapper
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The `engines` module on the new script module layer (roadmap P3.4): an event emitter (`emitter = true`, the methods of
 * `events.emitter()` at the end of the prototype chain, as the current `AsEmitter`) with the functions of the current
 * module. Installing it sets the current engine's `execArgv` from its execution config, as the current module did when it
 * was constructed; this is a side effect of the install hook and builds nothing, so the module object is built on first
 * use (or when the host emits an engine event to this runtime, `Engines.emit`).
 *
 * `execScript` / `execScriptFile` / `execAutoFile` take the documented config object (or an `ExecutionConfig`, which the
 * current module meant to accept). Arguments follow the real signatures (T4): a config that is neither is a TypeError, a
 * number field without a number a TypeError and NaN or an infinite one a RangeError, `arguments` that is not an object a
 * TypeError (D10; the current module threw IllegalArgumentException).
 *
 * zh-CN: 新脚本模块层上的 engines 模块 (P3.4): 事件发射器 (emitter = true, events.emitter() 的方法位于原型链末尾, 同现有 AsEmitter), 函数同现有模块.
 * 安装时按执行配置设置当前引擎的 execArgv, 与现有模块构造时相同; 这是安装钩子的副作用, 不构建模块, 模块对象在首次使用时 (或宿主向本运行时发送引擎事件时,
 * Engines.emit) 构建. execScript / execScriptFile / execAutoFile 接受文档所述的配置对象 (或 ExecutionConfig, 现有模块本意如此). 参数按真实签名 (T4):
 * 两者都不是时为 TypeError, 数值字段不是数值为 TypeError, NaN 或无穷为 RangeError, arguments 不是对象为 TypeError (D10; 现有模块抛 IllegalArgumentException).
 */
@ScriptModule(name = "engines", emitter = true)
class EnginesModule(private val runtime: ScriptRuntime) {

    @ScriptInstall
    fun install(@Suppress("UNUSED_PARAMETER") installation: ScriptModuleInstallation) {
        val engine = runtime.engines.myEngine()
        val config = engine.getTag(ExecutionConfig.tag) as? ExecutionConfig ?: return
        val scope = runtime.topLevelScope
        val execArgv = RhinoUtils.newNativeObject(scope)
        // Maps and lists (the mail trigger's event document) become plain objects and arrays; other values keep their
        // Java identity, as with the current module.
        config.arguments.entries.forEach { (key, value) -> execArgv.put(key, execArgv, RhinoUtils.toJsValue(scope, value)) }
        engine.setExecArgv(execArgv)
    }

    @ScriptFunction
    fun all(): NativeArray = runtime.engines.all()

    @ScriptFunction
    fun myEngine(): JavaScriptEngine = runtime.engines.myEngine()

    @ScriptFunction
    fun getEngines(): Set<ScriptEngine<out ScriptSource>> = runtime.engines.engines

    @ScriptFunction
    fun stopAll(): Int = runtime.engines.stopAll()

    @ScriptFunction
    fun stopAllAndToast(): Int = runtime.engines.stopAllAndToast()

    @ScriptFunction
    fun execScript(name: String, script: String, config: Any? = null): ScriptExecution =
        runtime.engines.execScript(name, script, fillConfig("engines.execScript", config))

    @ScriptFunction
    fun execScriptFile(path: String, config: Any? = null): ScriptExecution =
        runtime.engines.execScriptFile(path, fillConfig("engines.execScriptFile", config))

    @ScriptFunction
    fun execAutoFile(path: String, config: Any? = null): ScriptExecution =
        runtime.engines.execAutoFile(path, fillConfig("engines.execAutoFile", config))

    /**
     * The execution config of [o], with the current module's defaults and rules (`path` before `workingDirectory`); the
     * working directory is resolved last, after the other fields are checked.
     */
    private fun fillConfig(function: String, o: Any?): ExecutionConfig {
        val result = ExecutionConfig()
        val cwd by lazy { runtime.files.cwd() ?: "" }
        when (val config = if (o is Wrapper) o.unwrap() else o) {
            is ExecutionConfig -> {
                result.delay = config.delay.takeUnless { it < 0 } ?: 0L
                result.interval = config.interval.takeUnless { it < 0 } ?: 0L
                result.loopTimes = config.loopTimes.takeUnless { it < 0 } ?: 1
                config.arguments.entries.forEach { result.setArgument(it.key, it.value) }
                result.workingDirectory = config.workingDirectory.takeUnless { it.isBlank() } ?: cwd
            }
            is NativeObject -> {
                result.delay = field(config, "delay").let { if (isNullish(it)) 0L else GlobalValues.long(function, "config.delay", it) }
                result.interval = field(config, "interval").let { if (isNullish(it)) 0L else GlobalValues.long(function, "config.interval", it) }
                result.loopTimes = field(config, "loopTimes").let { if (isNullish(it)) 1 else GlobalValues.int(function, "config.loopTimes", it) }
                when (val arguments = field(config, "arguments")) {
                    is NativeObject -> arguments.ids.forEach { id ->
                        val value = if (id is Int) ScriptableObject.getProperty(arguments, id) else ScriptableObject.getProperty(arguments, id.toString())
                        result.setArgument(id.toString(), value)
                    }
                    else -> if (!isNullish(arguments)) throw GlobalValues.typeError(function, "config.arguments", "Object", arguments)
                }
                val path = field(config, "path").takeUnless { isNullish(it) } ?: field(config, "workingDirectory")
                result.workingDirectory = if (isNullish(path)) cwd else runtime.files.nonNullPath(RhinoScriptRuntime.toString(path))
            }
            else -> if (isNullish(config)) {
                result.workingDirectory = cwd
                result.delay = 0L
                result.interval = 0L
                result.loopTimes = 1
            } else {
                throw GlobalValues.typeError(function, "config", "Object", o)
            }
        }
        return result
    }

    private fun field(config: Scriptable, name: String): Any? = ScriptableObject.getProperty(config, name)

}
