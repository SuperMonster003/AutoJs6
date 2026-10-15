package org.autojs.autojs.core.plugin.gallery

import android.os.Bundle
import android.os.DeadObjectException
import android.os.RemoteException
import kotlinx.coroutines.TimeoutCancellationException
import org.autojs.plugin.gallery.api.GalleryContract
import org.autojs.plugin.gallery.api.GalleryErrorCodes
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.charset.StandardCharsets
import kotlin.coroutines.cancellation.CancellationException

/**
 * Normalizes the plugin's error answers and host-side failures into [GalleryError] values. Bundle
 * answers and `error` events carry `KEY_ERROR_CODE` / `KEY_ERROR_MESSAGE`; the non-Bundle methods of
 * the contract throw `IllegalArgumentException` / `IllegalStateException` whose message starts with
 * the code, which [fromThrowable] decodes. Unknown codes collapse to `INTERNAL` and messages are
 * truncated to the contract ceiling.
 */
object GalleryErrorMapper {

    /** Where a throwable was raised; the same exception type maps differently per phase. */
    enum class Phase { DISCOVERY, OPEN, CALL, OUTPUT }

    fun normalize(code: String?, message: String?): GalleryError {
        val trimmedCode = code?.trim().orEmpty()
        val knownCode = if (GalleryErrorCodes.isKnown(trimmedCode)) trimmedCode else GalleryErrorCodes.INTERNAL
        val text = buildString {
            if (knownCode != trimmedCode && trimmedCode.isNotEmpty()) {
                append("unknown error code ").append(trimmedCode)
                if (!message.isNullOrBlank()) append(": ")
            }
            append(message?.trim().orEmpty())
        }.ifBlank { defaultMessage(knownCode) }
        return GalleryError(knownCode, truncate(text, GalleryContract.MAX_ERROR_MESSAGE_BYTES))
    }

    /** The error of a Bundle answer or event, or null when it carries no error code. */
    fun fromBundle(bundle: Bundle?): GalleryError? {
        val code = bundle?.getString(GalleryContract.KEY_ERROR_CODE) ?: return null
        return normalize(code, bundle.getString(GalleryContract.KEY_ERROR_MESSAGE))
    }

    fun fromThrowable(throwable: Throwable, phase: Phase): GalleryError = when (throwable) {
        is GalleryPluginException -> throwable.error
        is TimeoutCancellationException -> error(GalleryErrorCodes.TIMEOUT, throwable.message ?: "gallery operation timed out")
        is CancellationException -> error(GalleryErrorCodes.CANCELLED, "gallery operation cancelled")
        is InterruptedException, is InterruptedIOException -> error(GalleryErrorCodes.CANCELLED, "gallery operation interrupted")
        is DeadObjectException -> when (phase) {
            Phase.CALL, Phase.OUTPUT -> error(GalleryErrorCodes.SESSION_CLOSED, "gallery plugin process died")
            else -> error(GalleryErrorCodes.PLUGIN_UNAVAILABLE, "gallery plugin process died")
        }
        is RemoteException -> when (phase) {
            Phase.CALL, Phase.OUTPUT -> error(GalleryErrorCodes.SESSION_CLOSED, describe("gallery plugin call failed", throwable))
            else -> error(GalleryErrorCodes.PLUGIN_UNAVAILABLE, describe("gallery plugin binding failed", throwable))
        }
        is SecurityException -> error(GalleryErrorCodes.PLUGIN_UNAVAILABLE, describe("gallery plugin refused the caller", throwable))
        is IllegalArgumentException -> encoded(throwable) ?: error(GalleryErrorCodes.INVALID_ARGUMENT, describe("invalid argument", throwable))
        is IllegalStateException -> encoded(throwable) ?: when (phase) {
            Phase.DISCOVERY, Phase.OPEN -> error(GalleryErrorCodes.PLUGIN_UNAVAILABLE, describe("gallery plugin is unavailable", throwable))
            else -> error(GalleryErrorCodes.INTERNAL, describe("gallery host failure", throwable))
        }
        is FileNotFoundException -> error(GalleryErrorCodes.FILE_NOT_FOUND, describe("file not found", throwable))
        is IOException -> error(GalleryErrorCodes.IO, describe("gallery file transfer failed", throwable))
        else -> error(GalleryErrorCodes.INTERNAL, describe("gallery host failure", throwable))
    }

    /** The code the plugin encoded into an exception message (`CODE: detail`), or null for an ordinary message. */
    fun encoded(throwable: Throwable): GalleryError? {
        val decoded = GalleryErrorCodes.decode(throwable.message ?: return null) ?: return null
        return normalize(decoded.first, decoded.second)
    }

    fun defaultMessage(code: String): String = when (code) {
        GalleryErrorCodes.PLUGIN_UNAVAILABLE -> "gallery plugin is unavailable"
        GalleryErrorCodes.PLUGIN_DISABLED -> "gallery plugin is disabled"
        GalleryErrorCodes.PLUGIN_INCOMPATIBLE -> "gallery plugin is incompatible with this host"
        GalleryErrorCodes.FILE_NOT_FOUND -> "file not found"
        GalleryErrorCodes.FILE_UNREADABLE -> "file is not readable"
        GalleryErrorCodes.UNSUPPORTED_FORMAT -> "unsupported image format"
        GalleryErrorCodes.DECODE_FAILED -> "image could not be decoded"
        GalleryErrorCodes.LIMIT_EXCEEDED -> "limit exceeded"
        GalleryErrorCodes.INVALID_ARGUMENT -> "invalid argument"
        GalleryErrorCodes.PERMISSION_REQUIRED -> "the gallery plugin has no photo access"
        GalleryErrorCodes.ACCESS_DENIED -> "access denied"
        GalleryErrorCodes.OUTPUT_EXISTS -> "output file exists"
        GalleryErrorCodes.SESSION_CLOSED -> "session is closed"
        GalleryErrorCodes.SESSION_REPLACED -> "session was replaced"
        GalleryErrorCodes.CANCELLED -> "operation cancelled"
        GalleryErrorCodes.TIMEOUT -> "operation timed out"
        GalleryErrorCodes.IO -> "I/O failure"
        else -> "gallery plugin failure"
    }

    fun truncate(text: String, maxBytes: Int): String {
        if (text.toByteArray(StandardCharsets.UTF_8).size <= maxBytes) return text
        var end = text.length
        while (end > 0 && text.substring(0, end).toByteArray(StandardCharsets.UTF_8).size > maxBytes) {
            end -= maxOf(1, end / 8)
        }
        return text.substring(0, end)
    }

    /** A host-side failure as the exception the client throws. */
    fun failure(code: String, message: String, cause: Throwable? = null): GalleryPluginException =
        GalleryPluginException(normalize(code, message), cause)

    private fun error(code: String, message: String) = normalize(code, message)

    private fun describe(prefix: String, throwable: Throwable): String {
        val detail = throwable.message?.trim().orEmpty()
        return if (detail.isEmpty()) prefix else "$prefix: $detail"
    }
}
