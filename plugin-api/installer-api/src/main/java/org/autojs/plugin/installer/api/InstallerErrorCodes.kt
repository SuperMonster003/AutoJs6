package org.autojs.plugin.installer.api

/**
 * Error codes of the error documents (`error.code`) and of the host-side `InstallerError`. The
 * set is closed per contract version; a code the host does not know is reported as [INTERNAL].
 */
object InstallerErrorCodes {

    /** Plugin missing, disabled, unauthorized, incompatible, binding failed or process gone (host side). */
    const val PLUGIN_UNAVAILABLE = "PLUGIN_UNAVAILABLE"
    const val INVALID_ARGUMENT = "INVALID_ARGUMENT"
    const val SOURCE_NOT_FOUND = "SOURCE_NOT_FOUND"
    const val SOURCE_UNREADABLE = "SOURCE_UNREADABLE"
    /** AAB or an unrecognized container. */
    const val UNSUPPORTED_FORMAT = "UNSUPPORTED_FORMAT"
    /** Manifest parsing failed, inconsistent splits or signers. */
    const val INVALID_PACKAGE = "INVALID_PACKAGE"
    /** No split matches the device, or minSdk above the device. */
    const val INCOMPATIBLE_DEVICE = "INCOMPATIBLE_DEVICE"
    /** The requested or automatically chosen authorizer is not installed, not running or too old. */
    const val AUTHORIZER_UNAVAILABLE = "AUTHORIZER_UNAVAILABLE"
    /** The user refused the authorizer. */
    const val AUTHORIZER_DENIED = "AUTHORIZER_DENIED"
    /** `silent` or a privileged option was requested without a privileged authorizer. */
    const val AUTHORIZER_REQUIRED = "AUTHORIZER_REQUIRED"
    const val USER_CANCELLED = "USER_CANCELLED"
    const val USER_ACTION_TIMEOUT = "USER_ACTION_TIMEOUT"
    /** `INSTALL_FAILED_UPDATE_INCOMPATIBLE` and other signer conflicts. */
    const val SIGNATURE_MISMATCH = "SIGNATURE_MISMATCH"
    const val INSUFFICIENT_STORAGE = "INSUFFICIENT_STORAGE"
    /** Any other system failure; `status` and `systemMessage` carry the platform detail. */
    const val INSTALL_FAILED = "INSTALL_FAILED"
    const val UNINSTALL_FAILED = "UNINSTALL_FAILED"
    /** A user restriction or device policy blocked the operation. */
    const val BLOCKED_BY_POLICY = "BLOCKED_BY_POLICY"
    const val TIMEOUT = "TIMEOUT"
    /** The script stopped or `cancel()` was called. */
    const val CANCELLED = "CANCELLED"
    const val INTERNAL = "INTERNAL"
    /** V2: notification presentation cannot be shown because permission or its channel is disabled. */
    const val NOTIFICATION_UNAVAILABLE = "NOTIFICATION_UNAVAILABLE"

    /** V1 codes keep their order; later versions append new codes. */
    val ALL: List<String> = listOf(
        PLUGIN_UNAVAILABLE,
        INVALID_ARGUMENT,
        SOURCE_NOT_FOUND,
        SOURCE_UNREADABLE,
        UNSUPPORTED_FORMAT,
        INVALID_PACKAGE,
        INCOMPATIBLE_DEVICE,
        AUTHORIZER_UNAVAILABLE,
        AUTHORIZER_DENIED,
        AUTHORIZER_REQUIRED,
        USER_CANCELLED,
        USER_ACTION_TIMEOUT,
        SIGNATURE_MISMATCH,
        INSUFFICIENT_STORAGE,
        INSTALL_FAILED,
        UNINSTALL_FAILED,
        BLOCKED_BY_POLICY,
        TIMEOUT,
        CANCELLED,
        INTERNAL,
        NOTIFICATION_UNAVAILABLE,
    )

    /**
     * Codes whose `retryable` flag defaults to true when the error document omits it: the
     * authorizer may come back, a timeout may not repeat and a plugin process may be rebound.
     */
    val RETRYABLE_DEFAULTS: Set<String> = setOf(
        AUTHORIZER_UNAVAILABLE,
        TIMEOUT,
        PLUGIN_UNAVAILABLE,
    )

    fun isKnown(code: String?): Boolean = code != null && code in ALL

    fun isRetryableByDefault(code: String?): Boolean = code != null && code in RETRYABLE_DEFAULTS
}
