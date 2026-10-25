package org.autojs.autojs.runtime.api.modules.http

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpContinuationCompatibilityTest {

    @Test
    fun continuationIsUsedOnlyForSynchronousUiRequestsWhenEnabled() {
        for (hasCallback in listOf(false, true)) {
            for (isUiThread in listOf(false, true)) {
                for (isEnabled in listOf(false, true)) {
                    assertEquals(
                        !hasCallback && isUiThread && isEnabled,
                        Http.shouldUseContinuation(hasCallback, isUiThread, isEnabled),
                    )
                }
            }
        }
    }

}
