package org.autojs.plugin.ondeviceai.api

import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiValidation
import java.util.Collections

data class AiToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: AiPayloadReference,
) {
    init {
        require(AiValidation.STABLE_ID.matches(name)) { "Invalid tool name" }
        AiValidation.requireUtf8(name, 1, OnDeviceAiLimits.MAX_TOOL_NAME_BYTES, "Tool name")
        AiValidation.requireUtf8(description, 1, OnDeviceAiLimits.MAX_TOOL_DESCRIPTION_BYTES, "Tool description")
        require(inputSchema.mimeType == OnDeviceAiMimeType.JSON)
        requireInlineTextPayload(inputSchema)
        require(inputSchema.declaredLengthBytes <= OnDeviceAiLimits.MAX_ONE_SCHEMA_BYTES)
    }

    override fun toString(): String =
        "AiToolDefinition(name=$name, descriptionBytes=${AiValidation.utf8Size(description)}, " +
            "inputSchemaMimeType=${inputSchema.mimeType}, " +
            "inputSchemaBytes=${inputSchema.declaredLengthBytes})"
}

data class AiToolCall(
    val callId: String,
    val name: String,
    val arguments: AiPayloadReference,
) {
    init {
        AiValidation.requireUtf8(callId, 1, OnDeviceAiLimits.MAX_TOOL_CALL_ID_BYTES, "Tool call ID")
        require(callId.none { character -> Character.isISOControl(character) })
        require(AiValidation.STABLE_ID.matches(name)) { "Invalid tool name" }
        AiValidation.requireUtf8(name, 1, OnDeviceAiLimits.MAX_TOOL_NAME_BYTES, "Tool name")
        require(arguments.mimeType == OnDeviceAiMimeType.JSON)
        requireInlineTextPayload(arguments)
        require(arguments.declaredLengthBytes <= OnDeviceAiLimits.MAX_TOOL_ARGUMENT_OR_RESULT_BYTES)
    }

    override fun toString(): String =
        "AiToolCall(name=$name, argumentsBytes=${arguments.declaredLengthBytes}, " +
            "argumentsDescriptorBacked=${!arguments.isInline})"
}

class AiToolCallBatch(
    calls: List<AiToolCall>,
) {
    val calls: List<AiToolCall> = immutableListSnapshot(calls)

    init {
        require(calls.isNotEmpty())
        require(calls.size <= OnDeviceAiLimits.MAX_OUTSTANDING_TOOL_CALLS)
        require(calls.map { it.callId }.distinct().size == calls.size) { "Duplicate tool call ID" }
    }

    operator fun component1() = calls

    fun copy(calls: List<AiToolCall> = this.calls) = AiToolCallBatch(calls)

    override fun equals(other: Any?): Boolean =
        this === other || other is AiToolCallBatch && calls == other.calls

    override fun hashCode(): Int = calls.hashCode()

    override fun toString(): String = "AiToolCallBatch(callCount=${calls.size})"
}

data class AiToolResult(
    val callId: String,
    val output: AiPayloadReference,
    val isError: Boolean = false,
) {
    init {
        AiValidation.requireUtf8(callId, 1, OnDeviceAiLimits.MAX_TOOL_CALL_ID_BYTES, "Tool call ID")
        require(callId.none { character -> Character.isISOControl(character) })
        require(output.mimeType in OnDeviceAiMimeType.INPUT_VALUES)
        requireInlineTextPayload(output)
        require(output.declaredLengthBytes <= OnDeviceAiLimits.MAX_TOOL_ARGUMENT_OR_RESULT_BYTES)
    }

    override fun toString(): String =
        "AiToolResult(outputMimeType=${output.mimeType}, outputBytes=${output.declaredLengthBytes}, " +
            "outputDescriptorBacked=${!output.isInline}, isError=$isError)"
}

class AiToolResultBatch(
    results: List<AiToolResult>,
) {
    val results: List<AiToolResult> = immutableListSnapshot(results)

    init {
        require(results.isNotEmpty())
        require(results.size <= OnDeviceAiLimits.MAX_OUTSTANDING_TOOL_CALLS)
        require(results.map { it.callId }.distinct().size == results.size) { "Duplicate tool result ID" }
    }

    operator fun component1() = results

    fun copy(results: List<AiToolResult> = this.results) = AiToolResultBatch(results)

    override fun equals(other: Any?): Boolean =
        this === other || other is AiToolResultBatch && results == other.results

    override fun hashCode(): Int = results.hashCode()

    override fun toString(): String = "AiToolResultBatch(resultCount=${results.size})"
}

private fun <T> immutableListSnapshot(values: List<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))
