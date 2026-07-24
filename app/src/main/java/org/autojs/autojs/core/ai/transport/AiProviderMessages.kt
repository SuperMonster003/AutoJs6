package org.autojs.autojs.core.ai.transport

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Provider-message helpers shared by response parsing, request mapping, and stream accumulation.
 *
 * The provider-native assistant message is intentionally kept separate from the provider-neutral
 * text/tool-call projection. Opaque fields such as Anthropic thinking signatures and Gemini
 * thought signatures must survive a same-provider tool round trip byte-for-byte.
 */
internal object AiProviderMessages {

    data class ToolCallInput(
        val id: String?,
        val name: String?,
        val arguments: JsonElement?,
        val raw: JsonObject?,
    )

    fun openAiAssistant(
        text: String,
        reasoning: String,
        toolCalls: List<AiToolCall>,
    ): JsonObject {
        return JsonObject().apply {
            addProperty("role", "assistant")
            when {
                text.isNotEmpty() -> addProperty("content", text)
                toolCalls.isNotEmpty() -> add("content", JsonNull.INSTANCE)
                else -> addProperty("content", "")
            }
            if (reasoning.isNotEmpty()) {
                addProperty("reasoning_content", reasoning)
            }
            if (toolCalls.isNotEmpty()) {
                add("tool_calls", JsonArray().apply {
                    toolCalls.forEach { toolCall ->
                        add(openAiToolCall(toolCall))
                    }
                })
            }
        }
    }

    fun toolCallsOf(message: JsonObject): List<ToolCallInput> {
        val calls = message.arrayOrNull("toolCalls")
            ?: message.arrayOrNull("tool_calls")
            ?: return emptyList()
        return calls.mapNotNull { element ->
            val call = element.takeIf(JsonElement::isJsonObject)?.asJsonObject
                ?: return@mapNotNull null
            toolCallInput(call)
        }
    }

    fun openAiToolCall(call: ToolCallInput, index: Int): JsonObject {
        val id = call.id
            ?: throw IllegalArgumentException("AI tool call at index $index must include an id")
        val name = call.name
            ?: throw IllegalArgumentException("AI tool call at index $index must include a name")
        return JsonObject().apply {
            addProperty("id", id)
            addProperty("type", "function")
            add("function", JsonObject().apply {
                addProperty("name", name)
                addProperty("arguments", argumentsString(call.arguments))
            })
        }
    }

    fun anthropicToolUse(call: ToolCallInput, index: Int): JsonObject {
        val id = call.id
            ?: throw IllegalArgumentException("Anthropic tool call at index $index must include an id")
        val name = call.name
            ?: throw IllegalArgumentException("Anthropic tool call at index $index must include a name")
        return JsonObject().apply {
            addProperty("type", "tool_use")
            addProperty("id", id)
            addProperty("name", name)
            add("input", argumentsObject(call.arguments, "Anthropic tool call at index $index"))
        }
    }

    fun geminiFunctionCallPart(call: ToolCallInput, index: Int): JsonObject {
        call.raw?.let { raw ->
            when {
                raw.objectOrNull("functionCall") != null -> return raw.deepCopy()
                raw.objectOrNull("function_call") != null -> {
                    return raw.deepCopy().apply {
                        add("functionCall", remove("function_call"))
                    }
                }
            }
        }
        val name = call.name
            ?: throw IllegalArgumentException("Gemini tool call at index $index must include a name")
        return JsonObject().apply {
            add("functionCall", JsonObject().apply {
                call.id?.let { addProperty("id", it) }
                addProperty("name", name)
                add("args", argumentsObject(call.arguments, "Gemini tool call at index $index"))
            })
        }
    }

    fun toolResultId(message: JsonObject): String? {
        return listOf("toolCallId", "tool_call_id", "toolUseId", "tool_use_id", "id")
            .firstNotNullOfOrNull(message::stringOrNull)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    fun toolResultName(message: JsonObject): String? {
        return message.stringOrNull("name")
            ?: message.objectOrNull("function")?.stringOrNull("name")
    }

    fun argumentsObject(value: JsonElement?, label: String): JsonObject {
        if (value == null || value.isJsonNull) return JsonObject()
        if (value.isJsonObject) return value.asJsonObject.deepCopy()
        if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            val parsed = try {
                JsonParser.parseString(value.asString)
            } catch (_: RuntimeException) {
                throw IllegalArgumentException("$label arguments must be a JSON object")
            }
            if (parsed.isJsonObject) return parsed.asJsonObject
        }
        throw IllegalArgumentException("$label arguments must be a JSON object")
    }

    fun argumentsString(value: JsonElement?): String {
        return when {
            value == null || value.isJsonNull -> "{}"
            value.isJsonPrimitive && value.asJsonPrimitive.isString -> value.asString
            else -> value.toString()
        }
    }

    private fun openAiToolCall(toolCall: AiToolCall): JsonObject {
        return JsonObject().apply {
            toolCall.id?.let { addProperty("id", it) }
            addProperty("type", "function")
            add("function", JsonObject().apply {
                toolCall.name?.let { addProperty("name", it) }
                addProperty("arguments", toolCall.arguments ?: "{}")
            })
        }
    }

    private fun toolCallInput(call: JsonObject): ToolCallInput {
        val raw = call.objectOrNull("raw")
        val rawToolCall = raw?.objectOrNull("toolCall")
        val rawAnthropic = raw?.takeIf { it.stringOrNull("type") == "tool_use" }
        val rawGeminiPart = raw?.takeIf {
            it.objectOrNull("functionCall") != null || it.objectOrNull("function_call") != null
        }
        val source = rawToolCall ?: rawAnthropic ?: call
        val function = source.objectOrNull("function")
        val functionCall = source.objectOrNull("functionCall")
            ?: source.objectOrNull("function_call")
            ?: rawGeminiPart?.objectOrNull("functionCall")
            ?: rawGeminiPart?.objectOrNull("function_call")
        val arguments = function?.get("arguments")
            ?: functionCall?.get("args")
            ?: functionCall?.get("arguments")
            ?: source.get("arguments")
            ?: source.get("input")
        return ToolCallInput(
            id = source.stringOrNull("id")
                ?: source.stringOrNull("call_id")
                ?: functionCall?.stringOrNull("id")
                ?: call.stringOrNull("id"),
            name = function?.stringOrNull("name")
                ?: functionCall?.stringOrNull("name")
                ?: source.stringOrNull("name")
                ?: call.stringOrNull("name"),
            arguments = arguments?.safeDeepCopy(),
            raw = rawGeminiPart?.deepCopy(),
        )
    }
}
