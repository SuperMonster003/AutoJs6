package org.autojs.plugin.ai.text.api

import org.junit.Assert.assertThrows
import org.junit.Test

class AiTextConstantsTest {

    @Test
    fun publicStringEnumSetsCannotBeMutatedThroughAForcedMutableCast() {
        listOf(
            AiTextCapabilityId.VALUES,
            AiTextMimeType.INPUT_VALUES,
            AiTextMimeType.SCHEMA_VALUES,
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
