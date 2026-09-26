package org.autojs.autojs.engine

import android.os.SystemClock
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.console.ConsoleLogFormat
import org.autojs.autojs.execution.AgentScriptExecutionState
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.ScriptExecutionListener
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.script.AutoFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.StringScriptSource
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.JvmFileSource
import org.autojs.autojs.script.LuaFileSource
import org.autojs.autojs.script.PythonFileSource
import org.autojs.autojs.script.PythonRuntimePreviewFeature
import org.autojs.autojs.script.ScriptLaunchSourceFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One authorized Agent script, with the same cancellation and result lifetime for file and inline sources. */
internal class AgentOwnedScriptExecution private constructor(
    private val workingDirectory: String,
    parametersJson: String,
    runId: String,
    presetName: String?,
    private val timeoutMs: Long,
    private val captureConsole: Boolean,
    private val cancellationRequested: () -> Boolean,
    private val createSource: (File) -> Pair<ScriptSource, ExecutionConfig>,
) : ScriptExecutionListener {
    private val agentState = AgentScriptExecutionState(runId, parametersJson, presetName)
    private val lock = Object()
    private val deadline = SystemClock.elapsedRealtime() + timeoutMs
    private var execution: ScriptExecution? = null
    private var outcome: Outcome? = null
    private var stopReason: String? = null
    private var responseFinished = false

    init { require(timeoutMs in 1..MAX_TIMEOUT_MS) { "Invalid Agent script timeout" } }

    fun execute(): JSONObject {
        val console = AutoJs.instance.globalConsole
        val sinceId = console.latestLogId
        try {
            if (cancellationRequested()) requestStop(OUTCOME_STOPPED)
            if (SystemClock.elapsedRealtime() >= deadline) requestStop(OUTCOME_TIMEOUT)
            if (synchronized(lock) { stopReason == null }) {
                val cwd = File(workingDirectory).canonicalFile
                require(cwd.isDirectory) { "Agent working directory is unavailable" }
                val (source, launchConfig) = createSource(cwd)
                val config = launchConfig.apply {
                    agentExecutionState = agentState
                    val parameters = JSONObject(agentState.parametersJson)
                    for (key in parameters.keys()) {
                        require(key.isNotBlank() && !key.contains('\u0000')) { "Invalid Agent script parameter name" }
                        val value = parameters.get(key)
                        require(value is String || value is Number || value is Boolean || value === JSONObject.NULL) {
                            "Agent script parameters must be scalar values"
                        }
                        setArgument(key, value)
                    }
                }
                if (SystemClock.elapsedRealtime() >= deadline) requestStop(OUTCOME_TIMEOUT)
                if (cancellationRequested()) requestStop(OUTCOME_STOPPED)
                if (synchronized(lock) { stopReason == null }) {
                    // The listener's onStart gate also closes cancellation between this check and
                    // execute(): an engine created later is stopped before evaluating its source.
                    val started = AutoJs.instance.scriptEngineService.execute(source, this, config)
                    synchronized(lock) { execution = started }
                    if (!awaitOutcome(deadline, stopAware = true)) {
                        if (synchronized(lock) { stopReason == null }) requestStop(OUTCOME_TIMEOUT)
                        stopOwnedEngine()
                        awaitOutcome(SystemClock.elapsedRealtime() + STOP_SETTLE_MS, stopAware = false)
                    }
                }
            }
        } catch (_: InterruptedException) {
            requestStop(OUTCOME_STOPPED)
            stopOwnedEngine()
            Thread.currentThread().interrupt()
        } catch (error: Exception) {
            synchronized(lock) {
                if (outcome == null) outcome = Outcome(error)
            }
        } finally {
            agentState.finish()
        }
        val result = synchronized(lock) {
            responseFinished = true
            val finalOutcome = when {
                stopReason != null -> stopReason!!
                outcome?.error?.let(ScriptInterruptedException::causedByInterrupt) == true -> OUTCOME_STOPPED
                outcome?.error != null -> OUTCOME_EXCEPTION
                else -> OUTCOME_SUCCESS
            }
            JSONObject()
                .put("executionId", execution?.id ?: JSONObject.NULL)
                .put("finished", outcome != null || execution == null)
                .put("outcome", finalOutcome)
                .put("error", when (finalOutcome) {
                    OUTCOME_TIMEOUT -> JSONObject().put("code", "SCRIPT_TIMEOUT").put("message", "Agent script exceeded its timeout")
                    OUTCOME_STOPPED -> JSONObject().put("code", "CANCELLED").put("message", "Agent script was stopped")
                    OUTCOME_EXCEPTION -> JSONObject().put("code", "SCRIPT_FAILED").put("message", boundedError(outcome?.error))
                    else -> JSONObject.NULL
                })
                .put("result", agentState.getResultJson()?.let(NodeBridgeJson::parseJsonValue) ?: JSONObject.NULL)
                .put("resultReported", agentState.getResultJson() != null)
        }
        val tail = JSONArray()
        if (captureConsole) {
            val selection = NodeBridgeConsoleTail.select(console.snapshotLogEntries().map {
                NodeBridgeConsoleTail.Entry(it.id, it.level, ConsoleLogFormat.formatLine(it.timestamp, it.level, it.content), it.timestamp)
            }, lines = 40, sinceId = sinceId, untilId = console.latestLogId, maxBytes = 64 * 1024)
            selection.entries.forEach { tail.put(it.text) }
            result.put("consoleCaptureMode", NodeBridgeConsoleBridge.CAPTURE_MODE_GLOBAL_WINDOW)
        }
        return result.put("consoleTail", tail)
    }

    fun cancel() { requestStop(OUTCOME_STOPPED) }

    private fun requestStop(reason: String) {
        synchronized(lock) {
            if (responseFinished || outcome != null || stopReason != null) return
            stopReason = reason
            agentState.finish()
            lock.notifyAll()
        }
        stopOwnedEngine()
    }

    private fun stopOwnedEngine() {
        val owned = synchronized(lock) { execution?.engine }
        if (owned != null && !owned.isDestroyed) runCatching { owned.forceStop() }
    }

    override fun onStart(execution: ScriptExecution) {
        val cancelled = synchronized(lock) {
            this.execution = execution
            if (cancellationRequested() && stopReason == null) stopReason = OUTCOME_STOPPED
            if (SystemClock.elapsedRealtime() >= deadline && stopReason == null) stopReason = OUTCOME_TIMEOUT
            stopReason != null
        }
        if (cancelled) {
            agentState.finish()
            runCatching { execution.engine?.forceStop() }
            throw ScriptInterruptedException()
        }
    }

    override fun onSuccess(execution: ScriptExecution, result: Any?) = completed(execution, null)

    override fun onException(execution: ScriptExecution, e: Throwable) = completed(execution, e)

    private fun completed(execution: ScriptExecution, error: Throwable?) {
        synchronized(lock) {
            this.execution = execution
            if (outcome == null) outcome = Outcome(error)
            agentState.finish()
            lock.notifyAll()
        }
    }

    private fun awaitOutcome(until: Long, stopAware: Boolean): Boolean = synchronized(lock) {
        while (outcome == null && (!stopAware || stopReason == null)) {
            if (stopAware && cancellationRequested()) {
                stopReason = OUTCOME_STOPPED
                agentState.finish()
                break
            }
            val remaining = until - SystemClock.elapsedRealtime()
            if (remaining <= 0) break
            lock.wait(minOf(remaining, 100L))
        }
        outcome != null
    }

    private class Outcome(val error: Throwable?)

    companion object {
        fun registered(
            launchPath: String, workingDirectory: String, expectedScriptPath: String,
            parametersJson: String, runId: String, presetName: String?, timeoutMs: Long,
            captureConsole: Boolean, cancellationRequested: () -> Boolean,
        ) = AgentOwnedScriptExecution(workingDirectory, parametersJson, runId, presetName,
            timeoutMs, captureConsole, cancellationRequested) { cwd ->
            val path = File(launchPath).canonicalFile
            require(path.exists() && (path == cwd || path.path.startsWith(cwd.path + File.separator))) {
                "Registered script is outside its working directory"
            }
            val launch = if (path.isDirectory) {
                ScriptLaunchSourceFactory.createAutoJsProjectLaunchForPythonPreview(path, cwd.path, PythonRuntimePreviewFeature.isEnabled)
            } else {
                ScriptLaunchSourceFactory.createPathLaunch(path.path, workingDirectory = cwd.path, defaultWorkingDirectory = cwd.path)
            }
            val resolvedFile = when (val source = launch.source) {
                is JavaScriptFileSource -> source.file
                is PythonFileSource -> source.file
                is LuaFileSource -> source.file
                is JvmFileSource -> source.file
                is AutoFileSource -> source.file
                else -> throw IllegalArgumentException("Registered script has no verifiable file source")
            }.canonicalFile
            require(resolvedFile.isFile && resolvedFile == File(expectedScriptPath).canonicalFile &&
                resolvedFile.path.startsWith(cwd.path + File.separator)) {
                "Registered script launch no longer matches the authorized entry"
            }
            launch.source to launch.createExecutionConfig(ExecutionConfig(workingDirectory = cwd.path))
        }

        fun dynamic(
            name: String, source: String, workingDirectory: String, runId: String, presetName: String?,
            timeoutMs: Long, captureConsole: Boolean, cancellationRequested: () -> Boolean,
        ): AgentOwnedScriptExecution {
            AgentDynamicScriptPolicy.validateSource(name, source)
            val script = StringScriptSource(name, source).apply { prefix = "\$agent/" }
            require(script.engineName == JavaScriptSource.ENGINE &&
                script.executionMode and (JavaScriptSource.EXECUTION_MODE_UI or JavaScriptSource.EXECUTION_MODE_UI_THREAD) == 0) {
                "Dynamic Agent scripts require the standard background JavaScript runtime"
            }
            return AgentOwnedScriptExecution(workingDirectory, "{}", runId, presetName,
                timeoutMs, captureConsole, cancellationRequested) { cwd ->
                script to ExecutionConfig(workingDirectory = cwd.path)
            }
        }

        const val MAX_TIMEOUT_MS = 300_000L
        private const val STOP_SETTLE_MS = 500L
        private const val OUTCOME_SUCCESS = "success"
        private const val OUTCOME_EXCEPTION = "exception"
        private const val OUTCOME_STOPPED = "stopped"
        private const val OUTCOME_TIMEOUT = "timeout"

        private fun boundedError(error: Throwable?): String {
            val message = error?.message ?: "Agent script failed"
            val builder = StringBuilder()
            var bytes = 0
            for (codePoint in message.codePoints().toArray()) {
                val part = String(Character.toChars(codePoint))
                val size = part.toByteArray(Charsets.UTF_8).size
                if (bytes + size > 4 * 1024) break
                builder.append(part)
                bytes += size
            }
            return builder.toString()
        }
    }
}
