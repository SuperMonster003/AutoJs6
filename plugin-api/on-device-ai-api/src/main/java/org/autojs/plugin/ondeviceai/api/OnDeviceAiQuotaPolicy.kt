package org.autojs.plugin.ondeviceai.api

import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiDescriptorReferencePolicy
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiUsage
import org.autojs.plugin.ai.common.api.AiUsagePolicy
import java.security.MessageDigest
import java.util.Collections

data class OnDeviceAiQuotaSnapshot(
    val messageCount: Int,
    val contentPartCount: Int,
    val toolDefinitionCount: Int,
    val inputBytes: Long,
    val toolSchemaBytes: Long,
    val descriptorCount: Int,
    val effectiveMaximumOutputBytes: Long,
    val effectiveMaximumToolRounds: Int,
)

data class OnDeviceAiCompletionQuotaSnapshot(
    val outputBytes: Long,
    val reasoningBytes: Long,
    val totalGeneratedBytes: Long,
    val descriptorCount: Int,
    val finalUsage: AiUsage?,
    val outputText: String,
    val reasoningSummaryText: String?,
) {
    override fun toString(): String =
        "OnDeviceAiCompletionQuotaSnapshot(outputBytes=$outputBytes, reasoningBytes=$reasoningBytes, " +
            "totalGeneratedBytes=$totalGeneratedBytes, descriptorCount=$descriptorCount, " +
            "finalUsagePresent=${finalUsage != null}, " +
            "reasoningSummaryPresent=${reasoningSummaryText != null})"
}

class AiVerifiedToolCallBatchSnapshot(
    val totalBytes: Long,
    val descriptorCount: Int,
    argumentsJsonByCallId: Map<String, String>,
) {
    val argumentsJsonByCallId: Map<String, String> =
        Collections.unmodifiableMap(LinkedHashMap(argumentsJsonByCallId))

    override fun equals(other: Any?): Boolean =
        this === other || other is AiVerifiedToolCallBatchSnapshot &&
            totalBytes == other.totalBytes &&
            descriptorCount == other.descriptorCount &&
            argumentsJsonByCallId == other.argumentsJsonByCallId

    override fun hashCode(): Int {
        var result = totalBytes.hashCode()
        result = 31 * result + descriptorCount
        result = 31 * result + argumentsJsonByCallId.hashCode()
        return result
    }

    override fun toString(): String =
        "AiVerifiedToolCallBatchSnapshot(totalBytes=$totalBytes, descriptorCount=$descriptorCount, " +
            "callCount=${argumentsJsonByCallId.size})"
}

object OnDeviceAiQuotaPolicy {
    fun validateRequest(
        request: OnDeviceAiRequest,
        provider: OnDeviceAiCapabilities,
        descriptorCount: Int,
    ): OnDeviceAiQuotaSnapshot {
        require(request.messages.size <= provider.maximumMessages) { "Message count exceeds provider capability" }
        require(request.messages.size <= OnDeviceAiLimits.MAX_MESSAGES) { "Message count exceeds host quota" }
        val partCount = request.messages.fold(0) { total, message ->
            require(message.parts.size <= OnDeviceAiLimits.MAX_CONTENT_PARTS) { "One message contains too many parts" }
            require(total <= Int.MAX_VALUE - message.parts.size) { "Content part count overflow" }
            total + message.parts.size
        }
        require(partCount <= provider.maximumContentParts) { "Content part count exceeds provider capability" }
        require(partCount <= OnDeviceAiLimits.MAX_CONTENT_PARTS) { "Content part count exceeds host quota" }
        require(request.tools.size <= provider.maximumToolDefinitions) { "Tool count exceeds provider capability" }
        require(request.tools.size <= OnDeviceAiLimits.MAX_TOOL_DEFINITIONS) { "Tool count exceeds host quota" }
        require(request.tools.map { it.name }.distinct().size == request.tools.size) { "Duplicate tool name" }

        val requiredCapabilities = request.options.requiredCapabilityIds.toMutableSet().apply {
            if (request.options.stream) add(OnDeviceAiCapabilityId.STREAMING)
            if (request.options.includeReasoning) add(OnDeviceAiCapabilityId.REASONING)
            if (request.options.structuredJson) add(OnDeviceAiCapabilityId.STRUCTURED_JSON)
            if (request.options.reportUsage) add(OnDeviceAiCapabilityId.USAGE)
            if (request.tools.isNotEmpty() || request.options.maximumToolRounds > 0) add(OnDeviceAiCapabilityId.TOOLS)
        }
        require(requiredCapabilities.all { it in provider.supportedCapabilityIds() }) {
            "Provider does not support every required capability"
        }
        require(request.options.responseMimeType in provider.acceptedTextMimeTypes) {
            "Provider does not accept the requested response MIME type"
        }
        request.messages.flatMap { it.parts }.forEach { part ->
            require(part.payload.mimeType in provider.acceptedTextMimeTypes) {
                "Provider does not accept a message MIME type"
            }
        }
        request.tools.forEach { tool ->
            require(tool.inputSchema.mimeType in provider.acceptedSchemaMimeTypes) {
                "Provider does not accept a tool schema MIME type"
            }
        }
        request.options.responseSchema?.let { schema ->
            require(schema.mimeType in provider.acceptedSchemaMimeTypes) {
                "Provider does not accept the response schema MIME type"
            }
        }

        val inputBytes = request.messages
            .asSequence()
            .flatMap { it.parts.asSequence() }
            .fold(0L) { total, part -> safeAdd(total, part.payload.declaredLengthBytes, "Input byte count") }
        require(inputBytes <= provider.maximumContextBytes) { "Input bytes exceed provider capability" }
        require(inputBytes <= OnDeviceAiLimits.MAX_TOTAL_INPUT_BYTES) { "Input bytes exceed host quota" }

        val schemaPayloads = buildList {
            request.tools.forEach { add(it.inputSchema) }
            request.options.responseSchema?.let(::add)
        }
        val schemaBytes = schemaPayloads.fold(0L) { total, payload ->
            require(payload.declaredLengthBytes <= OnDeviceAiLimits.MAX_ONE_SCHEMA_BYTES) { "One schema exceeds its quota" }
            safeAdd(total, payload.declaredLengthBytes, "Schema byte count")
        }
        require(schemaBytes <= OnDeviceAiLimits.MAX_ALL_TOOL_SCHEMA_BYTES) { "Schema bytes exceed session quota" }

        val descriptorIndices = collectPayloads(request).mapNotNull(AiPayloadReference::descriptorIndex)
        val maximumDescriptors = minOf(
            provider.maximumRequestDescriptors,
            AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST,
        )
        AiDescriptorReferencePolicy.validateExactReferences(
            descriptorIndices = descriptorIndices,
            descriptorCount = descriptorCount,
            maximumDescriptors = maximumDescriptors,
        )

        val hostOutputLimit = if (request.options.stream) {
            OnDeviceAiLimits.MAX_STREAMING_OUTPUT_BYTES
        } else {
            OnDeviceAiLimits.MAX_OUTPUT_BYTES
        }
        val effectiveOutputLimit = minOf(hostOutputLimit, provider.maximumOutputBytes)
        require(request.options.maximumOutputBytes <= effectiveOutputLimit) {
            "Requested output bytes exceed the effective limit"
        }
        val effectiveToolRounds = minOf(OnDeviceAiLimits.MAX_TOOL_ROUNDS, provider.maximumToolRounds)
        require(request.options.maximumToolRounds <= effectiveToolRounds) {
            "Requested tool rounds exceed the effective limit"
        }
        if (request.tools.isEmpty()) {
            require(request.options.maximumToolRounds == 0) { "Tool rounds require tool definitions" }
        }

        return OnDeviceAiQuotaSnapshot(
            messageCount = request.messages.size,
            contentPartCount = partCount,
            toolDefinitionCount = request.tools.size,
            inputBytes = inputBytes,
            toolSchemaBytes = schemaBytes,
            descriptorCount = descriptorCount,
            effectiveMaximumOutputBytes = effectiveOutputLimit,
            effectiveMaximumToolRounds = effectiveToolRounds,
        )
    }

    /**
     * Performs structural descriptor accounting only. It does not bind names to a request or
     * materialize descriptor-backed JSON. Use the request-aware overload at a provider boundary.
     */
    @Deprecated(
        message = "Use request-aware validateToolCalls to bind tool names and verify argument JSON",
    )
    fun validateToolCalls(
        batch: AiToolCallBatch,
        descriptorCount: Int,
        maximumDescriptors: Int,
    ): Long = validateToolPayloads(batch.calls.map { it.arguments }, descriptorCount, maximumDescriptors)

    /**
     * Binds provider tool calls to the original request and returns immutable, fully verified JSON
     * arguments. No descriptor is read until names, declared quotas, and exact descriptor references
     * have passed validation.
     */
    fun validateToolCalls(
        request: OnDeviceAiRequest,
        batch: AiToolCallBatch,
        descriptorCount: Int,
        maximumDescriptors: Int,
        readDescriptor: ((index: Int, declaredLengthBytes: Long) -> ByteArray)? = null,
    ): AiVerifiedToolCallBatchSnapshot {
        requireDeclaredToolCalls(request, batch)
        val totalBytes = validateToolPayloads(
            payloads = batch.calls.map { it.arguments },
            descriptorCount = descriptorCount,
            maximumDescriptors = maximumDescriptors,
        )
        val arguments = LinkedHashMap<String, String>(batch.calls.size)
        batch.calls.forEach { call ->
            arguments[call.callId] = OnDeviceAiPayloadPolicy.materializeAndValidateBounded(
                payload = call.arguments,
                maximumBytes = OnDeviceAiLimits.MAX_TOOL_ARGUMENT_OR_RESULT_BYTES,
                readDescriptor = readDescriptor,
            )
        }
        return AiVerifiedToolCallBatchSnapshot(totalBytes, descriptorCount, arguments)
    }

    fun requireDeclaredToolCalls(request: OnDeviceAiRequest, batch: AiToolCallBatch) {
        val declaredNames = request.tools.asSequence().map(AiToolDefinition::name).toHashSet()
        require(batch.calls.all { call -> call.name in declaredNames }) {
            "Provider requested a tool that was not declared by the original request"
        }
    }

    /** Validates descriptors carried concurrently by this single tool-result method event. */
    fun validateToolResults(
        batch: AiToolResultBatch,
        descriptorCount: Int,
        maximumDescriptors: Int,
    ): Long = validateToolPayloads(batch.results.map { it.output }, descriptorCount, maximumDescriptors)

    /**
     * Validates the terminal payload against the original request and the descriptors delivered by
     * the same Binder callback. The descriptor limit is per event and concurrently open, not a
     * cumulative session count. Callers must close every received descriptor whether validation
     * succeeds or fails. [AiToolTurnPolicy] separately enforces the cumulative tool-data byte quota.
     */
    fun validateCompletion(
        request: OnDeviceAiRequest,
        result: AiCompletionResult,
        descriptorCount: Int,
        maximumDescriptors: Int,
        observedUsage: AiUsage? = null,
        effectiveMaximumOutputBytes: Long = request.options.maximumOutputBytes,
        readDescriptor: ((index: Int, declaredLengthBytes: Long) -> ByteArray)? = null,
    ): OnDeviceAiCompletionQuotaSnapshot = validateCompletionAggregate(
        request = request,
        result = result,
        streamedChunkCount = 0L,
        streamedTextBytes = 0L,
        streamedReasoningBytes = 0L,
        streamedTextSha256 = null,
        descriptorCount = descriptorCount,
        maximumDescriptors = maximumDescriptors,
        observedUsage = observedUsage,
        effectiveMaximumOutputBytes = effectiveMaximumOutputBytes,
        readDescriptor = readDescriptor,
    )

    /**
     * Validates a streaming terminal result using aggregate-output semantics. When at least one
     * chunk was emitted, the terminal output must be the complete concatenated text stream: its
     * length and SHA-256 must match [streamedTextBytes] and [streamedTextSha256]. Text output is
     * therefore charged once, while streamed reasoning deltas and a terminal reasoning summary are
     * separate generated data and are both charged to the same negotiated byte budget.
     */
    fun validateCompletionAggregate(
        request: OnDeviceAiRequest,
        result: AiCompletionResult,
        streamedChunkCount: Long,
        streamedTextBytes: Long,
        streamedReasoningBytes: Long,
        streamedTextSha256: String?,
        descriptorCount: Int,
        maximumDescriptors: Int,
        observedUsage: AiUsage? = null,
        effectiveMaximumOutputBytes: Long = request.options.maximumOutputBytes,
        readDescriptor: ((index: Int, declaredLengthBytes: Long) -> ByteArray)? = null,
    ): OnDeviceAiCompletionQuotaSnapshot {
        require(effectiveMaximumOutputBytes in 1L..request.options.maximumOutputBytes) {
            "Invalid effective completion output limit"
        }
        require(streamedChunkCount >= 0L) { "Invalid streamed chunk count" }
        require(streamedTextBytes in 0L..effectiveMaximumOutputBytes) { "Invalid streamed text byte count" }
        require(streamedReasoningBytes in 0L..effectiveMaximumOutputBytes) {
            "Invalid streamed reasoning byte count"
        }
        if (streamedChunkCount == 0L) {
            require(streamedTextBytes == 0L && streamedReasoningBytes == 0L && streamedTextSha256 == null) {
                "Streaming aggregate metadata requires at least one chunk"
            }
        } else {
            require(request.options.stream) { "Streaming aggregate metadata requires a streaming request" }
            require(streamedTextSha256?.matches(SHA_256) == true) { "Invalid streamed text SHA-256" }
        }
        require(result.output.mimeType == request.options.responseMimeType) {
            "Completion MIME type does not match the request"
        }
        if (!request.options.includeReasoning) {
            require(streamedReasoningBytes == 0L) { "Stream included reasoning that was not requested" }
            require(result.reasoning == null) { "Completion included reasoning that was not requested" }
        }

        val outputBytes = result.output.declaredLengthBytes
        if (streamedChunkCount > 0L) {
            require(outputBytes == streamedTextBytes) {
                "Terminal output length does not match the aggregate streamed text"
            }
        }
        val reasoningSummaryBytes = result.reasoning?.summary?.declaredLengthBytes ?: 0L
        val reasoningBytes = safeAdd(
            streamedReasoningBytes,
            reasoningSummaryBytes,
            "Aggregate reasoning byte count",
        )
        val totalGeneratedBytes = safeAdd(outputBytes, reasoningBytes, "Aggregate completion byte count")
        require(totalGeneratedBytes <= effectiveMaximumOutputBytes) {
            "Aggregate output and reasoning exceed the effective limit"
        }

        val payloads = listOfNotNull(result.output, result.reasoning?.summary)
        AiDescriptorReferencePolicy.validateExactReferences(
            descriptorIndices = payloads.mapNotNull(AiPayloadReference::descriptorIndex),
            descriptorCount = descriptorCount,
            maximumDescriptors = protocolSessionDescriptorLimit(maximumDescriptors),
        )
        val outputText = OnDeviceAiPayloadPolicy.materializeAndValidate(result.output, readDescriptor)
        if (streamedChunkCount > 0L) {
            require(sha256(outputText.toByteArray(Charsets.UTF_8)) == streamedTextSha256) {
                "Terminal output content does not match the aggregate streamed text"
            }
        }
        val reasoningSummaryText = result.reasoning?.summary?.let { summary ->
            OnDeviceAiPayloadPolicy.materializeAndValidate(summary, readDescriptor)
        }

        val finalUsage = result.usage?.let { AiUsagePolicy.mergeMonotonic(observedUsage, it) }
            ?: observedUsage
        if (request.options.reportUsage) {
            require(finalUsage != null) { "Requested usage was not reported" }
        }
        val reasoningTokens = result.reasoning?.reasoningTokens
        if (reasoningTokens != null && finalUsage?.reasoningTokens != null) {
            require(reasoningTokens == finalUsage.reasoningTokens) {
                "Completion reasoning usage is inconsistent"
            }
        }

        return OnDeviceAiCompletionQuotaSnapshot(
            outputBytes = outputBytes,
            reasoningBytes = reasoningBytes,
            totalGeneratedBytes = totalGeneratedBytes,
            descriptorCount = descriptorCount,
            finalUsage = finalUsage,
            outputText = outputText,
            reasoningSummaryText = reasoningSummaryText,
        )
    }

    private fun validateToolPayloads(
        payloads: List<AiPayloadReference>,
        descriptorCount: Int,
        maximumDescriptors: Int,
    ): Long {
        val bytes = payloads.fold(0L) { total, payload ->
            require(payload.declaredLengthBytes <= OnDeviceAiLimits.MAX_TOOL_ARGUMENT_OR_RESULT_BYTES)
            safeAdd(total, payload.declaredLengthBytes, "Tool data byte count")
        }
        require(bytes <= OnDeviceAiLimits.MAX_TOTAL_TOOL_DATA_BYTES) { "Tool data exceeds the session quota" }
        AiDescriptorReferencePolicy.validateExactReferences(
            descriptorIndices = payloads.mapNotNull(AiPayloadReference::descriptorIndex),
            descriptorCount = descriptorCount,
            maximumDescriptors = protocolSessionDescriptorLimit(maximumDescriptors),
        )
        return bytes
    }

    private fun collectPayloads(request: OnDeviceAiRequest): List<AiPayloadReference> = buildList {
        request.messages.forEach { message -> message.parts.forEach { add(it.payload) } }
        request.tools.forEach { add(it.inputSchema) }
        request.options.responseSchema?.let(::add)
    }

    private fun safeAdd(left: Long, right: Long, label: String): Long {
        require(right >= 0L)
        require(left <= Long.MAX_VALUE - right) { "$label overflow" }
        return left + right
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    private val SHA_256 = Regex("[0-9a-f]{64}")

    private fun protocolSessionDescriptorLimit(maximumDescriptors: Int): Int {
        require(maximumDescriptors >= 0) { "Descriptor limit must not be negative" }
        return minOf(maximumDescriptors, AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION)
    }
}

object OnDeviceAiCapabilityNegotiator {
    fun intersect(host: OnDeviceAiCapabilities, provider: OnDeviceAiCapabilities): OnDeviceAiCapabilities {
        val supportsTools = host.supportsTools && provider.supportsTools
        return OnDeviceAiCapabilities(
            supportsStreaming = host.supportsStreaming && provider.supportsStreaming,
            supportsReasoning = host.supportsReasoning && provider.supportsReasoning,
            supportsTools = supportsTools,
            supportsStructuredJson = host.supportsStructuredJson && provider.supportsStructuredJson,
            supportsUsage = host.supportsUsage && provider.supportsUsage,
            maximumMessages = minOf(host.maximumMessages, provider.maximumMessages),
            maximumContentParts = minOf(host.maximumContentParts, provider.maximumContentParts),
            maximumToolDefinitions = if (supportsTools) {
                minOf(host.maximumToolDefinitions, provider.maximumToolDefinitions)
            } else {
                0
            },
            maximumContextBytes = minOf(host.maximumContextBytes, provider.maximumContextBytes),
            maximumOutputBytes = minOf(host.maximumOutputBytes, provider.maximumOutputBytes),
            maximumToolRounds = if (supportsTools) minOf(host.maximumToolRounds, provider.maximumToolRounds) else 0,
            maximumOutstandingToolCalls = if (supportsTools) {
                minOf(host.maximumOutstandingToolCalls, provider.maximumOutstandingToolCalls)
            } else {
                0
            },
            maximumRequestDescriptors = minOf(host.maximumRequestDescriptors, provider.maximumRequestDescriptors),
            maximumSessionDescriptors = minOf(host.maximumSessionDescriptors, provider.maximumSessionDescriptors),
            acceptedTextMimeTypes = host.acceptedTextMimeTypes.filter { it in provider.acceptedTextMimeTypes },
            acceptedSchemaMimeTypes = host.acceptedSchemaMimeTypes.filter { it in provider.acceptedSchemaMimeTypes },
        )
    }
}
