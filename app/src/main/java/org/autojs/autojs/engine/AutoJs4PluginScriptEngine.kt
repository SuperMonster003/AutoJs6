package org.autojs.autojs.engine

import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.console.GlobalConsole
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.autojs4.AutoJs4RuntimePluginClient
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.script.AutoJs4ScriptSource
import org.autojs.autojs6.R
import org.autojs.plugin.autojs4.runtime.api.AutoJs4RuntimeContract
import org.autojs.plugin.autojs4.runtime.api.IAutoJs4RuntimeCallback
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Host-side engine of the "Run with AutoJs4" action. The script itself is executed by the
 * AutoJs4 Runtime plugin in its own process; this engine admits the request, keeps a dedicated
 * service binding alive for the whole execution, mirrors console lines into the host console
 * with the `[AutoJs4]` prefix and maps completion, cancellation and plugin death to the usual
 * `ScriptExecution` outcomes (roadmap D2, D6, P3.3, P3.4).
 *
 * zh-CN: "使用 AutoJs4 运行" 的宿主侧引擎. 脚本本身由 AutoJs4 Runtime 插件在其进程中执行; 本引擎受理
 * 请求, 在整个执行期间保持专用服务绑定, 把控制台行以 `[AutoJs4]` 前缀镜像到宿主控制台, 并把结束, 取消
 * 与插件进程死亡映射为常规的 `ScriptExecution` 结果 (roadmap D2, D6, P3.3, P3.4).
 */
class AutoJs4PluginScriptEngine(
    context: Context,
    private val console: GlobalConsole,
) : ScriptEngine.AbstractScriptEngine<AutoJs4ScriptSource>() {
    private val appContext = context.applicationContext
    private val stopped = AtomicBoolean(false)
    private val selectedRuntime = AtomicReference<AutoJs4RuntimePluginClient.Selection?>()
    private val activeExecution = AtomicReference<ActiveExecution?>()

    override fun put(name: String, value: Any?) = Unit

    override fun init() {
        check(!isDestroyed) { "AutoJs4 engine is destroyed" }
        if (stopped.get()) throw ScriptInterruptedException()
        val selection = runRpc(SELECT_RPC_DEADLINE_MILLIS) { AutoJs4RuntimePluginClient.select(appContext) }
        runRpc(SELECT_RPC_DEADLINE_MILLIS) { AutoJs4RuntimePluginClient.runtimeInfo(appContext, selection) }
        selectedRuntime.set(selection)
    }

    override fun execute(scriptSource: AutoJs4ScriptSource): Any {
        check(!isDestroyed) { "AutoJs4 engine is destroyed" }
        if (stopped.get()) throw ScriptInterruptedException()
        val selection = selectedRuntime.get() ?: error("AutoJs4 engine was not initialized")
        val executionId = "autojs4-engine-$id-${UUID.randomUUID()}"
        val callback = EventCallback(executionId, selection.providerUid)
        val leased = try {
            blockingRpc {
                // An admission answering after the deadline is cancelled in the runtime and its binding released (Q59).
                AidlPluginHost.withTimeoutAbandoningLate(
                    ADMISSION_RPC_DEADLINE_MILLIS,
                    { admission: AutoJs4RuntimePluginClient.Admission -> admission.abandon(executionId) },
                ) {
                    AutoJs4RuntimePluginClient.runScript(appContext, selection, buildRequest(scriptSource, executionId), callback)
                }
            }
        } catch (error: RemoteException) {
            throw AutoJs4RuntimeExecutionException(appContext.getString(R.string.error_autojs4_runtime_exited_unexpectedly), error)
        }
        val active = ActiveExecution(executionId, selection, leased, callback)
        check(activeExecution.compareAndSet(null, active)) { "AutoJs4 engine already has an active execution" }
        try {
            val acceptance = leased.value.acceptance
            if (!acceptance.getBoolean(AutoJs4RuntimeContract.KEY_ACCEPTED)) {
                throw AutoJs4RuntimeExecutionException(describeRejection(acceptance))
            }
            active.watchBinder()
            if (stopped.get()) requestCancellation(active)
            val terminal = callback.awaitTerminal(active) ?: run {
                if (callback.processDied.get()) {
                    throw AutoJs4RuntimeExecutionException(appContext.getString(R.string.error_autojs4_runtime_exited_unexpectedly))
                }
                Log.w(TAG, "AutoJs4 execution $executionId did not confirm the stop request in time")
                throw ScriptInterruptedException()
            }
            if (stopped.get() || terminal.getBoolean(AutoJs4RuntimeContract.KEY_CANCELLED)) {
                throw ScriptInterruptedException()
            }
            if (!terminal.getBoolean(AutoJs4RuntimeContract.KEY_SUCCEEDED)) {
                val code = terminal.getString(AutoJs4RuntimeContract.KEY_ERROR_CODE).orEmpty()
                val message = terminal.getString(AutoJs4RuntimeContract.KEY_ERROR_MESSAGE) ?: "AutoJs4 execution failed"
                throw AutoJs4RuntimeExecutionException("$code: $message".trimStart(':', ' '))
            }
            return terminal.getString(AutoJs4RuntimeContract.KEY_RESULT) ?: 0
        } finally {
            active.release()
            activeExecution.compareAndSet(active, null)
        }
    }

    override fun forceStop() {
        stopped.set(true)
        activeExecution.get()?.let(::requestCancellation)
    }

    override fun destroy() {
        if (!isDestroyed) {
            forceStop()
            selectedRuntime.set(null)
            super.destroy()
        }
    }

    override fun toString(): String =
        "AutoJs4PluginScriptEngine@${Integer.toHexString(hashCode())}" +
            "{id=$id,source='${getTag(TAG_SOURCE)}',cwd='${cwd()}'}"

    private fun buildRequest(source: AutoJs4ScriptSource, executionId: String): Bundle = Bundle().apply {
        putInt(AutoJs4RuntimeContract.KEY_PROTOCOL_VERSION, AutoJs4RuntimeContract.PROTOCOL_VERSION)
        putString(AutoJs4RuntimeContract.KEY_EXECUTION_ID, executionId)
        putString(AutoJs4RuntimeContract.KEY_SOURCE_TYPE, source.sourceType)
        putString(AutoJs4RuntimeContract.KEY_SCRIPT_PATH, source.file.path)
        putString(
            AutoJs4RuntimeContract.KEY_WORKING_DIRECTORY,
            cwd()?.takeIf(String::isNotBlank) ?: source.workingDirectory,
        )
        source.projectDirectory?.let { putString(AutoJs4RuntimeContract.KEY_PROJECT_DIRECTORY, it.path) }
        scriptArguments()?.let { putBundle(AutoJs4RuntimeContract.KEY_ARGUMENTS, it) }
    }

    /** Host `ExecutionConfig.arguments` become AutoJs4 `engines.myEngine().execArgv`; only protocol scalar types travel. */
    private fun scriptArguments(): Bundle? {
        val config = getTag(ExecutionConfig.tag) as? ExecutionConfig ?: return null
        val arguments = config.arguments.entries
            .filter { (key, value) ->
                key.isNotEmpty() &&
                    key.toByteArray(Charsets.UTF_8).size <= AutoJs4RuntimeContract.MAX_ARGUMENT_KEY_BYTES &&
                    (value is String || value is Boolean || value is Int || value is Long || value is Double)
            }
            .take(AutoJs4RuntimeContract.MAX_ARGUMENTS)
        if (arguments.isEmpty()) return null
        return Bundle().apply {
            arguments.forEach { (key, value) ->
                when (value) {
                    is String -> if (value.toByteArray(Charsets.UTF_8).size <= AutoJs4RuntimeContract.MAX_ARGUMENT_VALUE_BYTES) putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Double -> putDouble(key, value)
                }
            }
        }
    }

    private fun describeRejection(acceptance: Bundle): String {
        val code = acceptance.getString(AutoJs4RuntimeContract.KEY_ERROR_CODE).orEmpty()
        val message = acceptance.getString(AutoJs4RuntimeContract.KEY_ERROR_MESSAGE).orEmpty()
        if (code == AutoJs4RuntimeContract.ERROR_PERMISSION_DENIED) {
            val permission = acceptance.getString(AutoJs4RuntimeContract.KEY_PERMISSION) ?: AutoJs4RuntimeContract.PERMISSION_STORAGE
            return appContext.getString(R.string.error_autojs4_runtime_permission_denied, permission)
        }
        if (code == AutoJs4RuntimeContract.ERROR_RUNTIME_UNAVAILABLE) {
            return appContext.getString(R.string.error_autojs4_runtime_exited_unexpectedly)
        }
        return "$code: $message".trimStart(':', ' ').ifBlank { "AutoJs4 Runtime rejected the request" }
    }

    private fun requestCancellation(active: ActiveExecution) {
        active.callback.markStopRequested()
        Thread({
            runCatching {
                runBlocking {
                    withTimeout(CANCEL_RPC_DEADLINE_MILLIS) {
                        AutoJs4RuntimePluginClient.cancel(appContext, active.selection, active.executionId)
                    }
                }
            }.onFailure { Log.w(TAG, "Unable to deliver the stop request for ${active.executionId}", it) }
        }, "autojs4-cancel-${active.executionId}").apply { isDaemon = true }.start()
    }

    private fun <T> runRpc(deadlineMillis: Long, block: suspend () -> T): T = blockingRpc { withTimeout(deadlineMillis) { block() } }

    private fun <T> blockingRpc(block: suspend CoroutineScope.() -> T): T {
        return try {
            runBlocking(block = block)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ScriptInterruptedException()
        }
    }

    private inner class ActiveExecution(
        val executionId: String,
        val selection: AutoJs4RuntimePluginClient.Selection,
        private val leased: AidlPluginHost.LeasedCall<AutoJs4RuntimePluginClient.Admission>,
        val callback: EventCallback,
    ) {
        private val deathRecipient = IBinder.DeathRecipient { callback.onProcessDied() }
        private val watching = AtomicBoolean(false)

        fun watchBinder() {
            if (!watching.compareAndSet(false, true)) return
            runCatching { leased.value.binder.linkToDeath(deathRecipient, 0) }
                .onFailure { callback.onProcessDied() }
        }

        fun release() {
            if (watching.get()) runCatching { leased.value.binder.unlinkToDeath(deathRecipient, 0) }
            callback.close()
            leased.bindingLease.close()
        }
    }

    private inner class EventCallback(
        private val executionId: String,
        private val expectedProviderUid: Int,
    ) : IAutoJs4RuntimeCallback.Stub() {
        private val closed = AtomicBoolean(false)
        private val lastSequence = AtomicLong(-1L)
        private val terminal = AtomicReference<Bundle?>()
        private val terminalLatch = CountDownLatch(1)
        private val stopRequestedAt = AtomicLong(0L)
        val processDied = AtomicBoolean(false)

        fun markStopRequested() {
            stopRequestedAt.compareAndSet(0L, SystemClock.elapsedRealtime())
        }

        fun onProcessDied() {
            processDied.set(true)
            terminalLatch.countDown()
        }

        fun close() {
            closed.set(true)
            terminalLatch.countDown()
        }

        /** Waits for the terminal event; returns null when the plugin died or a stop was not confirmed in time. */
        fun awaitTerminal(active: ActiveExecution): Bundle? {
            while (true) {
                try {
                    if (terminalLatch.await(TERMINAL_POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                        return terminal.get()
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    requestCancellation(active)
                    throw ScriptInterruptedException()
                }
                if (processDied.get()) return terminal.get()
                val requestedAt = stopRequestedAt.get()
                if (requestedAt != 0L && SystemClock.elapsedRealtime() - requestedAt > AutoJs4RuntimeContract.CANCEL_CONFIRM_TIMEOUT_MILLIS) {
                    return terminal.get()
                }
            }
        }

        override fun onEvent(event: Bundle?) {
            if (closed.get() || Binder.getCallingUid() != expectedProviderUid) return
            if (event?.getInt(AutoJs4RuntimeContract.KEY_PROTOCOL_VERSION, -1) != AutoJs4RuntimeContract.PROTOCOL_VERSION) return
            if (event.getString(AutoJs4RuntimeContract.KEY_EXECUTION_ID) != executionId) return
            val sequence = event.getLong(AutoJs4RuntimeContract.KEY_SEQUENCE, -1L)
            if (sequence < 0L || !acceptSequence(sequence)) return
            when (event.getString(AutoJs4RuntimeContract.KEY_EVENT_TYPE)) {
                AutoJs4RuntimeContract.EVENT_LOG -> {
                    val text = event.getString(AutoJs4RuntimeContract.KEY_TEXT) ?: return
                    if (text.toByteArray(Charsets.UTF_8).size > AutoJs4RuntimeContract.MAX_LOG_CHUNK_BYTES) return
                    val level = event.getInt(AutoJs4RuntimeContract.KEY_LEVEL, Log.INFO).coerceIn(Log.VERBOSE, Log.ASSERT)
                    val identity = Binder.clearCallingIdentity()
                    try {
                        console.println(level, LOG_PREFIX + text)
                    } finally {
                        Binder.restoreCallingIdentity(identity)
                    }
                }
                AutoJs4RuntimeContract.EVENT_FINISHED -> {
                    terminal.compareAndSet(null, Bundle(event))
                    terminalLatch.countDown()
                }
                AutoJs4RuntimeContract.EVENT_STARTED -> Unit
            }
        }

        private fun acceptSequence(sequence: Long): Boolean {
            while (!closed.get()) {
                val previous = lastSequence.get()
                if (sequence <= previous) return false
                if (lastSequence.compareAndSet(previous, sequence)) return true
            }
            return false
        }
    }

    private companion object {
        const val TAG = "AutoJs4PluginScriptEngine"
        const val LOG_PREFIX = "[AutoJs4] "
        const val SELECT_RPC_DEADLINE_MILLIS = 70_000L
        const val ADMISSION_RPC_DEADLINE_MILLIS = 60_000L + AutoJs4RuntimeContract.ACCEPT_TIMEOUT_MILLIS
        const val CANCEL_RPC_DEADLINE_MILLIS = 15_000L
        const val TERMINAL_POLL_MILLIS = 500L
    }
}

class AutoJs4RuntimeExecutionException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
