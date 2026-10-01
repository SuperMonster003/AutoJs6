package org.autojs.autojs.core.plugin.installer

/**
 * Host-side view of one installer failure: a code from `InstallerErrorCodes`, a message that never
 * carries file contents, the platform `PackageInstaller.STATUS_*` value and its verbatim message
 * when the platform produced the failure, the package the failure belongs to and the retry hint.
 */
data class InstallerError(
    val code: String,
    val message: String,
    val retryable: Boolean,
    val status: Int? = null,
    val systemMessage: String? = null,
    val packageName: String? = null,
    val authorizer: String? = null,
)

/** Thrown by the installer host client; the script layer turns it into the `InstallerError` JS object. */
class InstallerPluginException(val error: InstallerError, cause: Throwable? = null) : RuntimeException("${error.code}: ${error.message}", cause) {

    val code: String
        get() = error.code

    val retryable: Boolean
        get() = error.retryable
}
