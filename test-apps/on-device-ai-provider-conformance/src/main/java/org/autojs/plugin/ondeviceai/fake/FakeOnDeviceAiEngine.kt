package org.autojs.plugin.ondeviceai.fake

import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiUsage
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ondeviceai.api.OnDeviceAiMimeType
import org.autojs.plugin.ondeviceai.api.OnDeviceAiRequest
import org.autojs.plugin.ondeviceai.api.AiToolCall
import org.autojs.plugin.ondeviceai.api.AiToolCallBatch

data class FakeAiChunkPlan(
    val textDelta: String = "",
    val reasoningDelta: String = "",
)

data class FakeAiGenerationPlan(
    val output: String,
    val outputPayload: AiPayloadReference,
    val chunks: List<FakeAiChunkPlan>,
    val reasoningSummary: AiPayloadReference?,
    val usage: AiUsage,
)

object FakeOnDeviceAiEngine {
    const val TOOL_CALL_ID = "fake-call-1"

    fun createToolCalls(request: OnDeviceAiRequest): AiToolCallBatch {
        val tool = request.tools.firstOrNull() ?: throw IllegalArgumentException("Fake tools model requires a tool")
        return AiToolCallBatch(
            listOf(
                AiToolCall(
                    callId = TOOL_CALL_ID,
                    name = tool.name,
                    arguments = jsonPayload("{\"fixture\":true}"),
                ),
            ),
        )
    }

    fun plan(
        request: OnDeviceAiRequest,
        materialized: FakeMaterializedRequest,
        toolResult: String? = null,
    ): FakeAiGenerationPlan {
        val raw = if (request.modelId == FakeOnDeviceAiFixture.MODEL_TOOLS) {
            "tool:${requireNotNull(toolResult) { "Fake tool result is unavailable" }}"
        } else {
            materialized.prompt.ifEmpty { "fake" }
        }
        val requestedOutputBytes = request.options.maximumOutputBytes.toInt()
        val responseMinimumBytes = if (request.options.responseMimeType == OnDeviceAiMimeType.JSON) 1 else 0
        val reasoningBudget = (requestedOutputBytes - responseMinimumBytes).coerceAtLeast(0)
        val reasoningDelta = if (request.options.includeReasoning) {
            truncateUtf8(REASONING_DELTA, reasoningBudget)
        } else {
            ""
        }
        val reasoningSummaryText = if (request.options.includeReasoning) {
            truncateUtf8(REASONING_SUMMARY, reasoningBudget)
        } else {
            ""
        }
        val reservedReasoningBytes = maxOf(
            AiValidation.utf8Size(reasoningDelta),
            AiValidation.utf8Size(reasoningSummaryText),
        )
        val byteOutputLimit = requestedOutputBytes - reservedReasoningBytes
        val tokenOutputLimit = request.options.maximumOutputTokens?.let { maximumTokens ->
            if (maximumTokens > Long.MAX_VALUE / APPROXIMATE_BYTES_PER_TOKEN) {
                Long.MAX_VALUE
            } else {
                maximumTokens * APPROXIMATE_BYTES_PER_TOKEN
            }
        } ?: Long.MAX_VALUE
        val visibleOutputLimit = minOf(byteOutputLimit.toLong(), tokenOutputLimit).toInt()
        val output = if (request.options.responseMimeType == OnDeviceAiMimeType.JSON) {
            boundedJson(raw, visibleOutputLimit)
        } else {
            truncateUtf8(raw, visibleOutputLimit)
        }
        val outputPayload = if (request.options.responseMimeType == OnDeviceAiMimeType.JSON) {
            jsonPayload(output)
        } else {
            textPayload(output)
        }
        val chunks = if (request.options.stream) {
            buildList {
                if (reasoningDelta.isNotEmpty()) {
                    add(FakeAiChunkPlan(reasoningDelta = reasoningDelta))
                }
                splitUtf8(output, CHUNK_BYTES).forEach { add(FakeAiChunkPlan(textDelta = it)) }
            }
        } else {
            emptyList()
        }
        val reasoningSummary = if (reasoningSummaryText.isNotEmpty()) {
            textPayload(reasoningSummaryText)
        } else {
            null
        }
        val inputTokens = estimateTokens(materialized.inputBytes)
        val outputTokens = estimateTokens(AiValidation.utf8Size(output).toLong())
        val reasoningTokens = if (reasoningDelta.isNotEmpty()) 2L else 0L
        return FakeAiGenerationPlan(
            output = output,
            outputPayload = outputPayload,
            chunks = chunks,
            reasoningSummary = reasoningSummary,
            usage = AiUsage(
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                totalTokens = inputTokens + outputTokens,
                reasoningTokens = reasoningTokens,
            ),
        )
    }

    fun textPayload(text: String): AiPayloadReference {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return AiPayloadReference(
            mimeType = OnDeviceAiMimeType.PLAIN,
            declaredLengthBytes = bytes.size.toLong(),
            inlineBytes = bytes,
            charset = "utf-8",
        )
    }

    fun jsonPayload(json: String): AiPayloadReference {
        val bytes = json.toByteArray(Charsets.UTF_8)
        return AiPayloadReference(
            mimeType = OnDeviceAiMimeType.JSON,
            declaredLengthBytes = bytes.size.toLong(),
            inlineBytes = bytes,
        )
    }

    private fun boundedJson(value: String, maximumBytes: Int): String {
        require(maximumBytes > 0)
        val candidate = "{\"echo\":${quoteJson(value)}}"
        if (AiValidation.utf8Size(candidate) <= maximumBytes) return candidate
        return if (maximumBytes == 1) "0" else "{}"
    }

    private fun quoteJson(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private fun truncateUtf8(value: String, maximumBytes: Int): String {
        require(maximumBytes >= 0)
        var index = 0
        var usedBytes = 0
        while (index < value.length) {
            val codePoint = Character.codePointAt(value, index)
            val text = String(Character.toChars(codePoint))
            val bytes = AiValidation.utf8Size(text)
            if (usedBytes + bytes > maximumBytes) break
            usedBytes += bytes
            index += Character.charCount(codePoint)
        }
        return value.substring(0, index)
    }

    private fun splitUtf8(value: String, maximumChunkBytes: Int): List<String> {
        if (value.isEmpty()) return emptyList()
        val chunks = mutableListOf<String>()
        var start = 0
        var index = 0
        var usedBytes = 0
        while (index < value.length) {
            val codePoint = Character.codePointAt(value, index)
            val text = String(Character.toChars(codePoint))
            val bytes = AiValidation.utf8Size(text)
            if (usedBytes > 0 && usedBytes + bytes > maximumChunkBytes) {
                chunks += value.substring(start, index)
                start = index
                usedBytes = 0
            }
            usedBytes += bytes
            index += Character.charCount(codePoint)
        }
        if (start < value.length) chunks += value.substring(start)
        return chunks
    }

    private fun estimateTokens(bytes: Long): Long = (bytes + APPROXIMATE_BYTES_PER_TOKEN - 1L) /
        APPROXIMATE_BYTES_PER_TOKEN

    private const val CHUNK_BYTES = 12
    private const val APPROXIMATE_BYTES_PER_TOKEN = 4L
    private const val REASONING_DELTA = "deterministic reasoning"
    private const val REASONING_SUMMARY = "Deterministic fake reasoning"
}
