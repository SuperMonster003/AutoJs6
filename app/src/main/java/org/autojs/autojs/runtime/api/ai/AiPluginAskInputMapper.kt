package org.autojs.autojs.runtime.api.ai

import com.google.gson.JsonObject
import org.autojs.autojs.core.plugin.ai.OnDeviceAiPluginAskRequest
import org.autojs.autojs.core.plugin.ai.OnDeviceAiProviderComponent

/** Strict, prompt-only admission for the first explicitly selected local plugin route. */
internal object AiPluginAskInputMapper {

    fun hasPluginSelection(input: AiRequestInput): Boolean = input.options.has("plugin")

    fun requirePluginControl(input: AiRequestInput): AiControlOptions {
        val providerOptions = input.options.deepCopy()
        val control = AiControlOptions.takeFrom(providerOptions)
        require(control.pluginSelection != null) { "AI plugin selection is required" }
        return control
    }

    fun map(
        input: AiRequestInput,
        defaultTimeoutMillis: Long,
    ): OnDeviceAiPluginAskRequest {
        val control = requirePluginControl(input)
        val selection = requireNotNull(control.pluginSelection)
        require(input.messages.size() == 1) {
            "AI plugin ask requires exactly one user text message"
        }
        val element = input.messages[0]
        require(element.isJsonObject) {
            "AI plugin ask requires exactly one user text message"
        }
        val message = element.asJsonObject.deepCopy()
        val role = message.takeRequiredString("role", "AI plugin message role")
        require(role.lowercase() == "user") {
            "AI plugin ask requires exactly one user text message"
        }
        val prompt = message.takeRequiredString(
            name = "content",
            label = "AI plugin message content",
            trim = false,
        )
        require(message.entrySet().isEmpty()) {
            "AI plugin ask message contains unsupported fields"
        }
        return OnDeviceAiPluginAskRequest(
            component = OnDeviceAiProviderComponent(selection.packageName, selection.className),
            providerId = selection.providerId,
            modelId = selection.modelId,
            prompt = prompt,
            timeoutMillis = control.timeoutMillis ?: defaultTimeoutMillis,
        )
    }

    private fun JsonObject.takeRequiredString(
        name: String,
        label: String,
        trim: Boolean = true,
    ): String {
        val value = remove(name)
            ?: throw IllegalArgumentException("$label is required")
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            "$label must be a string"
        }
        val string = value.asString.let { if (trim) it.trim() else it }
        require(string.isNotEmpty()) { "$label must not be empty" }
        if (trim) {
            require(string.none(Character::isISOControl)) {
                "$label contains control characters"
            }
        }
        return string
    }
}
