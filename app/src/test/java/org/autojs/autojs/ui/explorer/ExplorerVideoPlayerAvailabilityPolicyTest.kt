package org.autojs.autojs.ui.explorer

import org.junit.Assert.assertEquals
import org.junit.Test

class ExplorerVideoPlayerAvailabilityPolicyTest {

    @Test
    fun missingDisabledUnauthorizedAndUnavailableStatesRemainDistinct() {
        assertEquals(
            ExplorerVideoPlayerAvailability.MISSING,
            resolve(installed = false),
        )
        assertEquals(
            ExplorerVideoPlayerAvailability.DISABLED,
            resolve(enabled = false),
        )
        assertEquals(
            ExplorerVideoPlayerAvailability.UNAUTHORIZED,
            resolve(authorized = false),
        )
        assertEquals(
            ExplorerVideoPlayerAvailability.UNAVAILABLE,
            resolve(),
        )
    }

    private fun resolve(
        installed: Boolean = true,
        enabled: Boolean = true,
        authorized: Boolean = true,
    ) = ExplorerVideoPlayerAvailabilityPolicy.resolve(
        installed = installed,
        enabled = enabled,
        authorized = authorized,
    )
}
