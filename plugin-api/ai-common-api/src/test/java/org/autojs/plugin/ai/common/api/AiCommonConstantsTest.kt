package org.autojs.plugin.ai.common.api

import org.junit.Assert.assertThrows
import org.junit.Test

class AiCommonConstantsTest {

    @Test
    fun publicEnumSetsCannotBeMutatedThroughAForcedMutableCast() {
        listOf(
            AiDataLocality.VALUES,
            AiCredentialMode.VALUES,
            AiRetryDisposition.VALUES,
            AiErrorCode.VALUES,
        ).forEach { values ->
            assertThrows(UnsupportedOperationException::class.java) {
                @Suppress("UNCHECKED_CAST")
                (values as MutableSet<Int>).add(Int.MAX_VALUE)
            }
        }
    }
}
