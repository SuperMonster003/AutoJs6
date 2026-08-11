package org.autojs.plugin.python.runtime.api

class PythonOutputCreditWindow(
    private val maximumCredits: Int,
) {
    init {
        require(maximumCredits > 0)
    }

    @get:Synchronized
    var outstandingCredits: Int = 0
        private set

    @Synchronized
    fun grant(count: Int) {
        require(count > 0) { "Output credit grant must be positive" }
        require(count <= maximumCredits - outstandingCredits) { "Output credit window overflow" }
        outstandingCredits += count
    }

    @Synchronized
    fun consume() {
        check(outstandingCredits > 0) { "No output credit is available" }
        outstandingCredits -= 1
    }
}

class PythonExecutionSessionPolicy(
    private val expectedRequest: PythonExecutionRequest,
    maximumCredits: Int,
    private val expectedPythonVersion: String,
) {
    enum class State {
        CREATED,
        START_REQUESTED,
        STARTED,
        TERMINAL,
        CLOSED,
    }

    enum class Terminal {
        RESULT,
        FAILED,
        CANCELLED,
    }

    enum class ReplayDisposition {
        SAFE_BEFORE_DISPATCH,
        FORBIDDEN_AFTER_DISPATCH,
    }

    private val credits = PythonOutputCreditWindow(maximumCredits)
    private var nextOutputSequence: Long? = null
    private var effectiveMaxOutputBytes: Long? = null
    private var effectiveMaxOutputChunkBytes: Int? = null
    private var effectiveMaxOutputChunks: Long? = null
    private var cancelRequested = false
    private var startDispatched = false
    private var observedStdoutBytes = 0L
    private var observedStderrBytes = 0L
    private var observedStdoutChunks = 0L
    private var observedStderrChunks = 0L

    init {
        PythonRuntimeValidation.validateRequest(expectedRequest)
        require(maximumCredits in 1..PythonRuntimeContract.MAX_OUTSTANDING_OUTPUT_CREDITS)
    }

    @get:Synchronized
    var state: State = State.CREATED
        private set

    @get:Synchronized
    var terminal: Terminal? = null
        private set

    val outstandingCredits: Int
        @Synchronized get() = credits.outstandingCredits

    val replayDisposition: ReplayDisposition
        @Synchronized get() = if (state == State.CREATED && !startDispatched && !cancelRequested) {
            ReplayDisposition.SAFE_BEFORE_DISPATCH
        } else {
            ReplayDisposition.FORBIDDEN_AFTER_DISPATCH
        }

    val stdoutBytes: Long
        @Synchronized get() = observedStdoutBytes

    val stderrBytes: Long
        @Synchronized get() = observedStderrBytes

    @Synchronized
    fun grantOutputCredits(count: Int) {
        requireOpen()
        requireDataAllowed()
        credits.grant(count)
    }

    /**
     * Marks the one-way Binder dispatch boundary. A duplicate call is ignored,
     * and automatic replay is forbidden as soon as the first call succeeds.
     */
    @Synchronized
    fun requestStart(): Boolean {
        if (cancelRequested) return false
        return when (state) {
            State.CREATED -> {
                startDispatched = true
                state = State.START_REQUESTED
                true
            }
            State.START_REQUESTED,
            State.STARTED,
            -> false
            State.TERMINAL,
            State.CLOSED,
            -> false
        }
    }

    @Synchronized
    fun onStarted(started: PythonSessionStarted) {
        acceptCallback {
            requireOpen()
            check(state == State.START_REQUESTED) { "Session start is duplicated or out of order" }
            PythonRuntimeValidation.validateStarted(started)
            require(started.requestId == expectedRequest.requestId) { "Started request ID does not match" }
            require(started.protocolVersion == expectedRequest.protocolVersion) {
                "Started protocol version does not match"
            }
            require(started.pythonVersion == expectedPythonVersion) { "Started Python version does not match" }
            require(started.effectiveMaxOutputBytes <= expectedRequest.maxOutputBytes) {
                "Provider output limit exceeds the requested limit"
            }
            require(started.effectiveMaxOutputChunkBytes <= expectedRequest.maxOutputChunkBytes) {
                "Provider output chunk limit exceeds the requested limit"
            }
            require(started.effectiveMaxOutputChunks <= expectedRequest.maxOutputChunks) {
                "Provider output chunk count limit exceeds the requested limit"
            }
            nextOutputSequence = started.firstOutputSequence
            effectiveMaxOutputBytes = started.effectiveMaxOutputBytes
            effectiveMaxOutputChunkBytes = started.effectiveMaxOutputChunkBytes
            effectiveMaxOutputChunks = started.effectiveMaxOutputChunks
            state = State.STARTED
        }
    }

    @Synchronized
    fun onOutput(chunk: PythonOutputChunk, callbackStream: PythonOutputStream) {
        acceptCallback {
            requireStarted()
            PythonRuntimeValidation.validateOutputChunk(chunk)
            require(chunk.requestId == expectedRequest.requestId) { "Output request ID does not match" }
            require(chunk.stream == callbackStream) { "Output stream does not match the callback method" }
            val expectedSequence = checkNotNull(nextOutputSequence) { "Output sequence is unavailable" }
            check(chunk.sequence == expectedSequence) {
                "Expected output sequence $expectedSequence but received ${chunk.sequence}"
            }
            check(chunk.sequence < Long.MAX_VALUE) { "Output sequence is exhausted" }
            val chunkLimit = checkNotNull(effectiveMaxOutputChunkBytes) { "Output chunk limit is unavailable" }
            check(chunk.sizeBytes <= chunkLimit) { "Output chunk exceeds the session limit" }
            val outputLimit = checkNotNull(effectiveMaxOutputBytes) { "Output limit is unavailable" }
            val emittedBytes = Math.addExact(observedStdoutBytes, observedStderrBytes)
            check(chunk.sizeBytes.toLong() <= outputLimit - emittedBytes) {
                "Output exceeds the session limit"
            }
            val chunkCountLimit = checkNotNull(effectiveMaxOutputChunks) { "Output chunk count limit is unavailable" }
            val emittedChunks = Math.addExact(observedStdoutChunks, observedStderrChunks)
            check(emittedChunks < chunkCountLimit) { "Output chunk count exceeds the session limit" }
            credits.consume()
            nextOutputSequence = chunk.sequence + 1L
            when (chunk.stream) {
                PythonOutputStream.STDOUT -> {
                    observedStdoutBytes += chunk.sizeBytes.toLong()
                    observedStdoutChunks += 1L
                }
                PythonOutputStream.STDERR -> {
                    observedStderrBytes += chunk.sizeBytes.toLong()
                    observedStderrChunks += 1L
                }
            }
        }
    }

    @Synchronized
    fun onResult(result: PythonExecutionResult, descriptorCount: Int) {
        acceptCallback {
            requireStarted()
            PythonRuntimeValidation.validateResult(result)
            require(result.requestId == expectedRequest.requestId) { "Result request ID does not match" }
            require(descriptorCount == 0) {
                "Python Runtime protocol v1.0 reserves result descriptors and requires an empty descriptor array"
            }
            require(result.stdoutBytes == observedStdoutBytes) { "Result stdout byte count does not match the stream" }
            require(result.stderrBytes == observedStderrBytes) { "Result stderr byte count does not match the stream" }
            require(result.stdoutChunks == observedStdoutChunks) { "Result stdout chunk count does not match the stream" }
            require(result.stderrChunks == observedStderrChunks) { "Result stderr chunk count does not match the stream" }
            acceptTerminal(Terminal.RESULT)
        }
    }

    @Synchronized
    fun onFailed(error: PythonExecutionError) {
        acceptCallback {
            requireDispatched()
            PythonRuntimeValidation.validateExecutionError(error)
            require(error.requestId == expectedRequest.requestId) { "Error request ID does not match" }
            acceptTerminal(Terminal.FAILED)
        }
    }

    @Synchronized
    fun onCancelled(cancellation: PythonExecutionCancellation) {
        acceptCallback {
            requireOpen()
            check(cancelRequested) { "Cancellation terminal arrived without a cancellation request" }
            PythonRuntimeValidation.validateCancellation(cancellation)
            require(cancellation.requestId == expectedRequest.requestId) { "Cancellation request ID does not match" }
            acceptTerminal(Terminal.CANCELLED)
        }
    }

    @Synchronized
    fun requestCancel(): Boolean {
        if (state == State.TERMINAL || state == State.CLOSED || cancelRequested) return false
        cancelRequested = true
        return true
    }

    @Synchronized
    fun close(): Boolean {
        if (state == State.CLOSED) return false
        state = State.CLOSED
        return true
    }

    private fun acceptTerminal(value: Terminal) {
        check(state != State.TERMINAL) { "A terminal event has already been accepted" }
        check(state != State.CLOSED) { "Session is closed" }
        terminal = value
        state = State.TERMINAL
    }

    private fun requireOpen() {
        check(state != State.CLOSED) { "Session is closed" }
        check(state != State.TERMINAL) { "Session is terminal" }
    }

    private fun requireStarted() {
        requireOpen()
        check(state == State.STARTED) { "Session has not started" }
    }

    private fun requireDispatched() {
        requireOpen()
        check(state == State.START_REQUESTED || state == State.STARTED) { "Session was not dispatched" }
    }

    private fun requireDataAllowed() {
        check(!cancelRequested) { "Cancellation has been requested" }
    }

    private inline fun acceptCallback(block: () -> Unit) {
        try {
            block()
        } catch (error: RuntimeException) {
            if (state != State.TERMINAL && state != State.CLOSED) {
                state = State.CLOSED
            }
            throw error
        }
    }
}
