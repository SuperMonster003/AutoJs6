package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCenterActionPositionPolicyTest {

    private val policy = PluginCenterActionPositionPolicy()
    private val comparator = PluginCenterSortPolicy().comparator()
    private val first = item("first", updated = 300)
    private val second = item("second", updated = 200)
    private val third = item("third", updated = 100)
    private val displayed = listOf(first, second, third)

    @Test
    fun automaticRefreshWithoutAUserActionUsesTheNormalOrder() {
        val updated = listOf(first, second, third.copy(lastUpdateTime = 400)).sortedWith(comparator)
        assertSame(updated, policy.apply(updated))
        assertEquals(listOf("third", "first", "second"), names(policy.apply(updated)))
    }

    @Test
    fun disablingKeepsTheRowInPlaceAndUsesItsFreshState() {
        policy.retain(first.packageName, displayed)
        val disabled = first.copy(isEnabled = false, enabledState = PluginEnabledState.DISABLED)
        val result = policy.apply(listOf(disabled, second, third).sortedWith(comparator))

        assertEquals(names(displayed), names(result))
        assertSame(disabled, result.first())
        assertFalse(result.first().isEnabled)
    }

    @Test
    fun installingDoesNotMoveTheNewlyInstalledRowToTheTop() {
        val installable = third.copy(isInstalled = false)
        policy.retain(installable.packageName, listOf(first, second, installable))
        val installed = installable.copy(isInstalled = true, lastUpdateTime = 400)

        val result = policy.apply(listOf(first, second, installed).sortedWith(comparator))
        assertEquals(names(displayed), names(result))
        assertSame(installed, result.last())
    }

    @Test
    fun uninstallingAKnownCatalogPluginKeepsItsInstallableRowInPlace() {
        policy.retain(first.packageName, displayed)
        val uninstalled = first.copy(isInstalled = false, isEnabled = false, installableApkUrl = "https://example.test/plugin.apk")

        val result = policy.apply(listOf(uninstalled, second, third).sortedWith(comparator))
        assertEquals(names(displayed), names(result))
        assertSame(uninstalled, result.first())
        assertFalse(result.first().isInstalled)
    }

    @Test
    fun replacementInstallCanPublishSeveralFreshSnapshotsWithoutMovingTheTarget() {
        policy.retain(third.packageName, displayed)
        val replacing = third.copy(isInstalled = false)
        val installed = third.copy(versionName = "2.0", versionCode = 2, lastUpdateTime = 400)
        val enabled = installed.copy(isEnabled = true, enabledState = PluginEnabledState.READY)

        listOf(replacing, installed, enabled).forEach { latest ->
            val result = policy.apply(listOf(first, second, latest).sortedWith(comparator))
            assertEquals(names(displayed), names(result))
            assertSame(latest, result.last())
        }
    }

    @Test
    fun unrelatedPluginsStillRefreshAndSortNormallyAroundTheRetainedRow() {
        policy.retain(second.packageName, displayed)
        val latestThird = third.copy(lastUpdateTime = 400, description = "Refreshed automatically")
        val newPlugin = item("new", updated = 500)

        val result = policy.apply(listOf(first, second, latestThird, newPlugin).sortedWith(comparator))
        assertEquals(listOf("new", "second", "third", "first"), names(result))
        assertSame(latestThird, result[2])
    }

    @Test
    fun consecutiveActionsRetainBothRows() {
        policy.retain(first.packageName, displayed)
        val disabledFirst = first.copy(isEnabled = false)
        val afterFirstAction = policy.apply(listOf(disabledFirst, second, third).sortedWith(comparator))
        policy.retain(third.packageName, afterFirstAction)
        val updatedThird = third.copy(lastUpdateTime = 400)

        assertEquals(names(displayed), names(policy.apply(listOf(disabledFirst, second, updatedThird).sortedWith(comparator))))
    }

    @Test
    fun aMissingRowIsNotRecreatedAndCanReturnAtItsRetainedPosition() {
        policy.retain(second.packageName, displayed)
        assertEquals(listOf("first", "third"), names(policy.apply(listOf(first, third))))
        val returned = second.copy(lastUpdateTime = 400)
        assertEquals(names(displayed), names(policy.apply(listOf(returned, first, third))))
    }

    @Test
    fun shrinkingListsClampPositionsAndTheNextActionUsesDisplayedPositions() {
        policy.retain(third.packageName, displayed)
        val shrunk = policy.apply(listOf(second, third))
        policy.retain(second.packageName, shrunk)

        assertEquals(listOf("second", "third"), names(policy.apply(listOf(third, second))))
        assertEquals(listOf("third"), names(policy.apply(listOf(third))))
    }

    @Test
    fun clearingAfterAnExplicitRefreshOrSortRestoresTheNormalOrder() {
        policy.retain(first.packageName, displayed)
        assertTrue(policy.isRetained(first.packageName))
        policy.clear()
        val normalOrder = listOf(third, second, first)

        assertFalse(policy.isRetained(first.packageName))
        assertSame(normalOrder, policy.apply(normalOrder))
    }

    @Test
    fun anActionForAnItemOutsideThisViewDoesNotRetainAnything() {
        policy.retain("elsewhere", displayed)
        assertFalse(policy.isRetained("elsewhere"))
        assertSame(displayed, policy.apply(displayed))
    }

    private fun names(items: List<PluginCenterItem>) = items.map { it.title }

    private fun item(name: String, updated: Long) = PluginCenterItem(
        title = name,
        packageName = name,
        versionName = "1.0",
        isInstalled = true,
        isEnabled = true,
        lastUpdateTime = updated,
    )
}
