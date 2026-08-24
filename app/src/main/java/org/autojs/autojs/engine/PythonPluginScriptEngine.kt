package org.autojs.autojs.engine

import android.content.Context
import android.util.Log
import org.autojs.autojs.core.console.GlobalConsole
import org.autojs.autojs.core.plugin.python.PythonExecutionInputPolicy
import org.autojs.autojs.core.plugin.python.PythonInteractiveInputAuthorization
import org.autojs.autojs.core.plugin.python.PythonLongRunningExecutionAuthorization
import org.autojs.autojs.core.plugin.python.PythonRuntimeClient
import org.autojs.autojs.core.plugin.python.PythonRuntimeExecutionCoordinator
import org.autojs.autojs.core.plugin.python.PythonRuntimeHostError
import org.autojs.autojs.core.plugin.python.PythonRuntimeHostException
import org.autojs.autojs.core.plugin.python.PythonRuntimeResultPresenter
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.script.PythonFileSource
import org.autojs.autojs.script.PythonProjectSource
import org.autojs.autojs.script.PythonRuntimeChannelPolicy
import org.autojs.autojs.script.PythonScriptSource
import org.autojs.plugin.python.runtime.api.PythonExecutionMode
import java.util.concurrent.atomic.AtomicBoolean
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
    private val queueCancellation = AtomicReference<AtomicBoolean?>()

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
        val cancellation = AtomicBoolean(false)
        if (!queueCancellation.compareAndSet(null, cancellation)) {
            throw PythonRuntimeHostException(
                PythonRuntimeHostError.BUSY,
                "Python engine already owns an active or queued execution",
            )
        }
        val currentThread = Thread.currentThread()
        if (!executionThread.compareAndSet(null, currentThread)) {
            queueCancellation.compareAndSet(cancellation, null)
            throw PythonRuntimeHostException(
                PythonRuntimeHostError.BUSY,
                "Python engine already owns an execution thread",
            )
        }
        return try {
            PythonRuntimeExecutionCoordinator.acquire().use {
                if (cancellation.get() || isDestroyed) {
                    throw InterruptedException("Queued Python execution was cancelled")
                }
                val executionConfig = getTag(ExecutionConfig.tag) as? ExecutionConfig
                val entryMode = PythonExecutionInputPolicy.resolveEntryMode(executionConfig)
                val stdinSnapshot = PythonExecutionInputPolicy.resolveStdinSnapshot(executionConfig)
                val interactiveInputGrant = PythonInteractiveInputAuthorization.resolve(executionConfig)
                val executionMode = (fileSource as? PythonProjectSource)?.executionMode
                    ?: PythonExecutionMode.BOUNDED
                val longRunningGrant = if (executionMode == PythonExecutionMode.LONG_RUNNING) {
                    PythonLongRunningExecutionAuthorization.resolve(executionConfig)
                } else {
                    null
                }
                val execution = try {
                    client.createExecution(
                        fileSource,
                        entryMode,
                        stdinSnapshot,
                        interactiveInputGrant,
                        executionMode,
                        longRunningGrant,
                        (fileSource as? PythonProjectSource)?.timeoutMillis,
                        id,
                        startTime,
                        ::forceStop,
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
                try {
                    val result = execution.run()
                    PythonRuntimeResultPresenter.present(fileSource, id, result).consoleLines.forEach { line ->
                        globalConsole.println(Log.INFO, line)
                    }
                    result
                } finally {
                    activeExecution.compareAndSet(execution, null)
                }
            }
        } catch (error: InterruptedException) {
            Thread.interrupted()
            throw ScriptInterruptedException(error)
        } finally {
            queueCancellation.compareAndSet(cancellation, null)
            executionThread.compareAndSet(currentThread, null)
        }
    }

    override fun forceStop() {
        queueCancellation.get()?.set(true)
        activeExecution.get()?.cancel()
        executionThread.get()?.interrupt()
    }

    @Synchronized
    override fun destroy() {
        forceStop()
        super.destroy()
    }
}
