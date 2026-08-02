package org.autojs.autojs.core.plugin.dex

/**
 * Host-side acceptance gate for one compiler callback lane and its session controls.
 *
 * Binder callbacks must be serialized before entering this gate. The synchronized methods
 * additionally make accidental concurrent delivery deterministic and ensure that only one
 * terminal callback can win.
 */
internal class DexCompilerSessionLifecycle {

    enum class CallbackState {
        CREATED,
        STARTED,
        COMPLETED,
        FAILED,
        CANCELLED,
    }

    data class Snapshot(
        val callbackState: CallbackState,
        val lastProgressSequence: Long?,
        val cancellationRequested: Boolean,
        val closed: Boolean,
    ) {
        val isTerminal: Boolean
            get() = callbackState == CallbackState.COMPLETED ||
                callbackState == CallbackState.FAILED ||
                callbackState == CallbackState.CANCELLED
    }

    private var callbackState = CallbackState.CREATED
    private var lastProgressSequence: Long? = null
    private var cancellationRequested = false
    private var closed = false

    @Synchronized
    fun acceptStarted(): Boolean {
        if (closed || cancellationRequested || callbackState != CallbackState.CREATED) return false
        callbackState = CallbackState.STARTED
        return true
    }

    @Synchronized
    fun acceptProgress(sequence: Long): Boolean {
        if (closed || cancellationRequested || callbackState != CallbackState.STARTED || sequence < 0L) {
            return false
        }
        val previous = lastProgressSequence
        if (previous != null && sequence <= previous) return false
        lastProgressSequence = sequence
        return true
    }

    @Synchronized
    fun acceptCompleted(): Boolean {
        if (closed || cancellationRequested || callbackState != CallbackState.STARTED) return false
        callbackState = CallbackState.COMPLETED
        return true
    }

    @Synchronized
    fun acceptFailed(): Boolean = acceptNonSuccessTerminal(CallbackState.FAILED)

    @Synchronized
    fun acceptCancelled(): Boolean = acceptNonSuccessTerminal(CallbackState.CANCELLED)

    /** Returns true only when the caller must forward the cancellation signal. */
    @Synchronized
    fun requestCancel(): Boolean {
        if (closed || isTerminal() || cancellationRequested) return false
        cancellationRequested = true
        return true
    }

    /** Returns true only when the caller must perform the one-time close operation. */
    @Synchronized
    fun close(): Boolean {
        if (closed) return false
        closed = true
        return true
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(
        callbackState = callbackState,
        lastProgressSequence = lastProgressSequence,
        cancellationRequested = cancellationRequested,
        closed = closed,
    )

    private fun acceptNonSuccessTerminal(terminal: CallbackState): Boolean {
        if (closed || isTerminal()) return false
        callbackState = terminal
        return true
    }

    private fun isTerminal(): Boolean = callbackState == CallbackState.COMPLETED ||
        callbackState == CallbackState.FAILED || callbackState == CallbackState.CANCELLED
}
