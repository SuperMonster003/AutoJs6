package org.autojs.autojs.core.plugin.terminal

/**
 * Host-side view of one terminal plugin failure (plugin roadmap P1.2, appendix B.4): a code from
 * `TerminalErrorCodes`, a bounded message and the session the failure belongs to when there is one.
 */
data class TerminalError(
    val code: String,
    val message: String,
    val sessionId: String? = null,
)

/** Thrown by the terminal host client; the script layer (plugin roadmap P4) turns it into the `TerminalError` JS object. */
class TerminalPluginException(val error: TerminalError, cause: Throwable? = null) : RuntimeException("${error.code}: ${error.message}", cause) {

    val code: String
        get() = error.code
}
