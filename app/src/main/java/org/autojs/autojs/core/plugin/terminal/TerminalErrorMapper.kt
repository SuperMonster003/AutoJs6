package org.autojs.autojs.core.plugin.terminal

import android.os.Bundle
import android.os.DeadObjectException
import android.os.RemoteException
import kotlinx.coroutines.TimeoutCancellationException
import org.autojs.plugin.terminal.api.TerminalContract
import org.autojs.plugin.terminal.api.TerminalErrorCodes
import java.io.InterruptedIOException
import java.nio.charset.StandardCharsets
import kotlin.coroutines.cancellation.CancellationException

/**
 * Normalizes the plugin's error documents and host-side failures into [TerminalError] values
 * (plugin roadmap P1.2, appendix B.4). Bundle answers carry the error document under
 * `KEY_ERROR_JSON`; the String-returning methods of the contract encode it the same way and the
 * `void` / primitive methods throw `IllegalArgumentException` / `IllegalStateException` whose
 * message starts with the code (`CODE: detail`). Unknown codes collapse to `INTERNAL`, host-only
 * codes are never accepted from the plugin, and messages are truncated to [MAX_MESSAGE_BYTES].
 */
object TerminalErrorMapper {

    /** Where a throwable was raised; the same exception type maps differently per phase. */
    enum class Phase { DISCOVERY, OPEN, CALL }

    /** Ceiling of a normalized message; the contract bounds whole documents, not messages. */
    const val MAX_MESSAGE_BYTES = 4_096

    fun normalize(code: String?, message: String?, sessionId: String? = null): TerminalError {
        val trimmedCode = code?.trim().orEmpty()
        val knownCode = if (TerminalErrorCodes.isKnown(trimmedCode)) trimmedCode else TerminalErrorCodes.INTERNAL
        val text = buildString {
            if (knownCode != trimmedCode && trimmedCode.isNotEmpty()) {
                append("unknown error code ").append(trimmedCode)
                if (!message.isNullOrBlank()) append(": ")
            }
            append(message?.trim().orEmpty())
        }.ifBlank { defaultMessage(knownCode) }
        return TerminalError(knownCode, truncate(text, MAX_MESSAGE_BYTES), sessionId?.takeIf { it.isNotBlank() })
    }

    /**
     * The error the plugin reported in a document or Bundle. A plugin may only raise plugin-side
     * codes: a host-only code (`PLUGIN_UNAVAILABLE`, `PLUGIN_INCOMPATIBLE`, `TIMEOUT`, `CANCELLED`)
     * coming over Binder is treated as unknown so it cannot impersonate a host decision.
     */
    fun fromPlugin(code: String?, message: String?, sessionId: String? = null): TerminalError {
        val trimmedCode = code?.trim().orEmpty()
        return if (TerminalErrorCodes.isHostOnly(trimmedCode)) {
            normalize(TerminalErrorCodes.INTERNAL, "plugin reported host-only code $trimmedCode" + (message?.trim()?.takeIf { it.isNotEmpty() }?.let { ": $it" } ?: ""), sessionId)
        } else {
            normalize(trimmedCode, message, sessionId)
        }
    }

    /** The error of a Bundle answer, or null when the answer carries no error document. */
    fun fromBundle(bundle: Bundle?): TerminalError? = TerminalJson.decodeError(bundle?.getString(TerminalContract.KEY_ERROR_JSON))

    fun fromThrowable(throwable: Throwable, phase: Phase): TerminalError = when (throwable) {
        is TerminalPluginException -> throwable.error
        is TimeoutCancellationException -> error(TerminalErrorCodes.TIMEOUT, throwable.message ?: "terminal operation timed out")
        is CancellationException -> error(TerminalErrorCodes.CANCELLED, "terminal operation cancelled")
        is InterruptedException, is InterruptedIOException -> error(TerminalErrorCodes.CANCELLED, "terminal operation interrupted")
        is DeadObjectException -> when (phase) {
            Phase.CALL -> error(TerminalErrorCodes.SESSION_CLOSED, "terminal plugin process died")
            else -> error(TerminalErrorCodes.PLUGIN_UNAVAILABLE, "terminal plugin process died")
        }
        is RemoteException -> when (phase) {
            Phase.CALL -> error(TerminalErrorCodes.INTERNAL, describe("terminal plugin call failed", throwable))
            else -> error(TerminalErrorCodes.PLUGIN_UNAVAILABLE, describe("terminal plugin binding failed", throwable))
        }
        is SecurityException -> error(TerminalErrorCodes.PLUGIN_UNAVAILABLE, describe("terminal plugin refused the caller", throwable))
        is IllegalArgumentException -> encoded(throwable) ?: error(TerminalErrorCodes.INVALID_ARGUMENT, describe("invalid argument", throwable))
        is IllegalStateException -> encoded(throwable) ?: when (phase) {
            Phase.DISCOVERY, Phase.OPEN -> error(TerminalErrorCodes.PLUGIN_UNAVAILABLE, describe("terminal plugin is unavailable", throwable))
            Phase.CALL -> error(TerminalErrorCodes.INTERNAL, describe("terminal host failure", throwable))
        }
        else -> error(TerminalErrorCodes.INTERNAL, describe("terminal host failure", throwable))
    }

    /** The code the plugin encoded into an exception message (`CODE: detail`), or null for an ordinary message. */
    fun encoded(throwable: Throwable): TerminalError? {
        val (code, detail) = decode(throwable.message ?: return null) ?: return null
        return fromPlugin(code, detail)
    }

    /** Splits `CODE: detail` (or a bare `CODE`) into its parts when `CODE` is a plugin-side error code. */
    fun decode(message: String): Pair<String, String?>? {
        val trimmed = message.trim()
        val head = trimmed.substringBefore(':').trim()
        if (head.isEmpty() || !TerminalErrorCodes.isKnown(head) || TerminalErrorCodes.isHostOnly(head)) return null
        val detail = if (':' in trimmed) trimmed.substringAfter(':').trim().takeIf { it.isNotEmpty() } else null
        return head to detail
    }

    fun defaultMessage(code: String): String = when (code) {
        TerminalErrorCodes.PLUGIN_UNAVAILABLE -> "terminal plugin is unavailable"
        TerminalErrorCodes.PLUGIN_INCOMPATIBLE -> "terminal plugin is incompatible with this host"
        TerminalErrorCodes.INVALID_ARGUMENT -> "invalid argument"
        TerminalErrorCodes.SESSION_NOT_FOUND -> "session not found"
        TerminalErrorCodes.SESSION_LIMIT -> "too many sessions"
        TerminalErrorCodes.SESSION_CLOSED -> "session is closed"
        TerminalErrorCodes.DIRECTORY_INACCESSIBLE -> "directory is not accessible"
        TerminalErrorCodes.STORAGE_PERMISSION_REQUIRED -> "all files access is required for this directory"
        TerminalErrorCodes.PTY_FAILED -> "pseudo terminal could not be created"
        TerminalErrorCodes.NODE_UNAVAILABLE -> "Node.js is not available"
        TerminalErrorCodes.SUBSCRIPTION_LIMIT -> "too many output subscriptions"
        TerminalErrorCodes.TIMEOUT -> "operation timed out"
        TerminalErrorCodes.CANCELLED -> "operation cancelled"
        else -> "terminal plugin failure"
    }

    fun truncate(text: String, maxBytes: Int): String {
        if (text.toByteArray(StandardCharsets.UTF_8).size <= maxBytes) return text
        var end = text.length
        while (end > 0 && text.substring(0, end).toByteArray(StandardCharsets.UTF_8).size > maxBytes) {
            end -= maxOf(1, end / 8)
        }
        return text.substring(0, end)
    }

    private fun error(code: String, message: String) = normalize(code, message)

    private fun describe(prefix: String, throwable: Throwable): String {
        val detail = throwable.message?.trim().orEmpty()
        return if (detail.isEmpty()) prefix else "$prefix: $detail"
    }
}
