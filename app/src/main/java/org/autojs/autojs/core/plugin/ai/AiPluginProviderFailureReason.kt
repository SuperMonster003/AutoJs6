package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiError
import org.autojs.plugin.ai.common.api.AiErrorCode

/** Only these fixed categories may cross the Agent boundary; provider text is never retained. */
internal enum class AiPluginProviderFailureReason {
    ONLINE_INVALID_REQUEST,
    ONLINE_CREDENTIAL_UNAVAILABLE,
    ONLINE_PROFILE_CHANGED,
    ONLINE_AUTHENTICATION_FAILED,
    ONLINE_PERMISSION_DENIED,
    ONLINE_RATE_LIMITED,
    ONLINE_REQUEST_REJECTED,
    ONLINE_REDIRECT_REFUSED,
    ONLINE_SERVICE_UNAVAILABLE,
    ONLINE_NETWORK_UNAVAILABLE,
    ONLINE_METERED_NETWORK_DISALLOWED,
    ONLINE_TIMED_OUT,
    ONLINE_TLS_FAILED,
    ONLINE_RESPONSE_TOO_LARGE,
    ONLINE_INVALID_RESPONSE,
    ONLINE_PROVIDER_ERROR,
    ONLINE_EXECUTION_CLOSED,

    /**
     * The Provider's own closed error code when it fails before or after the online backend
     * (an invalid or violating request, a descriptor problem, a quota). They are derived from
     * AiErrorCode only, never from provider text, so a tool continuation rejected inside the
     * Provider is distinguishable from a backend failure.
     */
    PROVIDER_INVALID_REQUEST,
    PROVIDER_UNSUPPORTED_PROTOCOL,
    PROVIDER_UNSUPPORTED_CAPABILITY,
    PROVIDER_UNAVAILABLE,
    PROVIDER_TARGET_UNAVAILABLE,
    PROVIDER_PERMISSION_DENIED,
    PROVIDER_QUOTA_EXCEEDED,
    PROVIDER_BACKPRESSURE,
    PROVIDER_CANCELLED,
    PROVIDER_PROTOCOL_VIOLATION,
    PROVIDER_DESCRIPTOR_FAILED,
    ;

    companion object {
        /** Online backend categories announced by the Provider; its own stage codes are never accepted as text. */
        fun fromProviderCode(code: String?): AiPluginProviderFailureReason? =
            entries.firstOrNull { it.name == code && it.name.startsWith("ONLINE_") }

        fun fromErrorCode(code: Int): AiPluginProviderFailureReason? = when (code) {
            AiErrorCode.INVALID_REQUEST -> PROVIDER_INVALID_REQUEST
            AiErrorCode.UNSUPPORTED_PROTOCOL -> PROVIDER_UNSUPPORTED_PROTOCOL
            AiErrorCode.UNSUPPORTED_CAPABILITY -> PROVIDER_UNSUPPORTED_CAPABILITY
            AiErrorCode.PROVIDER_UNAVAILABLE -> PROVIDER_UNAVAILABLE
            AiErrorCode.TARGET_UNAVAILABLE -> PROVIDER_TARGET_UNAVAILABLE
            AiErrorCode.PERMISSION_DENIED -> PROVIDER_PERMISSION_DENIED
            AiErrorCode.QUOTA_EXCEEDED -> PROVIDER_QUOTA_EXCEEDED
            AiErrorCode.BACKPRESSURE -> PROVIDER_BACKPRESSURE
            AiErrorCode.CANCELLED -> PROVIDER_CANCELLED
            AiErrorCode.PROTOCOL_VIOLATION -> PROVIDER_PROTOCOL_VIOLATION
            AiErrorCode.DESCRIPTOR_FAILED -> PROVIDER_DESCRIPTOR_FAILED
            else -> null
        }
    }
}

/** Preserve the existing public ask/stream failure while retaining safe Agent diagnostics. */
internal fun AiError.toPluginAskFailure() = AiPluginAskOutcome.Failed(
    failure = if (code == AiErrorCode.TIMEOUT) AiPluginAskFailure.TIMED_OUT else AiPluginAskFailure.PROVIDER_FAILED,
    providerReason = safeProviderFailureReason(),
)

internal fun AiError.safeProviderFailureReason(): AiPluginProviderFailureReason? =
    if (code == AiErrorCode.PROVIDER_FAILED) AiPluginProviderFailureReason.fromProviderCode(providerCode)
    else AiPluginProviderFailureReason.fromErrorCode(code)
