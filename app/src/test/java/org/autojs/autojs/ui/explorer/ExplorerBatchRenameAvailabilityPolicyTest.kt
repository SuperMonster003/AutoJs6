package org.autojs.autojs.ui.explorer

import org.junit.Assert.assertEquals
import org.junit.Test

class ExplorerBatchRenameAvailabilityPolicyTest {

    @Test
    fun missingDisabledUnauthorizedAndUnavailableStatesRemainDistinct() {
        assertEquals(ExplorerBatchRenameAvailability.MISSING, resolve(installed = false))
        assertEquals(ExplorerBatchRenameAvailability.DISABLED, resolve(enabled = false))
        assertEquals(ExplorerBatchRenameAvailability.UNAUTHORIZED, resolve(authorized = false))
        assertEquals(ExplorerBatchRenameAvailability.UNAVAILABLE, resolve())
    }

    private fun resolve(
        installed: Boolean = true,
        enabled: Boolean = true,
        authorized: Boolean = true,
    ) = ExplorerBatchRenameAvailabilityPolicy.resolve(
        installed = installed,
        enabled = enabled,
        authorized = authorized,
    )
}
