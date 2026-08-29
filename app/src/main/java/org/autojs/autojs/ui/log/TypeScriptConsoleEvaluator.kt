package org.autojs.autojs.ui.log

import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.runtime.api.Engines
import org.mozilla.javascript.Context

/** Executes independent TypeScript snippets and reports their final values to Global Console. */
internal class TypeScriptConsoleEvaluator(
    private val workingDirectory: String = "",
) {

    private val executionLock = Any()
    private var activeRequest: ActiveRequest? = null

    fun evaluate(source: String, onComplete: () -> Unit) {
        val request = synchronized(executionLock) {
            if (activeRequest != null) {
                null
            } else {
                ActiveRequest(onComplete).also { activeRequest = it }
            }
        }
        if (request == null) {
            onComplete()
            return
        }
        val console = AutoJs.instance.globalConsole
        console.log("> $source")
        val scriptSource = Engines.createExecScriptSource(SOURCE_NAME, source).apply {
            prefix = SOURCE_PREFIX
        }
        val listener = object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) {
                stopIfRequested(request, execution)
            }

            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                try {
                    console.info("=> ${formatResult(result)}")
                } finally {
                    complete(request)
                }
            }

            override fun onException(execution: ScriptExecution, e: Throwable) {
                complete(request)
            }
        }
        try {
            val execution = AutoJs.instance.scriptEngineService.execute(
                scriptSource,
                listener,
                ExecutionConfig(workingDirectory = workingDirectory),
            )
            synchronized(executionLock) {
                if (activeRequest === request) {
                    request.execution = execution
                }
            }
            stopIfRequested(request, execution)
        } catch (error: Throwable) {
            console.error(error)
            complete(request)
        }
    }

    fun stop(): Boolean {
        val request = synchronized(executionLock) {
            activeRequest?.also { it.stopRequested = true }
        } ?: return false
        request.execution?.engine?.forceStop()
        return true
    }

    internal fun isRunning(): Boolean = synchronized(executionLock) {
        activeRequest != null
    }

    private fun stopIfRequested(request: ActiveRequest, execution: ScriptExecution) {
        val shouldStop = synchronized(executionLock) {
            activeRequest === request && request.stopRequested
        }
        if (shouldStop) {
            execution.engine?.forceStop()
        }
    }

    private fun complete(request: ActiveRequest) {
        val onComplete = synchronized(executionLock) {
            if (activeRequest !== request) {
                null
            } else {
                activeRequest = null
                request.onComplete
            }
        }
        onComplete?.invoke()
    }

    private class ActiveRequest(
        val onComplete: () -> Unit,
    ) {
        @Volatile
        var execution: ScriptExecution? = null

        var stopRequested: Boolean = false
    }

    companion object {

        private const val SOURCE_NAME = "console.ts"
        private const val SOURCE_PREFIX = "\$console/"

        internal fun formatResult(result: Any?): String = runCatching {
            Context.toString(result)
        }.getOrDefault(java.lang.String.valueOf(result))

    }

}
