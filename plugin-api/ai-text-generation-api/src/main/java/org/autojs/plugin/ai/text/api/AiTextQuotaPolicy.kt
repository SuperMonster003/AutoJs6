package org.autojs.plugin.ai.text.api

import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiDescriptorReferencePolicy
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiUsage
import org.autojs.plugin.ai.common.api.AiUsagePolicy

data class AiTextQuotaSnapshot(
    val messageCount: Int,
    val contentPartCount: Int,
    val toolDefinitionCount: Int,
    val inputBytes: Long,
    val toolSchemaBytes: Long,
    val descriptorCount: Int,
    val effectiveMaximumOutputBytes: Long,
    val effectiveMaximumToolRounds: Int,
)

data class AiTextCompletionQuotaSnapshot(
    val outputBytes: Long,
    val reasoningBytes: Long,
    val totalGeneratedBytes: Long,
    val descriptorCount: Int,
    val finalUsage: AiUsage?,
    val outputText: String,
    val reasoningSummaryText: String?,
)

object AiTextQuotaPolicy {
    fun validateRequest(
        request: AiTextRequest,
        provider: AiTextCapabilities,
        descriptorCount: Int,
    ): AiTextQuotaSnapshot {
        require(request.messages.size <= provider.maximumMessages) { "Message count exceeds provider capability" }
        require(request.messages.size <= AiTextLimits.MAX_MESSAGES) { "Message count exceeds host quota" }
        val partCount = request.messages.fold(0) { total, message ->
            require(message.parts.size <= AiTextLimits.MAX_CONTENT_PARTS) { "One message contains too many parts" }
            require(total <= Int.MAX_VALUE - message.parts.size) { "Content part count overflow" }
            total + message.parts.size
        }
        require(partCount <= provider.maximumContentParts) { "Content part count exceeds provider capability" }
        require(partCount <= AiTextLimits.MAX_CONTENT_PARTS) { "Content part count exceeds host quota" }
        require(request.tools.size <= provider.maximumToolDefinitions) { "Tool count exceeds provider capability" }
        require(request.tools.size <= AiTextLimits.MAX_TOOL_DEFINITIONS) { "Tool count exceeds host quota" }
        require(request.tools.map { it.name }.distinct().size == request.tools.size) { "Duplicate tool name" }

        val requiredCapabilities = request.options.requiredCapabilityIds.toMutableSet().apply {
            if (request.options.stream) add(AiTextCapabilityId.STREAMING)
            if (request.options.includeReasoning) add(AiTextCapabilityId.REASONING)
            if (request.options.structuredJson) add(AiTextCapabilityId.STRUCTURED_JSON)
            if (request.options.reportUsage) add(AiTextCapabilityId.USAGE)
            if (request.options.maximumOutputTokens != null) add(AiTextCapabilityId.USAGE)
            if (request.tools.isNotEmpty() || request.options.maximumToolRounds > 0) add(AiTextCapabilityId.TOOLS)
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
        require(inputBytes <= AiTextLimits.MAX_TOTAL_INPUT_BYTES) { "Input bytes exceed host quota" }

        val schemaPayloads = buildList {
            request.tools.forEach { add(it.inputSchema) }
            request.options.responseSchema?.let(::add)
        }
        val schemaBytes = schemaPayloads.fold(0L) { total, payload ->
            require(payload.declaredLengthBytes <= AiTextLimits.MAX_ONE_SCHEMA_BYTES) { "One schema exceeds its quota" }
            safeAdd(total, payload.declaredLengthBytes, "Schema byte count")
        }
        require(schemaBytes <= AiTextLimits.MAX_ALL_TOOL_SCHEMA_BYTES) { "Schema bytes exceed session quota" }

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
            AiTextLimits.MAX_STREAMING_OUTPUT_BYTES
        } else {
            AiTextLimits.MAX_OUTPUT_BYTES
        }
        val effectiveOutputLimit = minOf(hostOutputLimit, provider.maximumOutputBytes)
        require(request.options.maximumOutputBytes <= effectiveOutputLimit) {
            "Requested output bytes exceed the effective limit"
        }
        val effectiveToolRounds = minOf(AiTextLimits.MAX_TOOL_ROUNDS, provider.maximumToolRounds)
        require(request.options.maximumToolRounds <= effectiveToolRounds) {
            "Requested tool rounds exceed the effective limit"
        }
        if (request.tools.isEmpty()) {
            require(request.options.maximumToolRounds == 0) { "Tool rounds require tool definitions" }
        }

        return AiTextQuotaSnapshot(
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

    /** Validates descriptors carried concurrently by this single tool-call callback event. */
    fun validateToolCalls(
        batch: AiToolCallBatch,
        descriptorCount: Int,
        maximumDescriptors: Int,
    ): Long = validateToolPayloads(batch.calls.map { it.arguments }, descriptorCount, maximumDescriptors)

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
        request: AiTextRequest,
        result: AiCompletionResult,
        descriptorCount: Int,
        maximumDescriptors: Int,
        observedUsage: AiUsage? = null,
        effectiveMaximumOutputBytes: Long = request.options.maximumOutputBytes,
        readDescriptor: ((index: Int, declaredLengthBytes: Long) -> ByteArray)? = null,
    ): AiTextCompletionQuotaSnapshot {
        require(effectiveMaximumOutputBytes in 1L..request.options.maximumOutputBytes) {
            "Invalid effective completion output limit"
        }
        require(result.output.mimeType == request.options.responseMimeType) {
            "Completion MIME type does not match the request"
        }
        if (!request.options.includeReasoning) {
            require(result.reasoning == null) { "Completion included reasoning that was not requested" }
        }

        val outputBytes = result.output.declaredLengthBytes
        val reasoningBytes = result.reasoning?.summary?.declaredLengthBytes ?: 0L
        val totalGeneratedBytes = safeAdd(outputBytes, reasoningBytes, "Completion byte count")
        require(totalGeneratedBytes <= effectiveMaximumOutputBytes) {
            "Completion output and reasoning exceed the effective limit"
        }

        val payloads = listOfNotNull(result.output, result.reasoning?.summary)
        AiDescriptorReferencePolicy.validateExactReferences(
            descriptorIndices = payloads.mapNotNull(AiPayloadReference::descriptorIndex),
            descriptorCount = descriptorCount,
            maximumDescriptors = protocolSessionDescriptorLimit(maximumDescriptors),
        )
        val outputText = AiTextPayloadPolicy.materializeAndValidate(result.output, readDescriptor)
        val reasoningSummaryText = result.reasoning?.summary?.let { summary ->
            AiTextPayloadPolicy.materializeAndValidate(summary, readDescriptor)
        }

        val finalUsage = result.usage?.let { AiUsagePolicy.mergeMonotonic(observedUsage, it) }
            ?: observedUsage
        if (request.options.reportUsage) {
            require(finalUsage != null) { "Requested usage was not reported" }
        }
        request.options.maximumOutputTokens?.let { maximumTokens ->
            val outputTokens = requireNotNull(finalUsage?.outputTokens) {
                "Output-token usage is required to verify the requested token limit"
            }
            require(outputTokens <= maximumTokens) { "Completion output tokens exceed the requested limit" }
        }
        val reasoningTokens = result.reasoning?.reasoningTokens
        if (reasoningTokens != null && finalUsage?.reasoningTokens != null) {
            require(reasoningTokens == finalUsage.reasoningTokens) {
                "Completion reasoning usage is inconsistent"
            }
        }

        return AiTextCompletionQuotaSnapshot(
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
            require(payload.declaredLengthBytes <= AiTextLimits.MAX_TOOL_ARGUMENT_OR_RESULT_BYTES)
            safeAdd(total, payload.declaredLengthBytes, "Tool data byte count")
        }
        require(bytes <= AiTextLimits.MAX_TOTAL_TOOL_DATA_BYTES) { "Tool data exceeds the session quota" }
        AiDescriptorReferencePolicy.validateExactReferences(
            descriptorIndices = payloads.mapNotNull(AiPayloadReference::descriptorIndex),
            descriptorCount = descriptorCount,
            maximumDescriptors = protocolSessionDescriptorLimit(maximumDescriptors),
        )
        return bytes
    }

    private fun collectPayloads(request: AiTextRequest): List<AiPayloadReference> = buildList {
        request.messages.forEach { message -> message.parts.forEach { add(it.payload) } }
        request.tools.forEach { add(it.inputSchema) }
        request.options.responseSchema?.let(::add)
    }

    private fun safeAdd(left: Long, right: Long, label: String): Long {
        require(right >= 0L)
        require(left <= Long.MAX_VALUE - right) { "$label overflow" }
        return left + right
    }

    private fun protocolSessionDescriptorLimit(maximumDescriptors: Int): Int {
        require(maximumDescriptors >= 0) { "Descriptor limit must not be negative" }
        return minOf(maximumDescriptors, AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION)
    }
}

object AiTextCapabilityNegotiator {
    fun intersect(host: AiTextCapabilities, provider: AiTextCapabilities): AiTextCapabilities {
        val supportsTools = host.supportsTools && provider.supportsTools
        return AiTextCapabilities(
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
