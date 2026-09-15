package org.autojs.plugin.mcp.server.fake

import org.autojs.plugin.mcp.server.api.McpServerContract

/**
 * State of one fake server session without any Android dependency: `opened` until the startup
 * probe finished, `running` while the (pretend) listener is up, `stopped` after `stop()`, and
 * `closed` once the host released it. No socket is ever opened; the endpoint is a string.
 */
class FakeMcpSessionMachine(port: Int) {

    enum class State { OPENED, RUNNING, STOPPED, CLOSED }

    var port: Int = port
        private set

    var state: State = State.OPENED
        private set

    var startedAt: Long = 0L
        private set

    var lastReasonCode: String? = null
        private set

    /** Returns false when the session is closed or already running. */
    fun markRunning(now: Long): Boolean {
        if (state != State.OPENED && state != State.STOPPED) return false
        state = State.RUNNING
        startedAt = now
        lastReasonCode = null
        return true
    }

    /** Returns true only on the transition from running; later calls are no-ops. */
    fun stop(reasonCode: String?): Boolean {
        if (state != State.RUNNING) return false
        state = State.STOPPED
        lastReasonCode = reasonCode ?: McpServerContract.REASON_USER_REQUEST
        return true
    }

    /** Returns true on the first close; the caller decides whether stop callbacks are due. */
    fun close(): Boolean {
        if (state == State.CLOSED) return false
        state = State.CLOSED
        return true
    }

    fun updatePort(newPort: Int) {
        port = newPort
    }

    val contractState: String
        get() = when (state) {
            State.OPENED -> McpServerContract.STATE_STARTING
            State.RUNNING -> McpServerContract.STATE_RUNNING
            State.STOPPED, State.CLOSED -> McpServerContract.STATE_STOPPED
        }

    val endpoints: List<String>
        get() = if (state == State.RUNNING) listOf(FakeMcpServerFixture.endpoint(port)) else emptyList()

    val boundPort: Int
        get() = if (state == State.RUNNING) port else 0
}
