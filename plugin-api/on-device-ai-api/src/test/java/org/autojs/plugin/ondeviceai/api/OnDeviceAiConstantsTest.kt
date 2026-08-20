package org.autojs.plugin.ondeviceai.api

import org.junit.Assert.assertThrows
import org.junit.Test

class OnDeviceAiConstantsTest {

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
