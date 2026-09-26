package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiError
import org.autojs.plugin.ai.common.api.AiErrorCode
import org.autojs.plugin.ai.common.api.AiRetryDisposition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AiPluginProviderFailureReasonTest {
    @Test fun knownProviderCategoriesAreRetainedWithoutMessageOrRetryPolicy() {
        AiPluginProviderFailureReason.entries.forEach { reason ->
            val outcome = AiError(AiErrorCode.PROVIDER_FAILED,
                message = "private prompt at https://private.example with secret-token",
                retryDisposition = AiRetryDisposition.NEVER, providerCode = reason.name).toPluginAskFailure()
            assertEquals(AiPluginAskFailure.PROVIDER_FAILED, outcome.failure)
            assertEquals(reason, outcome.providerReason)
            assertFalse(outcome.toString().contains("private"))
            assertFalse(outcome.toString().contains("secret-token"))
        }
    }

    @Test fun unknownAndModifiedCodesCannotCarryProviderText() {
        listOf(null, "REQUEST_REJECTED", "ONLINE_UNKNOWN", "online_network_unavailable",
            "ONLINE_NETWORK_UNAVAILABLE secret-token", " ONLINE_NETWORK_UNAVAILABLE",
            "ONLINE_NETWORK_UNAVAILABLE ", "https://private.example/secret-token").forEach { code ->
            val outcome = AiError(AiErrorCode.PROVIDER_FAILED, message = "private-error",
                providerCode = code).toPluginAskFailure()
            assertEquals(AiPluginAskFailure.PROVIDER_FAILED, outcome.failure)
            assertNull(outcome.providerReason)
            assertFalse(outcome.toString().contains("private"))
            assertFalse(outcome.toString().contains("secret-token"))
        }
    }

    @Test fun protocolTimeoutAndOtherExistingMappingsIgnoreProviderCategory() {
        AiErrorCode.VALUES.filter { it != AiErrorCode.PROVIDER_FAILED }.forEach { code ->
            val outcome = AiError(code, providerCode = "ONLINE_NETWORK_UNAVAILABLE").toPluginAskFailure()
            assertEquals(if (code == AiErrorCode.TIMEOUT) AiPluginAskFailure.TIMED_OUT
                else AiPluginAskFailure.PROVIDER_FAILED, outcome.failure)
            assertNull(outcome.providerReason)
        }
    }
}
