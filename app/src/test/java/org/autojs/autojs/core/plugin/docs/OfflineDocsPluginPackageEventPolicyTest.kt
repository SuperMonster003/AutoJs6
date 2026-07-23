package org.autojs.autojs.core.plugin.docs

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDocsPluginPackageEventPolicyTest {

    @Test
    fun `replacement removal and addition are ignored until replacement completes`() {
        assertTrue(
            OfflineDocsPluginPackageEventPolicy.shouldIgnore(
                Intent.ACTION_PACKAGE_REMOVED,
                replacing = true,
            ),
        )
        assertTrue(
            OfflineDocsPluginPackageEventPolicy.shouldIgnore(
                Intent.ACTION_PACKAGE_ADDED,
                replacing = true,
            ),
        )
    }

    @Test
    fun `real removal and completed replacement are observed`() {
        assertFalse(
            OfflineDocsPluginPackageEventPolicy.shouldIgnore(
                Intent.ACTION_PACKAGE_REMOVED,
                replacing = false,
            ),
        )
        assertFalse(
            OfflineDocsPluginPackageEventPolicy.shouldIgnore(
                Intent.ACTION_PACKAGE_REPLACED,
                replacing = true,
            ),
        )
    }

    @Test
    fun `replacement and content identity changes require a reload`() {
        val original = OfflineDocsPluginStateMonitor.State(
            status = OfflineDocsPluginStateMonitor.Status.AVAILABLE,
            packageVersionCode = 1L,
            packageLastUpdateTime = 2L,
            contentSha256 = "a".repeat(64),
        )
        val changed = original.copy(contentSha256 = "b".repeat(64))

        assertTrue(
            OfflineDocsPluginStateMonitor.Change(
                id = 1L,
                previous = original,
                current = original,
                cause = OfflineDocsPluginStateMonitor.Cause.PACKAGE_REPLACED,
            ).requiresContentReload,
        )
        assertTrue(
            OfflineDocsPluginStateMonitor.Change(
                id = 1L,
                previous = original,
                current = original.copy(status = OfflineDocsPluginStateMonitor.Status.LOADING),
                cause = OfflineDocsPluginStateMonitor.Cause.PACKAGE_REPLACED,
            ).requiresContentRelease,
        )
        assertTrue(
            OfflineDocsPluginStateMonitor.Change(
                id = 2L,
                previous = original,
                current = changed,
                cause = OfflineDocsPluginStateMonitor.Cause.REFRESHED,
            ).requiresContentReload,
        )
        assertFalse(
            OfflineDocsPluginStateMonitor.Change(
                id = 3L,
                previous = original,
                current = original,
                cause = OfflineDocsPluginStateMonitor.Cause.REFRESHED,
            ).requiresContentReload,
        )
        assertFalse(
            OfflineDocsPluginStateMonitor.Change(
                id = 4L,
                previous = original,
                current = original,
                cause = OfflineDocsPluginStateMonitor.Cause.REFRESHED,
            ).requiresContentRelease,
        )
    }

    @Test
    fun `only the latest asynchronous inspection may be published`() {
        val gate = OfflineDocsInspectionRequestGate()
        val initial = gate.newRequest(OfflineDocsPluginStateMonitor.Cause.INITIALIZED)
        val refreshed = gate.newRequest(OfflineDocsPluginStateMonitor.Cause.REFRESHED)

        assertFalse(gate.isLatest(initial))
        assertTrue(gate.isLatest(refreshed))
        assertNull(gate.resolveCauseForPublication(initial))
        assertEquals(
            OfflineDocsPluginStateMonitor.Cause.REFRESHED,
            gate.resolveCauseForPublication(refreshed),
        )
    }

    @Test
    fun `package replacement survives a following coalesced refresh`() {
        val gate = OfflineDocsInspectionRequestGate()
        gate.newRequest(OfflineDocsPluginStateMonitor.Cause.PACKAGE_REPLACED)
        val refreshed = gate.newRequest(OfflineDocsPluginStateMonitor.Cause.REFRESHED)

        assertEquals(
            OfflineDocsPluginStateMonitor.Cause.PACKAGE_REPLACED,
            gate.resolveCauseForPublication(refreshed),
        )

        val nextRefresh = gate.newRequest(OfflineDocsPluginStateMonitor.Cause.REFRESHED)
        assertEquals(
            OfflineDocsPluginStateMonitor.Cause.REFRESHED,
            gate.resolveCauseForPublication(nextRefresh),
        )
    }

    @Test
    fun `loading state is not exposed as available content`() {
        val loading = OfflineDocsPluginStateMonitor.State(
            status = OfflineDocsPluginStateMonitor.Status.LOADING,
        )

        assertFalse(loading.available)
        assertNull(loading.packageIdentity)
    }
}
