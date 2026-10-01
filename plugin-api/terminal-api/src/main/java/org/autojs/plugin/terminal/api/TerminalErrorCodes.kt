package org.autojs.plugin.terminal.api

/**
 * Error codes of the error documents (`code`) and of the script-side `TerminalError`. The set is
 * closed for contract version 1; a code the host does not know is reported as [INTERNAL].
 */
object TerminalErrorCodes {

    /** Plugin missing, disabled, unauthorized, binding failed or process gone (host side; the message tells which). */
    const val PLUGIN_UNAVAILABLE = "PLUGIN_UNAVAILABLE"
    /** Contract version or minimum host version not satisfied (host side). */
    const val PLUGIN_INCOMPATIBLE = "PLUGIN_INCOMPATIBLE"
    /** Shape or ceiling violated (TerminalContract MAX_*). */
    const val INVALID_ARGUMENT = "INVALID_ARGUMENT"
    const val SESSION_NOT_FOUND = "SESSION_NOT_FOUND"
    /** More than [TerminalContract.MAX_SESSIONS] concurrent sessions. */
    const val SESSION_LIMIT = "SESSION_LIMIT"
    /** Write or subscribe on a session whose shell exited. */
    const val SESSION_CLOSED = "SESSION_CLOSED"
    /** Directory missing, not a directory or unreadable for a reason other than shared storage. */
    const val DIRECTORY_INACCESSIBLE = "DIRECTORY_INACCESSIBLE"
    /** Shared-storage directory while the plugin lacks all-files access. */
    const val STORAGE_PERMISSION_REQUIRED = "STORAGE_PERMISSION_REQUIRED"
    /** `/dev/ptmx` or the child process could not be created. */
    const val PTY_FAILED = "PTY_FAILED"
    /** A Node.js command was requested while the toolchain is unavailable. */
    const val NODE_UNAVAILABLE = "NODE_UNAVAILABLE"
    /** More than [TerminalContract.MAX_SUBSCRIPTIONS_PER_SESSION] subscriptions on one session. */
    const val SUBSCRIPTION_LIMIT = "SUBSCRIPTION_LIMIT"
    /** `waitFor` or `wait` ran out of time (script side). */
    const val TIMEOUT = "TIMEOUT"
    /** The script stopped or the operation was cancelled explicitly. */
    const val CANCELLED = "CANCELLED"
    const val INTERNAL = "INTERNAL"

    /** Every code of contract version 1, in the order of the protocol document. */
    val ALL: List<String> = listOf(
        PLUGIN_UNAVAILABLE,
        PLUGIN_INCOMPATIBLE,
        INVALID_ARGUMENT,
        SESSION_NOT_FOUND,
        SESSION_LIMIT,
        SESSION_CLOSED,
        DIRECTORY_INACCESSIBLE,
        STORAGE_PERMISSION_REQUIRED,
        PTY_FAILED,
        NODE_UNAVAILABLE,
        SUBSCRIPTION_LIMIT,
        TIMEOUT,
        CANCELLED,
        INTERNAL,
    )

    /** Codes raised by the host before or around the Binder call, never produced by the plugin. */
    val HOST_ONLY: Set<String> = setOf(PLUGIN_UNAVAILABLE, PLUGIN_INCOMPATIBLE, TIMEOUT, CANCELLED)

    fun isKnown(code: String?): Boolean = code != null && code in ALL

    fun isHostOnly(code: String?): Boolean = code != null && code in HOST_ONLY
}
