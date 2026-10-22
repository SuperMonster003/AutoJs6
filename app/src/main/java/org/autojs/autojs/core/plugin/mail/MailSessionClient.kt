package org.autojs.autojs.core.plugin.mail

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.plugin.concurrentSnapshot
import org.autojs.plugin.mail.api.MailContract
import org.autojs.plugin.mail.api.MailErrorCodes
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Result channel of one submitted request. */
interface MailCallListener {
    fun onProgress(progress: MailJson.Progress)
    fun onResult(response: MailJson.Response)
}

/** Signals from the transport to the client: the session is gone and must be reopened. */
interface MailSessionEvents {
    fun onClosed(reason: MailError?)
}

/** Host-side handle of one watch started through [MailSessionPort.watch]. */
interface MailWatchHandle {
    fun status(): MailJson.Status
    fun stop()
}

/**
 * One open session as the client sees it. The Binder implementation is [BinderMailSessionPort];
 * the JVM tests use a fake, which keeps the state machine of [MailSessionClient] Android-free.
 */
interface MailSessionPort {
    fun status(): MailJson.Status
    fun call(request: MailJson.Request, descriptors: Array<ParcelFileDescriptor>?, listener: MailCallListener)
    fun cancel(requestId: String)
    fun watch(optionsJson: String, bridge: MailWatchBridge): MailWatchHandle
    fun close()
}

fun interface MailSessionOpener {
    suspend fun open(account: MailAccountSpec, events: MailSessionEvents): MailSessionPort
}

/**
 * Request state machine of one mail session (roadmap P1.2, decisions D4 and D25).
 *
 * Every [call] builds a `{id, op, args}` envelope, submits it and suspends until `onResult`, the
 * operation timeout or cancellation; cancellation and timeouts also cancel the request on the
 * plugin side. A session that died (death notification, `onStatus(closed)` or a transport
 * exception) is marked for reopening and the next call opens a new one with the credentials kept
 * in memory. An `AUTH_FAILED` reply on a token session with a [tokenProvider] refreshes the token,
 * reopens and retries exactly once. Watches (roadmap P5) register a session-loss listener to learn
 * about the plugin's death, which their own callback cannot report.
 */
class MailSessionClient(
    account: MailAccountSpec,
    private val opener: MailSessionOpener,
    private val tokenProvider: (suspend () -> String)? = null,
    private val requestIds: () -> String = { UUID.randomUUID().toString() },
) {

    private val mutex = Mutex()
    private val inflight = ConcurrentHashMap<String, CancellableContinuation<MailJson.Response>>()
    private val openCounter = AtomicInteger(0)
    private val lossListeners = CopyOnWriteArrayList<(MailError?) -> Unit>()

    @Volatile
    var account: MailAccountSpec = account
        private set

    @Volatile
    private var port: MailSessionPort? = null

    @Volatile
    private var needsReopen = false

    @Volatile
    private var closedByCaller = false

    @Volatile
    var lastCloseReason: MailError? = null
        private set

    val isOpen: Boolean
        get() = port != null && !needsReopen && !closedByCaller

    /** How many sessions were opened so far, including reopenings. */
    val openCount: Int
        get() = openCounter.get()

    private val events = object : MailSessionEvents {
        override fun onClosed(reason: MailError?) {
            lastCloseReason = reason
            needsReopen = true
            failInflight(reason)
            if (!closedByCaller) lossListeners.forEach { listener -> runCatching { listener(reason) } }
        }
    }

    /** Whether an `AUTH_FAILED` can be answered with a fresh token (token account with a provider). */
    val canRefreshToken: Boolean
        get() = tokenProvider != null && account.accessToken != null

    /** Called (off the caller's thread) when the open session was lost without [close]: plugin death or a `closed` status. */
    fun addSessionLossListener(listener: (MailError?) -> Unit) {
        lossListeners.addIfAbsent(listener)
    }

    fun removeSessionLossListener(listener: (MailError?) -> Unit) {
        lossListeners.remove(listener)
    }

    /** Opens the session eagerly; [call] does the same lazily. */
    suspend fun open() {
        mutex.withLock { ensureOpenLocked() }
    }

    fun status(): MailJson.Status? = port?.takeIf { !needsReopen }?.let { runCatching { it.status() }.getOrNull() }

    /**
     * Runs one operation and returns the encoded `result` element. [onSubmitted] runs right after
     * the plugin accepted the request, which is when a pipe write end must be closed on this side.
     */
    suspend fun call(
        op: String,
        argsJson: String? = null,
        descriptors: Array<ParcelFileDescriptor>? = null,
        timeoutMs: Long? = null,
        transferBytes: Long? = null,
        onSubmitted: (() -> Unit)? = null,
        onProgress: ((MailJson.Progress) -> Unit)? = null,
    ): String {
        var tokenRefreshed = false
        while (true) {
            val current = mutex.withLock { ensureOpenLocked() }
            try {
                return execute(current, op, argsJson, descriptors, timeoutMs ?: MailContract.callTimeoutMs(op, transferBytes), onSubmitted, onProgress)
            } catch (e: MailPluginException) {
                if (e.code == MailErrorCodes.AUTH_FAILED && !tokenRefreshed && refreshToken()) {
                    tokenRefreshed = true
                    continue
                }
                throw e
            }
        }
    }

    /**
     * Asks the [tokenProvider] for a fresh token and marks the session for reopening with it;
     * false when the account is no token account or has no provider. A provider failure is an
     * `AUTH_FAILED` that is not retryable.
     */
    suspend fun refreshToken(): Boolean {
        val provider = tokenProvider ?: return false
        if (account.accessToken == null) return false
        val token = try {
            provider()
        } catch (t: Throwable) {
            throw MailPluginException(MailErrorMapper.normalize(MailErrorCodes.AUTH_FAILED, "token refresh failed: ${t.message}", retryable = false), t)
        }
        mutex.withLock {
            account = account.withAccessToken(token)
            needsReopen = true
        }
        return true
    }

    suspend fun watch(options: MailJson.WatchOptions, generation: Long, sink: MailWatchSink): Pair<MailWatchBridge, MailWatchHandle> {
        val current = mutex.withLock { ensureOpenLocked() }
        val bridge = MailWatchBridge(generation, sink)
        val handle = try {
            current.watch(MailJson.encodeWatchOptions(options, generation), bridge)
        } catch (t: Throwable) {
            throw wrap(t, MailErrorMapper.Phase.CALL)
        }
        return bridge to handle
    }

    /** Closes the session; pending calls fail with a non-retryable `SESSION_CLOSED`. */
    suspend fun close() {
        closedByCaller = true
        val current = mutex.withLock { port.also { port = null } }
        current?.let { runCatching { it.close() } }
        failInflight(MailError(MailErrorCodes.SESSION_CLOSED, "mail session closed by the caller", retryable = false))
    }

    private suspend fun ensureOpenLocked(): MailSessionPort {
        if (closedByCaller) {
            throw MailPluginException(MailError(MailErrorCodes.SESSION_CLOSED, "mail session closed by the caller", retryable = false))
        }
        port?.takeIf { !needsReopen }?.let { return it }
        port?.let { stale -> runCatching { stale.close() } }
        port = null
        account.validate()?.let { throw MailPluginException(it) }
        val opened = try {
            opener.open(account, events)
        } catch (t: Throwable) {
            throw wrap(t, MailErrorMapper.Phase.OPEN)
        }
        port = opened
        needsReopen = false
        openCounter.incrementAndGet()
        return opened
    }

    private suspend fun execute(
        port: MailSessionPort,
        op: String,
        argsJson: String?,
        descriptors: Array<ParcelFileDescriptor>?,
        timeoutMs: Long,
        onSubmitted: (() -> Unit)?,
        onProgress: ((MailJson.Progress) -> Unit)?,
    ): String {
        val request = try {
            MailJson.encodeRequest(requestIds(), op, argsJson)
        } catch (t: Throwable) {
            throw wrap(t, MailErrorMapper.Phase.CALL)
        }
        val response = try {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine<MailJson.Response> { continuation ->
                    inflight[request.id] = continuation
                    continuation.invokeOnCancellation {
                        inflight.remove(request.id)
                        runCatching { port.cancel(request.id) }
                    }
                    val listener = object : MailCallListener {
                        override fun onProgress(progress: MailJson.Progress) {
                            onProgress?.invoke(progress)
                        }

                        override fun onResult(response: MailJson.Response) {
                            inflight.remove(request.id)
                            if (continuation.isActive) continuation.resume(response)
                        }
                    }
                    try {
                        port.call(request, descriptors, listener)
                    } catch (t: Throwable) {
                        inflight.remove(request.id)
                        if (continuation.isActive) continuation.resumeWithException(wrap(t, MailErrorMapper.Phase.CALL))
                        return@suspendCancellableCoroutine
                    }
                    onSubmitted?.let { runCatching(it) }
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw MailPluginException(MailErrorMapper.normalize(MailErrorCodes.TIMEOUT, "$op timed out after $timeoutMs ms", retryable = true), e)
        }
        if (response.ok) return response.result ?: "null"
        val error = response.error ?: MailErrorMapper.normalize(MailErrorCodes.INTERNAL, "response carries no error", retryable = false)
        if (error.code == MailErrorCodes.SESSION_CLOSED || error.code == MailErrorCodes.PLUGIN_UNAVAILABLE) needsReopen = true
        throw MailPluginException(error)
    }

    private fun wrap(throwable: Throwable, phase: MailErrorMapper.Phase): MailPluginException {
        if (throwable is MailPluginException) return throwable
        val error = MailErrorMapper.fromThrowable(throwable, phase)
        if (error.code == MailErrorCodes.SESSION_CLOSED || (phase == MailErrorMapper.Phase.CALL && error.code == MailErrorCodes.PLUGIN_UNAVAILABLE)) {
            needsReopen = true
        }
        return MailPluginException(error, throwable)
    }

    private fun failInflight(reason: MailError?) {
        val error = MailErrorMapper.normalize(MailErrorCodes.SESSION_CLOSED, reason?.message, reason?.retryable ?: true)
        val pending = inflight.values.concurrentSnapshot()
        inflight.clear()
        pending.forEach { continuation ->
            if (continuation.isActive) continuation.resumeWithException(MailPluginException(error))
        }
    }
}
