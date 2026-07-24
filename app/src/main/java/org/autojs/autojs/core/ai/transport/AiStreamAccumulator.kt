package org.autojs.autojs.core.ai.transport

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.TreeMap

/**
 * Accumulates normalized stream chunks into a completed response and a provider-native assistant
 * message suitable for a same-provider tool continuation.
 *
 * Instances are request-scoped and not thread-safe. Every chunk, including otherwise raw-only
 * signature events, must be appended in wire order.
 */
class AiStreamAccumulator(providerId: String) {

    private data class MutableToolCall(
        var index: Int?,
        var id: String?,
        var name: String?,
        val arguments: StringBuilder = StringBuilder(),
        var hasDelta: Boolean = false,
        var raw: JsonElement = JsonNull.INSTANCE,
    )

    private val normalizedProviderId = AiProviderIds.normalize(providerId)
    private val protocol = AiProviderIds.protocolOf(normalizedProviderId)
    private val text = StringBuilder()
    private val reasoning = StringBuilder()
    private val toolCalls = linkedMapOf<String, MutableToolCall>()
    private val openAiMessageExtras = JsonObject()
    private val anthropicBlocks = TreeMap<Int, JsonObject>()
    private val anthropicInputDeltas = mutableMapOf<Int, StringBuilder>()
    private val geminiParts = JsonArray()

    private var geminiRole = "model"
    private var usage: AiUsage? = null
    private var finishReason: String? = null
    private var error: AiProviderError? = null
    private var raw: JsonElement = JsonNull.INSTANCE

    fun append(chunk: AiStreamChunk): AiStreamAccumulator {
        text.append(chunk.text)
        reasoning.append(chunk.reasoning)
        chunk.toolCalls.forEach(::appendToolCall)
        chunk.usage?.let { usage = mergeUsage(usage, it) }
        chunk.finishReason?.let { finishReason = it }
        chunk.error?.let { error = it }
        raw = chunk.raw.safeDeepCopy()
        when (protocol) {
            AiProtocol.OPENAI_COMPATIBLE -> appendOpenAiRaw(chunk.raw)
            AiProtocol.ANTHROPIC_MESSAGES -> appendAnthropicRaw(chunk.raw)
            AiProtocol.GEMINI_GENERATE_CONTENT -> appendGeminiRaw(chunk.raw)
        }
        return this
    }

    private fun mergeUsage(previous: AiUsage?, next: AiUsage): AiUsage {
        if (previous == null) return next
        val inputTokens = next.inputTokens ?: previous.inputTokens
        val outputTokens = next.outputTokens ?: previous.outputTokens
        val totalTokens = when (protocol) {
            AiProtocol.ANTHROPIC_MESSAGES -> sumOrNull(inputTokens, outputTokens)
            else -> next.totalTokens
                ?: previous.totalTokens
                ?: sumOrNull(inputTokens, outputTokens)
        }
        return AiUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
            reasoningTokens = next.reasoningTokens ?: previous.reasoningTokens,
            cachedInputTokens = next.cachedInputTokens ?: previous.cachedInputTokens,
            raw = mergeUsageRaw(previous.raw, next.raw),
        )
    }

    private fun mergeUsageRaw(previous: JsonObject?, next: JsonObject?): JsonObject? {
        if (previous == null) return next?.deepCopy()
        if (next == null) return previous.deepCopy()
        return previous.deepCopy().apply {
            next.entrySet().forEach { (name, value) ->
                add(name, value.safeDeepCopy())
            }
        }
    }

    private fun sumOrNull(first: Long?, second: Long?): Long? {
        if (first == null && second == null) return null
        return (first ?: 0L) + (second ?: 0L)
    }

    fun toResponse(): AiResponse {
        val completedToolCalls = completedToolCalls()
        return AiResponse(
            text = text.toString(),
            reasoning = reasoning.toString(),
            toolCalls = completedToolCalls,
            usage = usage,
            message = providerMessage(completedToolCalls),
            raw = raw.safeDeepCopy(),
            finishReason = finishReason,
            error = error,
        )
    }

    private fun providerMessage(completedToolCalls: List<AiToolCall>): JsonObject {
        return when (protocol) {
            AiProtocol.OPENAI_COMPATIBLE -> {
                AiProviderMessages.openAiAssistant(
                    text = text.toString(),
                    reasoning = reasoning.toString(),
                    toolCalls = completedToolCalls,
                ).apply {
                    openAiMessageExtras.entrySet().forEach { (name, value) ->
                        if (!has(name)) add(name, value.safeDeepCopy())
                    }
                }
            }
            AiProtocol.ANTHROPIC_MESSAGES -> anthropicAssistantMessage(completedToolCalls)
            AiProtocol.GEMINI_GENERATE_CONTENT -> geminiAssistantMessage(completedToolCalls)
        }
    }

    private fun anthropicAssistantMessage(completedToolCalls: List<AiToolCall>): JsonObject {
        val content = JsonArray()
        if (anthropicBlocks.isNotEmpty()) {
            anthropicBlocks.values.forEach { block ->
                content.add(finalizeAnthropicBlock(block))
            }
        } else {
            if (text.isNotEmpty()) {
                content.add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", text.toString())
                })
            }
            completedToolCalls.forEach { toolCall ->
                content.add(
                    AiProviderMessages.anthropicToolUse(
                        AiProviderMessages.ToolCallInput(
                            id = toolCall.id,
                            name = toolCall.name,
                            arguments = toolCall.arguments?.let(::parseJsonValue),
                            raw = null,
                        ),
                        toolCall.index ?: content.size(),
                    ),
                )
            }
        }
        return JsonObject().apply {
            addProperty("role", "assistant")
            add("content", content)
        }
    }

    private fun geminiAssistantMessage(completedToolCalls: List<AiToolCall>): JsonObject {
        val parts = geminiParts.deepCopy()
        if (parts.size() == 0) {
            if (text.isNotEmpty()) {
                parts.add(JsonObject().apply { addProperty("text", text.toString()) })
            }
            completedToolCalls.forEachIndexed { index, toolCall ->
                parts.add(
                    AiProviderMessages.geminiFunctionCallPart(
                        AiProviderMessages.ToolCallInput(
                            id = toolCall.id,
                            name = toolCall.name,
                            arguments = toolCall.arguments?.let(::parseJsonValue),
                            raw = toolCall.raw.takeIf(JsonElement::isJsonObject)?.asJsonObject,
                        ),
                        index,
                    ),
                )
            }
        }
        return JsonObject().apply {
            addProperty("role", geminiRole)
            add("parts", parts)
        }
    }

    private fun appendToolCall(toolCall: AiToolCall) {
        val key = toolCall.id?.let { "id:$it" }
            ?: toolCall.index?.let { "index:$it:${toolCall.name.orEmpty()}" }
            ?: "tool:${toolCalls.size}"
        val target = toolCalls.getOrPut(key) {
            MutableToolCall(
                index = toolCall.index,
                id = toolCall.id,
                name = toolCall.name,
            )
        }
        target.index = toolCall.index ?: target.index
        target.id = toolCall.id ?: target.id
        target.name = toolCall.name ?: target.name
        toolCall.arguments?.let { arguments ->
            if (toolCall.argumentsAreDelta) {
                if (!target.hasDelta) {
                    target.arguments.setLength(0)
                    target.hasDelta = true
                }
                target.arguments.append(arguments)
            } else {
                target.arguments.setLength(0)
                target.arguments.append(arguments)
                target.hasDelta = false
            }
        }
        target.raw = toolCall.raw.safeDeepCopy()
    }

    private fun completedToolCalls(): List<AiToolCall> {
        return toolCalls.values.map { toolCall ->
            AiToolCall(
                index = toolCall.index,
                id = toolCall.id,
                name = toolCall.name,
                arguments = toolCall.arguments.toString().takeIf(String::isNotEmpty),
                raw = toolCall.raw.safeDeepCopy(),
            )
        }
    }

    private fun appendOpenAiRaw(value: JsonElement) {
        val root = value.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return
        val message = root.arrayOrNull("choices")
            ?.firstOrNull()
            ?.takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
            ?.let { choice -> choice.objectOrNull("delta") ?: choice.objectOrNull("message") }
            ?: return
        message.entrySet().forEach { (name, fieldValue) ->
            when (name) {
                "role", "content", "tool_calls", "reasoning_content", "reasoning", "analysis" -> Unit
                "reasoning_details" -> {
                    val target = openAiMessageExtras.arrayOrNull(name) ?: JsonArray().also {
                        openAiMessageExtras.add(name, it)
                    }
                    if (fieldValue.isJsonArray) {
                        fieldValue.asJsonArray.forEach { target.add(it.safeDeepCopy()) }
                    }
                }
                else -> openAiMessageExtras.add(name, fieldValue.safeDeepCopy())
            }
        }
    }

    private fun appendAnthropicRaw(value: JsonElement) {
        val root = value.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return
        when (root.stringOrNull("type")) {
            "message_start" -> {
                root.objectOrNull("message")
                    ?.arrayOrNull("content")
                    ?.forEachIndexed { index, block ->
                        block.takeIf(JsonElement::isJsonObject)?.asJsonObject?.let {
                            anthropicBlocks[index] = it.deepCopy()
                        }
                    }
            }
            "content_block_start" -> {
                val index = root.integerOrNull("index") ?: return
                val block = root.objectOrNull("content_block") ?: return
                anthropicBlocks[index] = block.deepCopy()
                if (block.stringOrNull("type") == "tool_use") {
                    anthropicInputDeltas[index] = StringBuilder()
                }
            }
            "content_block_delta" -> {
                val index = root.integerOrNull("index") ?: return
                val delta = root.objectOrNull("delta") ?: return
                val block = anthropicBlocks.getOrPut(index) {
                    JsonObject().apply {
                        when (delta.stringOrNull("type")) {
                            "text_delta" -> addProperty("type", "text")
                            "thinking_delta", "signature_delta" -> addProperty("type", "thinking")
                            "input_json_delta" -> addProperty("type", "tool_use")
                        }
                    }
                }
                when (delta.stringOrNull("type")) {
                    "text_delta" -> appendStringField(block, "text", delta.stringOrNull("text"))
                    "thinking_delta" -> appendStringField(block, "thinking", delta.stringOrNull("thinking"))
                    "signature_delta" -> appendStringField(block, "signature", delta.stringOrNull("signature"))
                    "input_json_delta" -> {
                        anthropicInputDeltas.getOrPut(index, ::StringBuilder)
                            .append(delta.stringOrNull("partial_json").orEmpty())
                    }
                }
            }
            "content_block_stop" -> {
                root.integerOrNull("index")?.let(::finalizeAnthropicInput)
            }
            "message_stop" -> anthropicInputDeltas.keys.toList().forEach(::finalizeAnthropicInput)
        }
    }

    private fun finalizeAnthropicBlock(block: JsonObject): JsonObject {
        val index = anthropicBlocks.entries.firstOrNull { it.value === block }?.key
        if (index != null) finalizeAnthropicInput(index)
        return block.deepCopy()
    }

    private fun finalizeAnthropicInput(index: Int) {
        val partialJson = anthropicInputDeltas.remove(index)?.toString().orEmpty()
        if (partialJson.isEmpty()) return
        val parsed = parseJsonValue(partialJson)
        if (!parsed.isJsonObject) {
            throw AiStreamProtocolException(
                normalizedProviderId,
                "tool input at index $index must be a JSON object",
            )
        }
        anthropicBlocks[index]?.add("input", parsed)
    }

    private fun appendGeminiRaw(value: JsonElement) {
        val root = value.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return
        val candidate = root.arrayOrNull("candidates")
            ?.firstOrNull()
            ?.takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
            ?: return
        candidate.objectOrNull("content")?.let { content ->
            content.stringOrNull("role")?.let { geminiRole = it }
            content.arrayOrNull("parts")?.forEach { part ->
                geminiParts.add(part.safeDeepCopy())
            }
        }
    }

    private fun appendStringField(target: JsonObject, name: String, value: String?) {
        if (value == null) return
        target.addProperty(name, target.stringOrNull(name).orEmpty() + value)
    }

    private fun parseJsonValue(value: String): JsonElement {
        return try {
            JsonParser.parseString(value)
        } catch (_: RuntimeException) {
            throw AiStreamProtocolException(normalizedProviderId, "tool arguments are not valid JSON")
        }
    }

    private fun JsonObject.integerOrNull(name: String): Int? {
        val value = get(name) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) return null
        return try {
            value.asInt
        } catch (_: RuntimeException) {
            null
        }
    }
}
