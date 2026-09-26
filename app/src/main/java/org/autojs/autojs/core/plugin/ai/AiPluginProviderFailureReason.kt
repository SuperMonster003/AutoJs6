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
    ;

    companion object {
        fun fromProviderCode(code: String?): AiPluginProviderFailureReason? =
            entries.firstOrNull { it.name == code }
    }
}

/** Preserve the existing public ask/stream failure while retaining safe Agent diagnostics. */
internal fun AiError.toPluginAskFailure() = AiPluginAskOutcome.Failed(
    failure = if (code == AiErrorCode.TIMEOUT) AiPluginAskFailure.TIMED_OUT else AiPluginAskFailure.PROVIDER_FAILED,
    providerReason = safeProviderFailureReason(),
)

internal fun AiError.safeProviderFailureReason(): AiPluginProviderFailureReason? =
    if (code == AiErrorCode.PROVIDER_FAILED) AiPluginProviderFailureReason.fromProviderCode(providerCode) else null
