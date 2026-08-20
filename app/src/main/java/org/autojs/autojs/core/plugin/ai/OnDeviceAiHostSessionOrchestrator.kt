package org.autojs.autojs.core.plugin.ai

/**
 * Transport-independent ownership gate for one future AI Binder session.
 *
 * Binder callbacks must be serialized before detailed protocol decoding. Synchronization here
 * makes terminal, cancellation, attachment, and close races deterministic even if an adapter
 * accidentally calls the gate concurrently.
 */
internal class OnDeviceAiHostSessionOrchestrator {

    enum class CallbackState {
        CREATED,
        DISPATCHING,
        STARTED,
        COMPLETED,
        FAILED,
        CANCELLED,
    }

    enum class CancellationDecision {
        COMPLETE_LOCALLY,
        FORWARD_NOW,
        DEFER_UNTIL_ATTACHED,
        IGNORED,
    }

    enum class CloseDecision {
        CLOSE_LOCALLY,
        FORWARD_NOW,
        DEFER_UNTIL_ATTACHED,
        IGNORED,
    }

    data class AttachDecision(
        val forwardCancellation: Boolean,
        val forwardClose: Boolean,
    )

    data class Snapshot(
        val callbackState: CallbackState,
        val dispatchStarted: Boolean,
        val remoteAttached: Boolean,
        val cancellationRequested: Boolean,
        val cancellationForwarded: Boolean,
        val closeRequested: Boolean,
        val closeForwarded: Boolean,
    ) {
        val isTerminal: Boolean
            get() = callbackState == CallbackState.COMPLETED ||
                callbackState == CallbackState.FAILED ||
                callbackState == CallbackState.CANCELLED
    }

    private var callbackState = CallbackState.CREATED
    private var dispatchStarted = false
    private var remoteAttached = false
    private var cancellationRequested = false
    private var cancellationForwarded = false
    private var closeRequested = false
    private var closeForwarded = false

    @Synchronized
    fun beginDispatch(): Boolean {
        if (dispatchStarted || closeRequested || cancellationRequested || isTerminal()) return false
        dispatchStarted = true
        callbackState = CallbackState.DISPATCHING
        return true
    }

    /**
     * Claims all deferred control signals exactly once. A callback may race ahead of the returned
     * remote handle, so terminal state does not by itself make a first attachment invalid.
     */
    @Synchronized
    fun attachRemote(): AttachDecision? {
        if (!dispatchStarted || remoteAttached) return null
        remoteAttached = true
        val forwardCancellation = cancellationRequested && !isTerminal() && !cancellationForwarded
        val forwardClose = closeRequested && !closeForwarded
        if (forwardCancellation) cancellationForwarded = true
        if (forwardClose) closeForwarded = true
        return AttachDecision(forwardCancellation, forwardClose)
    }

    @Synchronized
    fun acceptStarted(): Boolean {
        if (!callbacksAllowed() || cancellationRequested || callbackState != CallbackState.DISPATCHING) return false
        callbackState = CallbackState.STARTED
        return true
    }

    /** Admission gate for chunks, usage, tool calls, and other non-terminal callbacks. */
    @Synchronized
    fun acceptDataEvent(): Boolean =
        callbacksAllowed() && !cancellationRequested && callbackState == CallbackState.STARTED

    @Synchronized
    fun acceptCompleted(): Boolean {
        if (!callbacksAllowed() || cancellationRequested || callbackState != CallbackState.STARTED) return false
        callbackState = CallbackState.COMPLETED
        return true
    }

    @Synchronized
    fun acceptFailed(): Boolean = acceptNonSuccessTerminal(CallbackState.FAILED)

    @Synchronized
    fun acceptCancelled(): Boolean = acceptNonSuccessTerminal(CallbackState.CANCELLED)

    @Synchronized
    fun requestCancel(): CancellationDecision {
        if (closeRequested || cancellationRequested || isTerminal()) return CancellationDecision.IGNORED
        cancellationRequested = true
        if (!dispatchStarted) {
            callbackState = CallbackState.CANCELLED
            return CancellationDecision.COMPLETE_LOCALLY
        }
        return if (remoteAttached) {
            cancellationForwarded = true
            CancellationDecision.FORWARD_NOW
        } else {
            CancellationDecision.DEFER_UNTIL_ATTACHED
        }
    }

    @Synchronized
    fun close(): CloseDecision {
        if (closeRequested) return CloseDecision.IGNORED
        closeRequested = true
        if (!dispatchStarted) return CloseDecision.CLOSE_LOCALLY
        return if (remoteAttached) {
            closeForwarded = true
            CloseDecision.FORWARD_NOW
        } else {
            CloseDecision.DEFER_UNTIL_ATTACHED
        }
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(
        callbackState = callbackState,
        dispatchStarted = dispatchStarted,
        remoteAttached = remoteAttached,
        cancellationRequested = cancellationRequested,
        cancellationForwarded = cancellationForwarded,
        closeRequested = closeRequested,
        closeForwarded = closeForwarded,
    )

    private fun acceptNonSuccessTerminal(terminal: CallbackState): Boolean {
        if (!callbacksAllowed()) return false
        callbackState = terminal
        return true
    }

    private fun callbacksAllowed(): Boolean = dispatchStarted && !closeRequested && !isTerminal()

    private fun isTerminal(): Boolean = callbackState == CallbackState.COMPLETED ||
        callbackState == CallbackState.FAILED || callbackState == CallbackState.CANCELLED
}
