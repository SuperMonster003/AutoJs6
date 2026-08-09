package org.autojs.plugin.ai.text.fake

import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.text.api.AiContentPart
import org.autojs.plugin.ai.text.api.AiGenerationOptions
import org.autojs.plugin.ai.text.api.AiMessage
import org.autojs.plugin.ai.text.api.AiMessageRole
import org.autojs.plugin.ai.text.api.AiTextMimeType
import org.autojs.plugin.ai.text.api.AiTextRequest
import org.autojs.plugin.ai.text.api.AiToolDefinition

internal fun fakeRequest(
    modelId: String = FakeAiTextFixture.MODEL_ECHO,
    prompt: String = "hello",
    stream: Boolean = false,
    reasoning: Boolean = false,
    structuredJson: Boolean = false,
    responseMimeType: String = if (structuredJson) AiTextMimeType.JSON else AiTextMimeType.PLAIN,
    reportUsage: Boolean = true,
    maximumOutputBytes: Long = 1024L,
    maximumOutputTokens: Long? = null,
    tools: List<AiToolDefinition> = emptyList(),
) = AiTextRequest(
    requestId = "fake-request",
    protocolVersion = AiProtocolVersion(1, 0),
    providerId = FakeAiTextFixture.PROVIDER_ID,
    modelId = modelId,
    messages = listOf(
        AiMessage(
            role = AiMessageRole.USER,
            parts = listOf(AiContentPart(FakeAiTextEngine.textPayload(prompt))),
        ),
    ),
    options = AiGenerationOptions(
        stream = stream,
        includeReasoning = reasoning,
        structuredJson = structuredJson,
        reportUsage = reportUsage,
        maximumOutputBytes = maximumOutputBytes,
        maximumOutputTokens = maximumOutputTokens,
        maximumToolRounds = if (tools.isEmpty()) 0 else 1,
        timeoutMillis = 30_000L,
        responseMimeType = responseMimeType,
    ),
    tools = tools,
)

internal fun fakeTool() = AiToolDefinition(
    name = "lookup",
    description = "Deterministic lookup",
    inputSchema = FakeAiTextEngine.jsonPayload("{\"type\":\"object\"}"),
)

internal fun descriptorPayload(
    mimeType: String,
    length: Long,
    index: Int,
    sha256: String? = null,
    charset: String? = null,
) = AiPayloadReference(
    mimeType = mimeType,
    declaredLengthBytes = length,
    sha256 = sha256,
    descriptorIndex = index,
    charset = charset,
)
