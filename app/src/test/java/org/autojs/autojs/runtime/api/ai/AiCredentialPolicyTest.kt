package org.autojs.autojs.runtime.api.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCredentialPolicyTest {

    @Test
    fun secureAndLoopbackBaseUrlsAreAcceptedAndNormalized() {
        assertEquals(
            "https://api.example.com/v1",
            AiCredentialPolicy.normalizeBaseUrl(" https://api.example.com/v1/ "),
        )
        assertEquals(
            "http://127.0.0.1:11434/v1",
            AiCredentialPolicy.normalizeBaseUrl("http://127.0.0.1:11434/v1/"),
        )
        assertEquals(
            "http://localhost:8080",
            AiCredentialPolicy.normalizeBaseUrl("http://localhost:8080"),
        )
    }

    @Test
    fun unsafeCredentialDestinationsAreRejected() {
        listOf(
            "http://api.example.com/v1",
            "https://user:secret@api.example.com/v1",
            "https://api.example.com/v1?key=value",
            "https://api.example.com/v1#fragment",
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                AiCredentialPolicy.normalizeBaseUrl(value)
            }
        }
    }

    @Test
    fun originComparisonIncludesSchemeHostAndEffectivePortButNotPath() {
        assertTrue(
            AiCredentialPolicy.sameOrigin(
                "https://api.example.com/v1",
                "https://api.example.com/chat",
            ),
        )
        assertTrue(
            AiCredentialPolicy.sameOrigin(
                "https://API.EXAMPLE.COM/v1",
                "https://api.example.com:443/chat",
            ),
        )
        assertFalse(
            AiCredentialPolicy.sameOrigin(
                "https://api.example.com/v1",
                "https://other.example.com/v1",
            ),
        )
        assertFalse(
            AiCredentialPolicy.sameOrigin(
                "https://api.example.com/v1",
                "https://api.example.com:8443/v1",
            ),
        )
    }

    @Test
    fun storedKeysCannotCrossProviderProtocolsOrOrigins() {
        AiCredentialPolicy.requireStoredCredentialDestination(
            storedProviderId = "openai",
            requestedProviderId = "openai",
            storedBaseUrl = "https://api.example.com/v1",
            requestedBaseUrl = "https://api.example.com/custom",
        )

        assertThrows(IllegalArgumentException::class.java) {
            AiCredentialPolicy.requireStoredCredentialDestination(
                storedProviderId = "openai",
                requestedProviderId = "anthropic",
                storedBaseUrl = "https://api.example.com/v1",
                requestedBaseUrl = "https://api.example.com/v1",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiCredentialPolicy.requireStoredCredentialDestination(
                storedProviderId = "openai",
                requestedProviderId = "openai",
                storedBaseUrl = "https://api.example.com/v1",
                requestedBaseUrl = "https://evil.example/v1",
            )
        }
    }

    @Test
    fun onlyLoopbackOpenAiCompatibleEndpointsMayOmitApiKey() {
        listOf(
            "http://localhost:11434/v1",
            "https://127.0.0.1/v1",
            "http://127.255.10.20:1234/v1",
            "http://[::1]:11434/v1",
        ).forEach { baseUrl ->
            assertTrue(
                AiCredentialPolicy.allowsMissingApiKey(
                    "OPENAI-COMPATIBLE",
                    baseUrl,
                ),
            )
        }

        assertFalse(
            AiCredentialPolicy.allowsMissingApiKey(
                "openai",
                "http://127.0.0.1:11434/v1",
            ),
        )
        listOf(
            "https://models.example.com/v1",
            "http://192.168.1.8:11434/v1",
            "http://localhost:11434/v1?token=secret",
        ).forEach { baseUrl ->
            assertFalse(
                AiCredentialPolicy.allowsMissingApiKey(
                    "openai-compatible",
                    baseUrl,
                ),
            )
        }
    }
}
