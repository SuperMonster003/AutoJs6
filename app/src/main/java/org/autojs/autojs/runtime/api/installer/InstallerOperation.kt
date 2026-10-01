package org.autojs.autojs.runtime.api.installer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import org.autojs.autojs.core.plugin.installer.InstallerError
import org.autojs.autojs.core.plugin.installer.InstallerErrorMapper
import org.autojs.autojs.core.plugin.installer.InstallerJson
import org.autojs.autojs.core.plugin.installer.InstallerPluginException
import org.autojs.autojs.core.plugin.installer.InstallerSessionListener
import org.autojs.autojs.core.plugin.installer.InstallerSessionOutcome
import org.autojs.plugin.installer.api.InstallerContract
import org.autojs.plugin.installer.api.InstallerErrorCodes
import java.io.Closeable
import java.util.Collections
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/** The small session port keeps the lifecycle controller testable without Android or Rhino. */
internal interface InstallerOperationSession : Closeable {
    suspend fun await(): InstallerSessionOutcome
    fun cancel(): Boolean
}

/** Owns local sources even while the remote session is still opening. */
internal interface InstallerOperationBackend : Closeable {
    suspend fun open(id: String, listener: InstallerSessionListener): InstallerOperationSession
    fun cancelOpening()
    fun complete(value: InstallerJson.Completion): InstallerJson.Completion = value
}

/**
 * Per-script installation controller. The id exists before start; snapshots and callbacks contain
 * only immutable host values. Short waits never cancel the operation. Its configured deadline,
 * explicit cancellation and script exit are the only local cancellation paths.
 */
class InstallerOperation internal constructor(
    val isBatch: Boolean,
    private val timeoutMillis: Long,
    private val authorizer: String,
    private val tasks: InstallerTaskScope,
    private val backend: InstallerOperationBackend,
    listener: InstallerSessionListener,
    private val onFinished: (InstallerOperation) -> Unit = {},
) : Closeable {

    val id: String = UUID.randomUUID().toString()
    private val lock = Any()
    private val outcome = CompletableDeferred<InstallerSessionOutcome>()
    private var listener: InstallerSessionListener? = listener
    private var observed: InstallerSessionOutcome? = null
    private var session: InstallerOperationSession? = null
    private var closed = false
    private var cancelRequested = false
    private var publishing = false

    @Volatile
    var state: String = InstallerContract.STAGE_PENDING
        private set

    @Volatile
    var result: InstallerJson.Completion? = null
        private set

    @Volatile
    var failure: InstallerError? = null
        private set

    val isTerminal: Boolean get() = outcome.isCompleted

    private val observer = object : InstallerSessionListener {
        override fun onStage(stage: String, detail: InstallerJson.StageDetail) = synchronized(lock) {
            if (!closed && observed == null && !publishing && !outcome.isCompleted) {
                // A terminal stage can arrive before its result document. Only change the
                // terminal snapshot together with that document.
                if (!InstallerContract.isTerminalStage(stage)) state = stage
                runCatching { this@InstallerOperation.listener?.onStage(stage, detail) }
            }
            Unit
        }

        override fun onProgress(progress: Float, detail: InstallerJson.ProgressDetail) = synchronized(lock) {
            if (!closed && observed == null && !publishing && !outcome.isCompleted) {
                runCatching { this@InstallerOperation.listener?.onProgress(progress, detail) }
            }
            Unit
        }

        override fun onCompleted(completion: InstallerJson.Completion) {
            observe(InstallerSessionOutcome.Completed(completion))
        }

        override fun onFailed(error: InstallerError) {
            observe(InstallerSessionOutcome.Failed(error))
        }
    }

    private val worker = tasks.prepare(timeoutMillis) { execute() }.also { job ->
        job.invokeOnCompletion { cause ->
            // Includes cancellation while queued, before execute() ever gets a permit.
            val terminal = synchronized(lock) { observed }
            if (terminal != null) publish(terminal)
            else if (cause != null) publish(InstallerSessionOutcome.Failed(error(cause)))
            runCatching { backend.close() }
            runCatching { onFinished(this) }
        }
    }

    fun start() {
        synchronized(lock) {
            if (!closed && !outcome.isCompleted) worker.start()
        }
    }

    /** Returns whether a new cancellation request was accepted, without a Binder call on this thread. */
    fun cancel(): Boolean {
        val active: InstallerOperationSession?
        synchronized(lock) {
            if (closed || cancelRequested || observed != null || publishing || outcome.isCompleted) return false
            cancelRequested = true
            active = session
        }
        if (active == null) {
            backend.cancelOpening()
            worker.cancel(CancellationException("installation cancelled"))
        } else {
            if (!tasks.control {
                if (synchronized(lock) { observed == null && !outcome.isCompleted }) active.cancel()
            }) {
                synchronized(lock) { cancelRequested = false }
                return false
            }
        }
        return true
    }

    /** A TIMEOUT here belongs to this wait only; a later wait can still receive the actual result. */
    suspend fun await(timeoutMillis: Long? = null): InstallerJson.Completion {
        if (timeoutMillis != null && timeoutMillis <= 0L) throw InstallerPluginException(
            InstallerErrorMapper.error(InstallerErrorCodes.INVALID_ARGUMENT, "wait timeout must be positive", retryable = false),
        )
        start()
        val terminal = if (timeoutMillis == null) outcome.await() else {
            withTimeoutOrNull(timeoutMillis) { outcome.await() } ?: throw InstallerPluginException(InstallerErrorMapper.error(
                InstallerErrorCodes.TIMEOUT,
                "waiting for installation exceeded $timeoutMillis ms; the session is still running",
                retryable = true,
            ))
        }
        return when (terminal) {
            is InstallerSessionOutcome.Completed -> terminal.completion
            is InstallerSessionOutcome.Failed -> throw InstallerPluginException(terminal.error)
        }
    }

    fun cancelSilently() = close()

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            listener = null
        }
        backend.cancelOpening()
        // Close local descriptors immediately, even if a remote open is blocked. The backend
        // rejects and closes descriptors/handles that arrive after this point.
        runCatching { backend.close() }
        worker.cancel(CancellationException("installation session closed"))
        // If a real terminal callback is already known, its IO worker publishes that result.
        // In particular, source deletion must not move onto the exiting script thread.
        if (synchronized(lock) { observed == null }) publish(InstallerSessionOutcome.Failed(cancelled()))
    }

    private suspend fun execute() {
        var opened: InstallerOperationSession? = null
        try {
            currentCoroutineContext().ensureActive()
            opened = backend.open(id, observer)
            synchronized(lock) { session = opened }
            currentCoroutineContext().ensureActive()
            if (synchronized(lock) { closed }) throw CancellationException("installation session closed")
            val terminal = synchronized(lock) { observed } ?: opened.await()
            observe(terminal)
            publish(terminal)
        } catch (cause: Throwable) {
            // Publish before close(), whose host fallback can synthesize CANCELLED. A local
            // deadline remains TIMEOUT; an already observed real result still wins the race.
            publish(synchronized(lock) { observed } ?: InstallerSessionOutcome.Failed(error(cause)))
            throw cause
        } finally {
            opened?.let { handle ->
                if (synchronized(lock) { observed == null }) runCatching { handle.cancel() }
                runCatching { handle.close() }
            }
            synchronized(lock) { session = null }
        }
    }

    private fun observe(value: InstallerSessionOutcome) = synchronized(lock) {
        if (observed == null && !outcome.isCompleted) observed = when (value) {
            is InstallerSessionOutcome.Completed -> InstallerSessionOutcome.Completed(snapshot(value.completion))
            is InstallerSessionOutcome.Failed -> value
        }
    }

    private fun publish(value: InstallerSessionOutcome) {
        val actual = synchronized(lock) {
            if (publishing || outcome.isCompleted) return
            publishing = true
            observed ?: value
        }
        val finalValue = when (actual) {
            is InstallerSessionOutcome.Completed -> {
                // The operation backend performs D25 deletion here on the worker thread, before
                // the terminal listener or any waiter can see the result.
                val completed = runCatching { backend.complete(actual.completion) }.getOrDefault(actual.completion)
                InstallerSessionOutcome.Completed(snapshot(completed))
            }
            is InstallerSessionOutcome.Failed -> InstallerSessionOutcome.Failed(decorate(actual.error))
        }
        synchronized(lock) {
            when (finalValue) {
                is InstallerSessionOutcome.Completed -> {
                    result = finalValue.completion
                    state = InstallerContract.STAGE_COMPLETED
                    if (!closed) runCatching { listener?.onCompleted(finalValue.completion) }
                }
                is InstallerSessionOutcome.Failed -> {
                    failure = finalValue.error
                    state = if (finalValue.error.code == InstallerErrorCodes.CANCELLED || finalValue.error.code == InstallerErrorCodes.USER_CANCELLED) {
                        InstallerContract.STAGE_CANCELLED
                    } else InstallerContract.STAGE_FAILED
                    if (!closed) runCatching { listener?.onFailed(finalValue.error) }
                }
            }
            listener = null
            // Listener return means the script dispatcher has enqueued its terminal event.
            outcome.complete(finalValue)
        }
    }

    private fun decorate(error: InstallerError): InstallerError =
        if (error.authorizer == null && authorizer != InstallerContract.AUTHORIZER_AUTO) error.copy(authorizer = authorizer) else error

    private fun error(cause: Throwable): InstallerError = if (cause is TimeoutCancellationException) {
        InstallerErrorMapper.error(InstallerErrorCodes.TIMEOUT, "installation timed out after $timeoutMillis ms", retryable = true)
    } else InstallerErrorMapper.fromThrowable(cause, InstallerErrorMapper.Phase.SESSION)

    private fun cancelled(): InstallerError = InstallerErrorMapper.error(
        InstallerErrorCodes.CANCELLED, "installation session closed", retryable = false,
    )

    private fun snapshot(value: InstallerJson.Completion): InstallerJson.Completion = value.copy(
        results = Collections.unmodifiableList(value.results.map { result ->
            result.copy(notes = Collections.unmodifiableList(result.notes.toList()))
        }),
    )
}
