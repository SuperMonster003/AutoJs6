package org.autojs.autojs.core.plugin.epub

import android.os.Bundle
import android.os.DeadObjectException
import android.os.RemoteException
import kotlinx.coroutines.TimeoutCancellationException
import org.autojs.plugin.epub.api.EpubContract
import org.autojs.plugin.epub.api.EpubErrorCodes
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.charset.StandardCharsets
import kotlin.coroutines.cancellation.CancellationException

/**
 * Normalizes the plugin's error answers and host-side failures into [EpubError] values (plugin
 * roadmap P5.4). Bundle answers carry `KEY_ERROR_CODE` / `KEY_ERROR_MESSAGE`; the non-Bundle
 * methods of the contract throw `IllegalArgumentException` / `IllegalStateException` whose
 * message starts with the code, which [fromThrowable] decodes. Unknown codes collapse to
 * `INTERNAL` and messages are truncated to the contract ceiling.
 */
object EpubErrorMapper {

    /** Where a throwable was raised; the same exception type maps differently per phase. */
    enum class Phase { DISCOVERY, OPEN, CALL, TRANSFER }

    fun normalize(code: String?, message: String?): EpubError {
        val trimmedCode = code?.trim().orEmpty()
        val knownCode = if (EpubErrorCodes.isKnown(trimmedCode)) trimmedCode else EpubErrorCodes.INTERNAL
        val text = buildString {
            if (knownCode != trimmedCode && trimmedCode.isNotEmpty()) {
                append("unknown error code ").append(trimmedCode)
                if (!message.isNullOrBlank()) append(": ")
            }
            append(message?.trim().orEmpty())
        }.ifBlank { defaultMessage(knownCode) }
        return EpubError(knownCode, truncate(text, EpubContract.MAX_ERROR_MESSAGE_BYTES))
    }

    /** The error of a Bundle answer, or null when the answer carries no error code. */
    fun fromBundle(bundle: Bundle?): EpubError? {
        val code = bundle?.getString(EpubContract.KEY_ERROR_CODE) ?: return null
        return normalize(code, bundle.getString(EpubContract.KEY_ERROR_MESSAGE))
    }

    fun fromThrowable(throwable: Throwable, phase: Phase): EpubError = when (throwable) {
        is EpubPluginException -> throwable.error
        is TimeoutCancellationException -> error(EpubErrorCodes.TIMEOUT, throwable.message ?: "EPUB operation timed out")
        is CancellationException -> error(EpubErrorCodes.CANCELLED, "EPUB operation cancelled")
        is InterruptedException, is InterruptedIOException -> error(EpubErrorCodes.CANCELLED, "EPUB operation interrupted")
        is DeadObjectException -> when (phase) {
            Phase.CALL, Phase.TRANSFER -> error(EpubErrorCodes.SESSION_CLOSED, "EPUB plugin process died")
            else -> error(EpubErrorCodes.PLUGIN_UNAVAILABLE, "EPUB plugin process died")
        }
        is RemoteException -> when (phase) {
            Phase.CALL, Phase.TRANSFER -> error(EpubErrorCodes.SESSION_CLOSED, describe("EPUB plugin call failed", throwable))
            else -> error(EpubErrorCodes.PLUGIN_UNAVAILABLE, describe("EPUB plugin binding failed", throwable))
        }
        is SecurityException -> error(EpubErrorCodes.PLUGIN_UNAVAILABLE, describe("EPUB plugin refused the caller", throwable))
        is IllegalArgumentException -> encoded(throwable) ?: error(EpubErrorCodes.INVALID_ARGUMENT, describe("invalid argument", throwable))
        is IllegalStateException -> encoded(throwable) ?: when (phase) {
            Phase.DISCOVERY, Phase.OPEN -> error(EpubErrorCodes.PLUGIN_UNAVAILABLE, describe("EPUB plugin is unavailable", throwable))
            else -> error(EpubErrorCodes.INTERNAL, describe("EPUB host failure", throwable))
        }
        is FileNotFoundException -> error(EpubErrorCodes.FILE_NOT_FOUND, describe("file not found", throwable))
        is IOException -> error(EpubErrorCodes.IO, describe("EPUB transfer failed", throwable))
        else -> error(EpubErrorCodes.INTERNAL, describe("EPUB host failure", throwable))
    }

    /** The code the plugin encoded into an exception message (`CODE: detail`), or null for an ordinary message. */
    fun encoded(throwable: Throwable): EpubError? {
        val decoded = EpubErrorCodes.decode(throwable.message ?: return null) ?: return null
        return normalize(decoded.first, decoded.second)
    }

    fun defaultMessage(code: String): String = when (code) {
        EpubErrorCodes.PLUGIN_UNAVAILABLE -> "EPUB plugin is unavailable"
        EpubErrorCodes.PLUGIN_DISABLED -> "EPUB plugin is disabled"
        EpubErrorCodes.PLUGIN_INCOMPATIBLE -> "EPUB plugin is incompatible with this host"
        EpubErrorCodes.FILE_NOT_FOUND -> "file not found"
        EpubErrorCodes.FILE_UNREADABLE -> "file is not readable"
        EpubErrorCodes.NOT_EPUB -> "file is not an EPUB"
        EpubErrorCodes.PARSE_FAILED -> "EPUB could not be parsed"
        EpubErrorCodes.ENCRYPTED -> "EPUB is encrypted"
        EpubErrorCodes.RESOURCE_NOT_FOUND -> "resource not found"
        EpubErrorCodes.LIMIT_EXCEEDED -> "limit exceeded"
        EpubErrorCodes.INVALID_ARGUMENT -> "invalid argument"
        EpubErrorCodes.SESSION_CLOSED -> "session is closed"
        EpubErrorCodes.SESSION_REPLACED -> "session was replaced"
        EpubErrorCodes.READER_NOT_VISIBLE -> "reader is not visible"
        EpubErrorCodes.UNSUPPORTED_PREFERENCE -> "unsupported preference"
        EpubErrorCodes.CANCELLED -> "operation cancelled"
        EpubErrorCodes.TIMEOUT -> "operation timed out"
        EpubErrorCodes.IO -> "I/O failure"
        else -> "EPUB plugin failure"
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
