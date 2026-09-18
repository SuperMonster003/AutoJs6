package org.autojs.autojs.core.plugin.mail

/**
 * Host-side view of one mail failure: a code from `MailErrorCodes`, a message that never carries
 * credentials, the retry hint of the response envelope, and an optional JSON details document.
 */
data class MailError(
    val code: String,
    val message: String,
    val retryable: Boolean,
    val details: String? = null,
)

/** Thrown by the mail host client; the script layer turns it into the `MailError` JS object. */
class MailPluginException(val error: MailError, cause: Throwable? = null) : RuntimeException("${error.code}: ${error.message}", cause) {

    val code: String
        get() = error.code

    val retryable: Boolean
        get() = error.retryable

    val details: String?
        get() = error.details
}
