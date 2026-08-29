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

    fun evaluate(source: String, onComplete: () -> Unit) {
        val console = AutoJs.instance.globalConsole
        console.log("> $source")
        val scriptSource = Engines.createExecScriptSource(SOURCE_NAME, source).apply {
            prefix = SOURCE_PREFIX
        }
        val listener = object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                try {
                    console.info("=> ${formatResult(result)}")
                } finally {
                    onComplete()
                }
            }

            override fun onException(execution: ScriptExecution, e: Throwable) {
                onComplete()
            }
        }
        try {
            AutoJs.instance.scriptEngineService.execute(
                scriptSource,
                listener,
                ExecutionConfig(workingDirectory = workingDirectory),
            )
        } catch (error: Throwable) {
            console.error(error)
            onComplete()
        }
    }

    companion object {

        private const val SOURCE_NAME = "console.ts"
        private const val SOURCE_PREFIX = "\$console/"

        internal fun formatResult(result: Any?): String = runCatching {
            Context.toString(result)
        }.getOrDefault(java.lang.String.valueOf(result))

    }

}
