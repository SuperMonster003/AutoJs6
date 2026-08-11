package org.autojs.plugin.python.runtime.fake

/** Provider-side lifecycle used by the fake. Real callback acceptance is host-owned. */
class FakePythonSessionMachine(
    private val maximumCredits: Int,
    firstOutputSequence: Long,
) {
    enum class State { CREATED, STARTED, TERMINAL, CLOSED }
    enum class Terminal { RESULT, FAILED, CANCELLED }

    init {
        require(maximumCredits > 0)
        require(firstOutputSequence >= 0L)
    }

    @get:Synchronized
    var state: State = State.CREATED
        private set

    @get:Synchronized
    var terminal: Terminal? = null
        private set

    @get:Synchronized
    var outstandingCredits: Int = 0
        private set

    private var nextSequence = firstOutputSequence

    @Synchronized
    fun start(): Boolean = when (state) {
        State.CREATED -> {
            state = State.STARTED
            true
        }
        State.STARTED, State.TERMINAL, State.CLOSED -> false
    }

    @Synchronized
    fun grantCredits(count: Int) {
        check(state == State.CREATED || state == State.STARTED) { "Session cannot accept output credits" }
        require(count > 0) { "Output credit grant must be positive" }
        require(count <= maximumCredits - outstandingCredits) { "Output credit window overflow" }
        outstandingCredits += count
    }

    @Synchronized
    fun consumeOutputCredit(): Long {
        check(state == State.STARTED) { "Output requires a started session" }
        check(outstandingCredits > 0) { "Output requires credit" }
        check(nextSequence < Long.MAX_VALUE) { "Output sequence is exhausted" }
        outstandingCredits -= 1
        return nextSequence++
    }

    @Synchronized
    fun finish(value: Terminal): Boolean {
        if (state == State.TERMINAL || state == State.CLOSED) return false
        terminal = value
        state = State.TERMINAL
        return true
    }

    fun cancel(): Boolean = finish(Terminal.CANCELLED)

    @Synchronized
    fun close(): Boolean {
        if (state == State.CLOSED) return false
        state = State.CLOSED
        return true
    }
}
