package org.autojs.plugin.ai.common.api

import org.autojs.plugin.protocol.wire.TaggedWireError
import org.autojs.plugin.protocol.wire.TaggedWireException
import org.autojs.plugin.protocol.wire.TaggedWireWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCommonCodecTest {
    @Test
    fun providerInfoRoundTripsWithoutTrustClaimsOrBundles() {
        val expected = AiProviderInfo(
            providerId = "fake.local",
            displayName = "Fake local provider",
            implementationVersionName = "1.0.0",
            implementationVersionCode = 4L,
            protocolRange = AiProtocolRange(AiProtocolVersion(1, 0), AiProtocolVersion(1, 2)),
            locality = AiDataLocality.ON_DEVICE,
            credentialMode = AiCredentialMode.NONE,
            supportedAbis = emptyList(),
            minimumHostVersionCode = 5270L,
        )

        assertEquals(expected, AiCommonCodec.decodeProviderInfo(AiCommonCodec.encodeProviderInfo(expected)))
    }

    @Test
    fun providerInfoSnapshotsAndFreezesCallerOwnedLists() {
        val origins = mutableListOf("https://example.com")
        val abis = mutableListOf("arm64-v8a")
        val provider = AiProviderInfo(
            providerId = "remote.provider",
            displayName = "Remote provider",
            implementationVersionName = "1.0.0",
            implementationVersionCode = 1L,
            protocolRange = AiProtocolRange(AiProtocolVersion(1, 0), AiProtocolVersion(1, 0)),
            locality = AiDataLocality.REMOTE,
            credentialMode = AiCredentialMode.PLUGIN_MANAGED,
            declaredHttpsOrigins = origins,
            supportedAbis = abis,
        )

        origins.clear()
        abis[0] = "x86_64"

        assertEquals(listOf("https://example.com"), provider.declaredHttpsOrigins)
        assertEquals(listOf("arm64-v8a"), provider.supportedAbis)
        val (_, _, _, _, _, _, _, componentOrigins, componentAbis) = provider
        assertEquals(provider.declaredHttpsOrigins, componentOrigins)
        assertEquals(provider.supportedAbis, componentAbis)
        assertThrows(UnsupportedOperationException::class.java) {
            (provider.declaredHttpsOrigins as MutableList<String>).add("https://other.example")
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (provider.supportedAbis as MutableList<String>).clear()
        }
        assertEquals(provider, provider.copy())
        assertEquals(provider.hashCode(), provider.copy().hashCode())
        assertEquals(provider.toString(), provider.copy().toString())
    }

    @Test
    fun payloadReferencesRoundTripInlineAndDescriptorForms() {
        val inline = AiPayloadReference(
            mimeType = "text/plain",
            declaredLengthBytes = 5,
            sha256 = "0".repeat(64),
            inlineBytes = "hello".toByteArray(),
            charset = "utf-8",
        )
        val descriptor = AiPayloadReference(
            mimeType = "application/json",
            declaredLengthBytes = 1024,
            descriptorIndex = 2,
        )

        assertEquals(inline, AiCommonCodec.decodePayloadReference(AiCommonCodec.encodePayloadReference(inline)))
        assertEquals(descriptor, AiCommonCodec.decodePayloadReference(AiCommonCodec.encodePayloadReference(descriptor)))
        assertArrayEquals("hello".toByteArray(), inline.inlineBytes)
    }

    @Test
    fun payloadReferenceDefensivelyCopiesInlineBytes() {
        val source = byteArrayOf(1, 2, 3)
        val payload = AiPayloadReference("application/json", 3, inlineBytes = source)
        source[0] = 9
        val returned = requireNotNull(payload.inlineBytes)
        returned[1] = 9

        assertArrayEquals(byteArrayOf(1, 2, 3), payload.inlineBytes)
    }

    @Test
    fun errorAndUsageRoundTripWithoutProviderRawPayload() {
        val error = AiError(
            code = AiErrorCode.PROVIDER_FAILED,
            message = "Bounded failure",
            retryDisposition = AiRetryDisposition.NEVER,
            providerCode = "fake_failure",
        )
        val usage = AiUsage(10, 5, 15, 2, 1, 1_234)

        assertEquals(error, AiCommonCodec.decodeError(AiCommonCodec.encodeError(error)))
        assertEquals(usage, AiCommonCodec.decodeUsage(AiCommonCodec.encodeUsage(usage)))
    }

    @Test
    fun unknownOptionalProtocolFieldIsSkippedButUnknownRequiredFieldIsRejected() {
        fun encoded(required: Boolean) = TaggedWireWriter(AiCommonProtocol.SCHEMA_PROTOCOL_RANGE, 1, 1)
            .int32(1, 1, true)
            .int32(2, 0, true)
            .int32(3, 1, true)
            .int32(4, 1, true)
            .string(99, "future", required)
            .encode()

        assertEquals(
            AiProtocolVersion(1, 1),
            AiCommonCodec.decodeProtocolRange(encoded(false)).maximum,
        )
        val failure = assertThrows(TaggedWireException::class.java) {
            AiCommonCodec.decodeProtocolRange(encoded(true))
        }
        assertEquals(TaggedWireError.UNKNOWN_REQUIRED_FIELD, failure.error)
    }

    @Test
    fun negotiationChoosesHighestSharedVersionAndRejectsDisjointRanges() {
        val host = AiProtocolRange(AiProtocolVersion(1, 0), AiProtocolVersion(1, 4))
        val provider = AiProtocolRange(AiProtocolVersion(1, 2), AiProtocolVersion(1, 3))

        assertEquals(AiProtocolVersion(1, 3), AiProtocolNegotiator.negotiate(host, provider))
        assertNull(
            AiProtocolNegotiator.negotiate(
                host,
                AiProtocolRange(AiProtocolVersion(2, 0), AiProtocolVersion(2, 0)),
            ),
        )
    }

    @Test
    fun usageMustRemainMonotonicWhenBothSnapshotsReportACounter() {
        val first = AiUsage(inputTokens = 10, outputTokens = 2, totalTokens = 12, durationMillis = 50)
        assertTrue(AiUsagePolicy.isMonotonic(first, AiUsage(10, 3, 13, durationMillis = 60)))
        assertFalse(AiUsagePolicy.isMonotonic(first, AiUsage(9, 3, 12, durationMillis = 60)))
        assertFalse(AiUsagePolicy.isMonotonic(first, AiUsage(outputTokens = 3, durationMillis = 49)))
        assertTrue(AiUsagePolicy.isMonotonic(first, AiUsage(outputTokens = 3)))
    }

    @Test
    fun partialUsageSnapshotsCannotResetAnOmittedCounter() {
        val first = AiUsage(inputTokens = 10, outputTokens = 2, totalTokens = 20, durationMillis = 50)
        val merged = AiUsagePolicy.mergeMonotonic(first, AiUsage(outputTokens = 3))

        assertEquals(
            AiUsage(inputTokens = 10, outputTokens = 3, totalTokens = 20, durationMillis = 50),
            merged,
        )
        assertThrows(IllegalArgumentException::class.java) {
            AiUsagePolicy.mergeMonotonic(merged, AiUsage(inputTokens = 9))
        }
    }

    @Test
    fun partialUsageSnapshotsCannotHideAnInconsistentReportedTotal() {
        val exact = AiUsage(inputTokens = 10, outputTokens = 2, totalTokens = 12)
        assertThrows(IllegalArgumentException::class.java) {
            AiUsagePolicy.mergeMonotonic(exact, AiUsage(outputTokens = 3))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiUsagePolicy.mergeMonotonic(
                AiUsage(inputTokens = 10),
                AiUsage(outputTokens = 3, totalTokens = 12),
            )
        }
    }

    @Test
    fun usageTotalIsAnOverflowSafeInputPlusOutputLowerBound() {
        assertThrows(IllegalArgumentException::class.java) {
            AiUsage(inputTokens = 8, outputTokens = 8, totalTokens = 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiUsage(inputTokens = Long.MAX_VALUE, outputTokens = 1, totalTokens = Long.MAX_VALUE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiUsage(outputTokens = 1, durationMillis = -1)
        }
        AiUsage(
            inputTokens = 8,
            outputTokens = 8,
            totalTokens = 16,
            reasoningTokens = 3,
            cachedInputTokens = 2,
            durationMillis = 1_234,
        )
    }

    @Test
    fun descriptorReferencesMustBeBoundedUniqueAndExhaustive() {
        AiDescriptorReferencePolicy.validateExactReferences(listOf(1, 0), 2, 2)
        assertThrows(IllegalArgumentException::class.java) {
            AiDescriptorReferencePolicy.validateExactReferences(listOf(0, 0), 2, 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiDescriptorReferencePolicy.validateExactReferences(listOf(0), 2, 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiDescriptorReferencePolicy.validateExactReferences(listOf(2), 1, 2)
        }
    }

    @Test
    fun payloadReferenceRejectsAmbiguousOrMissingStorage() {
        assertThrows(IllegalArgumentException::class.java) {
            AiPayloadReference(
                mimeType = "application/json",
                declaredLengthBytes = 2,
                inlineBytes = byteArrayOf('{'.code.toByte(), '}'.code.toByte()),
                descriptorIndex = 0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiPayloadReference(
                mimeType = "application/json",
                declaredLengthBytes = 0,
            )
        }
    }

    @Test
    fun malformedAndDuplicateKnownFieldsAreRejected() {
        val valid = AiCommonCodec.encodeProtocolRange(
            AiProtocolRange(AiProtocolVersion(1, 0), AiProtocolVersion(1, 0)),
        )
        val malformed = assertThrows(TaggedWireException::class.java) {
            AiCommonCodec.decodeProtocolRange(valid.copyOf(valid.size - 1))
        }
        assertEquals(TaggedWireError.MALFORMED_HEADER, malformed.error)

        val duplicate = TaggedWireWriter(AiCommonProtocol.SCHEMA_PROTOCOL_RANGE, 1, 0)
            .int32(1, 1, true)
            .int32(1, 1)
            .int32(2, 0, true)
            .int32(3, 1, true)
            .int32(4, 0, true)
            .encode()
        val duplicateFailure = assertThrows(TaggedWireException::class.java) {
            AiCommonCodec.decodeProtocolRange(duplicate)
        }
        assertEquals(TaggedWireError.DUPLICATE_FIELD, duplicateFailure.error)
    }
}
