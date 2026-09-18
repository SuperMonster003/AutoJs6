package org.autojs.plugin.mail.api

/**
 * Error codes of the response envelope (`error.code`) and of the host-side `MailError`. The set
 * is closed for contract version 1; a code the host does not know is reported as [INTERNAL].
 */
object MailErrorCodes {

    const val INVALID_ARGUMENT = "INVALID_ARGUMENT"
    const val NO_DEFAULT_ACCOUNT = "NO_DEFAULT_ACCOUNT"
    const val ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND"
    const val PROVIDER_UNKNOWN = "PROVIDER_UNKNOWN"
    const val AUTH_FAILED = "AUTH_FAILED"
    const val AUTH_MECHANISM_UNSUPPORTED = "AUTH_MECHANISM_UNSUPPORTED"
    const val CONNECT_FAILED = "CONNECT_FAILED"
    const val TLS_FAILED = "TLS_FAILED"
    const val TIMEOUT = "TIMEOUT"
    const val CANCELLED = "CANCELLED"
    const val FOLDER_NOT_FOUND = "FOLDER_NOT_FOUND"
    const val MESSAGE_NOT_FOUND = "MESSAGE_NOT_FOUND"
    const val ATTACHMENT_NOT_FOUND = "ATTACHMENT_NOT_FOUND"
    const val UNSUPPORTED_OPERATION = "UNSUPPORTED_OPERATION"
    const val LIMIT_EXCEEDED = "LIMIT_EXCEEDED"
    const val IO_FAILED = "IO_FAILED"
    const val SESSION_CLOSED = "SESSION_CLOSED"
    const val WATCH_CLOSED = "WATCH_CLOSED"
    const val SEND_REJECTED = "SEND_REJECTED"
    const val SERVER_ERROR = "SERVER_ERROR"
    const val PLUGIN_UNAVAILABLE = "PLUGIN_UNAVAILABLE"
    const val INTERNAL = "INTERNAL"

    /** Every code of contract version 1, in the order of the protocol document. */
    val ALL: List<String> = listOf(
        INVALID_ARGUMENT,
        NO_DEFAULT_ACCOUNT,
        ACCOUNT_NOT_FOUND,
        PROVIDER_UNKNOWN,
        AUTH_FAILED,
        AUTH_MECHANISM_UNSUPPORTED,
        CONNECT_FAILED,
        TLS_FAILED,
        TIMEOUT,
        CANCELLED,
        FOLDER_NOT_FOUND,
        MESSAGE_NOT_FOUND,
        ATTACHMENT_NOT_FOUND,
        UNSUPPORTED_OPERATION,
        LIMIT_EXCEEDED,
        IO_FAILED,
        SESSION_CLOSED,
        WATCH_CLOSED,
        SEND_REJECTED,
        SERVER_ERROR,
        PLUGIN_UNAVAILABLE,
        INTERNAL,
    )

    /**
     * Codes whose `retryable` flag defaults to true when the response omits it. [SERVER_ERROR]
     * depends on the server reply and [PLUGIN_UNAVAILABLE] only on the process-death branch, so a
     * plugin should set the flag explicitly for those two.
     */
    val RETRYABLE_DEFAULTS: Set<String> = setOf(
        CONNECT_FAILED,
        TIMEOUT,
        SESSION_CLOSED,
        SERVER_ERROR,
        PLUGIN_UNAVAILABLE,
    )

    fun isKnown(code: String?): Boolean = code != null && code in ALL

    fun isRetryableByDefault(code: String?): Boolean = code != null && code in RETRYABLE_DEFAULTS
}
