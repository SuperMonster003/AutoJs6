package org.autojs.plugin.lua.runtime.api

class LuaOutputCreditWindow(
    private val maximumCredits: Int = LuaRuntimeContract.MAX_OUTSTANDING_OUTPUT_CREDITS,
) {
    @get:Synchronized
    var outstandingCredits: Int = 0
        private set

    init {
        require(maximumCredits in 1..LuaRuntimeContract.MAX_OUTSTANDING_OUTPUT_CREDITS) {
            "Output credit window exceeds the protocol limit"
        }
    }

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

class LuaExecutionSessionPolicy(
    private val request: LuaExecutionRequest,
    private val runtimeInfo: LuaRuntimeInfo,
) {
    enum class State {
        CREATED,
        STARTED,
        TERMINAL,
        CLOSED,
    }

    enum class Terminal {
        COMPLETED,
        FAILED,
        CANCELLED,
    }

    private val credits = LuaOutputCreditWindow()
    private var nextOutputSequence: Long? = null
    private var startRequested = false
    private var cancelRequested = false

    init {
        LuaRuntimeValidation.validateRequestAgainst(request, runtimeInfo, request.protocolVersion)
    }

    @get:Synchronized
    var state: State = State.CREATED
        private set

    @get:Synchronized
    var terminal: Terminal? = null
        private set

    @get:Synchronized
    var emittedOutputBytes: Long = 0L
        private set

    val outstandingOutputCredits: Int
        get() = credits.outstandingCredits

    @Synchronized
    fun requestStart(): Boolean {
        requireOpen()
        requireDataAllowed()
        if (startRequested) return false
        startRequested = true
        return true
    }

    @Synchronized
    fun grantOutputCredits(count: Int) {
        requireOpen()
        requireDataAllowed()
        credits.grant(count)
    }

    @Synchronized
    fun onStarted(started: LuaExecutionStarted) {
        requireOpen()
        requireDataAllowed()
        check(startRequested) { "Execution start was not requested" }
        check(state == State.CREATED) { "Execution start is duplicated or out of order" }
        LuaRuntimeValidation.validateStarted(started)
        requireMatchingRequest(started.requestId)
        require(started.protocolVersion == request.protocolVersion) { "Started protocol version does not match" }
        require(started.runtimeSlot == runtimeInfo.runtimeSlot) { "Started runtime slot does not match the pinned provider" }
        require(started.languageVersion == runtimeInfo.languageVersion) {
            "Started language version does not match the pinned provider"
        }
        nextOutputSequence = started.firstOutputSequence
        state = State.STARTED
    }

    @Synchronized
    fun onOutput(output: LuaOutputChunk) {
        requireStarted()
        LuaRuntimeValidation.validateOutput(output)
        requireMatchingRequest(output.requestId)
        val expectedSequence = checkNotNull(nextOutputSequence) { "Output sequence is unavailable" }
        check(output.sequence == expectedSequence) {
            "Expected output sequence $expectedSequence but received ${output.sequence}"
        }
        check(output.sequence < Long.MAX_VALUE) { "Output sequence is exhausted" }
        check(credits.outstandingCredits > 0) { "No output credit is available" }
        val outputBytes = output.text.toByteArray(Charsets.UTF_8).size.toLong()
        check(outputBytes <= request.outputByteLimit - emittedOutputBytes) {
            "Output exceeds the execution byte limit"
        }
        credits.consume()
        emittedOutputBytes += outputBytes
        nextOutputSequence = output.sequence + 1L
    }

    @Synchronized
    fun onCompleted(result: LuaExecutionResult, descriptorCount: Int = 0) {
        requireStarted()
        LuaRuntimeValidation.validateV1DescriptorCount(descriptorCount)
        LuaRuntimeValidation.validateResult(result)
        requireMatchingRequest(result.requestId)
        acceptTerminal(Terminal.COMPLETED)
    }

    @Synchronized
    fun onFailed(error: LuaExecutionError) {
        requireOpen()
        requireDataAllowed()
        check(startRequested) { "Execution start was not requested" }
        LuaRuntimeValidation.validateError(error)
        requireMatchingRequest(error.requestId)
        acceptTerminal(Terminal.FAILED)
    }

    @Synchronized
    fun onCancelled(cancellation: LuaExecutionCancellation) {
        requireOpen()
        check(startRequested || cancelRequested) { "Execution was neither started nor cancelled" }
        LuaRuntimeValidation.validateCancellation(cancellation)
        requireMatchingRequest(cancellation.requestId)
        acceptTerminal(Terminal.CANCELLED)
    }

    @Synchronized
    fun requestCancel(): Boolean {
        if (state == State.CLOSED || state == State.TERMINAL || cancelRequested) return false
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
        check(terminal == null) { "A terminal event has already been accepted" }
        terminal = value
        state = State.TERMINAL
    }

    private fun requireMatchingRequest(value: LuaRequestId) {
        require(value == request.requestId) { "Callback request ID does not match" }
    }

    private fun requireStarted() {
        requireOpen()
        requireDataAllowed()
        check(state == State.STARTED) { "Execution has not started" }
    }

    private fun requireOpen() {
        check(state != State.CLOSED) { "Execution session is closed" }
        check(state != State.TERMINAL) { "Execution session is terminal" }
    }

    private fun requireDataAllowed() {
        check(!cancelRequested) { "Execution cancellation was requested" }
    }
}

class LuaHostCallPolicy(
    private val executionId: LuaRequestId,
    allowedCapabilities: Collection<String>,
    private val maximumOutstandingCalls: Int = LuaRuntimeContract.MAX_CONCURRENT_HOST_CALLS,
    private val maximumTotalCalls: Int = LuaRuntimeContract.MAX_HOST_CALLS_PER_EXECUTION,
) {
    private val allowedCapabilities = allowedCapabilities.toSet()
    private val seenCallIds = linkedSetOf<String>()
    private val outstandingCallIds = linkedSetOf<String>()
    private var closed = false

    init {
        require(maximumOutstandingCalls in 1..LuaRuntimeContract.MAX_CONCURRENT_HOST_CALLS) {
            "Outstanding host call limit exceeds the protocol limit"
        }
        require(maximumTotalCalls in 1..LuaRuntimeContract.MAX_HOST_CALLS_PER_EXECUTION) {
            "Host call lifetime limit exceeds the protocol limit"
        }
        require(maximumOutstandingCalls <= maximumTotalCalls)
    }

    val outstandingCount: Int
        @Synchronized get() = outstandingCallIds.size

    @Synchronized
    fun open(request: LuaHostCallRequest, descriptorCount: Int = 0) {
        check(!closed) { "Host call policy is closed" }
        LuaRuntimeValidation.validateV1DescriptorCount(descriptorCount)
        LuaRuntimeValidation.validateHostRequest(request)
        require(request.executionId == executionId) { "Host request execution ID does not match" }
        require(request.capability in allowedCapabilities) { "Host capability was not granted" }
        check(request.callId !in seenCallIds) { "Host call ID was replayed" }
        check(seenCallIds.size < maximumTotalCalls) { "Host call lifetime limit is exhausted" }
        check(outstandingCallIds.size < maximumOutstandingCalls) { "Too many host calls are outstanding" }
        seenCallIds += request.callId
        outstandingCallIds += request.callId
    }

    @Synchronized
    fun complete(result: LuaHostCallResult, descriptorCount: Int = 0) {
        check(!closed) { "Host call policy is closed" }
        LuaRuntimeValidation.validateV1DescriptorCount(descriptorCount)
        LuaRuntimeValidation.validateHostResult(result)
        require(result.executionId == executionId) { "Host result execution ID does not match" }
        closeCall(result.callId)
    }

    @Synchronized
    fun fail(error: LuaHostCallError) {
        check(!closed) { "Host call policy is closed" }
        LuaRuntimeValidation.validateHostError(error)
        require(error.executionId == executionId) { "Host error execution ID does not match" }
        closeCall(error.callId)
    }

    @Synchronized
    fun close(): Boolean {
        if (closed) return false
        closed = true
        outstandingCallIds.clear()
        seenCallIds.clear()
        return true
    }

    private fun closeCall(callId: String) {
        check(outstandingCallIds.remove(callId)) { "Host call is not outstanding" }
    }
}
