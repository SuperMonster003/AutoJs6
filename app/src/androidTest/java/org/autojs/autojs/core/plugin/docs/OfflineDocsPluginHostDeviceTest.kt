package org.autojs.autojs.core.plugin.docs

import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.offlinedocs.api.OfflineDocsPluginContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Cross-APK static-content smoke tests for the Offline Documentation plugin. */
class OfflineDocsPluginHostDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun missingPluginRaisesCatchableLoadError() {
        assumeFalse("Offline Documentation plugin is installed", isPluginInstalled())

        val error = runCatching { OfflineDocsPluginHost.load(context) }.exceptionOrNull()
        assertTrue(error.toString(), error is OfflineDocsPluginLoadException)
        assertEquals(
            OfflineDocsPluginLoadError.PLUGIN_NOT_FOUND,
            (error as OfflineDocsPluginLoadException).code,
        )
    }

    @Test
    fun installedPluginExposesValidatedIndexAndExplicitNotFoundResponses() {
        assumeTrue("Offline Documentation plugin is not installed", isPluginInstalled())

        val diagnostics = OfflineDocsPluginHost.discover(context).joinToString { discovered ->
            "${discovered.serviceInfo.packageName}/${discovered.serviceInfo.name}: " +
                    (discovered.error?.code ?: "eligible")
        }
        assertTrue(diagnostics, OfflineDocsPluginHost.isAvailable(context))

        val loaded = OfflineDocsPluginHost.load(context)
        assertEquals(OfflineDocsPluginContract.DEFAULT_PACKAGE_NAME, loaded.candidate.serviceInfo.packageName)
        assertEquals(OfflineDocsPluginContract.VERSION, loaded.candidate.contractVersion)
        assertEquals(OfflineDocsPluginContract.CONTENT_FORMAT, loaded.candidate.contentFormat)
        assertEquals(OfflineDocsPluginContract.ASSET_ROOT, loaded.candidate.assetRoot)
        assertEquals(OfflineDocsPluginContract.ENTRY_POINT, loaded.candidate.entryPoint)
        assertEquals(loaded.candidate.fileCount, loaded.verifiedInventory.fileCount)
        assertEquals(loaded.candidate.totalBytes, loaded.verifiedInventory.totalBytes)
        assertEquals(loaded.candidate.contentSha256, loaded.verifiedInventory.contentSha256)
        assertTrue(OfflineDocsPluginContract.ENTRY_POINT in loaded.verifiedAssetPaths)

        val handler = OfflineDocsAssetPathHandler(loaded)
        val index = handler.handle("")
        assertEquals(200, index.statusCode)
        assertEquals("text/html", index.mimeType)
        index.data.bufferedReader().use { reader ->
            assertTrue(reader.readText().contains("<!doctype html>", ignoreCase = true))
        }

        assertEquals(404, handler.handle("missing.html").statusCode)
        assertEquals(404, handler.handle("../index.html").statusCode)
        assertEquals(404, handler.handle("%252e%252e/index.html").statusCode)
    }

    @Suppress("DEPRECATION")
    private fun isPluginInstalled(): Boolean = try {
        context.packageManager.getApplicationInfo(OfflineDocsPluginContract.DEFAULT_PACKAGE_NAME, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
