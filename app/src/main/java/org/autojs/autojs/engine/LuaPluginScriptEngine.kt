package org.autojs.autojs.engine

import android.content.Context
import android.util.Log
import org.autojs.autojs.core.plugin.lua.LuaRuntimeHostCapabilities
import org.autojs.autojs.core.plugin.lua.LuaRuntimePluginHostFactory
import org.autojs.autojs.core.plugin.scope.BridgeExecutionScope
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.script.LuaScriptSource
import org.autojs.plugin.lua.runtime.api.LuaExecutionError
import org.autojs.plugin.lua.runtime.api.LuaOutputChunk
import org.autojs.plugin.lua.runtime.api.LuaOutputStream
import org.autojs.plugin.lua.runtime.api.LuaRuntimeContract
import org.autojs.plugin.lua.runtime.api.LuaValue
import org.autojs.plugin.lua.runtime.api.LuaExecutionRequest
import org.autojs.plugin.lua.runtime.api.LuaRequestId
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Local ScriptEngine proxy. Plugin discovery is intentionally deferred to [init]. */
class LuaPluginScriptEngine internal constructor(
    private val cacheDirectory: File,
    private val backend: LuaEngineRuntimeBackend,
    private val outputSink: (LuaOutputChunk) -> Unit = {},
    private val terminalGraceMillis: Long = DEFAULT_TERMINAL_GRACE_MILLIS,
) : ScriptEngine.AbstractScriptEngine<LuaScriptSource>() {

    constructor(context: Context) : this(
        cacheDirectory = context.applicationContext.cacheDir,
        backend = BinderLuaEngineRuntimeBackend(
            host = LuaRuntimePluginHostFactory.create(context.applicationContext),
            hostStorage = SharedPreferencesLuaHostStorage(context.applicationContext),
            toastSink = AndroidLuaToastSink(context.applicationContext),
        ),
        outputSink = ::emitOutput,
        terminalGraceMillis = DEFAULT_TERMINAL_GRACE_MILLIS,
    )

    init {
        require(terminalGraceMillis >= 0L) { "Lua terminal grace must not be negative" }
    }

    private val lifecycleLock = Any()
    private val destroyed = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private val currentConnection = AtomicReference<LuaEngineRuntimeConnection?>()
    private val currentSession = AtomicReference<LuaEngineRemoteSession?>()

    @Volatile
    private var ownerThread: Thread? = null

    @Volatile
    private var preparedRuntime: LuaEnginePreparedRuntime? = null

    @Volatile
    private var preparedArguments: LuaValue.MapValue = EMPTY_ARGUMENTS

    /** ScriptEngineManager injects `context`; Lua must never retain or serialize it. */
    override fun put(name: String, value: Any?) = Unit

    override fun init() {
        check(!destroyed.get()) { "Lua engine is destroyed" }
        ownerThread = Thread.currentThread()
        try {
            throwIfStopped()
            if (preparedRuntime == null) {
                val arguments = executionArguments()
                val runtime = backend.prepare()
                preparedArguments = arguments
                preparedRuntime = runtime
            }
            throwIfStopped()
        } catch (interrupted: InterruptedException) {
            ownerThread = null
            Thread.currentThread().interrupt()
            throw ScriptInterruptedException(interrupted)
        } catch (error: Throwable) {
            ownerThread = null
            throw error
        }
    }

    override fun execute(scriptSource: LuaScriptSource): Any {
        check(!destroyed.get()) { "Lua engine is destroyed" }
        val prepared = checkNotNull(preparedRuntime) { "Lua engine was not initialized" }
        val arguments = preparedArguments
        ownerThread = Thread.currentThread()
        try {
            throwIfStopped()
            LuaSourceSnapshot.create(cacheDirectory, scriptSource).use { snapshot ->
                val connection = backend.acquire(prepared)
                if (!currentConnection.compareAndSet(null, connection)) {
                    runCatching { connection.close() }
                    error("Lua engine already has an active connection")
                }
                try {
                    throwIfStopped()
                    val modules = LuaModuleSnapshot.create(scriptSource)
                    if (
                        modules.moduleCount != 0 &&
                        LuaRuntimeHostCapabilities.MODULE_SNAPSHOT !in connection.grantedCapabilities
                    ) {
                        throw LuaRuntimeExecutionException(
                            "The selected Lua provider does not support controlled module snapshots",
                        )
                    }
                    val request = executionRequest(snapshot, connection, arguments, modules)
                    val observer = LuaEngineExecutionObserver(request, connection.runtimeInfo, outputSink)
                    var session: LuaEngineRemoteSession? = null
                    // Released in the order of the former finally, after the backend revokes the broker and lets go of the
                    // callback it exported (docs/memory-phase2/ROADMAP.md C41): the observer, then the session.
                    // zh-CN: 后端先撤销 broker 并放开它导出的回调 (docs/memory-phase2/ROADMAP.md C41), 其后按原 finally 的顺序释放:
                    // 先观察者, 再会话.
                    val scope = BridgeExecutionScope("lua", request.requestId.toString()).apply {
                        own(BridgeExecutionScope.Stage.CALLBACKS, "session") { session?.close() }
                        own(BridgeExecutionScope.Stage.CALLBACKS, "observer", observer::close)
                    }
                    try {
                        val createdSession = connection.createExecution(request, snapshot, modules, observer, scope)
                        session = createdSession
                        synchronized(lifecycleLock) {
                            check(currentSession.compareAndSet(null, createdSession)) {
                                "Lua engine already has an active session"
                            }
                            throwIfStopped()
                            val dispatched = observer.dispatchStart(INITIAL_OUTPUT_CREDITS) {
                                createdSession.grantOutputCredits(INITIAL_OUTPUT_CREDITS)
                                createdSession.start()
                            }
                            if (!dispatched) runCatching { createdSession.cancel() }
                        }
                        val outcome = observer.await(request.timeoutMillis + terminalGraceMillis)
                            ?: run {
                                observer.requestCancel()
                                runCatching { createdSession.cancel() }
                                throw LuaRuntimeExecutionException("Lua execution did not reach a terminal state before its deadline")
                            }
                        return outcome.toEngineResult()
                    } catch (interrupted: InterruptedException) {
                        observer.requestCancel()
                        runCatching { session?.cancel() }
                        Thread.currentThread().interrupt()
                        throw ScriptInterruptedException(interrupted)
                    } finally {
                        currentSession.compareAndSet(session, null)
                        scope.close()
                    }
                } finally {
                    currentConnection.compareAndSet(connection, null)
                    runCatching { connection.close() }
                }
            }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ScriptInterruptedException(interrupted)
        }
    }

    override fun forceStop() {
        val session: LuaEngineRemoteSession?
        synchronized(lifecycleLock) {
            stopRequested.set(true)
            session = currentSession.get()
        }
        runCatching { session?.cancel() }
        ownerThread?.takeIf { it !== Thread.currentThread() }?.interrupt()
    }

    @Synchronized
    override fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        forceStop()
        runCatching { currentSession.getAndSet(null)?.close() }
        runCatching { currentConnection.getAndSet(null)?.close() }
        preparedRuntime = null
        preparedArguments = EMPTY_ARGUMENTS
        ownerThread = null
        super.destroy()
    }

    override fun toString(): String =
        "LuaPluginScriptEngine(id=$id, initialized=${preparedRuntime != null}, " +
            "active=${currentSession.get() != null}, stopped=${stopRequested.get()})"

    private fun executionRequest(
        snapshot: LuaSourceSnapshot,
        connection: LuaEngineRuntimeConnection,
        arguments: LuaValue.MapValue,
        modules: LuaModuleSnapshot,
    ): LuaExecutionRequest {
        val limits = connection.runtimeInfo.limits
        return LuaExecutionRequest(
            requestId = LuaRequestId.fromUuid(UUID.randomUUID()),
            protocolVersion = connection.protocolVersion,
            sourceName = snapshot.logicalName,
            sourceLengthBytes = snapshot.lengthBytes,
            sourceSha256 = snapshot.sha256,
            arguments = arguments,
            requiredCapabilities = requiredCapabilities(connection, modules, snapshot.storagePrincipal),
            outputByteLimit = minOf(LuaRuntimeContract.DEFAULT_OUTPUT_BYTES, limits.maxOutputBytes),
            memoryByteLimit = minOf(LuaRuntimeContract.DEFAULT_MEMORY_BYTES, limits.maxMemoryBytes),
            timeoutMillis = minOf(LuaRuntimeContract.DEFAULT_TIMEOUT_MILLIS, limits.maxExecutionMillis),
        )
    }

    private fun requiredCapabilities(
        connection: LuaEngineRuntimeConnection,
        modules: LuaModuleSnapshot,
        storagePrincipal: LuaStoragePrincipal?,
    ): List<String> = connection.grantedCapabilities.filter { capability ->
        when (capability) {
            LuaRuntimeHostCapabilities.MODULE_SNAPSHOT -> modules.moduleCount != 0
            LuaRuntimeHostCapabilities.STORAGE_KV -> storagePrincipal != null
            else -> true
        }
    }

    private fun executionArguments(): LuaValue.MapValue = when (val config = getTag(ExecutionConfig.tag)) {
        null -> EMPTY_ARGUMENTS
        is ExecutionConfig -> LuaExecutionArgumentMapper.map(config.arguments)
        else -> throw IllegalArgumentException(
            "Lua execution config tag has unsupported type ${config.javaClass.name}",
        )
    }

    private fun LuaEngineExecutionObserver.Outcome.toEngineResult(): Any = when (this) {
        is LuaEngineExecutionObserver.Outcome.Completed -> result.value
        is LuaEngineExecutionObserver.Outcome.Failed -> throw LuaProviderExecutionException(error)
        is LuaEngineExecutionObserver.Outcome.Cancelled -> {
            if (stopRequested.get()) throw ScriptInterruptedException()
            throw LuaRuntimeExecutionException("Lua execution was cancelled: ${cancellation.reason}")
        }
        is LuaEngineExecutionObserver.Outcome.TransportViolation -> {
            if (stopRequested.get() || error is InterruptedException) throw ScriptInterruptedException(error)
            throw LuaRuntimeExecutionException("Lua runtime transport or protocol failure", error)
        }
    }

    private fun throwIfStopped() {
        if (stopRequested.get() || Thread.currentThread().isInterrupted) throw ScriptInterruptedException()
    }

    private companion object {
        const val TAG = "LuaPluginScriptEngine"
        const val INITIAL_OUTPUT_CREDITS = LuaRuntimeContract.MAX_OUTSTANDING_OUTPUT_CREDITS
        const val DEFAULT_TERMINAL_GRACE_MILLIS = 5_000L
        val EMPTY_ARGUMENTS = LuaValue.MapValue(emptyMap())

        fun emitOutput(output: LuaOutputChunk) {
            val level = if (output.stream == LuaOutputStream.STDERR) Log.ERROR else Log.INFO
            val console = runCatching { ScriptEngineService.getInstance().globalConsole }.getOrNull()
            if (console == null) {
                Log.println(level, TAG, output.text)
            } else {
                console.println(level, output.text)
            }
        }
    }
}

open class LuaRuntimeExecutionException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

class LuaProviderExecutionException(
    val providerError: LuaExecutionError,
) : LuaRuntimeExecutionException(
    "Lua provider ${providerError.code} during ${providerError.phase}: ${providerError.message.safeDiagnosticText()}",
)

private fun String.safeDiagnosticText(): String = map { character ->
    if (Character.isISOControl(character.code)) ' ' else character
}.joinToString(separator = "")
