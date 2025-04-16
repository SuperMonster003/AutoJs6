package org.autojs.autojs.runtime.api

import org.autojs.autojs.AutoJs
import org.autojs.autojs.engine.JavaScriptEngine
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.script.AutoFileSource
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs.script.StringScriptSource
import org.mozilla.javascript.NativeArray

/**
 * Created by Stardust on Aug 4, 2017.
 * Transformed by SuperMonster003 on Oct 26, 2024.
 */
class Engines(private val mScriptRuntime: ScriptRuntime) {

    private lateinit var mScriptEngine: JavaScriptEngine

    val engines: Set<ScriptEngine<out ScriptSource?>>
        get() = AutoJs.instance.scriptEngineService.engines

    fun setCurrentEngine(engine: JavaScriptEngine) {
        check(!::mScriptEngine.isInitialized)
        mScriptEngine = engine
    }

    fun myEngine(): JavaScriptEngine = mScriptEngine

    fun execScript(name: String, script: String, config: ExecutionConfig): ScriptExecution {
        return execScriptInternal(name, script, config)
    }

    fun execScriptFile(path: String, config: ExecutionConfig?): ScriptExecution {
        return AutoJs.instance.scriptEngineService.execute(JavaScriptFileSource(mScriptRuntime.files.nonNullPath(path)), config)
    }

    fun execAutoFile(path: String, config: ExecutionConfig?): ScriptExecution {
        return AutoJs.instance.scriptEngineService.execute(AutoFileSource(mScriptRuntime.files.nonNullPath(path)), config)
    }

    fun all(): NativeArray = engines.toNativeArray()

    fun stopAll(): Int = AutoJs.instance.scriptEngineService.stopAll()

    fun stopAllAndToast(): Int = AutoJs.instance.scriptEngineService.stopAllAndToast()

    companion object {

        private fun execScriptInternal(name: String, script: String, config: ExecutionConfig): ScriptExecution {
            val scriptSource = StringScriptSource(name, script).apply { prefix = "\$engine/" }
            return AutoJs.instance.scriptEngineService.execute(scriptSource, config)
        }

    }

}
