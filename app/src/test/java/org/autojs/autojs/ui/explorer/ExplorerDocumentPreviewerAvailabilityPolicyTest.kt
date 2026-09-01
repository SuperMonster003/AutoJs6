package org.autojs.autojs.ui.explorer

import org.junit.Assert.assertEquals
import org.junit.Test

class ExplorerDocumentPreviewerAvailabilityPolicyTest {

    @Test
    fun missingDisabledUnauthorizedAndUnavailableStatesRemainDistinct() {
        assertEquals(
            ExplorerDocumentPreviewerAvailability.MISSING,
            resolve(installed = false),
        )
        assertEquals(
            ExplorerDocumentPreviewerAvailability.DISABLED,
            resolve(enabled = false),
        )
        assertEquals(
            ExplorerDocumentPreviewerAvailability.UNAUTHORIZED,
            resolve(authorized = false),
        )
        assertEquals(
            ExplorerDocumentPreviewerAvailability.UNAVAILABLE,
            resolve(),
        )
    }

    private fun resolve(
        installed: Boolean = true,
        enabled: Boolean = true,
        authorized: Boolean = true,
    ) = ExplorerDocumentPreviewerAvailabilityPolicy.resolve(
        installed = installed,
        enabled = enabled,
        authorized = authorized,
    )
}
