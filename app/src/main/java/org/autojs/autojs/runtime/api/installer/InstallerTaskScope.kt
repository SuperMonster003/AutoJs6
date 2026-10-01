package org.autojs.autojs.runtime.api.installer

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.plugin.installer.InstallerErrorMapper
import org.autojs.autojs.core.plugin.installer.InstallerPluginException
import org.autojs.plugin.installer.api.InstallerContract
import org.autojs.plugin.installer.api.InstallerErrorCodes
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/** A script owns at most four running installer calls and a bounded waiting queue. */
internal class InstallerTaskScope(
    parentContext: CoroutineContext,
    maxRunning: Int = InstallerContract.MAX_CONCURRENT_SESSIONS,
    maxQueued: Int = InstallerContract.MAX_BATCH_SOURCES,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Closeable {

    private val job = SupervisorJob(parentContext[Job])
    private val scope = CoroutineScope(parentContext.minusKey(Job) + job + dispatcher)
    private val permits = Semaphore(maxRunning)
    private val maximum = maxRunning + maxQueued
    private val admitted = AtomicInteger()
    private val controls = AtomicInteger()
    private val closed = AtomicBoolean()

    val isClosed: Boolean get() = closed.get() || !job.isActive

    internal val pendingCount: Int get() = admitted.get()

    /** Reservation happens now; execution starts only after the caller has installed its listener. */
    fun <T> prepare(timeoutMillis: Long? = null, block: suspend () -> T): Deferred<T> {
        checkOpen()
        if (admitted.incrementAndGet() > maximum) {
            admitted.decrementAndGet()
            throw InstallerPluginException(InstallerErrorMapper.error(
                InstallerErrorCodes.INVALID_ARGUMENT,
                "the installer queue is full (at most $maximum pending calls per script)",
                retryable = true,
            ))
        }
        val task = scope.async(start = CoroutineStart.LAZY) {
            if (timeoutMillis == null) permits.withPermit { block() }
            else withTimeout(timeoutMillis) { permits.withPermit { block() } }
        }
        task.invokeOnCompletion { admitted.decrementAndGet() }
        if (isClosed) task.cancel()
        return task
    }

    /**
     * Cancellation may itself enter Binder (including CancellationSignal.cancel). Never run it on
     * the exiting script thread. Cleanup survives cancellation, but has its own bounded admission.
     */
    fun control(block: () -> Unit): Boolean {
        if (controls.incrementAndGet() > maximum * 2) {
            controls.decrementAndGet()
            return false
        }
        scope.launch(NonCancellable) {
            try {
                runCatching(block)
            } finally {
                controls.decrementAndGet()
            }
        }
        return true
    }

    fun checkOpen() {
        if (isClosed) throw InstallerPluginException(InstallerErrorMapper.error(
            InstallerErrorCodes.CANCELLED, "the script is exiting", retryable = false,
        ))
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) scope.cancel()
    }
}
