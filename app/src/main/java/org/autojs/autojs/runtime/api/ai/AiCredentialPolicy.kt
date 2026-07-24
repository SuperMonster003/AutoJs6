package org.autojs.autojs.runtime.api.ai

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Security boundary for credential-bearing AI requests.
 */
internal object AiCredentialPolicy {

    fun normalizeBaseUrl(value: String): String {
        val url = value.trim().toHttpUrlOrNull()
            ?: throw IllegalArgumentException("AI provider base URL must be a valid URL")
        require(url.username.isEmpty() && url.password.isEmpty()) {
            "AI provider base URL must not contain user information"
        }
        require(url.query == null && url.fragment == null) {
            "AI provider base URL must not contain a query or fragment"
        }
        require(url.scheme == "https" || url.scheme == "http" && isLoopbackHost(url.host)) {
            "AI provider base URL must use HTTPS; plain HTTP is allowed only for loopback hosts"
        }
        return url.toString().trimEnd('/')
    }

    fun requireStoredCredentialDestination(
        storedProviderId: String,
        requestedProviderId: String,
        storedBaseUrl: String,
        requestedBaseUrl: String,
    ) {
        require(storedProviderId == requestedProviderId) {
            "A stored API key cannot be used with a different AI provider protocol"
        }
        require(sameOrigin(storedBaseUrl, requestedBaseUrl)) {
            "A stored API key cannot be sent to a different origin; provide apiKey explicitly"
        }
    }

    /**
     * The only credential-free provider mode is a generic OpenAI-compatible endpoint bound to
     * the local device. This covers local model servers without weakening remote authentication
     * or the plain-HTTP policy.
     */
    fun allowsMissingApiKey(providerId: String, baseUrl: String): Boolean {
        if (providerId.trim().lowercase() != OPENAI_COMPATIBLE_PROVIDER_ID) {
            return false
        }
        val url = baseUrl.toHttpUrlOrNull() ?: return false
        return url.query == null &&
            url.fragment == null &&
            url.username.isEmpty() &&
            url.password.isEmpty() &&
            isLoopbackHost(url.host)
    }

    internal fun sameOrigin(first: String, second: String): Boolean {
        val firstUrl = first.toHttpUrlOrNull() ?: return false
        val secondUrl = second.toHttpUrlOrNull() ?: return false
        return firstUrl.scheme == secondUrl.scheme &&
            firstUrl.host == secondUrl.host &&
            firstUrl.port == secondUrl.port
    }

    internal fun isLoopbackHost(host: String): Boolean {
        val normalized = host
            .trim()
            .removePrefix("[")
            .removeSuffix("]")
            .trimEnd('.')
            .lowercase()
        if (
            normalized == "localhost" ||
            normalized == "::1" ||
            normalized == "0:0:0:0:0:0:0:1"
        ) {
            return true
        }
        val parts = normalized.split('.')
        return parts.size == 4 &&
            parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true } &&
            parts.first() == "127"
    }

    private const val OPENAI_COMPATIBLE_PROVIDER_ID = "openai-compatible"
}
