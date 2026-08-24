package org.autojs.plugin.ai.provider.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AiProviderConstantsTest {

    @Test
    fun protocolAndSamplerDefaultsArePinnedForGenerationOptionInterop() {
        assertEquals(AiProviderProtocol.PROTOCOL_V1_2, AiProviderProtocol.HOST_PROTOCOL_RANGE.minimum)
        assertEquals(AiProviderProtocol.PROTOCOL_V1_3, AiProviderProtocol.HOST_PROTOCOL_RANGE.maximum)
        assertEquals(1, AiProviderSamplingDefaults.TOP_K)
        assertEquals(0.95, AiProviderSamplingDefaults.TOP_P, 0.0)
        assertEquals(1.0, AiProviderSamplingDefaults.TEMPERATURE, 0.0)
    }

    @Test
    fun publicStringEnumSetsCannotBeMutatedThroughAForcedMutableCast() {
        listOf(
            AiProviderCapabilityId.VALUES,
            AiProviderBackendProfile.VALUES,
            AiProviderBackendAvailability.VALUES,
            AiProviderBackendUnavailableReason.VALUES,
            AiProviderMimeType.INPUT_VALUES,
            AiProviderMimeType.SCHEMA_VALUES,
        ).forEach { values ->
            assertThrows(UnsupportedOperationException::class.java) {
                @Suppress("UNCHECKED_CAST")
                (values as MutableSet<String>).add("forbidden")
            }
        }
    }

    @Test
    fun publicIntegerEnumSetsCannotBeMutatedThroughAForcedMutableCast() {
        listOf(AiMessageRole.VALUES, AiFinishReason.VALUES).forEach { values ->
            assertThrows(UnsupportedOperationException::class.java) {
                @Suppress("UNCHECKED_CAST")
                (values as MutableSet<Int>).add(Int.MAX_VALUE)
            }
        }
    }
}
