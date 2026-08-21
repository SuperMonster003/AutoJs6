package org.autojs.autojs.core.plugin.yolo

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.autojs.plugin.yolo.api.IYoloCallback
import org.autojs.plugin.yolo.api.IYoloSession
import org.autojs.plugin.yolo.api.YoloCancellation
import org.autojs.plugin.yolo.api.YoloCancellationReason
import org.autojs.plugin.yolo.api.YoloCancelRequest
import org.autojs.plugin.yolo.api.YoloCapabilities
import org.autojs.plugin.yolo.api.YoloCodec
import org.autojs.plugin.yolo.api.YoloDetectRequest
import org.autojs.plugin.yolo.api.YoloDetectResult
import org.autojs.plugin.yolo.api.YoloError
import org.autojs.plugin.yolo.api.YoloProviderInfo
import org.autojs.plugin.yolo.api.YoloRequestId
import org.autojs.plugin.yolo.api.YoloValidation
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** One dedicated remote session. Protocol v1 permits exactly one in-flight detect and no queue. */
internal class YoloRemoteSession private constructor(
    private val applicationContext: Context,
    val component: ComponentName,
    val providerUid: Int,
    val providerInfo: YoloProviderInfo,
    val capabilities: YoloCapabilities,
    private val remote: IYoloSession,
    private val callback: YoloCallbackBridge,
    private val bindingLease: Closeable,
    val modelDescriptorsClosedAfterOpen: Boolean,
) : Closeable {

    private val binder = remote.asBinder()
    private val closed = AtomicBoolean(false)
    private val detectClaimed = AtomicBoolean(false)
    private val lastSequence = AtomicLong(0L)
    private val lastTrace = AtomicReference<DetectionTrace?>()
    private val deathRecipient = IBinder.DeathRecipient {
        terminate(
            sendRemoteClose = false,
            cause = YoloSessionClosedException("YOLO session Binder died"),
        )
    }

    val isClosed: Boolean
        get() = closed.get()

    val isRemoteBinder: Boolean =
        binder.queryLocalInterface(IYoloSession.DESCRIPTOR) == null

    val lastDetectionObservation: YoloPluginHost.DetectionObservation?
        get() = lastTrace.get()?.snapshot()

    val lastImageDescriptorClosedAfterDispatch: Boolean
        get() = lastTrace.get()?.imageDescriptorClosedAfterDispatch?.get() == true

    val lastTerminalCount: Int
        get() = lastTrace.get()?.terminalCount?.get() ?: 0

    val lastCallbackUid: Int?
        get() = lastTrace.get()?.callbackUid?.get()?.takeIf { it >= 0 }

    init {
        check(modelDescriptorsClosedAfterOpen) {
            "Host model descriptors must be closed before publishing a YOLO session"
        }
        check(isRemoteBinder) { "YOLO session Binder unexpectedly resolved in the Host process" }
        check(binder.isBinderAlive) { "YOLO session Binder is not alive" }
        check(binder.interfaceDescriptor == IYoloSession.DESCRIPTOR) {
            "YOLO session Binder descriptor mismatch"
        }
        callback.attachFatalHandler(::onCallbackFatal)
        binder.linkToDeath(deathRecipient, 0)
        if (!binder.isBinderAlive) {
            runCatching { binder.unlinkToDeath(deathRecipient, 0) }
            throw YoloSessionClosedException("YOLO session Binder died while linking death recipient")
        }
    }

    suspend fun detect(
        bitmap: Bitmap,
        options: YoloPluginHost.DetectOptions = YoloPluginHost.DetectOptions(),
    ): YoloDetectResult = detect(applicationContext, bitmap, options)

    suspend fun detect(
        context: Context,
        bitmap: Bitmap,
        options: YoloPluginHost.DetectOptions = YoloPluginHost.DetectOptions(),
    ): YoloDetectResult = withContext(Dispatchers.IO) {
        detectOnIo(context, bitmap, options)
    }

    private suspend fun detectOnIo(
        context: Context,
        bitmap: Bitmap,
        options: YoloPluginHost.DetectOptions,
    ): YoloDetectResult {
        require(context.applicationContext.packageName == applicationContext.packageName) {
            "YOLO image transport must belong to the Host application"
        }
        ensureOpen()
        if (!detectClaimed.compareAndSet(false, true)) {
            throw YoloSessionBusyException("YOLO protocol v1 permits no queued detections")
        }

        var pending: PendingDetection? = null
        try {
            ensureOpen()
            val sequence = nextPositiveSequence()
            val requestId = YoloRequestId.fromUuid(UUID.randomUUID())
            val frame = YoloImageTransport.fromBitmap(context.applicationContext, bitmap)
            val trace = DetectionTrace(requestId, sequence)
            lastTrace.set(trace)
            val request = YoloDetectRequest(
                requestId = requestId,
                sequence = sequence,
                image = frame.reference,
                confidenceThreshold = options.confidenceThreshold,
                iouThreshold = options.iouThreshold,
                maxDetections = options.maxDetections,
                timeoutMillis = options.timeoutMillis,
            )
            try {
                YoloValidation.validateDetectRequestAgainst(request, capabilities)
                require(options.cancellationGraceMillis in 1L..MAX_CANCELLATION_GRACE_MILLIS) {
                    "YOLO cancellation grace period is invalid"
                }
                ensureOpen()
                pending = callback.begin(request, trace)
                try {
                    // IYoloSession.detect is oneway. A successful return means the provider has
                    // accepted Binder's descriptor copy; the Host copy must close immediately.
                    remote.detect(YoloCodec.encodeDetectRequest(request), frame.descriptor)
                } catch (error: Throwable) {
                    terminate(
                        sendRemoteClose = false,
                        cause = YoloDispatchException("YOLO detect dispatch failed", error),
                    )
                    throw YoloDispatchException("YOLO detect dispatch failed; request was not retried", error)
                } finally {
                    frame.close()
                    trace.imageDescriptorClosedAfterDispatch.set(isDescriptorClosed(frame))
                }

                val terminal = awaitTerminal(pending, options)
                return terminal.toResult()
            } finally {
                if (!isDescriptorClosed(frame)) {
                    runCatching { frame.close() }
                    trace.imageDescriptorClosedAfterDispatch.set(isDescriptorClosed(frame))
                }
            }
        } catch (cancelled: CancellationException) {
            val active = pending
            if (active != null && !active.terminalClaimed.get()) {
                withContext(NonCancellable) {
                    val cancellationSent = sendCancel(active, YoloCancellationReason.REQUESTED)
                    if (cancellationSent) {
                        val acknowledged = withTimeoutOrNull(options.cancellationGraceMillis) {
                            active.terminal.await()
                        }
                        if (acknowledged == null) {
                            terminate(
                                sendRemoteClose = true,
                                cause = YoloSessionClosedException(
                                    "YOLO cancellation was not acknowledged; session state is uncertain",
                                ),
                            )
                        }
                    }
                }
            }
            throw cancelled
        } finally {
            pending?.let(callback::end)
            detectClaimed.set(false)
        }
    }

    override fun close() {
        terminate(
            sendRemoteClose = true,
            cause = YoloSessionClosedException("YOLO session was closed"),
        )
    }

    private suspend fun awaitTerminal(
        pending: PendingDetection,
        options: YoloPluginHost.DetectOptions,
    ): DetectionTerminal {
        val first = withTimeoutOrNull(options.timeoutMillis) {
            pending.terminal.await()
        }
        if (first != null) return first

        val cancellationSent = pending.terminalClaimed.get() ||
            sendCancel(pending, YoloCancellationReason.TIMEOUT)
        if (!cancellationSent) {
            throw YoloDetectionTimeoutException(
                stateUncertain = true,
                message = "YOLO detection timed out and cancellation could not be dispatched",
            )
        }
        val acknowledged = withTimeoutOrNull(options.cancellationGraceMillis) {
            pending.terminal.await()
        }
        if (acknowledged == null) {
            terminate(
                sendRemoteClose = true,
                cause = YoloSessionClosedException(
                    "YOLO timeout cancellation was not acknowledged; session state is uncertain",
                ),
            )
            throw YoloDetectionTimeoutException(
                stateUncertain = true,
                message = "YOLO detection timed out; the uncertain session was closed",
            )
        }
        throw YoloDetectionTimeoutException(
            stateUncertain = false,
            message = "YOLO detection exceeded its Host timeout; provider terminal was observed",
        )
    }

    private fun sendCancel(
        pending: PendingDetection,
        reason: YoloCancellationReason,
    ): Boolean {
        if (closed.get() || pending.terminalClaimed.get()) return false
        val cancellation = YoloCancelRequest(
            requestId = pending.request.requestId,
            sequence = pending.request.sequence,
            reason = reason,
        )
        YoloValidation.validateCancelRequest(cancellation)
        return try {
            remote.cancel(YoloCodec.encodeCancelRequest(cancellation))
            true
        } catch (error: Throwable) {
            terminate(
                sendRemoteClose = false,
                cause = YoloDispatchException("YOLO cancellation dispatch failed", error),
            )
            false
        }
    }

    private fun nextPositiveSequence(): Long {
        while (true) {
            val current = lastSequence.get()
            if (current == Long.MAX_VALUE) {
                terminate(
                    sendRemoteClose = true,
                    cause = YoloSessionClosedException("YOLO detection sequence was exhausted"),
                )
                throw YoloSessionClosedException("YOLO detection sequence was exhausted")
            }
            val next = current + 1L
            if (lastSequence.compareAndSet(current, next)) return next
        }
    }

    private fun ensureOpen() {
        check(!closed.get()) { "YOLO session is closed" }
        check(binder.isBinderAlive) { "YOLO session Binder is dead" }
    }

    private fun onCallbackFatal(error: Throwable) {
        terminate(sendRemoteClose = true, cause = error)
    }

    private fun terminate(sendRemoteClose: Boolean, cause: Throwable) {
        if (!closed.compareAndSet(false, true)) return
        callback.failActive(cause)
        runCatching { binder.unlinkToDeath(deathRecipient, 0) }
        if (sendRemoteClose && binder.isBinderAlive) {
            // close is oneway and intentionally attempted only once.
            runCatching { remote.close() }
        }
        runCatching { bindingLease.close() }
    }

    private fun DetectionTerminal.toResult(): YoloDetectResult = when (this) {
        is DetectionTerminal.Detected -> result
        is DetectionTerminal.Failed -> throw YoloDetectionRemoteException(error)
        is DetectionTerminal.Cancelled -> throw YoloDetectionCancelledException(cancellation)
    }

    private fun isDescriptorClosed(frame: YoloImageTransport.Frame): Boolean =
        runCatching { !frame.descriptor.fileDescriptor.valid() }.getOrDefault(true)

    companion object {
        private const val MAX_CANCELLATION_GRACE_MILLIS = 10_000L

        fun create(
            applicationContext: Context,
            component: ComponentName,
            providerUid: Int,
            providerInfo: YoloProviderInfo,
            capabilities: YoloCapabilities,
            remote: IYoloSession,
            callback: YoloCallbackBridge,
            bindingLease: Closeable,
            modelDescriptorsClosedAfterOpen: Boolean,
        ): YoloRemoteSession = YoloRemoteSession(
            applicationContext = applicationContext.applicationContext,
            component = component,
            providerUid = providerUid,
            providerInfo = providerInfo,
            capabilities = capabilities,
            remote = remote,
            callback = callback,
            bindingLease = bindingLease,
            modelDescriptorsClosedAfterOpen = modelDescriptorsClosedAfterOpen,
        )
    }
}

internal class YoloCallbackBridge(
    private val expectedProviderUid: Int,
) : IYoloCallback.Stub() {
    private val active = AtomicReference<PendingDetection?>()
    private val mostRecentTrace = AtomicReference<DetectionTrace?>()
    private val fatalHandler = AtomicReference<((Throwable) -> Unit)?>()

    fun attachFatalHandler(handler: (Throwable) -> Unit) {
        check(fatalHandler.compareAndSet(null, handler)) {
            "YOLO callback fatal handler was already attached"
        }
    }

    fun begin(request: YoloDetectRequest, trace: DetectionTrace): PendingDetection {
        val pending = PendingDetection(request, trace)
        check(active.compareAndSet(null, pending)) { "YOLO callback already has an active request" }
        mostRecentTrace.set(trace)
        return pending
    }

    fun end(pending: PendingDetection) {
        active.compareAndSet(pending, null)
    }

    fun failActive(error: Throwable) {
        active.get()?.fail(error)
    }

    override fun onDetected(result: ByteArray?) {
        accept("detected", result) { payload, pending ->
            val decoded = YoloCodec.decodeDetectResult(payload)
            YoloValidation.validateDetectResultAgainst(decoded, pending.request)
            DetectionTerminal.Detected(decoded)
        }
    }

    override fun onFailed(error: ByteArray?) {
        accept("failed", error) { payload, pending ->
            val decoded = YoloCodec.decodeError(payload)
            YoloValidation.validateError(decoded)
            requireMatches(decoded.requestId, decoded.sequence, pending.request)
            DetectionTerminal.Failed(decoded)
        }
    }

    override fun onCancelled(cancellation: ByteArray?) {
        accept("cancelled", cancellation) { payload, pending ->
            val decoded = YoloCodec.decodeCancellation(payload)
            YoloValidation.validateCancellation(decoded)
            requireMatches(decoded.requestId, decoded.sequence, pending.request)
            DetectionTerminal.Cancelled(decoded)
        }
    }

    private inline fun accept(
        terminalName: String,
        payload: ByteArray?,
        decode: (ByteArray, PendingDetection) -> DetectionTerminal,
    ) {
        val callingUid = Binder.getCallingUid()
        val pending = active.get()
        val trace = pending?.trace ?: mostRecentTrace.get()
        trace?.callbackUid?.set(callingUid)
        trace?.terminalCount?.incrementAndGet()

        if (callingUid != expectedProviderUid) {
            protocolFailure(
                pending,
                SecurityException(
                    "YOLO callback UID mismatch: expected $expectedProviderUid, got $callingUid",
                ),
            )
            return
        }
        if (pending == null) {
            fatal(YoloCallbackProtocolException("Unexpected YOLO $terminalName callback without an active request"))
            return
        }
        val terminal = try {
            decode(requireNotNull(payload) { "YOLO $terminalName callback payload is missing" }, pending)
        } catch (error: Throwable) {
            protocolFailure(
                pending,
                YoloCallbackProtocolException("Malformed YOLO $terminalName callback", error),
            )
            return
        }
        if (!pending.terminalClaimed.compareAndSet(false, true)) {
            fatal(YoloCallbackProtocolException("YOLO provider emitted more than one terminal callback"))
            return
        }
        pending.terminal.complete(terminal)
    }

    private fun requireMatches(
        requestId: YoloRequestId,
        sequence: Long,
        request: YoloDetectRequest,
    ) {
        require(requestId == request.requestId) { "YOLO callback request ID does not match" }
        require(sequence == request.sequence) { "YOLO callback sequence does not match" }
    }

    private fun protocolFailure(pending: PendingDetection?, error: Throwable) {
        pending?.fail(error)
        fatal(error)
    }

    private fun fatal(error: Throwable) {
        runCatching { fatalHandler.get()?.invoke(error) }
    }
}

internal class PendingDetection(
    val request: YoloDetectRequest,
    val trace: DetectionTrace,
) {
    val terminalClaimed = AtomicBoolean(false)
    val terminal = CompletableDeferred<DetectionTerminal>()

    fun fail(error: Throwable) {
        if (terminalClaimed.compareAndSet(false, true)) {
            terminal.completeExceptionally(error)
        }
    }
}

internal class DetectionTrace(
    private val requestId: YoloRequestId,
    private val sequence: Long,
) {
    val imageDescriptorClosedAfterDispatch = AtomicBoolean(false)
    val callbackUid = AtomicInteger(-1)
    val terminalCount = AtomicInteger(0)

    fun snapshot(): YoloPluginHost.DetectionObservation = YoloPluginHost.DetectionObservation(
        requestId = requestId,
        sequence = sequence,
        imageDescriptorClosedAfterDispatch = imageDescriptorClosedAfterDispatch.get(),
        callbackUid = callbackUid.get().takeIf { it >= 0 },
        terminalCount = terminalCount.get(),
    )
}

internal sealed interface DetectionTerminal {
    data class Detected(val result: YoloDetectResult) : DetectionTerminal
    data class Failed(val error: YoloError) : DetectionTerminal
    data class Cancelled(val cancellation: YoloCancellation) : DetectionTerminal
}

class YoloSessionBusyException(message: String) : IllegalStateException(message)

class YoloSessionClosedException(message: String) : IllegalStateException(message)

class YoloDispatchException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class YoloCallbackProtocolException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class YoloDetectionRemoteException(
    val remoteError: YoloError,
) : IllegalStateException(
    "YOLO provider failed (${remoteError.code}/${remoteError.phase}): ${remoteError.message}",
)

class YoloDetectionCancelledException(
    val cancellation: YoloCancellation,
) : CancellationException(
    "YOLO detection was cancelled (${cancellation.reason})",
)

class YoloDetectionTimeoutException(
    val stateUncertain: Boolean,
    message: String,
) : IllegalStateException(message)
