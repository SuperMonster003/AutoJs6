package org.autojs.autojs.core.ai.transport

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/**
 * Maps the small provider-neutral option surface accepted by the facade to provider-native JSON.
 *
 * Provider-native fields remain supported and take precedence over their neutral aliases. Keys
 * used only by the facade or network dispatcher are always removed from request JSON.
 */
internal object AiOptionsMapper {

    private val FACADE_CONTROL_KEYS = setOf(
        "abortSignal",
        "apiKey",
        "api_key",
        "baseURL",
        "baseUrl",
        "base_url",
        "callbacks",
        "headers",
        "messages",
        "model",
        "onChunk",
        "onDone",
        "onError",
        "onReasoning",
        "onText",
        "onToken",
        "onToolCall",
        "onUsage",
        "profile",
        "profileId",
        "profile_id",
        "provider",
        "providerId",
        "provider_id",
        "raw",
        "returnRaw",
        "signal",
        "stream",
        "timeout",
        "timeoutMillis",
        "timeout_millis",
        "timeoutMs",
    )

    fun map(
        protocol: AiProtocol,
        providerId: String,
        options: JsonObject,
    ): JsonObject {
        val result = options.deepCopy().apply {
            FACADE_CONTROL_KEYS.forEach(::remove)
        }
        return when (protocol) {
            AiProtocol.OPENAI_COMPATIBLE -> mapOpenAi(result, providerId)
            AiProtocol.ANTHROPIC_MESSAGES -> mapAnthropic(result)
            AiProtocol.GEMINI_GENERATE_CONTENT -> mapGemini(result)
        }
    }

    private fun mapOpenAi(result: JsonObject, providerId: String): JsonObject {
        result.move("maxTokens", "max_tokens")
        result.move("topP", "top_p")
        result.move("stopSequences", "stop")

        val topK = result.remove("topK")
        val normalizedProviderId = AiProviderIds.normalize(providerId)
        if (topK != null &&
            normalizedProviderId in setOf(AiProviderIds.OPENROUTER, AiProviderIds.OPENAI_COMPATIBLE) &&
            !result.has("top_k")
        ) {
            result.add("top_k", topK)
        }

        result.normalizeTools(::openAiTools)
        result.move("toolChoice", "tool_choice", ::openAiToolChoice)
        return result
    }

    private fun mapAnthropic(result: JsonObject): JsonObject {
        result.move("maxTokens", "max_tokens")
        result.move("topP", "top_p")
        result.move("topK", "top_k")
        result.move("stopSequences", "stop_sequences", ::arrayValue)
        result.normalizeTools(::anthropicTools)
        result.move("toolChoice", "tool_choice", ::anthropicToolChoice)
        return result
    }

    private fun mapGemini(result: JsonObject): JsonObject {
        val generationConfig = result.objectOrNull("generationConfig")?.deepCopy() ?: JsonObject()
        result.remove("generationConfig")
        result.moveInto("maxTokens", generationConfig, "maxOutputTokens")
        result.moveInto("temperature", generationConfig, "temperature")
        result.moveInto("topP", generationConfig, "topP")
        result.moveInto("topK", generationConfig, "topK")
        result.moveInto("stopSequences", generationConfig, "stopSequences", ::arrayValue)
        if (generationConfig.size() > 0) {
            result.add("generationConfig", generationConfig)
        }

        result.normalizeTools(::geminiTools)
        val toolChoice = result.remove("toolChoice")
        if (toolChoice != null && !result.has("toolConfig")) {
            result.add("toolConfig", geminiToolChoice(toolChoice))
        }
        return result
    }

    private fun openAiTools(value: JsonElement): JsonElement {
        if (!value.isJsonArray) return value.safeDeepCopy()
        return JsonArray().apply {
            value.asJsonArray.forEach { element ->
                val tool = element.takeIf(JsonElement::isJsonObject)?.asJsonObject
                if (tool == null) {
                    add(element.safeDeepCopy())
                    return@forEach
                }
                val declarations = tool.arrayOrNull("functionDeclarations")
                if (declarations != null) {
                    declarations.forEach { declaration ->
                        add(openAiFunctionTool(declaration))
                    }
                } else {
                    add(openAiFunctionTool(tool))
                }
            }
        }
    }

    private fun openAiFunctionTool(element: JsonElement): JsonElement {
        val tool = element.takeIf(JsonElement::isJsonObject)?.asJsonObject
            ?: return element.safeDeepCopy()
        if (tool.objectOrNull("function") != null) {
            return tool.deepCopy().apply {
                if (!has("type")) addProperty("type", "function")
            }
        }
        val name = tool.stringOrNull("name") ?: return tool.deepCopy()
        return JsonObject().apply {
            addProperty("type", "function")
            add("function", JsonObject().apply {
                addProperty("name", name)
                tool.get("description")?.let { add("description", it.safeDeepCopy()) }
                (tool.get("parameters") ?: tool.get("input_schema"))
                    ?.let { add("parameters", it.safeDeepCopy()) }
                tool.get("strict")?.let { add("strict", it.safeDeepCopy()) }
            })
        }
    }

    private fun anthropicTools(value: JsonElement): JsonElement {
        if (!value.isJsonArray) return value.safeDeepCopy()
        return JsonArray().apply {
            value.asJsonArray.forEach { element ->
                val tool = element.takeIf(JsonElement::isJsonObject)?.asJsonObject
                if (tool == null) {
                    add(element.safeDeepCopy())
                    return@forEach
                }
                val declarations = tool.arrayOrNull("functionDeclarations")
                if (declarations != null) {
                    declarations.forEach { declaration ->
                        add(anthropicFunctionTool(declaration))
                    }
                } else {
                    add(anthropicFunctionTool(tool))
                }
            }
        }
    }

    private fun anthropicFunctionTool(element: JsonElement): JsonElement {
        val tool = element.takeIf(JsonElement::isJsonObject)?.asJsonObject
            ?: return element.safeDeepCopy()
        if (tool.stringOrNull("name") != null && tool.has("input_schema")) {
            return tool.deepCopy()
        }
        val function = tool.objectOrNull("function") ?: tool
        val name = function.stringOrNull("name") ?: return tool.deepCopy()
        return JsonObject().apply {
            addProperty("name", name)
            function.get("description")?.let { add("description", it.safeDeepCopy()) }
            add(
                "input_schema",
                (function.get("parameters") ?: function.get("input_schema"))
                    ?.safeDeepCopy()
                    ?: emptyObjectSchema(),
            )
        }
    }

    private fun geminiTools(value: JsonElement): JsonElement {
        if (!value.isJsonArray) return value.safeDeepCopy()
        val declarations = JsonArray()
        val nativeTools = JsonArray()
        value.asJsonArray.forEach { element ->
            val tool = element.takeIf(JsonElement::isJsonObject)?.asJsonObject
            when {
                tool == null -> nativeTools.add(element.safeDeepCopy())
                tool.has("functionDeclarations") -> nativeTools.add(tool.deepCopy())
                else -> geminiFunctionDeclaration(tool)?.let(declarations::add)
                    ?: nativeTools.add(tool.deepCopy())
            }
        }
        if (declarations.size() > 0) {
            nativeTools.add(JsonObject().apply {
                add("functionDeclarations", declarations)
            })
        }
        return nativeTools
    }

    private fun geminiFunctionDeclaration(tool: JsonObject): JsonObject? {
        val function = tool.objectOrNull("function") ?: tool
        val name = function.stringOrNull("name") ?: return null
        return JsonObject().apply {
            addProperty("name", name)
            function.get("description")?.let { add("description", it.safeDeepCopy()) }
            (function.get("parameters") ?: function.get("input_schema"))
                ?.let { add("parameters", it.safeDeepCopy()) }
        }
    }

    private fun openAiToolChoice(value: JsonElement): JsonElement {
        if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            return when (val choice = value.asString) {
                "auto", "none", "required" -> JsonPrimitive(choice)
                "any" -> JsonPrimitive("required")
                else -> openAiNamedToolChoice(choice)
            }
        }
        val choice = value.takeIf(JsonElement::isJsonObject)?.asJsonObject
            ?: return value.safeDeepCopy()
        if (choice.objectOrNull("function") != null) return choice.deepCopy()
        return choice.stringOrNull("name")
            ?.let(::openAiNamedToolChoice)
            ?: choice.deepCopy()
    }

    private fun openAiNamedToolChoice(name: String): JsonObject {
        return JsonObject().apply {
            addProperty("type", "function")
            add("function", JsonObject().apply { addProperty("name", name) })
        }
    }

    private fun anthropicToolChoice(value: JsonElement): JsonElement {
        if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            return when (val choice = value.asString) {
                "auto", "none" -> typedChoice(choice)
                "any", "required" -> typedChoice("any")
                else -> anthropicNamedToolChoice(choice)
            }
        }
        val choice = value.takeIf(JsonElement::isJsonObject)?.asJsonObject
            ?: return value.safeDeepCopy()
        val functionName = choice.objectOrNull("function")?.stringOrNull("name")
        if (functionName != null) return anthropicNamedToolChoice(functionName)
        val type = choice.stringOrNull("type")
        val name = choice.stringOrNull("name")
        if (name != null && type in setOf(null, "function")) {
            return anthropicNamedToolChoice(name)
        }
        return choice.deepCopy()
    }

    private fun anthropicNamedToolChoice(name: String): JsonObject {
        return typedChoice("tool").apply { addProperty("name", name) }
    }

    private fun typedChoice(type: String): JsonObject {
        return JsonObject().apply { addProperty("type", type) }
    }

    private fun geminiToolChoice(value: JsonElement): JsonObject {
        var mode = "AUTO"
        var name: String? = null
        if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            when (val choice = value.asString) {
                "auto" -> mode = "AUTO"
                "none" -> mode = "NONE"
                "any", "required" -> mode = "ANY"
                else -> {
                    mode = "ANY"
                    name = choice
                }
            }
        } else if (value.isJsonObject) {
            val choice = value.asJsonObject
            name = choice.objectOrNull("function")?.stringOrNull("name")
                ?: choice.stringOrNull("name")
            mode = when (choice.stringOrNull("type")) {
                "none" -> "NONE"
                "any", "required", "function", "tool" -> "ANY"
                else -> if (name == null) "AUTO" else "ANY"
            }
        }
        return JsonObject().apply {
            add("functionCallingConfig", JsonObject().apply {
                addProperty("mode", mode)
                if (name != null) {
                    add("allowedFunctionNames", JsonArray().apply { add(name) })
                }
            })
        }
    }

    private fun JsonObject.normalizeTools(transform: (JsonElement) -> JsonElement) {
        remove("tools")?.let { add("tools", transform(it)) }
    }

    private fun JsonObject.move(
        source: String,
        target: String,
        transform: (JsonElement) -> JsonElement = JsonElement::safeDeepCopy,
    ) {
        val value = remove(source) ?: return
        if (!has(target)) add(target, transform(value))
    }

    private fun JsonObject.moveInto(
        source: String,
        target: JsonObject,
        targetName: String,
        transform: (JsonElement) -> JsonElement = JsonElement::safeDeepCopy,
    ) {
        val value = remove(source) ?: return
        if (!target.has(targetName)) target.add(targetName, transform(value))
    }

    private fun arrayValue(value: JsonElement): JsonElement {
        if (value.isJsonArray) return value.safeDeepCopy()
        return JsonArray().apply { add(value.safeDeepCopy()) }
    }

    private fun emptyObjectSchema(): JsonObject {
        return JsonObject().apply {
            addProperty("type", "object")
            add("properties", JsonObject())
        }
    }

}
