package org.autojs.autojs.runtime.api.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.autojs.autojs.core.ai.AiProviderSelector
import org.autojs.autojs.core.plugin.ai.ThreeStoneAiOfficialPlugin
import org.autojs.plugin.ai.common.api.AiValidation
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
    val pluginSelection: AiPluginSelection? = null,
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
            "model=${model.redacted()}, apiKey=[REDACTED], timeoutMillis=$timeoutMillis, " +
            "pluginSelection=$pluginSelection)"
    }

    companion object {

        const val MIN_TIMEOUT_MILLIS = 1_000L
        const val MAX_TIMEOUT_MILLIS = 10L * 60L * 1_000L

        fun takeFrom(options: JsonObject): AiControlOptions {
            val pluginElement = options.remove("plugin")
            val hasCloudControl = CLOUD_CONTROL_NAMES.any(options::has)
            val hasExplicitStreamControl = options.has("stream")
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
            val pluginSelection = pluginElement?.let(::parsePluginSelection)
            if (pluginSelection != null) {
                require(!hasCloudControl) {
                    "AI plugin selection cannot be combined with cloud provider controls"
                }
                require(!hasExplicitStreamControl && options.entrySet().isEmpty()) {
                    "AI plugin selection contains unsupported options"
                }
            }
            return AiControlOptions(
                profile = profile,
                providerId = provider,
                baseUrl = baseUrl,
                model = model,
                apiKey = apiKey,
                timeoutMillis = timeout,
                pluginSelection = pluginSelection,
            )
        }

        private fun parsePluginSelection(value: JsonElement): AiPluginSelection {
            if (value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) {
                require(value.asBoolean) { "AI plugin selection must not be false" }
                return officialPluginSelection(modelId = null)
            }
            require(value.isJsonObject) { "AI plugin selection must be an object" }
            val plugin = value.asJsonObject.deepCopy()
            val componentElement = plugin.remove("component")
            val modelId = plugin.takeOptionalString("modelId", "AI plugin modelId")?.also {
                require(AiValidation.STABLE_ID.matches(it)) { "AI plugin modelId is invalid" }
            }
            if (componentElement == null) {
                // Omitted component selects the official on-device plugin; providerId may still
                // be pinned explicitly, and modelId may be omitted for its single-model default.
                val providerId = plugin.takeOptionalString("providerId", "AI plugin providerId")
                    ?: ThreeStoneAiOfficialPlugin.PROVIDER_ID
                require(plugin.entrySet().isEmpty()) {
                    "AI plugin selection contains unsupported fields"
                }
                require(AiValidation.STABLE_ID.matches(providerId)) {
                    "AI plugin providerId is invalid"
                }
                return officialPluginSelection(modelId = modelId, providerId = providerId)
            }
            val providerId = plugin.takeRequiredString("providerId", "AI plugin providerId")
            require(plugin.entrySet().isEmpty()) {
                "AI plugin selection contains unsupported fields"
            }
            require(AiValidation.STABLE_ID.matches(providerId)) {
                "AI plugin providerId is invalid"
            }
            require(componentElement.isJsonObject) {
                "AI plugin component must be an object"
            }
            val component = componentElement.asJsonObject.deepCopy()
            val packageName = component.takeRequiredString(
                "packageName",
                "AI plugin component packageName",
            )
            val className = component.takeRequiredString(
                "className",
                "AI plugin component className",
            )
            require(component.entrySet().isEmpty()) {
                "AI plugin component contains unsupported fields"
            }
            return AiPluginSelection(
                packageName = packageName,
                className = className,
                providerId = providerId,
                modelId = modelId,
            )
        }

        private fun officialPluginSelection(
            modelId: String?,
            providerId: String = ThreeStoneAiOfficialPlugin.PROVIDER_ID,
        ) = AiPluginSelection(
            packageName = ThreeStoneAiOfficialPlugin.PACKAGE_NAME,
            className = ThreeStoneAiOfficialPlugin.CLASS_NAME,
            providerId = providerId,
            modelId = modelId,
        )

        private fun JsonObject.takeRequiredString(name: String, label: String): String {
            val value = remove(name)
                ?: throw IllegalArgumentException("$label is required")
            return requireCleanString(value, label)
        }

        private fun JsonObject.takeOptionalString(name: String, label: String): String? {
            val value = remove(name) ?: return null
            if (value.isJsonNull) return null
            return requireCleanString(value, label)
        }

        private fun requireCleanString(value: JsonElement, label: String): String {
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                "$label must be a string"
            }
            return value.asString.trim().also {
                require(it.isNotEmpty()) { "$label must not be blank" }
                require(it.none(Character::isISOControl)) {
                    "$label contains control characters"
                }
            }
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

        private val CLOUD_CONTROL_NAMES = setOf(
            "profile",
            "profileId",
            "profile_id",
            "provider",
            "providerId",
            "provider_id",
            "baseUrl",
            "baseURL",
            "base_url",
            "model",
            "apiKey",
            "api_key",
        )
    }
}

/** [modelId] may be omitted; the plugin route then resolves the provider's selected model. */
internal data class AiPluginSelection(
    val packageName: String,
    val className: String,
    val providerId: String,
    val modelId: String?,
)
