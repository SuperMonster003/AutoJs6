package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ai.provider.api.AiMessageRole
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import org.autojs.plugin.ai.provider.api.AiProviderLimits
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAiPluginMessagesTest {

    @Test
    fun materializesOrderedMessagesAndPlansTheirExactCapabilityBudget() {
        val request = request(
            listOf(
                AiPluginMessage(AiPluginMessageRole.SYSTEM, "始终回答 marker-系统"),
                AiPluginMessage(AiPluginMessageRole.USER, "Return the marker."),
            ),
        )

        val protocolMessages = request.toProtocolMessages()

        assertEquals(listOf(AiMessageRole.SYSTEM, AiMessageRole.USER), protocolMessages.map { it.role })
        assertEquals(
            listOf("始终回答 marker-系统", "Return the marker."),
            protocolMessages.map { message ->
                val payload = message.parts.single().payload
                assertEquals(AiProviderMimeType.PLAIN, payload.mimeType)
                assertEquals("utf-8", payload.charset)
                assertNull(payload.descriptorIndex)
                assertEquals(payload.declaredLengthBytes, payload.inlineBytes?.size?.toLong())
                AiValidation.decodeUtf8(checkNotNull(payload.inlineBytes))
            },
        )
        assertTrue(protocolMessages.all { it.name == null && it.parts.size == 1 })

        val unary = aiProviderPluginCapabilityRequirement(request, streaming = false)
        assertEquals(2, unary.minimumMessages)
        assertEquals(2, unary.minimumContentParts)
        assertEquals(request.inputBytes, unary.minimumContextBytes)
        assertEquals(setOf(AiProviderCapabilityId.USAGE), unary.requiredCapabilityIds)
        assertEquals(setOf(AiProviderMimeType.PLAIN), unary.requiredTextMimeTypes)

        val streaming = aiProviderPluginCapabilityRequirement(request, streaming = true)
        assertEquals(
            setOf(AiProviderCapabilityId.STREAMING, AiProviderCapabilityId.USAGE),
            streaming.requiredCapabilityIds,
        )
        assertEquals(request.inputBytes, streaming.minimumContextBytes)

        val persistent = aiProviderPluginCapabilityRequirement(
            request,
            streaming = false,
            persistentSession = true,
        )
        assertEquals(
            setOf(AiProviderCapabilityId.USAGE, AiProviderCapabilityId.PERSISTENT_SESSION),
            persistent.requiredCapabilityIds,
        )
        val options = request.toGenerationOptions(
            stream = false,
            maximumOutputBytes = 1024,
            timeoutMillis = request.timeoutMillis,
            requiredCapabilityIds = persistent.requiredCapabilityIds.toList(),
            persistentSession = true,
        )
        assertTrue(options.persistentSession)
        assertEquals(
            setOf(AiProviderCapabilityId.USAGE, AiProviderCapabilityId.PERSISTENT_SESSION),
            options.requiredCapabilityIds.toSet(),
        )
    }

    @Test
    fun requestSnapshotsMessagesAndRedactsTheirContent() {
        val sensitiveSystem = "sensitive-system-instruction"
        val sensitiveUser = "sensitive-user-question"
        val source = mutableListOf(
            AiPluginMessage(AiPluginMessageRole.SYSTEM, sensitiveSystem),
            AiPluginMessage(AiPluginMessageRole.USER, sensitiveUser),
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
    fun structuredJsonRequiresJsonCapabilitiesAndCarriesAnInlineSchema() {
        val schema = """{"type":"object","properties":{"ok":{"type":"boolean"}}}"""
        val request = AiPluginAskRequest(
            component = AiProviderComponent(
                packageName = "org.example.provider",
                className = "org.example.provider.AiProviderService",
            ),
            providerId = "example.provider",
            targetId = "local:example-model",
            messages = listOf(AiPluginMessage(AiPluginMessageRole.USER, "Return JSON")),
            timeoutMillis = 5_000L,
            responseSchemaJson = schema,
        )

        val requirement = aiProviderPluginCapabilityRequirement(request, streaming = true)
        assertEquals(
            setOf(
                AiProviderCapabilityId.STREAMING,
                AiProviderCapabilityId.STRUCTURED_JSON,
                AiProviderCapabilityId.USAGE,
            ),
            requirement.requiredCapabilityIds,
        )
        assertEquals(
            setOf(AiProviderMimeType.PLAIN, AiProviderMimeType.JSON),
            requirement.requiredTextMimeTypes,
        )
        assertEquals(setOf(AiProviderMimeType.JSON), requirement.requiredSchemaMimeTypes)

        val options = request.toGenerationOptions(
            stream = true,
            maximumOutputBytes = 1024,
            timeoutMillis = request.timeoutMillis,
            requiredCapabilityIds = listOf(AiProviderCapabilityId.STREAMING),
        )
        assertTrue(options.structuredJson)
        assertEquals(AiProviderMimeType.JSON, options.responseMimeType)
        assertEquals(
            schema,
            AiValidation.decodeUtf8(checkNotNull(options.responseSchema?.inlineBytes)),
        )
        assertTrue(AiProviderCapabilityId.STRUCTURED_JSON in options.requiredCapabilityIds)
        assertFalse(schema in request.toString())
    }

    @Test
    fun requestRejectsInvalidMessageCountsFinalRolesAndAggregateInput() {
        assertThrows(IllegalArgumentException::class.java) { request(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            request(listOf(AiPluginMessage(AiPluginMessageRole.ASSISTANT, "answer")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            request(
                List(AiProviderLimits.MAX_MESSAGES + 1) {
                    AiPluginMessage(AiPluginMessageRole.USER, "message")
                },
            )
        }

        val maximumInlineText = "x".repeat(AiCommonLimits.MAX_INLINE_PAYLOAD_BYTES)
        val aggregateOverflow = List(33) { index ->
            AiPluginMessage(
                role = if (index == 32) {
                    AiPluginMessageRole.USER
                } else {
                    AiPluginMessageRole.SYSTEM
                },
                content = maximumInlineText,
            )
        }
        assertTrue(aggregateOverflow.sumOf { it.contentBytes.toLong() } > AiProviderLimits.MAX_TOTAL_INPUT_BYTES)
        assertThrows(IllegalArgumentException::class.java) { request(aggregateOverflow) }
    }

    @Test
    fun messageRejectsEmptyOversizedOrMalformedUtf16Content() {
        assertThrows(IllegalArgumentException::class.java) {
            AiPluginMessage(AiPluginMessageRole.USER, "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiPluginMessage(
                AiPluginMessageRole.USER,
                "x".repeat(AiCommonLimits.MAX_INLINE_PAYLOAD_BYTES + 1),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiPluginMessage(AiPluginMessageRole.USER, "\uD800")
        }
    }

    private fun request(messages: List<AiPluginMessage>) = AiPluginAskRequest(
        component = AiProviderComponent(
            packageName = "org.example.provider",
            className = "org.example.provider.AiProviderService",
        ),
        providerId = "example.provider",
        targetId = "local:example-model",
        messages = messages,
        timeoutMillis = 5_000L,
    )
}
