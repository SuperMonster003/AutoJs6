package org.autojs.autojs.core.plugin.installer

import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.autojs.plugin.installer.api.IInstallerSession
import org.autojs.plugin.installer.api.IInstallerSessionCallback
import org.autojs.plugin.installer.api.InstallerContract
import org.autojs.plugin.installer.api.InstallerErrorCodes
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/** What the host observes while a session runs; every method is called on a Binder thread. */
interface InstallerSessionListener {
    fun onStage(stage: String, detail: InstallerJson.StageDetail) = Unit
    fun onProgress(progress: Float, detail: InstallerJson.ProgressDetail) = Unit
    fun onCompleted(completion: InstallerJson.Completion) = Unit
    fun onFailed(error: InstallerError) = Unit
}

/** Terminal outcome of a session: a completion document, or the error that ended it. */
sealed interface InstallerSessionOutcome {
    data class Completed(val completion: InstallerJson.Completion) : InstallerSessionOutcome
    data class Failed(val error: InstallerError) : InstallerSessionOutcome
}

/**
 * The callback Binder the host registers with `openSession`. It settles [outcome] exactly once,
 * forwards every notification to the listener and ignores events of other session ids.
 */
internal class InstallerSessionCallbackBinder(
    private val hostSessionId: String,
    private val listener: InstallerSessionListener,
) : IInstallerSessionCallback.Stub() {

    val outcome = CompletableDeferred<InstallerSessionOutcome>()

    @Volatile
    var pluginSessionId: String? = null

    private fun accepts(sessionId: String?): Boolean = sessionId == null || sessionId == pluginSessionId || sessionId == hostSessionId

    override fun onStage(sessionId: String?, stage: String?, detail: Bundle?) {
        if (!accepts(sessionId) || stage == null || !InstallerContract.isStage(stage)) return
        runCatching { listener.onStage(stage, InstallerJson.decodeStageDetail(InstallerBundles.string(detail, InstallerContract.KEY_DETAIL_JSON))) }
            .onFailure { Log.w(TAG, "session listener failed on stage $stage", it) }
    }

    override fun onProgress(sessionId: String?, progress: Float, detail: Bundle?) {
        if (!accepts(sessionId)) return
        val clamped = progress.coerceIn(0f, 1f)
        runCatching { listener.onProgress(clamped, InstallerJson.decodeProgressDetail(InstallerBundles.string(detail, InstallerContract.KEY_DETAIL_JSON))) }
            .onFailure { Log.w(TAG, "session listener failed on progress", it) }
    }

    override fun onCompleted(sessionId: String?, result: Bundle?) {
        if (!accepts(sessionId)) return
        val outcomeValue = runCatching {
            InstallerSessionOutcome.Completed(InstallerJson.decodeCompletion(InstallerBundles.string(result, InstallerContract.KEY_RESULT_JSON)))
        }.getOrElse { failure ->
            InstallerSessionOutcome.Failed(InstallerErrorMapper.error(InstallerErrorCodes.INTERNAL, "installer plugin sent an unreadable result: ${failure.message}", retryable = false))
        }
        settle(outcomeValue)
    }

    override fun onFailed(sessionId: String?, error: Bundle?) {
        if (!accepts(sessionId)) return
        settle(InstallerSessionOutcome.Failed(InstallerJson.decodeError(InstallerBundles.string(error, InstallerContract.KEY_ERROR_JSON))))
    }

    fun settle(value: InstallerSessionOutcome) {
        if (!outcome.complete(value)) return
        runCatching {
            when (value) {
                is InstallerSessionOutcome.Completed -> listener.onCompleted(value.completion)
                is InstallerSessionOutcome.Failed -> listener.onFailed(value.error)
            }
        }.onFailure { Log.w(TAG, "session listener failed on terminal outcome", it) }
    }

    private companion object {
        const val TAG = "InstallerSessionCallback"
    }
}

/**
 * One open installation session: the plugin-side handle, the binding lease that keeps the plugin
 * bound and the host descriptors that must outlive the session. [await] suspends until the
 * terminal callback; [cancel] asks the plugin to stop; [close] releases everything once.
 */
class InstallerSessionHandle internal constructor(
    val hostSessionId: String,
    private val session: IInstallerSession,
    private val bindingLease: Closeable,
    private val callback: InstallerSessionCallbackBinder,
    private val sources: List<InstallerSource>,
    private val timeoutMillis: Long,
) : Closeable {

    private val closed = AtomicBoolean(false)

    private val deathRecipient = IBinder.DeathRecipient {
        callback.settle(InstallerSessionOutcome.Failed(InstallerErrorMapper.error(InstallerErrorCodes.PLUGIN_UNAVAILABLE, "installer plugin process died", retryable = true)))
    }

    init {
        runCatching { session.asBinder().linkToDeath(deathRecipient, 0) }
            .onFailure { deathRecipient.binderDied() }
    }

    val pluginSessionId: String? get() = callback.pluginSessionId

    val isTerminal: Boolean get() = callback.outcome.isCompleted

    /** The plugin's view of the session; `null` when the plugin can no longer be reached. */
    fun status(): InstallerJson.SessionStatus? = runCatching {
        InstallerJson.decodeSessionStatus(InstallerBundles.string(session.getStatus(), InstallerContract.KEY_STATUS_JSON))
    }.getOrNull()

    /** Waits for the terminal outcome, at most [timeoutMillis]; a timeout cancels the session and reports `TIMEOUT`. */
    suspend fun await(): InstallerSessionOutcome {
        return try {
            withTimeout(timeoutMillis) { callback.outcome.await() }
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            val failure = InstallerSessionOutcome.Failed(InstallerErrorMapper.error(InstallerErrorCodes.TIMEOUT, "installation timed out after $timeoutMillis ms", retryable = true))
            // A real callback may already have won while this waiter was still queued for
            // resumption. Settle before cancelling, so our own immediate CANCELLED callback
            // cannot replace TIMEOUT, and always return the outcome that actually won.
            callback.settle(failure)
            val actual = withContext(NonCancellable) { callback.outcome.await() }
            if (actual === failure) cancel()
            actual
        }
    }

    /** Requests cancellation; the plugin answers with a terminal `CANCELLED` failure. */
    fun cancel(): Boolean = runCatching { session.cancel() }.getOrElse { failure ->
        if (failure is RemoteException) {
            callback.settle(InstallerSessionOutcome.Failed(InstallerErrorMapper.fromThrowable(failure, InstallerErrorMapper.Phase.SESSION)))
        }
        false
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { session.asBinder().unlinkToDeath(deathRecipient, 0) }
        runCatching { session.close() }
        sources.forEach { it.close() }
        runCatching { bindingLease.close() }
        if (!callback.outcome.isCompleted) {
            callback.settle(InstallerSessionOutcome.Failed(InstallerErrorMapper.error(InstallerErrorCodes.CANCELLED, "installation session closed", retryable = false)))
        }
    }
}
