package org.autojs.autojs.core.plugin.center

import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in acceptance of a published index and installed official releases, without clearing data. */
class OfficialCatalogPresentationDeviceTest {
    @Test fun publishedArtworkAndBackgroundsReachInstalledOfficialRows(): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val expected = args.getString("expectedOfficialPackages").orEmpty().split(',').filter(String::isNotBlank).toSet()
        val retired = args.getString("retiredOfficialPackages").orEmpty().split(',').filter(String::isNotBlank).toSet()
        assumeTrue("Pass expectedOfficialPackages after publishing and installing releases", expected.isNotEmpty())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val issues = mutableListOf<PluginIndexFetchIssue>()
        val entries = withTimeout(120_000) {
            PluginIndexRepository().fetchOnlineIndex(context, forceRefresh = true,
                enabledSources = setOf(PluginIndexSource.OFFICIAL), onIssue = { issues.add(it) })
        }.associateBy { it.packageName }
        assertTrue("Public index issues: $issues", issues.isEmpty())
        assertTrue("Missing published packages: ${expected - entries.keys}", entries.keys.containsAll(expected))
        assertTrue("Retired packages are still downloadable", retired.intersect(entries.keys).isEmpty())
        val model = PluginCenterViewModel()
        val store = ViewModelStore().apply { put("catalog-presentation-acceptance", model) }
        try {
            instrumentation.runOnMainSync { model.load(context, forceRefreshIndex = false, sourceScope = PluginIndexSource.OFFICIAL) }
            val rows = withTimeout(120_000) {
                model.items.first { items -> items.filter { it.isInstalled && it.isOfficialVerified }.map { it.packageName }.toSet().containsAll(expected) }
            }.associateBy { it.packageName }
            for (packageName in expected) {
                val row = rows.getValue(packageName)
                val entry = entries.getValue(packageName)
                assertTrue(row.forceIgnoreLocalIcon)
                assertNull("Installed rows must retain the catalog image", row.icon)
                assertEquals(entry.iconUrl.toString(), row.remoteIconUrl)
                assertEquals(entry.nightIconUrl.toString(), row.remoteNightIconUrl)
                assertEquals(entry.iconBackgroundColor, row.iconBackgroundColor)
                assertEquals(entry.nightIconBackgroundColor, row.nightIconBackgroundColor)
                assertNotNull("Published background metadata is missing", row.iconBackgroundColor)
            }
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }
}
