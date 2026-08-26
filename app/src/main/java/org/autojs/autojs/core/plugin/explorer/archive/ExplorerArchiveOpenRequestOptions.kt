package org.autojs.autojs.core.plugin.explorer.archive

import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerArchiveOpenValues

/** Owns a transient password until one synchronous archive-open Binder call has returned. */
internal class ExplorerArchiveOpenRequestOptions(
    password: CharArray? = null,
) : AutoCloseable {

    private var passwordValue = password?.takeIf(CharArray::isNotEmpty)?.clone()

    init {
        if ((passwordValue?.size ?: 0) > ExplorerActionProtocol.MAX_ARCHIVE_PASSWORD_LENGTH) {
            passwordValue?.fill('\u0000')
            passwordValue = null
            throw IllegalArgumentException("Archive open password is too long")
        }
    }

    val hasPassword: Boolean
        @Synchronized get() = passwordValue != null

    @Synchronized
    fun passwordChars(): CharArray? = passwordValue?.clone()

    @Synchronized
    override fun close() {
        passwordValue?.fill('\u0000')
        passwordValue = null
    }

    override fun toString(): String =
        "ExplorerArchiveOpenRequestOptions(hasPassword=$hasPassword)"
}

internal class ExplorerArchiveOpenInteractionException(
    val interactionKind: Int,
    message: String,
    cause: Throwable,
) : IllegalStateException(message, cause)

internal class ExplorerArchiveOpenRemoteFailure(
    val errorCode: Int,
    message: String,
) : IllegalStateException(message)

internal fun archiveOpenInteractionForServiceError(
    protocolVersion: Int,
    serviceErrorCode: Int,
): Int? {
    if (protocolVersion < 11) return null
    return when (serviceErrorCode) {
        ExplorerArchiveOpenValues.ERROR_PASSWORD_REQUIRED ->
            ExplorerArchiveOpenValues.ERROR_PASSWORD_REQUIRED
        ExplorerArchiveOpenValues.ERROR_WRONG_PASSWORD ->
            ExplorerArchiveOpenValues.ERROR_WRONG_PASSWORD
        else -> null
    }
}
