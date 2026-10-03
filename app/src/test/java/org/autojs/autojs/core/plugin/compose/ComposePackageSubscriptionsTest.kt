package org.autojs.autojs.core.plugin.compose

import org.autojs.plugin.compose.api.ComposeUiErrorCodes as Codes
import org.junit.Assert.*
import org.junit.Test

class ComposePackageSubscriptionsTest {
    private fun identity(epoch: Long) = ComposeUiPluginLoader.Package("compose.test", 1, epoch, "/$epoch/base.apk", "Factory")

    @Test fun updateInvalidatesOldEpochOnceButLateBroadcastsPreserveTheNewEpoch() {
        val subscriptions = ComposePackageSubscriptions()
        val old = ArrayList<Throwable>(); val fresh = ArrayList<Throwable>()
        subscriptions.add(identity(1), old::add)
        subscriptions.changed(identity(1))
        assertTrue(old.isEmpty())
        subscriptions.add(identity(2), fresh::add)
        subscriptions.changed(identity(2))
        subscriptions.changed(identity(2))
        assertEquals(Codes.PLUGIN_UNAVAILABLE, (old.single() as ComposeUiError).code)
        assertTrue(fresh.isEmpty())
        subscriptions.changed(null)
        assertEquals(Codes.PLUGIN_UNAVAILABLE, (fresh.single() as ComposeUiError).code)
    }

    @Test fun gateFailuresKeepTheirCodeAndReleaseDoesNotAffectOtherSubscriptions() {
        val subscriptions = ComposePackageSubscriptions()
        val first = ArrayList<Throwable>(); val second = ArrayList<Throwable>()
        val removed = subscriptions.add(identity(1), first::add)
        subscriptions.add(identity(1), second::add)
        removed.close(); removed.close()
        subscriptions.changed(identity(1), ComposeUiError(Codes.PLUGIN_UNAUTHORIZED))
        assertTrue(first.isEmpty())
        assertEquals(Codes.PLUGIN_UNAUTHORIZED, (second.single() as ComposeUiError).code)
        subscriptions.changed(identity(1), ComposeUiError(Codes.PLUGIN_DISABLED))
        assertEquals(1, second.size)
    }

    @Test fun aDelayedOldInventoryResultCannotInvalidateSubscriptionsCreatedDuringTheQuery() {
        val subscriptions = ComposePackageSubscriptions()
        val old = ArrayList<Throwable>(); val fresh = ArrayList<Throwable>()
        subscriptions.add(identity(1), old::add)
        val queryingOldPackage = subscriptions.snapshot()
        // PackageManager has returned epoch 1, but another script loads epoch 2 before that
        // result is processed. Its registration performs a fresh check and closes epoch 1.
        subscriptions.add(identity(2), fresh::add)
        subscriptions.changed(identity(2))
        subscriptions.changed(identity(1), observed = queryingOldPackage)
        subscriptions.changed(null, observed = queryingOldPackage)
        assertEquals(1, old.size)
        assertTrue("New subscriptions are outside the earlier query's ownership", fresh.isEmpty())
        subscriptions.changed(null)
        assertEquals(Codes.PLUGIN_UNAVAILABLE, (fresh.single() as ComposeUiError).code)
    }

    @Test fun changedCodeTakesPrecedenceOverTheNewPackagesCompatibilityGate() {
        val subscriptions = ComposePackageSubscriptions()
        val failures = ArrayList<Throwable>()
        subscriptions.add(identity(1), failures::add)
        subscriptions.changed(identity(2), ComposeUiError(Codes.PLUGIN_INCOMPATIBLE))
        assertEquals(Codes.PLUGIN_UNAVAILABLE, (failures.single() as ComposeUiError).code)
    }

    @Test fun notificationsRunOutsideTheRegistryMonitorAndCloseCancelsAllWatches() {
        val subscriptions = ComposePackageSubscriptions()
        var calls = 0
        subscriptions.add(identity(1)) {
            assertFalse(Thread.holdsLock(subscriptions))
            calls++
        }
        subscriptions.changed(null)
        subscriptions.add(identity(2)) { calls++ }
        subscriptions.close()
        subscriptions.changed(null)
        assertEquals(1, calls)
    }
}
