package org.autojs.autojs.core.plugin.installer

import android.os.DeadObjectException
import android.os.RemoteException
import android.os.OperationCanceledException
import kotlinx.coroutines.TimeoutCancellationException
import org.autojs.plugin.installer.api.InstallerErrorCodes
import org.autojs.plugin.installer.api.InstallerContract
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Normalizes plugin error documents and host-side failures into [InstallerError] values (plugin
 * roadmap P1.3). Unknown codes collapse to `INTERNAL`, messages are truncated and the retry hint
 * falls back to the default set of `InstallerErrorCodes` when the document omits it.
 */
object InstallerErrorMapper {

    /** Where a throwable was raised; the same exception type maps differently per phase. */
    enum class Phase { DISCOVERY, SOURCE, OPEN, CALL, SESSION }

    private const val MAX_MESSAGE_CHARS = 2048

    fun normalize(
        code: String?,
        message: String?,
        retryable: Boolean?,
        status: Int? = null,
        systemMessage: String? = null,
        packageName: String? = null,
        authorizer: String? = null,
    ): InstallerError {
        val trimmedCode = code?.trim().orEmpty()
        val knownCode = if (InstallerErrorCodes.isKnown(trimmedCode)) trimmedCode else InstallerErrorCodes.INTERNAL
        val text = buildString {
            if (knownCode != trimmedCode && trimmedCode.isNotEmpty()) {
                append("unknown error code ").append(trimmedCode)
                if (!message.isNullOrBlank()) append(": ")
            }
            append(message?.trim().orEmpty())
        }.ifBlank { defaultMessage(knownCode) }
        return InstallerError(
            code = knownCode,
            message = text.take(MAX_MESSAGE_CHARS),
            retryable = retryable ?: InstallerErrorCodes.isRetryableByDefault(knownCode),
            status = status,
            systemMessage = systemMessage?.take(MAX_MESSAGE_CHARS),
            packageName = packageName?.takeIf { it.isNotBlank() },
            authorizer = authorizer?.takeIf { it in InstallerContract.AUTHORIZERS },
        )
    }

    fun error(code: String, message: String, retryable: Boolean? = null, packageName: String? = null): InstallerError =
        normalize(code, message, retryable, packageName = packageName)

    fun fromThrowable(throwable: Throwable, phase: Phase): InstallerError = when (throwable) {
        is InstallerPluginException -> throwable.error
        is TimeoutCancellationException -> error(InstallerErrorCodes.TIMEOUT, throwable.message ?: "installer operation timed out", retryable = true)
        is CancellationException -> error(InstallerErrorCodes.CANCELLED, "installer operation cancelled", retryable = false)
        is OperationCanceledException -> error(InstallerErrorCodes.CANCELLED, "package source operation cancelled", retryable = false)
        is InterruptedException, is InterruptedIOException -> error(InstallerErrorCodes.CANCELLED, "installer operation interrupted", retryable = false)
        is DeadObjectException -> error(InstallerErrorCodes.PLUGIN_UNAVAILABLE, "installer plugin process died", retryable = true)
        is RemoteException -> when (phase) {
            Phase.CALL, Phase.SESSION -> error(InstallerErrorCodes.INTERNAL, describe("installer plugin call failed", throwable), retryable = true)
            else -> error(InstallerErrorCodes.PLUGIN_UNAVAILABLE, describe("installer plugin binding failed", throwable), retryable = false)
        }
        is SecurityException -> if (phase == Phase.SOURCE) {
            error(InstallerErrorCodes.SOURCE_UNREADABLE, describe("package source access denied", throwable), retryable = false)
        } else error(InstallerErrorCodes.PLUGIN_UNAVAILABLE, describe("installer plugin refused the caller", throwable), retryable = false)
        is FileNotFoundException -> error(InstallerErrorCodes.SOURCE_NOT_FOUND, describe("package source not found", throwable), retryable = false)
        is IOException -> error(InstallerErrorCodes.SOURCE_UNREADABLE, describe("package source cannot be read", throwable), retryable = false)
        is IllegalArgumentException -> error(InstallerErrorCodes.INVALID_ARGUMENT, describe("invalid argument", throwable), retryable = false)
        is IllegalStateException -> when (phase) {
            Phase.DISCOVERY, Phase.OPEN -> error(InstallerErrorCodes.PLUGIN_UNAVAILABLE, describe("installer plugin is unavailable", throwable), retryable = false)
            else -> error(InstallerErrorCodes.INTERNAL, describe("installer host failure", throwable), retryable = false)
        }
        else -> error(InstallerErrorCodes.INTERNAL, describe("installer host failure", throwable), retryable = false)
    }

    private fun describe(prefix: String, throwable: Throwable): String {
        val detail = throwable.message?.trim().orEmpty()
        return if (detail.isEmpty()) prefix else "$prefix: $detail"
    }

    fun defaultMessage(code: String): String = when (code) {
        InstallerErrorCodes.PLUGIN_UNAVAILABLE -> "installer plugin is unavailable"
        InstallerErrorCodes.INVALID_ARGUMENT -> "invalid argument"
        InstallerErrorCodes.SOURCE_NOT_FOUND -> "package source not found"
        InstallerErrorCodes.SOURCE_UNREADABLE -> "package source cannot be read"
        InstallerErrorCodes.UNSUPPORTED_FORMAT -> "package format is not installable"
        InstallerErrorCodes.INVALID_PACKAGE -> "package is invalid"
        InstallerErrorCodes.INCOMPATIBLE_DEVICE -> "package is incompatible with this device"
        InstallerErrorCodes.AUTHORIZER_UNAVAILABLE -> "authorizer is unavailable"
        InstallerErrorCodes.AUTHORIZER_DENIED -> "authorizer was denied"
        InstallerErrorCodes.AUTHORIZER_REQUIRED -> "a privileged authorizer is required"
        InstallerErrorCodes.NOTIFICATION_UNAVAILABLE -> "installer notifications are unavailable"
        InstallerErrorCodes.USER_CANCELLED -> "cancelled by the user"
        InstallerErrorCodes.USER_ACTION_TIMEOUT -> "waiting for the user timed out"
        InstallerErrorCodes.SIGNATURE_MISMATCH -> "package signature does not match the installed application"
        InstallerErrorCodes.INSUFFICIENT_STORAGE -> "insufficient storage"
        InstallerErrorCodes.INSTALL_FAILED -> "installation failed"
        InstallerErrorCodes.UNINSTALL_FAILED -> "uninstallation failed"
        InstallerErrorCodes.BLOCKED_BY_POLICY -> "blocked by a device policy or user restriction"
        InstallerErrorCodes.TIMEOUT -> "installer operation timed out"
        InstallerErrorCodes.CANCELLED -> "installer operation cancelled"
        else -> "installer failure"
    }
}
