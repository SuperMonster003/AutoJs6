package org.autojs.autojs.ui.explorer

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.explorer.ExplorerActionEntryPoint
import org.autojs.autojs.core.plugin.explorer.ExplorerActionRegistry
import org.autojs.autojs.core.plugin.explorer.ExplorerSurface
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ApkInspectorIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun installedInspectorExposesPrimaryAndOverflowActionsAndTracksEnableState(): Unit = runBlocking {
        requireInstalledPlugin()
        val directory = File(context.cacheDir, "apk-inspector-catalog-${System.nanoTime()}").apply { mkdirs() }
        try {
            ExplorerActionRegistry.refresh(context)
            listOf("apk", "apks", "apkm", "xapk", "apkz", "aab").forEach { extension ->
                val file = File(directory, "sample.$extension").apply { writeBytes(byteArrayOf()) }
                val item = ExplorerFileItem(file.path, null)
                listOf(ExplorerActionEntryPoint.PRIMARY_BUTTON, ExplorerActionEntryPoint.OVERFLOW_MENU).forEach { entry ->
                    assertEquals("$extension at $entry", 1, ExplorerActionRegistry.actionsFor(
                        ExplorerSurface.MAIN_FILES, entry, item,
                    ).count { it.packageName == PLUGIN })
                }
            }
            val item = ExplorerFileItem(File(directory, "sample.apk").path, null)
            PluginEnableStore.setEnabled(context, PLUGIN, false)
            ExplorerActionRegistry.refresh(context)
            assertTrue(ExplorerActionRegistry.actionsFor(
                ExplorerSurface.MAIN_FILES, ExplorerActionEntryPoint.OVERFLOW_MENU, item,
            ).none { it.packageName == PLUGIN })
        } finally {
            PluginEnableStore.setEnabled(context, PLUGIN, true)
            ExplorerActionRegistry.refresh(context)
            directory.deleteRecursively()
        }
    }

    @Test
    fun overflowActionOpensTheInstalledInspectorAndDisplaysTheRealPackage(): Unit = runBlocking {
        requireInstalledPlugin()
        val arguments = InstrumentationRegistry.getArguments()
        val path = arguments.getString("packageArchivePath")
        assumeTrue("Pass packageArchivePath for the real-device fixture", path != null)
        val file = File(requireNotNull(path))
        assertTrue(file.isFile)
        ExplorerActionRegistry.refresh(context)
        val item = ExplorerFileItem(file.path, null)
        val action = ExplorerActionRegistry.actionsFor(
            ExplorerSurface.MAIN_FILES, ExplorerActionEntryPoint.OVERFLOW_MENU, item,
        ).single { it.packageName == PLUGIN }
        ExplorerActionRegistry.launch(context, ExplorerSurface.MAIN_FILES,
            ExplorerActionEntryPoint.OVERFLOW_MENU, listOf(item), action).getOrThrow()
        try {
            val expected = arguments.getString("expectedPackageName") ?: "com.tencent.mm"
            val deadline = SystemClock.uptimeMillis() + 180_000
            var found = false
            while (SystemClock.uptimeMillis() < deadline && !found) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                found = root?.packageName?.toString() == PLUGIN && containsText(root, expected)
                if (!found) SystemClock.sleep(250)
            }
            assertTrue("Inspector did not display $expected", found)
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(context.cacheDir, "apk-inspector-report.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        } finally {
            instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
    }

    private fun requireInstalledPlugin() {
        assumeTrue("Install and enable APK Inspector for this integration test",
            runCatching { context.packageManager.getPackageInfo(PLUGIN, 0) }.isSuccess &&
                PluginEnableStore.isEnabled(context, PLUGIN))
    }

    private fun containsText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.text?.toString()?.contains(text) == true) return true
        return (0 until node.childCount).any { index -> node.getChild(index)?.let { containsText(it, text) } == true }
    }

    private companion object {
        const val PLUGIN = "io.github.supermonster003.autojs6.plugin.apkinspector"
    }
}
