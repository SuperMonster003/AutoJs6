package org.autojs.autojs.engine

import android.content.Context
import android.util.Log
import org.autojs.autojs.core.console.GlobalConsole
import org.autojs.autojs.core.plugin.python.PythonExecutionInputPolicy
import org.autojs.autojs.core.plugin.python.PythonInteractiveInputAuthorization
import org.autojs.autojs.core.plugin.python.PythonRuntimeClient
import org.autojs.autojs.core.plugin.python.PythonRuntimeHostError
import org.autojs.autojs.core.plugin.python.PythonRuntimeHostException
import org.autojs.autojs.core.plugin.python.PythonRuntimeResultPresenter
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.script.PythonFileSource
import org.autojs.autojs.script.PythonProjectSource
import org.autojs.autojs.script.PythonRuntimeChannelPolicy
import org.autojs.autojs.script.PythonScriptSource
import java.util.concurrent.atomic.AtomicReference

/** Stable host facade; CPython remains entirely in the independent plugin process. */
class PythonPluginScriptEngine(
    context: Context,
    globalConsole: GlobalConsole,
) : ScriptEngine.AbstractScriptEngine<PythonScriptSource>() {

    private val globalConsole = globalConsole
    private val client = PythonRuntimeClient(context, globalConsole)
    private val activeExecution = AtomicReference<PythonRuntimeClient.Execution?>()
    private val executionThread = AtomicReference<Thread?>()

    override fun init() = Unit

    /** Java globals are deliberately unavailable across the Python plugin boundary. */
    override fun put(name: String, value: Any) = Unit

    override fun execute(scriptSource: PythonScriptSource): Any {
        if (!PythonRuntimeChannelPolicy.isEnabledForCurrentBuild) {
            throw PythonRuntimeHostException(
                PythonRuntimeHostError.PREVIEW_DISABLED,
                "Python Runtime is not supported by this host flavor",
            )
        }
        if (isDestroyed) {
            throw PythonRuntimeHostException(
                PythonRuntimeHostError.EXECUTION_FAILED,
                "Python engine is already destroyed",
            )
        }
        val fileSource = scriptSource as? PythonFileSource
            ?: throw PythonRuntimeHostException(
                PythonRuntimeHostError.SOURCE_INVALID,
                "Python preview accepts only admitted local file or project sources",
            )
        val executionConfig = getTag(ExecutionConfig.tag) as? ExecutionConfig
        val entryMode = PythonExecutionInputPolicy.resolveEntryMode(executionConfig)
        val stdinSnapshot = PythonExecutionInputPolicy.resolveStdinSnapshot(executionConfig)
        val interactiveInputGrant = PythonInteractiveInputAuthorization.resolve(executionConfig)
        val execution = try {
            client.createExecution(
                fileSource,
                entryMode,
                stdinSnapshot,
                interactiveInputGrant,
                (fileSource as? PythonProjectSource)?.timeoutMillis,
            )
        } finally {
            stdinSnapshot?.fill(0)
        }
        if (!activeExecution.compareAndSet(null, execution)) {
            execution.cancel()
            throw PythonRuntimeHostException(
                PythonRuntimeHostError.BUSY,
                "Python engine already owns an active execution",
            )
        }
        executionThread.set(Thread.currentThread())
        return try {
            val result = execution.run()
            PythonRuntimeResultPresenter.present(fileSource, id, result).consoleLines.forEach { line ->
                globalConsole.println(Log.INFO, line)
            }
            result
        } catch (error: InterruptedException) {
            Thread.interrupted()
            throw ScriptInterruptedException(error)
        } finally {
            executionThread.compareAndSet(Thread.currentThread(), null)
            activeExecution.compareAndSet(execution, null)
        }
    }

    override fun forceStop() {
        activeExecution.get()?.cancel()
        executionThread.get()?.interrupt()
    }

    @Synchronized
    override fun destroy() {
        forceStop()
        super.destroy()
    }
}
