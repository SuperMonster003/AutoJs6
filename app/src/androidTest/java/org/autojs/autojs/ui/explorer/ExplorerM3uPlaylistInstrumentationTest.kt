package org.autojs.autojs.ui.explorer

import android.content.ComponentName
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.core.plugin.explorer.ExplorerActionCatalogPolicy
import org.autojs.autojs.core.plugin.explorer.ExplorerActionEntryPoint
import org.autojs.autojs.core.plugin.explorer.ExplorerM3uPlaylistPolicy
import org.autojs.autojs.core.plugin.explorer.ExplorerSurface
import org.autojs.autojs.core.plugin.explorer.ResolvedExplorerAction
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.ui.main.ResumedMainActivity
import org.autojs.autojs.util.FileUtils
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerActionValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ExplorerM3uPlaylistInstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val amber = action(ExplorerM3uPlaylistPolicy.VIDEO_PACKAGE, "play-video")
    private val terra = action(ExplorerM3uPlaylistPolicy.AUDIO_PACKAGE, "play-audio")

    @Test
    fun remainingPlaylistsKeepPlayAndEditWhileRetainingTheirMatchedPlayer() {
        listOf(
            FileUtils.TYPE.M3U8_AUDIO, FileUtils.TYPE.M3U8_VIDEO, FileUtils.TYPE.PLS,
            FileUtils.TYPE.WAX, FileUtils.TYPE.WVX, FileUtils.TYPE.ASX,
        ).forEach { type ->
            val player = if (type == FileUtils.TYPE.WVX || type == FileUtils.TYPE.ASX ||
                type == FileUtils.TYPE.M3U8_VIDEO) amber else terra
            val item = item(type)
            val provider = RecordingProvider(listOf(player))
            val controller = controller(provider)
            val selected = requireNotNull(controller.resolve(ExplorerActionEntryPoint.PRIMARY_BUTTON, item))
            val state = ExplorerItemActionPolicy.resolve(item, selected)
            assertSame(type.name, ExplorerPrimaryAction.MEDIA_PLAY, state.primaryAction)
            assertEquals(type.name, 2, iconCount(state))
            assertSame(player, state.pluginPrimaryAction)
            assertTrue(controller.launch(context, ExplorerActionEntryPoint.PRIMARY_BUTTON, selected, item))
            assertSame(player, provider.launched)
        }
    }

    @Test
    fun eitherM3uClassificationKeepsTwoButtonsAndPrefersAmberOverCachedTerra() {
        listOf(FileUtils.TYPE.M3U_AUDIO, FileUtils.TYPE.M3U_VIDEO).forEach { type ->
            val item = item(type)
            val provider = RecordingProvider(listOf(terra, amber))
            val controller = controller(provider)
            val selected = controller.resolve(ExplorerActionEntryPoint.PRIMARY_BUTTON, item)
            assertSame(amber, selected)
            val state = ExplorerItemActionPolicy.resolve(item, selected)
            assertSame(ExplorerPrimaryAction.MEDIA_PLAY, state.primaryAction)
            assertEquals(2, iconCount(state))
            assertTrue(state.editVisible)

            assertTrue(controller.launchOfficialMediaOrShowGuidance(
                context, ExplorerActionEntryPoint.PRIMARY_BUTTON, item, preResolvedAction = terra,
            ))
            assertSame(amber, provider.launched)
        }
    }

    @Test
    fun missingAmberUsesTerraForBothButtonAndDefaultClick() {
        listOf(ExplorerActionEntryPoint.PRIMARY_BUTTON, ExplorerActionEntryPoint.DEFAULT_CLICK).forEach { entryPoint ->
            val provider = RecordingProvider(listOf(terra))
            assertTrue(controller(provider).launchOfficialMediaOrShowGuidance(
                context, entryPoint, item(FileUtils.TYPE.M3U_VIDEO),
            ))
            assertSame(terra, provider.launched)
        }
    }

    @Test
    fun unavailablePlayersLinkToAmberInPluginCenter() =
        checkPluginCenterDestination("3-Amber", ExplorerM3uPlaylistPolicy.VIDEO_PACKAGE)

    @Test
    fun unavailablePlayersLinkToTerraInPluginCenter() =
        checkPluginCenterDestination("3-Terra", ExplorerM3uPlaylistPolicy.AUDIO_PACKAGE)

    private fun checkPluginCenterDestination(label: String, packageName: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = ResumedMainActivity.launch()
        val monitor = instrumentation.addMonitor(PluginCenterActivity::class.java.name, null, false)
        try {
            instrumentation.runOnMainSync {
                controller(RecordingProvider(emptyList())).launchOfficialMediaOrShowGuidance(
                    activity, ExplorerActionEntryPoint.PRIMARY_BUTTON, item(FileUtils.TYPE.M3U_AUDIO),
                )
            }
            val deadline = SystemClock.uptimeMillis() + 10_000
            var button: AccessibilityNodeInfo? = null
            while (button == null && SystemClock.uptimeMillis() < deadline) {
                button = instrumentation.uiAutomation.rootInActiveWindow
                    ?.findAccessibilityNodeInfosByText(label.uppercase(Locale.ROOT))
                    ?.firstOrNull { it.text?.toString()?.equals(label, ignoreCase = true) == true && it.isClickable }
                if (button == null) SystemClock.sleep(100)
            }
            instrumentation.uiAutomation.takeScreenshot()?.let { screenshot ->
                File(context.cacheDir, "m3u-unavailable.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                screenshot.recycle()
            }
            assertTrue("Missing plugin installation button: $label", button != null)
            assertTrue(requireNotNull(button).performAction(AccessibilityNodeInfo.ACTION_CLICK))
            val center = instrumentation.waitForMonitorWithTimeout(monitor, 10_000) as? PluginCenterActivity
                ?: throw AssertionError("Plugin Center did not open for $label")
            try {
                assertEquals(packageName, center.intent.getStringExtra("extra_initial_query"))
                assertEquals("OFFICIAL", center.intent.getStringExtra("extra_source_scope"))
            } finally {
                instrumentation.runOnMainSync { center.finish() }
                instrumentation.waitForIdleSync()
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test
    fun unavailableOfficialPlayersKeepPlayAndEditWithoutSelectingAnUnrelatedAction() {
        val item = item(FileUtils.TYPE.M3U_AUDIO)
        val unrelated = action("example.other.player", "play-audio")
        val selected = controller(RecordingProvider(listOf(unrelated)))
            .resolve(ExplorerActionEntryPoint.PRIMARY_BUTTON, item)

        assertNull(selected)
        assertEquals(2, iconCount(ExplorerItemActionPolicy.resolve(item, selected)))
        assertNull(ExplorerM3uPlaylistPolicy.select(listOf(
            action(ExplorerM3uPlaylistPolicy.VIDEO_PACKAGE, "unrelated-action"),
            action(ExplorerM3uPlaylistPolicy.AUDIO_PACKAGE, "unrelated-action"),
        )))
    }

    private fun controller(provider: RecordingProvider) =
        ExplorerPluginActionController { _, _, _, _, _ -> Result.success(Unit) }.apply {
            setProvider(ExplorerSurface.MAIN_FILES, provider)
        }

    private fun item(type: FileUtils.TYPE) = object : ExplorerFileItem("/workspace/playlist.m3u", null) {
        override fun getType(): FileUtils.TYPE = type
    }

    private fun iconCount(state: ExplorerItemActionState): Int = listOf(
        state.primaryAction != null || state.pluginPrimaryAction != null,
        state.editVisible, state.runVisible, state.installVisible, state.infoVisible,
    ).count { it }

    private fun action(packageName: String, id: String): ResolvedExplorerAction {
        val descriptor = requireNotNull(ExplorerActionCatalogPolicy.normalizeDescriptor(
            protocolVersion = ExplorerActionProtocol.MAX_SUPPORTED_VERSION,
            packageName = packageName,
            id = id,
            labelResourceName = null,
            labelFallback = id,
            activityClassName = "$packageName.PlayerActivity",
            priority = 100,
            targetKind = ExplorerActionValues.TARGET_FILE,
            cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
            accessMode = ExplorerActionValues.ACCESS_READ_ONLY,
            placement = ExplorerActionValues.PLACEMENT_PRIMARY,
            mimeTypes = listOf("audio/*", "video/*"),
            extensions = listOf("m3u"),
        ))
        return ResolvedExplorerAction(
            packageName, ComponentName(packageName, descriptor.activityClassName), id, id, descriptor,
            AidlPluginHost.Discovered(ServiceInfo(), null, null),
        )
    }

    private class RecordingProvider(private val actions: List<ResolvedExplorerAction>) : ExplorerPluginActionProvider {
        var launched: ResolvedExplorerAction? = null

        override fun actionsFor(
            surface: ExplorerSurface, entryPoint: ExplorerActionEntryPoint, items: List<ExplorerItem>,
        ): List<ResolvedExplorerAction> = actions

        override fun launch(
            context: Context, surface: ExplorerSurface, entryPoint: ExplorerActionEntryPoint,
            items: List<ExplorerItem>, action: ResolvedExplorerAction,
        ): Result<Unit> {
            launched = action
            return Result.success(Unit)
        }
    }
}
