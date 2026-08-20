package org.autojs.autojs.runtime.api.ai

import com.google.gson.JsonObject
import org.autojs.autojs.core.plugin.ai.OnDeviceAiPluginAskRequest
import org.autojs.autojs.core.plugin.ai.OnDeviceAiProviderComponent
import org.autojs.plugin.ondeviceai.api.OnDeviceAiLimits

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
        val pluginOptions = input.options.deepCopy()
        val temperature = pluginOptions.takeOptionalDouble("temperature", "AI option temperature")?.also {
            require(it >= 0.0) { "AI option temperature must be non-negative" }
        }
        val topK = pluginOptions.takeOptionalInt("topK", "AI option topK")?.also {
            require(it > 0) { "AI option topK must be positive" }
        }
        val topP = pluginOptions.takeOptionalDouble("topP", "AI option topP")?.also {
            require(it in 0.0..1.0) { "AI option topP must be between 0 and 1" }
        }
        val maximumOutputTokens = pluginOptions.takeOptionalLong("maxTokens", "AI option maxTokens")?.also {
            require(it in 1L..OnDeviceAiLimits.MAX_OUTPUT_TOKENS) {
                "AI option maxTokens must be between 1 and ${OnDeviceAiLimits.MAX_OUTPUT_TOKENS}"
            }
        }
        val control = AiControlOptions.takeFrom(pluginOptions)
        require(control.pluginSelection != null) { "AI plugin selection is required" }
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
            maximumOutputTokens = maximumOutputTokens,
            temperature = temperature,
            topK = topK,
            topP = topP,
        )
    }

    private fun JsonObject.takeOptionalDouble(name: String, label: String): Double? {
        val value = remove(name) ?: return null
        if (value.isJsonNull) return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "$label must be a number" }
        return runCatching { value.asBigDecimal.toDouble() }
            .getOrElse { throw IllegalArgumentException("$label must be a number", it) }
            .also { require(it.isFinite()) { "$label must be finite" } }
    }

    private fun JsonObject.takeOptionalInt(name: String, label: String): Int? {
        val value = remove(name) ?: return null
        if (value.isJsonNull) return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "$label must be a number" }
        return runCatching { value.asBigDecimal.intValueExact() }
            .getOrElse { throw IllegalArgumentException("$label must be an integer", it) }
    }

    private fun JsonObject.takeOptionalLong(name: String, label: String): Long? {
        val value = remove(name) ?: return null
        if (value.isJsonNull) return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "$label must be a number" }
        return runCatching { value.asBigDecimal.longValueExact() }
            .getOrElse { throw IllegalArgumentException("$label must be an integer", it) }
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
