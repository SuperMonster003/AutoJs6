package org.autojs.plugin.ondeviceai.fake

import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ondeviceai.api.AiContentPart
import org.autojs.plugin.ondeviceai.api.AiGenerationOptions
import org.autojs.plugin.ondeviceai.api.AiMessage
import org.autojs.plugin.ondeviceai.api.AiMessageRole
import org.autojs.plugin.ondeviceai.api.OnDeviceAiMimeType
import org.autojs.plugin.ondeviceai.api.OnDeviceAiRequest
import org.autojs.plugin.ondeviceai.api.AiToolDefinition

internal fun fakeRequest(
    modelId: String = FakeOnDeviceAiFixture.MODEL_ECHO,
    prompt: String = "hello",
    stream: Boolean = false,
    reasoning: Boolean = false,
    structuredJson: Boolean = false,
    responseMimeType: String = if (structuredJson) OnDeviceAiMimeType.JSON else OnDeviceAiMimeType.PLAIN,
    reportUsage: Boolean = true,
    maximumOutputBytes: Long = 1024L,
    maximumOutputTokens: Long? = null,
    tools: List<AiToolDefinition> = emptyList(),
) = OnDeviceAiRequest(
    requestId = "fake-request",
    protocolVersion = FakeOnDeviceAiFixture.protocolVersion,
    providerId = FakeOnDeviceAiFixture.PROVIDER_ID,
    modelId = modelId,
    messages = listOf(
        AiMessage(
            role = AiMessageRole.USER,
            parts = listOf(AiContentPart(FakeOnDeviceAiEngine.textPayload(prompt))),
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
    inputSchema = FakeOnDeviceAiEngine.jsonPayload("{\"type\":\"object\"}"),
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
