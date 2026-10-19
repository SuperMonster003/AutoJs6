package org.autojs.autojs.core.plugin.bun

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.locks.ReentrantLock

internal enum class BunBinderCallFailure {
    BUSY,
    STALLED,
    TIMEOUT,
    POISONED,
}

internal class BunBinderCallException(
    val failure: BunBinderCallFailure,
    operationName: String,
) : IllegalStateException(
    if (failure == BunBinderCallFailure.POISONED) {
        "Bun Binder call $operationName is disabled until the host process restarts after an uncertain dispatch"
    } else {
        "Bun Binder call $operationName failed: $failure"
    },
)

/** A single daemon Binder worker with no queue and no replacement-worker growth after a stall. */
internal class BunBoundedBinderCallLane(
    threadNamePrefix: String,
) {
    private val permit = Semaphore(1)
    private val pending = AtomicReference<Call<*>?>()
    private val active = AtomicReference<Call<*>?>()
    private val poisoned = AtomicBoolean(false)
    private val worker = Thread(::workerLoop, "$threadNamePrefix-${THREAD_SEQUENCE.incrementAndGet()}").apply {
        isDaemon = true
        start()
    }

    fun <T> call(
        operationName: String,
        timeoutMillis: Long,
        onAbandoned: () -> Unit = {},
        operation: () -> T,
    ): T {
        require(operationName.matches(OPERATION_NAME_PATTERN)) { "Invalid Bun Binder operation name" }
        require(timeoutMillis in 1L..MAX_TIMEOUT_MILLIS) { "Bun Binder timeout is outside the host limit" }
        if (poisoned.get()) throw BunBinderCallException(BunBinderCallFailure.POISONED, operationName)
        if (!permit.tryAcquire()) {
            val failure = if (active.get()?.isAbandoned() == true) {
                BunBinderCallFailure.STALLED
            } else {
                BunBinderCallFailure.BUSY
            }
            throw BunBinderCallException(failure, operationName)
        }

        val call = Call(operationName, onAbandoned, detached = false, operation = operation)
        check(active.compareAndSet(null, call)) { "Bun Binder lane ownership is inconsistent" }
        check(pending.compareAndSet(null, call)) { "Bun Binder lane handoff is inconsistent" }
        LockSupport.unpark(worker)

        try {
            if (!call.completion.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                return claimPublishedOrAbandon(call, BunBinderCallFailure.TIMEOUT)
            }
            return claimPublished(call)
        } catch (error: InterruptedException) {
            abandonPublishedOrRunning(call)
            Thread.currentThread().interrupt()
            throw error
        }
    }

    fun poison() {
        poisoned.set(true)
    }

    fun tryDispatch(operationName: String, operation: () -> Unit): Boolean {
        require(operationName.matches(OPERATION_NAME_PATTERN)) { "Invalid Bun Binder operation name" }
        if (poisoned.get() || !permit.tryAcquire()) return false
        val call = Call(operationName, onAbandoned = {}, detached = true, operation = operation)
        check(active.compareAndSet(null, call)) { "Bun Binder lane ownership is inconsistent" }
        check(pending.compareAndSet(null, call)) { "Bun Binder lane handoff is inconsistent" }
        LockSupport.unpark(worker)
        return true
    }

    private fun workerLoop() {
        while (true) {
            val call = pending.getAndSet(null)
            if (call == null) {
                LockSupport.park(this)
                Thread.interrupted()
                continue
            }
            executeUnchecked(call)
            Thread.interrupted()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun executeUnchecked(call: Call<*>) = execute(call as Call<Any?>)

    private fun <T> execute(call: Call<T>) {
        if (!call.state.compareAndSet(CallState.NEW, CallState.RUNNING)) {
            check(call.state.get() == CallState.ABANDONED)
            finishAbandoned(call)
            return
        }
        call.outcome = try {
            CallOutcome.Success(call.operation())
        } catch (error: Throwable) {
            CallOutcome.Failure(error)
        }
        if (call.detached) {
            call.state.set(CallState.DRAINED)
            release(call)
            return
        }
        if (call.state.compareAndSet(CallState.RUNNING, CallState.PUBLISHED)) {
            call.completion.countDown()
            while (true) {
                when (call.state.get()) {
                    CallState.CLAIMED -> return
                    CallState.ABANDONED -> {
                        finishAbandoned(call)
                        return
                    }
                    CallState.PUBLISHED -> {
                        LockSupport.park(call)
                        Thread.interrupted()
                    }
                    else -> error("Bun Binder caller disposition is inconsistent")
                }
            }
        }
        check(call.state.get() == CallState.ABANDONED)
        finishAbandoned(call)
    }

    private fun finishAbandoned(call: Call<*>) {
        call.state.set(CallState.DRAINED)
        call.completion.countDown()
        release(call)
    }

    private fun release(call: Call<*>) {
        check(active.compareAndSet(call, null)) { "Bun Binder lane released an unexpected call" }
        permit.release()
    }

    private fun <T> claimPublishedOrAbandon(call: Call<T>, failure: BunBinderCallFailure): T {
        while (true) {
            when (val state = call.state.get()) {
                CallState.PUBLISHED -> return claimPublished(call)
                CallState.NEW, CallState.RUNNING -> if (call.state.compareAndSet(state, CallState.ABANDONED)) {
                    call.notifyAbandoned()
                    throw BunBinderCallException(failure, call.operationName)
                }
                CallState.ABANDONED, CallState.DRAINED -> throw BunBinderCallException(failure, call.operationName)
                CallState.CLAIMED -> error("Bun Binder call result was already claimed")
            }
        }
    }

    private fun <T> claimPublished(call: Call<T>): T {
        check(call.state.compareAndSet(CallState.PUBLISHED, CallState.CLAIMED)) {
            "Bun Binder call result is not available"
        }
        release(call)
        LockSupport.unpark(worker)
        return when (val outcome = checkNotNull(call.outcome)) {
            is CallOutcome.Success -> outcome.value
            is CallOutcome.Failure -> throw outcome.failure
        }
    }

    private fun abandonPublishedOrRunning(call: Call<*>) {
        while (true) {
            when (val state = call.state.get()) {
                CallState.NEW, CallState.RUNNING -> if (call.state.compareAndSet(state, CallState.ABANDONED)) {
                    call.notifyAbandoned()
                    return
                }
                CallState.PUBLISHED -> if (call.state.compareAndSet(CallState.PUBLISHED, CallState.ABANDONED)) {
                    call.notifyAbandoned()
                    LockSupport.unpark(worker)
                    return
                }
                CallState.ABANDONED, CallState.DRAINED, CallState.CLAIMED -> return
            }
        }
    }

    private class Call<T>(
        val operationName: String,
        private val onAbandoned: () -> Unit,
        val detached: Boolean,
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

    private enum class CallState { NEW, RUNNING, PUBLISHED, CLAIMED, ABANDONED, DRAINED }

    private sealed class CallOutcome<out T> {
        data class Success<T>(val value: T) : CallOutcome<T>()
        data class Failure(val failure: Throwable) : CallOutcome<Nothing>()
    }

    private companion object {
        const val MAX_TIMEOUT_MILLIS = 15L * 60L * 1000L
        val OPERATION_NAME_PATTERN = Regex("[a-z0-9.-]{1,48}")
        val THREAD_SEQUENCE = AtomicLong()
    }
}

/** Waits before dispatch without occupying the Binder worker or poisoning an unsubmitted call. */
internal class BunQueuedBinderCallLane(threadNamePrefix: String) {
    private val admission = ReentrantLock(true)
    private val lane = BunBoundedBinderCallLane(threadNamePrefix)

    fun <T> call(
        operationName: String,
        timeoutMillis: Long,
        onAbandoned: () -> Unit = {},
        isCancelled: () -> Boolean = { false },
        operation: () -> T,
    ): T {
        require(timeoutMillis in 1L..15L * 60L * 1000L)
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (true) {
            if (isCancelled()) throw InterruptedException("Bun call cancelled before dispatch")
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) throw BunBinderCallException(BunBinderCallFailure.TIMEOUT, operationName)
            if (admission.tryLock(minOf(remaining, TimeUnit.MILLISECONDS.toNanos(50)), TimeUnit.NANOSECONDS)) break
        }
        try {
            if (isCancelled()) throw InterruptedException("Bun call cancelled before dispatch")
            val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
            if (remaining <= 0) throw BunBinderCallException(BunBinderCallFailure.TIMEOUT, operationName)
            return lane.call(operationName, remaining, onAbandoned, operation)
        } finally {
            admission.unlock()
        }
    }

    fun poison() = lane.poison()
}

/** Main lane is permanently poisoned only after an uncertain synchronous dispatch. */
internal object BunRuntimeBinderCallLane {
    private val lane = BunQueuedBinderCallLane("autojs-bun-binder")

    fun <T> call(
        operationName: String,
        timeoutMillis: Long,
        onAbandoned: () -> Unit = {},
        isCancelled: () -> Boolean = { false },
        operation: () -> T,
    ): T = lane.call(operationName, timeoutMillis, onAbandoned, isCancelled, operation)

    fun poison() = lane.poison()
}

/** Cleanup uses a distinct bounded lane so cancellation is still possible if the main lane stalls. */
internal object BunRuntimeCleanupBinderCallLane {
    private val lane = BunBoundedBinderCallLane("autojs-bun-cleanup-binder")

    fun tryDispatch(operationName: String, operation: () -> Unit): Boolean =
        lane.tryDispatch(operationName, operation)
}
