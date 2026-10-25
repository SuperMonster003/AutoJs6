package org.autojs.autojs.runtime.api.modules.installer

import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.modules.ScriptAsyncDispatcher
import org.autojs.autojs.runtime.api.modules.toJsPromise
import org.autojs.plugin.installer.api.InstallerErrorCodes
import org.mozilla.javascript.Scriptable
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Worker outcomes are plain values; Promise settlement and result decoration only run on the origin thread. */
internal object InstallerPromises {
    private val dispatches = object : ThreadLocal<MutableSet<InstallerDispatch>>() {
        override fun initialValue(): MutableSet<InstallerDispatch> = ConcurrentHashMap.newKeySet()
    }

    fun dispatch(runtime: ScriptRuntime, onClose: () -> Unit = {}): InstallerDispatch =
        InstallerDispatch(runtime, requireNotNull(dispatches.get()), onClose)

    fun launch(runtime: ScriptRuntime, inspect: Boolean = false, block: suspend () -> Any?): Scriptable {
        val adapter = ScriptPromiseAdapter()
        val promise = adapter.toJsPromise(runtime) as Scriptable
        var job: Job? = null
        val delivery = dispatch(runtime) { job?.cancel() }
        val launched = runtime.coroutineScope.launch {
            val outcome = runCatching { block() }
            deliver(runtime, delivery, adapter, outcome, inspect)
        }
        job = launched
        launched.invokeOnCompletion { cause ->
            // A coroutine cancelled before it started never executes its body.
            if (cause != null && !delivery.dispatcher.isTerminal) {
                if (runtime.isExiting || runtime.isStopped) delivery.close()
                else deliver(runtime, delivery, adapter, Result.failure(cause), inspect)
            }
        }
        if (!delivery.active) launched.cancel()
        return promise
    }

    fun rejected(runtime: ScriptRuntime, failure: Throwable): Scriptable {
        val adapter = ScriptPromiseAdapter()
        val promise = adapter.toJsPromise(runtime) as Scriptable
        deliver(runtime, dispatch(runtime), adapter, Result.failure(failure), inspect = false)
        return promise
    }

    private fun deliver(runtime: ScriptRuntime, delivery: InstallerDispatch, adapter: ScriptPromiseAdapter, outcome: Result<Any?>, inspect: Boolean) {
        if (!delivery.active) {
            delivery.close()
            return
        }
        try {
            outcome.fold(
                onSuccess = { plain -> delivery.dispatcher.terminalValues(arrayOf(plain)) { values ->
                    try {
                        if (delivery.active) {
                            val value = try { InstallerJsResults.decorate(runtime, values[0], inspect) } catch (failure: Throwable) {
                                adapter.reject(InstallerJsErrors.toJs(runtime, failure))
                                return@terminalValues
                            }
                            adapter.resolve(value)
                        }
                    } finally { delivery.finish() }
                } },
                onFailure = { failure -> delivery.dispatcher.terminalValues { _ ->
                    try {
                        if (delivery.active) adapter.reject(InstallerJsErrors.toJs(runtime, failure))
                    } finally { delivery.finish() }
                } },
            )
        } catch (_: Throwable) {
            // The originating looper no longer accepts work.
            delivery.close()
        }
    }

    /** A condition wait permits this thread's installer events and microtasks to run, including inside session.wait. */
    fun <T> sync(runtime: ScriptRuntime, block: suspend () -> T): T {
        val looper = Looper.myLooper()
        if (looper != null && looper == Looper.getMainLooper()) {
            throw InstallerJsErrors.failure(InstallerErrorCodes.INVALID_ARGUMENT, "synchronous installer methods cannot block the UI thread; use the Async form")
        }
        val wait = InstallerBlockingWait<T>()
        val job = runtime.coroutineScope.launch { wait.complete(runCatching { block() }) }
        job.invokeOnCompletion { cause ->
            if (cause != null) wait.complete(Result.failure(cause))
        }
        try {
            return wait.await(
                stopped = { runtime.isExiting || runtime.isStopped },
                pump = { drainCurrentThread(runtime) },
            )
        } catch (interrupted: InterruptedException) {
            // Condition.await clears the flag. Preserve force-stop so a JS catch cannot revive the script.
            Thread.currentThread().interrupt()
            throw interrupted
        } finally {
            job.cancel()
        }
    }

    private fun drainCurrentThread(runtime: ScriptRuntime) {
        // The set itself was captured in a ThreadLocal; dispatchers also enforce thread identity.
        requireNotNull(dispatches.get()).toList().filter { it.runtime === runtime && it.active }.forEach { it.dispatcher.drainOnOriginatingThread() }
    }
}

/** Tracks queued work until delivery, so exit suppresses even a terminal callback already queued by Binder. */
internal class InstallerDispatch(
    val runtime: ScriptRuntime,
    private val ownerDispatches: MutableSet<InstallerDispatch>,
    private val onClose: () -> Unit,
) : Closeable {
    val dispatcher = ScriptAsyncDispatcher(runtime)
    private val released = AtomicBoolean()

    val active: Boolean get() = !released.get() && !runtime.isExiting && !runtime.isStopped

    init {
        ownerDispatches += this
        runtime.closeableManager.add(this)
        if (runtime.isExiting || runtime.isStopped) close()
    }

    fun finish() {
        if (!released.compareAndSet(false, true)) return
        ownerDispatches -= this
        runtime.closeableManager.remove(this)
    }

    override fun close() {
        if (!released.compareAndSet(false, true)) return
        try {
            dispatcher.cancelSilently()
            onClose()
        } finally {
            ownerDispatches -= this
            runtime.closeableManager.remove(this)
        }
    }
}

/** Pure condition-wait seam: never executes callbacks on a worker or while holding the condition lock. */
internal class InstallerBlockingWait<T> {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private var outcome: Result<T>? = null

    fun complete(result: Result<T>) = lock.withLock {
        if (outcome == null) {
            outcome = result
            changed.signalAll()
        }
    }

    fun await(stopped: () -> Boolean = { false }, pump: () -> Unit): T {
        while (true) {
            if (stopped()) throw CancellationException("installer script stopped")
            pump()
            if (stopped()) throw CancellationException("installer script stopped")
            val ready = lock.withLock { outcome }
            if (ready != null) {
                // A service signals only after its terminal listener has enqueued the final event.
                pump()
                if (stopped()) throw CancellationException("installer script stopped")
                return ready.getOrThrow()
            }
            lock.withLock { if (outcome == null) changed.await(10, TimeUnit.MILLISECONDS) }
        }
    }
}
