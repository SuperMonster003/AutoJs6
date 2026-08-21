package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ondeviceai.api.AiMessageRole
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilityId
import org.autojs.plugin.ondeviceai.api.OnDeviceAiLimits
import org.autojs.plugin.ondeviceai.api.OnDeviceAiMimeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidOnDeviceAiPluginMessagesTest {

    @Test
    fun materializesOrderedMessagesAndPlansTheirExactCapabilityBudget() {
        val request = request(
            listOf(
                OnDeviceAiPluginMessage(OnDeviceAiPluginMessageRole.SYSTEM, "始终回答 marker-系统"),
                OnDeviceAiPluginMessage(OnDeviceAiPluginMessageRole.USER, "Return the marker."),
            ),
        )

        val protocolMessages = request.toProtocolMessages()

        assertEquals(listOf(AiMessageRole.SYSTEM, AiMessageRole.USER), protocolMessages.map { it.role })
        assertEquals(
            listOf("始终回答 marker-系统", "Return the marker."),
            protocolMessages.map { message ->
                val payload = message.parts.single().payload
                assertEquals(OnDeviceAiMimeType.PLAIN, payload.mimeType)
                assertEquals("utf-8", payload.charset)
                assertNull(payload.descriptorIndex)
                assertEquals(payload.declaredLengthBytes, payload.inlineBytes?.size?.toLong())
                AiValidation.decodeUtf8(checkNotNull(payload.inlineBytes))
            },
        )
        assertTrue(protocolMessages.all { it.name == null && it.parts.size == 1 })

        val unary = onDeviceAiPluginCapabilityRequirement(request, streaming = false)
        assertEquals(2, unary.minimumMessages)
        assertEquals(2, unary.minimumContentParts)
        assertEquals(request.inputBytes, unary.minimumContextBytes)
        assertEquals(setOf(OnDeviceAiCapabilityId.USAGE), unary.requiredCapabilityIds)
        assertEquals(setOf(OnDeviceAiMimeType.PLAIN), unary.requiredTextMimeTypes)

        val streaming = onDeviceAiPluginCapabilityRequirement(request, streaming = true)
        assertEquals(
            setOf(OnDeviceAiCapabilityId.STREAMING, OnDeviceAiCapabilityId.USAGE),
            streaming.requiredCapabilityIds,
        )
        assertEquals(request.inputBytes, streaming.minimumContextBytes)
    }

    @Test
    fun requestSnapshotsMessagesAndRedactsTheirContent() {
        val sensitiveSystem = "sensitive-system-instruction"
        val sensitiveUser = "sensitive-user-question"
        val source = mutableListOf(
            OnDeviceAiPluginMessage(OnDeviceAiPluginMessageRole.SYSTEM, sensitiveSystem),
            OnDeviceAiPluginMessage(OnDeviceAiPluginMessageRole.USER, sensitiveUser),
        )

        val request = request(source)
        source.clear()

        assertEquals(2, request.messages.size)
        assertEquals(
            (sensitiveSystem + sensitiveUser).toByteArray(Charsets.UTF_8).size.toLong(),
            request.inputBytes,
        )
        assertFalse(sensitiveSystem in request.toString())
        assertFalse(sensitiveUser in request.toString())
        assertFalse(sensitiveSystem in request.messages.first().toString())
    }

    @Test
    fun requestRejectsInvalidMessageCountsFinalRolesAndAggregateInput() {
        assertThrows(IllegalArgumentException::class.java) { request(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            request(listOf(OnDeviceAiPluginMessage(OnDeviceAiPluginMessageRole.ASSISTANT, "answer")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            request(
                List(OnDeviceAiLimits.MAX_MESSAGES + 1) {
                    OnDeviceAiPluginMessage(OnDeviceAiPluginMessageRole.USER, "message")
                },
            )
        }

        val maximumInlineText = "x".repeat(AiCommonLimits.MAX_INLINE_PAYLOAD_BYTES)
        val aggregateOverflow = List(33) { index ->
            OnDeviceAiPluginMessage(
                role = if (index == 32) {
                    OnDeviceAiPluginMessageRole.USER
                } else {
                    OnDeviceAiPluginMessageRole.SYSTEM
                },
                content = maximumInlineText,
            )
        }
        assertTrue(aggregateOverflow.sumOf { it.contentBytes.toLong() } > OnDeviceAiLimits.MAX_TOTAL_INPUT_BYTES)
        assertThrows(IllegalArgumentException::class.java) { request(aggregateOverflow) }
    }

    @Test
    fun messageRejectsEmptyOversizedOrMalformedUtf16Content() {
        assertThrows(IllegalArgumentException::class.java) {
            OnDeviceAiPluginMessage(OnDeviceAiPluginMessageRole.USER, "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            OnDeviceAiPluginMessage(
                OnDeviceAiPluginMessageRole.USER,
                "x".repeat(AiCommonLimits.MAX_INLINE_PAYLOAD_BYTES + 1),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OnDeviceAiPluginMessage(OnDeviceAiPluginMessageRole.USER, "\uD800")
        }
    }

    private fun request(messages: List<OnDeviceAiPluginMessage>) = OnDeviceAiPluginAskRequest(
        component = OnDeviceAiProviderComponent(
            packageName = "org.example.provider",
            className = "org.example.provider.OnDeviceAiService",
        ),
        providerId = "example.provider",
        modelId = "example-model",
        messages = messages,
        timeoutMillis = 5_000L,
    )
}
