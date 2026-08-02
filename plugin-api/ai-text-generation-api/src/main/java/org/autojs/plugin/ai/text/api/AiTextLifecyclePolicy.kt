package org.autojs.plugin.ai.text.api

import org.autojs.plugin.ai.common.api.AiUsage
import org.autojs.plugin.ai.common.api.AiUsagePolicy

class AiCreditWindow(
    private val maximumCredits: Int = AiTextLimits.MAX_OUTSTANDING_CHUNK_CREDITS,
) {
    var outstandingCredits: Int = 0
        private set

    @Synchronized
    fun grant(count: Int) {
        require(count > 0) { "Credit grant must be positive" }
        require(count <= maximumCredits - outstandingCredits) { "Credit window overflow" }
        outstandingCredits += count
    }

    @Synchronized
    fun consume() {
        check(outstandingCredits > 0) { "No chunk credit is available" }
        outstandingCredits -= 1
    }

    @Synchronized
    fun requireAvailable() {
        check(outstandingCredits > 0) { "No chunk credit is available" }
    }
}

class AiChunkSequencePolicy {
    private var nextSequence: Long? = null

    @Synchronized
    fun start(firstSequence: Long) {
        require(firstSequence >= 0L)
        check(nextSequence == null) { "Chunk sequence has already started" }
        nextSequence = firstSequence
    }

    @Synchronized
    fun accept(sequence: Long) {
        requireNext(sequence)
        check(sequence < Long.MAX_VALUE) { "Chunk sequence is exhausted" }
        nextSequence = sequence + 1L
    }

    @Synchronized
    fun requireNext(sequence: Long) {
        val expected = checkNotNull(nextSequence) { "Chunk sequence has not started" }
        check(sequence == expected) { "Expected chunk sequence $expected but received $sequence" }
        check(sequence < Long.MAX_VALUE) { "Chunk sequence is exhausted" }
    }
}

class AiTerminalGate {
    enum class Terminal {
        COMPLETED,
        FAILED,
        CANCELLED,
    }

    var terminal: Terminal? = null
        private set

    @Synchronized
    fun accept(value: Terminal) {
        check(terminal == null) { "A terminal event has already been accepted" }
        terminal = value
    }
}

class AiToolTurnPolicy(
    private val maximumRounds: Int,
    private val maximumOutstandingCalls: Int = AiTextLimits.MAX_OUTSTANDING_TOOL_CALLS,
) {
    private val seenCallIds = linkedSetOf<String>()
    private var outstandingCallIds: Set<String> = emptySet()

    var completedRounds: Int = 0
        private set

    var totalToolDataBytes: Long = 0L
        private set

    val hasOutstandingTurn: Boolean
        @Synchronized get() = outstandingCallIds.isNotEmpty()

    init {
        require(maximumRounds in 0..AiTextLimits.MAX_TOOL_ROUNDS)
        require(maximumOutstandingCalls in 0..AiTextLimits.MAX_OUTSTANDING_TOOL_CALLS)
    }

    @Synchronized
    fun open(batch: AiToolCallBatch) {
        check(outstandingCallIds.isEmpty()) { "A tool turn is already outstanding" }
        check(completedRounds < maximumRounds) { "Tool round limit is exhausted" }
        require(batch.calls.size <= maximumOutstandingCalls) { "Too many outstanding tool calls" }
        val ids = batch.calls.mapTo(linkedSetOf()) { it.callId }
        require(ids.none { it in seenCallIds }) { "A tool call ID was replayed" }
        addToolData(batch.calls.map { it.arguments.declaredLengthBytes })
        seenCallIds += ids
        outstandingCallIds = ids
    }

    @Synchronized
    fun submit(batch: AiToolResultBatch) {
        check(outstandingCallIds.isNotEmpty()) { "No tool turn is outstanding" }
        val ids = batch.results.mapTo(linkedSetOf()) { it.callId }
        require(ids == outstandingCallIds) { "Tool results do not exactly match the outstanding calls" }
        addToolData(batch.results.map { it.output.declaredLengthBytes })
        outstandingCallIds = emptySet()
        completedRounds += 1
    }

    private fun addToolData(lengths: List<Long>) {
        var next = totalToolDataBytes
        lengths.forEach { length ->
            require(length in 0L..AiTextLimits.MAX_TOOL_ARGUMENT_OR_RESULT_BYTES)
            require(next <= Long.MAX_VALUE - length) { "Tool data byte count overflow" }
            next += length
        }
        require(next <= AiTextLimits.MAX_TOTAL_TOOL_DATA_BYTES) { "Tool data exceeds the session quota" }
        totalToolDataBytes = next
    }
}

class AiTextSessionPolicy(
    private val maximumToolRounds: Int,
    maximumOutstandingToolCalls: Int = AiTextLimits.MAX_OUTSTANDING_TOOL_CALLS,
    private val maximumOutputBytes: Long = AiTextLimits.MAX_STREAMING_OUTPUT_BYTES,
    private val expectedRequest: AiTextRequest? = null,
    private val maximumSessionDescriptors: Int = org.autojs.plugin.ai.common.api.AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION,
) {
    enum class State {
        CREATED,
        STARTED,
        TERMINAL,
        CLOSED,
    }

    private val credits = AiCreditWindow()
    private val sequences = AiChunkSequencePolicy()
    private val terminals = AiTerminalGate()
    private val tools = AiToolTurnPolicy(maximumToolRounds, maximumOutstandingToolCalls)
    private var usage: AiUsage? = null
    private var cancelRequested = false
    private var emittedOutputBytes = 0L
    private var effectiveMaximumOutputBytes: Long? = null
    private var effectiveMaximumToolRounds: Int? = null

    init {
        require(maximumOutputBytes in 1L..AiTextLimits.MAX_STREAMING_OUTPUT_BYTES)
        require(
            maximumSessionDescriptors in 0..org.autojs.plugin.ai.common.api.AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION,
        )
    }

    var state: State = State.CREATED
        private set

    val outstandingCredits: Int
        get() = credits.outstandingCredits

    val completedToolRounds: Int
        get() = tools.completedRounds

    val terminal: AiTerminalGate.Terminal?
        get() = terminals.terminal

    @Synchronized
    fun grantCredits(count: Int) {
        requireOpen()
        requireDataAllowed()
        credits.grant(count)
    }

    @Synchronized
    fun onStarted(metadata: AiSessionStarted) {
        requireOpen()
        requireDataAllowed()
        check(state == State.CREATED) { "Session start is duplicated or out of order" }
        require(metadata.effectiveMaximumOutputBytes <= maximumOutputBytes) {
            "Provider output limit exceeds the negotiated session limit"
        }
        require(metadata.effectiveMaximumToolRounds <= maximumToolRounds) {
            "Provider tool-round limit exceeds the negotiated session limit"
        }
        expectedRequest?.let { request ->
            require(metadata.requestId == request.requestId) { "Session request ID does not match" }
            require(metadata.protocolVersion == request.protocolVersion) { "Session protocol version does not match" }
            require(metadata.providerId == request.providerId) { "Session provider ID does not match" }
            require(metadata.modelId == request.modelId) { "Session model ID does not match" }
            require(metadata.effectiveMaximumOutputBytes <= request.options.maximumOutputBytes) {
                "Provider output limit exceeds the requested limit"
            }
            require(metadata.effectiveMaximumToolRounds <= request.options.maximumToolRounds) {
                "Provider tool-round limit exceeds the requested limit"
            }
        }
        sequences.start(metadata.firstChunkSequence)
        effectiveMaximumOutputBytes = metadata.effectiveMaximumOutputBytes
        effectiveMaximumToolRounds = metadata.effectiveMaximumToolRounds
        state = State.STARTED
    }

    @Synchronized
    fun onChunk(chunk: AiTextChunk) {
        requireStarted()
        check(!tools.hasOutstandingTurn) { "Chunks are paused while a tool turn is outstanding" }
        credits.requireAvailable()
        sequences.requireNext(chunk.sequence)
        val chunkBytes = org.autojs.plugin.ai.common.api.AiValidation.utf8Size(chunk.textDelta).toLong() +
            org.autojs.plugin.ai.common.api.AiValidation.utf8Size(chunk.reasoningDelta).toLong()
        val outputLimit = checkNotNull(effectiveMaximumOutputBytes) { "Session output limit is unavailable" }
        check(chunkBytes <= outputLimit && emittedOutputBytes <= outputLimit - chunkBytes) {
            "Streaming output exceeds the session quota"
        }
        sequences.accept(chunk.sequence)
        credits.consume()
        emittedOutputBytes += chunkBytes
    }

    @Synchronized
    fun onToolCalls(batch: AiToolCallBatch) {
        requireStarted()
        val toolRoundLimit = checkNotNull(effectiveMaximumToolRounds) { "Session tool-round limit is unavailable" }
        check(tools.completedRounds < toolRoundLimit) { "Tool round limit is exhausted" }
        tools.open(batch)
    }

    @Synchronized
    fun submitToolResults(batch: AiToolResultBatch) {
        requireStarted()
        tools.submit(batch)
    }

    @Synchronized
    fun onUsage(next: AiUsage) {
        requireStarted()
        check(AiUsagePolicy.isMonotonic(usage, next)) { "Usage counters moved backwards" }
        usage = AiUsagePolicy.mergeMonotonic(usage, next)
    }

    @Synchronized
    fun onCompleted() {
        requireStarted()
        check(!tools.hasOutstandingTurn) { "Completion cannot bypass an outstanding tool turn" }
        acceptTerminal(AiTerminalGate.Terminal.COMPLETED)
    }

    @Synchronized
    fun onCompleted(
        result: AiCompletionResult,
        descriptorCount: Int,
        readDescriptor: ((index: Int, declaredLengthBytes: Long) -> ByteArray)? = null,
    ): AiTextCompletionQuotaSnapshot {
        requireStarted()
        check(!tools.hasOutstandingTurn) { "Completion cannot bypass an outstanding tool turn" }
        val request = checkNotNull(expectedRequest) { "A request-aware session policy is required" }
        val snapshot = AiTextQuotaPolicy.validateCompletion(
            request = request,
            result = result,
            descriptorCount = descriptorCount,
            maximumDescriptors = maximumSessionDescriptors,
            observedUsage = usage,
            readDescriptor = readDescriptor,
            effectiveMaximumOutputBytes = checkNotNull(effectiveMaximumOutputBytes) {
                "Session output limit is unavailable"
            },
        )
        usage = snapshot.finalUsage
        acceptTerminal(AiTerminalGate.Terminal.COMPLETED)
        return snapshot
    }

    @Synchronized
    fun onFailed() {
        requireOpen()
        acceptTerminal(AiTerminalGate.Terminal.FAILED)
    }

    @Synchronized
    fun onCancelled() {
        requireOpen()
        acceptTerminal(AiTerminalGate.Terminal.CANCELLED)
    }

    @Synchronized
    fun requestCancel(): Boolean {
        if (state == State.CLOSED || state == State.TERMINAL) return false
        if (cancelRequested) return false
        cancelRequested = true
        return true
    }

    @Synchronized
    fun close(): Boolean {
        if (state == State.CLOSED) return false
        state = State.CLOSED
        return true
    }

    private fun acceptTerminal(value: AiTerminalGate.Terminal) {
        terminals.accept(value)
        state = State.TERMINAL
    }

    private fun requireOpen() {
        check(state != State.CLOSED) { "Session is closed" }
        check(state != State.TERMINAL) { "Session is terminal" }
    }

    private fun requireStarted() {
        requireOpen()
        requireDataAllowed()
        check(state == State.STARTED) { "Session has not started" }
    }

    private fun requireDataAllowed() {
        check(!cancelRequested) { "Session cancellation was requested" }
    }
}
