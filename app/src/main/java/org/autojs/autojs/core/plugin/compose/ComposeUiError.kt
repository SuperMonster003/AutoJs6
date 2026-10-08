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

    /** Selection failures already carry the Plugin Center's localized message verbatim. */
    fun localizedMessage(context: Context): String = localizedMessage { resource ->
        context.getString(resource, ComposeUiIds.PLUGIN_PACKAGE_NAME)
    }

    internal fun localizedMessage(readString: (Int) -> String): String {
        val detail = message?.takeUnless { it.isBlank() || it == code }
        if (retryable && detail != null) return detail
        val prefix = readString(messageResource(code))
        val text = if (detail == null || detail == prefix) prefix else "$prefix $detail"
        val fields = buildList {
            nodeId?.let { add("nodeId=$it") }
            prop?.takeUnless { detail?.contains(it) == true }?.let { add("prop=$it") }
        }
        return if (fields.isEmpty()) text else "$text [${fields.joinToString(", ")}]"
    }

    companion object {
        /** A presentation failure is a lifecycle message; never expose an unnegotiated code. */
        internal fun presentationFailure(code: String?, prop: String? = null): ComposeUiError {
            val accepted = when (code) {
                ComposeUiErrorCodes.PLUGIN_UNAVAILABLE, ComposeUiErrorCodes.PLUGIN_DISABLED,
                ComposeUiErrorCodes.PLUGIN_UNAUTHORIZED, ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE,
                ComposeUiErrorCodes.UI_MODE_REQUIRED, ComposeUiErrorCodes.PERMISSION_REQUIRED,
                ComposeUiErrorCodes.INVALID_ARGUMENT, ComposeUiErrorCodes.UNKNOWN_COMPONENT,
                ComposeUiErrorCodes.UNKNOWN_PROP, ComposeUiErrorCodes.INVALID_MODIFIER,
                ComposeUiErrorCodes.SCOPE_MISMATCH, ComposeUiErrorCodes.DUPLICATE_KEY,
                ComposeUiErrorCodes.LIMIT_EXCEEDED, ComposeUiErrorCodes.RENDER_FAILED,
                ComposeUiErrorCodes.SESSION_CLOSED, ComposeUiErrorCodes.NODE_DETACHED,
                ComposeUiErrorCodes.RENDERER_FAILED, ComposeUiErrorCodes.INTERNAL -> requireNotNull(code)
                else -> ComposeUiErrorCodes.RENDER_FAILED
            }
            return ComposeUiError(accepted, prop = prop)
        }

        internal fun messageResource(code: String): Int = when (code) {
            ComposeUiErrorCodes.PLUGIN_UNAVAILABLE -> R.string.error_plugin_not_found_in_installed_apps
            ComposeUiErrorCodes.PLUGIN_DISABLED -> R.string.error_plugin_is_not_enabled_in_plugin_center
            ComposeUiErrorCodes.PLUGIN_UNAUTHORIZED -> R.string.error_plugin_is_not_authorized_in_plugin_center
            ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE -> R.string.error_compose_plugin_incompatible
            ComposeUiErrorCodes.UI_MODE_REQUIRED -> R.string.error_compose_ui_mode_required
            ComposeUiErrorCodes.PERMISSION_REQUIRED -> R.string.error_compose_permission_required
            ComposeUiErrorCodes.INVALID_ARGUMENT -> R.string.error_compose_invalid_argument
            ComposeUiErrorCodes.UNKNOWN_COMPONENT -> R.string.error_compose_unknown_component
            ComposeUiErrorCodes.UNKNOWN_PROP -> R.string.error_compose_unknown_prop
            ComposeUiErrorCodes.INVALID_MODIFIER -> R.string.error_compose_invalid_modifier
            ComposeUiErrorCodes.SCOPE_MISMATCH -> R.string.error_compose_scope_mismatch
            ComposeUiErrorCodes.DUPLICATE_KEY -> R.string.error_compose_duplicate_key
            ComposeUiErrorCodes.LIMIT_EXCEEDED -> R.string.error_compose_limit_exceeded
            ComposeUiErrorCodes.RENDER_FAILED -> R.string.error_compose_render_failed
            ComposeUiErrorCodes.SESSION_CLOSED -> R.string.error_compose_session_closed
            ComposeUiErrorCodes.NODE_DETACHED -> R.string.error_compose_node_detached
            ComposeUiErrorCodes.RENDERER_FAILED -> R.string.error_compose_renderer_failed
            else -> R.string.error_compose_internal
        }

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
