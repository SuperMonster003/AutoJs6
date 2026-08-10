package org.autojs.autojs.engine

import org.autojs.plugin.lua.runtime.api.LuaExecutionCancellation
import org.autojs.plugin.lua.runtime.api.LuaExecutionError
import org.autojs.plugin.lua.runtime.api.LuaExecutionRequest
import org.autojs.plugin.lua.runtime.api.LuaExecutionResult
import org.autojs.plugin.lua.runtime.api.LuaExecutionSessionPolicy
import org.autojs.plugin.lua.runtime.api.LuaOutputChunk
import org.autojs.plugin.lua.runtime.api.LuaRuntimeCodec
import org.autojs.plugin.lua.runtime.api.LuaRuntimeInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Thread-safe protocol observer shared by the Binder adapter and in-process engine tests. */
internal class LuaEngineExecutionObserver(
    request: LuaExecutionRequest,
    runtimeInfo: LuaRuntimeInfo,
    private val outputSink: (LuaOutputChunk) -> Unit,
) {

    sealed interface Outcome {
        data class Completed(val result: LuaExecutionResult) : Outcome
        data class Failed(val error: LuaExecutionError) : Outcome
        data class Cancelled(val cancellation: LuaExecutionCancellation) : Outcome
        data class TransportViolation(val error: Throwable) : Outcome
    }

    private val policy = LuaExecutionSessionPolicy(request, runtimeInfo)
    private val terminalLatch = CountDownLatch(1)

    @Volatile
    private var outcome: Outcome? = null

    /**
     * Serializes the last local terminal check with the one-way start dispatch. A provider death or
     * invalid callback observed while the synchronous creation call is returning therefore prevents
     * source execution instead of merely being reported after `start()`.
     */
    @Synchronized
    fun dispatchStart(initialCredits: Int, dispatch: () -> Unit): Boolean {
        if (outcome != null) return false
        check(policy.requestStart()) { "Lua execution start was already requested" }
        policy.grantOutputCredits(initialCredits)
        dispatch()
        return true
    }

    @Synchronized
    fun onStarted(metadata: ByteArray) = acceptNonTerminal {
        policy.onStarted(LuaRuntimeCodec.decodeStarted(metadata))
    }

    @Synchronized
    fun onOutput(encodedOutput: ByteArray): Boolean {
        return acceptNonTerminal {
            val output = LuaRuntimeCodec.decodeOutput(encodedOutput)
            policy.onOutput(output)
            outputSink(output)
            policy.grantOutputCredits(1)
            true
        } ?: false
    }

    @Synchronized
    fun onCompleted(encodedResult: ByteArray, descriptorCount: Int) = acceptNonTerminal {
        val result = LuaRuntimeCodec.decodeResult(encodedResult)
        policy.onCompleted(result, descriptorCount)
        complete(Outcome.Completed(result))
    }

    @Synchronized
    fun onFailed(encodedError: ByteArray) = acceptNonTerminal {
        val error = LuaRuntimeCodec.decodeError(encodedError)
        policy.onFailed(error)
        complete(Outcome.Failed(error))
    }

    @Synchronized
    fun onCancelled(encodedCancellation: ByteArray) = acceptNonTerminal {
        val cancellation = LuaRuntimeCodec.decodeCancellation(encodedCancellation)
        policy.onCancelled(cancellation)
        complete(Outcome.Cancelled(cancellation))
    }

    @Synchronized
    fun requestCancel(): Boolean = outcome == null && policy.requestCancel()

    @Synchronized
    fun transportViolation(error: Throwable) {
        if (outcome != null) return
        complete(Outcome.TransportViolation(error))
    }

    fun await(timeoutMillis: Long): Outcome? {
        require(timeoutMillis >= 0L)
        return if (terminalLatch.await(timeoutMillis, TimeUnit.MILLISECONDS)) outcome else null
    }

    @Synchronized
    fun close() {
        policy.close()
    }

    private inline fun <T> acceptNonTerminal(block: () -> T): T? {
        if (outcome != null) return null
        return try {
            block()
        } catch (error: Throwable) {
            complete(Outcome.TransportViolation(error))
            null
        }
    }

    private fun complete(value: Outcome) {
        if (outcome != null) return
        outcome = value
        terminalLatch.countDown()
    }
}
