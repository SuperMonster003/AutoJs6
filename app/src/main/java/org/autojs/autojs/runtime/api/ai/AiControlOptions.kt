package org.autojs.autojs.runtime.api.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.autojs.autojs.core.ai.AiProviderSelector
import java.util.UUID

/**
 * Facade-only options consumed before provider-native request mapping.
 *
 * Parsing lives outside [AiService] so aliases and numeric validation remain independently
 * testable as the JavaScript API evolves.
 */
internal data class AiControlOptions(
    val profile: String?,
    val providerId: String?,
    val baseUrl: String?,
    val model: String?,
    val apiKey: String?,
    val timeoutMillis: Long?,
) {

    fun canUseStandaloneConfig(): Boolean {
        return profile == null &&
            providerId != null &&
            model != null &&
            (
                apiKey != null ||
                    baseUrl?.let {
                        AiCredentialPolicy.allowsMissingApiKey(providerId, it)
                    } == true
            )
    }

    fun profileSelector(): AiProviderSelector {
        val selectedProfile = profile
        if (selectedProfile != null) {
            val normalized = selectedProfile.trim().lowercase()
            val isUuid = runCatching { UUID.fromString(normalized).toString() == normalized }
                .getOrDefault(false)
            return if (isUuid) {
                AiProviderSelector.Id(normalized)
            } else {
                AiProviderSelector.Name(selectedProfile)
            }
        }
        return providerId?.let { AiProviderSelector.ProviderId(it) }
            ?: AiProviderSelector.Default
    }

    override fun toString(): String {
        fun String?.redacted(): String? {
            val key = apiKey?.takeIf(String::isNotBlank) ?: return this
            return this?.replace(key, "[REDACTED]")
        }
        return "AiControlOptions(profile=${profile.redacted()}, " +
            "providerId=${providerId.redacted()}, baseUrl=${baseUrl.redacted()}, " +
            "model=${model.redacted()}, apiKey=[REDACTED], timeoutMillis=$timeoutMillis)"
    }

    companion object {

        const val MIN_TIMEOUT_MILLIS = 1_000L
        const val MAX_TIMEOUT_MILLIS = 10L * 60L * 1_000L

        fun takeFrom(options: JsonObject): AiControlOptions {
            val profile = options.takeString("profile", "profileId", "profile_id")
            val provider = options.takeString("provider", "providerId", "provider_id")
            val baseUrl = options.takeString("baseUrl", "baseURL", "base_url")
            val model = options.takeString("model")
            val apiKey = options.takeString("apiKey", "api_key")
            val timeout = options.takeLong(
                "timeout",
                "timeoutMillis",
                "timeoutMs",
                "timeout_millis",
            )?.also {
                require(it in MIN_TIMEOUT_MILLIS..MAX_TIMEOUT_MILLIS) {
                    "AI timeout must be between $MIN_TIMEOUT_MILLIS and " +
                        "$MAX_TIMEOUT_MILLIS milliseconds"
                }
            }
            options.remove("stream")
            return AiControlOptions(
                profile = profile,
                providerId = provider,
                baseUrl = baseUrl,
                model = model,
                apiKey = apiKey,
                timeoutMillis = timeout,
            )
        }

        private fun JsonObject.takeString(vararg names: String): String? {
            var selected: JsonElement? = null
            names.forEach { name ->
                remove(name)?.let { value ->
                    if (selected == null && !value.isJsonNull) {
                        selected = value
                    }
                }
            }
            val value = selected ?: return null
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                "AI option ${names.first()} must be a string"
            }
            return value.asString.trim().takeIf(String::isNotEmpty)
        }

        private fun JsonObject.takeLong(vararg names: String): Long? {
            var selected: JsonElement? = null
            names.forEach { name ->
                remove(name)?.let { value ->
                    if (selected == null && !value.isJsonNull) {
                        selected = value
                    }
                }
            }
            val value = selected ?: return null
            require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
                "AI option ${names.first()} must be a number"
            }
            return runCatching { value.asBigDecimal.longValueExact() }.getOrElse {
                throw IllegalArgumentException(
                    "AI option ${names.first()} must be an integer",
                    it,
                )
            }
        }
    }
}
