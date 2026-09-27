package org.autojs.autojs.core.plugin.agent

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.ContextThemeWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.app.tool.ThreeStoveAgentTool
import org.autojs.autojs.core.plugin.center.InstalledPluginRepository
import org.autojs.autojs.core.plugin.center.PluginDefaultEnabledPolicy
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.ui.settings.ThreeStoveAgentLauncher
import org.autojs.autojs.engine.NodeAccessibilitySmokeActivity
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentActions
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentIds
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Requires the official Three Stove Agent APK. No Agent task or model request is started. */
class ThreeStoveAgentEntryDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** The installed plugin creates the PendingIntent and sends the production protected broadcast. */
    @Test fun installedPluginLauncherRequestsProductionAttachmentAfterWake() = runBlocking {
        assumeTrue("Opt in with -e autojs.agent.entry true",
            InstrumentationRegistry.getArguments().getString("autojs.agent.entry").toBoolean())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val controller = ThreeStoveAgentLinkController.get(context)
        val owner = ThreeStoveAgentConnectionOwner.get(context)
        assertNull("Do not interrupt an existing task", controller.status.value.runningRunId)
        assertEquals(0, controller.status.value.queuedCount)
        val wasClosed = owner.isNormallyClosed
        val enabled = PluginEnableStore.isEnabled(context, ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME, false)
        val roots = Pref.getStringOrNull(ThreeStoveAgentConnectionOwner.ROOT_CONFIGURATION_KEY)
        fun await(message: String, check: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 20_000
            while (!check() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
            assertTrue(message, check())
        }
        try {
            instrumentation.runOnMainSync { owner.disconnect() }
            await("Disconnected before any plugin request") { controller.status.value.state == "detached" }
            context.startActivity(Intent("org.autojs.plugin.action.WAKE").addCategory(Intent.CATEGORY_DEFAULT)
                .setPackage(ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await("Installed Wake Activity clears the stopped flag") {
                context.packageManager.getApplicationInfo(ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME, 0).flags and ApplicationInfo.FLAG_STOPPED == 0
            }
            PluginEnableStore.setEnabled(context, ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME, true)
            assertEquals(ThreeStoveAgentUiState.AVAILABLE, ThreeStoveAgentPluginInspector.inspect(context).state)
            assertTrue("Automatic host restoration is disabled", owner.isNormallyClosed)
            val started = SystemClock.elapsedRealtime()
            context.startActivity(checkNotNull(ThreeStoveAgentLauncher.resolveIntent(context))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            await("Plugin broadcast must attach the real host controller") { controller.status.value.state == "attached" }
            assertFalse(owner.isNormallyClosed)
            assertNull(controller.status.value.runningRunId)
            assertEquals(0, controller.status.value.queuedCount)
            instrumentation.sendStatus(0, android.os.Bundle().apply {
                putString("stream", "P7_ENTRY productionBroadcastAttached=true elapsedMs=${SystemClock.elapsedRealtime() - started}\n")
            })
        } finally {
            instrumentation.runOnMainSync { owner.disconnect() }
            await("Final detach releases the production lease") { controller.status.value.state == "detached" }
            PluginEnableStore.setEnabled(context, ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME, enabled)
            if (roots == null) Pref.remove(ThreeStoveAgentConnectionOwner.ROOT_CONFIGURATION_KEY)
            else Pref.putString(ThreeStoveAgentConnectionOwner.ROOT_CONFIGURATION_KEY, roots)
            owner.isNormallyClosed = wasClosed
        }
    }

    @Test fun installedPluginIsVisibleAndItsExistingLauncherIsExplicit() = runBlocking {
        assertEquals(1, ThreeStoveAgentPluginHost.queryServiceCount(context))
        val entry = InstalledPluginRepository().discoverInstalledSnapshot(context)
            .single { it.packageName == ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME }
        assertEquals("three-stove-agent", entry.engine)
        assertFalse(PluginDefaultEnabledPolicy.isEnabledByDefault(entry.engine))
        val launch = checkNotNull(ThreeStoveAgentLauncher.resolveIntent(context))
        assertEquals(ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME, launch.component?.packageName)
        assertEquals(Intent.ACTION_MAIN, launch.action)
        assertTrue(launch.hasCategory(Intent.CATEGORY_LAUNCHER))
        val metadata = ThreeStoveAgentPluginHost.probeMetadata(context, ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME)
        assertEquals(ThreeStoveAgentIds.PLUGIN_ID, metadata.id)
        assertEquals(ThreeStoveAgentIds.ENGINE, metadata.engine)
        val required = metadata.capabilities!!.getLong(PluginCapabilityKeys.REQUIRES_HOST_VERSION)
        @Suppress("DEPRECATION")
        val service = context.packageManager.queryIntentServices(Intent(ThreeStoveAgentActions.SERVICE_ACTION)
            .addCategory(ThreeStoveAgentActions.SERVICE_CATEGORY).setPackage(ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME), PackageManager.GET_META_DATA).single().serviceInfo
        assertEquals("Installed metadata and service manifest must agree", service.metaData.getInt("requiresHostVersion").toLong(), required)
        assertTrue("Plugin may require a newer host than the original contract floor",
            required in ThreeStoveAgentIds.REQUIRED_HOST_VERSION_CODE..BuildConfig.VERSION_CODE.toLong())
    }

    @Test fun receiverManifestAndForeignIdentityRequestsAreRejectedBeforeBinding() {
        @Suppress("DEPRECATION")
        val receiver = context.packageManager.getReceiverInfo(ComponentName(context, ThreeStoveAgentAttachRequestReceiver::class.java), PackageManager.GET_META_DATA)
        assertTrue(receiver.exported)
        assertEquals(ThreeStoveAgentActions.PLUGIN_PERMISSION, receiver.permission)
        val request = Intent(ThreeStoveAgentActions.ACTION_ATTACH_REQUEST).setPackage(context.packageName)
        assertFalse(ThreeStoveAgentAttachRequestReceiver.isAuthorizedRequest(context, request))
        val identity = PendingIntent.getBroadcast(context, 519,
            Intent("org.autojs.autojs6.test.AGENT_IDENTITY").setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)
        try {
            request.putExtra(ThreeStoveAgentActions.EXTRA_ATTACH_IDENTITY, identity)
            assertFalse("A host-created identity is not a plugin-created identity",
                ThreeStoveAgentAttachRequestReceiver.isAuthorizedRequest(context, request))
            val foreground = Intent(request).putExtra(ThreeStoveAgentActions.EXTRA_THREE_STOVE_AGENT_ATTACH, true)
            ThreeStoveAgentAttachRequestReceiver.handleForegroundRequest(context, foreground)
            assertFalse(foreground.hasExtra(ThreeStoveAgentActions.EXTRA_THREE_STOVE_AGENT_ATTACH))
            assertFalse(foreground.hasExtra(ThreeStoveAgentActions.EXTRA_ATTACH_IDENTITY))
        } finally { identity.cancel() }
    }

    @Test fun everyGuideStateRendersAndAttachedSubtitleShowsRunAndQueueCounts() {
        for (state in ThreeStoveAgentUiState.entries) {
            assertTrue(state.name, ThreeStoveAgentTool.subtitle(context, ThreeStoveAgentToolState(state)).isNotBlank())
        }
        val text = ThreeStoveAgentTool.subtitle(context, ThreeStoveAgentToolState(ThreeStoveAgentUiState.ATTACHED,
            ThreeStoveAgentLinkStatus("attached", "test-run", queuedCount = 3)))
        assertTrue(text.contains("1"))
        assertTrue(text.contains("3"))
    }

    @Test fun allDiscoveryGuidesShowTheirSpecificMessageAndDismissCleanly() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(context, NodeAccessibilitySmokeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            instrumentation.runOnMainSync {
                val themed = ContextThemeWrapper(activity, context.applicationInfo.theme)
                val tool = ThreeStoveAgentTool(themed)
                val show = ThreeStoveAgentTool::class.java.getDeclaredMethod("showGuide", ThreeStoveAgentInspection::class.java).apply { isAccessible = true }
                val field = ThreeStoveAgentTool::class.java.getDeclaredField("dialog").apply { isAccessible = true }
                val messages = mapOf(
                    ThreeStoveAgentUiState.NOT_INSTALLED to R.string.prompt_three_stove_agent_install,
                    ThreeStoveAgentUiState.APPLICATION_DISABLED to R.string.prompt_three_stove_agent_application_disabled,
                    ThreeStoveAgentUiState.ACTIVATION_REQUIRED to R.string.prompt_three_stove_agent_activate,
                    ThreeStoveAgentUiState.PLUGIN_DISABLED to R.string.prompt_three_stove_agent_enable,
                    ThreeStoveAgentUiState.AUTHORIZATION_REQUIRED to R.string.prompt_three_stove_agent_authorize,
                    ThreeStoveAgentUiState.TRUST_CONFIRMATION_REQUIRED to R.string.prompt_three_stove_agent_trust,
                    ThreeStoveAgentUiState.FAILED to R.string.prompt_three_stove_agent_failed,
                )
                try {
                    for ((state, message) in messages) {
                        show.invoke(tool, ThreeStoveAgentInspection(state))
                        val dialog = field.get(tool) as MaterialDialog
                        assertTrue(state.name, dialog.isShowing)
                        assertEquals(themed.getString(message), dialog.contentView!!.text.toString())
                        dialog.dismiss()
                    }
                    val required = BuildConfig.VERSION_CODE.toLong() + 1
                    show.invoke(tool, ThreeStoveAgentInspection(ThreeStoveAgentUiState.INCOMPATIBLE, required))
                    val dialog = field.get(tool) as MaterialDialog
                    assertTrue(dialog.isShowing)
                    assertEquals(themed.getString(R.string.prompt_three_stove_agent_incompatible, required), dialog.contentView!!.text.toString())
                } finally { tool.dispose() }
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
