package org.autojs.plugin.ai.provider.fake

import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.provider.api.AiContentPart
import org.autojs.plugin.ai.provider.api.AiGenerationOptions
import org.autojs.plugin.ai.provider.api.AiMessage
import org.autojs.plugin.ai.provider.api.AiMessageRole
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.autojs.plugin.ai.provider.api.AiProviderRequest
import org.autojs.plugin.ai.provider.api.AiToolDefinition

internal fun fakeRequest(
    targetId: String = FakeAiProviderFixture.TARGET_ECHO,
    prompt: String = "hello",
    stream: Boolean = false,
    reasoning: Boolean = false,
    structuredJson: Boolean = false,
    responseMimeType: String = if (structuredJson) AiProviderMimeType.JSON else AiProviderMimeType.PLAIN,
    reportUsage: Boolean = true,
    maximumOutputBytes: Long = 1024L,
    maximumOutputTokens: Long? = null,
    tools: List<AiToolDefinition> = emptyList(),
) = AiProviderRequest(
    requestId = "fake-request",
    protocolVersion = FakeAiProviderFixture.protocolVersion,
    providerId = FakeAiProviderFixture.PROVIDER_ID,
    targetId = targetId,
    messages = listOf(
        AiMessage(
            role = AiMessageRole.USER,
            parts = listOf(AiContentPart(FakeAiProviderEngine.textPayload(prompt))),
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
    inputSchema = FakeAiProviderEngine.jsonPayload("{\"type\":\"object\"}"),
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
