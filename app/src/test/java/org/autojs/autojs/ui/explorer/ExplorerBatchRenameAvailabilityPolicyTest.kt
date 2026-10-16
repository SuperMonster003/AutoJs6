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

    /**
     * A plugin that needs a newer host asks for an AutoJs6 upgrade, not a plugin upgrade (Batch Rename plugin roadmap
     * P6.6), even when it is also disabled or unauthorized.
     * zh-CN: 需要更新宿主的插件提示升级 AutoJs6 而不是升级插件 (Batch Rename 插件路线图 P6.6), 即使它同时未启用或未授权.
     */
    @Test
    fun aPluginThatNeedsANewerHostAsksForAnAutoJs6Upgrade() {
        assertEquals(ExplorerBatchRenameAvailability.HOST_TOO_OLD, resolve(requiresHostVersion = 6000, hostVersion = 5454))
        assertEquals(ExplorerBatchRenameAvailability.HOST_TOO_OLD, resolve(enabled = false, authorized = false, requiresHostVersion = 6000, hostVersion = 5454))
        assertEquals(ExplorerBatchRenameAvailability.MISSING, resolve(installed = false, requiresHostVersion = 6000, hostVersion = 5454))
        assertEquals(ExplorerBatchRenameAvailability.UNAVAILABLE, resolve(requiresHostVersion = 5454, hostVersion = 5454))
        assertEquals(ExplorerBatchRenameAvailability.UNAVAILABLE, resolve(requiresHostVersion = null, hostVersion = 5454))
    }

    private fun resolve(
        installed: Boolean = true,
        enabled: Boolean = true,
        authorized: Boolean = true,
        requiresHostVersion: Long? = null,
        hostVersion: Long = 5454,
    ) = ExplorerBatchRenameAvailabilityPolicy.resolve(
        installed = installed,
        enabled = enabled,
        authorized = authorized,
        requiresHostVersion = requiresHostVersion,
        hostVersion = hostVersion,
    )
}
