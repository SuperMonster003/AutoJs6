package org.autojs.plugin.ai.text.fake

import org.autojs.plugin.ai.text.api.AiTextLimits

class FakeAiSessionMachine(
    private val maximumCredits: Int = AiTextLimits.MAX_OUTSTANDING_CHUNK_CREDITS,
    private val maximumToolRounds: Int,
    private val maximumOutstandingToolCalls: Int,
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

    private val seenToolCallIds = linkedSetOf<String>()
    private var outstandingToolCallIds: Set<String> = emptySet()
    private var nextChunkSequence = 0L

    var state = State.CREATED
        private set

    var terminal: Terminal? = null
        private set

    var credits = 0
        private set

    var completedToolRounds = 0
        private set

    val hasOutstandingToolTurn: Boolean
        @Synchronized get() = outstandingToolCallIds.isNotEmpty()

    val isActive: Boolean
        @Synchronized get() = state == State.CREATED || state == State.STARTED

    init {
        require(maximumCredits > 0)
        require(maximumToolRounds in 0..AiTextLimits.MAX_TOOL_ROUNDS)
        require(maximumOutstandingToolCalls in 0..AiTextLimits.MAX_OUTSTANDING_TOOL_CALLS)
    }

    @Synchronized
    fun grantCredits(count: Int) {
        requireOpen()
        require(count > 0) { "Chunk credit grant must be positive" }
        require(count <= maximumCredits - credits) { "Chunk credit window overflow" }
        credits += count
    }

    @Synchronized
    fun start(firstChunkSequence: Long = 0L) {
        check(state == State.CREATED) { "Fake session start is duplicated or out of order" }
        require(firstChunkSequence >= 0L)
        nextChunkSequence = firstChunkSequence
        state = State.STARTED
    }

    @Synchronized
    fun reserveNextChunk(): Long? {
        requireStarted()
        check(outstandingToolCallIds.isEmpty()) { "Chunks are paused during a tool turn" }
        if (credits == 0) return null
        check(nextChunkSequence < Long.MAX_VALUE) { "Chunk sequence is exhausted" }
        val reserved = nextChunkSequence
        nextChunkSequence += 1L
        credits -= 1
        return reserved
    }

    @Synchronized
    fun openToolTurn(callIds: Collection<String>) {
        requireStarted()
        check(outstandingToolCallIds.isEmpty()) { "A fake tool turn is already outstanding" }
        check(completedToolRounds < maximumToolRounds) { "Fake tool round limit is exhausted" }
        require(callIds.isNotEmpty()) { "A fake tool turn must contain a call" }
        require(callIds.size <= maximumOutstandingToolCalls) { "Too many fake tool calls" }
        val ids = callIds.toSet()
        require(ids.size == callIds.size) { "Duplicate fake tool call ID" }
        require(ids.none { it in seenToolCallIds }) { "Fake tool call ID was replayed" }
        seenToolCallIds += ids
        outstandingToolCallIds = ids
    }

    @Synchronized
    fun submitToolResults(callIds: Collection<String>) {
        requireStarted()
        check(outstandingToolCallIds.isNotEmpty()) { "No fake tool turn is outstanding" }
        val ids = callIds.toSet()
        require(ids.size == callIds.size) { "Duplicate fake tool result ID" }
        require(ids == outstandingToolCallIds) { "Fake tool results do not exactly match the calls" }
        outstandingToolCallIds = emptySet()
        completedToolRounds += 1
    }

    @Synchronized
    fun complete(): Boolean {
        requireStarted()
        check(outstandingToolCallIds.isEmpty()) { "Completion cannot bypass a fake tool turn" }
        return acceptTerminal(Terminal.COMPLETED)
    }

    @Synchronized
    fun fail(): Boolean {
        requireOpen()
        return acceptTerminal(Terminal.FAILED)
    }

    @Synchronized
    fun cancel(): Boolean {
        if (state == State.CLOSED || state == State.TERMINAL) return false
        return acceptTerminal(Terminal.CANCELLED)
    }

    @Synchronized
    fun close(): Boolean {
        if (state == State.CLOSED) return false
        state = State.CLOSED
        return true
    }

    private fun acceptTerminal(value: Terminal): Boolean {
        if (terminal != null) return false
        terminal = value
        state = State.TERMINAL
        return true
    }

    private fun requireOpen() {
        check(state != State.CLOSED) { "Fake session is closed" }
        check(state != State.TERMINAL) { "Fake session is terminal" }
    }

    private fun requireStarted() {
        requireOpen()
        check(state == State.STARTED) { "Fake session has not started" }
    }
}
