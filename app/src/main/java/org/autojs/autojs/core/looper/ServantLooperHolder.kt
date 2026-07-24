package org.autojs.autojs.core.looper

import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinates one asynchronous servant-looper initialization across concurrent callers.
 *
 * Closing is terminal: current waiters are released immediately, and a value published after
 * closing is disposed exactly once.
 */
internal class ServantLooperHolder<T : Any>(
    private val starter: (publish: (T) -> Unit, fail: (Throwable) -> Unit) -> Unit,
    private val disposer: (T) -> Unit,
) {

    private val started = AtomicBoolean()
    private val completed = AtomicBoolean()
    private val closed = AtomicBoolean()
    private val disposed = AtomicBoolean()
    private val ready = CountDownLatch(1)

    @Volatile
    private var value: T? = null

    @Volatile
    private var failure: Throwable? = null

    fun get(): T {
        check(!closed.get()) { "Servant looper is unavailable after runtime recycling" }
        value?.let { return it }
        startIfNeeded()
        try {
            ready.await()
        } catch (e: InterruptedException) {
            throw ScriptInterruptedException(e)
        }
        failure?.let { throw IllegalStateException("Failed to initialize servant looper", it) }
        check(!closed.get()) { "Servant looper is unavailable after runtime recycling" }
        return checkNotNull(value) { "Servant looper initialization completed without a value" }
    }

    fun close() {
        closed.set(true)
        ready.countDown()
        disposeIfNeeded()
    }

    private fun startIfNeeded() {
        if (closed.get() || !started.compareAndSet(false, true)) return
        if (closed.get()) return
        try {
            starter(::publish, ::fail)
        } catch (t: Throwable) {
            fail(t)
        }
    }

    private fun publish(value: T) {
        if (!completed.compareAndSet(false, true)) return
        this.value = value
        ready.countDown()
        disposeIfNeeded()
    }

    private fun fail(t: Throwable) {
        if (!completed.compareAndSet(false, true)) return
        failure = t
        ready.countDown()
    }

    private fun disposeIfNeeded() {
        if (!closed.get()) return
        value?.let { if (disposed.compareAndSet(false, true)) disposer(it) }
    }

}
