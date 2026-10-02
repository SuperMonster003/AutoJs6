package org.autojs.autojs.core.plugin.compose

import android.content.Context
import org.autojs.autojs6.R
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.ComposeUiIds

class ComposeUiError(
    val code: String,
    message: String = code,
    val nodeId: Int? = null,
    val prop: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    val retryable get() = code in setOf(ComposeUiErrorCodes.PLUGIN_UNAVAILABLE, ComposeUiErrorCodes.PLUGIN_DISABLED,
        ComposeUiErrorCodes.PLUGIN_UNAUTHORIZED, ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE)

    companion object {
        fun from(failure: Throwable): ComposeUiError = when (failure) {
            is ComposeUiError -> failure
            is ComposeUiContractException -> ComposeUiError(failure.code, failure.message ?: failure.code, failure.nodeId, failure.prop, failure)
            else -> ComposeUiError(ComposeUiErrorCodes.RENDERER_FAILED, cause = failure)
        }

        internal fun selection(context: Context, reason: SelectionFailure): ComposeUiError {
            val resource = when (reason) {
                SelectionFailure.MISSING -> R.string.error_plugin_not_found_in_installed_apps
                SelectionFailure.SYSTEM_DISABLED -> R.string.error_compose_plugin_system_disabled
                SelectionFailure.CENTER_DISABLED -> R.string.error_plugin_is_not_enabled_in_plugin_center
                SelectionFailure.UNAUTHORIZED -> R.string.error_plugin_is_not_authorized_in_plugin_center
                SelectionFailure.HOST_TOO_OLD -> R.string.error_compose_host_too_old
                SelectionFailure.CONTRACT, SelectionFailure.DISCOVERY -> R.string.error_compose_plugin_incompatible
                SelectionFailure.FINGERPRINT -> R.string.error_compose_shared_dependencies
            }
            return ComposeUiError(reason.code, context.getString(resource, ComposeUiIds.PLUGIN_PACKAGE_NAME))
        }
    }
}

internal enum class SelectionFailure(val code: String) {
    MISSING(ComposeUiErrorCodes.PLUGIN_UNAVAILABLE), SYSTEM_DISABLED(ComposeUiErrorCodes.PLUGIN_DISABLED),
    CENTER_DISABLED(ComposeUiErrorCodes.PLUGIN_DISABLED), UNAUTHORIZED(ComposeUiErrorCodes.PLUGIN_UNAUTHORIZED),
    HOST_TOO_OLD(ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE), DISCOVERY(ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE),
    CONTRACT(ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE), FINGERPRINT(ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE),
}
