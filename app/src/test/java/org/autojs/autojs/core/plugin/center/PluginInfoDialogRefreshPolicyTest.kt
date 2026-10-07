package org.autojs.autojs.core.plugin.center

import org.autojs.autojs.core.plugin.center.PluginInfoDialogRefreshPolicy.NeutralAction
import org.autojs.autojs.core.plugin.center.PluginInfoDialogRefreshPolicy.Refresh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginInfoDialogRefreshPolicyTest {

    private val installed = PluginCenterItem(
        title = "Plugin",
        packageName = "org.autojs.plugin.test",
        versionName = "1.0.0",
        versionCode = 1L,
        isInstalled = true,
        iconSignature = "icon-1",
    )

    @Test
    fun anUnchangedItemLeavesTheOpenDialogAlone() {
        // Cold start publishes the same content several times; each used to replace the dialog window.
        assertEquals(Refresh.NONE, PluginInfoDialogRefreshPolicy.decide(installed, installed.copy()))
    }

    @Test
    fun rowChangesAreAppliedInPlace() {
        listOf(
            installed.copy(description = "Description"),
            installed.copy(enabledState = PluginEnabledState.DISABLED, isEnabled = false),
            installed.copy(authorizedState = PluginAuthorizedState.TRUSTED),
            installed.copy(installedAbis = listOf("arm64-v8a")),
            installed.copy(supportedAbis = listOf("arm64-v8a", "x86_64")),
            installed.copy(versionName = "1.0.1", versionCode = 2L),
            installed.copy(packageSize = 1024L),
            installed.copy(iconSignature = "icon-2"),
        ).forEach { next ->
            assertEquals(next.toString(), Refresh.IN_PLACE, PluginInfoDialogRefreshPolicy.decide(installed, next))
        }
        val updatable = installed.copy(updatableVersionCode = 3L, updatableApkUrl = "https://example.com/a.apk")
        assertEquals(
            "new download data keeps the same button",
            Refresh.IN_PLACE,
            PluginInfoDialogRefreshPolicy.decide(updatable, updatable.copy(updatableApkUrl = "https://example.com/b.apk")),
        )
    }

    @Test
    fun onlyAChangedActionButtonRebuildsTheDialog() {
        assertEquals(Refresh.REBUILD, PluginInfoDialogRefreshPolicy.decide(installed, installed.copy(updatableVersionCode = 2L)))
        assertEquals(Refresh.REBUILD, PluginInfoDialogRefreshPolicy.decide(installed, installed.copy(isInstalled = false)))
        val updatable = installed.copy(updatableVersionCode = 2L)
        assertEquals(Refresh.REBUILD, PluginInfoDialogRefreshPolicy.decide(updatable, updatable.copy(requiresDowngradeReinstall = true)))
        assertEquals(Refresh.REBUILD, PluginInfoDialogRefreshPolicy.decide(updatable, installed))
    }

    @Test
    fun theNeutralActionFollowsInstallationAndUpdateState() {
        assertEquals(NeutralAction.NONE, PluginInfoDialogRefreshPolicy.neutralActionOf(installed))
        assertEquals(NeutralAction.INSTALL, PluginInfoDialogRefreshPolicy.neutralActionOf(installed.copy(isInstalled = false)))
        assertEquals(NeutralAction.VIEW_UPDATE, PluginInfoDialogRefreshPolicy.neutralActionOf(installed.copy(updatableVersionCode = 2L)))
        assertEquals(
            NeutralAction.VIEW_MATCHED_VERSION,
            PluginInfoDialogRefreshPolicy.neutralActionOf(installed.copy(updatableVersionCode = 2L, requiresDowngradeReinstall = true)),
        )
    }

    @Test
    fun theIconIsBoundAgainOnlyWhenItsSourceChanged() {
        assertFalse(PluginInfoDialogRefreshPolicy.iconChanged(installed, installed.copy(description = "Description")))
        assertFalse(PluginInfoDialogRefreshPolicy.iconChanged(installed, installed.copy(isEnabled = false)))
        assertTrue(PluginInfoDialogRefreshPolicy.iconChanged(installed, installed.copy(iconSignature = "icon-2")))
        assertTrue(PluginInfoDialogRefreshPolicy.iconChanged(installed, installed.copy(remoteIconUrl = "https://example.com/icon.png")))
        assertTrue(PluginInfoDialogRefreshPolicy.iconChanged(installed, installed.copy(iconBackgroundColor = "#336699")))
    }
}
