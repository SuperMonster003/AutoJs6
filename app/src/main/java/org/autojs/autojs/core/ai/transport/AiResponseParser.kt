package org.autojs.autojs.core.ai.transport

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

object AiResponseParser {

    @JvmStatic
    fun parse(providerId: String, raw: JsonElement): AiResponse {
        val protocol = AiProviderIds.protocolOf(providerId)
        val root = raw.takeIf(JsonElement::isJsonObject)?.asJsonObject
            ?: throw IllegalArgumentException("AI response must be a JSON object")
        return when (protocol) {
            AiProtocol.OPENAI_COMPATIBLE -> parseOpenAi(root)
            AiProtocol.ANTHROPIC_MESSAGES -> parseAnthropic(root)
            AiProtocol.GEMINI_GENERATE_CONTENT -> parseGemini(root)
        }
    }

    private fun parseOpenAi(root: JsonObject): AiResponse {
        AiProtocolJson.errorOf(root)?.let {
            return AiResponse(raw = root.deepCopy(), error = it)
        }

        val choices = root.arrayOrNull("choices")
        if (choices != null) {
            val choice = choices.firstOrNull()
                ?.takeIf(JsonElement::isJsonObject)
                ?.asJsonObject
                ?: throw AiResponseProtocolException(
                    "OpenAI response choices must contain a JSON object",
                )
            val text = StringBuilder()
            val reasoning = StringBuilder()
            val toolCalls = mutableListOf<AiToolCall>()
            val message = choice.objectOrNull("message") ?: choice.objectOrNull("delta")
            val providerMessage = if (message != null) {
                message.deepCopy().apply {
                    if (!has("role")) addProperty("role", "assistant")
                }
            } else {
                null
            }
            if (message != null) {
                appendContentText(text, message.get("content"))
                appendStandaloneRefusal(text, message)
                appendOpenAiReasoning(reasoning, message)
                parseOpenAiToolCalls(message.arrayOrNull("tool_calls"), 0).let(toolCalls::addAll)
            } else {
                choice.get("text").textValue()?.let(text::append)
            }
            return AiResponse(
                text = text.toString(),
                reasoning = reasoning.toString(),
                toolCalls = toolCalls,
                usage = AiProtocolJson.openAiUsage(root.objectOrNull("usage")),
                message = providerMessage
                    ?: AiProviderMessages.openAiAssistant(
                        text = text.toString(),
                        reasoning = reasoning.toString(),
                        toolCalls = toolCalls,
                    ),
                raw = root.deepCopy(),
                finishReason = choice.stringOrNull("finish_reason"),
            )
        }

        // OpenAI Responses API shape. Supporting it here also benefits compatible gateways that
        // expose /responses-shaped JSON behind a custom endpoint.
        val output = root.arrayOrNull("output")
        if (output != null) {
            if (output.size() == 0) {
                throw AiResponseProtocolException(
                    "OpenAI response output array must not be empty",
                )
            }
            val text = StringBuilder()
            val reasoning = StringBuilder()
            val toolCalls = mutableListOf<AiToolCall>()
            output.forEachIndexed { index, itemElement ->
                val item = itemElement.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@forEachIndexed
                when (item.stringOrNull("type")) {
                    "message" -> item.arrayOrNull("content")?.forEach { partElement ->
                        val part = partElement.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@forEach
                        when (part.stringOrNull("type")) {
                            "output_text", "text" -> part.stringOrNull("text")?.let(text::append)
                            "refusal" -> {
                                (part.stringOrNull("refusal") ?: part.stringOrNull("text"))
                                    ?.let(text::append)
                            }
                            "reasoning_text" -> part.stringOrNull("text")?.let(reasoning::append)
                        }
                    }
                    "reasoning" -> {
                        item.arrayOrNull("summary")?.forEach { summaryElement ->
                            summaryElement.takeIf(JsonElement::isJsonObject)
                                ?.asJsonObject
                                ?.stringOrNull("text")
                                ?.let(reasoning::append)
                        }
                    }
                    "function_call" -> toolCalls.add(
                        AiToolCall(
                            index = index,
                            id = item.stringOrNull("call_id") ?: item.stringOrNull("id"),
                            name = item.stringOrNull("name"),
                            arguments = item.get("arguments").jsonStringOrNull(),
                            raw = item.deepCopy(),
                        )
                    )
                }
            }
            return AiResponse(
                text = text.toString(),
                reasoning = reasoning.toString(),
                toolCalls = toolCalls,
                usage = AiProtocolJson.openAiUsage(root.objectOrNull("usage")),
                message = AiProviderMessages.openAiAssistant(
                    text = text.toString(),
                    reasoning = reasoning.toString(),
                    toolCalls = toolCalls,
                ),
                raw = root.deepCopy(),
                finishReason = root.stringOrNull("status"),
            )
        }

        throw AiResponseProtocolException(
            "OpenAI response must contain a choices or output array",
        )
    }

    private fun parseAnthropic(root: JsonObject): AiResponse {
        AiProtocolJson.errorOf(root)?.let {
            return AiResponse(raw = root.deepCopy(), error = it)
        }
        val text = StringBuilder()
        val reasoning = StringBuilder()
        val toolCalls = mutableListOf<AiToolCall>()
        val content = root.arrayOrNull("content")
            ?: throw AiResponseProtocolException(
                "Anthropic response must contain a content array",
            )
        content.forEachIndexed { index, element ->
            val block = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@forEachIndexed
            when (block.stringOrNull("type")) {
                "text" -> block.stringOrNull("text")?.let(text::append)
                "thinking" -> block.stringOrNull("thinking")?.let(reasoning::append)
                "tool_use" -> toolCalls.add(
                    AiToolCall(
                        index = index,
                        id = block.stringOrNull("id"),
                        name = block.stringOrNull("name"),
                        arguments = block.get("input").jsonStringOrNull(),
                        raw = block.deepCopy(),
                    )
                )
            }
        }
        return AiResponse(
            text = text.toString(),
            reasoning = reasoning.toString(),
            toolCalls = toolCalls,
            usage = AiProtocolJson.anthropicUsage(root.objectOrNull("usage")),
            message = JsonObject().apply {
                addProperty("role", "assistant")
                add("content", content.deepCopy())
            },
            raw = root.deepCopy(),
            finishReason = root.stringOrNull("stop_reason"),
        )
    }

    private fun parseGemini(root: JsonObject): AiResponse {
        AiProtocolJson.errorOf(root)?.let {
            return AiResponse(raw = root.deepCopy(), error = it)
        }
        val candidates = root.arrayOrNull("candidates")
        if (candidates == null || candidates.size() == 0) {
            throw geminiShapeException(
                root,
                "Gemini response must contain a candidates array",
            )
        }
        val firstCandidate = candidates.first()
            .takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
            ?: throw AiResponseProtocolException(
                "Gemini response candidates must contain a JSON object",
            )
        val firstContent = firstCandidate.objectOrNull("content")
            ?: throw geminiShapeException(
                root,
                "Gemini response candidate must contain content",
            )
        val extracted = AiProtocolJson.extractGemini(root)
        val providerMessage = firstContent.deepCopy()
            .apply {
                if (!has("role")) addProperty("role", "model")
            }
        return AiResponse(
            text = extracted.text,
            reasoning = extracted.reasoning,
            toolCalls = extracted.toolCalls,
            usage = AiProtocolJson.geminiUsage(root.objectOrNull("usageMetadata")),
            message = providerMessage,
            raw = root.deepCopy(),
            finishReason = extracted.finishReason,
        )
    }

    private fun appendContentText(target: StringBuilder, content: JsonElement?) {
        when {
            content == null || content.isJsonNull -> Unit
            content.isJsonPrimitive && content.asJsonPrimitive.isString -> target.append(content.asString)
            content.isJsonArray -> content.asJsonArray.forEach { partElement ->
                val part = partElement.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@forEach
                when (part.stringOrNull("type")) {
                    "text", "output_text" -> part.stringOrNull("text")?.let(target::append)
                    "refusal" -> {
                        (part.stringOrNull("refusal") ?: part.stringOrNull("text"))
                            ?.let(target::append)
                    }
                }
            }
        }
    }

    private fun appendStandaloneRefusal(target: StringBuilder, message: JsonObject) {
        val hasRefusalPart = message.arrayOrNull("content")
            ?.any { element ->
                element.takeIf(JsonElement::isJsonObject)
                    ?.asJsonObject
                    ?.stringOrNull("type") == "refusal"
            } == true
        if (!hasRefusalPart) {
            message.get("refusal").textValue()?.let(target::append)
        }
    }

    private fun geminiShapeException(
        root: JsonObject,
        fallbackDetail: String,
    ): AiResponseProtocolException {
        val blockReason = root.objectOrNull("promptFeedback")
            ?.stringOrNull("blockReason")
            ?.uppercase()
            ?.takeIf(KNOWN_GEMINI_BLOCK_REASONS::contains)
        return AiResponseProtocolException(
            if (blockReason == null) {
                fallbackDetail
            } else {
                "Gemini response was blocked ($blockReason)"
            },
        )
    }

    private fun appendOpenAiReasoning(target: StringBuilder, message: JsonObject) {
        listOf("reasoning_content", "reasoning", "analysis").firstNotNullOfOrNull { key ->
            message.get(key).textValue()
        }?.let(target::append)
        message.arrayOrNull("reasoning_details")?.forEach { detailElement ->
            detailElement.takeIf(JsonElement::isJsonObject)
                ?.asJsonObject
                ?.stringOrNull("text")
                ?.let(target::append)
        }
    }

    private fun parseOpenAiToolCalls(toolCallArray: JsonArray?, choiceIndex: Int): List<AiToolCall> {
        if (toolCallArray == null) return emptyList()
        return toolCallArray.mapIndexedNotNull { arrayIndex, element ->
            val toolCall = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapIndexedNotNull null
            val function = toolCall.objectOrNull("function")
            AiToolCall(
                index = toolCall.get("index")?.let { value ->
                    try {
                        value.asInt
                    } catch (_: RuntimeException) {
                        null
                    }
                } ?: arrayIndex,
                id = toolCall.stringOrNull("id"),
                name = function?.stringOrNull("name") ?: toolCall.stringOrNull("name"),
                arguments = function?.get("arguments").jsonStringOrNull()
                    ?: toolCall.get("arguments").jsonStringOrNull(),
                raw = JsonObject().apply {
                    addProperty("choiceIndex", choiceIndex)
                    add("toolCall", toolCall.deepCopy())
                },
            )
        }
    }

    private val KNOWN_GEMINI_BLOCK_REASONS = setOf(
        "BLOCKLIST",
        "BLOCK_REASON_UNSPECIFIED",
        "IMAGE_SAFETY",
        "MODEL_ARMOR",
        "OTHER",
        "PROHIBITED_CONTENT",
        "SAFETY",
    )

}

internal object AiProtocolJson {

    data class GeminiExtraction(
        val text: String,
        val reasoning: String,
        val toolCalls: List<AiToolCall>,
        val finishReason: String?,
    )

    fun errorOf(root: JsonObject): AiProviderError? {
        val error = root.objectOrNull("error") ?: return null
        return AiProviderError(
            type = error.stringOrNull("type") ?: error.stringOrNull("status"),
            code = error.get("code")?.let {
                when {
                    it.isJsonPrimitive -> it.asString
                    else -> it.toString()
                }
            },
            message = error.stringOrNull("message"),
        )
    }

    fun openAiUsage(usage: JsonObject?): AiUsage? {
        usage ?: return null
        val input = usage.longOrNull("prompt_tokens") ?: usage.longOrNull("input_tokens")
        val output = usage.longOrNull("completion_tokens") ?: usage.longOrNull("output_tokens")
        val total = usage.longOrNull("total_tokens") ?: sumOrNull(input, output)
        val completionDetails = usage.objectOrNull("completion_tokens_details")
            ?: usage.objectOrNull("output_tokens_details")
        val promptDetails = usage.objectOrNull("prompt_tokens_details")
            ?: usage.objectOrNull("input_tokens_details")
        return AiUsage(
            inputTokens = input,
            outputTokens = output,
            totalTokens = total,
            reasoningTokens = completionDetails?.longOrNull("reasoning_tokens"),
            cachedInputTokens = promptDetails?.longOrNull("cached_tokens"),
            raw = usage.deepCopy(),
        )
    }

    fun anthropicUsage(usage: JsonObject?): AiUsage? {
        usage ?: return null
        val input = usage.longOrNull("input_tokens")
        val output = usage.longOrNull("output_tokens")
        val cacheRead = usage.longOrNull("cache_read_input_tokens")
        val cacheCreation = usage.longOrNull("cache_creation_input_tokens")
        return AiUsage(
            inputTokens = input,
            outputTokens = output,
            totalTokens = sumOrNull(input, output),
            cachedInputTokens = sumOrNull(cacheRead, cacheCreation),
            raw = usage.deepCopy(),
        )
    }

    fun geminiUsage(usage: JsonObject?): AiUsage? {
        usage ?: return null
        val input = usage.longOrNull("promptTokenCount")
        val output = usage.longOrNull("candidatesTokenCount")
        return AiUsage(
            inputTokens = input,
            outputTokens = output,
            totalTokens = usage.longOrNull("totalTokenCount") ?: sumOrNull(input, output),
            cachedInputTokens = usage.longOrNull("cachedContentTokenCount"),
            raw = usage.deepCopy(),
        )
    }

    fun extractGemini(root: JsonObject): GeminiExtraction {
        val text = StringBuilder()
        val reasoning = StringBuilder()
        val toolCalls = mutableListOf<AiToolCall>()
        val candidate = root.arrayOrNull("candidates")
            ?.firstOrNull()
            ?.takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
        candidate?.objectOrNull("content")
            ?.arrayOrNull("parts")
            ?.forEachIndexed { index, partElement ->
                val part = partElement.takeIf(JsonElement::isJsonObject)?.asJsonObject
                    ?: return@forEachIndexed
                val partText = part.stringOrNull("text")
                if (partText != null) {
                    if (part.booleanOrNull("thought") == true) {
                        reasoning.append(partText)
                    } else {
                        text.append(partText)
                    }
                }
                val functionCall = part.objectOrNull("functionCall")
                    ?: part.objectOrNull("function_call")
                if (functionCall != null) {
                    toolCalls.add(
                        AiToolCall(
                            index = index,
                            id = functionCall.stringOrNull("id"),
                            name = functionCall.stringOrNull("name"),
                            arguments = functionCall.get("args").jsonStringOrNull()
                                ?: functionCall.get("arguments").jsonStringOrNull(),
                            raw = part.deepCopy(),
                        )
                    )
                }
            }
        return GeminiExtraction(
            text = text.toString(),
            reasoning = reasoning.toString(),
            toolCalls = toolCalls,
            finishReason = candidate?.stringOrNull("finishReason"),
        )
    }

    private fun sumOrNull(first: Long?, second: Long?): Long? {
        if (first == null && second == null) return null
        return (first ?: 0L) + (second ?: 0L)
    }

}
