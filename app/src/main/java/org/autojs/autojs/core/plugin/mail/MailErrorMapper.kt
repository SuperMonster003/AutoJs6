package org.autojs.autojs.core.plugin.mail

import android.os.DeadObjectException
import android.os.RemoteException
import kotlinx.coroutines.TimeoutCancellationException
import org.autojs.plugin.mail.api.MailContract
import org.autojs.plugin.mail.api.MailErrorCodes
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.charset.StandardCharsets
import kotlin.coroutines.cancellation.CancellationException

/**
 * Normalizes plugin error envelopes and host-side failures into [MailError] values (roadmap P1.2).
 * Unknown codes collapse to `INTERNAL`, messages are truncated to the contract ceiling, and the
 * retry hint falls back to the default set of `MailErrorCodes` when the envelope omits it.
 */
object MailErrorMapper {

    /** Where a throwable was raised; the same exception type maps differently per phase. */
    enum class Phase { DISCOVERY, OPEN, CALL, TRANSFER }

    fun normalize(code: String?, message: String?, retryable: Boolean?, details: String? = null): MailError {
        val trimmedCode = code?.trim().orEmpty()
        val knownCode = if (MailErrorCodes.isKnown(trimmedCode)) trimmedCode else MailErrorCodes.INTERNAL
        val text = buildString {
            if (knownCode != trimmedCode && trimmedCode.isNotEmpty()) {
                append("unknown error code ").append(trimmedCode)
                if (!message.isNullOrBlank()) append(": ")
            }
            append(message?.trim().orEmpty())
        }.ifBlank { defaultMessage(knownCode) }
        return MailError(
            code = knownCode,
            message = truncate(text, MailContract.MAX_ERROR_MESSAGE_BYTES),
            retryable = retryable ?: MailErrorCodes.isRetryableByDefault(knownCode),
            details = details,
        )
    }

    fun fromThrowable(throwable: Throwable, phase: Phase): MailError = when (throwable) {
        is MailPluginException -> throwable.error
        is TimeoutCancellationException -> error(MailErrorCodes.TIMEOUT, throwable.message ?: "mail operation timed out", retryable = true)
        is CancellationException -> error(MailErrorCodes.CANCELLED, "mail operation cancelled", retryable = false)
        is InterruptedException, is InterruptedIOException -> error(MailErrorCodes.CANCELLED, "mail operation interrupted", retryable = false)
        is DeadObjectException -> when (phase) {
            Phase.CALL, Phase.TRANSFER -> error(MailErrorCodes.SESSION_CLOSED, "mail plugin process died", retryable = true)
            else -> error(MailErrorCodes.PLUGIN_UNAVAILABLE, "mail plugin process died", retryable = true)
        }
        is RemoteException -> when (phase) {
            Phase.CALL, Phase.TRANSFER -> error(MailErrorCodes.SESSION_CLOSED, describe("mail plugin call failed", throwable), retryable = true)
            else -> error(MailErrorCodes.PLUGIN_UNAVAILABLE, describe("mail plugin binding failed", throwable), retryable = false)
        }
        is SecurityException -> error(MailErrorCodes.PLUGIN_UNAVAILABLE, describe("mail plugin refused the caller", throwable), retryable = false)
        is IllegalArgumentException -> error(MailErrorCodes.INVALID_ARGUMENT, describe("invalid argument", throwable), retryable = false)
        is IllegalStateException -> when (phase) {
            Phase.DISCOVERY, Phase.OPEN -> error(MailErrorCodes.PLUGIN_UNAVAILABLE, describe("mail plugin is unavailable", throwable), retryable = false)
            else -> error(MailErrorCodes.INTERNAL, describe("mail host failure", throwable), retryable = false)
        }
        is FileNotFoundException -> error(MailErrorCodes.ATTACHMENT_NOT_FOUND, describe("attachment not found", throwable), retryable = false)
        is IOException -> error(MailErrorCodes.IO_FAILED, describe("mail transfer failed", throwable), retryable = false)
        else -> error(MailErrorCodes.INTERNAL, describe("mail host failure", throwable), retryable = false)
    }

    fun defaultMessage(code: String): String = when (code) {
        MailErrorCodes.INVALID_ARGUMENT -> "invalid argument"
        MailErrorCodes.NO_DEFAULT_ACCOUNT -> "no default mail account"
        MailErrorCodes.ACCOUNT_NOT_FOUND -> "mail account not found"
        MailErrorCodes.PROVIDER_UNKNOWN -> "unknown mail provider"
        MailErrorCodes.AUTH_FAILED -> "mail authentication failed"
        MailErrorCodes.AUTH_MECHANISM_UNSUPPORTED -> "authentication mechanism unsupported"
        MailErrorCodes.CONNECT_FAILED -> "mail server connection failed"
        MailErrorCodes.TLS_FAILED -> "TLS negotiation failed"
        MailErrorCodes.TIMEOUT -> "mail operation timed out"
        MailErrorCodes.CANCELLED -> "mail operation cancelled"
        MailErrorCodes.FOLDER_NOT_FOUND -> "mail folder not found"
        MailErrorCodes.MESSAGE_NOT_FOUND -> "mail message not found"
        MailErrorCodes.ATTACHMENT_NOT_FOUND -> "attachment not found"
        MailErrorCodes.UNSUPPORTED_OPERATION -> "operation unsupported"
        MailErrorCodes.LIMIT_EXCEEDED -> "limit exceeded"
        MailErrorCodes.IO_FAILED -> "mail transfer failed"
        MailErrorCodes.SESSION_CLOSED -> "mail session closed"
        MailErrorCodes.WATCH_CLOSED -> "mail watch closed"
        MailErrorCodes.SEND_REJECTED -> "mail rejected by the server"
        MailErrorCodes.SERVER_ERROR -> "mail server error"
        MailErrorCodes.PLUGIN_UNAVAILABLE -> "mail plugin is unavailable"
        else -> "mail operation failed"
    }

    /** Truncates to at most [maxBytes] of UTF-8 without splitting a code point. */
    fun truncate(text: String, maxBytes: Int): String {
        if (text.toByteArray(StandardCharsets.UTF_8).size <= maxBytes) return text
        var end = 0
        var bytes = 0
        while (end < text.length) {
            val codePoint = text.codePointAt(end)
            val width = Character.charCount(codePoint)
            val size = String(Character.toChars(codePoint)).toByteArray(StandardCharsets.UTF_8).size
            if (bytes + size > maxBytes - ELLIPSIS_BYTES) break
            bytes += size
            end += width
        }
        return text.substring(0, end) + ELLIPSIS
    }

    private fun error(code: String, message: String, retryable: Boolean) = normalize(code, message, retryable)

    private fun describe(prefix: String, throwable: Throwable): String {
        val detail = throwable.message?.trim().orEmpty()
        return if (detail.isEmpty()) prefix else "$prefix: $detail"
    }

    private const val ELLIPSIS = "..."
    private const val ELLIPSIS_BYTES = 3
}
