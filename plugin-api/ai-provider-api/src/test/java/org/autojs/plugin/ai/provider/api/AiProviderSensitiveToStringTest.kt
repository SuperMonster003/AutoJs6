package org.autojs.plugin.ai.provider.api

import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderSensitiveToStringTest {

    @Test
    fun targetPagingSummariesDoNotExposeOpaqueTokensOrGeneration() {
        val requestSummary = AiTargetListRequest(
            protocolVersion = AiProtocolVersion(2, 0),
            pageSize = 20,
            pageToken = "sensitive-page-token",
        ).toString()
        val pageSummary = AiTargetPage(
            catalogGeneration = "sensitive-catalog-generation",
            targets = emptyList(),
            nextPageToken = "sensitive-next-page-token",
        ).toString()

        assertFalse(requestSummary.contains("sensitive-page-token"))
        assertTrue(requestSummary.contains("pageTokenPresent=true"))
        assertFalse(pageSummary.contains("sensitive-catalog-generation"))
        assertFalse(pageSummary.contains("sensitive-next-page-token"))
        assertTrue(pageSummary.contains("targetCount=0"))
        assertTrue(pageSummary.contains("nextPageTokenPresent=true"))
    }

    @Test
    fun requestAndToolSummariesDoNotRecursivelyExposeContent() {
        val prompt = textPayload("sensitive-prompt-text")
        val schemaDigest = "44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a"
        val schemaBytes = "{}".toByteArray()
        val schema = AiPayloadReference(
            mimeType = AiProviderMimeType.JSON,
            declaredLengthBytes = schemaBytes.size.toLong(),
            sha256 = schemaDigest,
            inlineBytes = schemaBytes,
            charset = "utf-8",
        )
        val tool = AiToolDefinition(
            name = "lookup",
            description = "sensitive-tool-description",
            inputSchema = schema,
        )
        val request = AiProviderRequest(
            requestId = "request-1",
            protocolVersion = AiProtocolVersion(2, 0),
            providerId = "provider-1",
            targetId = "provider-1:target-1",
            messages = listOf(AiMessage(AiMessageRole.USER, listOf(AiContentPart(prompt)))),
            options = AiGenerationOptions(
                stream = true,
                includeReasoning = false,
                structuredJson = false,
                reportUsage = false,
                maximumOutputBytes = 1024,
                maximumToolRounds = 1,
                timeoutMillis = AiProviderLimits.MIN_TIMEOUT_MILLIS,
            ),
            tools = listOf(tool),
        )

        val toolSummary = tool.toString()
        val requestSummary = request.toString()
        listOf(toolSummary, requestSummary).forEach { rendered ->
            assertFalse(rendered.contains("sensitive-tool-description"))
            assertFalse(rendered.contains(schemaDigest))
        }
        assertFalse(requestSummary.contains("sensitive-prompt-text"))
        assertTrue(toolSummary.contains("descriptionBytes="))
        assertTrue(requestSummary.contains("messageCount=1"))
        assertTrue(requestSummary.contains("contentPartCount=1"))
        assertTrue(requestSummary.contains("toolCount=1"))
    }

    @Test
    fun generatedTextSummariesExposeOnlyCounts() {
        val chunkSummary = AiProviderChunk(
            sequence = 7,
            textDelta = "sensitive-output-delta",
            reasoningDelta = "sensitive-reasoning-delta",
        ).toString()
        val completionSummary = AiProviderCompletionQuotaSnapshot(
            outputBytes = 22,
            reasoningBytes = 25,
            totalGeneratedBytes = 47,
            descriptorCount = 0,
            finalUsage = null,
            outputText = "sensitive-complete-output",
            reasoningSummaryText = "sensitive-complete-reasoning",
        ).toString()

        assertFalse(chunkSummary.contains("sensitive-output-delta"))
        assertFalse(chunkSummary.contains("sensitive-reasoning-delta"))
        assertTrue(chunkSummary.contains("sequence=7"))
        assertTrue(chunkSummary.contains("textDeltaBytes="))
        assertTrue(chunkSummary.contains("reasoningDeltaBytes="))
        assertFalse(completionSummary.contains("sensitive-complete-output"))
        assertFalse(completionSummary.contains("sensitive-complete-reasoning"))
        assertTrue(completionSummary.contains("totalGeneratedBytes=47"))
        assertTrue(completionSummary.contains("reasoningSummaryPresent=true"))
    }

    @Test
    fun toolTurnSummariesDoNotExposeCallIdsOrPayloads() {
        val call = AiToolCall(
            callId = "sensitive-call-id",
            name = "lookup",
            arguments = jsonPayload("{\"sensitive-argument\":true}"),
        )
        val result = AiToolResult(
            callId = "sensitive-call-id",
            output = textPayload("sensitive-tool-result"),
        )
        val callBatchSummary = AiToolCallBatch(listOf(call)).toString()
        val resultBatchSummary = AiToolResultBatch(listOf(result)).toString()

        listOf(call.toString(), result.toString(), callBatchSummary, resultBatchSummary).forEach { rendered ->
            assertFalse(rendered.contains("sensitive-call-id"))
            assertFalse(rendered.contains("sensitive-argument"))
            assertFalse(rendered.contains("sensitive-tool-result"))
        }
        assertTrue(callBatchSummary.contains("callCount=1"))
        assertTrue(resultBatchSummary.contains("resultCount=1"))
    }

    @Test
    fun verifiedToolSnapshotDoesNotExposeCallIdsOrArguments() {
        val rendered = AiVerifiedToolCallBatchSnapshot(
            totalBytes = 28,
            descriptorCount = 1,
            argumentsJsonByCallId = mapOf("sensitive-call-id" to "{\"sensitive\":true}"),
        ).toString()

        assertFalse(rendered.contains("sensitive-call-id"))
        assertFalse(rendered.contains("sensitive"))
        assertTrue(rendered.contains("totalBytes=28"))
        assertTrue(rendered.contains("callCount=1"))
    }

    private fun textPayload(value: String): AiPayloadReference {
        val bytes = value.toByteArray()
        return AiPayloadReference(
            mimeType = AiProviderMimeType.PLAIN,
            declaredLengthBytes = bytes.size.toLong(),
            inlineBytes = bytes,
            charset = "utf-8",
        )
    }

    private fun jsonPayload(value: String): AiPayloadReference {
        val bytes = value.toByteArray()
        return AiPayloadReference(
            mimeType = AiProviderMimeType.JSON,
            declaredLengthBytes = bytes.size.toLong(),
            inlineBytes = bytes,
            charset = "utf-8",
        )
    }
}
