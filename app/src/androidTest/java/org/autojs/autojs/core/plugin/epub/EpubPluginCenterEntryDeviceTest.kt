package org.autojs.autojs.core.plugin.epub

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.center.InstalledPluginRepository
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.plugin.epub.api.EpubActions
import org.autojs.plugin.epub.api.EpubIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Plugin roadmap D10 on a device: the Readium EPUB Reader package exports two contract services
 * (Explorer Action and EPUB) but the plugin center lists it once, with one identity, and the
 * single enable switch of that entry governs the EPUB service too (disabled: `PLUGIN_DISABLED`).
 * Writes the entry as text and a screenshot of the plugin center under
 * `cache/epub-evidence/` of the host for the roadmap record.
 *
 * Skipped when no EPUB plugin is installed.
 */
class EpubPluginCenterEntryDeviceTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private val context: Context
        get() = instrumentation.targetContext

    private val evidenceDir: File
        get() = File(context.cacheDir, "epub-evidence").apply { mkdirs() }

    @Test
    fun thePackageHasOneEntryWhoseSwitchCoversBothServices(): Unit = runBlocking {
        assumeTrue("the EPUB plugin is not installed", EpubPluginHost.queryServiceCount(context) > 0)
        val target = EpubPluginHost.discover(context).firstOrNull { it.pluginInfo != null }
            ?: throw AssertionError("no usable EPUB plugin")
        val packageName = target.serviceInfo.packageName
        val epubInfo = requireNotNull(target.pluginInfo)

        val plugins = InstalledPluginRepository().discoverInstalled(context)
        val entries = plugins.filter { it.packageName == packageName }
        val notes = StringBuilder()
        notes.append("device api=").append(Build.VERSION.SDK_INT).append('\n')
        notes.append("entries for ").append(packageName).append(": ").append(entries.size).append('\n')
        for (entry in entries) {
            notes.append("entry: title=").append(entry.title)
                .append(" version=").append(entry.versionName).append(" (").append(entry.versionCode).append(')')
                .append(" engine=").append(entry.engine).append(" variant=").append(entry.variant)
                .append(" id=").append(entry.pluginInfo?.id)
                .append(" serviceAction=").append(entry.serviceAction)
                .append(" requiresHostVersion=").append(entry.requiresHostVersion)
                .append(" description=").append(entry.description?.take(80))
                .append(" bindError=").append(entry.bindError?.message)
                .append('\n')
        }
        notes.append("epub service: engine=").append(epubInfo.engine).append(" variant=").append(epubInfo.variant)
            .append(" id=").append(epubInfo.id).append(" version=").append(epubInfo.versionName).append(" (").append(epubInfo.versionCode).append(")\n")
        Log.i(TAG, notes.toString())

        assertEquals("one plugin-center entry per package (D10)", 1, entries.size)
        val entry = entries.single()
        assertTrue(entry.title.isNotBlank())
        assertNotNull(entry.pluginInfo)
        assertEquals(epubInfo.id, entry.pluginInfo?.id)
        assertEquals(epubInfo.variant, entry.pluginInfo?.variant)
        assertEquals(epubInfo.versionName, entry.versionName)
        assertEquals(epubInfo.versionCode, entry.versionCode)
        assertEquals(EpubIds.ENGINE, epubInfo.engine)
        assertEquals(EpubIds.VARIANT_DEFAULT, epubInfo.variant)
        assertEquals(EpubIds.PLUGIN_ID, epubInfo.id)
        assertTrue("the entry keeps the Explorer Action identity or the EPUB one", entry.engine == "explorer-action" || entry.engine == EpubIds.ENGINE)

        // The single switch: disabled in the store, the EPUB service is refused with PLUGIN_DISABLED.
        val wasEnabled = PluginEnableStore.isEnabled(context, packageName)
        assertTrue("enabled by default (D29)", wasEnabled)
        try {
            PluginEnableStore.setEnabled(context, packageName, false)
            val refusal = try {
                EpubPluginHost.getCapabilities(context)
                fail("expected PLUGIN_DISABLED")
                throw AssertionError()
            } catch (e: EpubPluginException) {
                e.error
            }
            notes.append("disabled: ").append(refusal.code).append(": ").append(refusal.message).append('\n')
            assertEquals("PLUGIN_DISABLED", refusal.code)
            assertFalse(PluginEnableStore.isEnabled(context, packageName))
        } finally {
            PluginEnableStore.setEnabled(context, packageName, wasEnabled)
        }
        assertEquals(EpubActions.SERVICE_ACTION, EpubPluginHost.ACTION_EPUB)
        val capabilities = EpubPluginHost.getCapabilities(context)
        notes.append("re-enabled: contract version ").append(capabilities.getInt("epubContractVersion")).append('\n')

        // The plugin center screen with the entry, for the roadmap record.
        val activity = instrumentation.startActivitySync(
            Intent(context, PluginCenterActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        try {
            delay(4_000)
            instrumentation.waitForIdleSync()
            val shot: Bitmap? = instrumentation.uiAutomation.takeScreenshot()
            if (shot != null) {
                val png = File(evidenceDir, "p51-plugin-center-api${Build.VERSION.SDK_INT}.png")
                png.outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                notes.append("screenshot: ").append(png.name).append(' ').append(png.length()).append(" bytes\n")
            } else {
                notes.append("screenshot: unavailable\n")
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
        File(evidenceDir, "p51-plugin-center-api${Build.VERSION.SDK_INT}.txt").writeText(notes.toString())
        Log.i(TAG, notes.toString())
    }

    companion object {
        private const val TAG = "EpubPluginCenterEntryDeviceTest"
    }
}
