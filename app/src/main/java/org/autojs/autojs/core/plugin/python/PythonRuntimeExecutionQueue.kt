package org.autojs.autojs.core.plugin.python

import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Process-local admission queue for the one-session CPython provider.
 *
 * Every production Python entry point converges on the Python script engine, so serializing before
 * provider discovery keeps queued executions from holding Binder descriptors, foreground services,
 * interactive controllers, or a provider start lease. The Plugin's own single-session gate remains
 * the authoritative cross-process safety boundary.
 */
internal object PythonRuntimeExecutionCoordinator {
    private const val MAX_PENDING_EXECUTIONS = 32
    private val queue = PythonRuntimeExecutionQueue(MAX_PENDING_EXECUTIONS)

    @Throws(InterruptedException::class)
    fun acquire(): PythonRuntimeExecutionQueue.Lease = queue.acquire()

    /** For the memory gate (docs/memory-phase2/ROADMAP.md T1 (h)). */
    fun snapshot(): PythonRuntimeExecutionQueue.Snapshot = queue.snapshot()
}

/** Fair, bounded and interruptible single-owner queue with no Android dependencies. */
internal class PythonRuntimeExecutionQueue(
    private val maxPendingExecutions: Int,
) {
    private val lock = ReentrantLock(true)
    private val changed = lock.newCondition()
    private val waiters = ArrayDeque<Long>()
    private var active = false
    private var nextTicket = 1L

    init {
        require(maxPendingExecutions > 0) { "Python pending execution capacity must be positive" }
    }

    @Throws(InterruptedException::class)
    fun acquire(): Lease {
        lock.lockInterruptibly()
        var ticket: Long? = null
        try {
            if (!active && waiters.isEmpty()) {
                active = true
                return Lease(::release)
            }
            if (waiters.size >= maxPendingExecutions) {
                throw PythonRuntimeHostException(
                    PythonRuntimeHostError.BUSY,
                    "Python execution queue already has $maxPendingExecutions pending scripts",
                )
            }

            ticket = nextTicket++
            waiters.addLast(ticket)
            while (active || waiters.peekFirst() != ticket) changed.await()
            check(waiters.removeFirst() == ticket) { "Python execution queue lost FIFO ownership" }
            ticket = null
            active = true
            return Lease(::release)
        } catch (error: Throwable) {
            ticket?.let { queued ->
                if (waiters.remove(queued)) changed.signalAll()
            }
            throw error
        } finally {
            lock.unlock()
        }
    }

    fun snapshot(): Snapshot = lock.withLock {
        Snapshot(active = active, pendingExecutions = waiters.size)
    }

    private fun release() = lock.withLock {
        check(active) { "Python execution queue released without an active owner" }
        active = false
        changed.signalAll()
    }

    data class Snapshot(
        val active: Boolean,
        val pendingExecutions: Int,
    )

    class Lease internal constructor(
        private val release: () -> Unit,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) release()
        }
    }
}
