package org.autojs.plugin.ai.provider.api

import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiProtocolRange
import org.autojs.plugin.protocol.wire.TaggedWireDocument
import org.autojs.plugin.protocol.wire.TaggedWireException
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class AiVisionProtocolTest {
    private val v20 = AiProviderProtocol.PROTOCOL_V2
    private val v21 = AiProviderProtocol.PROTOCOL_V2_1
    private val vision = capabilities().copy(vision = AiVisionCapabilities())

    @Test fun newAndOldPeersNegotiateAndCatalogNeverAdvertisesVisionToOldHosts() {
        assertEquals(v20, AiProviderVersionPolicy.negotiate(AiProtocolRange(v20, v20)))
        assertEquals(v21, AiProviderVersionPolicy.negotiate(AiProtocolRange(v20, v21)))
        val target = localTarget(capabilityIds = listOf(AiProviderCapabilityId.TOOLS, AiProviderCapabilityId.VISION))
        assertEquals(listOf(AiProviderCapabilityId.TOOLS), AiTargetCatalogPolicy.forProtocol(target, v20).capabilityIds)
        assertEquals(target, AiTargetCatalogPolicy.forProtocol(target, v21))
        assertThrows(IllegalArgumentException::class.java) { request().copy(protocolVersion = v20) }
        assertThrows(IllegalArgumentException::class.java) { request(emptyList()).copy(protocolVersion = v20) }
    }

    @Test fun imageCapabilitiesAreOptionalForOldDecodersButImagesAreRequiredFields() {
        val encoded = AiProviderCodec.encodeCapabilities(vision)
        TaggedWireDocument.decode(encoded).rejectUnknownRequiredFields((1..17).toSet())
        assertEquals(vision, AiProviderCodec.decodeCapabilities(encoded))
        assertNull(AiProviderCodec.decodeCapabilities(AiProviderCodec.encodeCapabilities(capabilities())).vision)
        val part = image()
        val encodedPart = AiProviderCodec.encodeContentPart(part)
        assertEquals(part, AiProviderCodec.decodeContentPart(encodedPart))
        assertThrows(TaggedWireException::class.java) {
            TaggedWireDocument.decode(encodedPart).rejectUnknownRequiredFields(setOf(1))
        }
        val result = AiToolResult("call", textPayload("ok"), imageParts = listOf(part))
        val encodedResult = AiProviderCodec.encodeToolResult(result)
        assertEquals(result, AiProviderCodec.decodeToolResult(encodedResult))
        assertThrows(TaggedWireException::class.java) {
            TaggedWireDocument.decode(encodedResult).rejectUnknownRequiredFields(setOf(1, 2, 3))
        }
        assertEquals(request(), AiProviderCodec.decodeTextRequest(AiProviderCodec.encodeTextRequest(request())))
    }

    @Test fun independentImageQuotaDoesNotConsumeTextContextAndEnforcesExactDescriptorReferences() {
        val req = request(listOf(image(length = 4096)))
        val snapshot = AiProviderQuotaPolicy.validateRequest(req, vision.copy(maximumContextBytes = 4), 1)
        assertEquals(4L, snapshot.inputBytes)
        assertEquals(4096L, snapshot.imageBytes)
        assertEquals(1, snapshot.imageCount)
        for (count in listOf(0, 2)) assertThrows(IllegalArgumentException::class.java) {
            AiProviderQuotaPolicy.validateRequest(req, vision, count)
        }
        assertThrows(IllegalArgumentException::class.java) { AiProviderQuotaPolicy.validateRequest(req, capabilities(), 1) }
        assertThrows(IllegalArgumentException::class.java) {
            AiProviderQuotaPolicy.validateRequest(request(listOf(image(), image())), vision, 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiProviderQuotaPolicy.validateRequest(req, vision.copy(vision = AiVisionCapabilities(maximumImageBytes = 100)), 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiProviderQuotaPolicy.validateRequest(request((0..2).map { image(it, AiProviderLimits.MAX_IMAGE_BYTES) }), vision, 3)
        }
    }

    @Test fun imagePayloadRequiresSupportedMimeDescriptorDigestAndUserRole() {
        val part = image()
        val payload = part.payload
        assertThrows(IllegalArgumentException::class.java) { AiContentPart(AiPayloadReference(AiProviderMimeType.PNG, 1, descriptorIndex = 0), part.image) }
        assertThrows(IllegalArgumentException::class.java) { AiContentPart(AiPayloadReference(AiProviderMimeType.PNG, 1,
            descriptorIndex = 0, charset = "utf-8", sha256 = payload.sha256), part.image) }
        assertThrows(IllegalArgumentException::class.java) { AiContentPart(AiPayloadReference("image/webp", 1,
            descriptorIndex = 0, sha256 = payload.sha256), part.image) }
        assertThrows(IllegalArgumentException::class.java) {
            AiContentPart(AiPayloadReference(AiProviderMimeType.PNG, 1, inlineBytes = byteArrayOf(1), sha256 = payload.sha256), part.image)
        }
        assertThrows(IllegalArgumentException::class.java) { AiContentPart(payload) }
        assertThrows(IllegalArgumentException::class.java) { AiImageMetadata(4097, 1) }
        assertThrows(IllegalArgumentException::class.java) {
            request().copy(messages = listOf(AiMessage(AiMessageRole.ASSISTANT, listOf(part))))
        }
    }

    @Test fun materializationChecksLengthAndDigestBeforeImageDecode() {
        val bytes = byteArrayOf(1, 2, 3)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        val part = AiContentPart(AiPayloadReference(AiProviderMimeType.PNG, 3, sha256 = hash, descriptorIndex = 0), AiImageMetadata(1, 1))
        assertArrayEquals(bytes, AiProviderImagePolicy.materialize(part) { index, size ->
            assertEquals(0, index); assertEquals(3L, size); bytes
        })
        assertThrows(IllegalArgumentException::class.java) { AiProviderImagePolicy.materialize(part) { _, _ -> byteArrayOf(1) } }
        assertThrows(IllegalArgumentException::class.java) { AiProviderImagePolicy.materialize(part) { _, _ -> byteArrayOf(3, 2, 1) } }
    }

    @Test fun toolImageResultsRequireNegotiatedVisionAndCannotBypassSessionBudget() {
        val batch = AiToolResultBatch(listOf(AiToolResult("call", textPayload("ok"), imageParts = listOf(image()))))
        assertThrows(IllegalArgumentException::class.java) { AiProviderQuotaPolicy.validateToolResults(batch, 1, 4) }
        assertEquals(1, AiProviderQuotaPolicy.validateToolResults(batch, 1, 4, request(), vision).count)
        val textRequest = request(emptyList()).copy(options = options(vision = false), protocolVersion = v20)
        assertThrows(IllegalArgumentException::class.java) { AiProviderQuotaPolicy.validateToolResults(batch, 1, 4, textRequest, vision) }
        assertThrows(IllegalArgumentException::class.java) { AiProviderQuotaPolicy.validateToolResults(batch, 0, 4, request(), vision) }
        val calls = AiToolCallBatch(listOf(AiToolCall("call", "read", jsonPayload("{}"))))
        for (policy in listOf(AiToolTurnPolicy(1, initialImageCount = 16),
            AiToolTurnPolicy(1, initialImageBytes = AiProviderLimits.MAX_SESSION_IMAGE_BYTES))) {
            policy.open(calls)
            assertThrows(IllegalArgumentException::class.java) { policy.submit(batch) }
            assertTrue(policy.hasOutstandingTurn)
        }
    }

    @Test fun imageCapabilityIntersectionAndResultListsAreImmutable() {
        val mimes = mutableListOf(AiProviderMimeType.PNG)
        val limited = AiVisionCapabilities(maximumImages = 1, maximumImageBytes = 100, acceptedImageMimeTypes = mimes)
        mimes.clear()
        assertEquals(listOf(AiProviderMimeType.PNG), limited.acceptedImageMimeTypes)
        val result = AiVisionCapabilities().intersect(limited)!!
        assertEquals(1, result.maximumImages)
        assertEquals(100L, result.maximumImageBytes)
        assertNull(limited.intersect(AiVisionCapabilities(acceptedImageMimeTypes = listOf(AiProviderMimeType.JPEG))))
        val images = mutableListOf(image())
        val tool = AiToolResult("call", textPayload("private text"), imageParts = images)
        images.clear()
        assertEquals(1, tool.imageParts.size)
        assertFalse(tool.toString().contains("private"))
        assertFalse(tool.toString().contains("aaaa"))
    }

    private fun image(index: Int = 0, length: Long = 1) = AiContentPart(
        AiPayloadReference(AiProviderMimeType.PNG, length, sha256 = "a".repeat(64), descriptorIndex = index), AiImageMetadata(16, 16))

    private fun options(vision: Boolean = true) = AiGenerationOptions(stream = false, includeReasoning = false,
        structuredJson = false, reportUsage = false, maximumOutputBytes = 1024, timeoutMillis = 30000,
        requiredCapabilityIds = if (vision) listOf(AiProviderCapabilityId.VISION) else emptyList())

    private fun request(images: List<AiContentPart> = listOf(image())) = AiProviderRequest("vision-1", v21,
        "fake.local", "fake.local:vision", listOf(AiMessage(AiMessageRole.USER,
            listOf(AiContentPart(textPayload("look"))) + images)), options())
}
