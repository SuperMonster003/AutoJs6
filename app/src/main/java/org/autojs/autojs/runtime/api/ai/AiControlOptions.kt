package org.autojs.autojs.runtime.api.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.autojs.autojs.core.plugin.ai.ThreeStoneAiOfficialPlugin
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ai.provider.api.AiProviderLimits

/** Facade-only controls consumed before a request crosses the plugin boundary. */
internal data class AiControlOptions(
    val timeoutMillis: Long?,
    val targetId: String?,
    val pluginSelection: AiPluginSelection,
) {

    companion object {

        fun takeFrom(options: JsonObject): AiControlOptions {
            val pluginElement = options.remove("plugin")
            val targetId = options.takeOptionalString("target", "AI target")?.also {
                require(AiValidation.TARGET_ID.matches(it)) { "AI target is invalid" }
            }
            val timeout = options.takeOptionalLong("timeout", "AI timeout")?.also {
                require(it in AiProviderLimits.MIN_TIMEOUT_MILLIS..
                    AiProviderLimits.MAX_TIMEOUT_MILLIS) {
                    "AI timeout must be between ${AiProviderLimits.MIN_TIMEOUT_MILLIS} and " +
                        "${AiProviderLimits.MAX_TIMEOUT_MILLIS} milliseconds"
                }
            }
            val pluginSelection = pluginElement?.let(::parsePluginSelection)
                ?: officialPluginSelection()
            require(options.entrySet().isEmpty()) {
                "AI request contains unsupported options"
            }
            return AiControlOptions(
                timeoutMillis = timeout,
                targetId = targetId,
                pluginSelection = pluginSelection,
            )
        }

        private fun parsePluginSelection(value: JsonElement): AiPluginSelection {
            if (value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) {
                require(value.asBoolean) { "AI plugin selection must not be false" }
                return officialPluginSelection()
            }
            require(value.isJsonObject) { "AI plugin selection must be an object" }
            val plugin = value.asJsonObject.deepCopy()
            val componentElement = plugin.remove("component")
            if (componentElement == null) {
                val providerId = plugin.takeOptionalString(
                    "providerId",
                    "AI plugin providerId",
                ) ?: ThreeStoneAiOfficialPlugin.PROVIDER_ID
                require(plugin.entrySet().isEmpty()) {
                    "AI plugin selection contains unsupported fields"
                }
                require(AiValidation.STABLE_ID.matches(providerId)) {
                    "AI plugin providerId is invalid"
                }
                return officialPluginSelection(providerId)
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
            return AiPluginSelection(packageName, className, providerId)
        }

        private fun officialPluginSelection(
            providerId: String = ThreeStoneAiOfficialPlugin.PROVIDER_ID,
        ) = AiPluginSelection(
            packageName = ThreeStoneAiOfficialPlugin.PACKAGE_NAME,
            className = ThreeStoneAiOfficialPlugin.CLASS_NAME,
            providerId = providerId,
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

        private fun JsonObject.takeOptionalLong(name: String, label: String): Long? {
            val value = remove(name) ?: return null
            if (value.isJsonNull) return null
            require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
                "$label must be a number"
            }
            return runCatching { value.asBigDecimal.longValueExact() }.getOrElse {
                throw IllegalArgumentException("$label must be an integer", it)
            }
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
    }
}

internal data class AiPluginSelection(
    val packageName: String,
    val className: String,
    val providerId: String,
)
