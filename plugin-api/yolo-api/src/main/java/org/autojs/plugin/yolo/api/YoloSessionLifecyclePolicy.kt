package org.autojs.plugin.yolo.api

/**
 * Android-free policy for one persistent model session.
 *
 * The policy never queues inference work: callers receiving [BeginDisposition.BUSY]
 * must fail that request before inference dispatch. Binder and worker ownership stay
 * outside this class.
 */
class YoloSessionLifecyclePolicy {
    enum class State {
        IDLE,
        ACTIVE,
        CLOSED,
    }

    enum class BeginDisposition {
        ACCEPTED,
        BUSY,
        CLOSED,
        REPLAYED_OR_OUT_OF_ORDER,
    }

    enum class Terminal {
        DETECTED,
        FAILED,
        CANCELLED,
    }

    data class CloseResult(
        val changed: Boolean,
        val cancelledRequestId: YoloRequestId?,
        val cancelledSequence: Long?,
    )

    private data class ActiveDetect(
        val request: YoloDetectRequest,
        var cancelRequested: Boolean = false,
    )

    private var closed = false
    private var active: ActiveDetect? = null

    val state: State
        @Synchronized get() = when {
            closed -> State.CLOSED
            active != null -> State.ACTIVE
            else -> State.IDLE
        }

    val activeRequestId: YoloRequestId?
        @Synchronized get() = active?.request?.requestId

    /** V1 has no queue; this remains zero in every state. */
    val queuedDetectionCount: Int
        get() = 0

    @get:Synchronized
    var lastTerminal: Terminal? = null
        private set

    @get:Synchronized
    var lastSeenSequence: Long? = null
        private set

    @Synchronized
    fun beginDetect(request: YoloDetectRequest): BeginDisposition {
        YoloValidation.validateDetectRequest(request)
        val previousSequence = lastSeenSequence
        if (previousSequence != null && request.sequence <= previousSequence) {
            return BeginDisposition.REPLAYED_OR_OUT_OF_ORDER
        }
        lastSeenSequence = request.sequence
        if (closed) return BeginDisposition.CLOSED
        if (active != null) return BeginDisposition.BUSY
        active = ActiveDetect(request)
        return BeginDisposition.ACCEPTED
    }

    /** Returns false for a duplicate, late, mismatched, or post-cancel success callback. */
    @Synchronized
    fun onDetected(result: YoloDetectResult): Boolean {
        val current = active ?: return false
        if (
            result.requestId != current.request.requestId ||
            result.sequence != current.request.sequence ||
            current.cancelRequested
        ) return false
        YoloValidation.validateDetectResultAgainst(result, current.request)
        finish(Terminal.DETECTED)
        return true
    }

    /** Returns false for a duplicate, late, or mismatched failure callback. */
    @Synchronized
    fun onFailed(error: YoloError): Boolean {
        YoloValidation.validateError(error)
        val current = active ?: return false
        if (
            error.requestId != current.request.requestId ||
            error.sequence != current.request.sequence
        ) return false
        finish(Terminal.FAILED)
        return true
    }

    /** Returns false for a duplicate, late, mismatched, or unsolicited cancellation callback. */
    @Synchronized
    fun onCancelled(cancellation: YoloCancellation): Boolean {
        YoloValidation.validateCancellation(cancellation)
        val current = active ?: return false
        if (
            cancellation.requestId != current.request.requestId ||
            cancellation.sequence != current.request.sequence
        ) return false
        val providerInitiated = cancellation.reason == YoloCancellationReason.TIMEOUT ||
            cancellation.reason == YoloCancellationReason.SESSION_CLOSED ||
            cancellation.reason == YoloCancellationReason.PROVIDER_SHUTDOWN
        if (!current.cancelRequested && !providerInitiated) return false
        finish(Terminal.CANCELLED)
        return true
    }

    @Synchronized
    fun requestCancel(request: YoloCancelRequest): Boolean {
        YoloValidation.validateCancelRequest(request)
        if (closed) return false
        val current = active ?: return false
        if (
            request.requestId != current.request.requestId ||
            request.sequence != current.request.sequence ||
            current.cancelRequested
        ) return false
        current.cancelRequested = true
        return true
    }

    /**
     * Closes the session exactly once. When a request was active, the adapter uses
     * [CloseResult.cancelledRequestId] and [CloseResult.cancelledSequence] to emit
     * its single SESSION_CLOSED terminal.
     */
    @Synchronized
    fun close(): CloseResult {
        if (closed) {
            return CloseResult(
                changed = false,
                cancelledRequestId = null,
                cancelledSequence = null,
            )
        }
        val cancelledRequestId = active?.request?.requestId
        val cancelledSequence = active?.request?.sequence
        if (cancelledRequestId != null) lastTerminal = Terminal.CANCELLED
        active = null
        closed = true
        return CloseResult(
            changed = true,
            cancelledRequestId = cancelledRequestId,
            cancelledSequence = cancelledSequence,
        )
    }

    private fun finish(value: Terminal) {
        check(!closed) { "YOLO session is closed" }
        check(active != null) { "No YOLO detection is active" }
        active = null
        lastTerminal = value
    }
}
