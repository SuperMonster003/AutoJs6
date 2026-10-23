package org.autojs.autojs.core.plugin.lua

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport

internal enum class LuaBinderCallFailure {
    BUSY,
    STALLED,
    TIMEOUT,
    POISONED,
    CLOSED,
}

internal class LuaBinderCallException(
    val failure: LuaBinderCallFailure,
    operationName: String,
) : IllegalStateException(
    when (failure) {
        LuaBinderCallFailure.POISONED ->
            "Lua Binder call $operationName is disabled until the host process restarts after an uncertain dispatch"
        else -> "Lua Binder call $operationName failed: $failure"
    },
)

/**
 * Process-local lane for the V1 synchronous plugin Binder calls.
 *
 * It has one daemon worker and no backlog: while one call is executing, every later call fails
 * closed instead of queuing or allocating another worker. A timeout or caller interruption marks
 * the in-flight call abandoned, but deliberately does not pretend to cancel a Binder transaction.
 * The lane remains saturated until the original call returns and any late result is disposed.
 */
internal class LuaBoundedBinderCallLane(
    threadNamePrefix: String = DEFAULT_THREAD_NAME_PREFIX,
) : AutoCloseable {
    private val permit = Semaphore(1)
    private val pending = AtomicReference<Call<*>?>()
    private val active = AtomicReference<Call<*>?>()
    private val closed = AtomicBoolean(false)
    private val poisoned = AtomicBoolean(false)
    private val worker = Thread(::workerLoop, "$threadNamePrefix-${THREAD_SEQUENCE.incrementAndGet()}").apply {
        isDaemon = true
        start()
    }

    fun <T> call(
        operationName: String,
        timeoutMillis: Long,
        onAbandoned: () -> Unit = {},
        lateResultDisposer: (T) -> Unit = {},
        operation: () -> T,
    ): T {
        require(operationName.matches(OPERATION_NAME_PATTERN)) { "Invalid Lua Binder operation name" }
        require(timeoutMillis in 1L..MAX_TIMEOUT_MILLIS) { "Lua Binder timeout is outside the host limit" }
        if (closed.get()) throw LuaBinderCallException(LuaBinderCallFailure.CLOSED, operationName)
        if (poisoned.get()) throw LuaBinderCallException(LuaBinderCallFailure.POISONED, operationName)
        if (!permit.tryAcquire()) {
            val failure = if (active.get()?.isAbandoned() == true) {
                LuaBinderCallFailure.STALLED
            } else {
                LuaBinderCallFailure.BUSY
            }
            throw LuaBinderCallException(failure, operationName)
        }

        val call = Call(operationName, onAbandoned, lateResultDisposer, operation)
        check(active.compareAndSet(null, call)) { "Lua Binder lane ownership is inconsistent" }
        check(pending.compareAndSet(null, call)) { "Lua Binder lane handoff is inconsistent" }
        LockSupport.unpark(worker)

        try {
            if (!call.completion.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                return claimPublishedOrAbandon(call, LuaBinderCallFailure.TIMEOUT)
            }
            return claimPublished(call)
        } catch (interrupted: InterruptedException) {
            abandonAndDisposePublished(call)
            Thread.currentThread().interrupt()
            throw interrupted
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        LockSupport.unpark(worker)
    }

    /** Permanently rejects later calls; an uncertain create-execution dispatch is never reused. */
    fun poison() {
        poisoned.set(true)
    }

    /** Calls holding the lane (0 or 1), for the memory gate (docs/memory-phase2/ROADMAP.md T1 (h)). */
    fun inFlightCalls(): Int = if (active.get() == null) 0 else 1

    private fun workerLoop() {
        while (true) {
            val call = pending.getAndSet(null)
            if (call == null) {
                if (closed.get() && active.get() == null) return
                LockSupport.park(this)
                Thread.interrupted()
                continue
            }
            executeUnchecked(call)
            Thread.interrupted()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun executeUnchecked(value: Call<*>) {
        execute(value as Call<Any?>)
    }

    private fun <T> execute(call: Call<T>) {
        if (!call.state.compareAndSet(CallState.NEW, CallState.RUNNING)) {
            check(call.state.get() == CallState.ABANDONED)
            finishAbandoned(call, null)
            return
        }

        val outcome = try {
            CallOutcome.Success(call.operation())
        } catch (failure: Throwable) {
            CallOutcome.Failure(failure)
        }
        call.outcome = outcome
        if (call.state.compareAndSet(CallState.RUNNING, CallState.PUBLISHED)) {
            call.completion.countDown()
            awaitCallerDisposition(call, outcome)
        } else {
            check(call.state.get() == CallState.ABANDONED)
            finishAbandoned(call, outcome)
        }
    }

    private fun <T> awaitCallerDisposition(call: Call<T>, outcome: CallOutcome<T>) {
        while (true) {
            when (call.state.get()) {
                CallState.CLAIMED -> return
                CallState.ABANDONED -> {
                    finishAbandoned(call, outcome)
                    return
                }
                CallState.PUBLISHED -> {
                    LockSupport.park(call)
                    Thread.interrupted()
                }
                else -> error("Lua Binder caller disposition is inconsistent")
            }
        }
    }

    private fun <T> finishAbandoned(call: Call<T>, outcome: CallOutcome<T>?) {
        if (outcome is CallOutcome.Success) runCatching { call.lateResultDisposer(outcome.value) }
        call.state.set(CallState.DRAINED)
        call.completion.countDown()
        release(call)
    }

    private fun release(call: Call<*>) {
        check(active.compareAndSet(call, null)) { "Lua Binder lane released an unexpected call" }
        permit.release()
        if (closed.get()) LockSupport.unpark(worker)
    }

    private fun <T> claimPublishedOrAbandon(call: Call<T>, failure: LuaBinderCallFailure): T {
        while (true) {
            when (val state = call.state.get()) {
                CallState.PUBLISHED -> return claimPublished(call)
                CallState.NEW,
                CallState.RUNNING,
                -> if (call.state.compareAndSet(state, CallState.ABANDONED)) {
                    call.notifyAbandoned()
                    throw LuaBinderCallException(failure, call.operationName)
                }
                CallState.ABANDONED,
                CallState.DRAINED,
                -> throw LuaBinderCallException(failure, call.operationName)
                CallState.CLAIMED -> error("Lua Binder call result was already claimed")
            }
        }
    }

    private fun <T> claimPublished(call: Call<T>): T {
        check(call.state.compareAndSet(CallState.PUBLISHED, CallState.CLAIMED)) {
            "Lua Binder call result is not available"
        }
        release(call)
        LockSupport.unpark(worker)
        return when (val outcome = checkNotNull(call.outcome)) {
            is CallOutcome.Success -> outcome.value
            is CallOutcome.Failure -> throw outcome.failure
        }
    }

    private fun <T> abandonAndDisposePublished(call: Call<T>) {
        while (true) {
            when (val state = call.state.get()) {
                CallState.NEW,
                CallState.RUNNING,
                -> if (call.state.compareAndSet(state, CallState.ABANDONED)) {
                    call.notifyAbandoned()
                    return
                }
                CallState.PUBLISHED -> if (call.state.compareAndSet(CallState.PUBLISHED, CallState.ABANDONED)) {
                    call.notifyAbandoned()
                    LockSupport.unpark(worker)
                    return
                }
                CallState.ABANDONED,
                CallState.DRAINED,
                CallState.CLAIMED,
                -> return
            }
        }
    }

    private class Call<T>(
        val operationName: String,
        val onAbandoned: () -> Unit,
        val lateResultDisposer: (T) -> Unit,
        val operation: () -> T,
    ) {
        val state = AtomicReference(CallState.NEW)
        val completion = CountDownLatch(1)
        private val abandonmentNotified = AtomicBoolean(false)

        @Volatile
        var outcome: CallOutcome<T>? = null

        fun isAbandoned(): Boolean = state.get() == CallState.ABANDONED

        fun notifyAbandoned() {
            if (abandonmentNotified.compareAndSet(false, true)) runCatching(onAbandoned)
        }
    }

    private enum class CallState {
        NEW,
        RUNNING,
        PUBLISHED,
        CLAIMED,
        ABANDONED,
        DRAINED,
    }

    private sealed class CallOutcome<out T> {
        data class Success<T>(val value: T) : CallOutcome<T>()
        data class Failure(val failure: Throwable) : CallOutcome<Nothing>()
    }

    private companion object {
        const val DEFAULT_THREAD_NAME_PREFIX = "autojs-lua-binder"
        const val MAX_TIMEOUT_MILLIS = 60_000L
        val OPERATION_NAME_PATTERN = Regex("[a-z0-9.-]{1,48}")
        val THREAD_SEQUENCE = AtomicLong()
    }
}

/** One process-wide fuse: a wedged V1 call cannot cause replacement workers or an IPC backlog. */
internal object LuaRuntimeBinderCallLane {
    private val lane = LuaBoundedBinderCallLane()

    fun <T> call(
        operationName: String,
        timeoutMillis: Long,
        onAbandoned: () -> Unit = {},
        lateResultDisposer: (T) -> Unit = {},
        operation: () -> T,
    ): T = lane.call(operationName, timeoutMillis, onAbandoned, lateResultDisposer, operation)

    fun poison() = lane.poison()

    fun inFlightCalls(): Int = lane.inFlightCalls()
}
