package org.autojs.autojs.core.ai.transport

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/**
 * Stateful parser for one provider stream. Instances must not be shared between requests.
 */
class AiStreamParser(providerId: String) {

    private data class ToolState(
        var id: String? = null,
        var name: String? = null,
    )

    private val normalizedProviderId = AiProviderIds.normalize(providerId)
    private val protocol = AiProviderIds.protocolOf(normalizedProviderId)
    private val openAiTools = mutableMapOf<Int, ToolState>()
    private val anthropicTools = mutableMapOf<Int, ToolState>()

    fun parse(event: SseEvent): AiStreamChunk {
        if (event.isDone) {
            return AiStreamChunk(
                raw = JsonPrimitive(event.data),
                done = true,
            )
        }
        val raw = parseObject(event.data)
        return when (protocol) {
            AiProtocol.OPENAI_COMPATIBLE -> parseOpenAi(event, raw)
            AiProtocol.ANTHROPIC_MESSAGES -> parseAnthropic(event, raw)
            AiProtocol.GEMINI_GENERATE_CONTENT -> parseGemini(raw)
        }
    }

    private fun parseOpenAi(event: SseEvent, root: JsonObject): AiStreamChunk {
        AiProtocolJson.errorOf(root)?.let {
            return AiStreamChunk(
                raw = root.deepCopy(),
                done = true,
                error = it,
            )
        }
        val eventType = root.stringOrNull("type") ?: event.event
        if (eventType?.startsWith("response.") == true || eventType == "error") {
            return parseOpenAiResponsesEvent(eventType, root)
        }

        val choice = root.arrayOrNull("choices")
            ?.firstOrNull()
            ?.takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
        val delta = choice?.objectOrNull("delta")
        val text = buildString {
            delta?.get("content").appendTextContent(this)
            delta?.get("refusal").textValue()?.let(::append)
            if (isEmpty()) {
                choice?.get("text").textValue()?.let(::append)
            }
        }
        val reasoning = buildString {
            if (delta != null) {
                appendOpenAiReasoning(this, delta)
            }
        }
        val toolCalls = parseOpenAiToolDeltas(delta)
        return AiStreamChunk(
            text = text,
            reasoning = reasoning,
            toolCalls = toolCalls,
            usage = AiProtocolJson.openAiUsage(root.objectOrNull("usage")),
            raw = root.deepCopy(),
            finishReason = choice?.stringOrNull("finish_reason"),
        )
    }

    private fun parseOpenAiResponsesEvent(eventType: String, root: JsonObject): AiStreamChunk {
        val text: String
        val reasoning: String
        val toolCalls = mutableListOf<AiToolCall>()
        var usage: AiUsage? = null
        var finishReason: String? = null
        var done = false
        var error: AiProviderError? = null

        when (eventType) {
            "response.output_text.delta",
            "response.refusal.delta",
            -> {
                text = root.stringOrNull("delta").orEmpty()
                reasoning = ""
            }
            "response.reasoning_text.delta",
            "response.reasoning_summary_text.delta",
            -> {
                text = ""
                reasoning = root.stringOrNull("delta").orEmpty()
            }
            "response.output_item.added" -> {
                text = ""
                reasoning = ""
                root.objectOrNull("item")
                    ?.takeIf { it.stringOrNull("type") == "function_call" }
                    ?.let { item ->
                        val index = root.intOrNull("output_index") ?: 0
                        val state = openAiTools.getOrPut(index, ::ToolState)
                        state.id = item.stringOrNull("call_id") ?: item.stringOrNull("id") ?: state.id
                        state.name = item.stringOrNull("name") ?: state.name
                        toolCalls.add(
                            AiToolCall(
                                index = index,
                                id = state.id,
                                name = state.name,
                                arguments = item.get("arguments").jsonStringOrNull(),
                                raw = item.deepCopy(),
                            )
                        )
                    }
            }
            "response.function_call_arguments.delta" -> {
                text = ""
                reasoning = ""
                val index = root.intOrNull("output_index") ?: 0
                val state = openAiTools.getOrPut(index, ::ToolState)
                state.id = root.stringOrNull("call_id")
                    ?: state.id
                    ?: root.stringOrNull("item_id")
                state.name = root.stringOrNull("name") ?: state.name
                toolCalls.add(
                    AiToolCall(
                        index = index,
                        id = state.id,
                        name = state.name,
                        arguments = root.stringOrNull("delta").orEmpty(),
                        argumentsAreDelta = true,
                        raw = root.deepCopy(),
                    )
                )
            }
            "response.function_call_arguments.done" -> {
                text = ""
                reasoning = ""
                val index = root.intOrNull("output_index") ?: 0
                val state = openAiTools.getOrPut(index, ::ToolState)
                state.id = root.stringOrNull("call_id")
                    ?: state.id
                    ?: root.stringOrNull("item_id")
                state.name = root.stringOrNull("name") ?: state.name
                toolCalls.add(
                    AiToolCall(
                        index = index,
                        id = state.id,
                        name = state.name,
                        arguments = root.stringOrNull("arguments"),
                        raw = root.deepCopy(),
                    )
                )
            }
            "response.completed", "response.incomplete", "response.failed" -> {
                text = ""
                reasoning = ""
                done = true
                finishReason = eventType.removePrefix("response.")
                val response = root.objectOrNull("response")
                usage = AiProtocolJson.openAiUsage(response?.objectOrNull("usage"))
                if (eventType == "response.failed") {
                    error = response?.objectOrNull("error")?.let { errorObject ->
                        AiProviderError(
                            type = errorObject.stringOrNull("type"),
                            code = errorObject.stringOrNull("code"),
                            message = errorObject.stringOrNull("message"),
                        )
                    } ?: AiProviderError(type = "response.failed")
                }
            }
            "error" -> {
                text = ""
                reasoning = ""
                done = true
                error = AiProviderError(
                    type = root.stringOrNull("type"),
                    code = root.stringOrNull("code"),
                    message = root.stringOrNull("message"),
                )
            }
            else -> {
                text = ""
                reasoning = ""
            }
        }
        return AiStreamChunk(
            text = text,
            reasoning = reasoning,
            toolCalls = toolCalls,
            usage = usage,
            raw = root.deepCopy(),
            finishReason = finishReason,
            done = done,
            error = error,
        )
    }

    private fun parseAnthropic(event: SseEvent, root: JsonObject): AiStreamChunk {
        val eventType = root.stringOrNull("type") ?: event.event
        if (eventType == "error") {
            return AiStreamChunk(
                raw = root.deepCopy(),
                done = true,
                error = AiProtocolJson.errorOf(root) ?: AiProviderError(type = "error"),
            )
        }

        var text = ""
        var reasoning = ""
        val toolCalls = mutableListOf<AiToolCall>()
        var usage: AiUsage? = null
        var finishReason: String? = null
        var done = false

        when (eventType) {
            "message_start" -> {
                usage = AiProtocolJson.anthropicUsage(root.objectOrNull("message")?.objectOrNull("usage"))
            }
            "content_block_start" -> {
                val index = root.intOrNull("index") ?: 0
                root.objectOrNull("content_block")?.let { block ->
                    when (block.stringOrNull("type")) {
                        "text" -> text = block.stringOrNull("text").orEmpty()
                        "thinking" -> reasoning = block.stringOrNull("thinking").orEmpty()
                        "tool_use" -> {
                            val state = anthropicTools.getOrPut(index, ::ToolState)
                            state.id = block.stringOrNull("id") ?: state.id
                            state.name = block.stringOrNull("name") ?: state.name
                            toolCalls.add(
                                AiToolCall(
                                    index = index,
                                    id = state.id,
                                    name = state.name,
                                    arguments = block.get("input").jsonStringOrNull(),
                                    raw = block.deepCopy(),
                                )
                            )
                        }
                        else -> Unit
                    }
                }
            }
            "content_block_delta" -> {
                val index = root.intOrNull("index") ?: 0
                root.objectOrNull("delta")?.let { delta ->
                    when (delta.stringOrNull("type")) {
                        "text_delta" -> text = delta.stringOrNull("text").orEmpty()
                        "thinking_delta" -> reasoning = delta.stringOrNull("thinking").orEmpty()
                        "input_json_delta" -> {
                            val state = anthropicTools.getOrPut(index, ::ToolState)
                            toolCalls.add(
                                AiToolCall(
                                    index = index,
                                    id = state.id,
                                    name = state.name,
                                    arguments = delta.stringOrNull("partial_json").orEmpty(),
                                    argumentsAreDelta = true,
                                    raw = delta.deepCopy(),
                                )
                            )
                        }
                        else -> Unit
                    }
                }
            }
            "message_delta" -> {
                finishReason = root.objectOrNull("delta")?.stringOrNull("stop_reason")
                usage = AiProtocolJson.anthropicUsage(root.objectOrNull("usage"))
            }
            "message_stop" -> {
                done = true
            }
            // ping, content_block_stop, signature_delta, and future event types intentionally
            // remain raw-only so forward-compatible callers can still inspect them.
        }
        return AiStreamChunk(
            text = text,
            reasoning = reasoning,
            toolCalls = toolCalls,
            usage = usage,
            raw = root.deepCopy(),
            finishReason = finishReason,
            done = done,
        )
    }

    private fun parseGemini(root: JsonObject): AiStreamChunk {
        AiProtocolJson.errorOf(root)?.let {
            return AiStreamChunk(
                raw = root.deepCopy(),
                done = true,
                error = it,
            )
        }
        val extracted = AiProtocolJson.extractGemini(root)
        return AiStreamChunk(
            text = extracted.text,
            reasoning = extracted.reasoning,
            toolCalls = extracted.toolCalls,
            usage = AiProtocolJson.geminiUsage(root.objectOrNull("usageMetadata")),
            raw = root.deepCopy(),
            finishReason = extracted.finishReason,
            done = extracted.finishReason != null,
        )
    }

    private fun parseOpenAiToolDeltas(delta: JsonObject?): List<AiToolCall> {
        val array = delta?.arrayOrNull("tool_calls") ?: return emptyList()
        return array.mapIndexedNotNull { fallbackIndex, element ->
            val toolCall = element.takeIf(JsonElement::isJsonObject)?.asJsonObject
                ?: return@mapIndexedNotNull null
            val index = toolCall.intOrNull("index") ?: fallbackIndex
            val state = openAiTools.getOrPut(index, ::ToolState)
            state.id = toolCall.stringOrNull("id") ?: state.id
            val function = toolCall.objectOrNull("function")
            state.name = function?.stringOrNull("name")
                ?: toolCall.stringOrNull("name")
                ?: state.name
            AiToolCall(
                index = index,
                id = state.id,
                name = state.name,
                arguments = function?.get("arguments").jsonStringOrNull()
                    ?: toolCall.get("arguments").jsonStringOrNull(),
                argumentsAreDelta = true,
                raw = toolCall.deepCopy(),
            )
        }
    }

    private fun appendOpenAiReasoning(target: StringBuilder, delta: JsonObject) {
        listOf("reasoning_content", "reasoning", "analysis").firstNotNullOfOrNull { key ->
            delta.get(key).textValue()
        }?.let(target::append)
        delta.arrayOrNull("reasoning_details")?.forEach { detailElement ->
            detailElement.takeIf(JsonElement::isJsonObject)
                ?.asJsonObject
                ?.stringOrNull("text")
                ?.let(target::append)
        }
    }

    private fun JsonElement?.appendTextContent(target: StringBuilder) {
        val content = this ?: return
        when {
            content.isJsonPrimitive && content.asJsonPrimitive.isString -> target.append(content.asString)
            content.isJsonArray -> content.asJsonArray.forEach { partElement ->
                partElement.takeIf(JsonElement::isJsonObject)?.asJsonObject?.let { part ->
                    when (part.stringOrNull("type")) {
                        "refusal" -> {
                            (part.stringOrNull("refusal") ?: part.stringOrNull("text"))
                                ?.let(target::append)
                        }
                        else -> part.stringOrNull("text")?.let(target::append)
                    }
                }
            }
        }
    }

    private fun JsonObject.intOrNull(name: String): Int? {
        val value = get(name) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) return null
        return try {
            value.asInt
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun parseObject(data: String): JsonObject {
        val parsed = try {
            JsonParser.parseString(data)
        } catch (_: RuntimeException) {
            throw AiStreamProtocolException(normalizedProviderId, "event payload is not valid JSON")
        }
        if (!parsed.isJsonObject) {
            throw AiStreamProtocolException(normalizedProviderId, "event payload must be a JSON object")
        }
        return parsed.asJsonObject
    }

    companion object {

        @JvmStatic
        fun parseEvent(providerId: String, event: SseEvent): AiStreamChunk {
            return AiStreamParser(providerId).parse(event)
        }

    }

}
