package org.autojs.autojs.core.plugin.jvm

internal class JvmSourceSessionLifecycle(
    private val diagnosticByteLimit: Int,
) {
    init {
        require(diagnosticByteLimit > 0)
    }

    enum class State {
        CREATED,
        STARTED,
        COMPLETED,
        FAILED,
        CANCELLED,
        ABORTED,
    }

    data class Snapshot(
        val state: State,
        val diagnosticBytes: Int,
        val cancellationRequested: Boolean,
        val closed: Boolean,
    ) {
        val terminal: Boolean
            get() = state in setOf(State.COMPLETED, State.FAILED, State.CANCELLED, State.ABORTED)
    }

    private var state = State.CREATED
    private var diagnosticBytes = 0
    private var cancellationRequested = false
    private var closed = false

    @Synchronized
    fun acceptStarted(): Boolean {
        // A cancel can race the provider's mandatory STARTED callback. Preserve the protocol
        // prefix; only already-queued bounded diagnostics and the CANCELLED terminal may follow.
        if (closed || state != State.CREATED) return false
        state = State.STARTED
        return true
    }

    @Synchronized
    fun acceptDiagnostic(encodedBytes: Int): Boolean {
        // Binder callbacks queued before the provider observes cancel may arrive after the host has
        // requested it. They remain bounded evidence, while success/failure terminals stay barred.
        if (closed || state != State.STARTED || encodedBytes <= 0) return false
        val next = diagnosticBytes.toLong() + encodedBytes.toLong()
        if (next > diagnosticByteLimit) return false
        diagnosticBytes = next.toInt()
        return true
    }

    @Synchronized
    fun acceptCompleted(): Boolean = acceptTerminal(State.COMPLETED, requireStarted = true)

    @Synchronized
    fun acceptFailed(): Boolean = acceptTerminal(State.FAILED, requireStarted = true)

    @Synchronized
    fun acceptCancelled(): Boolean {
        if (closed || isTerminal() || state != State.STARTED) return false
        state = State.CANCELLED
        return true
    }

    @Synchronized
    fun abort(): Boolean {
        if (closed || isTerminal()) return false
        state = State.ABORTED
        return true
    }

    /** True means the caller owns forwarding the single remote cancel signal. */
    @Synchronized
    fun requestCancel(): Boolean {
        if (closed || isTerminal() || cancellationRequested) return false
        cancellationRequested = true
        return true
    }

    /** True means the caller owns the one-time resource cleanup. */
    @Synchronized
    fun close(): Boolean {
        if (closed) return false
        closed = true
        return true
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(state, diagnosticBytes, cancellationRequested, closed)

    private fun acceptTerminal(terminal: State, requireStarted: Boolean): Boolean {
        if (closed || cancellationRequested || isTerminal()) return false
        if (requireStarted && state != State.STARTED) return false
        if (!requireStarted && state !in setOf(State.CREATED, State.STARTED)) return false
        state = terminal
        return true
    }

    private fun isTerminal(): Boolean = state in setOf(
        State.COMPLETED,
        State.FAILED,
        State.CANCELLED,
        State.ABORTED,
    )
}
