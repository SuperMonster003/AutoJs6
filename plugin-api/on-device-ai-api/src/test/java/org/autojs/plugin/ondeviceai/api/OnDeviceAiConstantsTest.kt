package org.autojs.plugin.ondeviceai.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OnDeviceAiConstantsTest {

    @Test
    fun protocolAndSamplerDefaultsArePinnedForGenerationOptionInterop() {
        assertEquals(OnDeviceAiProtocol.PROTOCOL_V1_1, OnDeviceAiProtocol.HOST_PROTOCOL_RANGE.minimum)
        assertEquals(OnDeviceAiProtocol.PROTOCOL_V1_1, OnDeviceAiProtocol.HOST_PROTOCOL_RANGE.maximum)
        assertEquals(1, OnDeviceAiSamplingDefaults.TOP_K)
        assertEquals(0.95, OnDeviceAiSamplingDefaults.TOP_P, 0.0)
        assertEquals(1.0, OnDeviceAiSamplingDefaults.TEMPERATURE, 0.0)
    }

    @Test
    fun publicStringEnumSetsCannotBeMutatedThroughAForcedMutableCast() {
        listOf(
            OnDeviceAiCapabilityId.VALUES,
            OnDeviceAiMimeType.INPUT_VALUES,
            OnDeviceAiMimeType.SCHEMA_VALUES,
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
