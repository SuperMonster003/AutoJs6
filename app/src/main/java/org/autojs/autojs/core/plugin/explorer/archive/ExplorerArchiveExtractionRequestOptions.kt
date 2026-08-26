package org.autojs.autojs.core.plugin.explorer.archive

import org.autojs.plugin.explorer.api.ExplorerActionProtocol

/** Owns transient recovery values until one Binder extraction request has been sent. */
internal class ExplorerArchiveExtractionRequestOptions(
    password: CharArray? = null,
    val skipUnsafePaths: Boolean = false,
    val allowResourceBudgetOverride: Boolean = false,
) : AutoCloseable {

    private var passwordValue = password?.takeIf(CharArray::isNotEmpty)?.clone()

    init {
        if ((passwordValue?.size ?: 0) > ExplorerActionProtocol.MAX_ARCHIVE_PASSWORD_LENGTH) {
            passwordValue?.fill('\u0000')
            passwordValue = null
            throw IllegalArgumentException("Archive extraction password is too long")
        }
    }

    val hasPassword: Boolean
        @Synchronized get() = passwordValue != null

    val hasRecoveryOptions: Boolean
        @Synchronized get() = hasPassword ||
            skipUnsafePaths ||
            allowResourceBudgetOverride

    @Synchronized
    fun passwordChars(): CharArray? = passwordValue?.clone()

    @Synchronized
    override fun close() {
        passwordValue?.fill('\u0000')
        passwordValue = null
    }

    override fun toString(): String =
        "ExplorerArchiveExtractionRequestOptions(" +
            "hasPassword=$hasPassword, " +
            "skipUnsafePaths=$skipUnsafePaths, " +
            "allowResourceBudgetOverride=$allowResourceBudgetOverride)"
}
