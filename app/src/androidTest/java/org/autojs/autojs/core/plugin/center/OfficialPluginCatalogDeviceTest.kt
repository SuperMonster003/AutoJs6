package org.autojs.autojs.core.plugin.center

import android.util.Log
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in release acceptance against the public catalog and installed signed APKs. */
class OfficialPluginCatalogDeviceTest {

    @Test
    fun publishedPluginsAreRecognizedInBothPluginCenterViews(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val expected = InstrumentationRegistry.getArguments()
            .getString("expectedOfficialPackages").orEmpty()
            .split(',').map(String::trim).filter(String::isNotEmpty).toSet()
        assumeTrue("Pass expectedOfficialPackages after installing the release APKs", expected.isNotEmpty())
        val context = instrumentation.targetContext
        val issues = mutableListOf<PluginIndexFetchIssue>()
        val entries = withTimeout(120_000) {
            PluginIndexRepository().fetchOnlineIndex(
                context, forceRefresh = true,
                enabledSources = setOf(PluginIndexSource.OFFICIAL),
                onIssue = { issues.add(it) },
            )
        }
        assertTrue("Public index fetch issues: $issues", issues.isEmpty())
        val featured = entries.filter { it.isFeatured }.associateBy { it.packageName }
        assertTrue("Missing featured packages: ${expected - featured.keys}", featured.keys.containsAll(expected))
        expected.forEach { packageName ->
            val entry = featured.getValue(packageName)
            assertEquals(packageName, PluginIndexSource.OFFICIAL, entry.source)
            assertTrue("No release for $packageName", entry.releases.isNotEmpty())
            assertTrue("Unrecognized official signature: $packageName",
                PluginTrustManager.resolveTrustInfo(context, packageName).isOfficial)
        }

        for (scope in listOf(null, PluginIndexSource.OFFICIAL)) {
            val model = PluginCenterViewModel()
            val store = ViewModelStore().apply { put("catalog-release-acceptance", model) }
            try {
                instrumentation.runOnMainSync {
                    model.load(context, forceRefreshIndex = false, sourceScope = scope)
                }
                val items = withTimeout(120_000) {
                    model.items.first { current ->
                        val installed = current.filter { it.isInstalled && it.isOfficialVerified }
                            .map { it.packageName }.toSet()
                        installed.containsAll(expected)
                    }
                }
                val selected = items.filter { it.packageName in expected }
                assertEquals("Duplicate or missing packages in scope $scope", expected.size, selected.size)
                selected.forEach { item ->
                    assertEquals(item.packageName, PluginIndexSource.OFFICIAL, item.source)
                    assertEquals(item.packageName, PluginAuthorizedState.OFFICIAL, item.authorizedState)
                    assertTrue("Missing title: ${item.packageName}", item.title.isNotBlank())
                    Log.i("OfficialCatalogAcceptance", "scope=$scope package=${item.packageName} version=${item.versionName} official=${item.isOfficialVerified}")
                }
            } finally {
                instrumentation.runOnMainSync { store.clear() }
            }
        }
        Log.i("OfficialCatalogAcceptance", "PASS ${expected.size} official plugins in both plugin center views")
    }
}
