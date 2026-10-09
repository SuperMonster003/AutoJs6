package org.autojs.autojs.core.plugin

import org.autojs.plugin.common.api.PluginInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PluginDiscoveryCacheTest {

    private val cache = PluginDiscoveryCache()
    private val info = PluginInfo()
    private val installed = PluginDiscoveryCache.PackageIdentity(versionCode = 91L, lastUpdateTime = 1_000L)

    @Test
    fun reusesInfoWhileTheInstalledPackageIdentityIsUnchanged() {
        assertNull(cache.lookup(installed, "action", "typescript", "plugin.a", ".Service"))
        cache.store(installed, "action", "typescript", "plugin.a", ".Service", info)

        assertSame(info, cache.lookup(installed, "action", "typescript", "plugin.a", ".Service"))
        assertNull(cache.lookup(installed, "action", null, "plugin.a", ".Service"))
        assertNull(cache.lookup(installed, "other", "typescript", "plugin.a", ".Service"))
        assertEquals(1, cache.sizeForTesting())
    }

    @Test
    fun updatedOrRemovedPackagesMissAndDropTheStaleEntry() {
        cache.store(installed, "action", null, "plugin.a", ".Service", info)

        val updated = PluginDiscoveryCache.PackageIdentity(versionCode = 92L, lastUpdateTime = 2_000L)
        assertNull(cache.lookup(updated, "action", null, "plugin.a", ".Service"))
        assertEquals(0, cache.sizeForTesting())

        cache.store(updated, "action", null, "plugin.a", ".Service", info)
        assertNull(cache.lookup(null, "action", null, "plugin.a", ".Service"))
        assertEquals(0, cache.sizeForTesting())
    }

    @Test
    fun storeWithoutIdentityIsIgnoredAndInvalidateIsScoped() {
        cache.store(null, "action", null, "missing", ".Service", info)
        assertEquals(0, cache.sizeForTesting())

        cache.store(installed, "action", null, "plugin.a", ".Service", info)
        cache.store(installed, "action", null, "plugin.b", ".Service", info)
        cache.invalidate("plugin.a")
        assertNull(cache.lookup(installed, "action", null, "plugin.a", ".Service"))
        assertSame(info, cache.lookup(installed, "action", null, "plugin.b", ".Service"))
        cache.invalidate()
        assertEquals(0, cache.sizeForTesting())
    }
}
