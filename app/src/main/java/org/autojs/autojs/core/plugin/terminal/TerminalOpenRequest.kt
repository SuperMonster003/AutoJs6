package org.autojs.autojs.core.plugin.terminal

import org.autojs.plugin.terminal.api.TerminalContract

/**
 * One request to show the plugin's terminal UI (plugin roadmap D19 / D25): the host starts the
 * plugin's exported entry Activity with these extras. Pure data so the JVM tests can check the
 * extras the five launcher entry points produce.
 */
data class TerminalOpenRequest(
    /** Directory the session starts in; null lets the plugin pick its home. */
    val directory: String? = null,
    /** Bring this existing session to the front instead of creating one. */
    val sessionId: String? = null,
    /** Always create a new session, even when one already runs in [directory]. */
    val newSession: Boolean = false,
    /** Command line executed after the shell starts in the new session. */
    val command: String? = null,
    /** Open the session manager instead of a session. */
    val manager: Boolean = false,
) {

    /** The Intent extras in contract order; defaults are omitted so an empty request carries none. */
    fun extras(): Map<String, Any> = buildMap {
        directory?.takeIf { it.isNotBlank() }?.let { put(TerminalContract.EXTRA_DIRECTORY, it) }
        sessionId?.takeIf { it.isNotBlank() }?.let { put(TerminalContract.EXTRA_SESSION_ID, it) }
        if (newSession) put(TerminalContract.EXTRA_NEW_SESSION, true)
        command?.takeIf { it.isNotEmpty() }?.let { put(TerminalContract.EXTRA_COMMAND, it) }
        if (manager) put(TerminalContract.EXTRA_MANAGER, true)
    }

    /** Null when the request honors the contract ceilings and combines its extras sensibly. */
    fun validate(): String? = when {
        directory != null && (directory.isBlank() || TerminalJson.utf8Size(directory) > TerminalContract.MAX_PATH_BYTES) ->
            "directory is blank or longer than ${TerminalContract.MAX_PATH_BYTES} bytes"
        sessionId != null && sessionId.isBlank() -> "sessionId is blank"
        command != null && TerminalJson.utf8Size(command) > TerminalContract.MAX_COMMAND_BYTES ->
            "command is longer than ${TerminalContract.MAX_COMMAND_BYTES} bytes"
        manager && (sessionId != null || newSession || command != null) -> "manager cannot be combined with a session request"
        sessionId != null && (newSession || command != null) -> "sessionId cannot be combined with newSession or command"
        else -> null
    }

    companion object {
        fun open(directory: String? = null) = TerminalOpenRequest(directory = directory)
        fun openNew(directory: String? = null) = TerminalOpenRequest(directory = directory, newSession = true)
        fun session(sessionId: String) = TerminalOpenRequest(sessionId = sessionId)
        fun manager() = TerminalOpenRequest(manager = true)
    }
}
