package org.autojs.autojs.runtime.api

import androidx.annotation.VisibleForTesting
import org.autojs.autojs.AutoJs
import org.autojs.autojs.engine.JavaScriptEngine
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.script.AutoFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.ScriptLaunchSourceFactory
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs.script.StringScriptSource
import org.autojs.autojs.script.TypeScriptStringSource
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
        val launch = ScriptLaunchSourceFactory.createPathLaunch(mScriptRuntime.files.nonNullPath(path))
        return AutoJs.instance.scriptEngineService.execute(
            launch.source,
            createExecScriptFileExecutionConfig(launch, config),
        )
    }

    fun execAutoFile(path: String, config: ExecutionConfig?): ScriptExecution {
        val resolvedPath = mScriptRuntime.files.nonNullPath(path)
        ScriptLaunchSourceFactory.enforcePythonLaunchBoundary(resolvedPath)
        return AutoJs.instance.scriptEngineService.execute(AutoFileSource(resolvedPath), config)
    }

    fun all(): NativeArray = engines.toNativeArray()

    fun stopAll(): Int = AutoJs.instance.scriptEngineService.stopAll()

    fun stopAllAndToast(): Int = AutoJs.instance.scriptEngineService.stopAllAndToast()

    companion object {

        @JvmStatic
        @VisibleForTesting
        fun createExecScriptFileExecutionConfig(
            launch: ScriptLaunchSourceFactory.Launch,
            config: ExecutionConfig?,
        ): ExecutionConfig {
            val base = config ?: ExecutionConfig()
            return launch.createExecutionConfig(
                base = base,
                overrideWorkingDirectory = base.workingDirectory.isBlank() || launch.nodeProjectLaunchSource != null,
            )
        }

        @VisibleForTesting
        internal fun createExecScriptSource(name: String, script: String): JavaScriptSource {
            return if (
                JavaScriptSource.isCompilerBackedTypeScriptFileName(name) ||
                JavaScriptSource.isTypeScriptDeclarationFileName(name)
            ) {
                TypeScriptStringSource(name, script)
            } else {
                StringScriptSource(name, script)
            }
        }

        private fun execScriptInternal(name: String, script: String, config: ExecutionConfig): ScriptExecution {
            val scriptSource = createExecScriptSource(name, script).apply { prefix = "\$engine/" }
            return AutoJs.instance.scriptEngineService.execute(scriptSource, config)
        }

    }

}
